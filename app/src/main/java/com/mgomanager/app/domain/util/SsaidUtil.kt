package com.mgomanager.app.domain.util

import com.mgomanager.app.data.repository.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for SSAID (Android ID) operations
 * Handles generation, extraction, and patching of SSAID for Monopoly GO
 */
@Singleton
class SsaidUtil @Inject constructor(
    private val rootUtil: RootUtil,
    private val idExtractor: IdExtractor,
    private val logRepository: LogRepository
) {

    companion object {
        const val SSAID_PATH = "/data/system/users/0/settings_ssaid.xml"
        const val MGO_PACKAGE = "com.scopely.monopolygo"
    }

    /**
     * Generate a new 16-character hex SSAID (similar to Android's format)
     */
    fun generateNewSsaid(): String {
        // Generate UUID and take first 16 hex characters (without dashes)
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return uuid.substring(0, 16).lowercase()
    }

    /**
     * Read current SSAID for Monopoly GO from settings_ssaid.xml
     */
    suspend fun readCurrentSsaid(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Copy the file to a temp location we can read
            val tempPath = "/data/local/tmp/ssaid_temp.xml"
            rootUtil.executeCommand("cp $SSAID_PATH $tempPath").getOrThrow()
            rootUtil.executeCommand("chmod 644 $tempPath").getOrThrow()

            val tempFile = File(tempPath)
            val ssaid = idExtractor.extractSsaid(tempFile)

            // Clean up
            rootUtil.executeCommand("rm -f $tempPath")

            if (ssaid == "nicht vorhanden" || ssaid.isBlank()) {
                Result.failure(Exception("SSAID konnte nicht gelesen werden"))
            } else {
                logRepository.logInfo("SSAID_UTIL", "Aktuelle SSAID gelesen: $ssaid")
                Result.success(ssaid)
            }
        } catch (e: Exception) {
            logRepository.logError("SSAID_UTIL", "Fehler beim Lesen der SSAID", exception = e)
            Result.failure(e)
        }
    }

    /**
     * Patch SSAID in settings_ssaid.xml for Monopoly GO
     * This modifies the Android Binary XML (ABX) file to set a new SSAID value
     */
    suspend fun patchSsaid(newSsaid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logRepository.logInfo("SSAID_UTIL", "Starte SSAID-Patching mit Wert: $newSsaid")

            // Validate SSAID format (16 hex characters)
            if (!newSsaid.matches(Regex("^[a-f0-9]{16}$"))) {
                return@withContext Result.failure(Exception("Ungültiges SSAID-Format: $newSsaid"))
            }

            // Read current SSAID to find what we need to replace
            val currentSsaidResult = readCurrentSsaid()
            val currentSsaid = currentSsaidResult.getOrNull()

            if (currentSsaid != null && currentSsaid != newSsaid) {
                // Use sed to replace the old SSAID with new one in the binary file
                // This works because SSAIDs are plain text in the ABX file
                val sedResult = rootUtil.executeCommand(
                    "sed -i 's/$currentSsaid/$newSsaid/g' $SSAID_PATH"
                )

                if (sedResult.isFailure) {
                    // Fallback: try using hexdump and xxd approach
                    val fallbackResult = patchSsaidFallback(currentSsaid, newSsaid)
                    if (fallbackResult.isFailure) {
                        throw Exception("SSAID-Patching fehlgeschlagen: ${fallbackResult.exceptionOrNull()?.message}")
                    }
                }

                logRepository.logInfo("SSAID_UTIL", "SSAID erfolgreich gepatcht: $currentSsaid -> $newSsaid")
            } else if (currentSsaid == null) {
                // No existing entry for Monopoly GO, we might need to add one
                logRepository.logWarning("SSAID_UTIL", "Keine bestehende SSAID für Monopoly GO gefunden")
                // The entry will be created when the app is launched
            } else {
                logRepository.logInfo("SSAID_UTIL", "SSAID ist bereits $newSsaid, kein Patching nötig")
            }

            // Verify the change
            val verifyResult = readCurrentSsaid()
            val verifiedSsaid = verifyResult.getOrNull()

            if (verifiedSsaid != null && verifiedSsaid != newSsaid) {
                logRepository.logWarning(
                    "SSAID_UTIL",
                    "SSAID-Verifikation ergab anderen Wert: erwartet $newSsaid, gefunden $verifiedSsaid"
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            logRepository.logError("SSAID_UTIL", "SSAID-Patching fehlgeschlagen", exception = e)
            Result.failure(e)
        }
    }

    /**
     * Fallback method for patching SSAID using binary manipulation
     */
    private suspend fun patchSsaidFallback(oldSsaid: String, newSsaid: String): Result<Unit> {
        return try {
            // Use dd or custom binary replacement
            val tempPath = "/data/local/tmp/ssaid_patch.xml"

            // Copy file to temp location
            rootUtil.executeCommand("cp $SSAID_PATH $tempPath").getOrThrow()

            // Try using tr or busybox sed
            val replaceResult = rootUtil.executeCommand(
                "busybox sed -i 's/$oldSsaid/$newSsaid/g' $tempPath 2>/dev/null || sed -i 's/$oldSsaid/$newSsaid/g' $tempPath"
            )

            if (replaceResult.isSuccess) {
                // Copy back
                rootUtil.executeCommand("cp $tempPath $SSAID_PATH").getOrThrow()
                rootUtil.executeCommand("chmod 600 $SSAID_PATH").getOrThrow()
            }

            // Clean up
            rootUtil.executeCommand("rm -f $tempPath")

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear Monopoly GO app data
     */
    suspend fun clearMonopolyGoData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logRepository.logInfo("SSAID_UTIL", "Lösche Monopoly GO App-Daten")

            // Use pm clear to wipe all app data
            val clearResult = rootUtil.executeCommand("pm clear $MGO_PACKAGE")

            if (clearResult.isFailure) {
                throw Exception("pm clear fehlgeschlagen: ${clearResult.exceptionOrNull()?.message}")
            }

            // Additionally ensure the data directory is clean
            rootUtil.executeCommand("rm -rf /data/data/$MGO_PACKAGE/*")

            logRepository.logInfo("SSAID_UTIL", "Monopoly GO App-Daten erfolgreich gelöscht")
            Result.success(Unit)
        } catch (e: Exception) {
            logRepository.logError("SSAID_UTIL", "Fehler beim Löschen der App-Daten", exception = e)
            Result.failure(e)
        }
    }

    /**
     * Start Monopoly GO for initialization
     */
    suspend fun startMonopolyGo(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logRepository.logInfo("SSAID_UTIL", "Starte Monopoly GO")
            rootUtil.executeCommand("am start -n $MGO_PACKAGE/.MainActivity").map { }
        } catch (e: Exception) {
            logRepository.logError("SSAID_UTIL", "Fehler beim Starten von Monopoly GO", exception = e)
            Result.failure(e)
        }
    }

    /**
     * Stop Monopoly GO
     */
    suspend fun stopMonopolyGo(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logRepository.logInfo("SSAID_UTIL", "Stoppe Monopoly GO")
            rootUtil.forceStopMonopolyGo()
        } catch (e: Exception) {
            logRepository.logError("SSAID_UTIL", "Fehler beim Stoppen von Monopoly GO", exception = e)
            Result.failure(e)
        }
    }
}

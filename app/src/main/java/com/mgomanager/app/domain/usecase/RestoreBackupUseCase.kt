package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FilePermissionManager
import com.mgomanager.app.domain.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class RestoreBackupUseCase @Inject constructor(
    private val rootUtil: RootUtil,
    private val permissionManager: FilePermissionManager,
    private val accountRepository: AccountRepository,
    private val logRepository: LogRepository
) {

    companion object {
        const val MGO_DATA_PATH = "/data/data/com.scopely.monopolygo"
        const val MGO_FILES_PATH = "$MGO_DATA_PATH/files"
        const val MGO_PREFS_PATH = "$MGO_DATA_PATH/shared_prefs"
        const val SSAID_PATH = "/data/system/users/0/settings_ssaid.xml"
    }

    suspend fun execute(accountId: Long, startMonopolyGo: Boolean = true): RestoreResult = withContext(Dispatchers.IO) {
        val correlationId = UUID.randomUUID().toString()
        val sessionId = logRepository.getCurrentSessionId()
        val logBuilder = StringBuilder()
        var logLevel = "INFO"
        var accountNameForLog: String? = null
        try {
            // Step 1: Get account from database
            val account = accountRepository.getAccountById(accountId)
                ?: run {
                    logBuilder.appendLine("Starte Restore von Unbekannt")
                    logBuilder.appendLine("correlationId: $correlationId")
                    logBuilder.appendLine("sessionId: $sessionId")
                    logBuilder.appendLine()
                    logBuilder.appendLine("1. Account finden .. [Fehler]")
                    logBuilder.appendLine("Fehlerdetails: Account nicht gefunden")
                    logRepository.addLog(
                        "ERROR",
                        "RESTORE",
                        "Account unbekannt Restore.",
                        null,
                        logBuilder.toString()
                    )
                    return@withContext RestoreResult.Failure("Account nicht gefunden")
                }
            accountNameForLog = account.fullName

            logBuilder.appendLine("Starte Restore von ${account.fullName}")
            logBuilder.appendLine("correlationId: $correlationId")
            logBuilder.appendLine("sessionId: $sessionId")
            logBuilder.appendLine()

            // Step 1: Validate backup files exist
            logBuilder.appendLine("1. Validiere das Backup ..")
            val backupPath = account.backupPath
            val diskCacheDir = File("${backupPath}DiskBasedCacheDirectory/")
            val sharedPrefsDir = File("${backupPath}shared_prefs/")
            val ssaidFile = File("${backupPath}settings_ssaid.xml")

            val missingDirectory = when {
                !diskCacheDir.exists() -> diskCacheDir.path
                !sharedPrefsDir.exists() -> sharedPrefsDir.path
                else -> null
            }
            if (missingDirectory != null) {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "Files vorhanden ..",
                    "Fehler",
                    "Fehlendes Verzeichnis: $missingDirectory"
                )
            } else {
                appendStatusLine(logBuilder, "Files vorhanden ..", "Erfolg")
            }

            if (!ssaidFile.exists()) {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "SSAID vorhanden ..",
                    "Fehler",
                    "Fehlende Datei: ${ssaidFile.path}"
                )
            } else {
                appendStatusLine(logBuilder, "SSAID vorhanden ..", "Erfolg")
            }

            if (logLevel == "ERROR") {
                logRepository.addLog(
                    logLevel,
                    "RESTORE",
                    "Account ${account.fullName} Restore.",
                    account.fullName,
                    logBuilder.toString()
                )
                return@withContext RestoreResult.Failure("Backup-Dateien fehlen oder sind beschädigt")
            }

            // Step 2: Force stop Monopoly Go
            val stopResult = rootUtil.forceStopMonopolyGo()
            if (stopResult.isFailure) {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "2. Stoppe Monopoly GO..",
                    "Fehler",
                    resolveRootError(stopResult.exceptionOrNull())
                )
                logRepository.addLog(
                    logLevel,
                    "RESTORE",
                    "Account ${account.fullName} Restore.",
                    account.fullName,
                    logBuilder.toString()
                )
                return@withContext RestoreResult.Failure("Monopoly GO konnte nicht gestoppt werden")
            } else {
                appendStatusLine(logBuilder, "2. Stoppe Monopoly GO..", "Erfolg")
            }

            // Step 3: Remove old directories and copy backup directories
            logBuilder.appendLine()
            logBuilder.appendLine("3. Wechsel Accountdaten ..")
            val rmDiskCacheResult = rootUtil.executeCommand("rm -rf \"$MGO_FILES_PATH/DiskBasedCacheDirectory\"")
            val rmPrefsResult = rootUtil.executeCommand("rm -rf \"$MGO_PREFS_PATH\"")
            val cleanupFailed = rmDiskCacheResult.isFailure || rmPrefsResult.isFailure
            val cleanupStatus = if (cleanupFailed) "Warnung" else "Erfolg"
            appendStatusLine(logBuilder, "Data Pfad säubern", cleanupStatus)

            rootUtil.executeCommand("mkdir -p \"$MGO_FILES_PATH\"")
            rootUtil.executeCommand("mkdir -p \"$MGO_DATA_PATH\"")

            val copyDiskCacheResult = copyBackupDirectory(
                "${backupPath}DiskBasedCacheDirectory",
                "$MGO_FILES_PATH/DiskBasedCacheDirectory"
            )
            if (copyDiskCacheResult.isFailure) {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "Account Files kopieren (1/2)",
                    "Fehler",
                    resolveCopyError(
                        "${backupPath}DiskBasedCacheDirectory",
                        "$MGO_FILES_PATH/DiskBasedCacheDirectory",
                        copyDiskCacheResult.exceptionOrNull()
                    )
                )
                logRepository.addLog(
                    logLevel,
                    "RESTORE",
                    "Account ${account.fullName} Restore.",
                    account.fullName,
                    logBuilder.toString()
                )
                return@withContext RestoreResult.Failure("Backup konnte nicht kopiert werden")
            } else {
                appendStatusLine(logBuilder, "Account Files kopieren (1/2)", "Erfolg")
            }

            val copyPrefsResult = copyBackupDirectory(
                "${backupPath}shared_prefs",
                "$MGO_PREFS_PATH"
            )
            if (copyPrefsResult.isFailure) {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "Account Files kopieren (2/2)",
                    "Fehler",
                    resolveCopyError(
                        "${backupPath}shared_prefs",
                        "$MGO_PREFS_PATH",
                        copyPrefsResult.exceptionOrNull()
                    )
                )
                logRepository.addLog(
                    logLevel,
                    "RESTORE",
                    "Account ${account.fullName} Restore.",
                    account.fullName,
                    logBuilder.toString()
                )
                return@withContext RestoreResult.Failure("Backup konnte nicht kopiert werden")
            } else {
                appendStatusLine(logBuilder, "Account Files kopieren (2/2)", "Erfolg")
            }

            // Step 4: Restore SSAID
            val ssaidCopyResult = rootUtil.executeCommand("cp \"${backupPath}settings_ssaid.xml\" \"$SSAID_PATH\"")
            if (ssaidCopyResult.isFailure) {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "4. IDs werden korrigiert",
                    "Fehler",
                    resolveCopyError("${backupPath}settings_ssaid.xml", SSAID_PATH, ssaidCopyResult.exceptionOrNull())
                )
                logRepository.addLog(
                    logLevel,
                    "RESTORE",
                    "Account ${account.fullName} Restore.",
                    account.fullName,
                    logBuilder.toString()
                )
                return@withContext RestoreResult.Failure("SSAID konnte nicht wiederhergestellt werden")
            } else {
                appendStatusLine(logBuilder, "4. IDs werden korrigiert", "Erfolg")
            }

            // Step 5: Restore permissions
            val permissionsResult = runCatching {
                permissionManager.setFileOwnership(
                    MGO_FILES_PATH,
                    account.fileOwner,
                    account.fileGroup
                ).getOrThrow()

                permissionManager.setFileOwnership(
                    MGO_PREFS_PATH,
                    account.fileOwner,
                    account.fileGroup
                ).getOrThrow()

                permissionManager.setFilePermissions(MGO_FILES_PATH, account.filePermissions).getOrThrow()
                permissionManager.setFilePermissions(MGO_PREFS_PATH, account.filePermissions).getOrThrow()
            }

            if (permissionsResult.isFailure) {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "5. Berechtigungen werden korrigiert",
                    "Fehler",
                    "Berechtigungen setzen fehlgeschlagen: ${sanitizeMessage(permissionsResult.exceptionOrNull())}"
                )
                logRepository.addLog(
                    logLevel,
                    "RESTORE",
                    "Account ${account.fullName} Restore.",
                    account.fullName,
                    logBuilder.toString()
                )
                return@withContext RestoreResult.Failure("Berechtigungen konnten nicht gesetzt werden")
            } else {
                appendStatusLine(logBuilder, "5. Berechtigungen werden korrigiert", "Erfolg")
            }

            // Step 6: Start Monopoly Go (optional)
            if (startMonopolyGo) {
                val launchResult = rootUtil.launchMonopolyGo()
                if (launchResult.isFailure) {
                    logLevel = "ERROR"
                    appendStatusLine(
                        logBuilder,
                        "6. Monopoly Go starten ..",
                        "Fehler",
                        sanitizeMessage(launchResult.exceptionOrNull())
                    )
                    logRepository.addLog(
                        logLevel,
                        "RESTORE",
                        "Account ${account.fullName} Restore.",
                        account.fullName,
                        logBuilder.toString()
                    )
                    return@withContext RestoreResult.Failure("Monopoly GO konnte nicht gestartet werden")
                } else {
                    appendStatusLine(logBuilder, "6. Monopoly Go starten ..", "Erfolg")
                }
            } else {
                appendStatusLine(logBuilder, "6. Monopoly Go starten ..", "Userabbruch", "User hat Vorgang abgebrochen")
            }

            // Step 7: Update lastPlayedAt timestamp
            accountRepository.updateLastPlayedTimestamp(accountId)

            logRepository.addLog(
                logLevel,
                "RESTORE",
                "Account ${account.fullName} Restore.",
                account.fullName,
                logBuilder.toString()
            )
            RestoreResult.Success(account.fullName)

        } catch (e: Exception) {
            logLevel = "ERROR"
            logBuilder.appendLine()
            appendStatusLine(
                logBuilder,
                "Restore fehlgeschlagen",
                "Fehler",
                "Exception: ${sanitizeMessage(e)}"
            )
            logRepository.addLog(
                logLevel,
                "RESTORE",
                "Account ${accountNameForLog ?: "Unbekannt"} Restore.",
                accountNameForLog,
                logBuilder.toString()
            )
            RestoreResult.Failure("Restore fehlgeschlagen: ${e.message}", e)
        }
    }

    private suspend fun copyBackupDirectory(source: String, destination: String): Result<Unit> {
        // Use cp -rT to copy source TO destination (not INTO destination)
        // This prevents nested folder issues like shared_prefs/shared_prefs
        return rootUtil.executeCommand("cp -rT \"$source\" \"$destination\"").map { }
    }

    private fun appendStatusLine(
        builder: StringBuilder,
        line: String,
        status: String,
        errorDetail: String? = null
    ) {
        builder.appendLine("$line [$status]")
        if (status == "Fehler" || status == "Userabbruch") {
            builder.appendLine("Fehlerdetails: ${errorDetail ?: "Unbekannter Fehler"}")
        }
    }

    private fun resolveRootError(exception: Throwable?): String {
        val message = sanitizeMessage(exception).lowercase()
        return when {
            message.contains("timeout") -> "Timeout / Magisk Dialog nicht bestätigt"
            message.contains("root") -> "Fehlende Rootrechte"
            else -> "Exception: ${sanitizeMessage(exception)}"
        }
    }

    private fun resolveCopyError(source: String, destination: String, exception: Throwable?): String {
        val message = sanitizeMessage(exception)
        return "Kopieren fehlgeschlagen: $source -> $destination (Permission/IO Fehler: $message)"
    }

    private fun sanitizeMessage(exception: Throwable?): String {
        return exception?.message?.lineSequence()?.firstOrNull()?.ifBlank { "Unbekannter Fehler" } ?: "Unbekannter Fehler"
    }
}

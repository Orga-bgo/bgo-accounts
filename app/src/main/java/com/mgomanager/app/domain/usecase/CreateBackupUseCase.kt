package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FilePermissionManager
import com.mgomanager.app.domain.util.FilePermissions
import com.mgomanager.app.domain.util.IdExtractor
import com.mgomanager.app.domain.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

data class BackupRequest(
    val accountName: String,
    val prefix: String,
    val backupRootPath: String,
    val hasFacebookLink: Boolean,
    val fbUsername: String? = null,
    val fbPassword: String? = null,
    val fb2FA: String? = null,
    val fbTempMail: String? = null
)

class CreateBackupUseCase @Inject constructor(
    private val rootUtil: RootUtil,
    private val idExtractor: IdExtractor,
    private val permissionManager: FilePermissionManager,
    private val accountRepository: AccountRepository,
    private val logRepository: LogRepository
) {

    companion object {
        const val MGO_DATA_PATH = "/data/data/com.scopely.monopolygo"
        const val MGO_FILES_PATH = "$MGO_DATA_PATH/files/DiskBasedCacheDirectory"
        const val MGO_PREFS_PATH = "$MGO_DATA_PATH/shared_prefs"
        const val SSAID_PATH = "/data/system/users/0/settings_ssaid.xml"
        const val PLAYER_PREFS_FILE = "com.scopely.monopolygo.v2.playerprefs.xml"
    }

    suspend fun execute(request: BackupRequest, forceDuplicate: Boolean = false): BackupResult = withContext(Dispatchers.IO) {
        val correlationId = UUID.randomUUID().toString()
        val sessionId = logRepository.getCurrentSessionId()
        val logBuilder = StringBuilder()
        var logLevel = "INFO"

        try {
            logBuilder.appendLine("Starte Backup von ${request.accountName}")
            logBuilder.appendLine("correlationId: $correlationId")
            logBuilder.appendLine("sessionId: $sessionId")
            logBuilder.appendLine()

            logBuilder.appendLine("1. Root-Zugriff prüfen ..")
            val rootReady = rootUtil.requestRootAccess()
            if (!rootReady) {
                logLevel = "ERROR"
                appendStatusLine(logBuilder, "Root-Zugriff vorhanden ..", "Fehler", "Fehlende Rootrechte")
                logRepository.addLog(
                    logLevel,
                    "BACKUP",
                    "Account ${request.accountName} Backup.",
                    request.accountName,
                    logBuilder.toString()
                )
                return@withContext BackupResult.Failure("Root-Zugriff nicht verfügbar")
            }
            appendStatusLine(logBuilder, "Root-Zugriff vorhanden ..", "Erfolg")

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
                    "BACKUP",
                    "Account ${request.accountName} Backup.",
                    request.accountName,
                    logBuilder.toString()
                )
                return@withContext BackupResult.Failure("Monopoly GO konnte nicht gestoppt werden")
            }
            appendStatusLine(logBuilder, "2. Stoppe Monopoly GO..", "Erfolg")

            logBuilder.appendLine()
            logBuilder.appendLine("3. Berechtigungen lesen ..")
            val permissionsResult = readFilePermissionsWithRetry(MGO_FILES_PATH)
            val permissions = permissionsResult.getOrElse {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "Berechtigungen gelesen ..",
                    "Fehler",
                    "Berechtigungen lesen fehlgeschlagen: ${sanitizeMessage(it)}"
                )
                logRepository.addLog(
                    logLevel,
                    "BACKUP",
                    "Account ${request.accountName} Backup.",
                    request.accountName,
                    logBuilder.toString()
                )
                return@withContext BackupResult.Failure("Berechtigungen konnten nicht gelesen werden", it as? Exception)
            }
            appendStatusLine(logBuilder, "Berechtigungen gelesen ..", "Erfolg")

            logBuilder.appendLine()
            logBuilder.appendLine("4. Account-Namen prüfen ..")
            val finalAccountName = findUniqueAccountName(request.accountName, request.prefix, request.backupRootPath)
            if (finalAccountName != request.accountName) {
                appendStatusLine(logBuilder, "Account-Name prüfen ..", "Warnung")
                logBuilder.appendLine("Neuer Account-Name: $finalAccountName")
            } else {
                appendStatusLine(logBuilder, "Account-Name prüfen ..", "Erfolg")
            }

            val normalizedRootPath = request.backupRootPath.trimEnd('/')
            val backupPath = "$normalizedRootPath/${request.prefix}$finalAccountName/"
            val createDirResult = rootUtil.executeCommand("mkdir -p \"$backupPath\"")
            if (createDirResult.isFailure) {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "5. Backup-Verzeichnis erstellen ..",
                    "Fehler",
                    "Backup-Verzeichnis konnte nicht erstellt werden: ${sanitizeMessage(createDirResult.exceptionOrNull())}"
                )
                logRepository.addLog(
                    logLevel,
                    "BACKUP",
                    "Account $finalAccountName Backup.",
                    finalAccountName,
                    logBuilder.toString()
                )
                return@withContext BackupResult.Failure("Backup-Verzeichnis konnte nicht erstellt werden: ${createDirResult.exceptionOrNull()?.message}")
            }
            appendStatusLine(logBuilder, "5. Backup-Verzeichnis erstellen ..", "Erfolg")

            logBuilder.appendLine()
            logBuilder.appendLine("6. Daten kopieren ..")
            val diskCopyResult = copyDirectory(MGO_FILES_PATH, "${backupPath}DiskBasedCacheDirectory/")
            if (diskCopyResult.isFailure) {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "Account Files kopieren (1/2)",
                    "Fehler",
                    resolveCopyError(MGO_FILES_PATH, "${backupPath}DiskBasedCacheDirectory/", diskCopyResult.exceptionOrNull())
                )
                logRepository.addLog(
                    logLevel,
                    "BACKUP",
                    "Account $finalAccountName Backup.",
                    finalAccountName,
                    logBuilder.toString()
                )
                return@withContext BackupResult.Failure("Verzeichnis konnte nicht kopiert werden")
            }
            appendStatusLine(logBuilder, "Account Files kopieren (1/2)", "Erfolg")

            val prefsCopyResult = copyDirectory(MGO_PREFS_PATH, "${backupPath}shared_prefs/")
            if (prefsCopyResult.isFailure) {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "Account Files kopieren (2/2)",
                    "Fehler",
                    resolveCopyError(MGO_PREFS_PATH, "${backupPath}shared_prefs/", prefsCopyResult.exceptionOrNull())
                )
                logRepository.addLog(
                    logLevel,
                    "BACKUP",
                    "Account $finalAccountName Backup.",
                    finalAccountName,
                    logBuilder.toString()
                )
                return@withContext BackupResult.Failure("Verzeichnis konnte nicht kopiert werden")
            }
            appendStatusLine(logBuilder, "Account Files kopieren (2/2)", "Erfolg")

            logBuilder.appendLine()
            logBuilder.appendLine("7. SSAID sichern ..")
            val copyResult = rootUtil.executeCommand("cp \"$SSAID_PATH\" \"${backupPath}settings_ssaid.xml\"")
            if (copyResult.isFailure) {
                appendStatusLine(logBuilder, "SSAID kopieren ..", "Warnung")
            } else {
                appendStatusLine(logBuilder, "SSAID kopieren ..", "Erfolg")
            }

            logBuilder.appendLine()
            logBuilder.appendLine("8. IDs extrahieren ..")
            val playerPrefsFile = File("${backupPath}shared_prefs/$PLAYER_PREFS_FILE")
            val extractedIds = idExtractor.extractIdsFromPlayerPrefs(playerPrefsFile).getOrElse {
                logLevel = "ERROR"
                appendStatusLine(
                    logBuilder,
                    "ID-Extraktion ..",
                    "Fehler",
                    "ID-Extraktion fehlgeschlagen: ${sanitizeMessage(it)}"
                )
                logRepository.addLog(
                    logLevel,
                    "BACKUP",
                    "Account $finalAccountName Backup.",
                    finalAccountName,
                    logBuilder.toString()
                )
                return@withContext BackupResult.Failure("User ID konnte nicht extrahiert werden (MANDATORY)", it as? Exception)
            }
            appendStatusLine(logBuilder, "ID-Extraktion ..", "Erfolg")

            logBuilder.appendLine()
            logBuilder.appendLine("9. Duplicate Check ..")
            if (!forceDuplicate) {
                val existingAccount = accountRepository.getAccountByUserId(extractedIds.userId)
                if (existingAccount != null) {
                    logLevel = "WARNING"
                    appendStatusLine(
                        logBuilder,
                        "Duplicate UserId prüfen ..",
                        "Fehler",
                        "Duplicate UserId: ${hashValue(extractedIds.userId)} besteht bereits als ${existingAccount.fullName}"
                    )
                    rootUtil.executeCommand("rm -rf \"$backupPath\"")
                    logRepository.addLog(
                        logLevel,
                        "BACKUP",
                        "Account $finalAccountName Backup.",
                        finalAccountName,
                        logBuilder.toString()
                    )
                    return@withContext BackupResult.DuplicateUserId(
                        userId = extractedIds.userId,
                        existingAccountName = existingAccount.fullName
                    )
                }
            }
            appendStatusLine(logBuilder, "Duplicate UserId prüfen ..", "Erfolg")

            logBuilder.appendLine()
            logBuilder.appendLine("10. SSAID extrahieren ..")
            val ssaidFile = File("${backupPath}settings_ssaid.xml")
            if (!ssaidFile.exists()) {
                logLevel = "ERROR"
                appendStatusLine(logBuilder, "SSAID vorhanden ..", "Fehler", "SSAID nicht vorhanden")
                rootUtil.executeCommand("rm -rf \"$backupPath\"")
                logRepository.addLog(
                    logLevel,
                    "BACKUP",
                    "Account $finalAccountName Backup.",
                    finalAccountName,
                    logBuilder.toString()
                )
                return@withContext BackupResult.Failure("SSAID-Datei konnte nicht kopiert werden (MANDATORY)")
            }
            val ssaid = idExtractor.extractSsaid(ssaidFile)
            if (ssaid == "nicht vorhanden" || ssaid.isBlank()) {
                logLevel = "ERROR"
                appendStatusLine(logBuilder, "SSAID extrahieren ..", "Fehler", "SSAID nicht vorhanden")
                rootUtil.executeCommand("rm -rf \"$backupPath\"")
                logRepository.addLog(
                    logLevel,
                    "BACKUP",
                    "Account $finalAccountName Backup.",
                    finalAccountName,
                    logBuilder.toString()
                )
                return@withContext BackupResult.Failure("SSAID konnte nicht extrahiert werden (MANDATORY)")
            }
            appendStatusLine(logBuilder, "SSAID extrahieren ..", "Erfolg")

            val now = System.currentTimeMillis()
            val account = Account(
                accountName = finalAccountName,
                prefix = request.prefix,
                createdAt = now,
                lastPlayedAt = now,
                userId = extractedIds.userId,
                gaid = extractedIds.gaid,
                deviceToken = extractedIds.deviceToken,
                appSetId = extractedIds.appSetId,
                ssaid = ssaid,
                hasFacebookLink = request.hasFacebookLink,
                fbUsername = request.fbUsername,
                fbPassword = request.fbPassword,
                fb2FA = request.fb2FA,
                fbTempMail = request.fbTempMail,
                backupPath = backupPath,
                fileOwner = permissions.owner,
                fileGroup = permissions.group,
                filePermissions = permissions.permissions
            )

            val insertedId = accountRepository.insertAccount(account)
            val accountWithId = account.copy(id = insertedId)

            logBuilder.appendLine()
            appendStatusLine(logBuilder, "11. Account in DB speichern ..", "Erfolg")

            val missingIds = mutableListOf<String>()
            if (extractedIds.gaid == "nicht vorhanden") missingIds.add("GAID")
            if (extractedIds.deviceToken == "nicht vorhanden") missingIds.add("Device Token")
            if (extractedIds.appSetId == "nicht vorhanden") missingIds.add("App Set ID")

            if (missingIds.isNotEmpty()) {
                logLevel = "WARNING"
                logBuilder.appendLine("Hinweis: Fehlende optionale IDs: ${missingIds.joinToString(", ")}")
                logRepository.addLog(
                    logLevel,
                    "BACKUP",
                    "Account $finalAccountName Backup.",
                    finalAccountName,
                    logBuilder.toString()
                )
                BackupResult.PartialSuccess(accountWithId, missingIds)
            } else {
                logRepository.addLog(
                    logLevel,
                    "BACKUP",
                    "Account $finalAccountName Backup.",
                    finalAccountName,
                    logBuilder.toString()
                )
                BackupResult.Success(accountWithId)
            }

        } catch (e: Exception) {
            logLevel = "ERROR"
            appendStatusLine(logBuilder, "Backup fehlgeschlagen", "Fehler", "Exception: ${sanitizeMessage(e)}")
            logRepository.addLog(
                logLevel,
                "BACKUP",
                "Account ${request.accountName} Backup.",
                request.accountName,
                logBuilder.toString()
            )
            BackupResult.Failure("Backup fehlgeschlagen: ${e.message}", e)
        }
    }

    private suspend fun copyDirectory(source: String, destination: String): Result<Unit> {
        return rootUtil.executeCommand("cp -r \"$source\" \"$destination\"").map { }
    }

    /**
     * Read file permissions with retry mechanism for root timing issues
     * Retries up to 3 times with exponential backoff (500ms, 1s, 2s)
     */
    private suspend fun readFilePermissionsWithRetry(
        path: String,
        maxRetries: Int = 3
    ): Result<FilePermissions> {
        var lastException: Throwable? = null

        for (attempt in 1..maxRetries) {
            val result = permissionManager.getFilePermissions(path)

            if (result.isSuccess) {
                return Result.success(result.getOrThrow())
            }

            lastException = result.exceptionOrNull()

            if (attempt < maxRetries) {
                // Exponential backoff: 500ms, 1s, 2s
                val delayMs = 500L * (1 shl (attempt - 1))
                kotlinx.coroutines.delay(delayMs)
            }
        }

        return Result.failure(lastException ?: Exception("Fehler beim Lesen der Berechtigungen"))
    }

    /**
     * Find a unique account name by checking DB and backup folder
     * If name exists, append _1, _2, etc. until unique
     */
    private suspend fun findUniqueAccountName(baseName: String, prefix: String, backupRootPath: String): String {
        var candidateName = baseName
        var suffix = 0

        while (true) {
            // Check if name exists in database
            val existingInDb = accountRepository.getAccountByName(candidateName)

            // Check if backup folder already exists
            val backupPath = "$backupRootPath$prefix$candidateName/"
            val folderExists = File(backupPath).exists()

            if (existingInDb == null && !folderExists) {
                return candidateName
            }

            // Name is taken, try with suffix
            suffix++
            candidateName = "${baseName}_$suffix"
        }
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

    private fun hashValue(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    private fun sanitizeMessage(exception: Throwable?): String {
        return exception?.message?.lineSequence()?.firstOrNull()?.ifBlank { "Unbekannter Fehler" } ?: "Unbekannter Fehler"
    }
}

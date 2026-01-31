package com.mgomanager.app.domain.usecase

import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FilePermissionManager
import com.mgomanager.app.domain.util.IdExtractor
import com.mgomanager.app.domain.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
        try {
            logRepository.logInfo("BACKUP", "Starte Backup für ${request.accountName}")

            // Step 1: Force stop Monopoly Go
            rootUtil.forceStopMonopolyGo().getOrThrow()
            logRepository.logInfo("BACKUP", "Monopoly Go gestoppt", request.accountName)

            // Step 2: Read file permissions
            val permissions = permissionManager.getFilePermissions(MGO_FILES_PATH).getOrElse {
                logRepository.logError("BACKUP", "Fehler beim Lesen der Berechtigungen", request.accountName, it as? Exception)
                throw it
            }

            // Step 2.5: Handle account name duplicates (auto-rename with _1, _2, etc.)
            val finalAccountName = findUniqueAccountName(request.accountName, request.prefix, request.backupRootPath)
            if (finalAccountName != request.accountName) {
                logRepository.logInfo("BACKUP", "Account-Name umbenannt: ${request.accountName} -> $finalAccountName")
            }

            // Step 3: Create backup directory
            val backupPath = "${request.backupRootPath}${request.prefix}$finalAccountName/"
            val backupDir = File(backupPath)

            val createDirResult = rootUtil.executeCommand("mkdir -p $backupPath")
            if (createDirResult.isFailure) {
                val errorMsg = createDirResult.exceptionOrNull()?.message ?: "Unknown error"
                throw Exception("Backup-Verzeichnis konnte nicht erstellt werden: $errorMsg")
            }
            logRepository.logInfo("BACKUP", "Backup-Verzeichnis erstellt: $backupPath", request.accountName)

            // Step 4: Copy directories
            copyDirectory(MGO_FILES_PATH, "$backupPath/DiskBasedCacheDirectory/", request.accountName)
            copyDirectory(MGO_PREFS_PATH, "$backupPath/shared_prefs/", request.accountName)

            // Step 5: Copy SSAID file
            val copyResult = rootUtil.executeCommand("cp $SSAID_PATH $backupPath/settings_ssaid.xml")
            if (copyResult.isFailure) {
                logRepository.logWarning("BACKUP", "SSAID-Datei konnte nicht kopiert werden", request.accountName)
            }

            // Step 6: Extract IDs
            val playerPrefsFile = File("$backupPath/shared_prefs/$PLAYER_PREFS_FILE")
            val extractedIds = idExtractor.extractIdsFromPlayerPrefs(playerPrefsFile).getOrElse {
                logRepository.logError("BACKUP", "ID-Extraktion fehlgeschlagen", request.accountName, it as? Exception)
                throw Exception("User ID konnte nicht extrahiert werden (MANDATORY)")
            }

            // Step 6.5: Check for duplicate User ID (unless force flag is set)
            if (!forceDuplicate) {
                val existingAccount = accountRepository.getAccountByUserId(extractedIds.userId)
                if (existingAccount != null) {
                    logRepository.logWarning("BACKUP", "Duplicate User ID found: ${extractedIds.userId} exists as ${existingAccount.fullName}", finalAccountName)
                    // Clean up the backup directory we just created
                    rootUtil.executeCommand("rm -rf $backupPath")
                    return@withContext BackupResult.DuplicateUserId(
                        userId = extractedIds.userId,
                        existingAccountName = existingAccount.fullName
                    )
                }
            }

            // Step 7: Extract SSAID (MANDATORY per P3 spec)
            val ssaidFile = File("$backupPath/settings_ssaid.xml")
            if (!ssaidFile.exists()) {
                logRepository.logError("BACKUP", "SSAID-Datei nicht vorhanden (MANDATORY)", finalAccountName)
                rootUtil.executeCommand("rm -rf $backupPath")
                throw Exception("SSAID-Datei konnte nicht kopiert werden (MANDATORY)")
            }
            val ssaid = idExtractor.extractSsaid(ssaidFile)
            if (ssaid == "nicht vorhanden" || ssaid.isBlank()) {
                logRepository.logError("BACKUP", "SSAID konnte nicht extrahiert werden (MANDATORY)", finalAccountName)
                rootUtil.executeCommand("rm -rf $backupPath")
                throw Exception("SSAID konnte nicht extrahiert werden (MANDATORY)")
            }

            // Step 8: Create Account object
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

            // Step 9: Save to database
            accountRepository.insertAccount(account)

            logRepository.logInfo(
                "BACKUP",
                "Backup erfolgreich abgeschlossen für $finalAccountName",
                finalAccountName
            )

            // Check if any optional IDs are missing (userId and SSAID are mandatory)
            val missingIds = mutableListOf<String>()
            if (extractedIds.gaid == "nicht vorhanden") missingIds.add("GAID")
            if (extractedIds.deviceToken == "nicht vorhanden") missingIds.add("Device Token")
            if (extractedIds.appSetId == "nicht vorhanden") missingIds.add("App Set ID")
            // Note: SSAID is mandatory and would have caused abort above if missing

            if (missingIds.isNotEmpty()) {
                BackupResult.PartialSuccess(account, missingIds)
            } else {
                BackupResult.Success(account)
            }

        } catch (e: Exception) {
            logRepository.logError("BACKUP", "Backup fehlgeschlagen: ${e.message}", request.accountName, e)
            BackupResult.Failure("Backup fehlgeschlagen: ${e.message}", e)
        }
    }

    private suspend fun copyDirectory(source: String, destination: String, accountName: String) {
        val result = rootUtil.executeCommand("cp -r $source $destination")
        if (result.isSuccess) {
            logRepository.logInfo("BACKUP", "Verzeichnis kopiert: $source -> $destination", accountName)
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
            logRepository.logError("BACKUP", "Fehler beim Kopieren: $source - $errorMsg", accountName)
            throw Exception("Verzeichnis konnte nicht kopiert werden: $source - $errorMsg")
        }
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
}

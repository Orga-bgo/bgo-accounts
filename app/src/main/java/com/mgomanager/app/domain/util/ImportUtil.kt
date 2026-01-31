package com.mgomanager.app.domain.util

import android.content.Context
import com.mgomanager.app.data.local.database.entities.AccountEntity
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class for import account JSON format
 */
@Serializable
data class ImportAccountData(
    val id: Long = 0,
    val accountName: String,
    val prefix: String = "",
    val createdAt: Long,
    val lastPlayedAt: Long,
    val userId: String,
    val gaid: String = "nicht vorhanden",
    val deviceToken: String = "nicht vorhanden",
    val appSetId: String = "nicht vorhanden",
    val ssaid: String = "nicht vorhanden",
    val susLevelValue: Int = 0,
    val hasError: Boolean = false,
    val hasFacebookLink: Boolean = false,
    val fbUsername: String? = null,
    val fbPassword: String? = null,
    val fb2FA: String? = null,
    val fbTempMail: String? = null,
    val backupPath: String,
    val fileOwner: String = "",
    val fileGroup: String = "",
    val filePermissions: String = ""
)

/**
 * Result of an import operation
 */
data class ImportResult(
    val success: Boolean,
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
    val errorMessage: String? = null,
    val skippedAccounts: List<String> = emptyList()
)

/**
 * Utility class for importing accounts from ZIP files
 */
@Singleton
class ImportUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val appStateRepository: AppStateRepository,
    private val logRepository: LogRepository
) {
    companion object {
        const val IMPORT_ZIP_PATH = "/storage/emulated/0/Download/bgo_accounts_import.zip"
        const val ACCOUNTS_JSON_FILENAME = "accounts.json"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Check if an import ZIP file exists at the default location
     */
    suspend fun checkImportZipExists(): Boolean = withContext(Dispatchers.IO) {
        try {
            File(IMPORT_ZIP_PATH).exists()
        } catch (e: Exception) {
            Timber.e(e, "Error checking import ZIP existence")
            false
        }
    }

    /**
     * Import accounts from a ZIP file
     * @param zipPath Path to the ZIP file
     * @param backupDirectory Target directory for backup data
     * @return ImportResult with details about the import
     */
    suspend fun importFromZip(
        zipPath: String = IMPORT_ZIP_PATH,
        backupDirectory: String? = null
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val zipFile = File(zipPath)
            if (!zipFile.exists()) {
                return@withContext Result.failure(Exception("ZIP-Datei nicht gefunden: $zipPath"))
            }

            // Get backup directory from repository if not provided
            val targetBackupDir = backupDirectory
                ?: appStateRepository.getBackupDirectory()
                ?: "/storage/emulated/0/bgo_backups/"

            // Create temp directory for extraction
            val tempDir = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            var accountsJson: String? = null
            val extractedBackupDirs = mutableListOf<String>()

            // Extract ZIP contents
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(tempDir, entry.name)

                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                        extractedBackupDirs.add(entry.name.trimEnd('/'))
                    } else {
                        entryFile.parentFile?.mkdirs()
                        entryFile.outputStream().use { output ->
                            zis.copyTo(output)
                        }

                        // Check if this is the accounts.json file
                        if (entry.name == ACCOUNTS_JSON_FILENAME ||
                            entry.name.endsWith("/$ACCOUNTS_JSON_FILENAME")) {
                            accountsJson = entryFile.readText()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Parse accounts.json
            if (accountsJson == null) {
                tempDir.deleteRecursively()
                return@withContext Result.failure(Exception("accounts.json nicht gefunden im ZIP"))
            }

            val importAccounts = try {
                json.decodeFromString<List<ImportAccountData>>(accountsJson!!)
            } catch (e: Exception) {
                tempDir.deleteRecursively()
                Timber.e(e, "Error parsing accounts.json")
                return@withContext Result.failure(Exception("Fehler beim Parsen von accounts.json: ${e.message}"))
            }

            var importedCount = 0
            var skippedCount = 0
            val skippedAccounts = mutableListOf<String>()

            // Process each account
            for (importAccount in importAccounts) {
                // Check for UserID collision
                val existingByUserId = accountRepository.getAccountByUserId(importAccount.userId)
                if (existingByUserId != null) {
                    // Skip this account - UserID already exists
                    skippedCount++
                    skippedAccounts.add("${importAccount.accountName} (UserID bereits vorhanden)")
                    logRepository.logWarning(
                        "IMPORT",
                        "Account '${importAccount.accountName}' übersprungen: UserID ${importAccount.userId} existiert bereits"
                    )
                    continue
                }

                // Handle name collision with suffix
                var finalName = importAccount.accountName
                var suffix = 0
                while (accountRepository.getAccountByName(finalName) != null) {
                    suffix++
                    finalName = "${importAccount.accountName}_$suffix"
                }

                // Determine new backup path
                val newBackupPath = "$targetBackupDir/$finalName"

                // Copy backup data if exists
                val sourceBackupDir = File(tempDir, importAccount.backupPath.substringAfterLast("/"))
                if (sourceBackupDir.exists() && sourceBackupDir.isDirectory) {
                    val targetDir = File(newBackupPath)
                    targetDir.mkdirs()
                    sourceBackupDir.copyRecursively(targetDir, overwrite = true)
                }

                // Create account entity
                val accountEntity = AccountEntity(
                    id = 0, // Auto-generate new ID
                    accountName = finalName,
                    prefix = importAccount.prefix,
                    createdAt = importAccount.createdAt,
                    lastPlayedAt = importAccount.lastPlayedAt,
                    userId = importAccount.userId,
                    gaid = importAccount.gaid,
                    deviceToken = importAccount.deviceToken,
                    appSetId = importAccount.appSetId,
                    ssaid = importAccount.ssaid,
                    susLevelValue = importAccount.susLevelValue,
                    hasError = importAccount.hasError,
                    hasFacebookLink = importAccount.hasFacebookLink,
                    fbUsername = importAccount.fbUsername,
                    fbPassword = importAccount.fbPassword,
                    fb2FA = importAccount.fb2FA,
                    fbTempMail = importAccount.fbTempMail,
                    backupPath = newBackupPath,
                    fileOwner = importAccount.fileOwner,
                    fileGroup = importAccount.fileGroup,
                    filePermissions = importAccount.filePermissions
                )

                // Insert into database
                accountRepository.insertAccountEntity(accountEntity)
                importedCount++

                logRepository.logInfo(
                    "IMPORT",
                    "Account '$finalName' erfolgreich importiert"
                )
            }

            // Cleanup temp directory
            tempDir.deleteRecursively()

            logRepository.logInfo(
                "IMPORT",
                "Import abgeschlossen: $importedCount importiert, $skippedCount übersprungen"
            )

            Result.success(
                ImportResult(
                    success = true,
                    importedCount = importedCount,
                    skippedCount = skippedCount,
                    skippedAccounts = skippedAccounts
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "Error importing from ZIP")
            logRepository.logError(
                "IMPORT",
                "Import fehlgeschlagen: ${e.message}"
            )
            Result.failure(e)
        }
    }

    /**
     * Delete the import ZIP file after successful import
     */
    suspend fun deleteImportZip(zipPath: String = IMPORT_ZIP_PATH): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(zipPath)
            if (file.exists()) {
                file.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting import ZIP")
            Result.failure(e)
        }
    }
}

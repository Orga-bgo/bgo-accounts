package com.mgomanager.app.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mgomanager.app.data.local.database.entities.AccountEntity
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.ui.components.DuplicatePair
import com.mgomanager.app.ui.components.ResolveChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of the import preparation phase
 */
sealed class ImportPrepareResult {
    /**
     * Import can proceed without conflicts
     */
    data class Ready(
        val accountsToImport: List<ImportAccountData>,
        val tempDir: File
    ) : ImportPrepareResult()

    /**
     * There are UserID conflicts that need resolution
     * DEVIATION FROM P6: Instead of automatically skipping, we ALWAYS show this
     */
    data class NeedsResolution(
        val duplicates: List<DuplicatePair>,
        val accountsToImport: List<ImportAccountData>,
        val tempDir: File
    ) : ImportPrepareResult()

    /**
     * Import failed during preparation
     */
    data class Error(val message: String) : ImportPrepareResult()
}

/**
 * Result of the import apply phase
 */
data class ImportApplyResult(
    val importedCount: Int,
    val skippedCount: Int,
    val archivedCount: Int,
    val errors: List<String>
)

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
 * Use case for importing backups from ZIP files with interactive conflict resolution
 *
 * IMPORTANT DEVIATION FROM P6 SPEC:
 * The original P6 spec states: "UserID-Kollision → Import dieses Accounts überspringen + Log(ERROR)"
 *
 * This implementation ALWAYS shows an interactive dialog for UserID conflicts, giving users
 * the choice to:
 * 1. Keep the local account (skip import of this account)
 * 2. Keep the imported account (archive local, import new)
 *
 * Rationale:
 * - Better UX: Users have full control over their data
 * - Prevents accidental data loss
 * - Archive strategy: Local data is never destroyed, just moved to archive
 */
@Singleton
class ImportBackupsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val appStateRepository: AppStateRepository,
    private val logRepository: LogRepository
) {
    companion object {
        const val ACCOUNTS_JSON_FILENAME = "accounts.json"
        const val ARCHIVE_DIR_NAME = "archive"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Phase 1: Prepare import by extracting ZIP and detecting conflicts
     * This does NOT modify any data
     *
     * @param zipUri URI of the ZIP file to import (from SAF)
     * @return ImportPrepareResult indicating ready, needs resolution, or error
     */
    suspend fun prepareImport(zipUri: Uri): ImportPrepareResult = withContext(Dispatchers.IO) {
        try {
            logRepository.logInfo("IMPORT", "Bereite Import vor: $zipUri")

            // Create temp directory for extraction
            val tempDir = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            var accountsJson: String? = null

            // Extract ZIP contents using ContentResolver for SAF
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryFile = File(tempDir, entry.name)

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            entryFile.outputStream().use { output ->
                                zis.copyTo(output)
                            }

                            // Check if this is the accounts.json file
                            if (entry.name == ACCOUNTS_JSON_FILENAME ||
                                entry.name.endsWith("/$ACCOUNTS_JSON_FILENAME")
                            ) {
                                accountsJson = entryFile.readText()
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: run {
                tempDir.deleteRecursively()
                logRepository.logError("IMPORT", "Konnte ZIP nicht öffnen")
                return@withContext ImportPrepareResult.Error("Konnte ZIP nicht öffnen")
            }

            // Parse accounts.json
            if (accountsJson == null) {
                tempDir.deleteRecursively()
                logRepository.logError("IMPORT", "accounts.json nicht gefunden")
                return@withContext ImportPrepareResult.Error("accounts.json nicht gefunden im ZIP")
            }

            val importAccounts = try {
                json.decodeFromString<List<ImportAccountData>>(accountsJson!!)
            } catch (e: Exception) {
                tempDir.deleteRecursively()
                Timber.e(e, "Error parsing accounts.json")
                logRepository.logError("IMPORT", "Fehler beim Parsen: ${e.message}")
                return@withContext ImportPrepareResult.Error("Fehler beim Parsen von accounts.json")
            }

            logRepository.logInfo("IMPORT", "${importAccounts.size} Accounts im ZIP gefunden")

            // Check for UserID conflicts
            val duplicates = mutableListOf<DuplicatePair>()
            for (importAccount in importAccounts) {
                val existingAccount = accountRepository.getAccountByUserId(importAccount.userId)
                if (existingAccount != null) {
                    duplicates.add(
                        DuplicatePair(
                            userId = importAccount.userId,
                            localName = existingAccount.fullName,
                            localCreatedAt = existingAccount.createdAt,
                            importName = importAccount.accountName,
                            importCreatedAt = importAccount.createdAt
                        )
                    )
                }
            }

            if (duplicates.isNotEmpty()) {
                logRepository.logInfo(
                    "IMPORT",
                    "${duplicates.size} UserID-Kollisionen gefunden - warte auf Nutzerentscheidung"
                )
                ImportPrepareResult.NeedsResolution(
                    duplicates = duplicates,
                    accountsToImport = importAccounts,
                    tempDir = tempDir
                )
            } else {
                ImportPrepareResult.Ready(
                    accountsToImport = importAccounts,
                    tempDir = tempDir
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "Error preparing import")
            logRepository.logError("IMPORT", "Vorbereitung fehlgeschlagen: ${e.message}")
            ImportPrepareResult.Error("Import-Vorbereitung fehlgeschlagen: ${e.message}")
        }
    }

    /**
     * Phase 2: Apply import with user decisions for conflicts
     *
     * @param accountsToImport List of accounts from the ZIP
     * @param tempDir Temporary directory with extracted data
     * @param decisions Map of userId to ResolveChoice for conflicts
     * @return ImportApplyResult with counts of imported, skipped, archived
     */
    suspend fun applyImport(
        accountsToImport: List<ImportAccountData>,
        tempDir: File,
        decisions: Map<String, ResolveChoice> = emptyMap()
    ): ImportApplyResult = withContext(Dispatchers.IO) {
        var importedCount = 0
        var skippedCount = 0
        var archivedCount = 0
        val errors = mutableListOf<String>()

        try {
            // Get backup directory
            val backupDirectory = appStateRepository.getBackupDirectory()
                ?: "/storage/emulated/0/mgo/backups/"

            // Create archive directory if needed
            val archiveDir = File(backupDirectory, ARCHIVE_DIR_NAME)

            for (importAccount in accountsToImport) {
                try {
                    // Check for UserID collision
                    val existingAccount = accountRepository.getAccountByUserId(importAccount.userId)

                    if (existingAccount != null) {
                        // We have a collision - check user decision
                        val decision = decisions[importAccount.userId] ?: ResolveChoice.KEEP_LOCAL

                        when (decision) {
                            ResolveChoice.KEEP_LOCAL -> {
                                // Skip this import
                                skippedCount++
                                logRepository.logInfo(
                                    "IMPORT",
                                    "Account '${importAccount.accountName}' übersprungen (Nutzerentscheidung: Lokal behalten)"
                                )
                                continue
                            }
                            ResolveChoice.KEEP_IMPORT -> {
                                // Archive local and import new
                                val archived = archiveLocalAccount(
                                    existingAccount.id,
                                    existingAccount.fullName,
                                    existingAccount.backupPath,
                                    archiveDir
                                )
                                if (archived) {
                                    archivedCount++
                                    logRepository.logInfo(
                                        "IMPORT",
                                        "Lokaler Account '${existingAccount.fullName}' archiviert"
                                    )
                                    // Delete old DB entry
                                    accountRepository.deleteAccountById(existingAccount.id)
                                } else {
                                    errors.add("Archivierung von '${existingAccount.fullName}' fehlgeschlagen")
                                    skippedCount++
                                    continue
                                }
                            }
                        }
                    }

                    // Handle name collision with suffix
                    var finalName = importAccount.accountName
                    var suffix = 0
                    while (accountRepository.getAccountByName(finalName) != null) {
                        suffix++
                        finalName = "${importAccount.accountName}_$suffix"
                    }

                    // Determine new backup path
                    val newBackupPath = "$backupDirectory/$finalName"

                    // Copy backup data if exists
                    val sourceBackupDir = findBackupDir(tempDir, importAccount.backupPath)
                    if (sourceBackupDir != null && sourceBackupDir.exists() && sourceBackupDir.isDirectory) {
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

                    logRepository.logInfo("IMPORT", "Account '$finalName' erfolgreich importiert")

                } catch (e: Exception) {
                    Timber.e(e, "Error importing account ${importAccount.accountName}")
                    errors.add("${importAccount.accountName}: ${e.message}")
                    logRepository.logError("IMPORT", "Fehler bei '${importAccount.accountName}': ${e.message}")
                }
            }

            // Cleanup temp directory
            tempDir.deleteRecursively()

            logRepository.logInfo(
                "IMPORT",
                "Import abgeschlossen: $importedCount importiert, $skippedCount übersprungen, $archivedCount archiviert"
            )

        } catch (e: Exception) {
            Timber.e(e, "Error applying import")
            logRepository.logError("IMPORT", "Import fehlgeschlagen: ${e.message}")
            errors.add("Allgemeiner Fehler: ${e.message}")
            tempDir.deleteRecursively()
        }

        ImportApplyResult(
            importedCount = importedCount,
            skippedCount = skippedCount,
            archivedCount = archivedCount,
            errors = errors
        )
    }

    /**
     * Archive a local account's backup data (non-destructive)
     * Moves the backup folder to archive/<name>_<timestamp>/
     *
     * @return true if archiving succeeded
     */
    private suspend fun archiveLocalAccount(
        accountId: Long,
        accountName: String,
        backupPath: String,
        archiveDir: File
    ): Boolean {
        return try {
            val sourceDir = File(backupPath)
            if (!sourceDir.exists()) {
                // No backup data to archive, that's OK
                return true
            }

            // Create archive directory
            archiveDir.mkdirs()

            // Create timestamped archive folder
            val timestamp = timestampFormat.format(Date())
            val archiveName = "${accountName}_$timestamp"
            val archiveTarget = File(archiveDir, archiveName)

            // Move (copy then delete) the backup data
            sourceDir.copyRecursively(archiveTarget, overwrite = false)
            sourceDir.deleteRecursively()

            logRepository.logInfo(
                "IMPORT",
                "Backup archiviert: $archiveName"
            )
            true
        } catch (e: Exception) {
            Timber.e(e, "Error archiving account $accountName")
            logRepository.logError("IMPORT", "Archivierung fehlgeschlagen: ${e.message}")
            false
        }
    }

    /**
     * Find the backup directory in the temp extraction folder
     */
    private fun findBackupDir(tempDir: File, originalPath: String): File? {
        // Try to find by the last path segment
        val dirName = originalPath.trimEnd('/').substringAfterLast("/")

        // Check direct match
        val direct = File(tempDir, dirName)
        if (direct.exists() && direct.isDirectory) {
            return direct
        }

        // Check in backups subfolder
        val inBackups = File(tempDir, "backups/$dirName")
        if (inBackups.exists() && inBackups.isDirectory) {
            return inBackups
        }

        // Search recursively
        tempDir.walkTopDown()
            .filter { it.isDirectory && it.name == dirName }
            .firstOrNull()
            ?.let { return it }

        return null
    }

    /**
     * Cancel an in-progress import (cleanup temp directory)
     */
    suspend fun cancelImport(tempDir: File) = withContext(Dispatchers.IO) {
        logRepository.logInfo("IMPORT", "Import abgebrochen durch Nutzer")
        tempDir.deleteRecursively()
    }
}

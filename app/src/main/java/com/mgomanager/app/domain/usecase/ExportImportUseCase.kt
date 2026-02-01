package com.mgomanager.app.domain.usecase

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.mgomanager.app.data.local.database.entities.AccountEntity
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.SSHSyncService
import com.mgomanager.app.domain.util.SSHOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class ExportImportUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val settingsDataStore: SettingsDataStore,
    private val logRepository: LogRepository,
    private val sshSyncService: SSHSyncService
) {

    companion object {
        const val EXPORT_DIR = "/storage/emulated/0/mgo/exports/"
        const val DB_FOLDER = "database"
        const val BACKUPS_FOLDER = "backups"
        const val ACCOUNTS_JSON = "accounts.json"
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Export database and all backup files to a zip file
     * @return Path to the created zip file
     */
    suspend fun exportData(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            logRepository.logInfo("EXPORT", "Starting data export")

            // Create export directory
            val exportDir = File(EXPORT_DIR)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            // Generate filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFile = File(exportDir, "mgo_export_$timestamp.zip")

            // Get backup path from settings
            val backupPath = settingsDataStore.backupRootPath.first()

            // Get database path
            val dbPath = context.getDatabasePath("mgo_database").absolutePath

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // Add database file
                val dbFile = File(dbPath)
                if (dbFile.exists()) {
                    addFileToZip(zos, dbFile, "$DB_FOLDER/${dbFile.name}")
                    logRepository.logInfo("EXPORT", "Database added to export")
                }

                // Add all backup directories
                val backupsDir = File(backupPath)
                if (backupsDir.exists() && backupsDir.isDirectory) {
                    backupsDir.listFiles()?.forEach { accountDir ->
                        if (accountDir.isDirectory) {
                            addDirectoryToZip(zos, accountDir, "$BACKUPS_FOLDER/${accountDir.name}")
                        }
                    }
                    logRepository.logInfo("EXPORT", "Backup files added to export")
                }
            }

            // Add accounts.json with metadata
            addAccountsJsonToZip(context, zos)

            logRepository.logInfo("EXPORT", "Export completed: ${zipFile.absolutePath}")

            // Auto-upload to SSH server if enabled
            val autoUpload = settingsDataStore.sshAutoUploadOnExport.first()
            val sshServer = settingsDataStore.sshServer.first()

            if (autoUpload && sshServer.isNotBlank()) {
                logRepository.logInfo("EXPORT", "Auto-uploading to SSH server...")
                val uploadResult = sshSyncService.uploadZip(zipFile.absolutePath)

                when (uploadResult) {
                    is SSHOperationResult.Success -> {
                        logRepository.logInfo("EXPORT", "Auto-upload successful")
                        Result.success("Export gespeichert und hochgeladen:\n${zipFile.absolutePath}")
                    }
                    is SSHOperationResult.Error -> {
                        logRepository.logError("EXPORT", "Auto-upload failed: ${uploadResult.message}")
                        Result.success("Export gespeichert (Upload fehlgeschlagen: ${uploadResult.message}):\n${zipFile.absolutePath}")
                    }
                }
            } else {
                Result.success("Export gespeichert unter:\n${zipFile.absolutePath}")
            }

        } catch (e: Exception) {
            logRepository.logError("EXPORT", "Export failed: ${e.message}", exception = e)
            Result.failure(e)
        }
    }

    /**
     * Import database and backup files from a zip file
     * Note: This looks for the most recent export file in the exports directory
     */
    suspend fun importData(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logRepository.logInfo("IMPORT", "Starting data import")

            // Find the most recent export file
            val exportDir = File(EXPORT_DIR)
            val zipFiles = exportDir.listFiles { file ->
                file.name.startsWith("mgo_export_") && file.name.endsWith(".zip")
            }?.sortedByDescending { it.lastModified() }

            if (zipFiles.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Keine Export-Datei gefunden in $EXPORT_DIR"))
            }

            val zipFile = zipFiles.first()
            logRepository.logInfo("IMPORT", "Importing from: ${zipFile.name}")

            // Get backup path from settings
            val backupPath = settingsDataStore.backupRootPath.first()

            // Get database path
            val dbPath = context.getDatabasePath("mgo_database").parentFile?.absolutePath
                ?: return@withContext Result.failure(Exception("Database path not found"))

            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name

                    when {
                        entryName.startsWith("$DB_FOLDER/") -> {
                            // Extract database file
                            val fileName = entryName.removePrefix("$DB_FOLDER/")
                            val destFile = File(dbPath, fileName)
                            extractFile(zis, destFile)
                            logRepository.logInfo("IMPORT", "Database restored: $fileName")
                        }
                        entryName.startsWith("$BACKUPS_FOLDER/") -> {
                            // Extract backup files
                            val relativePath = entryName.removePrefix("$BACKUPS_FOLDER/")
                            val destFile = File(backupPath, relativePath)
                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                destFile.parentFile?.mkdirs()
                                extractFile(zis, destFile)
                            }
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            logRepository.logInfo("IMPORT", "Import completed successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            logRepository.logError("IMPORT", "Import failed: ${e.message}", exception = e)
            Result.failure(e)
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            zos.putNextEntry(ZipEntry(entryName))
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }

    private fun addDirectoryToZip(zos: ZipOutputStream, dir: File, basePath: String) {
        dir.listFiles()?.forEach { file ->
            val entryPath = "$basePath/${file.name}"
            if (file.isDirectory) {
                addDirectoryToZip(zos, file, entryPath)
            } else {
                addFileToZip(zos, file, entryPath)
            }
        }
    }

    private fun extractFile(zis: ZipInputStream, destFile: File) {
        FileOutputStream(destFile).use { fos ->
            zis.copyTo(fos)
        }
    }

    /**
     * Export data to a SAF-selected URI (for CreateDocument result)
     */
    suspend fun exportDataToUri(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logRepository.logInfo("EXPORT", "Starting SAF export to: $uri")

            // Get backup path from settings
            val backupPath = settingsDataStore.backupRootPath.first()

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    // Add database file
                    val dbFile = context.getDatabasePath("mgo_database")
                    if (dbFile.exists()) {
                        addFileToZip(zos, dbFile, "$DB_FOLDER/${dbFile.name}")
                        logRepository.logInfo("EXPORT", "Database added to export")
                    }

                    // Add all backup directories
                    val backupsDir = File(backupPath)
                    if (backupsDir.exists() && backupsDir.isDirectory) {
                        backupsDir.listFiles()?.forEach { accountDir ->
                            if (accountDir.isDirectory && accountDir.name != "archive") {
                                addDirectoryToZip(zos, accountDir, "$BACKUPS_FOLDER/${accountDir.name}")
                            }
                        }
                        logRepository.logInfo("EXPORT", "Backup files added to export")
                    }

                    // Add accounts.json with metadata
                    addAccountsJsonToZip(context, zos)
                }
            } ?: run {
                logRepository.logError("EXPORT", "Could not open output stream")
                return@withContext Result.failure(Exception("Konnte Output-Stream nicht öffnen"))
            }

            logRepository.logInfo("EXPORT", "SAF Export completed successfully")

            // Auto-upload to SSH server if enabled
            val autoUpload = settingsDataStore.sshAutoUploadOnExport.first()
            val sshServer = settingsDataStore.sshServer.first()

            if (autoUpload && sshServer.isNotBlank()) {
                logRepository.logInfo("EXPORT", "SAF export does not support auto-upload")
            }

            Result.success(Unit)

        } catch (e: Exception) {
            logRepository.logError("EXPORT", "SAF Export failed: ${e.message}", exception = e)
            Result.failure(e)
        }
    }

    /**
     * Add accounts.json to the ZIP with all account metadata
     */
    private suspend fun addAccountsJsonToZip(context: Context, zos: ZipOutputStream) {
        try {
            val accounts = accountRepository.getAllAccountsList()
            val accountDataList = accounts.map { account ->
                ImportAccountData(
                    id = account.id,
                    accountName = account.accountName,
                    prefix = account.prefix,
                    createdAt = account.createdAt,
                    lastPlayedAt = account.lastPlayedAt,
                    userId = account.userId,
                    gaid = account.gaid,
                    deviceToken = account.deviceToken,
                    appSetId = account.appSetId,
                    ssaid = account.ssaid,
                    susLevelValue = account.susLevel.value,
                    hasError = account.hasError,
                    hasFacebookLink = account.hasFacebookLink,
                    fbUsername = account.fbUsername,
                    fbPassword = account.fbPassword,
                    fb2FA = account.fb2FA,
                    fbTempMail = account.fbTempMail,
                    backupPath = account.backupPath,
                    fileOwner = account.fileOwner,
                    fileGroup = account.fileGroup,
                    filePermissions = account.filePermissions
                )
            }

            val jsonString = json.encodeToString(accountDataList)
            zos.putNextEntry(ZipEntry(ACCOUNTS_JSON))
            zos.write(jsonString.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            logRepository.logInfo("EXPORT", "accounts.json added with ${accountDataList.size} accounts")
        } catch (e: Exception) {
            logRepository.logError("EXPORT", "Failed to add accounts.json: ${e.message}")
        }
    }
}

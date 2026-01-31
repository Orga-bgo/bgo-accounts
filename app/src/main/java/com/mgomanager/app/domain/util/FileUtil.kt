package com.mgomanager.app.domain.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for file operations
 */
@Singleton
class FileUtil @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Check if a file exists at the given path
     */
    suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(path).exists()
        } catch (e: Exception) {
            Timber.e(e, "Error checking if file exists: $path")
            false
        }
    }

    /**
     * Check if a directory exists at the given path
     */
    suspend fun directoryExists(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            file.exists() && file.isDirectory
        } catch (e: Exception) {
            Timber.e(e, "Error checking if directory exists: $path")
            false
        }
    }

    /**
     * Create a directory at the given path (including parent directories)
     */
    suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = File(path)
            if (dir.exists()) {
                if (dir.isDirectory) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Path exists but is not a directory: $path"))
                }
            } else {
                if (dir.mkdirs()) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to create directory: $path"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error creating directory: $path")
            Result.failure(e)
        }
    }

    /**
     * Check if a directory is writable
     */
    suspend fun isDirectoryWritable(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(path)
            dir.exists() && dir.isDirectory && dir.canWrite()
        } catch (e: Exception) {
            Timber.e(e, "Error checking if directory is writable: $path")
            false
        }
    }

    /**
     * Check if a directory is readable
     */
    suspend fun isDirectoryReadable(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(path)
            dir.exists() && dir.isDirectory && dir.canRead()
        } catch (e: Exception) {
            Timber.e(e, "Error checking if directory is readable: $path")
            false
        }
    }

    /**
     * Delete a file or directory recursively
     */
    suspend fun deleteRecursively(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists()) {
                if (file.deleteRecursively()) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete: $path"))
                }
            } else {
                Result.success(Unit) // Already doesn't exist
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting: $path")
            Result.failure(e)
        }
    }

    /**
     * Copy a file from source to destination
     */
    suspend fun copyFile(sourcePath: String, destPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            val dest = File(destPath)

            if (!source.exists()) {
                return@withContext Result.failure(Exception("Source file does not exist: $sourcePath"))
            }

            // Create parent directories if needed
            dest.parentFile?.mkdirs()

            source.copyTo(dest, overwrite = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error copying file from $sourcePath to $destPath")
            Result.failure(e)
        }
    }

    /**
     * Copy a directory recursively from source to destination
     */
    suspend fun copyDirectory(sourcePath: String, destPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            val dest = File(destPath)

            if (!source.exists() || !source.isDirectory) {
                return@withContext Result.failure(Exception("Source directory does not exist: $sourcePath"))
            }

            source.copyRecursively(dest, overwrite = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error copying directory from $sourcePath to $destPath")
            Result.failure(e)
        }
    }

    /**
     * Read file content as string
     */
    suspend fun readFileAsString(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File does not exist: $path"))
            }
            Result.success(file.readText())
        } catch (e: Exception) {
            Timber.e(e, "Error reading file: $path")
            Result.failure(e)
        }
    }

    /**
     * Write string content to file
     */
    suspend fun writeStringToFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error writing to file: $path")
            Result.failure(e)
        }
    }

    /**
     * Get file size in bytes
     */
    suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        try {
            File(path).length()
        } catch (e: Exception) {
            Timber.e(e, "Error getting file size: $path")
            0L
        }
    }

    /**
     * List files in a directory
     */
    suspend fun listFiles(directoryPath: String): List<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(directoryPath)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error listing files in: $directoryPath")
            emptyList()
        }
    }

    /**
     * Get the app's cache directory
     */
    fun getCacheDirectory(): File = context.cacheDir

    /**
     * Get the app's files directory
     */
    fun getFilesDirectory(): File = context.filesDir

    /**
     * Get the external files directory
     */
    fun getExternalFilesDirectory(): File? = context.getExternalFilesDir(null)
}

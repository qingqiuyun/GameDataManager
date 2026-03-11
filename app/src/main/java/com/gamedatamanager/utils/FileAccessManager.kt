package com.gamedatamanager.utils

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 文件访问管理器
 * 支持通过文件提供器注入的方式访问 /data/data 目录
 */
class FileAccessManager(private val context: Context) {

    companion object {
        private const val TAG = "FileAccessManager"

        // 文件提供器授权
        private const val FILE_PROVIDER_AUTHORITY = "com.gamedatamanager.provider"
        
        // 游戏数据文件提供器授权
        private const val GAME_DATA_PROVIDER_AUTHORITY = "com.gamedatamanager.provider"
    }

    // 已注入的应用列表
    private val injectedApps = mutableSetOf<String>()

    /**
     * 设置已注入的应用
     */
    fun setInjectedApp(packageName: String) {
        injectedApps.add(packageName)
        Log.d(TAG, "App marked as injected: $packageName")
    }

    /**
     * 移除已注入的应用
     */
    fun removeInjectedApp(packageName: String) {
        injectedApps.remove(packageName)
        Log.d(TAG, "App removed from injected list: $packageName")
    }

    /**
     * 检查应用是否已注入
     */
    fun isAppInjected(packageName: String): Boolean {
        return injectedApps.contains(packageName)
    }

    /**
     * 检查是否有访问权限
     */
    fun hasAccessPermission(): Boolean {
        // 检查是否有 MANAGE_EXTERNAL_STORAGE 权限
        return context.checkSelfPermission(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * 获取应用的私有数据目录列表
     * @param packageName 应用包名
     * @return 文件路径列表
     */
    suspend fun getAppPrivateDataPaths(packageName: String): List<String> = withContext(Dispatchers.IO) {
        val paths = mutableListOf<String>()
        val dataPath = "/data/data/$packageName"

        // 如果应用已注入，通过ContentProvider访问
        if (isAppInjected(packageName)) {
            try {
                val uri = Uri.Builder()
                    .scheme("content")
                    .authority(GAME_DATA_PROVIDER_AUTHORITY)
                    .appendPath("data")
                    .appendPath("data")
                    .appendPath(packageName)
                    .build()

                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf("path", "is_directory"),
                    null,
                    null,
                    null
                )

                cursor?.use {
                    val pathIndex = it.getColumnIndex("path")
                    val isDirIndex = it.getColumnIndex("is_directory")

                    while (it.moveToNext()) {
                        val path = it.getString(pathIndex)
                        val isDir = it.getInt(isDirIndex) == 1
                        if (isDir) {
                            paths.add(path)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to access private data via ContentProvider", e)
            }
        } else if (hasAccessPermission()) {
            // 如果没有注入但有存储权限，直接访问
            val dataDir = File(dataPath)
            if (dataDir.exists() && dataDir.isDirectory) {
                dataDir.listFiles()?.forEach { file ->
                    paths.add(file.absolutePath)
                }
            }
        }

        paths
    }

    /**
     * 通过ContentProvider URI获取应用私有数据目录的文件列表
     * @param packageName 应用包名
     * @param relativePath 相对路径（空字符串表示根目录）
     * @return 文件信息列表
     */
    suspend fun getAppPrivateDataFiles(packageName: String, relativePath: String = ""): List<FileInfo> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileInfo>()

        if (!isAppInjected(packageName)) {
            Log.w(TAG, "App not injected, cannot access private data: $packageName")
            return@withContext files
        }

        try {
            val uriBuilder = Uri.Builder()
                .scheme("content")
                .authority(GAME_DATA_PROVIDER_AUTHORITY)
                .appendPath("data")
                .appendPath("data")
                .appendPath(packageName)

            if (relativePath.isNotEmpty()) {
                uriBuilder.appendPath(relativePath)
            }

            val uri = uriBuilder.build()

            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                    "path",
                    "is_directory",
                    "last_modified"
                ),
                null,
                null,
                null
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                val pathIndex = it.getColumnIndex("path")
                val isDirIndex = it.getColumnIndex("is_directory")
                val modifiedIndex = it.getColumnIndex("last_modified")

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex)
                    val size = it.getLong(sizeIndex)
                    val path = it.getString(pathIndex)
                    val isDir = it.getInt(isDirIndex) == 1
                    val modified = it.getLong(modifiedIndex)

                    files.add(
                        FileInfo(
                            name = name,
                            path = path,
                            size = size,
                            isDirectory = isDir,
                            lastModified = modified
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list files via ContentProvider", e)
        }

        files
    }

    /**
     * 获取应用的公共数据目录列表
     * @param packageName 应用包名
     * @return 文件路径列表
     */
    suspend fun getAppPublicDataPaths(packageName: String): List<String> = withContext(Dispatchers.IO) {
        val paths = mutableListOf<String>()
        val dataPath = "/storage/emulated/0/Android/data/$packageName"

        val dataDir = File(dataPath)
        if (dataDir.exists() && dataDir.isDirectory) {
            dataDir.listFiles()?.forEach { file ->
                paths.add(file.absolutePath)
            }
        }

        paths
    }

    /**
     * 读取文件内容
     * @param filePath 文件路径
     * @return 文件内容（字节数组）
     */
    suspend fun readFile(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // 检查是否为应用私有数据路径
            if (filePath.startsWith("/data/data/")) {
                val parts = filePath.split("/", limit = 4)
                if (parts.size >= 4) {
                    val packageName = parts[3]
                    if (isAppInjected(packageName)) {
                        return@withContext readFileViaProvider(packageName, filePath)
                    }
                }
            }

            // 普通文件读取
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
                file.readBytes()
            } else {
                Log.e(TAG, "无法读取文件: $filePath")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取文件失败: $filePath", e)
            null
        }
    }

    /**
     * 通过ContentProvider读取应用私有数据文件
     * @param packageName 应用包名
     * @param filePath 完整文件路径
     * @return 文件内容（字节数组）
     */
    private suspend fun readFileViaProvider(packageName: String, filePath: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val relativePath = filePath.substringAfter("/data/data/$packageName/")
            val uri = Uri.Builder()
                .scheme("content")
                .authority(GAME_DATA_PROVIDER_AUTHORITY)
                .appendPath("data")
                .appendPath("data")
                .appendPath(packageName)
                .appendPath(relativePath)
                .build()

            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "通过ContentProvider读取文件失败: $filePath", e)
            null
        }
    }

    /**
     * 写入文件内容
     * @param filePath 文件路径
     * @param content 文件内容（字节数组）
     * @return 是否成功
     */
    suspend fun writeFile(filePath: String, content: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            // 检查是否为应用私有数据路径
            if (filePath.startsWith("/data/data/")) {
                val parts = filePath.split("/", limit = 4)
                if (parts.size >= 4) {
                    val packageName = parts[3]
                    if (isAppInjected(packageName)) {
                        return@withContext writeFileViaProvider(packageName, filePath, content)
                    }
                }
            }

            // 普通文件写入
            val file = File(filePath)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { output ->
                output.write(content)
            }
            Log.d(TAG, "文件写入成功: $filePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "文件写入失败: $filePath", e)
            false
        }
    }

    /**
     * 通过ContentProvider写入应用私有数据文件
     * @param packageName 应用包名
     * @param filePath 完整文件路径
     * @param content 文件内容（字节数组）
     * @return 是否成功
     */
    private suspend fun writeFileViaProvider(packageName: String, filePath: String, content: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val relativePath = filePath.substringAfter("/data/data/$packageName/")
            val uri = Uri.Builder()
                .scheme("content")
                .authority(GAME_DATA_PROVIDER_AUTHORITY)
                .appendPath("data")
                .appendPath("data")
                .appendPath(packageName)
                .appendPath(relativePath)
                .build()

            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(content)
            }
            Log.d(TAG, "通过ContentProvider写入文件成功: $filePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "通过ContentProvider写入文件失败: $filePath", e)
            false
        }
    }

    /**
     * 获取文件大小
     */
    fun getFileSize(filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) file.length() else 0
    }

    /**
     * 检查文件是否存在
     */
    fun fileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }

    /**
     * 删除文件
     */
    suspend fun deleteFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "删除文件失败: $filePath", e)
            false
        }
    }

    /**
     * 复制文件
     */
    suspend fun copyFile(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            val dest = File(destPath)
            dest.parentFile?.mkdirs()

            source.copyTo(dest, overwrite = true)
            Log.d(TAG, "文件复制成功: $sourcePath -> $destPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "文件复制失败: $sourcePath -> $destPath", e)
            false
        }
    }

    /**
     * 获取目录下所有文件
     */
    suspend fun listFiles(directoryPath: String): List<File> = withContext(Dispatchers.IO) {
        val dir = File(directoryPath)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 通过 URI 读取文件（用于文件选择器）
     */
    suspend fun readFileFromUri(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "从 URI 读取文件失败", e)
            null
        }
    }

    /**
     * 获取 URI 文件名
     */
    fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }
}

/**
 * 文件信息数据类
 */
data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long
)
package com.gamedatamanager.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileNotFoundException

/**
 * 游戏数据文件提供器
 * 
 * 这个ContentProvider用于通过注入的方式访问目标应用的私有数据目录
 * 
 * 功能：
 * - 读取文件
 * - 写入文件
 * - 列出目录内容
 * - 获取文件信息
 * 
 * 使用方式：
 * content://com.gamedatamanager.provider/data/data/<package_name>/path/to/file
 */
class GameDataFileProvider : ContentProvider() {

    companion object {
        private const val TAG = "GameDataFileProvider"
        
        // URI 匹配器
        private const val CODE_FILES = 1
        private const val CODE_FILE_ITEM = 2
        private const val CODE_FILE_EXISTS = 3
        
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI("com.gamedatamanager.provider", "data/data/*", CODE_FILES)
            addURI("com.gamedatamanager.provider", "data/data/*/*", CODE_FILE_ITEM)
            addURI("com.gamedatamanager.provider", "data/data/*/*/#", CODE_FILE_EXISTS)
        }
        
        // MIME 类型
        private const val MIME_TYPE_DIR = "vnd.android.cursor.dir/vnd.gamedatamanager.file"
        private const val MIME_TYPE_FILE = "vnd.android.cursor.item/vnd.gamedatamanager.file"
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "GameDataFileProvider created")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val match = uriMatcher.match(uri)
        
        when (match) {
            CODE_FILE_ITEM -> {
                // 查询单个文件信息
                val file = getFileFromUri(uri)
                if (file != null && file.exists()) {
                    return createFileInfoCursor(file, projection)
                }
            }
            CODE_FILES -> {
                // 查询目录内容
                val dir = getDirectoryFromUri(uri)
                if (dir != null && dir.exists() && dir.isDirectory) {
                    return createDirectoryCursor(dir, projection)
                }
            }
        }
        
        return null
    }

    override fun getType(uri: Uri): String? {
        val match = uriMatcher.match(uri)
        return when (match) {
            CODE_FILES -> MIME_TYPE_DIR
            CODE_FILE_ITEM -> MIME_TYPE_FILE
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // 不支持插入操作
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val file = getFileFromUri(uri)
        return if (file != null && file.exists()) {
            if (file.delete()) 1 else 0
        } else {
            0
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        // 不支持更新操作
        return 0
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = getFileFromUri(uri)
        
        if (file == null || !file.exists()) {
            throw FileNotFoundException("File not found: $uri")
        }
        
        val fileMode = when {
            mode.contains("w") -> "rw"
            mode.contains("r") -> "r"
            else -> "r"
        }
        
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(fileMode))
    }

    /**
     * 从 URI 获取文件对象
     */
    private fun getFileFromUri(uri: Uri): File? {
        val pathSegments = uri.pathSegments
        
        if (pathSegments.size >= 4) {
            // 格式: data/data/<package>/path/to/file
            val packageName = pathSegments[2]
            val relativePath = pathSegments.drop(3).joinToString("/")
            val fullPath = "/data/data/$packageName/$relativePath"
            return File(fullPath)
        }
        
        return null
    }

    /**
     * 从 URI 获取目录对象
     */
    private fun getDirectoryFromUri(uri: Uri): File? {
        val pathSegments = uri.pathSegments
        
        if (pathSegments.size >= 3) {
            // 格式: data/data/<package>
            val packageName = pathSegments[2]
            val fullPath = if (pathSegments.size > 3) {
                val relativePath = pathSegments.drop(3).joinToString("/")
                "/data/data/$packageName/$relativePath"
            } else {
                "/data/data/$packageName"
            }
            return File(fullPath)
        }
        
        return null
    }

    /**
     * 创建文件信息 Cursor
     */
    private fun createFileInfoCursor(file: File, projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            "path",
            "is_directory",
            "last_modified"
        )
        
        val cursor = MatrixCursor(columns)
        val row = cursor.newRow()
        
        for (column in columns) {
            when (column) {
                OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                OpenableColumns.SIZE -> row.add(file.length())
                "path" -> row.add(file.absolutePath)
                "is_directory" -> row.add(file.isDirectory)
                "last_modified" -> row.add(file.lastModified())
            }
        }
        
        return cursor
    }

    /**
     * 创建目录内容 Cursor
     */
    private fun createDirectoryCursor(dir: File, projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            "path",
            "is_directory",
            "last_modified"
        )
        
        val cursor = MatrixCursor(columns)
        
        dir.listFiles()?.forEach { file ->
            val row = cursor.newRow()
            
            for (column in columns) {
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                    OpenableColumns.SIZE -> row.add(file.length())
                    "path" -> row.add(file.absolutePath)
                    "is_directory" -> row.add(file.isDirectory)
                    "last_modified" -> row.add(file.lastModified())
                }
            }
        }
        
        return cursor
    }
}
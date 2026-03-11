package com.gamedatamanager.model

import java.io.File

data class SavedData(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val saveName: String,
    val filePath: String,
    val fileSize: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val description: String = ""
) {
    val fileName: String
        get() = File(filePath).name

    val fileExtension: String
        get() = File(filePath).extension
}
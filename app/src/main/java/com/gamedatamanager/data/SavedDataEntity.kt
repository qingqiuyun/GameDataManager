package com.gamedatamanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 存档数据实体
 */
@Entity(tableName = "saved_data")
data class SavedDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val saveName: String,
    val filePath: String,
    val fileSize: Long,
    val createdAt: Long,
    val description: String
)
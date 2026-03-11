package com.gamedatamanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 存档数据访问对象
 */
@Dao
interface SavedDataDao {

    /**
     * 插入存档
     */
    @Insert
    suspend fun insert(savedData: SavedDataEntity): Long

    /**
     * 更新存档
     */
    @Update
    suspend fun update(savedData: SavedDataEntity)

    /**
     * 删除存档
     */
    @Delete
    suspend fun delete(savedData: SavedDataEntity)

    /**
     * 根据ID删除存档
     */
    @Query("DELETE FROM saved_data WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 获取所有存档
     */
    @Query("SELECT * FROM saved_data ORDER BY createdAt DESC")
    fun getAllSavedData(): Flow<List<SavedDataEntity>>

    /**
     * 根据包名获取存档
     */
    @Query("SELECT * FROM saved_data WHERE packageName = :packageName ORDER BY createdAt DESC")
    fun getSavedDataByPackage(packageName: String): Flow<List<SavedDataEntity>>

    /**
     * 根据ID获取存档
     */
    @Query("SELECT * FROM saved_data WHERE id = :id")
    suspend fun getSavedDataById(id: Long): SavedDataEntity?

    /**
     * 搜索存档
     */
    @Query("SELECT * FROM saved_data WHERE appName LIKE '%' || :query || '%' OR saveName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchSavedData(query: String): Flow<List<SavedDataEntity>>

    /**
     * 删除指定包名的所有存档
     */
    @Query("DELETE FROM saved_data WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}
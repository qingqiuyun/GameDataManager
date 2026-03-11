package com.gamedatamanager.data

import com.gamedatamanager.model.SavedData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 存档数据仓库
 */
class SavedDataRepository(private val savedDataDao: SavedDataDao) {

    /**
     * 获取所有存档
     */
    fun getAllSavedData(): Flow<List<SavedData>> {
        return savedDataDao.getAllSavedData().map { entities ->
            entities.map { it.toModel() }
        }
    }

    /**
     * 根据包名获取存档
     */
    fun getSavedDataByPackage(packageName: String): Flow<List<SavedData>> {
        return savedDataDao.getSavedDataByPackage(packageName).map { entities ->
            entities.map { it.toModel() }
        }
    }

    /**
     * 搜索存档
     */
    fun searchSavedData(query: String): Flow<List<SavedData>> {
        return savedDataDao.searchSavedData(query).map { entities ->
            entities.map { it.toModel() }
        }
    }

    /**
     * 根据ID获取存档
     */
    suspend fun getSavedDataById(id: Long): SavedData? {
        return savedDataDao.getSavedDataById(id)?.toModel()
    }

    /**
     * 保存存档
     */
    suspend fun saveSavedData(savedData: SavedData): Long {
        val entity = savedData.toEntity()
        return savedDataDao.insert(entity)
    }

    /**
     * 更新存档
     */
    suspend fun updateSavedData(savedData: SavedData) {
        savedDataDao.update(savedData.toEntity())
    }

    /**
     * 删除存档
     */
    suspend fun deleteSavedData(savedData: SavedData) {
        savedDataDao.delete(savedData.toEntity())
    }

    /**
     * 根据ID删除存档
     */
    suspend fun deleteSavedDataById(id: Long) {
        savedDataDao.deleteById(id)
    }

    /**
     * 删除指定包名的所有存档
     */
    suspend fun deleteSavedDataByPackage(packageName: String) {
        savedDataDao.deleteByPackage(packageName)
    }
}

/**
 * Entity 转 Model
 */
private fun SavedDataEntity.toModel(): SavedData {
    return SavedData(
        id = id,
        packageName = packageName,
        appName = appName,
        saveName = saveName,
        filePath = filePath,
        fileSize = fileSize,
        createdAt = createdAt,
        description = description
    )
}

/**
 * Model 转 Entity
 */
private fun SavedData.toEntity(): SavedDataEntity {
    return SavedDataEntity(
        id = id,
        packageName = packageName,
        appName = appName,
        saveName = saveName,
        filePath = filePath,
        fileSize = fileSize,
        createdAt = createdAt,
        description = description
    )
}
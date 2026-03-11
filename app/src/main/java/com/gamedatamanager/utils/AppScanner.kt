package com.gamedatamanager.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.gamedatamanager.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 应用扫描器
 * 扫描已安装的应用，特别是游戏应用
 */
class AppScanner(private val context: Context) {

    private val packageManager = context.packageManager

    /**
     * 获取所有已安装的应用
     */
    suspend fun getAllApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        packages.mapNotNull { appInfo ->
            try {
                val packageName = appInfo.packageName
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo)
                val versionInfo = packageManager.getPackageInfo(packageName, 0)
                val versionName = versionInfo.versionName ?: "Unknown"

                // 判断是否为游戏
                val isGame = isGameApp(appInfo)

                // 检查数据目录是否存在
                val hasPrivateData = checkPrivateDataExists(packageName)
                val hasPublicData = checkPublicDataExists(packageName)

                AppInfo(
                    packageName = packageName,
                    appName = appName,
                    icon = icon,
                    versionName = versionName,
                    isGame = isGame,
                    hasPrivateData = hasPrivateData,
                    hasPublicData = hasPublicData
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 只获取游戏应用
     */
    suspend fun getGameApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        getAllApps().filter { it.isGame }
    }

    /**
     * 搜索应用
     */
    suspend fun searchApps(query: String): List<AppInfo> = withContext(Dispatchers.IO) {
        getAllApps().filter {
            it.appName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }

    /**
     * 判断是否为游戏应用
     */
    private fun isGameApp(appInfo: ApplicationInfo): Boolean {
        // 检查 category 是否为游戏
        if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
            return true
        }

        // 检查是否安装在外部存储（游戏通常比较大）
        if ((appInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
            return true
        }

        // 检查包名是否包含游戏相关关键词
        val gameKeywords = listOf("game", "play", "游戏", "娱乐", "娱乐")
        val packageName = appInfo.packageName.lowercase()
        if (gameKeywords.any { packageName.contains(it) }) {
            return true
        }

        return false
    }

    /**
     * 检查私有数据目录是否存在
     */
    private fun checkPrivateDataExists(packageName: String): Boolean {
        val dataPath = "/data/data/$packageName"
        return File(dataPath).exists()
    }

    /**
     * 检查公共数据目录是否存在
     */
    private fun checkPublicDataExists(packageName: String): Boolean {
        val dataPath = "/storage/emulated/0/Android/data/$packageName"
        return File(dataPath).exists()
    }

    /**
     * 检查应用大小
     */
    suspend fun getAppSize(packageName: String): Long = withContext(Dispatchers.IO) {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val apkPath = appInfo.sourceDir
            File(apkPath).length()
        } catch (e: Exception) {
            0
        }
    }
}
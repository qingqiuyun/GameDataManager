package com.gamedatamanager.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val versionName: String,
    val isGame: Boolean = false,
    val hasPrivateData: Boolean = false,
    val hasPublicData: Boolean = false
) {
    // 私有数据目录路径
    val privateDataPath: String
        get() = "/data/data/$packageName"

    // 公共数据目录路径
    val publicDataPath: String
        get() = "/storage/emulated/0/Android/data/$packageName"
}
package com.gamedatamanager

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.gamedatamanager.service.InjectionService
import com.gamedatamanager.ui.theme.GameDataManagerTheme
import com.gamedatamanager.viewmodel.MainViewModel
import com.gamedatamanager.ui.screens.MainScreen
import com.gamedatamanager.ui.screens.PermissionRequestScreen

/**
 * 主 Activity
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var injectionService: InjectionService? = null
    private var isServiceBound = false

    // 权限请求结果
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // 权限已授予，请求 Shizuku 权限
            requestShizukuPermission()
        }
    }

    // 存储管理权限请求
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // 权限已授予，请求 Shizuku 权限
                requestShizukuPermission()
            }
        }
    }

    // Shizuku 权限请求
    private val shizukuPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Shizuku 权限请求后，检查权限状态
        val hasShizuku = checkShizukuPermission()
        android.util.Log.d("MainActivity", "Shizuku permission: $hasShizuku")
        
        if (hasShizuku) {
            android.util.Log.d("MainActivity", "Shizuku permission granted")
        }
    }

    // 服务连接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as InjectionService.InjectionBinder
            injectionService = binder.getService()
            isServiceBound = true
            
            // 将注入服务传递给 ViewModel
            viewModel.setInjectionService(injectionService)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            injectionService = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动注入服务
        val serviceIntent = Intent(this, InjectionService::class.java)
        startService(serviceIntent)

        // 绑定注入服务
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // 复制rish文件到外部存储
        copyRishFiles()

        setContent {
            GameDataManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasPermission by remember { mutableStateOf(checkPermissions()) }

                    if (hasPermission) {
                        // 有存储权限，请求 Shizuku 权限
                        LaunchedEffect(Unit) {
                            if (!checkShizukuPermission()) {
                                requestShizukuPermission()
                            }
                        }
                        MainScreen(viewModel = viewModel)
                    } else {
                        PermissionRequestScreen(
                            onRequestPermission = { requestPermissions() }
                        )
                    }
                }
            }
        }
    }

    /**
     * 复制rish文件到外部存储目录
     */
    private fun copyRishFiles() {
        try {
            val externalDir = getExternalFilesDir(null) ?: return
            val rishScript = java.io.File(externalDir, "rish.sh")
            val rishDex = java.io.File(externalDir, "rish_shizuku.dex")

            // 检查文件是否已存在
            if (rishScript.exists() && rishDex.exists()) {
                return
            }

            // 从 assets 复制 rish 文件
            assets.open("rish.sh").use { input ->
                java.io.FileOutputStream(rishScript).use { output ->
                    input.copyTo(output)
                }
            }
            rishScript.setExecutable(true)

            assets.open("rish_shizuku.dex").use { input ->
                java.io.FileOutputStream(rishDex).use { output ->
                    input.copyTo(output)
                }
            }

            android.util.Log.d("MainActivity", "Rish files copied to external storage")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to copy rish files", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }

    /**
     * 检查 Shizuku 权限
     */
    private fun checkShizukuPermission(): Boolean {
        return try {
            val shizukuPackage = "moe.shizuku.privileged.api"
            val shizukuPermission = "moe.shizuku.manager.permission.API_V23"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(shizukuPermission) == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to check Shizuku permission", e)
            false
        }
    }

    /**
     * 请求 Shizuku 权限
     */
    private fun requestShizukuPermission() {
        try {
            val shizukuPackage = "moe.shizuku.privileged.api"
            
            // 检查 Shizuku 是否已安装
            val packageManager = packageManager
            packageManager.getPackageInfo(shizukuPackage, 0)
            
            // Shizuku 已安装，请求权限
            val intent = Intent().apply {
                action = "moe.shizuku.manager.intent.action.REQUEST_PERMISSION"
                setPackage(shizukuPackage)
                putExtra("moe.shizuku.manager.intent.extra.EXTRA_APPLICATION_ID", packageName)
            }
            
            shizukuPermissionLauncher.launch(intent)
            android.util.Log.d("MainActivity", "Requesting Shizuku permission")
        } catch (e: PackageManager.NameNotFoundException) {
            // Shizuku 未安装，提示用户安装
            android.util.Log.w("MainActivity", "Shizuku not installed")
            showShizukuNotInstalledDialog()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to request Shizuku permission", e)
        }
    }

    /**
     * 显示 Shizuku 未安装对话框
     */
    private fun showShizukuNotInstalledDialog() {
        // 可以在 UI 中显示提示
        android.util.Log.d("MainActivity", "Shizuku not installed. Please install Shizuku from F-Droid or GitHub.")
    }

    /**
     * 检查权限
     */
    private fun checkPermissions(): Boolean {
        // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }

        // Android 10 及以下需要存储权限
        val readPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val writePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        return readPermission && writePermission
    }

    /**
     * 请求权限
     */
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 请求存储管理权限
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            // Android 10 及以下请求存储权限
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}
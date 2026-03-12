package com.gamedatamanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gamedatamanager.R
import com.gamedatamanager.injection.InjectionManager
import com.gamedatamanager.injection.InjectionMethod
import com.gamedatamanager.injection.InjectionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 注入服务
 * 
 * 这是一个后台服务，负责管理注入操作
 * 
 * 功能：
 * - 执行注入操作
 * - 管理注入状态
 * - 提供注入结果通知
 * - 维护已注入的应用列表
 * 
 * 使用方式：
 * 1. 绑定服务
 * 2. 调用 inject() 方法执行注入
 * 3. 通过 StateFlow 监听注入状态
 */
class InjectionService : Service() {

    companion object {
        private const val TAG = "InjectionService"
        private const val NOTIFICATION_CHANNEL_ID = "injection_service_channel"
        private const val NOTIFICATION_ID = 1001
        
        // 服务动作
        const val ACTION_INJECT = "com.gamedatamanager.action.INJECT"
        const val ACTION_STOP_INJECTION = "com.gamedatamanager.action.STOP_INJECTION"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }

    private val binder = InjectionBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var injectionManager: InjectionManager

    // 注入状态
    private val _injectionState = MutableStateFlow<InjectionState>(InjectionState.Idle)
    val injectionState: StateFlow<InjectionState> = _injectionState.asStateFlow()

    // 已注入的应用列表
    private val _injectedApps = MutableStateFlow<Set<String>>(emptySet())
    val injectedApps: StateFlow<Set<String>> = _injectedApps.asStateFlow()

    inner class InjectionBinder : Binder() {
        fun getService(): InjectionService = this@InjectionService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "InjectionService created")
        
        injectionManager = InjectionManager(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_INJECT -> {
                    val packageName = it.getStringExtra(EXTRA_PACKAGE_NAME)
                    if (packageName != null) {
                        inject(packageName)
                    }
                }
                ACTION_STOP_INJECTION -> {
                    val packageName = it.getStringExtra(EXTRA_PACKAGE_NAME)
                    if (packageName != null) {
                        stopInjection(packageName)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "InjectionService destroyed")
        serviceScope.cancel()
    }

    /**
     * 执行注入
     */
    fun inject(packageName: String) {
        if (_injectedApps.value.contains(packageName)) {
            Log.d(TAG, "$packageName already injected")
            return
        }

        _injectionState.value = InjectionState.Injecting(packageName)

        serviceScope.launch {
            val result = injectionManager.inject(packageName)
            
            if (result.success) {
                _injectedApps.value = _injectedApps.value + packageName
                Log.d(TAG, "Successfully injected into $packageName using ${result.method}")
            } else {
                Log.e(TAG, "Failed to inject into $packageName: ${result.message}")
            }
            
            _injectionState.value = InjectionState.Completed(
                packageName = packageName,
                result = result
            )
            
            updateNotification()
        }
    }

    /**
     * 使用 rish.sh 注入
     */
    fun injectWithRish(packageName: String) {
        if (_injectedApps.value.contains(packageName)) {
            Log.d(TAG, "$packageName already injected")
            return
        }

        _injectionState.value = InjectionState.Injecting(packageName)

        serviceScope.launch {
            val success = injectionManager.injectWithRish(packageName)
            
            if (success) {
                _injectedApps.value = _injectedApps.value + packageName
                _injectionState.value = InjectionState.Completed(
                    packageName = packageName,
                    result = InjectionResult(
                        success = true,
                        method = InjectionMethod.SHIZUKU,
                        message = "使用 rish.sh 注入成功"
                    )
                )
                Log.d(TAG, "Successfully injected into $packageName using rish.sh")
            } else {
                _injectionState.value = InjectionState.Completed(
                    packageName = packageName,
                    result = InjectionResult(
                        success = false,
                        method = InjectionMethod.SHIZUKU,
                        message = "使用 rish.sh 注入失败"
                    )
                )
                Log.e(TAG, "Failed to inject into $packageName using rish.sh")
            }
            
            updateNotification()
        }
    }

    /**
     * 停止注入
     */
    fun stopInjection(packageName: String) {
        if (!_injectedApps.value.contains(packageName)) {
            Log.d(TAG, "$packageName not injected")
            return
        }

        _injectionState.value = InjectionState.Stopping(packageName)

        serviceScope.launch {
            val success = injectionManager.stopInjection(packageName)
            
            if (success) {
                _injectedApps.value = _injectedApps.value - packageName
                Log.d(TAG, "Successfully stopped injection for $packageName")
            } else {
                Log.e(TAG, "Failed to stop injection for $packageName")
            }
            
            _injectionState.value = InjectionState.Idle
            updateNotification()
        }
    }

    /**
     * 检查应用是否已注入
     */
    fun isInjected(packageName: String): Boolean {
        return _injectedApps.value.contains(packageName)
    }

    /**
     * 获取可用的注入方法
     */
    fun getAvailableMethods(): List<InjectionMethod> {
        val methods = mutableListOf<InjectionMethod>()

        // 检查是否有 Shizuku 权限
        if (injectionManager.hasShizukuPermission()) {
            methods.add(InjectionMethod.SHIZUKU)
        }

        // 添加 rish.sh 选项
        if (injectionManager.isRishAvailable()) {
            methods.add(InjectionMethod.SHIZUKU)
        }

        return methods
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "注入服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "游戏数据注入服务通知"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("游戏数据管理器")
            .setContentText("注入服务运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
        
        return notificationBuilder.build()
    }

    /**
     * 更新通知
     */
    private fun updateNotification() {
        val injectedCount = _injectedApps.value.size
        val text = if (injectedCount > 0) {
            "已注入 $injectedCount 个应用"
        } else {
            "注入服务运行中"
        }
        
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("游戏数据管理器")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

/**
 * 注入状态
 */
sealed class InjectionState {
    object Idle : InjectionState()
    data class Injecting(val packageName: String) : InjectionState()
    data class Stopping(val packageName: String) : InjectionState()
    data class Completed(
        val packageName: String,
        val result: InjectionResult
    ) : InjectionState()
}
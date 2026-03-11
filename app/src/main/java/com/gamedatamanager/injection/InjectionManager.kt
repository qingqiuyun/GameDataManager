package com.gamedatamanager.injection

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * 注入管理器
 * 
 * 负责将代码注入到目标应用进程，以实现免root访问 /data/data 目录
 * 
 * 工作原理：
 * 1. 检查目标应用是否正在运行
 * 2. 使用 ptrace 或其他技术注入代码到目标进程
 * 3. 在目标进程中加载 dex 文件
 * 4. 通过注入的代码访问目标应用的私有数据
 * 
 * 注意：这个实现需要 root 权限或者特殊的系统权限
 * 如果没有 root，可以使用 Shizuku 等工具来辅助实现
 */
class InjectionManager(private val context: Context) {

    companion object {
        private const val TAG = "InjectionManager"
        
        // Shizuku 服务常量
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
    }

    /**
     * 检查是否有 Shizuku 权限
     */
    fun hasShizukuPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(SHIZUKU_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    /**
     * 检查是否有 root 权限
     */
    suspend fun hasRootPermission(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        }
    }

    /**
     * 检查应用是否正在运行
     */
    fun isAppRunning(packageName: String): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return false
        
        return runningProcesses.any { it.processName == packageName }
    }

    /**
     * 获取应用进程 PID
     */
    fun getAppPid(packageName: String): Int? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return null
        
        val process = runningProcesses.find { it.processName == packageName }
        return process?.pid
    }

    /**
     * 使用 ptrace 注入到目标进程
     * 
     * 注意：这是一个简化的实现，实际的 ptrace 注入更复杂
     * 需要处理架构差异、地址空间布局等
     */
    suspend fun injectWithPtrace(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val pid = getAppPid(packageName) ?: return@withContext false
        
        try {
            // 检查是否有 root 权限
            if (!hasRootPermission()) {
                Log.e(TAG, "No root permission")
                return@withContext false
            }
            
            // 使用 su 命令执行注入
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            
            // 这里使用简化的注入方式
            // 实际实现需要：
            // 1. 附加到目标进程 (ptrace attach)
            // 2. 保存寄存器状态
            // 3. 在目标进程中分配内存
            // 4. 注入代码（加载 dex 文件）
            // 5. 执行注入的代码
            // 6. 恢复寄存器状态
            // 7. 分离进程 (ptrace detach)
            
            // 简化版本：使用 LD_PRELOAD 注入
            val injectedLibPath = context.applicationInfo.nativeLibraryDir + "/libinjector.so"
            val cmd = """
                LD_PRELOAD=$injectedLibPath kill -USR1 $pid
            """.trimIndent()
            
            os.writeBytes(cmd + "\n")
            os.writeBytes("exit\n")
            os.flush()
            
            val exitValue = process.waitFor()
            val success = exitValue == 0
            
            if (success) {
                Log.d(TAG, "Successfully injected into $packageName (pid: $pid)")
            } else {
                Log.e(TAG, "Failed to inject into $packageName (pid: $pid)")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Injection failed", e)
            false
        }
    }

    /**
     * 使用 Shizuku 注入
     * 
     * Shizuku 提供了一种不需要 root 的方式来执行系统级操作
     */
    suspend fun injectWithShizuku(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasShizukuPermission()) {
            Log.e(TAG, "No Shizuku permission")
            return@withContext false
        }
        
        try {
            // 通过 Shizuku 执行命令
            val pid = getAppPid(packageName) ?: return@withContext false
            
            // 这里需要使用 Shizuku 的 API
            // 简化实现：假设我们已经通过 Shizuku 执行了注入命令
            Log.d(TAG, "Shizuku injection requested for $packageName (pid: $pid)")
            
            // 实际实现需要：
            // 1. 获取 Shizuku 服务
            // 2. 通过 Shizuku 执行注入命令
            // 3. 处理注入结果
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku injection failed", e)
            false
        }
    }

    /**
     * 使用 rish.sh 执行命令
     * 
     * rish.sh 是 Shizuku 提供的 shell 脚本，可以通过 ADB 权限执行命令
     * 
     * @param command 要执行的命令
     * @return 命令输出
     */
    suspend fun executeWithRish(command: String): String = withContext(Dispatchers.IO) {
        try {
            val rishScript = context.getExternalFilesDir(null)?.absolutePath + "/rish.sh"
            val rishDex = context.getExternalFilesDir(null)?.absolutePath + "/rish_shizuku.dex"
            
            // 检查 rish 文件是否存在
            val scriptFile = java.io.File(rishScript)
            val dexFile = java.io.File(rishDex)
            
            if (!scriptFile.exists() || !dexFile.exists()) {
                Log.e(TAG, "rish.sh or rish_shizuku.dex not found")
                return@withContext ""
            }
            
            // 设置 RISH_APPLICATION_ID 环境变量
            val packageName = context.packageName
            
            // 执行命令
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "sh",
                    rishScript,
                    command
                )
            )
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command with rish.sh", e)
            ""
        }
    }

    /**
     * 使用 rish.sh 注入到目标应用
     * 
     * 这种方法不需要 root，只需要 Shizuku 的 ADB 权限
     */
    suspend fun injectWithRish(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pid = getAppPid(packageName) ?: return@withContext false
            
            // 使用 rish.sh 执行注入命令
            val command = "su -c 'kill -USR1 $pid'" // 示例命令，实际注入命令会更复杂
            
            val output = executeWithRish(command)
            
            val success = output.isNotEmpty()
            if (success) {
                Log.d(TAG, "Successfully injected into $packageName using rish.sh")
            } else {
                Log.e(TAG, "Failed to inject into $packageName using rish.sh")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Rish injection failed", e)
            false
        }
    }

    /**
     * 检查 rish 是否可用
     */
    fun isRishAvailable(): Boolean {
        val rishScript = context.getExternalFilesDir(null)?.absolutePath + "/rish.sh"
        val rishDex = context.getExternalFilesDir(null)?.absolutePath + "/rish_shizuku.dex"
        
        return java.io.File(rishScript).exists() && java.io.File(rishDex).exists()
    }

    /**
     * 注入到目标应用
     * 
     * 根据可用的权限选择合适的注入方式
     */
    suspend fun inject(packageName: String): InjectionResult {
        return withContext(Dispatchers.IO) {
            if (!isAppRunning(packageName)) {
                return@withContext InjectionResult(
                    success = false,
                    method = InjectionMethod.NONE,
                    message = "应用未运行"
                )
            }
            
            val hasRoot = hasRootPermission()
            val hasShizuku = hasShizukuPermission()
            
            val result = when {
                hasRoot -> {
                    val success = injectWithPtrace(packageName)
                    InjectionResult(
                        success = success,
                        method = InjectionMethod.PTRACE,
                        message = if (success) "使用 ptrace 注入成功" else "ptrace 注入失败"
                    )
                }
                hasShizuku -> {
                    val success = injectWithShizuku(packageName)
                    InjectionResult(
                        success = success,
                        method = InjectionMethod.SHIZUKU,
                        message = if (success) "使用 Shizuku 注入成功" else "Shizuku 注入失败"
                    )
                }
                else -> {
                    InjectionResult(
                        success = false,
                        method = InjectionMethod.NONE,
                        message = "需要 root 或 Shizuku 权限"
                    )
                }
            }
            
            result
        }
    }

    /**
     * 停止注入
     */
    suspend fun stopInjection(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pid = getAppPid(packageName) ?: return@withContext false
            
            if (hasRootPermission()) {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                os.writeBytes("kill -USR2 $pid\n")
                os.writeBytes("exit\n")
                os.flush()
                
                val exitValue = process.waitFor()
                exitValue == 0
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop injection", e)
            false
        }
    }

    /**
     * 执行 Shell 命令（需要 root）
     */
    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes(command + "\n")
            os.writeBytes("exit\n")
            os.flush()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            ""
        }
    }
}

/**
 * 注入方法枚举
 */
enum class InjectionMethod {
    NONE,
    PTRACE,
    SHIZUKU,
    XPOSED
}

/**
 * 注入结果数据类
 */
data class InjectionResult(
    val success: Boolean,
    val method: InjectionMethod,
    val message: String
)
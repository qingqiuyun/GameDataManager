package com.gamedatamanager.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gamedatamanager.injection.InjectionMethod
import com.gamedatamanager.injection.InjectionResult
import com.gamedatamanager.service.InjectionService
import com.gamedatamanager.service.InjectionState
import kotlinx.coroutines.delay

/**
 * 注入对话框
 * 
 * 用于显示注入状态、选择注入方法、执行注入操作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InjectionDialog(
    packageName: String,
    appName: String,
    onDismiss: () -> Unit,
    onInjectionSuccess: () -> Unit
) {
    val context = LocalContext.current
    var isBound by remember { mutableStateOf(false) }
    var injectionService by remember { mutableStateOf<InjectionService?>(null) }
    var injectionState by remember { mutableStateOf<InjectionState>(InjectionState.Idle) }
    var availableMethods by remember { mutableStateOf<List<InjectionMethod>>(emptyList()) }
    var selectedMethod by remember { mutableStateOf<InjectionMethod?>(null) }

    // 服务连接
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as InjectionService.InjectionBinder
                injectionService = binder.getService()
                isBound = true
                
                // 获取可用的注入方法
                availableMethods = injectionService?.getAvailableMethods() ?: emptyList<InjectionMethod>()
                
                // 检查应用是否已注入
                if (injectionService?.isInjected(packageName) == true) {
                    injectionState = InjectionState.Completed(
                        packageName = packageName,
                        result = InjectionResult(
                            success = true,
                            method = InjectionMethod.NONE,
                            message = "应用已注入"
                        )
                    )
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                injectionService = null
                isBound = false
            }
        }
    }

    // 绑定服务
    LaunchedEffect(Unit) {
        val intent = Intent(context, InjectionService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        // 监听注入状态
        while (true) {
            delay(100)
            injectionService?.injectionState?.collect { state ->
                injectionState = state
                if (state is InjectionState.Completed && state.result.success) {
                    onInjectionSuccess()
                }
            }
        }
    }

    // 解绑服务
    DisposableEffect(Unit) {
        onDispose {
            if (isBound) {
                context.unbindService(serviceConnection)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "注入应用",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 应用信息
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 根据状态显示不同内容
                when (injectionState) {
                    is InjectionState.Idle -> {
                        IdleContent(
                            availableMethods = availableMethods,
                            selectedMethod = selectedMethod,
                            onMethodSelected = { selectedMethod = it },
                            onInject = {
                                selectedMethod?.let { method ->
                                    // 如果选择了Shizuku方法，尝试使用rish.sh
                                    if (method == InjectionMethod.SHIZUKU) {
                                        try {
                                            // 尝试使用rish.sh注入
                                            injectionService?.javaClass?.getMethod("injectWithRish", String::class.java)
                                                ?.invoke(injectionService, packageName)
                                        } catch (e: Exception) {
                                            // 如果失败，使用常规方法
                                            injectionService?.inject(packageName)
                                        }
                                    } else {
                                        injectionService?.inject(packageName)
                                    }
                                }
                            }
                        )
                    }
                    is InjectionState.Injecting -> {
                        InjectingContent(packageName = packageName)
                    }
                    is InjectionState.Stopping -> {
                        StoppingContent(packageName = packageName)
                    }
                    is InjectionState.Completed -> {
                        val completedState = injectionState as InjectionState.Completed
                        CompletedContent(
                            result = completedState.result,
                            onStop = {
                                injectionService?.stopInjection(packageName)
                            },
                            onDismiss = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    availableMethods: List<InjectionMethod>,
    selectedMethod: InjectionMethod?,
    onMethodSelected: (InjectionMethod) -> Unit,
    onInject: () -> Unit
) {
    Column {
        Text(
            text = "选择注入方法",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (availableMethods.isEmpty()) {
            // 没有可用的注入方法
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "警告",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "没有可用的注入方法\n\n需要 root 权限或 Shizuku\n请授予相应权限后重试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            // 显示可用的注入方法
            availableMethods.forEach { method ->
                MethodCard(
                    method = method,
                    selected = selectedMethod == method,
                    onClick = { onMethodSelected(method) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 注入按钮
        Button(
            onClick = onInject,
            enabled = selectedMethod != null && availableMethods.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开始注入")
        }
    }
}

@Composable
private fun MethodCard(
    method: InjectionMethod,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = getMethodName(method),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = getMethodDescription(method),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InjectingContent(packageName: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "正在注入到 $packageName",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "请稍候...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StoppingContent(packageName: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "正在停止注入: $packageName",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "请稍候...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompletedContent(
    result: InjectionResult,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        // 结果图标和消息
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (result.success) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = if (result.success) "成功" else "失败",
                    tint = if (result.success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (result.success) "注入成功" else "注入失败",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (result.success) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.success) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (result.success) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("停止注入")
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("关闭")
            }
        }
    }
}

private fun getMethodName(method: InjectionMethod): String {
    return when (method) {
        InjectionMethod.PTRACE -> "Ptrace 注入"
        InjectionMethod.SHIZUKU -> "Shizuku 注入"
        InjectionMethod.XPOSED -> "Xposed 注入"
        InjectionMethod.NONE -> "未知方法"
    }
}

private fun getMethodDescription(method: InjectionMethod): String {
    return when (method) {
        InjectionMethod.PTRACE -> "需要 root 权限，直接注入到目标进程"
        InjectionMethod.SHIZUKU -> "需要 Shizuku（支持 rish.sh），无需 root 权限"
        InjectionMethod.XPOSED -> "需要 Xposed 框架"
        InjectionMethod.NONE -> "未知方法"
    }
}
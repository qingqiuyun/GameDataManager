package com.gamedatamanager.ui.screens

@OptIn(ExperimentalMaterial3Api::class)
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamedatamanager.model.AppInfo
import com.gamedatamanager.ui.components.InjectionDialog
import com.gamedatamanager.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val appList by viewModel.appList.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showInjectionDialog by remember { mutableStateOf(false) }
    var selectedAppForInjection by remember { mutableStateOf<AppInfo?>(null) }
    var injectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Data Manager") },
                actions = {
                    IconButton(onClick = { viewModel.scanGameApps() }) {
                        Icon(Icons.Default.Search, contentDescription = "扫描游戏")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.isNotEmpty()) {
                        viewModel.searchApps(it)
                    } else {
                        viewModel.scanGameApps()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("搜索游戏...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                },
                singleLine = true
            )

            // 加载指示器
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // 应用列表
                if (appList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("未找到游戏应用\n点击搜索图标扫描")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(appList) { app ->
                            val isInjected = injectedApps.contains(app.packageName)
                            AppListItem(
                                app = app,
                                isInjected = isInjected,
                                onClick = { viewModel.selectApp(app) },
                                onInjectClick = {
                                    selectedAppForInjection = app
                                    showInjectionDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 注入对话框
    if (showInjectionDialog && selectedAppForInjection != null) {
        InjectionDialog(
            packageName = selectedAppForInjection!!.packageName,
            appName = selectedAppForInjection!!.appName,
            onDismiss = { showInjectionDialog = false },
            onInjectionSuccess = {
                injectedApps = injectedApps + selectedAppForInjection!!.packageName
                showInjectionDialog = false
            }
        )
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    isInjected: Boolean,
    onClick: () -> Unit,
    onInjectClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 应用图标
            app.icon?.let {
                // 在实际应用中，这里需要使用 AsyncImage 或 Coil 来加载图标
                // 简化版本：只显示名称
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 应用信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isInjected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "已注入",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "版本: ${app.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 注入按钮
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onInjectClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isInjected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                )
            ) {
                Icon(
                    imageVector = if (isInjected) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (isInjected) "已注入" else "注入",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isInjected) "已注入" else "注入",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

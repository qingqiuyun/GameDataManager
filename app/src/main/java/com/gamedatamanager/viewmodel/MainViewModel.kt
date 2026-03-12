package com.gamedatamanager.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamedatamanager.data.AppDatabase
import com.gamedatamanager.data.SavedDataRepository
import com.gamedatamanager.model.AppInfo
import com.gamedatamanager.model.SavedData
import com.gamedatamanager.service.InjectionService
import com.gamedatamanager.utils.AppScanner
import com.gamedatamanager.utils.FileAccessManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 主视图模型
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val savedDataRepository = SavedDataRepository(database.savedDataDao())
    private val appScanner = AppScanner(application)
    private val fileAccessManager = FileAccessManager(application)
    
    private var injectionService: InjectionService? = null

    // UI 状态
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 应用列表
    private val _appList = MutableStateFlow<List<AppInfo>>(emptyList())
    val appList: StateFlow<List<AppInfo>> = _appList.asStateFlow()

    // 存档列表
    private val _savedDataList = MutableStateFlow<List<SavedData>>(emptyList())
    val savedDataList: StateFlow<List<SavedData>> = _savedDataList.asStateFlow()

    // 当前选中的应用
    private val _selectedApp = MutableStateFlow<AppInfo?>(null)
    val selectedApp: StateFlow<AppInfo?> = _selectedApp.asStateFlow()

    // 已注入的应用列表
    private val _injectedApps = MutableStateFlow<Set<String>>(emptySet())
    val injectedApps: StateFlow<Set<String>> = _injectedApps.asStateFlow()

    init {
        loadSavedData()
    }

    /**
     * 设置注入服务
     */
    fun setInjectionService(service: InjectionService?) {
        injectionService = service
        
        // 监听注入服务状态
        injectionService?.injectedApps?.let { flow ->
            viewModelScope.launch {
                flow.collect { apps ->
                    _injectedApps.value = apps
                    // 更新 FileAccessManager 中的注入状态
                    apps.forEach { packageName ->
                        fileAccessManager.setInjectedApp(packageName)
                    }
                }
            }
        }
    }

    /**
     * 扫描所有应用
     */
    fun scanAllApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val apps = appScanner.getAllApps()
                _appList.value = apps
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * 只扫描游戏应用
     */
    fun scanGameApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val apps = appScanner.getGameApps()
                _appList.value = apps
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * 搜索应用
     */
    fun searchApps(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val apps = appScanner.searchApps(query)
                _appList.value = apps
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * 选择应用
     */
    fun selectApp(app: AppInfo) {
        _selectedApp.value = app
    }

    /**
     * 获取应用的私有数据路径
     */
    fun getAppPrivateDataPaths(packageName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val paths = fileAccessManager.getAppPrivateDataPaths(packageName)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dataPaths = paths
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * 获取应用的公共数据路径
     */
    fun getAppPublicDataPaths(packageName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val paths = fileAccessManager.getAppPublicDataPaths(packageName)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dataPaths = paths
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * 读取文件
     */
    fun readFile(filePath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val content = fileAccessManager.readFile(filePath)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentFileContent = content,
                    currentFilePath = filePath
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * 写入文件
     */
    fun writeFile(filePath: String, content: ByteArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val success = fileAccessManager.writeFile(filePath, content)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    writeSuccess = success
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * 保存存档
     */
    fun saveSavedData(savedData: SavedData) {
        viewModelScope.launch {
            try {
                savedDataRepository.saveSavedData(savedData)
                loadSavedData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * 加载存档列表
     */
    private fun loadSavedData() {
        viewModelScope.launch {
            savedDataRepository.getAllSavedData().collect { data ->
                _savedDataList.value = data
            }
        }
    }

    /**
     * 搜索存档
     */
    fun searchSavedData(query: String) {
        viewModelScope.launch {
            savedDataRepository.searchSavedData(query).collect { data ->
                _savedDataList.value = data
            }
        }
    }

    /**
     * 删除存档
     */
    fun deleteSavedData(savedData: SavedData) {
        viewModelScope.launch {
            try {
                savedDataRepository.deleteSavedData(savedData)
                loadSavedData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * UI 状态数据类
     */
    data class UiState(
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val dataPaths: List<String> = emptyList(),
        val currentFileContent: ByteArray? = null,
        val currentFilePath: String? = null,
        val writeSuccess: Boolean? = null
    )
}
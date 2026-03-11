# Game Data Manager

一个用于管理游戏数据的应用，支持通过注入方式访问和修改游戏应用的私有数据目录。

## 功能特性

- 扫描已安装的游戏应用
- 通过注入访问应用的 `/data/data` 目录
- 支持 rish.sh (Shizuku) 注入，无需 root 权限
- 支持 ptrace 注入（需要 root 权限）
- 读取、写入、删除游戏数据文件
- 备份和恢复游戏存档

## 构建说明

由于 Android 系统架构限制，本地构建存在兼容性问题。建议使用 GitHub Actions 进行云端构建。

### 使用 GitHub Actions 构建

1. 将项目推送到 GitHub 仓库：
   ```bash
   git remote add origin https://github.com/your-username/GameDataManager.git
   git branch -M master
   git push -u origin master
   ```

2. 在 GitHub 仓库中：
   - 进入 `Actions` 标签页
   - 选择 `Build Android APK` 工作流
   - 点击 `Run workflow`
   - 选择分支并点击 `Run workflow`

3. 构建完成后：
   - 在 Actions 页面下载生成的 APK 文件
   - APK 文件保存在工作流的 Artifacts 中

## 使用说明

### 首次使用

1. 安装 APK 到设备
2. 授予存储权限
3. 安装并配置 Shizuku 应用（推荐）
4. 扫描已安装的游戏应用
5. 选择要修改的游戏
6. 点击"注入"按钮开始注入
7. 注入成功后可以访问游戏数据

### rish.sh 配置

项目支持使用 rish.sh 进行注入：

1. 确保 `rish.sh` 和 `rish_shizuku.dex` 文件位于项目目录
2. 应用会自动将这些文件复制到外部存储目录
3. 在注入对话框中选择"Shizuku注入"方法

## 技术架构

- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **架构**: MVVM
- **注入方式**: 
  - rish.sh (Shizuku ADB)
  - ptrace (需要 root)
  - ContentProvider (文件访问)

## 依赖项

- Android SDK 34
- Kotlin 2.0.21
- Jetpack Compose
- Room Database
- Kotlin Coroutines

## 注意事项

- 使用本工具前请确保你有权修改相应游戏的文件
- 修改游戏文件可能会导致游戏数据损坏或封号，请谨慎使用
- 建议在使用前备份游戏数据
- 仅支持 Android 7.0 及以上版本

## 许可证

本项目仅供学习和研究使用，请勿用于非法用途。
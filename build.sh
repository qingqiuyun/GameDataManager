#!/bin/bash

# 手动构建APK脚本
# 用于在Android/Termux环境下构建APK

set -e

# 配置变量
PROJECT_DIR="/data/data/com.termux/files/home/工作目录/GameDataManager"
ANDROID_HOME="$HOME/android-sdk"
BUILD_DIR="$PROJECT_DIR/app/build/manual"
OUTPUT_DIR="$BUILD_DIR/outputs"
DEX_DIR="$BUILD_DIR/dex"
CLASSES_DIR="$BUILD_DIR/classes"
RESOURCES_DIR="$BUILD_DIR/resources"

# 创建构建目录
mkdir -p "$OUTPUT_DIR"
mkdir -p "$DEX_DIR"
mkdir -p "$CLASSES_DIR"
mkdir -p "$RESOURCES_DIR"

echo "================================"
echo "开始手动构建APK"
echo "================================"
echo ""

# 步骤1: 清理之前的构建
echo "步骤1: 清理之前的构建"
rm -rf "$BUILD_DIR"
mkdir -p "$OUTPUT_DIR"
mkdir -p "$DEX_DIR"
mkdir -p "$CLASSES_DIR"
mkdir -p "$RESOURCES_DIR"
echo "清理完成"
echo ""

# 步骤2: 编译Kotlin代码
echo "步骤2: 编译Kotlin代码"
cd "$PROJECT_DIR"

# 收集所有Kotlin源文件
KOTLIN_SOURCES=$(find app/src/main/java -name "*.kt" | tr '\n' ' ')

# 编译Kotlin代码
kotlinc \
    -cp "$ANDROID_HOME/platforms/android-34/android.jar:$ANDROID_HOME/build-tools/34.0.0/lib/d8.jar" \
    -d "$CLASSES_DIR" \
    -no-stdlib \
    $KOTLIN_SOURCES

echo "Kotlin代码编译完成"
echo ""

# 步骤3: 检查是否有依赖项需要处理
echo "步骤3: 检查依赖项"
echo "注意: 此构建脚本仅编译主代码，不包含AndroidX等依赖"
echo "完整构建需要Gradle或其他构建工具"
echo ""

# 步骤4: 创建DEX文件
echo "步骤4: 创建DEX文件"
d8 \
    --lib "$ANDROID_HOME/platforms/android-34/android.jar" \
    --output "$DEX_DIR" \
    $(find "$CLASSES_DIR" -name "*.class" | tr '\n' ' ')

echo "DEX文件创建完成"
echo ""

# 步骤5: 使用aapt生成未签名APK
echo "步骤5: 使用aapt生成未签名APK"
aapt package \
    -f \
    -m \
    -J "$RESOURCES_DIR" \
    -S app/src/main/res \
    -M app/src/main/AndroidManifest.xml \
    -I "$ANDROID_HOME/platforms/android-34/android.jar" \
    -F "$OUTPUT_DIR/unsigned.apk" \
    "$DEX_DIR"

echo "未签名APK生成完成"
echo ""

# 步骤6: 签名APK
echo "步骤6: 签名APK"
# 创建调试密钥
if [ ! -f "$HOME/.android/debug.keystore" ]; then
    mkdir -p "$HOME/.android"
    keytool -genkey -v -keystore "$HOME/.android/debug.keystore" \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

# 签名APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA256 \
    -keystore "$HOME/.android/debug.keystore" \
    -storepass android -keypass android \
    "$OUTPUT_DIR/unsigned.apk" androiddebugkey

# 对齐APK
zipalign -v -p 4 "$OUTPUT_DIR/unsigned.apk" "$OUTPUT_DIR/app-debug.apk"

echo "APK签名和对齐完成"
echo ""

echo "================================"
echo "构建完成！"
echo "================================"
echo "APK位置: $OUTPUT_DIR/app-debug.apk"
echo ""

# 显示APK信息
ls -lh "$OUTPUT_DIR/app-debug.apk"
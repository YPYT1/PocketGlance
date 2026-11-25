# 05 · 构建与原生库

## 前置条件

1. 目录布局：本仓与 `../OpenCine` 同级（`pocket4p/OpenCine`）  
2. `local.properties` 指向本机 Android SDK（已 gitignore）  
3. NDK **28.2.13676358**（`app/build.gradle.kts` 写死）  
4. JDK 17  

可选：`../OpenPocketCine` —— 仅在需要**重建** Swift JNI 时用。

## 常用命令

```bash
cd /Users/liam/project/pocket4p/PocketGlance
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 单测
./gradlew :app:testDebugUnitTest
```

## Gradle 关键开关

根 `gradle.properties`：

- `org.gradle.configuration-cache=false` —— 注释写明：sibling 路径相关脚本不宜配置缓存  
- `pocketglance.versionCode` / `pocketglance.versionName` —— 版本源  

`app/build.gradle.kts`：

- 仅 `arm64-v8a`  
- `jniLibs.srcDirs("src/main/jniLibs")`  
- Compose + BuildConfig  
- minify release 当前 **关闭**  

## 依赖（应用直接声明）

```text
opencine:dji, decoder, media, core, platform, native-runtime, appkit
Compose BOM + Material3 + Navigation + Lifecycle + DataStore + Coroutines
```

版本目录来自 OpenCine `build-logic` includeBuild。

## JNI / `.so`

路径：`app/src/main/jniLibs/arm64-v8a/`  

清单见同目录 `native-libraries.txt`，核心：

- **`libOpenPocketCineAndroid.so`** —— 应用 `SwiftCoreBootstrap` 加载的库名（无 lib/前缀）：`OpenPocketCineAndroid`
- 其余：Swift runtime、Foundation、Blocks、dispatch、`libc++_shared` 等  

加载链：

```text
GlanceApp.onCreate
  → SwiftCoreBootstrap.isAvailable
    → DjiNative.load("OpenPocketCineAndroid")
      → System.loadLibrary(...)
```

`DjiNative` 的 **Java 包名与类名是原生契约**，不可改名/挪包（OpenCine 注释与 ProGuard consumer rules 依赖此点）。

### 重建 `.so`（可选）

```bash
bash ../OpenPocketCine/scripts/android-stage-swift-core.sh \
  --output "$(pwd)/app/src/main/jniLibs/arm64-v8a"
```

重建后应核对 `native-libraries.txt` 与实际文件一致，并真机验证无线扫描。

## 图标

源：`branding/icon.png` → 各密度 mipmap / adaptive icon XML。

## 与 OpenPocketCine 旧路径

`OpenPocketCine/Apps/PocketGlance` 仅保留迁出提示 README，**不要在那里构建**。

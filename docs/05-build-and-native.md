# 05 · 构建与原生库

## 前置条件

1. OpenCine 检出：默认 `../OpenCine-tasks/pocketglance-photo`，也可通过 `-Popencine.root=...` 指向包含同等修复的 OpenCine 检出。当前工作树改动尚未合入 OpenCine。
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

## Log 预览

监视器默认不自动套 LUT。视频监视器的「LUT」先显示分类和数量，点分类后只看该组；也可在所有分类或当前分类内搜索。「关闭 LUT」恢复直出。可通过「导入 .cube」添加自定义文件，归入单独的分类。自定义列表仅在应用运行期间保留。支持 2–65 点立方表（65 点重采样到 64 点）；D-Log M 的 LUT 用于 D-Log2 不保证色彩准确。

Gradle 递归打包用户提供的温影工坊目录下的全部 `.cube`，也可通过 `-Ppocketglance.lutPack=/path/to/pack` 指定来源。打包需要 Python 3 和 NumPy；找不到该目录时构建失败，不会生成缺少内置包的 APK。原始 LUT 不进 Git；发布含第三方 LUT 的 APK 前需确认授权。

本地 LUT 只影响显示，不修改相机、录制文件或机身的「预览还原」开关；拍照模式不应用 LUT。HUD 同时显示实际收到的直播分辨率，不将录制规格误当作直播清晰度。

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

# PocketGlance（Pocket 一览）

独立 Android 工程：连接 DJI Osmo Pocket（无线 SoftAP / 有线入口）、监视画面、相册标签。  
从 OpenPocketCine 的 `Apps/PocketGlance` 迁出，目录在：

```text
pocket4p/
  OpenCine/          # 共享协议与解码（本工程用 includeBuild 引用，不拷贝）
  OpenPocketCine/    # 完整监视器产品（可选；用于重建 Swift JNI）
  PocketGlance/      # ← 本仓库
```

## 依赖边界

| 需要 | 怎么来 |
| --- | --- |
| 应用 UI / 连接壳 | 本仓库 `app/` |
| DJI SoftAP、解码、权限门 | 兄弟目录 `../OpenCine/Android`（Gradle `includeBuild`） |
| `libOpenPocketCineAndroid.so` | 已放在 `app/src/main/jniLibs/arm64-v8a/`；重建见下 |

**没有**整份拷贝 OpenPocketCine 或 OpenCine 源码树。

## 构建

```bash
cd /Users/liam/project/pocket4p/PocketGlance
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

要求本机已有 `../OpenCine` 检出，且 `local.properties` 指向 Android SDK。

## 重建 Swift JNI（可选）

无线核心 `.so` 来自 OpenPocketCine 的 Android facade。若要重编：

```bash
# 在 OpenPocketCine 里 stage 后拷回
bash ../OpenPocketCine/scripts/android-stage-swift-core.sh \
  --output "$(pwd)/app/src/main/jniLibs/arm64-v8a"
```

## 图标

源文件：`branding/icon.png`（与桌面 launcher 资源同源）。

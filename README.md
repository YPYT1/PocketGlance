# PocketGlance（Pocket 监视器）

独立 Android 工程：连接 DJI Osmo Pocket（无线 SoftAP / 有线入口）、监视画面、浏览机身和存储卡媒体。  
从 OpenPocketCine 的 `Apps/PocketGlance` 迁出。

## 兼容性说明（重要）

**本仓库 1.0 仅在 DJI Osmo Pocket 4P 上完成实机验证。**

| 机型 | 状态 |
| --- | --- |
| **Osmo Pocket 4P** | 已验证：无线监视、相册浏览／删除、有线 USB 基础会话（主机枚举）等 |
| Osmo Pocket 4 / 3 / 其他 | **未验证**；不保证可用 |

有线直播码流尚未接入；有线路径目前为止会话壳与 USB 枚举。详细功能与缺口见 [`docs/`](./docs/README.md)。

## 系统文档（给 AI / 开发者）

完整架构、模块、功能、状态、构建与陷阱说明统一在：

**[`docs/`](./docs/README.md)**

请先读 `docs/README.md` 的阅读顺序；改行为时同步更新对应文档。

## 仓布局

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
| DJI SoftAP、解码、权限门 | OpenCine 任务工作树（Gradle `includeBuild`） |
| `libOpenPocketCineAndroid.so` | 已放在 `app/src/main/jniLibs/arm64-v8a/`；重建见 `docs/05-build-and-native.md` |

**没有**整份拷贝 OpenPocketCine 或 OpenCine 源码树。

## 构建

```bash
cd /Users/liam/project/pocket4p/PocketGlance
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

当前版本需要含拍照状态和解码修复的 `../OpenCine-tasks/pocketglance-photo` 工作树，或通过 `-Popencine.root=/path/to/OpenCine` 指向包含同等改动的检出。`local.properties` 须指向 Android SDK。

## 版本

- **1.0**：首个对外标记版本；实机范围仅限 Pocket 4P（见上方兼容性说明）。

## 图标

源文件：`branding/icon.png`（与桌面 launcher 资源同源）。

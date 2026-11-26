# PocketGlance 系统文档（给 AI / 开发者）

本目录是 **PocketGlance 的权威说明**。目标：读完这里，就能理解产品边界、模块分布、功能、依赖、状态流和踩坑点，**无需再通读全部源码**。改代码前仍应用文档里的「文件地图」定位到具体文件再动手。

## 阅读顺序（推荐）

| 顺序 | 文件 | 读完应掌握 |
| --- | --- | --- |
| 1 | [00-overview.md](./00-overview.md) | 产品是什么、做什么、不做什么 |
| 2 | [01-architecture.md](./01-architecture.md) | 仓间依赖、进程内分层、直播画面路径 |
| 3 | [02-modules.md](./02-modules.md) | 每个包/类职责 |
| 4 | [03-features.md](./03-features.md) | 用户可见功能与完整流程 |
| 5 | [04-data-and-state.md](./04-data-and-state.md) | 状态模型、偏好、导航 |
| 6 | [05-build-and-native.md](./05-build-and-native.md) | 构建、JNI、OpenCine includeBuild |
| 7 | [06-gotchas.md](./06-gotchas.md) | **必读**：改坏系统的常见坑 |
| 8 | [07-file-map.md](./07-file-map.md) | 路径 ↔ 职责速查 |
| 9 | [08-roadmap-gaps.md](./08-roadmap-gaps.md) | 已实现 vs 占位/未完成 |
| 10 | [09-wired-usb-evidence.md](./09-wired-usb-evidence.md) | Pocket 4P 有线 USB 实机接口证据 |
| 11 | [10-mode-hud-capture.md](./10-mode-hud-capture.md) | 多模式 HUD：订阅 key / payload 对照与未证实项 |

## 一句话定位

**PocketGlance（Pocket 监视器）** 是独立 Android 应用：用无线 SoftAP（或有线 USB 入口）连接 DJI Osmo Pocket，监视实时画面，并带一个相册标签壳。重逻辑（BLE、配对、Wi‑Fi、DUML、HEVC 解码）在兄弟仓 **OpenCine**；无线原生库 `.so` 来自 **OpenPocketCine** 的 Swift JNI facade。

## 仓位置约定

```text
pocket4p/
  OpenCine/          # 共享协议与解码；本工程 Gradle includeBuild 引用
  OpenPocketCine/    # 完整监视器产品；可重建 libOpenPocketCineAndroid.so
  PocketGlance/      # ← 本仓库（应用壳 + UI + 连接编排）
```

## 维护约定

- 行为变更、新增模块、依赖边界变化时：**同步更新本目录对应文档**。
- 根目录 `README.md` 只保留构建入口；细节以 `docs/` 为准。
- 文档中的类名、包名、文件路径以仓库现状为准；若与代码冲突，以代码为准并修文档。

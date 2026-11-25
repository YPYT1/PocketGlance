# 02 · 模块说明书

包根：`com.opencapture.pocketglance`

## 根包

### `GlanceApp`

- `Application` 子类；`onCreate` 访问 `SwiftCoreBootstrap.isAvailable`，尽早加载 JNI。
- Manifest：`android:name=".GlanceApp"`。

### `MainActivity`

- 唯一 Activity：`singleTop`、edge-to-edge、Compose 入口。
- 内含 `GlanceRoot`：导航、权限门、蓝牙开启、自动进监视、方向锁定、`LiveFeedHost` 挂载。
- USB_ACCESSORY_ATTACHED intent-filter（有线入口预留）。

### `GlanceModel`

应用唯一 `AndroidViewModel`：

- 暴露：`startupPreference`、`lastMode`、`connection`、`gallery` / `galleryFilter` / `filteredGallery`
- 行为：`connect(mode)`、`connectToFoundCamera`、`disconnect`、`attachSurface`、`setStartupPreference`、`setGalleryFilter`
- 持有当前 `ConnectionTransport`；同模式且已在 Connecting/Live 时只补 Surface，不重连
- 无线连接时读 `lastCameraId` 做暖重连；Live 后写回相机 id
- 相册目前为 `sampleGallery()` 静态样片

---

## `connection/`

### `ConnectionModels`

- `ConnectionMode`：Wireless / Wired  
- `StartupPreference`：Wireless / Wired / Last  
- `ConnectionPhase`：Idle / Connecting / Live / Failed  
- `FoundCameraRow`、`ConnectionState`（含 `cameraVertical`、`foundCameras`、`message`）

### `ConnectionTransport`（接口）

统一 `mode`、`state`、`connect`、`disconnect`、`attachSurface`。

### `WirelessSoftApTransport`

- 依赖：`SwiftCoreBootstrap`、`PocketCameraSession`、`GlanceSessionHost`
- 校验：JNI、蓝牙适配器存在且开启
- `startScan(reconnect=warmId)` 或盲扫；盲扫时首台发现后自动 `connect`
- `connectTo(cameraId)`：用户点选列表
- 把 OpenCine 细阶段映射为 Glance 四阶段 + 中文文案
- 订阅 `phaseFlow` + `decoder.isVerticalPicture` + `found`

### `WiredUsbTransport`

- 用 `UsbManager.accessoryList` 找 DJI/Pocket/Osmo 相关附件
- **成功检测到附件也仍报 Failed**：明确「有线直播尚未接入解码」
- `attachSurface` 为空操作

### `GlancePrefs`（DataStore）

文件名：`pocketglance`

| Key | 含义 |
| --- | --- |
| `startup_preference` | 启动默认 |
| `last_connection_mode` | 上次连接方式 |
| `last_camera_id` | 上次无线相机 id（暖重连） |

提供 `resolveStartupMode()`，但 **UI 层尚未在冷启动调用**（见 gaps）。

---

## `session/`

### `SwiftCoreBootstrap`

- `System.loadLibrary("OpenPocketCineAndroid")` via `DjiNative.load`
- 未加载则无线全程不可用（Transport 会 Failed 文案）

### `GlanceSessionHost`

实现 OpenCine `PocketSessionHost` 的最小宿主：

- 快门角 / native ISO hop：**关闭**
- Face detector：空实现（不读帧）
- Incident：进程内 `FeedIncidentRecorder` 环形记录，不上传
- Build 身份：`pocketglance-${VERSION_NAME}`

---

## `monitor/`

### `MonitorLayout`

纯函数工具（含单测）：

- 主页方向：`PORTRAIT`
- 进监视一次性方向：竖拍→竖屏，横拍→横屏，随后应 `LOCKED`
- `contentAspect`：9:16 / 16:9
- `contentRotationDegrees` / `fillScaleAfterQuarterTurn` / `fittedSize`：给 `LiveFeedHost` 矩阵用

**禁止**在直播中途根据翻拍改 Activity orientation（历史假重连问题）。

---

## `gallery/`

### `GalleryModels`

- `MediaKind`：Image / Video / Motion  
- `GalleryItem`  
- `MediaKindClassifier.classify(fileName, mime?, motionFlag?)`：按扩展名/mime/动图标志分类（含 `.lrf`/`.xrf` 等代理后缀）

---

## `ui/`

| 文件 | 职责 |
| --- | --- |
| `GlanceTheme` / `GlanceColors` | 浅色品牌色与排版 |
| `Strings` (`GlanceCopy`) | mode/startup/kind 中文标签 |
| `HomeScreen` | 连接入口、状态面板；入口进设置 |
| `SettingsScreen` | 默认连接：上次 / 无线 / 有线 |
| `MonitorScreen` | 监视 HUD（不负责画面）；可进设置 |
| `GalleryScreen` | 过滤网格 + 简单预览 Dialog |
| `LiveFeedHost` | TextureView + Surface 生命周期 + 变换矩阵 |
| `LivePerformance` | 常亮与高刷 |

---

## 外部模块（OpenCine，本仓直接用到的）

| 坐标 | 本仓用法 |
| --- | --- |
| `opencine:dji` | `PocketCameraSession`、`DjiNative`、`PocketSessionHost` |
| `opencine:decoder` | 经会话持有的 `HevcDecoder`（`isVerticalPicture`） |
| `opencine:media` | Incident 类型 |
| `opencine:appkit` | `DiscoveryPermissions`、`rememberPermissionGate` |
| `opencine:core` / `platform` / `native-runtime` | 被 dji/decoder 传递依赖；app 也显式 implementation |

OpenCine 里还有大量 PocketGlance **未使用**的能力（云台、多机位、Frame.io 等）——不要假设本应用已接线。

---

## 测试模块

`app/src/test/...`：

- `MediaKindClassifierTest`
- `MonitorLayoutTest`

无仪器化 UI 测试目录内容（仅 runner 配置）。

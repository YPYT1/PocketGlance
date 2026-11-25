# 04 · 数据与状态

## 运行时状态（内存）

### `ConnectionState`（权威连接 UI 状态）

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `mode` | `ConnectionMode?` | 当前路径 |
| `phase` | `ConnectionPhase` | Idle / Connecting / Live / Failed |
| `message` | `String` | 状态/错误中文 |
| `cameraVertical` | `Boolean` | 解码器判定竖拍画面 |
| `pictureReady` | `Boolean` | 当前解码会话曾呈现首帧，不代表此刻仍有新帧 |
| `pocketState` | `PocketState` | 唤醒、连接及恢复进度，不是独立电源遥测 |
| `foundCameras` | `List<FoundCameraRow>` | 扫描到的相机（无线） |
| `chrome` | `MonitorChrome` | 模式/清晰度/色彩/录制时长等 HUD |

来源：`GlanceModel._connection` ← 订阅当前 Transport 的 `state`。

### Transport 局部状态

`WirelessSoftApTransport` 额外：

- `session: PocketCameraSession?`
- `pendingSurface`
- `autoConnectAttempted`（防重复自动连）
- `reportedLiveCamera`（防重复写 lastCameraId）

`GlanceModel` 额外：

- `transport`、`observeJob`、`lastSurface`（重连时补绑）

### 相册状态

- `_gallery`：当前为样片列表  
- `_galleryFilter: MediaKind?`  
- `filteredGallery`：combine 派生  

### UI 瞬时状态（`GlanceRoot`）

- `pendingWireless`：等权限后再连  
- `didAutoOpenMonitor`：本连接周期是否已自动进监视  

---

## 持久化（DataStore `pocketglance`）

| Key | 读写点 | 用途 |
| --- | --- | --- |
| `startup_preference` | Home「启动默认」 | 预定冷启动模式（未自动执行） |
| `last_connection_mode` | `connect()` 成功选定模式时 | 「记住上次」 |
| `last_camera_id` | 无线 Live / 用户点选 | 暖重连 |

权限拒绝记忆：OpenCine 权限门用独立 prefs 名 **`pocketglance.permissions`**（勿与 DataStore 混淆）。

---

## 导航状态

`NavHost` start = `home`。  
路由与业务相位耦合：

- `phase → Live` 且 `!didAutoOpenMonitor` → 进 `monitor`  
- Live 时启用窗口传感器方向；`Idle` / `Failed` 时恢复竖屏并重置 `didAutoOpenMonitor`。

---

## Surface 归属

```text
LiveFeedHost (remember heldSurface[0])
  → GlanceModel.lastSurface + transport.attachSurface
      → WirelessSoftApTransport.pendingSurface + session.attachSurface
          → HevcDecoder.attachSurface
```

销毁 TextureView → attach null → release Surface。
换 Transport 时 Model 用 `lastSurface` 补绑。

---

## 样片相册数据

`GlanceModel.sampleGallery()` 固定 6 条，用于验证分类与 UI；**不是相机媒体列表**。真相册需将来接 OpenCine `CameraMediaService` 等（本仓 Host 未暴露媒体浏览）。

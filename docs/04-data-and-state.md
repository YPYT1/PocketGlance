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
- Live 时启用窗口传感器方向；非 Live 时手机首页仍为竖屏，平板跟随系统方向。宽屏平板首页双栏，手机仍为单栏。`Idle` / `Failed` 时重置 `didAutoOpenMonitor`。

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
切换相机模式仅更新画幅和需要重建的解码器；GLES 使用同一个输出 Surface。

拍照 HUD 从 `cam_photo_param`（以及带版本前缀的 `cam_photo_param_new`）读取画质、比例、设定延时和实时剩余秒数；动图状态从相机报告的拍摄模式判定。未收到的参数显示 `—`，与相机明确报告的「倒计时关」区分。

支持对焦的 Pocket 直播：画面单击发起对焦，双击发起 ActiveTrack，静止长按 1.5 秒尝试锁焦。坐标会按直播裁切和旋转换算；以相机回复为准，不将未确认的锁定显示成成功。单击其他位置、双击追踪或按「点此解锁」会先尝试解除本次锁定。

首帧后短暂显示「正在稳定直播画面」，码流和解码继续运行。这只避开刚接通时的低码率画面，不会改变无线链路或提高相机输出的分辨率。

---

## 样片相册数据

`GlanceModel.sampleGallery()` 固定 6 条，用于验证分类与 UI；**不是相机媒体列表**。真相册需将来接 OpenCine `CameraMediaService` 等（本仓 Host 未暴露媒体浏览）。

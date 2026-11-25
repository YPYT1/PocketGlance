# 04 · 数据与状态

## 运行时状态（内存）

### `ConnectionState`（权威连接 UI 状态）

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `mode` | `ConnectionMode?` | 当前路径 |
| `phase` | `ConnectionPhase` | Idle / Connecting / Live / Failed |
| `message` | `String` | 状态/错误中文 |
| `cameraVertical` | `Boolean` | 解码器判定竖拍画面 |
| `cameraPoseRotation` | `PictureRotation` | 从相机姿态重力投影稳定判定的预览校正角度 |
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

`GlanceGallery` 持有当前相机的浏览会话和分页游标。照片和视频按来源存储筛选，退出时释放浏览会话并恢复直播。

### UI 瞬时状态（`GlanceRoot`）

- `pendingWireless`：等权限后再连  
- `openRequest`：应用恢复或收到新的打开请求时，检查当前会话并进入监视页。

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

- 首次进入 Live，或已连接时重新打开应用，直接进入 `monitor`，不重新连接相机。监视页的系统返回手势将应用退到后台；仍可通过底部「连接」主动查看连接页。
- 已连接时，连接首页、设置、相册和 LUT 选择器的左上角均可直接返回同一个监视会话；未连接时，设置返回连接首页。
- Live 监视页在画面是竖向时请求正向竖屏，其余 Live 页面跟随传感器。非 Live 时手机首页仍为竖屏，平板跟随系统方向。宽屏平板首页双栏，手机仍为单栏。`Idle` / `Failed` 时重置手动旋转。

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

支持对焦的 Pocket 直播：画面单击发起对焦，双击发起 ActiveTrack，静止长按 1.5 秒尝试锁焦。坐标按直播裁切和旋转换算；机身拒绝 AF 锁定时，用支持的单次对焦保持作为备选，并用不同文案标明。单击、双击或按解除按钮会先尝试解除本次保持。

首帧后短暂显示「正在稳定直播画面」，码流和解码继续运行。这只避开刚接通时的低码率画面，不会改变无线链路或提高相机输出的分辨率。

---

## 相机相册数据

相册使用 OpenCine `CameraMediaService` 查询两个存储。文件标识包含存储来源；缩略图、原图和视频仅从该来源读取，不以另一存储中同名文件替代。

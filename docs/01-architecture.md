# 01 · 架构

## 仓间边界

```text
┌──────────────────────────── PocketGlance ────────────────────────────┐
│  app/ (Compose UI + GlanceModel + Transport 编排)                      │
│  jniLibs/arm64-v8a/*.so  ← 预置，来自 OpenPocketCine Swift facade       │
└───────────────┬───────────────────────────────┬──────────────────────┘
                │ Gradle includeBuild           │ System.loadLibrary
                ▼                               ▼
┌───────────────────────────┐     ┌─────────────────────────────────────┐
│ ../OpenCine/Android       │     │ libOpenPocketCineAndroid.so (+Swift)│
│  :dji :decoder :media     │     │ 实现 DjiNative JNI 导出               │
│  :core :platform :appkit  │     └─────────────────────────────────────┘
│  :native-runtime …        │
└───────────────────────────┘
```

`settings.gradle.kts` 要点：

- `includeBuild("../OpenCine/Android/build-logic")`：插件与版本目录
- `includeBuild("../OpenCine/Android")`：用 `dependencySubstitution` 把坐标  
  `com.opencapture.opencine:{core,media,platform,decoder,native-runtime,dji,vulkan,appkit,media3}`  
  指到对应 project
- **不**拷贝 OpenCine / OpenPocketCine 源码进本仓

## 应用内分层

```text
MainActivity / GlanceRoot          UI 导航、权限、方向、LiveFeed 挂载
        │
GlanceModel (AndroidViewModel)     单一应用状态枢纽
        │
ConnectionTransport                连接路径抽象
   ├── WirelessSoftApTransport     → PocketCameraSession (OpenCine)
   └── WiredUsbTransport           → Camera2 外接 UVC（LENS_FACING_EXTERNAL）
        │
GlanceSessionHost                  实现 PocketSessionHost（精简：无 Face AF）
        │
OpenCine PocketCameraSession       BLE → 配对 → SoftAP → datalink → HevcDecoder
        │
Surface (TextureView)              无线 LiveFeedHost / 有线 UvcFeedHost 直出
```

### 职责切分原则

| 层 | 可以做 | 不应做 |
| --- | --- | --- |
| UI (`ui/`) | 展示、触发回调 | 直接持有 `PocketCameraSession` |
| `GlanceModel` | 选 Transport、转发 Surface、偏好 | 解码细节、BLE |
| Transport | 映射阶段文案、调会话 API | Compose |
| `GlanceSessionHost` | 给会话提供 prefs/诊断/人脸口 | 业务 UI |
| OpenCine | 协议与解码 | PocketGlance 品牌 UI |

## 直播画面路径（关键）

1. 连接 Live 且监视路由可见时，根 `Box` 在 **NavHost 之外**挂载画面宿主：无线用 `LiveFeedHost`（GPU/GLES 解码链），有线 UVC 用 `UvcFeedHost`（Camera2 直出，绕开 GPU 效果/LUT）。
2. `TextureView` 每个 `SurfaceTexture` 生命周期只创建一个 `Surface`：无线交给 Model → Transport → `PocketCameraSession` → `HevcDecoder`；有线交给 `WiredUsbTransport` → `UvcCameraController` → `CameraCaptureSession`。手机旋转只更新布局；码流分辨率变更可在同一有效 `Surface` 上重新配置。
3. `onSurfaceTextureUpdated` 把帧时间戳回传（无线给解码器、有线给 Transport 置 `pictureReady`），作为首帧显示及恢复判断的依据。
4. 进入 Live 后窗口启用传感器模式；Pocket 翻拍只改变画面矩阵，不重新设置 `requestedOrientation`。
5. 有线 UVC 在 **Connecting** 阶段就挂出 `UvcFeedHost`，好让 Camera2 有目标 Surface；会话配置完成才切 Live。

## 连接阶段映射

OpenCine `vocabulary.ConnectionPhase` → 本应用 `ConnectionPhase`：

| OpenCine | PocketGlance | 用户文案要点 |
| --- | --- | --- |
| IDLE | Idle | — |
| SCANNING … OPENING_DATALINK | Connecting | 扫描/蓝牙/配对/确认/读热点/加 Wi‑Fi/开通道 |
| LIVE | Live | 自动进监视（一次） |
| FAILED | Failed | 提示退出 Mimo、靠近重试 |

主页额外显示 `PocketState`，区分发现、连接蓝牙、唤醒请求、配对、入网、等待首帧、监视和恢复。未连接时电源状态未知。

## 权限与系统能力

无线路径需要：

1. **发现权限门**（OpenCine `DiscoveryPermissions` + `rememberPermissionGate`）  
   - API 31+：`BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`  
   - API 29–30：`ACCESS_FINE_LOCATION`（且系统定位服务常需开启）
2. **蓝牙已打开**（否则弹系统开启蓝牙）
3. JNI 已加载（`SwiftCoreBootstrap`，在 `GlanceApp.onCreate` 预热）

有线 UVC 路径额外需要 **CAMERA** 运行时权限（`MainActivity.connectWired` 权限门）；Manifest 另声明 Wi‑Fi / 附近设备 / USB accessory 等。外接摄像头依赖 OEM 的 `LENS_FACING_EXTERNAL` Camera HAL/provider（可选能力）。

## 导航结构

路由常量（`MainActivity` 内）：

- `home` → `HomeScreen`
- `monitor` → `MonitorScreen`
- `gallery` → `GalleryScreen`

`Live` 且尚未自动打开过监视 → `navigate(monitor)`。断开从监视回 `home`。

## 性能与前台策略

- `LivePerformanceGate`：直播时 `FLAG_KEEP_SCREEN_ON` + 尽量锁最高刷新率 DisplayMode  
- SoftAP 的 `WIFI_MODE_FULL_LOW_LATENCY` 由 OpenCine `PocketCameraSession` 持有，本仓不重复申请

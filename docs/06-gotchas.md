# 06 · 注意点与陷阱（AI 必读）

改 PocketGlance 或接线 OpenCine 前先过一遍本节。这些是从代码注释与现状推导出的硬约束。

---

## G1 · 直播中禁止改 Activity 方向

**现象**：中途改 `requestedOrientation` 会触发 relayout，打断 MediaCodec，日志里像「假重连」、画面卡死。  

**正确做法**：

1. 进入监视：按当时 `cameraVertical` 设一次方向 → delay(~120ms) → `SCREEN_ORIENTATION_LOCKED`  
2. Live 期间 `cameraVertical` 变化：**只**更新 `LiveFeedHost` 变换矩阵  
3. 离开监视：恢复主页 `PORTRAIT`  

相关：`MainActivity` 方向 `LaunchedEffect`、`MonitorLayout`、`LiveFeedHost.applyFeedTransform`。

---

## G2 · Surface 一生一次，挂在 NavHost 外

`LiveFeedHost` 必须在导航根上，不能只挂在 `MonitorScreen` 里随路由销毁重建。  
TextureView 可用后创建一个 `Surface`，销毁时 release 并 `attachSurface(null)`。  
`GlanceModel` 缓存 `lastSurface`，以便 Transport 重建后补绑。

---

## G3 · JNI 必须尽早加载，库名固定

未调用 `DjiNative.load("OpenPocketCineAndroid")` 则 `DjiNative.isAvailable == false`，无线扫描起不来。  
`GlanceApp` 已在启动预热；不要删。  
库文件必须在 `jniLibs/arm64-v8a/`，且 abi 只有 arm64——装到其他 ABI 设备会挂。

---

## G4 · 不要改 `DjiNative` 的包/类名

原生符号按  
`Java_com_opencapture_opencine_dji_DjiNative_*`  
导出。改名 = 全无线链路静默失效。该类在 **OpenCine** 仓，不在本仓，但本仓依赖它。

---

## G5 · 无线前必须退官方 Mimo

失败文案已写：「请确认已退出官方 Mimo」。蓝牙/热点常被官方占用。调试失败时先查这个，再查权限/JNI。

---

## G6 · 权限门与蓝牙是两道关

`DiscoveryPermissions.discovery()` ≠ 蓝牙已开启。  
MainActivity 顺序：权限 → 蓝牙 Adapter enabled → `model.connect(Wireless)`。  
API&lt;31 还可能需要系统「定位服务」开着，否则 BLE 扫不到（OpenCine 文档约定）。

---

## G7 · 本仓没有整份 OpenCine

构建依赖 `../OpenCine`。缺兄弟仓 → Gradle 失败。  
不要把 OpenCine 源码复制进 PocketGlance「图省事」——会破坏单一真相与 includeBuild 替换。

---

## G8 · 有线 ≠ 已直播

`WiredUsbTransport` 检测到 USB 附件仍返回 **Failed**（有意为之）。  
接 AOA 解码前，UI 不应假设 Wired 能进 Monitor 出图。

---

## G9 · 相册是假数据

改相册 UI 时别假设能拉到相机文件。真媒体需新接线（OpenCine media 服务等），并更新 docs/08。

---

## G10 · 「启动默认」未自动执行

`GlancePrefs.resolveStartupMode` 存在，Home 可改偏好，但 **MainActivity 冷启动不会自动 connect**。  
若实现自动连接：复用该函数 + 与权限/蓝牙门控相同路径，并写测试/更新功能文档。

---

## G11 · GlanceSessionHost 刻意砍功能

Face AF、快门角、ISO hop 全关。不要从 OpenPocketCine 整段粘贴 Host 实现除非产品要求；粘贴会引入未声明权限与依赖。

---

## G12 · 配置缓存保持关闭

`org.gradle.configuration-cache=false`。无充分验证不要擅自打开。

---

## G13 · cleartext 与 SoftAP

`android:usesCleartextTraffic="true"` 是 SoftAP HTTP/明文控制所需。收紧网络安全配置时勿误伤相机热点路径。

---

## G14 · 同模式重复 connect

`GlanceModel.connect`：若已是同 mode 且 Connecting/Live，只 `attachSurface`，不断开重扫。改连接逻辑时保留此短路，避免监视中误触发全量重连。

---

## G15 · 自动连第一台只在无 warmId 时

有 `preferredCameraId` 时走 `startScan(reconnect=)`，并 `autoConnectAttempted=true`，不会再盲连列表第一台。改扫描 UX 时注意两分支。

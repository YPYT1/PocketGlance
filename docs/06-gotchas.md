# 06 · 注意点与陷阱（AI 必读）

改 PocketGlance 或接线 OpenCine 前先过一遍本节。这些是从代码注释与现状推导出的硬约束。

---

## G1 · 不要用相机码流方向切换 Activity

手机旋转和相机编码尺寸切换是两条独立路径。旋转附近的 `ENETUNREACH` 说明当时 UDP 路由不可达，不能单凭时间接近判定是旋转造成的。

**正确做法**：

1. 进入 Live 时启用 `SCREEN_ORIENTATION_FULL_SENSOR`，由 `configChanges` 保留 Activity。
2. 相机翻拍只更新画面矩阵；保留有效 `Surface` 并让解码器根据新码流尺寸重新配置。
3. 通过 `TextureView` 帧时间戳确认实际出图，不能把链接建立或 codec 配置成功当成首帧。

相关：`MainActivity`、`MonitorLayout`、`LiveFeedHost.applyFeedTransform`。

---

## G2 · Surface 一生一次，挂在 NavHost 外

`LiveFeedHost` 在导航根上，连接 Live 且监视路由可见时挂载。手机旋转和相机翻拍不应重建宿主。
TextureView 可用后创建一个 `Surface`，销毁时先 `attachSurface(null)` 再释放。
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

另：ColorOS 常把无网 SoftAP 判为 `NO_INTERNET` 并踢网；空闲后 UDP `EPERM` 会导致画面卡住。Live 时用前台服务 `LiveKeepaliveService` 保活，并关掉「Wi‑Fi 无网自动切移动网络」。

应用级会话不随界面销毁。切到后台时保留无线连接及心跳，释放视频输出；返回后优先在原连接上等待新关键帧，热点不可用才重连。同一相机丢链时限速重试；若其他设备明确接管，不会自动抢回。系统强行停止应用、关闭 Wi‑Fi 或相机离线时不能维持连接。

录制规格不是直播规格。监视器显示实际收到的直播尺寸；放大使用画面区域对应的插值，不能把相机发送的 720p 变成原生 4K。

## G16 · 首帧糊 / 花是 format poke

Pocket 进 Live 会短暂 `4K ↔ 1080` 踢格式再拉回，编码器重开 GOP。属 OpenCine first-picture 路径，数秒后应变清晰；不是解码永久坏了。

## G17 · 竖向预览不等于竖向录制

监视器同时读取编码宽高和云台姿态。编码仍为 16:9 而镜头横滚到侧向时，只旋转预览；不会更改相机的 4K 模式或录制文件。若相机没有上报可用的姿态，可在监视器手动旋转画面。

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

## G8 · 有线会话 ≠ 已出图

`WiredUsbTransport` 可进入 **Live**（持有 USB 连接／厂商 Bulk 或附件 FD），但 `pictureReady` 仍为 false，监视为黑屏壳。电脑上的 ExFAT 文件挂载是主机 MSC，不是手机监视通路。  
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

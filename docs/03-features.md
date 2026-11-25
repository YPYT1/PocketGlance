# 03 · 功能与用户流程

## 功能清单

### F1 · 无线连接 Pocket（主路径）

1. 用户点「无线连接」  
2. 若缺附近设备/蓝牙相关权限 → 权限门请求；可显示「授予附近设备权限」  
3. 若蓝牙关 → 请求开启蓝牙（失败则跳系统蓝牙设置）  
4. `WirelessSoftApTransport.connect`：加载 JNI、建 `PocketCameraSession`、开始扫描  
5. 若有 `lastCameraId` → 暖重连该 id；否则盲扫，发现第一台自动连  
6. 用户也可在列表点选其它相机  
7. 阶段推进：扫描 → GATT → 唤醒和配对请求 →（相机屏确认）→ 读 Wi‑Fi → 加热点 → 开 datalink → Live
8. Live 后自动导航进监视页（每个连接周期一次）

失败常见原因：未退 DJI Mimo、JNI 缺失、无蓝牙、权限拒绝、距离远。
「Pocket 状态」报告连接步骤，不把未发现设备当作关机；唤醒依赖相机仍可通过蓝牙响应。

### F2 · 有线连接入口

1. 用户点「有线连接」  
2. 枚举 USB accessory；尝试匹配 DJI / Pocket / Osmo  
3. **无论是否找到，目前不会进入 Live**；给出「尚未接入解码」或「未检测到附件」类 Failed 文案  
4. Manifest 已能接收 `USB_ACCESSORY_ATTACHED`（后续接 AOA 直播用）

### F3 · 实时监视

- Live 时根级 `LiveFeedHost` 出图  
- 顶栏半透明信息带（`chromeStatus` → `MonitorChrome`）：  
  模式（拍照/视频/低光/动图等）· 清晰度 · 色彩 · 连接方式 · 横/竖（低调小字）  
  录制中：红点 + 已录时长 + 剩余可录（有则显示）  
- 底栏：连接 / 设置 / 相册；断开在顶栏右侧  
- 手机横竖切换跟随传感器；相机码流翻拍只调整画面矩阵。
- 横屏默认隐藏 HUD 和系统栏，轻点显示控件；连接恢复失败时可手动重试。
- 直播时常亮 + 尽量最高刷新率  

### F4 · 断开与切模式

- 主页或监视「断开」→ `disconnect()`，状态回 Idle，可重新连  
- `GlanceModel.connect` 换模式会先断旧 Transport  

### F5 · 设置页 · 默认连接方式

独立 **设置** 路由（主页右上角 / 监视底栏可进）。  
三项：上次连接 / 无线连接 / 有线连接（`StartupPreference`）。  
偏好写入 DataStore；**冷启动自动按偏好发起连接尚未接线**（`resolveStartupMode` 已写好）。

### F6 · 相册标签（样片）

- 网格展示样片；过滤：全部 / 图片 / 视频 / 动图  
- 点开 Dialog 文本预览（非真播放器）  
- 分类规则见 `MediaKindClassifier`（扩展名、mime、motionFlag）

### F7 · 上次相机暖重连

无线 Live 成功后记住 `last_camera_id`；下次无线 `startScan(reconnect=id)`，减少盲扫等待。

---

## 端到端：无线首次连接

```text
Home → 点无线 → 权限/蓝牙 OK
  → Connecting「正在扫描…」
  →（可选）发现列表 / 自动连第一台
  →「请在相机屏幕上确认配对」等
  → Live「无线已连接」
  → 自动 navigate Monitor
  → Surface 已 attach → 出图
```

## 端到端：翻拍

```text
Live 中 cameraVertical 翻转
  → ConnectionState.cameraVertical 更新
  → LiveFeedHost 重算矩阵（旋转+铺满）
  → Activity.requestedOrientation 保持 FULL_SENSOR（不变）
```

## 非功能特性

- 中文 UI 文案（`GlanceCopy` + 硬编码中文）  
- 浅色品牌主题（米底 + pear/coral/azure）  
- 仅 arm64-v8a 包体  
- 明文流量允许（SoftAP）

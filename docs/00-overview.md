# 00 · 产品概览

## 是什么

PocketGlance（应用显示名：**Pocket 监视器**）是一个精简的 Android 监视器应用：

- 连接 DJI Osmo Pocket（主路径：无线 SoftAP；有线为 UVC 网络摄像头直出）
- 实时监视相机画面（无线 HEVC → Surface；有线 Camera2 UVC → TextureView）
- 浏览机身／存储卡相册（删除、刷新等）

**仅在 Osmo Pocket 4P 上完成实机验证**；Pocket 4／3 等其他机型未验证。

包名 / applicationId：`com.opencapture.pocketglance`  
Debug 后缀：`.debug` → `com.opencapture.pocketglance.debug`  
当前版本属性：`pocketglance.versionName=1.2.2`，`versionCode=7`（见根 `gradle.properties`；设置页「关于」可查看，并可检查 GitHub Release 更新）

## 设计意图

从 OpenPocketCine 的 `Apps/PocketGlance` **迁出为独立工程**，刻意做成「薄壳」：

| 本仓负责 | 不在本仓 |
| --- | --- |
| Compose UI、导航、权限申请编排 | DUML / SoftAP / 解码实现细节 |
| 连接模式抽象（无线 / 有线） | 完整操作台（曝光、云台、Face AF 等） |
| 监视页方向策略与画面变换 | 官方 Mimo 替代的全部功能 |
| 本地偏好（启动默认、上次相机） | 真实相机相册拉取（尚未接） |
| 预置 `jniLibs` 打包 | 拷贝整份 OpenCine / OpenPocketCine 源码 |

## 目标设备与约束

- **minSdk 29**，**targetSdk 36**，**compileSdk** 由 OpenCine 版本目录提供
- **仅 arm64-v8a**（NDK abiFilters + 预置 jniLibs）
- 需要真实手机蓝牙 + Wi‑Fi；无线连接前通常要退出官方 **DJI Mimo**
- `usesCleartextTraffic=true`（相机 SoftAP 无 TLS）

## 三个界面

1. **主页（Home）**：选无线/有线、看连接状态、选发现的相机；平板宽屏右侧内嵌设置，手机仍走设置页
2. **监视（Monitor）**：全屏看直播 + 断开/回连接/进相册；无线画面由根级 `LiveFeedHost`、有线 UVC 由 `UvcFeedHost` 提供
3. **相册（Gallery）**：按 图片/视频/动图 过滤的网格（样片数据）

## 与姊妹产品关系

- **OpenPocketCine**：完整专业监视器；PocketGlance 复用其 Android Swift JNI（`libOpenPocketCineAndroid.so`），不是再写一套原生协议。
- **OpenCine/Android**：Kotlin 模块库（`dji`、`decoder`、`appkit` 等）；PocketGlance 通过 `includeBuild` 依赖，不 vendor 源码。

## 当前能力成熟度（摘要）

| 能力 | 状态 |
| --- | --- |
| 无线发现 → 配对 → SoftAP → 直播 | ✅ 可用（依赖 OpenCine + JNI） |
| 手机横竖切换 + 相机翻拍画面适配 | 跟随手机传感器；相机码流方向独立处理 |
| 有线 UVC 网络摄像头 | ⚠️ Camera2 外接摄像头直出；**待目标平板实机验证**（OEM 外部 Camera HAL 可选） |
| 相册 | ⚠️ UI + 分类器就绪，**数据为样片** |
| 冷启动自动按「启动默认」连接 | ⚠️ 偏好可存，**MainActivity 未自动触发** |
| 人脸对焦 / 快门角偏好 | ❌ Host 里显式关掉 |

细节见 [08-roadmap-gaps.md](./08-roadmap-gaps.md)。

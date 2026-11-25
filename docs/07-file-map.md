# 07 · 文件地图

路径相对于仓库根 `PocketGlance/`。

## 工程与构建

| 路径 | 说明 |
| --- | --- |
| `settings.gradle.kts` | includeBuild OpenCine、模块替换 |
| `build.gradle.kts` | 根插件别名 |
| `gradle.properties` | 版本号、关配置缓存 |
| `app/build.gradle.kts` | SDK、ABI、依赖、jniLibs |
| `app/proguard-rules.pro` | 近空；release minify 关 |
| `gradle/wrapper/` | Wrapper |
| `branding/icon.png` | 图标源 |
| `README.md` | 人类向构建入口 |
| `docs/` | **本系统文档（权威）** |

## 应用源码

| 路径 | 说明 |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | 权限、Activity、USB、cleartext |
| `app/src/main/kotlin/.../GlanceApp.kt` | Application / JNI 预热 |
| `app/src/main/kotlin/.../MainActivity.kt` | 导航、权限、方向、Live 挂载 |
| `app/src/main/kotlin/.../GlanceModel.kt` | ViewModel 枢纽 |
| `.../connection/ConnectionModels.kt` | 枚举与状态数据类 |
| `.../connection/ConnectionTransport.kt` | Transport 接口 |
| `.../connection/WirelessSoftApTransport.kt` | 无线 SoftAP |
| `.../connection/WiredUsbTransport.kt` | 有线 USB 入口 |
| `.../connection/GlancePrefs.kt` | DataStore 偏好 |
| `.../session/SwiftCoreBootstrap.kt` | 加载 `.so` |
| `.../session/GlanceSessionHost.kt` | PocketSessionHost 实现 |
| `.../monitor/MonitorLayout.kt` | 方向与几何 |
| `.../gallery/GalleryModels.kt` | 相册模型与分类 |
| `.../ui/HomeScreen.kt` | 主页 |
| `.../ui/MonitorScreen.kt` | 监视 HUD |
| `.../ui/GalleryScreen.kt` | 相册 |
| `.../ui/LiveFeedHost.kt` | TextureView / Surface |
| `.../ui/LivePerformance.kt` | 常亮/高刷 |
| `.../ui/GlanceTheme.kt` | 主题色 |
| `.../ui/Strings.kt` | `GlanceCopy` |

## 资源与原生

| 路径 | 说明 |
| --- | --- |
| `app/src/main/res/xml/usb_accessory_filter.xml` | DJI Osmo Pocket USB 过滤 |
| `app/src/main/res/values/*` | strings/colors/themes |
| `app/src/main/res/mipmap-*` | Launcher 图标 |
| `app/src/main/jniLibs/arm64-v8a/` | 预置 `.so` |
| `app/src/main/jniLibs/arm64-v8a/native-libraries.txt` | `.so` 清单 |

## 测试

| 路径 | 说明 |
| --- | --- |
| `app/src/test/.../gallery/MediaKindClassifierTest.kt` | 分类 |
| `app/src/test/.../monitor/MonitorLayoutTest.kt` | 布局几何 |

## 兄弟仓（本仓外，构建/行为依赖）

| 路径 | 说明 |
| --- | --- |
| `../OpenCine/Android/dji/.../PocketCameraSession.kt` | 无线会话核心 |
| `../OpenCine/Android/dji/.../DjiNative.kt` | JNI 门面 |
| `../OpenCine/Android/dji/.../PocketSessionHost.kt` | Host 接口 |
| `../OpenCine/Android/decoder/.../HevcDecoder.kt` | 解码与竖拍标志 |
| `../OpenCine/Android/appkit/.../DiscoveryPermissions.kt` | 发现权限 |
| `../OpenPocketCine/scripts/android-stage-swift-core.sh` | 重建 jniLibs |
| `../OpenPocketCine/Apps/PocketGlance/` | 旧路径提示（勿构建） |

## 按任务找文件

| 想改… | 先打开 |
| --- | --- |
| 连接文案 / 阶段映射 | `WirelessSoftApTransport.kt` |
| 主页按钮与启动默认 UI | `HomeScreen.kt` |
| 自动进监视 / 权限 / 方向 | `MainActivity.kt` |
| 出图 / 旋转画面 | `LiveFeedHost.kt` + `MonitorLayout.kt` |
| 偏好持久化 | `GlancePrefs.kt` |
| 加新连接方式 | 新 Transport + `GlanceModel.connect` |
| Host 能力（Face AF 等） | `GlanceSessionHost.kt` + OpenCine 接口 |
| 版本号 | `gradle.properties` |
| 原生库 | `jniLibs/` + `SwiftCoreBootstrap.kt` |

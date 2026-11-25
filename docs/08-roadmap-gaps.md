# 08 · 已实现 vs 占位 / 缺口

供 AI 判断「该实现」还是「故意未做」，避免把占位当成 bug 乱修，或重复造轮子。

## 已实现且可用

- 无线 SoftAP 全链路（依赖 OpenCine + 预置 JNI）
- 权限门 + 蓝牙开启编排
- Live 监视、手机传感器方向、翻拍矩阵适配、横屏沉浸模式
- 连接状态中文反馈与发现相机列表
- 上次相机暖重连
- DataStore 偏好读写（启动默认 / 上次模式 / 相机 id）
- 相机相册：机身内存／存储卡，照片和视频只读预览
- 直播常亮与高刷尝试
- USB accessory Manifest 过滤与有线「检测」文案
- unit test：分类器、MonitorLayout

## 占位 / 部分完成

| 项 | 现状 | 建议方向 |
| --- | --- | --- |
| 有线直播 | 只枚举 USB，不解码、不 Live | 接 AOA + 与无线共用 decoder Surface |
| 冷启动自动连接 | 偏好可存，无启动副作用 | 在 `GlanceRoot` 用 `resolveStartupMode` + 权限门 |

## 明确不做（当前产品范围）

相对 OpenPocketCine / OpenCine 全功能，PocketGlance **当前刻意不做**：

- Face AF / 人脸框
- 曝光、对焦、白平衡、录制控制等操作台
- 云台程序运动、多机位 Multiview
- Frame.io / 上传 / 完整诊断上报 UI
- 非 arm64 ABI
- 将 OpenCine 源码 vendoring 进本仓

`GlanceSessionHost` 用空实现关掉 Face 与快门角，即体现此边界。

## 文档同步检查清单

改下列任一项时，更新对应 docs：

- [ ] 新 Transport 或连接阶段 → 01、02、03、06  
- [ ] 导航 / 方向策略 → 01、06、07  
- [ ] 偏好或自动启动行为 → 03、04、08  
- [ ] jniLibs / 依赖模块 → 05、07  
- [ ] 有线或相册从占位变真功能 → 00、03、08  

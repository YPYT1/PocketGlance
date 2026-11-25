# 09 · 有线 USB 实机证据（Pocket 4P ↔ Mac）

采集于本机直连：`OsmoPocket4P-ANHXP7R002JDRU`。这是 **电脑当 USB 主机** 时的复合设备形态，与 Mimo 的 **手机 AOA 附件** 形态不同，但同属有线路径的基础事实。

## 设备身份

| 项 | 值 |
| --- | --- |
| USB Product | `OsmoPocket4P-ANHXP7R002JDRU` |
| USB Vendor | `DJI` |
| idVendor | `11427` (`0x2CA3`) |
| idProduct | `32` (`0x0020`) |
| 挂载卷 | `Pocket4P`（机身 ~112 GB ExFAT）、`Pocket4内存卡`（ExFAT） |

`ls` 大目录可能长时间无响应；`diskutil info` / `ioreg` 仍可读。

## 接口布局（主机复合设备）

| IF | 名称 | class/sub/proto | 含义 |
| --- | --- | --- | --- |
| 0 | RNDIS Communications Control | 224 / 1 / 3 | USB 网卡控制 |
| 1 | RNDIS Ethernet Data | 10 / 0 / 0 | USB 网卡数据 |
| 2 | Mass Storage | 8 / 6 / 80 | 机身存储 |
| 3–7 | BULK Interface | **255 / 67 / 1** | DJI 厂商批量（监视候选） |
| 8 | Bulk Only / UAS MSC | 8 / 6 / … | 第二存储（存储卡） |

本机 macOS **未**拉起可用的 RNDIS 网卡（无 `192.168.2.1` 路由）；存储与厂商 Bulk 节点存在。

## Android UVC 实时预览路径

Pocket 4P 的「网络摄像头」模式是与上表文件传输复合设备不同的标准 UVC 输出路径。Pocket 官方说明支持横向 4K/1080p、竖向 3K/1080p，25/30 fps；连接电脑可供电，并可同时在机内录制。旧版泛用说明的 1080p 描述不应覆盖 Pocket 4 专属规格。参考：[DJI 官方帮助](https://support.dji.com/help/content?customId=zh-cn03400006962&spaceId=34&re=CN&lang=zh-CN)。

PocketGlance 的有线预览经 Android Camera2 `LENS_FACING_EXTERNAL` → SurfaceTexture → TextureView；客户端限定选用不超过 1920×1080 的可用预览尺寸。若平板 OEM 未提供 external camera HAL/provider，无法由应用侧补出此 provider，也不会显示伪 Live。当前没有 Android 平板可连接，本路径尚待目标设备实测权限、provider、帧展示与拔线行为。

## 与产品路径的关系

| 路径 | 角色 | 本仓状态 |
| --- | --- | --- |
| Pocket「文件传输：USB/OTG」 | 面向电脑文件传输/MSC 等复合设备 | PocketGlance 不访问这些 USB endpoints，不视作直播源 |
| Pocket「网络摄像头」 | 标准 UVC webcam，由 Android OEM Camera HAL 暴露 external camera | Camera2 直出 SurfaceTexture；无 HAL/帧时不进入 Live |
| **AOA 附件** | 非标准 UVC Camera2 输出 | 不打开 AOA FD、不作为画面来源 |

## 目标平板手动验证清单

1. 首次点有线连接时授予 CAMERA 权限；拒绝后应显示解释并保持非 Live。
2. Pocket 选「网络摄像头」(2秒) 后连接，检查 Android 是否出现 external cameraId；平板 HAL/provider 可能不支持。
3. 有 cameraId 时确认预览画面真实更新、尺寸≤1920×1080，且 Live/pictureReady 只在帧显示后成立。
4. 从 Home/Monitor 切换页面及拔线，确认 Surface/Camera2 正确关闭且无线预览不受影响。
5. Pocket 文件传输模式应诊断提示切换 UVC，不claim DJI Bulk、MSC、RNDIS 或 AOA。

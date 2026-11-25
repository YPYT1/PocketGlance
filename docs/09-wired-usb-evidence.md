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

## 与产品路径的关系

| 路径 | 角色 | 本仓状态 |
| --- | --- | --- |
| OTG **主机** ↔ 上表复合设备 | 手机枚举 VID/PID、打开连接、声明厂商 Bulk；后续可走 Bulk 或 RNDIS | **基础会话已接**（无码流） |
| **AOA 附件**（Mimo：`usb.accessory`） | 相机当 USB 主机，手机当附件 | Manifest + 打开附件 FD 已接；直播未接 |
| Webcam UVC | 另一套输出 | 非本监视控制栈 |

## 下一步（码流）

1. 在 Android OTG 上复现同一 VID/`0x0020` 枚举与 Bulk 声明。  
2. 对照 Mimo USB 会话，区分 Bulk 帧与 RNDIS 上网后的 SoftAP 同源 DUML/HEVC。  
3. 将字节流接入现有 decoder Surface，无线/有线共用。  

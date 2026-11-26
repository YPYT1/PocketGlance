# 多模式 HUD：订阅 key 与 payload 对照

显示-only：PocketGlance 只识别并在顶栏展示，不切换模式、不改参数。证据优先来自 OpenPocketCine handbook（Pocket 3 / Action 6）；实机未知 key 由 `StatusExtras` 经 `DjiLog` → `GlanceLog` 打一次 hex。

## 主模式码（`0x02/0xE1` / `cam_status`）

| 模式 | 码 | Glance 标签 | 子参数 key |
| --- | --- | --- | --- |
| 慢动作 | `00` | 慢动作 | 规格走 `cam_video_param_v2`；倍速由传感器 fps 推断 |
| 视频 | `01` | 视频 | 已有 |
| 静止延时 Fixed Angle | `02` | 静止延时 | `cam_lapse_param` |
| 拍照 | `05` / `17` | 拍照 | `cam_photo_param(_new)` |
| 运动延时 | `0A` | 运动延时 | `cam_lapse_param`（倍速 `@9–10`） |
| 全景 | `0C` | 全景 | `cam_pano_params` |
| 轨迹延时 Motionlapse | `18` | 轨迹延时 | `cam_motionlapse_params` + `0x8E` pid `0x0037` |
| 低光 | `28` | 低光视频 | 规格同视频 |
| 动图 | `4D` | 动图 | 拍照参数 |
| 其它 | — | `模式 0xNN` | 记日志，不猜名 |

## 订阅字段（已接入 OpenCine）

### `cam_pano_params`

| 偏移 | 含义 | 已映射值 |
| --- | --- | --- |
| `@0` | 类型 | `05` 180° / `07` 3×3 → `panoType` |
| `@1` | 存储 | `00` JPEG / `01` RAW → `panoStorage` |

来源：`handbook/.../pocket-3/modes.md` Panorama。

### `cam_lapse_param`（21 字节读回）

| 偏移 | 含义 | 字段 |
| --- | --- | --- |
| `@0` | 保存 | `00` Video / `02` JPEG+Video / `03` Raw+Video → `lapseSaveType` |
| `@1–2` | 间隔（0.1s，LE16） | `lapseIntervalTenths` |
| `@3–4` | 最小间隔候选 | 未单独暴露（保留在 payload） |
| `@5–8` | 时长秒（LE32，`0`=无限） | `lapseDurationSec` |
| `@9–10` | Hyperlapse 倍速（LE16，`0`=Auto） | `hyperlapseRatio` |

来源：Action 6 / Pocket 3 Timelapse / Hyperlapse handbook。

### `cam_motionlapse_params`

| 偏移 | 含义 | 字段 |
| --- | --- | --- |
| `@7`（8 字节头） | 轨迹点数 | `motionlapsePointCount` |

路径：`0x02/0x8E` pid `0x0037` → `00` 自定义 / `01` 左→右 / `02` 右→左 → `motionlapsePath`。

## 未证实（故意留空）

| 项 | 状态 |
| --- | --- |
| 延时「息屏」 | `lapseScreenOff` 字段已预留；`-1` 未知。实机观察到字节后再解码。 |
| 全景倒计时 | 手册称与拍照同类 UI，未单独抓包；暂不猜布局。 |

## 实机补证据步骤

1. 设置里打开诊断日志，无线连 Pocket。
2. 在机身依次切：全景 180°/3×3、静止延时（改间隔/时长/存盘）、轨迹（自定义加点）、运动延时倍速、低光、慢动作。
3. 设置 → 日志：搜 `unknown subscribe key=` 与 `chrome mode=`。
4. 有新 key / 新字节语义时，先补本表再改解码，禁止无证据猜 opcode。

## OpenCine / Glance 落点

- 订阅列表：`DatalinkDriver.SUBSCRIPTION_KEYS`
- 解码：`StatusExtras.applyPanoParams` / `applyLapseParam` / `applyMotionlapseParams`
- HUD：`MonitorChromeLabels`

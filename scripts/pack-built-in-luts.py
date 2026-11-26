#!/usr/bin/env python3
"""Pack .cube LUTs into GLES-ready RGBA assets + catalog.tsv (category + tags)."""

from __future__ import annotations

import hashlib
import re
import shutil
import sys
from pathlib import Path

import numpy as np

# 侧栏短标签关键词（分类名同步 ≤4 字）。
TAG_KEYWORDS: list[tuple[list[str], str]] = [
    (["人像", "古风", "汉服", "元气", "冷影", "梦境", "画报", "温柔", "柔和", "港风", "potrait", "portrait"], "人像"),
    (["风景", "森系", "森林", "雪景", "芦苇", "冰岛", "加州", "蓝调", "蓝感", "城市", "漫游", "景观"], "风景"),
    (["天空", "晴空", "晚霞", "落日", "夕阳", "霞"], "天空"),
    (
        [
            "胶片",
            "富士",
            "柯达",
            "理光",
            "炮塔",
            "金200",
            "C200",
            "5207",
            "5219",
            "复古",
            "宝丽来",
            "拍立得",
            "昭和",
            "eterna",
            "eastman",
            "bleach",
        ],
        "胶片",
    ),
    (["夜景", "黑金", "夜晚"], "夜景"),
    (["美食", "料理", "烟火", "食光"], "美食"),
    (["油画", "艺术", "朦胧", "莫奈", "宫崎骏", "动漫"], "艺术"),
    (["四季", "冬日", "夏日", "秋日", "春", "私语", "季节"], "季节"),
    (["阿莱", "iceblue", "jamaica", "utopia", "tungsten", "neutral", "arri"], "阿莱"),
    (["电影", "709", "aura", "bliss", "crane", "glow", "pulse", "vivid", "万能", "百搭"], "电影"),
    (["航拍", "aerial"], "航拍"),
    (["日系", "小清新"], "日系"),
    (["肤色", "skin"], "肤色"),
    (["口播", "网红"], "口播"),
]

# 路径片段 → ≤4 字分类（更具体的放前面）。
FOLDER_CATEGORY: list[tuple[str, str]] = [
    ("阿莱", "阿莱"),
    ("人像专用", "人像"),
    ("肤色优化", "肤色"),
    ("网红口播", "口播"),
    ("日系小清新", "日系"),
    ("富士胶片", "富士"),
    ("航拍专用", "航拍"),
    ("风景景观", "风景"),
    ("自然风景", "风景"),
    ("百搭电影", "电影"),
    ("专业电影", "电影"),
    ("胶片电影", "胶片"),
    ("胶片氛围", "胶片"),
    ("709电影", "电影"),
    ("万能电影", "电影"),
    ("大疆官方", "官方"),
    ("vision", "视觉"),
    ("通用色彩", "通用"),
    ("pocket4", "Pocket4"),
    ("人像", "人像"),
    ("胶片", "胶片"),
    ("夜景", "夜景"),
    ("美食", "美食"),
    ("艺术", "艺术"),
    ("季节", "季节"),
    ("四季", "季节"),
    ("森系", "风景"),
    ("雪景", "风景"),
    ("风景", "风景"),
]

# 短主题 / 旧「合集」名 → 统一 ≤4 字分类。
THEME_PACK: dict[str, str] = {
    "人像": "人像",
    "人像合集": "人像",
    "风景": "风景",
    "风景合集": "风景",
    "风景景观专用": "风景",
    "天空": "天空",
    "天空合集": "天空",
    "胶片": "胶片",
    "胶片合集": "胶片",
    "夜景": "夜景",
    "夜景合集": "夜景",
    "美食": "美食",
    "美食合集": "美食",
    "艺术": "艺术",
    "艺术合集": "艺术",
    "季节": "季节",
    "季节合集": "季节",
    "阿莱": "阿莱",
    "阿莱色调": "阿莱",
    "电影": "电影",
    "日系": "日系",
    "富士": "富士",
    "航拍": "航拍",
    "肤色": "肤色",
    "口播": "口播",
    "官方": "官方",
    "视觉": "视觉",
    "通用": "通用",
    "Pocket4": "Pocket4",
}

DATE_OR_BATCH_TOKEN = re.compile(
    r"("
    r"20\d{2}\d{2}"
    r"|20\d{2}\s*年"
    r"|20\d{2}"
    r"|\d{2}\s*年"
    r"|\d{1,2}\s*月"
    r"|提前上新"
    r"|上新"
    r"|第[一二三四五六七八九十\d]+弹"
    r"|更新"
    r"|LUT"
    r")",
    re.IGNORECASE,
)

COUNT_SUFFIX = re.compile(r"[（(]?\s*\d+\s*款\s*[）)]?")
LEADING_INDEX = re.compile(r"^\d+[\.、．\s]+")
# 只剥「IceBlue - M3P D-LogM」后缀，避免误伤 Dlogm-D-SUNSET 文件名。
LOG_SUFFIX = re.compile(
    r"(?:\s*[-–—]\s*|\s+)(?:M3P\s+)?D-?Log-?M\b.*$|\s*for Resolve.*$",
    re.IGNORECASE,
)
DLOGM_PREFIX = re.compile(r"^dlogm-d-", re.IGNORECASE)

# 去重时优先保留这些目录下的副本（分类更准、名字更可读）。
PREFERRED_PATH_MARKERS = (
    "阿莱",
    "官方还原",
    "肤色优化",
    "Vision",
    "富士胶片",
    "航拍专用",
    "Pocket4",
)


def clean_category_label(name: str) -> str:
    """去掉「17款」「（5款）」和备注括号，保留主题名。"""
    text = re.sub(r"\s+", " ", name).strip()
    text = COUNT_SUFFIX.sub("", text)
    text = re.sub(r"[（(][^）)]*[）)]", "", text)
    text = LEADING_INDEX.sub("", text)
    text = text.strip(" -_·")
    return text or name.strip()


def normalize_category(label: str) -> str:
    """统一到 ≤4 字展示名。"""
    if label in THEME_PACK:
        return THEME_PACK[label]
    return label


def thematic_category_label(name: str) -> str | None:
    """从文件夹名提炼主题分类；纯日期批次返回 None。"""
    text = clean_category_label(name)
    if not text or re.fullmatch(r"\d+", text):
        return None
    remainder = text
    while True:
        nxt = DATE_OR_BATCH_TOKEN.sub(" ", remainder)
        nxt = re.sub(r"\s+", " ", nxt).strip(" -_·/")
        if nxt == remainder:
            break
        remainder = nxt
    if not remainder or re.fullmatch(r"\d+", remainder):
        return None
    if remainder in {"滤镜文件", "导入教程", "滤镜", "还原", "风格"}:
        return None
    mapped = THEME_PACK.get(remainder)
    if mapped:
        return mapped
    # 「人像合集」剥「合集」后再映射。
    stripped = re.sub(r"(合集|专用|色调|系列)$", "", remainder).strip()
    if stripped in THEME_PACK:
        return THEME_PACK[stripped]
    if len(remainder) <= 4:
        return remainder
    return remainder[:4]


def is_date_or_batch_label(name: str) -> bool:
    return thematic_category_label(name) is None


def load_cube(path: Path) -> bytes:
    size = 0
    minimum = np.zeros(3, dtype=np.float32)
    maximum = np.ones(3, dtype=np.float32)
    rows: list[str] = []
    with path.open(encoding="utf-8-sig", errors="replace") as source:
        for line in source:
            fields = line.split("#", 1)[0].split()
            if not fields or fields[0] == "TITLE":
                continue
            if fields[0] == "LUT_3D_SIZE":
                size = int(fields[1])
            elif fields[0] == "DOMAIN_MIN":
                minimum = np.array(fields[1:], dtype=np.float32)
            elif fields[0] == "DOMAIN_MAX":
                maximum = np.array(fields[1:], dtype=np.float32)
            elif fields[0] == "LUT_3D_INPUT_RANGE":
                minimum[:] = float(fields[1])
                maximum[:] = float(fields[2])
            elif len(fields) == 3:
                rows.append(" ".join(fields))
            else:
                raise ValueError(f"Unsupported cube directive: {fields[0]}")
    if not 2 <= size <= 65 or len(rows) != size**3 or not np.all(maximum > minimum):
        raise ValueError("Invalid cube grid or domain")
    rgb = np.fromstring(" ".join(rows), dtype=np.float32, sep=" ").reshape(size, size, size, 3)
    if not np.isfinite(rgb).all():
        raise ValueError("Non-finite RGB value")
    if size == 65 or np.any(minimum != 0) or np.any(maximum != 1):
        index = np.linspace(0, 1, 64, dtype=np.float32)
        coords = np.clip((index[:, None] - minimum) / (maximum - minimum), 0, 1) * (size - 1)
        low = coords.astype(np.intp)
        high = np.minimum(low + 1, size - 1)
        fraction = coords - low
        for axis in range(3):
            channel = 2 - axis
            shape = [1, 1, 1, 1]
            shape[axis] = 64
            weight = fraction[:, channel].reshape(shape)
            rgb = (
                np.take(rgb, low[:, channel], axis=axis) * (1 - weight)
                + np.take(rgb, high[:, channel], axis=axis) * weight
            )
        size = 64
    rgba = np.full((size, size, size, 4), 255, dtype=np.uint8)
    rgba[..., :3] = np.floor(np.clip(rgb.transpose(1, 0, 2, 3), 0, 1) * 255 + 0.5).astype(np.uint8)
    return rgba.tobytes()


def display_name(stem: str, path: Path) -> str:
    name = re.sub(r"\s+", " ", stem).strip()
    name = LOG_SUFFIX.sub("", name).strip(" -_")
    name = DLOGM_PREFIX.sub("", name).strip(" -_")
    portrait = re.fullmatch(r"(?i)pocket\s*3\s*potrait\s*\((\d+)\)", name)
    if portrait:
        return f"人像-{portrait.group(1)}"
    red = re.fullmatch(r"(?i)pocket\s*3\s*red\s*\((\d+)\)", name)
    if red:
        return f"红调-{red.group(1)}"
    # 「大疆官方的还原LUT」→「官方还原」
    if "官方" in name and "还原" in name:
        return "官方还原"
    if re.fullmatch(r"\d+", name) or len(name) <= 1:
        parent = clean_category_label(path.parent.name)
        if parent and parent not in {"滤镜文件", "导入教程", "还原", "风格"}:
            return f"{parent}-{name}" if name else parent
    return name or stem


def infer_tags(path: Path, name: str, category: str) -> list[str]:
    hay = f"{'/'.join(path.parts)}/{name}/{category}".lower()
    tags: list[str] = []
    for keys, tag in TAG_KEYWORDS:
        if any(key.lower() in hay for key in keys):
            tags.append(tag)
    if category and category not in {"其他", "通用", "官方", "视觉"} and category not in tags:
        # 分类本身也当作可筛选标签（阿莱／航拍／富士等）。
        if category in {t for _, t in TAG_KEYWORDS} or category in {
            "阿莱",
            "电影",
            "航拍",
            "富士",
            "日系",
            "肤色",
            "口播",
            "Pocket4",
        }:
            tags.append(category)
    if "pocket4" in hay or "pocket 4" in hay:
        if "Pocket4" not in tags:
            tags.append("Pocket4")
    order = [t for _, t in TAG_KEYWORDS] + ["富士", "官方", "视觉", "通用", "Pocket4"]
    # 去重保序
    seen: set[str] = set()
    ordered: list[str] = []
    for tag in order + tags:
        if tag in tags and tag not in seen:
            seen.add(tag)
            ordered.append(tag)
    return ordered


def theme_from_tags(tags: list[str]) -> str:
    for tag in tags:
        if tag in THEME_PACK:
            return THEME_PACK[tag]
    return "其他"


def infer_category(path: Path, roots: list[Path], tags: list[str]) -> str:
    try:
        relative = path.resolve().relative_to(_matching_root(path, roots).resolve())
    except ValueError:
        relative = Path(path.name)
    parts = [p for p in relative.parts[:-1]]
    joined = "/".join(parts).lower()
    root_name = _matching_root(path, roots).name.lower()

    # 先扫相对路径关键词（温影编号目录等）；不要用根目录名误伤全部 Pocket4。
    for needle, label in FOLDER_CATEGORY:
        if needle.lower() == "pocket4":
            continue
        if needle.lower() in joined:
            return label

    for part in parts:
        thematic = thematic_category_label(part)
        if thematic:
            return thematic

    if "pocket4" in root_name or "pocket4" in joined:
        return "Pocket4"

    return theme_from_tags(tags)


def _matching_root(path: Path, roots: list[Path]) -> Path:
    resolved = path.resolve()
    for root in roots:
        try:
            resolved.relative_to(root.resolve())
            return root
        except ValueError:
            continue
    return path.parent


def collect_unique(roots: list[Path]) -> list[tuple[Path, str]]:
    """Return (path, content_hash) unique by MD5 of file bytes."""
    chosen: dict[str, Path] = {}
    for root in roots:
        if not root.exists():
            raise ValueError(f"Missing source: {root}")
        for path in sorted(root.rglob("*.cube")):
            if not path.is_file():
                continue
            digest = hashlib.md5(path.read_bytes()).hexdigest()
            previous = chosen.get(digest)
            if previous is None:
                chosen[digest] = path
                continue
            text = str(path)
            prev_text = str(previous)

            def path_score(p: Path, text: str) -> tuple:
                prefer = sum(1 for marker in PREFERRED_PATH_MARKERS if marker.lower() in text.lower())
                return (
                    prefer,
                    len(p.stem),
                    "温影" in text,
                    "pocket4" in text.lower(),
                    len(text),
                )

            if path_score(path, text) > path_score(previous, prev_text):
                chosen[digest] = path
    return [(path, digest) for digest, path in sorted(chosen.items(), key=lambda item: item[1].as_posix())]


def main() -> None:
    if len(sys.argv) < 3:
        raise SystemExit(
            "usage: pack-built-in-luts.py <output-assets-dir> <cube-root> [cube-root...]\n"
            "example: pack-built-in-luts.py app/src/main/assets ~/Downloads/A ~/Downloads/B"
        )
    output = Path(sys.argv[1])
    roots = [Path(arg) for arg in sys.argv[2:]]
    if output.exists():
        shutil.rmtree(output)
    assets = output / "built-in-luts"
    assets.mkdir(parents=True)

    catalog: list[str] = []
    unique = collect_unique(roots)
    for index, (path, _digest) in enumerate(unique, 1):
        name = display_name(path.stem, path)
        # 先分类再补标签，保证阿莱等目录名进入 tags。
        category = infer_category(path, roots, [])
        tags = infer_tags(path, name, category)
        if category in {"其他", "Pocket4"}:
            themed = theme_from_tags(tags)
            if themed != "其他":
                category = themed
                if "Pocket4" in tags or "pocket4" in str(path).lower():
                    if "Pocket4" not in tags:
                        tags.append("Pocket4")
        category = normalize_category(category)
        thematic = thematic_category_label(category)
        category = normalize_category(thematic or category)
        filename = f"look-{index:03d}.rgba"
        try:
            packed = load_cube(path)
        except Exception as error:
            raise ValueError(f"{path}: {error}") from error
        (assets / filename).write_bytes(packed)
        catalog.append(f"{filename}\t{category}\t{name}\t{','.join(tags)}")
        print(f"{filename}\t{category}\t{name}\t{','.join(tags)}")

    (assets / "catalog.tsv").write_text("\n".join(catalog) + "\n", encoding="utf-8")
    total = sum((assets / f"look-{i:03d}.rgba").stat().st_size for i in range(1, len(catalog) + 1))
    print(f"Packed {len(catalog)} preview LUTs ({total / (1024 * 1024):.1f} MiB)")


if __name__ == "__main__":
    main()

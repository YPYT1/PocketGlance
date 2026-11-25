from pathlib import Path
import shutil
import sys

import numpy as np


def load_cube(path):
    size = 0
    minimum = np.zeros(3, dtype=np.float32)
    maximum = np.ones(3, dtype=np.float32)
    rows = []
    with path.open(encoding="utf-8-sig") as source:
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
            rgb = (np.take(rgb, low[:, channel], axis=axis) * (1 - weight)
                   + np.take(rgb, high[:, channel], axis=axis) * weight)
        size = 64
    rgba = np.full((size, size, size, 4), 255, dtype=np.uint8)
    rgba[..., :3] = np.floor(np.clip(rgb.transpose(1, 0, 2, 3), 0, 1) * 255 + 0.5).astype(np.uint8)
    return rgba.tobytes()


def main():
    source, output = (Path(arg) for arg in sys.argv[1:3])
    if output.exists():
        shutil.rmtree(output)
    assets = output / "built-in-luts"
    assets.mkdir(parents=True)
    files = sorted(path for path in source.rglob("*") if path.is_file() and path.suffix.lower() == ".cube")
    if not files:
        raise ValueError(f"No .cube files in {source}")
    catalog = []
    for index, path in enumerate(files, 1):
        relative = path.relative_to(source)
        group = relative.parts[0] if len(relative.parts) > 1 else "其他"
        group = group.partition(".")[2].strip() or group
        filename = f"look-{index:03d}.rgba"
        try:
            packed = load_cube(path)
        except Exception as error:
            raise ValueError(f"{relative}: {error}") from error
        (assets / filename).write_bytes(packed)
        catalog.append(f"{filename}\t{group}\t{path.stem}")
    (assets / "catalog.tsv").write_text("\n".join(catalog) + "\n", encoding="utf-8")
    print(f"Packed {len(catalog)} preview LUTs")


if __name__ == "__main__":
    main()

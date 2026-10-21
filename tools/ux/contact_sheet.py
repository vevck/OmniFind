#!/usr/bin/env python3
"""Lays DeviceMatrixTest's renders out as one sheet per screen, each device scaled to the same physical size.

    python3 tools/ux/contact_sheet.py [out folder]

Every render is shown at 1 dp = 1.5 px, so a dp is the same size on the sheet whatever the device's density.
"""
import sys
import pathlib
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[2]
DPI = {"ldpi": 0.75, "mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
SCALE = 1.5


def density(name):
    for key in sorted(DPI, key=len, reverse=True):
        if f"{key}-" in name or name.endswith(key):
            return DPI[key]
    if "tablet" in name: return 2.0
    return 3.0


def main():
    src = ROOT / "app/build/screens/devices"
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else src
    for screen in ["search", "index", "engine"]:
        files = sorted(src.glob(f"*-{screen}.png"), key=lambda p: p.stat().st_mtime)
        tiles = []
        for f in files:
            img = Image.open(f).convert("RGB")
            d = density(f.stem)
            w, h = round(img.width / d * SCALE), round(img.height / d * SCALE)
            tiles.append((f.stem.removesuffix(f"-{screen}"), img.resize((w, h), Image.LANCZOS)))
        width = sum(t.width for _, t in tiles) + 24 * (len(tiles) + 1)
        height = max(t.height for _, t in tiles) + 60
        sheet = Image.new("RGB", (width, height), "white")
        draw = ImageDraw.Draw(sheet)
        x = 24
        for label, tile in tiles:
            draw.text((x, 12), label, fill="black")
            sheet.paste(tile, (x, 40))
            x += tile.width + 24
        sheet.save(out / f"sheet-{screen}.png")
        print(out / f"sheet-{screen}.png", sheet.size)


if __name__ == "__main__":
    main()

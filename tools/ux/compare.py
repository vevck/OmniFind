#!/usr/bin/env python3
"""Compares rendered screens (app/build/screens, from ScreenshotTest) with the design mockups.

    python3 tools/ux/compare.py <mockups folder> [out folder]

The mockups folder holds search.png, index.png and engine.png (780 × 1688). For each screen it writes
<out>/<name>-compare.png (mockup | render | difference) and prints the content bands of both, in dp, so offsets in
position and size can be read off directly. Needs Pillow and numpy.
"""
import sys
import pathlib
import numpy as np
from PIL import Image, ImageChops

ROOT = pathlib.Path(__file__).resolve().parents[2]
BG = np.array([0xF3, 0xF5, 0xF7])


def bands(img):
    a = np.asarray(img.convert("RGB")).astype(int)
    rows = (np.abs(a - BG).sum(axis=2) > 24)
    on = rows.any(axis=1)
    out, y = [], 0
    while y < len(on):
        if on[y]:
            y0 = y
            while y < len(on) and on[y]:
                y += 1
            xs = np.where(rows[y0:y].any(axis=0))[0]
            out.append((y0 / 2, y / 2, xs[0] / 2, (xs[-1] + 1) / 2))
        else:
            y += 1
    return out


def main():
    mockups = pathlib.Path(sys.argv[1])
    out = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "app/build/screens"
    out.mkdir(parents=True, exist_ok=True)
    for name in ["search", "index", "engine"]:
        want = Image.open(mockups / f"{name}.png").convert("RGB")
        got = Image.open(ROOT / f"app/build/screens/{name}.png").convert("RGB")
        if got.size != want.size:
            print(f"{name}: render is {got.size}, mockup {want.size}")
            got = got.resize(want.size)
        diff = ImageChops.difference(want, got)
        a = np.asarray(diff).astype(int).sum(axis=2)
        print(f"\n=== {name}: {100 * (a > 40).mean():.2f}% of pixels differ visibly")
        sheet = Image.new("RGB", (want.width * 3 + 40, want.height), "white")
        sheet.paste(want, (0, 0)); sheet.paste(got, (want.width + 20, 0))
        sheet.paste(Image.fromarray(np.clip(255 - a * 2, 0, 255).astype(np.uint8)).convert("RGB"), (2 * want.width + 40, 0))
        sheet.save(out / f"{name}-compare.png")
        w, g = bands(want), bands(got)
        print(f"  {'mockup y':>17}  {'x':>13}   |  {'render y':>17}  {'x':>13}")
        for i in range(max(len(w), len(g))):
            l = f"{w[i][0]:7.1f}-{w[i][1]:7.1f}  {w[i][2]:5.1f}-{w[i][3]:5.1f}" if i < len(w) else " " * 32
            r = f"{g[i][0]:7.1f}-{g[i][1]:7.1f}  {g[i][2]:5.1f}-{g[i][3]:5.1f}" if i < len(g) else ""
            print(f"  {l}   |  {r}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Renders OmniFind's launcher icon as PNGs for every density bucket, from the mark's geometry (ui/theme/Logo.kt).

    python3 tools/ux/launcher_icons.py

Android 8+ (all OmniFind runs on) draws the adaptive vector icon in mipmap-anydpi-v26; these bitmaps follow the
density guide's 3:4:6:8:12:16 ratio for launchers and tools that ask for a bitmap: mipmap-<density>/ic_launcher.png
(rounded square) and ic_launcher_round.png (circle), 36 px (ldpi) to 192 px (xxxhdpi), plus a 512 px store icon.
"""
import pathlib
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
BLUE, GREEN, WHITE = (0x2A, 0x44, 0xD4, 255), (0x3D, 0xDC, 0x97, 255), (255, 255, 255, 255)
SIZES = {"ldpi": 36, "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SUPER = 8   # draw at 8× and downsample: smooth edges at every size


def mark(draw, x0, y0, s):
    """The lens, handle, sparkle and dot in a tile of side s at (x0, y0), as drawMark() in Logo.kt."""
    c = (x0 + 0.4523 * s, y0 + 0.4523 * s)
    r, w = 0.1761 * s, 0.0568 * s
    draw.ellipse([c[0] - r - w / 2, c[1] - r - w / 2, c[0] + r + w / 2, c[1] + r + w / 2], outline=WHITE, width=round(w))
    a, b, hw = 0.1761 * s * 0.7071, 0.3634 * s * 0.7071, 0.0643 * s
    draw.line([(c[0] + a, c[1] + a), (c[0] + b, c[1] + b)], fill=WHITE, width=round(hw))
    for p in [(c[0] + b, c[1] + b)]:   # round cap at the handle's end
        draw.ellipse([p[0] - hw / 2, p[1] - hw / 2, p[0] + hw / 2, p[1] + hw / 2], fill=WHITE)
    sx, sy = x0 + 0.4534 * s, y0 + 0.4534 * s
    sr, k = 0.0898 * s, 0.16 * 0.0898 * s
    pts = []
    for i in range(4):   # four arms with concave sides: sample the cubic curves of drawMark
        tips = [(sx, sy - sr), (sx + sr, sy), (sx, sy + sr), (sx - sr, sy), (sx, sy - sr)]
        ctl = [(sx + k, sy - k), (sx + k, sy + k), (sx - k, sy + k), (sx - k, sy - k)]
        p0, p3, p1 = tips[i], tips[i + 1], ctl[i]
        for t in [j / 24 for j in range(24)]:
            u = 1 - t
            pts.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p1[0] + t ** 3 * p3[0],
                        u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p1[1] + t ** 3 * p3[1]))
    draw.polygon(pts, fill=GREEN)
    dx, dy, dr = x0 + 0.5261 * s, y0 + 0.3807 * s, 0.0148 * s
    draw.ellipse([dx - dr, dy - dr, dx + dr, dy + dr], fill=GREEN)


def icon(px, round_shape):
    S = px * SUPER
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Legacy launcher keylines: the shape fills 44/48 of the icon, centred (2 dp margin at mdpi).
    m = S * 2 / 48
    side = S - 2 * m
    if round_shape:
        d.ellipse([m, m, S - m, S - m], fill=BLUE)
        # The mark is drawn for a square tile; in a circle it sits a little smaller so the handle stays inside.
        inset = side * 0.06
        mark(d, m + inset, m + inset, side - 2 * inset)
    else:
        d.rounded_rectangle([m, m, S - m, S - m], radius=side * 0.21, fill=BLUE)
        mark(d, m, m, side)
    return img.resize((px, px), Image.LANCZOS)


def main():
    for bucket, px in SIZES.items():
        folder = RES / f"mipmap-{bucket}"
        folder.mkdir(parents=True, exist_ok=True)
        icon(px, False).save(folder / "ic_launcher.png", optimize=True)
        icon(px, True).save(folder / "ic_launcher_round.png", optimize=True)
        print(f"mipmap-{bucket}: {px}×{px}")
    store = ROOT / "docs/store"
    store.mkdir(parents=True, exist_ok=True)
    full = Image.new("RGBA", (512 * SUPER, 512 * SUPER), BLUE)   # the store crops its own corners: full bleed
    mark(ImageDraw.Draw(full), 0, 0, 512 * SUPER)
    full.resize((512, 512), Image.LANCZOS).convert("RGB").save(store / "icon-512.png", optimize=True)
    print("docs/store/icon-512.png: 512×512")


if __name__ == "__main__":
    main()

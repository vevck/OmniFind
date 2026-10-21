#!/usr/bin/env python3
"""Makes a release's tour and documents from its manifest and the build validation's screenshots.

    ./gradlew testDebugUnitTest --tests com.omnifind.app.TourScreenshotTest --tests com.omnifind.app.DeviceMatrixTest
    python3 tools/release/make_docs.py

From app/src/main/assets/release/release.json (version, build number, numbered tour, test cases, fixes, known issues)
and app/build/screens/ (the screenshots the tests captured) it writes:

- app/src/main/assets/release/tour/<screenshot>.webp: the in-app tour's images
- docs/release/<version>/tour/<n>-<screenshot>.png: the same, for the documents
- docs/Release-Notes.md: this build's notes, then every earlier build's
- the walk-through and build line in docs/Product-Specification.md and docs/Solution-Design.md (between markers)

Everything a reader sees about a release comes from one file, so the app and the documents can't disagree.
Needs Pillow (with WebP).
"""
import json
import pathlib
import re
from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "app/src/main/assets/release/release.json"
SCREENS = ROOT / "app/build/screens"
EMULATOR = SCREENS / "emulator"   # captures from a device run (tools/demo): used first when there
ASSETS = ROOT / "app/src/main/assets/release/tour"
DOCS = ROOT / "docs"


def font(size):
    for path in [ROOT / "app/src/main/res/font/sora.ttf"]:
        try:
            f = ImageFont.truetype(str(path), size)
            f.set_variation_by_axes([600])
            return f
        except Exception:
            pass
    return ImageFont.load_default()


def screens_collage():
    """Step "screens": the same screen on a small ldpi phone, the Pixel 8 Pro and a tablet, at one physical scale."""
    picks = [("ldpi-240x320-search", 0.75, "ldpi 240 × 320"), ("xxhdpi-Pixel8Pro-1344x2992-search", 3.0, "Pixel 8 Pro"),
             ("tablet-sw800-xhdpi-index", 2.0, "Tablet")]
    scale = 1.0   # px per dp on the collage
    tiles = []
    for name, density, label in picks:
        img = Image.open(SCREENS / "devices" / f"{name}.png").convert("RGB")
        tiles.append((label, img.resize((round(img.width / density * scale), round(img.height / density * scale)), Image.LANCZOS)))
    height = max(t.height for _, t in tiles) + 40
    width = sum(t.width for _, t in tiles) + 24 * (len(tiles) + 1)
    sheet = Image.new("RGB", (width, height), (0xF3, 0xF5, 0xF7))
    draw = ImageDraw.Draw(sheet)
    x = 24
    for label, tile in tiles:
        draw.text((x, 8), label, fill=(0x4A, 0x55, 0x68), font=font(16))
        sheet.paste(tile, (x, 34))
        x += tile.width + 24
    return sheet


def tour_images(release):
    ASSETS.mkdir(parents=True, exist_ok=True)
    docs = DOCS / "release" / release["version"] / "tour"
    docs.mkdir(parents=True, exist_ok=True)
    out = {}
    for step in release["tour"]:
        name = step["screenshot"]
        captured = EMULATOR / f"{name}.png"
        if name == "screens":
            img = screens_collage()
        elif captured.exists():
            img = Image.open(captured).convert("RGB")
        else:
            img = Image.open(SCREENS / "tour" / f"{name}.png").convert("RGB")
        app = img.copy()
        app.thumbnail((720, 1560), Image.LANCZOS)
        app.save(ASSETS / f"{name}.webp", "WEBP", quality=82, method=6)
        doc = img.copy()
        doc.thumbnail((540, 1170), Image.LANCZOS)
        path = docs / f"{step['number']}-{name}.png"
        doc.save(path, optimize=True)
        out[name] = path.relative_to(DOCS).as_posix()
        print(f"step {step['number']}: {name} → {(ASSETS / f'{name}.webp').relative_to(ROOT)} ({(ASSETS / f'{name}.webp').stat().st_size // 1024} KB)")
    return out


def walkthrough(release, figures, heading_level="###"):
    cases = {t["id"]: t for t in release["testCases"]}
    lines = [f"{heading_level} Feature walk-through: {release['product']} {release['version']}, build {release['build']}", "",
             f"Shown in the app as **Take a tour of the features** after installing or updating to build {release['build']}"
             " (and from Engine → What's new). Screenshots and highlights come from build validation: a run on a "
             "device with sample data where noted, otherwise `TourScreenshotTest` and `DeviceMatrixTest`.", ""]
    run = release.get("validationRun")
    if run:
        lines += ["| Validation run | |", "|---|---|"] + [f"| {k.capitalize()} | {v} |" for k, v in run.items()] + [""]
    for step in release["tour"]:
        lines += [f"**{step['number']}. {step['title']}** · _{step['status']}_", "", step["overview"], ""]
        if step.get("highlights"):
            lines += ["Highlights from the validation run:", ""] + [f"- {h}" for h in step["highlights"]] + [""]
        lines += ["Verified by: " + ", ".join(f"{t} ({cases[t]['name']})" for t in step["tests"]), "",
                  f"![Step {step['number']}: {step['title']}]({figures[step['screenshot']]})", ""]
        if step.get("source"):
            lines += [f"_Screenshot: {step['source']}._", ""]
    return "\n".join(lines).rstrip() + "\n"


def release_notes(release, figures):
    builds = {}
    for fix in release["fixes"]:
        builds.setdefault(fix["build"], []).append(fix["title"])
    current = [f"## {release['product']} {release['version']} · Build {release['build']}", "",
               f"{release['channel']} · {release['date']} · {release['minAndroid']} to {release['targetAndroid']} · arm64", "",
               release["summary"], "", "### What's in this build", ""]
    for step in release["tour"]:
        current.append(f"{step['number']}. **{step['title']}** ({step['status']}). {step['overview']}")
    current += ["", "### Fixed", ""]
    for i, fix in enumerate(release["fixes"], 1):
        current.append(f"{i}. {fix['title']} _({fix['build']})_")
    current += ["", "### Known issues", ""] + [f"- {k}" for k in release["known"]]
    current += ["", "### Validation", "", "| Test case | What it checks | Test | Where | Result |", "|---|---|---|---|---|"]
    for t in release["testCases"]:
        current.append(f"| {t['id']} | {t['name']} | `{t['test']}` | {t['where']} | {t.get('result', '')} |")
    current += ["", walkthrough(release, figures).rstrip()]
    earlier = ["", "## Earlier builds", "",
               "- **0.3.0-beta.3 · Build 5** (2026-10-20): first cut of folder Refresh and the tour, with rendered "
               "screenshots only; superseded by build 6 before going to testers.",
               "- **0.3.0-beta.2 · Build 4** (2026-10-20): Android 15–17 edge to edge, every density and large text, "
               "launcher icons per density, Android 11+ file access. Fixes listed above as _0.3.0-beta.2_.",
               "- **0.3.0-beta.1 · Build 3** (2026-10-18): first beta. Release signing, in-app model download, photos "
               "and screenshots, the redesigned screens.",
               "- **Milestones 1–3** (2026-10-18, builds 1–2, not released): the engine on the Mi A1, text search, "
               "pictures."]
    head = ["# OmniFind Release Notes", "",
            "_Generated by `tools/release/make_docs.py` from `app/src/main/assets/release/release.json`; edit that, "
            "not this file._", ""]
    return "\n".join(head + current + earlier) + "\n"


def fill(path, release, figures):
    """Replaces the build line and the walk-through between their markers in a hand-written document."""
    text = path.read_text()
    build = (f"<!-- build:begin -->**{release['product']} {release['version']} · Build {release['build']}** · "
             f"{release['date']} · {release['minAndroid']} to {release['targetAndroid']}<!-- build:end -->")
    text = re.sub(r"<!-- build:begin -->.*?<!-- build:end -->", build, text, flags=re.S)
    block = "<!-- walkthrough:begin -->\n" + walkthrough(release, figures) + "<!-- walkthrough:end -->"
    text = re.sub(r"<!-- walkthrough:begin -->.*?<!-- walkthrough:end -->", lambda _: block, text, flags=re.S)
    path.write_text(text)
    print(f"updated {path.relative_to(ROOT)}")


def main():
    release = json.loads(MANIFEST.read_text())
    figures = tour_images(release)
    (DOCS / "Release-Notes.md").write_text(release_notes(release, figures))
    print("wrote docs/Release-Notes.md")
    for doc in ["Product-Specification.md", "Solution-Design.md"]:
        fill(DOCS / doc, release, figures)


if __name__ == "__main__":
    main()

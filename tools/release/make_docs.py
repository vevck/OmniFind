#!/usr/bin/env python3
"""Makes a release's tour and documents from its manifest and the build validation's screenshots.

    ./gradlew testDebugUnitTest --tests com.omnifind.app.TourScreenshotTest --tests com.omnifind.app.DeviceMatrixTest
    python3 tools/release/make_docs.py

From app/src/main/assets/release/release.json (version, build number, numbered tour, test cases, fixes, known issues)
and app/build/screens/ (the screenshots the tests captured) it writes:

- app/src/main/assets/release/tour/<screenshot>.webp: the in-app tour's images
- docs/release/<version>/tour/<n>-<screenshot>.png: the same, for the documents
- docs/Release-Notes.md: this build's notes, then every earlier build's (tools/release/history.json)
- app/src/main/assets/help/release_notes.json: the in-app Release Notes, this version then the earlier ones
- app/src/main/assets/help/figures/<name>.webp: the User Guide's screenshots, cropped from device captures
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
HISTORY = ROOT / "tools/release/history.json"
HELP = ROOT / "app/src/main/assets/help"

# The User Guide's figures: (capture in app/build/screens/emulator, top, bottom) in the capture's pixels.
# A figure whose capture isn't there keeps the one already in the assets.
FIGURES = {
    "search-results": ("search-results", 300, 1600),
    "hilsa": ("hilsa", 300, 1700),
    "pictures": ("pictures", 300, 1200),
    "payment": ("payment", 300, 1050),
    "index-types": ("index-types", 1500, 2200),
    "index-folders": ("index-folders", 330, 2100),
}


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
    earlier = ["", "## Earlier versions"]
    for v in json.loads(HISTORY.read_text())["versions"]:
        earlier += ["", f"### {release['product']} {v['version']} · Build {v['build']}", "", f"{v['date']} · {v['summary']}"]
        for label in ["new", "improved", "fixed"]:
            if v.get(label):
                earlier += ["", f"**{label.capitalize()}**", ""] + [f"- {item}" for item in v[label]]
        if v.get("thanks"):
            earlier += ["", f"_{v['thanks']}_"]
    earlier += ["", "Milestones 1–3 (18 Oct 2026, builds 1–2) weren't released: the engine on the Mi A1, text search, "
                "pictures."]
    head = ["# OmniFind Release Notes", "",
            "_Generated by `tools/release/make_docs.py` from `app/src/main/assets/release/release.json`; edit that, "
            "not this file._", ""]
    return "\n".join(head + current + earlier) + "\n"


def short_date(iso):
    """2026-10-21 → 21 Oct 2026, as the app shows dates."""
    from datetime import date
    return date.fromisoformat(iso).strftime("%-d %b %Y")


def help_notes(release):
    """The in-app Release Notes: this version from the manifest, then the earlier ones, newest first."""
    current = {"version": release["version"], "build": release["build"], "date": short_date(release["date"]),
               "summary": release["oneLine"], **release["notes"]}
    versions = [current] + [v for v in json.loads(HISTORY.read_text())["versions"] if v["version"] != release["version"]]
    path = HELP / "release_notes.json"
    path.write_text(json.dumps({"versions": versions}, indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {path.relative_to(ROOT)} ({len(versions)} versions)")


def help_figures():
    out = HELP / "figures"
    out.mkdir(parents=True, exist_ok=True)
    for name, (capture, top, bottom) in FIGURES.items():
        source = EMULATOR / f"{capture}.png"
        if not source.exists():
            print(f"figure {name}: no capture, kept {'the existing one' if (out / f'{name}.webp').exists() else 'none'}")
            continue
        img = Image.open(source).convert("RGB")
        img = img.crop((0, top, img.width, min(bottom, img.height)))
        img.thumbnail((720, 1600), Image.LANCZOS)
        img.save(out / f"{name}.webp", "WEBP", quality=82, method=6)
        print(f"figure {name} → {(out / f'{name}.webp').relative_to(ROOT)} ({(out / f'{name}.webp').stat().st_size // 1024} KB)")


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
    help_notes(release)
    help_figures()
    for doc in ["Product-Specification.md", "Solution-Design.md"]:
        fill(DOCS / doc, release, figures)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Builds a phone's worth of sample files for demos and validation runs, laid out as on /sdcard.

    python3 tools/demo/make_sample_data.py <out folder>
    adb push <out folder>/. /sdcard/

Documents (txt, md, html, docx), PDFs with real text layers, photos and app screenshots, in Documents/, Download/,
Pictures/, Pictures/Screenshots/ and DCIM/Camera/, each about something a search can be asked for: a lease's pet
clause, an invoice number, a trip, a recipe, a declined payment, a beach at sunset. All made here, from scratch: no
real person's data. Needs Pillow.
"""
import pathlib
import sys
import zipfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
SORA = ROOT / "app/src/main/res/font/sora.ttf"


def font(size, weight=400):
    f = ImageFont.truetype(str(SORA), size)
    f.set_variation_by_axes([weight])
    return f


# ---------------------------------------------------------------- documents

TEXTS = {
    "Documents/Trip to Kamakura.md": """# Trip to Kamakura

- Nov 3: flight to Tokyo Haneda, NH 106, lands 15:40
- Nov 4: train from Shinagawa to Kamakura (JR Yokosuka line, about 55 minutes)
- See the Great Buddha at Kotoku-in, then walk to Hase-dera for the view over the bay
- Dinner back in Shibuya; book the ramen place near the scramble crossing
""",
    "Documents/Grandma's dal recipe.txt": """Grandma's dal

Soak one cup of toor dal overnight. Pressure cook with turmeric and a pinch of salt, three whistles.
Temper cumin seeds, mustard seeds, curry leaves and two dried red chillies in ghee, then add garlic.
Pour the tempering over the dal, simmer five minutes, finish with lemon juice and coriander.
""",
    "Documents/Meeting notes 2026-10-14.txt": """Product review, 14 October 2026

The review moved to room 4 because the projector in the main hall failed again.
Decisions: ship the search feature in the October beta; cut the app's start time in half by December.
Actions: Priya to send the slides the night before; Arjun to book the hall's projector repair.
""",
    "Documents/Car service record.txt": """Car service record

12 Aug 2026, 58,200 km: oil change, new air filter.
Next due at 60,000 km: oil change, brake pads front and rear, check the tyre pressure sensor.
Garage: Lakeview Motors, ask for Ravi.
""",
    "Download/Library hours.html": """<html><head><title>City Library: opening hours</title></head><body>
<h1>City Library opening hours</h1>
<p>Monday to Friday: 9 am to 8 pm. Saturday and Sunday: 10 am to 5 pm. Closed on public holidays.</p>
<p>Books are due back within 21 days; renew online up to twice.</p>
</body></html>
""",
    "Documents/ইলিশ রান্না.txt": """সরষে ইলিশ

ইলিশ মাছ ধুয়ে লবণ ও হলুদ মাখিয়ে রাখুন। সরষে বাটা, কাঁচা লঙ্কা আর সরষের তেল দিয়ে মাছ দশ মিনিট ভাপে রান্না করুন।
""",
}

DOCX = {
    "Documents/House rules.docx": [
        "House rules for residents",
        "Pets: residents must register any animal with the building office within two weeks of moving in.",
        "Quiet hours are from 10 pm to 7 am. Bicycles are kept in the basement store, not in the stairwells.",
        "Rubbish is collected on Monday and Thursday mornings; recycling goes in the blue bins by the gate.",
    ],
}

PDFS = {
    "Documents/Lease 2026.pdf": [
        ["Residential lease agreement", "Between the landlord, M. Rao, and the tenant, for the flat at 14 Lake Road.",
         "Term: twelve months from 1 November 2026. Rent: 25,000 rupees a month, due on the 5th."],
        ["Deposit and repairs", "A deposit of two months' rent is held and returned within 30 days of the end.",
         "The tenant reports repairs promptly; the landlord fixes plumbing and electrics within a week."],
        ["Pets and noise", "The tenant may keep one cat or one small dog with the landlord's written consent.",
         "No pets may be kept on the balcony overnight. Quiet hours follow the house rules."],
    ],
    "Download/Invoice INV-2026-0417.pdf": [
        ["Invoice INV-2026-0417", "Web hosting, October 2026: 4,200 rupees. Domain renewal: 900 rupees.",
         "Total due: 5,100 rupees by 31 October 2026. Pay by bank transfer, quoting the invoice number."],
    ],
    "Download/Insurance renewal 2027.pdf": [
        ["Health insurance renewal", "Your policy renews on 1 January 2027.",
         "The premium rises by eight percent to 18,360 rupees a year; the deductible is unchanged."],
    ],
    "Download/Warranty - phone screen.pdf": [
        ["Screen protection warranty", "Covers one free screen replacement within 24 months of purchase.",
         "Cracks from drops are covered; water damage is not. Bring this certificate and the receipt."],
    ],
}


def write_docx(path, paragraphs):
    body = "".join(f"<w:p><w:r><w:t xml:space=\"preserve\">{p}</w:t></w:r></w:p>" for p in paragraphs)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
        z.writestr("_rels/.rels", '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
        z.writestr("word/document.xml", f'<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>{body}</w:body></w:document>')


def write_pdf(path, pages):
    """A plain PDF with a real text layer (Helvetica), one page per list of lines."""
    objects = []
    def add(body):
        objects.append(body)
        return len(objects)
    font_id = add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    pages_id = len(objects) + 1 + 2 * len(pages)
    kids = []
    for lines in pages:
        esc = lambda s: s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        stream = "BT /F1 20 Tf 72 740 Td (" + esc(lines[0]) + ") Tj /F1 12 Tf 0 -36 Td 16 TL " + \
            " ".join(f"({esc(l)}) Tj T*" for l in lines[1:]) + " ET"
        content = add(f"<< /Length {len(stream)} >>\nstream\n{stream}\nendstream")
        kids.append(add(f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 612 792] "
                        f"/Contents {content} 0 R /Resources << /Font << /F1 {font_id} 0 R >> >> >>"))
    add(f"<< /Type /Pages /Kids [{' '.join(f'{k} 0 R' for k in kids)}] /Count {len(kids)} >>")
    catalog = add(f"<< /Type /Catalog /Pages {pages_id} 0 R >>")
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for i, body in enumerate(objects, 1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n{body}\nendobj\n".encode("latin-1")
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objects) + 1} /Root {catalog} 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    path.write_bytes(bytes(out))


# ---------------------------------------------------------------- photos

def gradient(size, top, bottom):
    w, h = size
    img = Image.new("RGB", size)
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return img


def beach_sunset():
    img = gradient((1600, 1200), (255, 140, 66), (255, 214, 153))
    d = ImageDraw.Draw(img)
    d.ellipse([620, 420, 980, 780], fill=(255, 236, 170))
    d.rectangle([0, 700, 1600, 950], fill=(38, 110, 160))
    for y in range(710, 950, 22):
        d.line([(200, y), (1400, y + 6)], fill=(250, 190, 120), width=3)
    d.polygon([(0, 950), (1600, 880), (1600, 1200), (0, 1200)], fill=(236, 205, 150))
    # a dog running on the sand
    d.ellipse([980, 960, 1160, 1040], fill=(120, 72, 40)); d.ellipse([1130, 920, 1210, 990], fill=(120, 72, 40))
    for x in (1000, 1040, 1100, 1140):
        d.rectangle([x, 1030, x + 14, 1090], fill=(110, 64, 34))
    d.polygon([(985, 980), (930, 950), (940, 975)], fill=(120, 72, 40))
    return img.filter(ImageFilter.GaussianBlur(0.6))


def mountain_lake():
    img = gradient((1600, 1200), (120, 170, 230), (200, 225, 245))
    d = ImageDraw.Draw(img)
    d.polygon([(0, 760), (380, 300), (700, 700), (1000, 260), (1600, 760)], fill=(90, 100, 120))
    d.polygon([(330, 360), (380, 300), (430, 360)], fill=(245, 245, 250))
    d.polygon([(940, 330), (1000, 260), (1060, 330)], fill=(245, 245, 250))
    d.rectangle([0, 760, 1600, 1200], fill=(60, 120, 150))
    for x in range(0, 1600, 70):
        d.polygon([(x, 780), (x + 30, 650), (x + 60, 780)], fill=(34, 90, 52))
    return img.filter(ImageFilter.GaussianBlur(0.6))


def birthday_cake():
    img = gradient((1400, 1400), (250, 230, 240), (240, 210, 225))
    d = ImageDraw.Draw(img)
    d.ellipse([300, 1050, 1100, 1180], fill=(255, 255, 255))
    d.rectangle([380, 700, 1020, 1100], fill=(255, 190, 210)); d.ellipse([380, 640, 1020, 760], fill=(255, 220, 230))
    d.rectangle([380, 880, 1020, 920], fill=(240, 120, 160))
    for x in (480, 600, 720, 840, 940):
        d.rectangle([x, 520, x + 22, 690], fill=(90, 160, 240)); d.ellipse([x - 6, 470, x + 28, 530], fill=(255, 190, 60))
    return img


def city_night():
    img = gradient((1600, 1100), (16, 24, 60), (40, 50, 100))
    d = ImageDraw.Draw(img)
    import random
    random.seed(7)
    x = 0
    while x < 1600:
        w, h = random.randint(90, 180), random.randint(250, 700)
        d.rectangle([x, 1100 - h, x + w, 1100], fill=(28, 32, 48))
        for wy in range(1100 - h + 20, 1080, 40):
            for wx in range(x + 12, x + w - 16, 30):
                if random.random() < 0.55: d.rectangle([wx, wy, wx + 14, wy + 20], fill=(255, 214, 120))
        x += w + random.randint(6, 20)
    d.ellipse([1300, 120, 1400, 220], fill=(240, 240, 220))
    return img


# ---------------------------------------------------------------- screenshots

def phone_screen(title, lines, accent, banner=None):
    w, h = 1080, 2340
    img = Image.new("RGB", (w, h), (246, 247, 250))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w, 120], fill=(255, 255, 255)); d.text((60, 40), "9:15", fill=(20, 20, 30), font=font(36, 600))
    d.text((60, 200), title, fill=(15, 27, 45), font=font(64, 700))
    y = 330
    if banner:
        d.rounded_rectangle([60, y, w - 60, y + 220], 28, fill=accent)
        d.text((100, y + 50), banner[0], fill=(255, 255, 255), font=font(48, 700))
        d.text((100, y + 125), banner[1], fill=(255, 255, 255), font=font(34, 500))
        y += 280
    for line in lines:
        d.rounded_rectangle([60, y, w - 60, y + 150], 24, fill=(255, 255, 255))
        d.text((100, y + 50), line, fill=(45, 55, 72), font=font(38, 500))
        y += 180
    return img


def screenshots():
    return {
        "Pictures/Screenshots/Screenshot_20261019-091512_Payments.png": phone_screen(
            "Checkout", ["Order #48213 · 2 items", "Total 2,340 rupees", "Card ending 4242"], (197, 48, 48),
            banner=("Payment declined", "Your card was declined. Try another card.")),
        "Pictures/Screenshots/Screenshot_20261020-080210_Boarding.png": phone_screen(
            "Boarding pass", ["NH 106 · Tokyo Haneda", "Gate 112 · Boards 13:55", "Seat 32K · Group 4"], (42, 68, 212),
            banner=("Flight on time", "Boarding starts at 13:55")),
        "Pictures/Screenshots/Screenshot_20261018-173005_Settings.png": phone_screen(
            "Wi-Fi", ["Home_5G · Connected", "Office guest · Saved", "Add network"], (61, 220, 151),
            banner=("Wi-Fi is on", "Connected to Home_5G")),
    }


def main():
    out = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "build/sample-data")
    for rel, text in TEXTS.items():
        p = out / rel; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text, encoding="utf-8")
    for rel, paras in DOCX.items():
        p = out / rel; p.parent.mkdir(parents=True, exist_ok=True); write_docx(p, paras)
    for rel, pages in PDFS.items():
        p = out / rel; p.parent.mkdir(parents=True, exist_ok=True); write_pdf(p, pages)
    photos = {"Pictures/beach_sunset.jpg": beach_sunset(), "Pictures/mountain_lake.jpg": mountain_lake(),
              "Pictures/birthday_cake.jpg": birthday_cake(), "DCIM/Camera/IMG_20261012_183044.jpg": city_night()}
    hills = ROOT / "app/src/androidTest/assets/reference/photo.jpg"
    for rel, img in {**photos}.items():
        p = out / rel; p.parent.mkdir(parents=True, exist_ok=True); img.save(p, quality=90)
    (out / "DCIM/Camera").mkdir(parents=True, exist_ok=True)
    (out / "DCIM/Camera/IMG_20261011_101530.jpg").write_bytes(hills.read_bytes())
    for rel, img in screenshots().items():
        p = out / rel; p.parent.mkdir(parents=True, exist_ok=True); img.save(p)
    files = sorted(f.relative_to(out).as_posix() for f in out.rglob("*") if f.is_file())
    print(f"{len(files)} files in {out}:")
    for f in files: print("  " + f)


if __name__ == "__main__":
    main()

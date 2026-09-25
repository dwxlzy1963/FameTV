#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_icons.py
Generate the launcher icon + TV banner for the FameTV Android app.
"""
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

ROOT = Path("/home/z/my-project/download/FameTV/app/src/main/res")

BG = (14, 14, 16)
ACCENT = (255, 107, 0)
WHITE = (245, 245, 245)


def find_font(size: int, bold: bool = False):
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for p in candidates:
        if Path(p).exists():
            return ImageFont.truetype(p, size=size)
    return ImageFont.load_default()


def rounded_rect(draw, xy, radius, fill, outline=None, width=0):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def gen_banner():
    W, H = 320, 180
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 8, H], fill=ACCENT)
    cx, cy = 90, H // 2
    s = 60
    triangle = [(cx - s // 2, cy - s // 2), (cx - s // 2, cy + s // 2), (cx + s // 2, cy)]
    d.polygon(triangle, fill=ACCENT)
    f = find_font(46, bold=True)
    d.text((150, 50), "FameTV", font=f, fill=WHITE)
    f2 = find_font(16, bold=False)
    d.text((152, 110), "Free Live TV Worldwide", font=f2, fill=(156, 163, 175))
    out = ROOT / "drawable" / "app_banner.png"
    img.save(out, "PNG")
    print(f"[banner] {out}  ({W}x{H})")


def gen_logo():
    W = H = 96
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rounded_rect(d, [4, 4, W - 4, H - 4], radius=18, fill=BG, outline=ACCENT, width=2)
    cx, cy = W // 2, H // 2
    s = 36
    triangle = [(cx - s // 2, cy - s // 2), (cx - s // 2, cy + s // 2), (cx + s // 2, cy)]
    d.polygon(triangle, fill=ACCENT)
    out = ROOT / "drawable" / "app_logo.png"
    img.save(out, "PNG")
    print(f"[logo] {out}  ({W}x{H})")


def gen_launcher_icons():
    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for name, size in sizes.items():
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        rounded_rect(d, [0, 0, size, size], radius=size // 6, fill=BG, outline=ACCENT, width=max(1, size // 48))
        cx, cy = size // 2, size // 2
        s = int(size * 0.5)
        triangle = [(cx - s // 2, cy - s // 2), (cx - s // 2, cy + s // 2), (cx + s // 2, cy)]
        d.polygon(triangle, fill=ACCENT)
        out_dir = ROOT / name
        out_dir.mkdir(parents=True, exist_ok=True)
        out = out_dir / "ic_launcher.png"
        img.save(out, "PNG")
        out2 = out_dir / "ic_launcher_round.png"
        img.save(out2, "PNG")
        print(f"[icon] {out}  ({size}x{size})")


def gen_adaptive_xml():
    out_dir = ROOT / "mipmap-anydpi-v26"
    out_dir.mkdir(parents=True, exist_ok=True)
    for fname in ("ic_launcher.xml", "ic_launcher_round.xml"):
        xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""
        (out_dir / fname).write_text(xml, encoding="utf-8")
        print(f"[adaptive] {out_dir / fname}")


def gen_launcher_foreground():
    out = ROOT / "drawable" / "ic_launcher_foreground.xml"
    xml = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF6B00"
        android:pathData="M44,36L44,72L72,54Z" />
</vector>
"""
    out.write_text(xml, encoding="utf-8")
    print(f"[foreground] {out}")


if __name__ == "__main__":
    gen_banner()
    gen_logo()
    gen_launcher_icons()
    gen_adaptive_xml()
    gen_launcher_foreground()
    print("[done] all icons generated")

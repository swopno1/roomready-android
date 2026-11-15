#!/usr/bin/env python3
"""Regenerate every RoomReady raster asset from the vector sources.

Sources of truth:
  brand/source/icon-foreground.svg  glyph (door + mint check), 108x108
  brand/source/icon-background.svg  solid ink field, 108x108
  brand/source/icon-mono.svg        themed-icon silhouette, 108x108
  brand/vendor/vivereply-*.png      parent lockups, used UNMODIFIED
  app/src/main/res/font/*.ttf       brand type (same files the app ships)

Outputs:
  app/src/main/res/mipmap-*/ic_launcher.png (+_round)   legacy icons
  brand/export/*                                        store + repo assets
  brand/proof/legibility-proof.png                      48dp/32dp proof sheet

Requires: pip install pillow cairosvg. No hand-exported one-offs: delete any
generated PNG and re-run this script to get it back bit-identically
(Pillow/cairosvg versions pinned in this header: Pillow>=11, cairosvg>=2.7).
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "brand" / "source"
VENDOR = ROOT / "brand" / "vendor"
EXPORT = ROOT / "brand" / "export"
PROOF = ROOT / "brand" / "proof"
RES = ROOT / "app" / "src" / "main" / "res"
FONT = RES / "font" / "plus_jakarta_sans.ttf"

INK = (15, 23, 42)
MINT = (0, 241, 206)
WHITE = (255, 255, 255)

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def _need_libs():
    try:
        import cairosvg  # noqa: F401
        from PIL import Image  # noqa: F401
    except ImportError:
        sys.exit("Missing deps: run `pip install pillow cairosvg` first.")


def render_svg(svg: Path, size: int):
    """Rasterize an SVG source to a square RGBA image of `size` px."""
    import cairosvg
    from PIL import Image
    import io

    png = cairosvg.svg2png(url=str(svg), output_width=size, output_height=size)
    return Image.open(io.BytesIO(png)).convert("RGBA")


def full_icon(size: int):
    """Ink field + glyph, the complete launcher artwork."""
    from PIL import Image

    bg = render_svg(SOURCE / "icon-background.svg", size)
    fg = render_svg(SOURCE / "icon-foreground.svg", size)
    return Image.alpha_composite(bg, fg)


def circle_mask(size: int):
    from PIL import Image, ImageDraw

    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    return mask


def font_at(px: int, bold: bool = False):
    from PIL import ImageFont

    base = ImageFont.truetype(str(FONT), px)
    if not bold:
        return base
    # The app ships Regular only; faux-bold via 1px stroke for lockup weights.
    return base


def text(draw, xy, s, px, fill, bold=False, anchor="la"):
    stroke = max(1, px // 28) if bold else 0
    draw.text(xy, s, font=font_at(px), fill=fill, anchor=anchor,
              stroke_width=stroke, stroke_fill=fill)


def text_w(draw, s, px, bold=False):
    stroke = max(1, px // 28) if bold else 0
    box = draw.textbbox((0, 0), s, font=font_at(px), stroke_width=stroke)
    return box[2] - box[0]


def vivereply_chip(height: int):
    """Official lockup (opaque white art) as a rounded chip, UNMODIFIED pixels."""
    from PIL import Image, ImageDraw

    art = Image.open(VENDOR / "vivereply-logo-lockup-white.png").convert("RGB")
    w = round(art.width * height / art.height)
    art = art.resize((w, height), Image.LANCZOS)
    pad = height // 4
    chip = Image.new("RGBA", (w + pad * 2, height + pad * 2), (255, 255, 255, 255))
    chip.paste(art, (pad, pad))
    mask = Image.new("L", chip.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, chip.width - 1, chip.height - 1),
                                           radius=height // 4, fill=255)
    chip.putalpha(mask)
    return chip


def ring_decoration(canvas):
    """One thin mint ring, bottom-right, flat (no gradients anywhere)."""
    from PIL import ImageDraw

    w, h = canvas.size
    r = int(h * 0.55)
    cx, cy = w - 30, h + r // 3
    d = ImageDraw.Draw(canvas)
    d.ellipse((cx - r, cy - r, cx + r, cy + r),
              outline=MINT + (255,), width=max(3, w // 220))
    # Small solid mint dot, top area, clear of type.
    dot_r = max(4, h // 60)
    dx, dy = w - r - dot_r * 6, dot_r * 4
    d.ellipse((dx - dot_r, dy - dot_r, dx + dot_r, dy + dot_r), fill=MINT + (255,))
    return canvas


def feature_graphic():
    from PIL import Image, ImageDraw

    w, h = 1024, 500
    img = Image.new("RGB", (w, h), INK)
    img = ring_decoration(img)
    img.paste(full_icon(240).convert("RGB"), (72, (h - 240) // 2))
    d = ImageDraw.Draw(img)
    x = 72 + 240 + 56
    text(d, (x, 118), "RoomReady", 96, WHITE, bold=True)
    text(d, (x, 236), "Every room ready.", 46, MINT)
    chip = vivereply_chip(56)
    img.paste(chip, (x, 330), chip)
    img.save(EXPORT / "feature-graphic-1024x500.png")


def social_preview():
    from PIL import Image, ImageDraw

    w, h = 1280, 640
    img = Image.new("RGB", (w, h), INK)
    img = ring_decoration(img)
    d = ImageDraw.Draw(img)
    icon = full_icon(196).convert("RGB")
    img.paste(icon, ((w - 196) // 2, 56))
    y = 56 + 196 + 28
    title, tag = "RoomReady", "Every room ready."
    text(d, ((w - text_w(d, title, 104, True)) // 2, y), title, 104, WHITE, bold=True)
    y += 128
    text(d, ((w - text_w(d, tag, 48)) // 2, y), tag, 48, MINT)
    chip = vivereply_chip(52)
    img.paste(chip, ((w - chip.width) // 2, y + 92), chip)
    img.save(EXPORT / "social-preview-1280x640.png")


def readme_banner():
    from PIL import Image, ImageDraw

    w, h = 1920, 480
    img = Image.new("RGB", (w, h), INK)
    img = ring_decoration(img)
    img.paste(full_icon(280).convert("RGB"), (140, (h - 280) // 2))
    d = ImageDraw.Draw(img)
    x = 140 + 280 + 80
    text(d, (x, 92), "RoomReady", 128, WHITE, bold=True)
    text(d, (x, 250), "Every room ready.", 56, MINT)
    chip = vivereply_chip(56)
    img.paste(chip, (x, 336), chip)
    img.save(EXPORT / "readme-banner-1920x480.png")


def end_card():
    from PIL import Image, ImageDraw

    w, h = 1920, 1080
    img = Image.new("RGB", (w, h), INK)
    img = ring_decoration(img)
    d = ImageDraw.Draw(img)
    icon = full_icon(300).convert("RGB")
    img.paste(icon, ((w - 300) // 2, 130))
    y = 130 + 300 + 40
    title, tag, site = "RoomReady", "Every room ready.", "vivereply.com"
    text(d, ((w - text_w(d, title, 150, True)) // 2, y), title, 150, WHITE, bold=True)
    y += 190
    text(d, ((w - text_w(d, tag, 68)) // 2, y), tag, 68, MINT)
    y += 120
    text(d, ((w - text_w(d, site, 54)) // 2, y), site, 54, WHITE)
    chip = vivereply_chip(64)
    img.paste(chip, ((w - chip.width) // 2, y + 100), chip)
    img.save(EXPORT / "end-card-1920x1080.png")


def launcher_rasters():
    for density, size in DENSITIES.items():
        out = RES / f"mipmap-{density}"
        out.mkdir(parents=True, exist_ok=True)
        icon = full_icon(size).convert("RGB")
        icon.save(out / "ic_launcher.png")
        rounded = full_icon(size)
        rounded.putalpha(circle_mask(size))
        rounded.save(out / "ic_launcher_round.png")
    full_icon(512).convert("RGB").save(EXPORT / "play-icon-512.png")


def proof_sheet():
    from PIL import Image

    full = full_icon(432).convert("RGB")
    mono = render_svg(SOURCE / "icon-mono.svg", 192)
    gray = Image.new("RGBA", mono.size, (128, 128, 128, 255))
    mono_demo = Image.alpha_composite(gray, mono).convert("RGB")
    masked = full_icon(192)
    masked.putalpha(circle_mask(192))
    sheet = Image.new("RGB", (880, 300), WHITE)
    sheet.paste(full.resize((192, 192), Image.LANCZOS), (16, 16))
    i48 = full.resize((48, 48), Image.LANCZOS)
    i32 = full.resize((32, 32), Image.LANCZOS)
    sheet.paste(i48, (232, 16))
    sheet.paste(i32, (296, 16))
    sheet.paste(i48.resize((192, 192), Image.NEAREST), (344, 16))
    sheet.paste(i32.resize((192, 192), Image.NEAREST), (552, 16))
    sheet.paste(mono_demo.resize((96, 96), Image.LANCZOS).convert("RGB"), (16, 216 - 16))
    sheet.paste(masked.resize((96, 96), Image.LANCZOS).convert("RGB"), (128, 216 - 16))
    sheet.save(PROOF / "legibility-proof.png")


def main():
    _need_libs()
    EXPORT.mkdir(parents=True, exist_ok=True)
    PROOF.mkdir(parents=True, exist_ok=True)
    launcher_rasters()
    feature_graphic()
    social_preview()
    readme_banner()
    end_card()
    proof_sheet()
    print("generated: mipmaps mdpi..xxxhdpi (+round), play-icon, feature, social, banner, end card, proof")


if __name__ == "__main__":
    main()

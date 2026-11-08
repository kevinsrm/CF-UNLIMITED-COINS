#!/usr/bin/env python3
"""Generates the CF LOCK launcher icon set: legacy PNGs per density plus an
adaptive icon pair (foreground/background) for API 26 and up."""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(ROOT, "..", "res"))
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

BG = (11, 11, 11, 255)
RED = (255, 59, 48, 255)
STEEL = (235, 235, 235, 255)


def draw_cf(size, pad_ratio=0.0, mono=False):
    """CF monogram on a dark tile. pad_ratio insets content for adaptive masks."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    inset = int(size * pad_ratio)
    tile = (inset, inset, size - inset, size - inset)
    d.rounded_rectangle(tile, radius=int(size * 0.22), fill=BG)

    box_w = size - 2 * inset
    fsize = int(box_w * 0.46)
    font = ImageFont.truetype(FONT_BOLD, fsize)
    text = "CF"
    bbox = d.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (size - tw) / 2 - bbox[0]
    y = (size - th) / 2 - bbox[1]
    d.text((x, y), text, font=font, fill=(STEEL if mono else RED))

    bar_h = max(2, int(size * 0.035))
    bar_y = int(size * 0.5 + th * 0.62)
    d.rectangle(
        (int(size * 0.24), bar_y, int(size * 0.76), bar_y + bar_h), fill=(255, 59, 48, 255)
    )
    return img


def main():
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, px in densities.items():
        out = os.path.join(RES, "mipmap-" + name)
        os.makedirs(out, exist_ok=True)
        draw_cf(px).save(os.path.join(out, "ic_launcher.png"))
        base = draw_cf(px * 2)
        mask = Image.new("L", base.size, 0)
        ImageDraw.Draw(mask).ellipse((0, 0, base.size[0], base.size[1]), fill=255)
        rounded = Image.new("RGBA", base.size, (0, 0, 0, 0))
        rounded.paste(base, (0, 0), mask)
        rounded.resize((px, px), Image.LANCZOS).save(os.path.join(out, "ic_launcher_round.png"))

    fg = draw_cf(432)
    bg = Image.new("RGBA", (432, 432), BG)
    out = os.path.join(RES, "mipmap-xxxhdpi")
    os.makedirs(out, exist_ok=True)
    bg.save(os.path.join(out, "ic_launcher_background.png"))
    fg.save(os.path.join(out, "ic_launcher_foreground.png"))
    print("icons written")


if __name__ == "__main__":
    main()

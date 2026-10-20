#!/usr/bin/env python3
"""Draws the mod's 16x16 item icons and the Tackle Bench block textures with Pillow.

Run from anywhere; writes into src/main/resources/assets/create_galore_fishing/textures/. The rods' in-world look
is a 3D model drawn in code; these icons are their flat fallback.

    python3 tools/fishing-mod/art/make_textures.py
"""
import pathlib
from PIL import Image, ImageDraw

HERE = pathlib.Path(__file__).resolve().parent
ITEM = HERE.parent / "src/main/resources/assets/create_galore_fishing/textures/item"
BLOCK = HERE.parent / "src/main/resources/assets/create_galore_fishing/textures/block"


def rgb(hexcolor, a=255):
    h = hexcolor.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def save(img, name, folder=ITEM):
    folder.mkdir(parents=True, exist_ok=True)
    img.save(folder / f"{name}.png")


def rod(name, blank, handle="#3a2a1a", reel=None, sections=3):
    """A rod drawn corner to corner like the vanilla fishing rod, with a handle, guides and optional reel."""
    img = canvas()
    b = rgb(blank)
    for i in range(14):
        x, y = 1 + i, 14 - i
        c = shade(b, 1.1 - 0.25 * (i / 14))
        px(img, x, y, c)
        if i < 4:
            px(img, x + 1, y, shade(rgb(handle), 1.0))
        # section joints
        if sections > 1 and i % max(2, 14 // sections) == 0 and i > 3:
            px(img, x, y - 1, shade(b, 0.7))
    for gx in (6, 9, 12):  # guides
        px(img, gx, 16 - gx - 2, (200, 200, 200, 255))
    if reel:
        r = rgb(reel)
        for (x, y) in ((3, 10), (4, 10), (3, 11), (4, 11)):
            px(img, x, y, r)
        px(img, 2, 11, shade(r, 0.7))
    # hanging line
    for y in range(2, 9):
        px(img, 14, y, (230, 230, 230, 200))
    save(img, name)


def pole(name):
    img = canvas()
    for i in range(15):
        x, y = i, 15 - i
        c = (201, 179, 107, 255) if i % 5 else (150, 130, 70, 255)
        px(img, x, y, c)
        px(img, x + 1, y, shade(c, 0.85))
    for y in range(1, 10):
        px(img, 15, y, (230, 230, 230, 200))
    save(img, name)


def reel(name, color, style):
    img = canvas()
    d = ImageDraw.Draw(img)
    c = rgb(color)
    if style == "trolling":
        d.rectangle((3, 4, 12, 12), fill=c, outline=shade(c, 0.6))
        d.rectangle((5, 6, 10, 10), fill=shade(c, 0.8))
        d.line((12, 8, 15, 6), fill=(60, 40, 30, 255))
        d.rectangle((14, 4, 15, 6), fill=(40, 40, 40, 255))
    elif style == "baitcaster":
        d.ellipse((2, 5, 13, 12), fill=c, outline=shade(c, 0.6))
        d.rectangle((5, 7, 10, 10), fill=shade(c, 1.2))
        d.line((13, 8, 15, 10), fill=(40, 40, 40, 255))
    else:
        d.ellipse((3, 3, 12, 12), fill=c, outline=shade(c, 0.55))
        d.ellipse((5, 5, 10, 10), fill=shade(c, 1.25))
        d.ellipse((7, 7, 8, 8), fill=shade(c, 0.5))
        d.line((11, 8, 14, 11), fill=(70, 50, 30, 255))
        px(img, 14, 12, (40, 30, 20, 255))
        px(img, 7, 13, shade(c, 0.6))
        px(img, 8, 14, shade(c, 0.6))
    save(img, name)


def spool(name, color):
    img = canvas()
    d = ImageDraw.Draw(img)
    d.rectangle((3, 3, 12, 4), fill=(120, 90, 60, 255))
    d.rectangle((3, 11, 12, 12), fill=(120, 90, 60, 255))
    c = rgb(color)
    for y in range(5, 11):
        for x in range(4, 12):
            px(img, x, y, shade(c, 0.85 + 0.15 * ((x + y) % 2)))
    d.line((12, 7, 15, 2), fill=shade(c, 1.0))
    save(img, name)


def leader(name, color):
    img = canvas()
    c = rgb(color)
    d = ImageDraw.Draw(img)
    for r in (6, 4):
        d.ellipse((8 - r, 8 - r, 8 + r, 8 + r), outline=c)
    d.line((12, 4, 15, 1), fill=c)
    save(img, name)


def hook(name, size, color="#c8c8c8"):
    img = canvas()
    c = rgb(color)
    top = 2 + (3 - size)
    bottom = 12
    for y in range(top, bottom):
        px(img, 9, y, c)
    px(img, 8, top - 1, shade(c, 0.8))
    px(img, 9, top - 1, shade(c, 0.8))
    px(img, 8, bottom, c)
    px(img, 7, bottom + 1, c)
    px(img, 6, bottom + 1, c)
    px(img, 5, bottom, c)
    px(img, 5, bottom - 1, c)
    px(img, 5, bottom - 2, c)
    px(img, 6, bottom - 3, shade(c, 1.2))
    save(img, name)


def bobber(name):
    img = canvas()
    d = ImageDraw.Draw(img)
    d.ellipse((4, 4, 11, 11), fill=(240, 240, 240, 255))
    d.chord((4, 4, 11, 11), 180, 360, fill=(208, 48, 48, 255))
    d.line((7, 1, 7, 4), fill=(60, 60, 60, 255))
    d.line((7, 11, 7, 14), fill=(60, 60, 60, 255))
    save(img, name)


def sinker(name):
    img = canvas()
    d = ImageDraw.Draw(img)
    d.ellipse((5, 6, 11, 13), fill=(110, 110, 120, 255), outline=(70, 70, 80, 255))
    d.polygon([(7, 3), (9, 3), (10, 7), (6, 7)], fill=(110, 110, 120, 255))
    px(img, 8, 2, (200, 200, 200, 255))
    save(img, name)


def lure(name, kind, color):
    img = canvas()
    d = ImageDraw.Draw(img)
    c = rgb(color)
    if kind == "spoon":
        d.ellipse((4, 3, 11, 13), fill=c, outline=shade(c, 0.6))
        d.ellipse((6, 5, 8, 9), fill=shade(c, 1.3))
    elif kind == "spinner":
        d.line((2, 8, 14, 8), fill=(180, 180, 180, 255))
        d.ellipse((5, 4, 10, 12), fill=c, outline=shade(c, 0.6))
        d.rectangle((11, 7, 13, 9), fill=(200, 40, 40, 255))
    elif kind in ("crankbait", "deep_crankbait"):
        d.ellipse((3, 5, 12, 11), fill=c, outline=shade(c, 0.6))
        d.polygon([(12, 8), (15, 6), (15, 10)], fill=shade(c, 0.8))
        lip = 4 if kind == "deep_crankbait" else 2
        d.line((3, 9, 3 - 1, 9 + lip), fill=(220, 230, 240, 200))
        px(img, 5, 7, (10, 10, 10, 255))
    elif kind == "jig":
        d.ellipse((3, 3, 8, 8), fill=c)
        for i in range(5):
            d.line((6, 7, 9 + i, 13), fill=shade(c, 0.8 + 0.1 * i))
    elif kind == "popper":
        d.ellipse((3, 5, 13, 11), fill=c, outline=shade(c, 0.6))
        d.ellipse((2, 6, 5, 10), fill=shade(c, 0.5))
        px(img, 8, 7, (10, 10, 10, 255))
    elif kind == "soft_worm":
        pts = [(2, 10), (4, 8), (6, 9), (8, 11), (10, 9), (12, 6), (14, 5)]
        d.line(pts, fill=c, width=2)
    save(img, name)


def bench():
    top = Image.new("RGBA", (16, 16))
    side = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            grain = ((x * 7 + y * 3) % 5) / 30
            top.putpixel((x, y), shade((150, 112, 70, 255), 0.9 + grain))
            side.putpixel((x, y), shade((120, 88, 52, 255), 0.9 + grain))
    d = ImageDraw.Draw(top)
    d.line((2, 13, 13, 2), fill=(180, 180, 180, 255))  # a rod lying on top
    d.ellipse((9, 9, 13, 13), outline=(200, 160, 60, 255))  # a reel
    d.rectangle((2, 2, 5, 5), outline=(210, 210, 210, 255))  # a spool
    ds = ImageDraw.Draw(side)
    ds.rectangle((0, 0, 15, 3), fill=(150, 112, 70, 255))
    ds.line((0, 3, 15, 3), fill=(90, 66, 40, 255))
    ds.rectangle((2, 6, 13, 9), outline=(90, 66, 40, 255))  # drawer
    px(side, 7, 7, (200, 200, 200, 255))
    px(side, 8, 7, (200, 200, 200, 255))
    save(top, "tackle_bench_top", BLOCK)
    save(side, "tackle_bench_side", BLOCK)


def main():
    pole("bamboo_pole")
    rod("wooden_rod", "#8a6a45", reel="#c87840", sections=2)
    rod("iron_rod", "#b8b8b8", reel="#a8a8a8")
    rod("steel_rod", "#4a5560", reel="#d8b040", sections=5)
    rod("boat_rod", "#2f3b4a", handle="#1a1a1a", reel="#c0a040", sections=2)
    rod("netherite_rod", "#4a3f43", handle="#2a1a1a", reel="#d0a040", sections=4)
    for name, color, style in (("copper_reel", "#c87840", "spinning"), ("iron_reel", "#a8a8a8", "spinning"),
                               ("brass_reel", "#d8b040", "spinning"), ("steel_baitcaster", "#506070", "baitcaster"),
                               ("trolling_reel", "#c0a040", "trolling")):
        reel(name, color, style)
    for name, color in (("string_line", "#e8e8e8"), ("mono_line", "#b8d8a8"), ("clear_line", "#c8e8ff"),
                        ("braided_line", "#3a6a3a"), ("heavy_line", "#f0d070")):
        spool(name, color)
    leader("clear_leader", "#c8e8ff")
    leader("steel_leader", "#909aa0")
    hook("small_hook", 0)
    hook("medium_hook", 1)
    hook("large_hook", 2)
    hook("big_game_hook", 3)
    hook("golden_hook", 1, "#e8c040")
    hook("netherite_hook", 2, "#5a4a50")
    bobber("float")
    sinker("sinker")
    for name, color in (("spoon", "#d0d0d0"), ("spinner", "#e0b040"), ("crankbait", "#40a050"),
                        ("deep_crankbait", "#305080"), ("jig", "#a04040"), ("popper", "#f0f0a0"), ("soft_worm", "#8050a0")):
        lure(name, name, color)
    bench()
    print("textures written")


if __name__ == "__main__":
    main()

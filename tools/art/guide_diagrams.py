#!/usr/bin/env python3
"""Draws the Field Guide's diagram pages (200x200, shown at half size on a Modonomicon image page).
Usage: python3 tools/art/guide_diagrams.py <textures dir>
<textures dir> holds PNGs copied out of the game and mod jars as <namespace>/<path>.png, e.g.
minecraft/item/raw_iron.png, minecraft/font/ascii.png, create/item/crushed_raw_iron.png (see TEXTURES below).
Writes pack/config/paxi/resourcepacks/create-galore-guide/assets/create_galore/textures/gui/guide/*.png"""
import math, sys, pathlib
from PIL import Image, ImageDraw

TEX = pathlib.Path(sys.argv[1])
OUT = pathlib.Path(__file__).resolve().parents[2] / 'pack/config/paxi/resourcepacks/create-galore-guide/assets/create_galore/textures/gui/guide'
INK = (54, 42, 30, 255)
SOFT = (120, 104, 84, 255)
RED, ORANGE, GREEN, BLUE = (170, 40, 30, 255), (190, 110, 20, 255), (60, 120, 40, 255), (40, 90, 170, 255)
TEXTURES = ['minecraft/font/ascii', 'minecraft/block/iron_ore', 'minecraft/item/raw_iron', 'create/item/crushed_raw_iron',
            'minecraft/item/iron_nugget', 'minecraft/item/iron_ingot', 'minecraft/item/redstone',
            'minecraft/block/water_still', 'minecraft/block/blast_furnace_front_on', 'minecraft/item/campfire',
            'minecraft/block/furnace_front_on', 'minecraft/item/potion', 'minecraft/item/potion_overlay']

def tex(name, scale=2):
    im = Image.open(TEX / f'{name}.png').convert('RGBA')
    im = im.crop((0, 0, 16, 16))  # animated textures are strips; first frame
    return im.resize((16 * scale, 16 * scale), Image.NEAREST)

# Minecraft's own pixel font, so labels match the book's text
_font = Image.open(TEX / 'minecraft/font/ascii.png').convert('RGBA')
def glyph_width(c):
    gx, gy = (ord(c) % 16) * 8, (ord(c) // 16) * 8
    if c == ' ': return 4
    w = 0
    for x in range(8):
        if any(_font.getpixel((gx + x, gy + y))[3] for y in range(8)): w = x + 1
    return w + 1
def text_width(s, scale=2): return (sum(glyph_width(c) for c in s) - 1) * scale
def text(img, x, y, s, color=INK, center=False, scale=2):
    # labels are drawn at 2x: the page shows the image at half size, so this matches the book's own text size
    if center: x -= text_width(s, scale) // 2
    for c in s:
        gx, gy = (ord(c) % 16) * 8, (ord(c) // 16) * 8
        g = _font.crop((gx, gy, gx + 8, gy + 8)).resize((8 * scale, 8 * scale), Image.NEAREST)
        img.paste(Image.new('RGBA', g.size, color), (x, y), g)
        x += glyph_width(c) * scale

def arrow(d, x0, y, x1, color=SOFT):
    d.line([(x0, y), (x1 - 3, y)], fill=color, width=2)
    d.polygon([(x1, y), (x1 - 5, y - 4), (x1 - 5, y + 4)], fill=color)
def varrow(d, x, y0, y1, color=SOFT):
    d.line([(x, y0), (x, y1 - 3)], fill=color, width=2)
    d.polygon([(x, y1), (x - 4, y1 - 5), (x + 4, y1 - 5)], fill=color)

def tint(im, rgb):
    r, g, b = rgb; px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            c = px[x, y]
            px[x, y] = (c[0] * r // 255, c[1] * g // 255, c[2] * b // 255, c[3])
    return im

def wheels():
    # Create's crushing wheel is a 3D model with no flat item texture, so draw the pair: two toothed stone wheels
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    for cx in (9, 23):
        d.ellipse([cx - 8, 8, cx + 8, 24], fill=(130, 128, 124, 255), outline=(70, 68, 66, 255))
        for k in range(8):
            a = k * math.pi / 4; x, y = cx + 8 * math.cos(a), 16 + 8 * math.sin(a)
            d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=(70, 68, 66, 255))
        d.ellipse([cx - 2, 14, cx + 2, 18], fill=(90, 70, 50, 255))
    return im

def canvas(): return Image.new('RGBA', (200, 200), (0, 0, 0, 0))

def flow_row(img, d, y, items, label, label_color, result=''):
    """One row: a label, then item -> item -> item and an optional result count, 32 px items."""
    text(img, 100, y, label, label_color, center=True)
    y += 22
    xs = [0, 56, 112]
    for x, it in zip(xs, items): img.alpha_composite(it, (x, y))
    arrow(d, 35, y + 16, 54); arrow(d, 91, y + 16, 110)
    if result: text(img, 148, y + 8, result, INK)

def ore_doubling():
    img = canvas(); d = ImageDraw.Draw(img)
    wheel, crushed = wheels(), tex('create/item/crushed_raw_iron')
    flow_row(img, d, 14, [tex('minecraft/block/iron_ore'), wheel, crushed], 'Ore block', GREEN, 'x1.75')
    flow_row(img, d, 110, [tex('minecraft/item/raw_iron'), wheel, crushed], 'Raw ore', RED, 'x1')
    return img

def crushed_to_metal():
    img = canvas(); d = ImageDraw.Draw(img)
    crushed = tex('create/item/crushed_raw_iron')
    flow_row(img, d, 14, [crushed, tint(tex('minecraft/block/water_still'), (63, 118, 228)), tex('minecraft/item/iron_nugget')], 'Washing', BLUE, 'x9')
    flow_row(img, d, 110, [crushed, tex('minecraft/block/blast_furnace_front_on'), tex('minecraft/item/iron_ingot')], 'Smelting', ORANGE, 'x1')
    return img

def water_purity():
    img = canvas(); d = ImageDraw.Draw(img)
    steps = [('Dirty', RED), ('Slightly Dirty', ORANGE), ('Acceptable', GREEN), ('Purified', BLUE)]
    water = [(120, 92, 52), (122, 128, 88), (110, 170, 220), (60, 118, 228)]  # muddy to clear
    bottle = tex('minecraft/item/potion')
    camp = tex('minecraft/item/campfire', 1)
    for k, ((name, col), w) in enumerate(zip(steps, water)):
        y = 4 + k * 52
        b = tint(tex('minecraft/item/potion_overlay'), w); b.alpha_composite(bottle)
        img.alpha_composite(b, (0, y))
        text(img, 38, y + 8, name, col)
        if k < 3:  # one boil raises purity one step
            varrow(d, 16, y + 34, y + 52)
            img.alpha_composite(camp, (22, y + 34))
    return img

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in [('ore_doubling', ore_doubling), ('crushed_to_metal', crushed_to_metal), ('water_purity', water_purity)]:
        im = fn(); im.save(OUT / f'{name}.png')
        bg = Image.new('RGBA', im.size, (238, 228, 205, 255)); bg.alpha_composite(im)
        bg.resize((600, 600), Image.NEAREST).save(f'/tmp/claude-0/diagram_{name}.png')
        print('wrote', name)
main()

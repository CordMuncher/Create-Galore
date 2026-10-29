#!/usr/bin/env python3
"""Draws the Reel Drive window's GUI sprites with Pillow: the control deck, the stone push buttons (up, hover, pressed
in, pressed in and hovered), the readout window, the raised slot plate and the etched glyphs.

Run from anywhere; writes into src/main/resources/assets/create_galore_fishing/textures/gui/sprites/drive/, where
Minecraft stitches them into its GUI atlas (each nine-slice or tiled sprite has its .mcmeta next to it).

    python3 tools/fishing-mod/art/make_gui.py

The look follows Create's machine screens: light stone panels with a one-pixel black outline, white top-left and
dark bottom-right bevels, and a dark patterned deck the controls sit on. Glyphs are cut into the surface rather than
painted on: each is a dark groove with a light lip one pixel below and to the right, so they read as engraved in
stone on a button and in metal on the deck. A toggle that is on is drawn pressed in (darker face, bevel reversed).
"""
import json
import pathlib

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parent / "src/main/resources/assets/create_galore_fishing/textures/gui/sprites/drive"

BLACK = (0, 0, 0, 255)
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)


def rgba(hexcolor, a=255):
    h = hexcolor.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a


def save(name, im, scaling=None):
    OUT.mkdir(parents=True, exist_ok=True)
    im.save(OUT / f"{name}.png")
    meta = OUT / f"{name}.png.mcmeta"
    if scaling:
        meta.write_text(json.dumps({"gui": {"scaling": scaling}}, indent=2) + "\n")
    elif meta.exists():
        meta.unlink()


def fill(im, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            im.putpixel((x, y), c)


# ---- surfaces ----

def deck():
    """16x16 tile of the dark control deck: a slate-blue sheet with a faint diagonal weave, like Create's
    patterned screen backs but darker so the stone controls stand out."""
    im = Image.new("RGBA", (16, 16))
    a, b, c = rgba("#4c5d66"), rgba("#475761"), rgba("#53656e")
    for y in range(16):
        for x in range(16):
            col = a if (x + y) % 2 == 0 else b
            if (x + y) % 8 == 0 and x % 2 == 0:
                col = c
            im.putpixel((x, y), col)
    save("deck", im, {"type": "tile", "width": 16, "height": 16})


def button(name, face, top, bottom):
    """16x16 nine-slice stone button: black outline, a one-pixel bevel inside it."""
    im = Image.new("RGBA", (16, 16), face)
    fill(im, 0, 0, 16, 1, BLACK)
    fill(im, 0, 15, 16, 16, BLACK)
    fill(im, 0, 0, 1, 16, BLACK)
    fill(im, 15, 0, 16, 16, BLACK)
    fill(im, 1, 1, 15, 2, top)
    fill(im, 1, 1, 2, 15, top)
    fill(im, 1, 14, 15, 15, bottom)
    fill(im, 14, 2, 15, 15, bottom)
    # Corners where light meets shade take the face colour, as vanilla's bevels do.
    im.putpixel((14, 1), face)
    im.putpixel((1, 14), face)
    save(name, im, {"type": "nine_slice", "width": 16, "height": 16, "border": 2})


def buttons():
    button("button", rgba("#c6c6c6"), WHITE, rgba("#555555"))
    button("button_hover", rgba("#d4d6da"), WHITE, rgba("#5d6168"))
    # Pressed in: darker face, shade on the top left where the rim now overhangs it.
    button("button_on", rgba("#8b8b8b"), rgba("#4a4a4a"), rgba("#a8a8a8"))
    button("button_on_hover", rgba("#979a9f"), rgba("#4f5156"), rgba("#b3b6bb"))
    button("button_off", rgba("#a9a9a9"), rgba("#c8c8c8"), rgba("#6c6c6c"))


def window():
    """16x16 nine-slice readout window, sunk into the deck: dark glass under a black lip, a lighter sill below."""
    im = Image.new("RGBA", (16, 16), rgba("#1d2328"))
    fill(im, 0, 0, 16, 1, rgba("#2a3238"))
    fill(im, 0, 0, 1, 16, rgba("#2a3238"))
    fill(im, 0, 15, 16, 16, rgba("#7d8f98"))
    fill(im, 15, 0, 16, 16, rgba("#7d8f98"))
    fill(im, 1, 1, 15, 2, rgba("#141819"))
    save("window", im, {"type": "nine_slice", "width": 16, "height": 16, "border": 2})


def plate():
    """16x16 nine-slice raised stone plate the item slots sit in."""
    im = Image.new("RGBA", (16, 16), rgba("#c6c6c6"))
    fill(im, 0, 0, 16, 1, BLACK)
    fill(im, 0, 15, 16, 16, BLACK)
    fill(im, 0, 0, 1, 16, BLACK)
    fill(im, 15, 0, 16, 16, BLACK)
    fill(im, 1, 1, 15, 3, WHITE)
    fill(im, 1, 1, 3, 15, WHITE)
    fill(im, 1, 13, 15, 15, rgba("#555555"))
    fill(im, 13, 3, 15, 15, rgba("#555555"))
    fill(im, 2, 2, 3, 3, rgba("#e8e8e8"))
    save("plate", im, {"type": "nine_slice", "width": 16, "height": 16, "border": 3})


def slot():
    """18x18 item slot, as vanilla's: sunk, dark top-left, white bottom-right."""
    im = Image.new("RGBA", (18, 18), rgba("#8b8b8b"))
    fill(im, 0, 0, 17, 1, rgba("#373737"))
    fill(im, 0, 0, 1, 17, rgba("#373737"))
    fill(im, 1, 17, 18, 18, WHITE)
    fill(im, 17, 1, 18, 18, WHITE)
    save("slot", im)


# ---- glyphs ----
# 10x10 masks, '#' is the groove. Each is cut: groove dark, lip light one pixel down-right where the surface shows.

GLYPHS = {
    # IEC power symbol.
    "power": """
....##....
.#..##..#.
#...##...#
#...##...#
#...##...#
#........#
#........#
.#......#.
..######..
..........
""",
    # Two arrows chasing round: casts again on its own.
    "autocast": """
..#####...
.#.....#.#
#.......##
#......###
#.........
.........#
###......#
##.......#
#.#.....#.
...#####..
""",
    # Go: cast once, now.
    "cast": """
..#.......
..##......
..###.....
..####....
..#####...
..#####...
..####....
..###.....
..##......
..#.......
""",
    # A note with writing on it, folded corner: lost fish reported in chat.
    "report": """
#######...
#.....##..
#.###.#.#.
#.....####
#.#####..#
#........#
#.####...#
#........#
##########
..........
""",
    # Float at rest on the water: cast and wait.
    "wait": """
....##....
...####...
...#..#...
...####...
....##....
....##....
##.####.##
.##....##.
..........
..........
""",
    # Thrown out and wound back: cast and retrieve.
    "retrieve": """
......#...
.......#..
#########.
.......#..
......#...
...#......
..#.......
.#########
..#.......
...#......
""",
    # Measured throw: span between two stops.
    "distance": """
..........
#........#
#.#....#.#
###....###
#.######.#
###....###
#.#....#.#
#........#
..........
..........
""",
    # Cog with its teeth.
    "gear": """
...#..#...
..######..
.###..###.
.#......#.
###.##.###
###.##.###
.#......#.
.###..###.
..######..
...#..#...
""",
    # Hook with barb, for the bait slot.
    "bait": """
.....##...
.....##...
......#...
......#...
......#...
.#....#...
##....#...
.#....#...
.#...#....
..###.....
""",
    # Flame, for the fuel slot.
    "fuel": """
....#.....
....##....
...#.#....
..#..#.#..
..#...##..
.#.....#..
.#..#..#..
.#.###.#..
..#...#...
...###....
""",
    "minus": """
..........
..........
..........
..........
..######..
..........
..........
..........
..........
..........
""",
    "plus": """
..........
..........
....##....
....##....
..######..
..######..
....##....
....##....
..........
..........
""",
}


def mask(text):
    rows = [r for r in text.strip("\n").split("\n")]
    assert len(rows) == 10 and all(len(r) == 10 for r in rows), rows
    return {(x, y) for y, r in enumerate(rows) for x, ch in enumerate(r) if ch == "#"}


def glyph(name, groove, lip):
    m = mask(GLYPHS[name])
    im = Image.new("RGBA", (11, 11), CLEAR)
    for (x, y) in m:
        if (x + 1, y + 1) not in m:
            im.putpixel((x + 1, y + 1), lip)
    for (x, y) in m:
        im.putpixel((x, y), groove)
    return im


def glyphs():
    for name in GLYPHS:
        # Cut into the stone of a button.
        save(f"glyph_{name}", glyph(name, rgba("#3c3c3c"), rgba("#f2f2f2", 200)))
        # Cut into the dark deck: a darker groove with a pale metal lip.
        save(f"etch_{name}", glyph(name, rgba("#1a2024"), rgba("#a3b7c0", 230)))


def preview():
    """An 8x zoom sheet, for looking at the sprites, not shipped: each glyph on a button up and pressed in, then
    etched in the deck."""
    load = lambda n: Image.open(OUT / f"{n}.png").convert("RGBA")
    tile = load("deck")
    sheet = Image.new("RGBA", (len(GLYPHS) * 20 + 4, 64))
    for y in range(0, 64, 16):
        for x in range(0, sheet.width, 16):
            sheet.paste(tile, (x, y))
    for i, name in enumerate(GLYPHS):
        x = 4 + i * 20
        for row, (face, dy) in enumerate((("button", 0), ("button_on", 1))):
            b = load(face)
            sheet.paste(b, (x, 4 + row * 20), b)
            g = load(f"glyph_{name}")
            sheet.paste(g, (x + 3, 7 + row * 20 + dy), g)
        e = load(f"etch_{name}")
        sheet.paste(e, (x + 3, 47), e)
    return sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST)


if __name__ == "__main__":
    deck()
    buttons()
    window()
    plate()
    slot()
    glyphs()
    import sys
    if len(sys.argv) > 1:
        preview().save(sys.argv[1])

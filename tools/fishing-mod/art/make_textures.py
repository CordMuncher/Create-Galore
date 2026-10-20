#!/usr/bin/env python3
"""Draws the mod's textures with Pillow: the 16x16 item icons, the Tackle Bench overlay and the material atlas the
3D tackle models are textured from.

Run from anywhere; writes into src/main/resources/assets/create_galore_fishing/textures/.

    python3 tools/fishing-mod/art/make_textures.py

Icons follow vanilla's conventions (read off its own item textures) so they sit well next to vanilla and modded
items: each part is drawn as a light core, and a one-pixel outline is added around it outside, a dark-mid tone on the
upper left and near-black on the lower right; light comes from the top left, so core pixels on the upper-left edge
are lit and those on the lower-right edge shaded. Details (fishing line, glints, eyes) are drawn on top flat.
Colours come from hand-picked ramps per material rather than tinting one grey, as vanilla's do.
"""
import pathlib
import random

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent / "src/main/resources/assets/create_galore_fishing/textures"
ITEM = ROOT / "item"
BLOCK = ROOT / "block"
ENTITY = ROOT / "entity"


def rgb(hexcolor):
    h = hexcolor.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def ramp(base):
    """Five tones from a base colour: outline-dark, outline-light, shadow, mid, light, highlight. Shadows lean cool
    and lights warm, as Minecraft's palettes do."""
    c = rgb(base) if isinstance(base, str) else base
    return {
        "dark": mix(c, (18, 14, 28), 0.72),
        "edge": mix(c, (24, 20, 36), 0.5),
        "shadow": mix(c, (30, 30, 60), 0.22),
        "mid": c,
        "light": mix(c, (255, 248, 225), 0.28),
        "hi": mix(c, (255, 255, 250), 0.6),
    }


# Materials: name -> ramp. Parts reference these by name.
MATERIALS = {
    "oak": ramp("#9c7a48"),
    "darkwood": ramp("#6a4a2c"),
    "cork": ramp("#c49a62"),
    "foam": ramp("#3a3a40"),
    "iron": ramp("#c4c4c8"),
    "steel": ramp("#6c7884"),
    "gunmetal": ramp("#4a5058"),
    "netherite": ramp("#4e4448"),
    "copper": ramp("#c87a48"),
    "brass": ramp("#d6aa48"),
    "gold": ramp("#f0c838"),
    "bamboo": ramp("#cbb46a"),
    "bamboo_node": ramp("#8f9a4a"),
    "lead": ramp("#7c7e88"),
    "red": ramp("#c83a30"),
    "white": ramp("#eeeeea"),
    "black": ramp("#2c2c30"),
}


class Sprite:
    """A 16x16 drawing: core pixels by material, then an outline and lighting pass, then flat details."""

    def __init__(self):
        self.core = {}      # (x, y) -> material name
        self.tone = {}      # (x, y) -> forced tone for a core pixel
        self.decal = {}     # (x, y) -> rgba drawn last, flat
        self.no_outline = set()

    def add_material(self, name, base):
        MATERIALS[name] = ramp(base)
        return name

    def put(self, x, y, mat, tone=None):
        if 0 <= x < 16 and 0 <= y < 16:
            self.core[(x, y)] = mat
            if tone:
                self.tone[(x, y)] = tone
            else:
                self.tone.pop((x, y), None)

    def rect(self, x0, y0, x1, y1, mat, tone=None):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, mat, tone)

    def line(self, x0, y0, x1, y1, mat, tone=None):
        """Bresenham, so diagonals step one pixel at a time like vanilla's sticks."""
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.put(x0, y0, mat, tone)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def disc(self, cx, cy, r, mat, tone=None):
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 0.5:
                    self.put(x, y, mat, tone)

    def ring(self, cx, cy, r, mat, tone=None):
        for y in range(16):
            for x in range(16):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if r - 0.5 <= d < r + 0.5:
                    self.put(x, y, mat, tone)

    def dot(self, x, y, color):
        """A flat detail pixel, drawn over everything (outline included)."""
        if 0 <= x < 16 and 0 <= y < 16:
            self.decal[(x, y)] = rgb(color) + (255,) if isinstance(color, str) else color

    def thread(self, points, color):
        for x, y in points:
            self.dot(x, y, color)

    def render(self):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        core = self.core
        # Outline outside the core: dark-mid where the core lies to the right or below (upper-left side), near
        # black where it lies to the left or above (lower-right side).
        outline = {}
        for (x, y), mat in core.items():
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                p = (x + dx, y + dy)
                if p in core or not (0 <= p[0] < 16 and 0 <= p[1] < 16):
                    continue
                lower_right = dx > 0 or dy > 0
                prev = outline.get(p)
                if prev is None or (lower_right and prev[1] == "edge"):
                    outline[p] = (mat, "dark" if lower_right else "edge")
        for p, (mat, tone) in outline.items():
            if mat in self.no_outline:
                continue
            img.putpixel(p, MATERIALS[mat][tone] + (255,))
        for (x, y), mat in core.items():
            tone = self.tone.get((x, y))
            if tone is None:
                lit = (x - 1, y) not in core or (x, y - 1) not in core
                dim = (x + 1, y) not in core or (x, y + 1) not in core
                tone = "light" if lit and not dim else "shadow" if dim and not lit else "mid"
            img.putpixel((x, y), MATERIALS[mat][tone] + (255,))
        for p, c in self.decal.items():
            img.putpixel(p, c)
        return img


def save(img, name, folder=ITEM):
    folder.mkdir(parents=True, exist_ok=True)
    img.save(folder / f"{name}.png")


LINE_GREY = "#d8d8d8"


# --- rods -------------------------------------------------------------------------------------------------------

def rod(name, blank, grip, reel=None, sections=3, telescopic=False, heavy=False, glint=False):
    """Diagonal from the butt (bottom left) to the tip (top right), like vanilla's fishing rod; a thicker grip, the
    reel hanging under the seat (or on top for a boat rod), ferrules or telescopic joints, the line off the tip."""
    s = Sprite()
    s.add_material("blank", blank)
    tip = (13, 2) if not heavy else (12, 3)
    # Blank: one pixel core (the outline makes it three wide, as vanilla's stick).
    for x in range(5, tip[0] + 1):
        s.put(x, 15 - x, "blank")
    # Grip: two pixels wide over the butt.
    for x in range(2, 6):
        s.put(x, 15 - x, grip)
        s.put(x + 1, 15 - x, grip)
    s.put(2, 14, grip, "shadow")
    if heavy:
        for x in range(6, 10):
            s.put(x + 1, 15 - x, "blank")
    # Joints along the blank.
    joints = [round(6 + (tip[0] - 6) * k / sections) for k in range(1, sections)]
    for x in joints:
        s.put(x, 15 - x, "blank", "hi" if telescopic else "light")
    # Reel seat and reel.
    if reel:
        s.put(6, 9, "iron", "light")
        if heavy:
            s.rect(6, 5, 9, 7, reel)
            s.put(10, 6, "black", "mid")
        else:
            s.put(7, 10, "iron", "shadow")
            s.rect(6, 11, 8, 12, reel)
            s.put(8, 11, reel, "hi")
            s.put(9, 12, "black", "mid")
    # Line hanging off the tip, with a little hook.
    tx, ty = tip
    s.thread([(tx + 1, ty + k) for k in range(1, 8)], LINE_GREY)
    s.dot(tx + 1, ty + 8, "#a8a8a8")
    s.dot(tx, ty + 9, "#a8a8a8")
    s.dot(tx - 1, ty + 8, "#a8a8a8")
    if glint:
        s.dot(10, 5, "#c89ad8")
    save(s.render(), name)


def pole(name):
    s = Sprite()
    for x in range(2, 14):
        node = (x - 2) % 4 == 3
        s.put(x, 15 - x, "bamboo_node" if node else "bamboo")
    for x in range(2, 5):
        s.put(x + 1, 15 - x, "bamboo")
    s.thread([(14, 2 + k) for k in range(1, 9)], LINE_GREY)
    s.dot(14, 11, "#a8a8a8")
    s.dot(13, 12, "#a8a8a8")
    save(s.render(), name)


# --- reels ------------------------------------------------------------------------------------------------------

def reel(name, color, style, line="#d8e8d0"):
    s = Sprite()
    body = s.add_material("body", color)
    spool = s.add_material("spool", line)
    if style == "spinning":
        # Side view: the rod-mount foot on top, a stem down to the gearbox, the spool full of line in front (right)
        # with its bail, and the crank to the left with its knob.
        s.rect(4, 1, 11, 1, "iron")            # foot
        s.rect(7, 2, 8, 4, "iron")             # stem
        s.disc(6, 8, 3.2, body)                # gearbox, round
        s.rect(9, 5, 10, 11, body)             # rotor cup
        s.rect(11, 5, 13, 11, spool)           # spool, wound with line
        for y in (6, 8, 10):
            s.rect(11, y, 13, y, spool, "light")
        s.put(14, 8, body, "light")            # spool knob
        s.thread([(12, 4), (13, 4)], "#d0d0d4")  # bail wire
        s.line(3, 9, 1, 12, "gunmetal")        # crank arm
        s.rect(1, 13, 2, 14, "black")          # knob
    elif style == "baitcaster":
        # Low profile, round side plates, handle on the right.
        s.disc(7, 8, 4.4, body)
        s.rect(4, 6, 10, 10, body)
        s.rect(5, 5, 9, 5, spool)              # line on the spool, seen over the frame
        s.ring(7, 8, 2, body, "shadow")
        s.put(7, 8, "iron", "hi")
        s.rect(11, 7, 13, 8, "gunmetal")       # handle arm
        s.rect(13, 4, 14, 6, "black")          # paddles
        s.rect(13, 9, 14, 11, "black")
    else:
        # Trolling: big drum, lever drag on top, single big knob.
        s.rect(3, 4, 11, 12, body)
        s.rect(4, 3, 10, 3, body)
        s.rect(4, 13, 10, 13, body)
        s.rect(5, 5, 9, 11, spool)
        for y in (6, 8, 10):
            s.rect(5, y, 9, y, spool, "light")
        s.line(3, 2, 6, 2, "iron")             # lever
        s.put(2, 2, "red", "mid")
        s.rect(12, 7, 13, 8, "gunmetal")
        s.rect(13, 9, 14, 11, "black")
    save(s.render(), name)


# --- line spools and leaders ------------------------------------------------------------------------------------

def spool(name, color, braided=False):
    s = Sprite()
    line = s.add_material("line", color)
    s.rect(2, 3, 3, 12, "oak")                 # flanges
    s.rect(12, 3, 13, 12, "oak")
    s.rect(4, 4, 11, 11, line)                 # the wound line
    for y in range(4, 12):
        for x in range(4, 12):
            if braided and (x + y) % 3 == 0:
                s.put(x, y, line, "shadow")
            elif not braided and y % 2 == 0:
                s.put(x, y, line, "light" if x < 10 else "mid")
    # The loose end.
    s.thread([(12, 2), (13, 1), (14, 1)], color)
    save(s.render(), name)


def leader(name, color, steel=False):
    s = Sprite()
    line = s.add_material("line", color)
    s.ring(7, 8, 4.5, line)
    s.ring(7, 8, 3.2, line, "shadow")
    # Swivel and snap at the end.
    s.rect(11, 3, 11, 4, "iron")
    s.put(12, 2, "iron", "light")
    s.put(13, 1, "iron", "hi")
    if steel:
        for x, y in ((3, 8), (11, 8), (7, 4), (7, 12)):
            s.put(x, y, line, "hi")
    save(s.render(), name)


# --- hooks ------------------------------------------------------------------------------------------------------

def hook(name, size, metal="iron"):
    """A J hook: eye at the top, shank, bend, point and barb. size 0..3 from #12 to big game."""
    s = Sprite()
    top = (5, 4, 3, 2)[size]
    bend_y = (10, 11, 12, 12)[size]
    width = (3, 3, 4, 5)[size]
    x = 10
    s.ring(x, top - 1, 1, metal) if size >= 2 else s.put(x, top - 1, metal, "hi")
    for y in range(top, bend_y):
        s.put(x, y, metal)
        if size == 3:
            s.put(x - 1, y, metal)
    # Bend.
    s.put(x - 1, bend_y, metal)
    for k in range(1, width):
        s.put(x - 1 - k, bend_y + 1 if k < width - 1 else bend_y, metal)
    px = x - width
    s.put(px, bend_y - 1, metal)
    s.put(px, bend_y - 2, metal)
    if size >= 1:
        s.put(px, bend_y - 3, metal, "hi")
    s.put(px + 1, bend_y - 2, metal, "light")  # barb
    save(s.render(), name)


# --- terminals --------------------------------------------------------------------------------------------------

def bobber(name):
    s = Sprite()
    s.disc(7, 8, 4, "white")
    for y in range(8, 13):
        for x in range(16):
            if (x, y) in s.core:
                s.put(x, y, "red")
    s.rect(3, 8, 11, 8, "black", "mid")        # band
    s.rect(7, 2, 7, 3, "red")                  # antenna
    s.put(7, 13, "black")                      # eyelet
    save(s.render(), name)


def sinker(name):
    s = Sprite()
    s.disc(7, 10, 3.2, "lead")
    s.rect(7, 6, 7, 7, "lead")
    s.rect(6, 7, 8, 7, "lead")
    s.put(6, 9, "lead", "hi")
    s.ring(7, 4, 1, "iron")
    save(s.render(), name)


def treble(s, x, y):
    """A small treble hook hanging at (x, y): fine wire, so flat pixels without an outline."""
    s.dot(x, y, "#9a9aa0")
    s.dot(x, y + 1, "#c8c8cc")
    s.dot(x - 1, y + 2, "#7a7a80")
    s.dot(x + 1, y + 2, "#7a7a80")


def lure(name, kind, color):
    s = Sprite()
    paint = s.add_material("paint", color)
    if kind == "spoon":
        s.line(4, 11, 10, 4, paint)
        s.line(5, 11, 11, 4, paint)
        s.line(5, 12, 11, 5, paint)
        s.line(6, 9, 9, 6, "red", "mid")
        s.put(10, 4, paint, "hi")
        s.ring(12, 3, 1, "iron")
        treble(s, 4, 12)
    elif kind == "spinner":
        s.line(3, 12, 12, 3, "iron")
        s.disc(9, 5, 2, "gold")
        s.put(8, 4, "gold", "hi")
        s.rect(5, 9, 6, 10, paint)
        s.put(4, 11, "red", "mid")
        treble(s, 2, 12)
    elif kind in ("crankbait", "deep_crankbait"):
        # A plug shaped like a small fish, head right, diving lip under the chin.
        rows = {5: (6, 10), 6: (4, 11), 7: (3, 12), 8: (3, 12), 9: (4, 11), 10: (6, 9)}
        for y, (a, b) in rows.items():
            s.rect(a, y, b, y, paint)
        s.rect(2, 7, 2, 8, paint, "shadow")     # tail
        s.rect(4, 6, 9, 6, paint, "light")      # back
        s.rect(5, 9, 10, 9, paint, "light" if kind == "crankbait" else "shadow")
        s.dot(10, 6, "#f0f0e8")                 # eye
        s.dot(11, 6, "#18181c")
        lip = s.add_material("lip", "#c8dcea")
        if kind == "deep_crankbait":
            s.rect(13, 9, 13, 11, lip)
            s.put(14, 11, lip, "light")
        else:
            s.rect(13, 9, 13, 10, lip)
        s.no_outline.add(lip)
        treble(s, 6, 11)
        treble(s, 10, 11)
        s.dot(13, 7, "#9a9aa0")                 # line tie
    elif kind == "jig":
        s.disc(10, 5, 2.5, "lead")
        s.put(9, 4, "lead", "hi")
        s.dot(11, 4, "#e8e040")
        s.dot(11, 5, "#202020")
        for k, (x0, y0) in enumerate(((8, 6), (8, 7), (9, 7), (7, 6))):
            s.line(x0, y0, x0 - 5, y0 + 6 - k, paint)
        s.ring(12, 2, 1, "iron")
    elif kind == "popper":
        # A tapered plug with a cupped face (right) that spits water, feathered tail.
        rows = {5: (6, 11), 6: (5, 12), 7: (4, 12), 8: (4, 12), 9: (5, 12), 10: (6, 11)}
        for y, (a, b) in rows.items():
            s.rect(a, y, b, y, paint)
        s.rect(6, 6, 11, 6, paint, "light")
        s.rect(12, 7, 12, 8, "black", "mid")    # the cup
        s.dot(11, 6, "#18181c")                 # eye
        s.dot(2, 7, "#e8e8e8")                  # feathers
        s.dot(3, 8, "#c83a30")
        s.dot(2, 9, "#e8e8e8")
        treble(s, 6, 11)
        treble(s, 10, 11)
        s.dot(13, 6, "#9a9aa0")
    else:  # soft worm
        pts = [(2, 9), (3, 8), (4, 8), (5, 9), (6, 10), (7, 10), (8, 9), (9, 8), (10, 7), (11, 7), (12, 8), (13, 9)]
        for k, (x, y) in enumerate(pts):
            s.put(x, y, paint, "light" if k % 3 == 0 else None)
            s.put(x, y + 1, paint, "shadow" if k % 3 else None)
        s.put(13, 8, paint, "hi")
        s.line(11, 4, 11, 6, "iron")           # hook through the head
        s.put(10, 7, "iron", "light")
    save(s.render(), name)


# --- the 3D models' material atlas -------------------------------------------------------------------------------

ATLAS_CELLS = ["paint", "cork", "foam", "wood", "metal", "thread", "bamboo", "plain"]


def atlas():
    """64x32: eight 16x16 greyscale pattern cells (paint, cork, foam, wood, metal, thread, bamboo, plain) in order,
    left to right then top to bottom. The models tint them with each part's colour, so a cell holds only light and
    texture: values near 230, darker grain and pores, a few lighter flecks, in Minecraft's chunky style."""
    r = random.Random(11)
    img = Image.new("RGBA", (64, 32))

    def cell(i, fn):
        ox, oy = (i % 4) * 16, (i // 4) * 16
        for y in range(16):
            for x in range(16):
                v = fn(x, y)
                v = max(0, min(255, int(v)))
                img.putpixel((ox + x, oy + y), (v, v, v, 255))

    # Paint: smooth gloss with faint streaks along the blank (the blank runs along v).
    cell(0, lambda x, y: 236 + (10 if x in (5, 6) else 0) - (8 if x in (12, 13) else 0) + r.choice((0, 0, 0, -4)))
    # Cork: mottled with dark pores.
    cork = [[226 + r.choice((-14, -6, 0, 0, 6, 12)) for _ in range(16)] for _ in range(16)]
    for _ in range(26):
        cork[r.randrange(16)][r.randrange(16)] = 168
    cell(1, lambda x, y: cork[y][x])
    # EVA foam: fine dull noise.
    cell(2, lambda x, y: 214 + r.choice((-6, 0, 0, 4)))
    # Wood: grain running along the part.
    cell(3, lambda x, y: 222 + (16 if x % 5 == 1 else 0) - (22 if x % 5 == 3 else 0) - (10 if (y + x * 3) % 11 == 0 else 0))
    # Metal: bright with a highlight band and darker edge band.
    cell(4, lambda x, y: 238 + (14 if x in (3, 4) else 0) - (26 if x >= 13 else 0) + r.choice((0, -3)))
    # Thread (wound line): alternating rows.
    cell(5, lambda x, y: 236 - (22 if y % 2 else 0) + r.choice((0, 0, -5)))
    # Bamboo: lengthwise fibres.
    cell(6, lambda x, y: 230 + (12 if x % 4 == 0 else 0) - (14 if x % 4 == 2 else 0))
    # Plain: barely textured.
    cell(7, lambda x, y: 240 + r.choice((0, 0, -3)))
    save(img, "tackle", ENTITY)


# --- bait and fish processing ----------------------------------------------------------------------------------

def maggots(name):
    """Three plump cream grubs, two pixels thick, each with a darker head."""
    s = Sprite()
    grub = s.add_material("grub", "#ece0bc")
    head = s.add_material("grub_head", "#9a7a50")
    for x0, y0 in ((2, 3), (8, 7), (3, 11)):
        s.rect(x0, y0, x0 + 4, y0 + 1, grub)
        s.put(x0 + 2, y0 + 1, grub, "shadow")    # a segment line
        s.rect(x0 + 5, y0, x0 + 5, y0 + 1, head)
    save(s.render(), name)


def boilies(name):
    """Three round baits, the near one lit on its upper left."""
    s = Sprite()
    bait = s.add_material("boilie", "#d88a3a")
    for cx, cy in ((5, 5), (10, 6), (7, 10)):
        s.disc(cx, cy, 2.3, bait)
        s.put(cx - 1, cy - 1, bait, "hi")
    save(s.render(), name)


def chum(name):
    """A ball of mashed fish: reddish brown with darker flecks and a pale bit of scale."""
    s = Sprite()
    mash = s.add_material("chum", "#8a4a3a")
    s.disc(7.5, 8, 5, mash)
    for x, y in ((5, 6), (9, 9), (7, 11), (10, 5), (4, 9)):
        s.put(x, y, mash, "shadow")
    s.put(6, 5, mash, "hi")
    s.dot(9, 7, "#d8d0c0")
    save(s.render(), name)


def fish_scraps(name):
    """Offcuts: pink flesh, a silver bit of skin, a tail fin."""
    s = Sprite()
    flesh = s.add_material("flesh", "#e08a80")
    skin = s.add_material("skin", "#a8b4bc")
    s.rect(3, 8, 6, 10, flesh)
    s.rect(8, 9, 11, 11, flesh)
    s.rect(6, 4, 9, 6, skin)
    s.put(10, 4, skin, "hi")
    s.line(11, 5, 13, 3, skin)
    s.line(11, 5, 13, 7, skin)
    save(s.render(), name)


def fish_meal(name):
    """A heap of dry tan meal, like bone meal's pile."""
    s = Sprite()
    meal = s.add_material("meal", "#c8a868")
    for y in range(6, 13):
        half = (y - 5) * 5 // 7 + 1
        s.rect(8 - half, y, 7 + half, y, meal)
    for x, y in ((6, 9), (9, 10), (7, 12), (10, 12)):
        s.put(x, y, meal, "shadow")
    s.put(7, 6, meal, "hi")
    save(s.render(), name)


def fish_roe(name):
    """A cluster of bright orange eggs: two-pixel beads, each lit on its upper left."""
    s = Sprite()
    egg = s.add_material("roe", "#f0782a")
    beads = ((5, 4), (8, 4), (4, 7), (7, 7), (10, 7), (5, 10), (8, 10), (11, 10), (7, 13))
    for x, y in beads:
        s.rect(x, y, x + 1, y + 1, egg, "mid")
    for x, y in beads:
        s.put(x, y, egg, "hi")
        s.put(x + 1, y + 1, egg, "shadow")
    save(s.render(), name)


def shark_skin(name):
    """A stretched grey pelt like vanilla's leather: four corner flaps, a paler belly half, rough denticles."""
    s = Sprite()
    hide = s.add_material("hide", "#6a7a84")
    belly = s.add_material("belly", "#b8c0c4")
    for y in range(4, 12):
        x0, x1 = (2, 13) if y in (4, 11) else (4, 11)
        s.rect(x0, y, x1, y, hide if y < 8 else belly)
    for x in (2, 3, 12, 13):
        s.put(x, 3, hide)
        s.put(x, 12, belly)
    for x, y in ((5, 5), (8, 5), (10, 6), (6, 7), (9, 7)):
        s.put(x, y, hide, "light")
    save(s.render(), name)


def live_bait_bucket(name):
    """An iron bucket of water with a small fish showing at the top."""
    s = Sprite()
    water = s.add_material("water", "#3a6ad8")
    fish = s.add_material("baitfish", "#d8a040")
    for y in range(6, 15):
        inset = (y - 6) // 4
        s.rect(3 + inset, y, 12 - inset, y, "iron")
    s.rect(4, 6, 11, 7, water, "mid")
    s.put(6, 6, fish)
    s.put(7, 6, fish, "light")
    s.put(8, 7, fish, "shadow")
    # The handle, drawn flat over the outline like vanilla's.
    s.thread([(3, 5), (3, 4), (4, 3), (5, 2), (6, 2), (7, 2), (8, 2), (9, 2), (10, 2), (11, 3), (12, 4), (12, 5)], "#5a5a60")
    save(s.render(), name)


# --- the Tackle Bench's top overlay -----------------------------------------------------------------------------

def bench_overlay():
    """Tackle laid out on the bench top, drawn on transparency: the block model puts it over vanilla's own planks,
    so the bench is built of the same wood as everything else."""
    s = Sprite()
    s.add_material("blank", "#b8b8b8")
    for x in range(3, 12):
        s.put(x, 14 - x, "blank")
    for x in range(1, 4):
        s.put(x, 14 - x, "cork")
        s.put(x + 1, 14 - x, "cork")
    s.rect(9, 9, 11, 11, "iron")               # a reel
    s.put(12, 10, "black", "mid")
    s.rect(1, 1, 4, 3, "oak")                  # a spool of line
    s.rect(2, 1, 3, 3, s.add_material("line", "#d8e8d0"))
    s.put(13, 3, "red")                        # a float
    s.put(13, 2, "white")
    hook_pts = [(6, 12), (6, 13), (5, 14), (4, 13)]
    for x, y in hook_pts:
        s.put(x, y, "iron")
    save(s.render(), "tackle_bench_overlay", BLOCK)


def main():
    pole("bamboo_pole")
    rod("wooden_rod", "#8a6a45", "darkwood", reel="copper", sections=2)
    rod("iron_rod", "#c0c0c4", "cork", reel="iron")
    rod("steel_rod", "#5a6672", "foam", reel="brass", sections=5, telescopic=True)
    rod("boat_rod", "#33414f", "foam", reel="brass", sections=2, heavy=True)
    rod("netherite_rod", "#4e4448", "foam", reel="gold", sections=4, glint=True)
    for name, color, style in (("copper_reel", "#c87a48", "spinning"), ("iron_reel", "#b8b8bc", "spinning"),
                               ("brass_reel", "#d6aa48", "spinning"), ("steel_baitcaster", "#5a6a7a", "baitcaster"),
                               ("trolling_reel", "#c8a040", "trolling")):
        reel(name, color, style)
    spool("string_line", "#e8e8e4")
    spool("mono_line", "#b8d8a8")
    spool("clear_line", "#c8e4f4")
    spool("braided_line", "#4a7a42", braided=True)
    spool("heavy_line", "#f0d070")
    leader("clear_leader", "#c8e4f4")
    leader("steel_leader", "#98a2a8", steel=True)
    hook("small_hook", 0)
    hook("medium_hook", 1)
    hook("large_hook", 2)
    hook("big_game_hook", 3)
    hook("golden_hook", 1, "gold")
    hook("netherite_hook", 2, "netherite")
    bobber("float")
    sinker("sinker")
    for name, color in (("spoon", "#d4d4d8"), ("spinner", "#e0b040"), ("crankbait", "#48a858"),
                        ("deep_crankbait", "#3a6098"), ("jig", "#b04848"), ("popper", "#f2c838"),
                        ("soft_worm", "#8a58b0")):
        lure(name, name, color)
    maggots("maggots")
    boilies("boilies")
    chum("chum")
    fish_scraps("fish_scraps")
    fish_meal("fish_meal")
    fish_roe("fish_roe")
    shark_skin("shark_skin")
    live_bait_bucket("live_bait_bucket")
    atlas()
    bench_overlay()
    print("textures written")


if __name__ == "__main__":
    main()

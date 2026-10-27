"""Draws the Field Guide Pendant item texture (16x16) for the bridge mod: the Field Guide's red cover and gold
glyphs made small, a brass clasp and bail, hung on a brass chain. Colours are taken from the guide's own item
(modonomicon_red) so the two read as the same book."""
import sys
from PIL import Image

out = sys.argv[1]
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()

# The guide's palette.
OUTLINE = (21, 4, 4)
SPINE = (33, 6, 6)
COVER_D = (54, 9, 9)
COVER = (76, 12, 12)
COVER_L = (92, 15, 15)
COVER_H = (100, 16, 16)
GOLD_H = (250, 226, 98)
GOLD = (245, 185, 79)
GOLD_D = (217, 127, 22)
PAGE_L = (224, 209, 188)
PAGE = (201, 184, 159)
PAGE_D = (163, 133, 107)
# Brass for the chain, bail and clasp.
BRASS_H = (252, 214, 120)
BRASS = (214, 158, 64)
BRASS_D = (140, 92, 38)


def put(x, y, c):
    px[x, y] = c + (255,)


# Chain: two strands from the top corners down to the bail, links alternating light and dark.
left = [(3, 0), (3, 1), (4, 2), (5, 3), (6, 4)]
right = [(12, 0), (12, 1), (11, 2), (10, 3), (9, 4)]
for i, (x, y) in enumerate(left):
    put(x, y, BRASS if i % 2 == 0 else BRASS_D)
for i, (x, y) in enumerate(right):
    put(x, y, BRASS_D if i % 2 == 0 else BRASS)
put(2, 0, BRASS_D)
put(13, 0, BRASS_D)
# Bail: a small ring the chain runs through.
put(7, 4, BRASS_H)
put(8, 4, BRASS)
put(7, 5, BRASS)
put(8, 5, BRASS_D)

# Book: cover x 4..11, y 6..14, pages showing along the right and bottom edges.
x0, x1, y0, y1 = 4, 11, 6, 14
for y in range(y0, y1 + 1):
    for x in range(x0, x1 + 1):
        if x == x0:
            c = SPINE
        elif x == x0 + 1:
            c = COVER_D
        elif y == y0:
            c = COVER_H
        elif (x + y) % 5 == 0:
            c = COVER_L
        else:
            c = COVER
        put(x, y, c)
# Page block peeking out on the right and bottom (the book seen a little from the side, like the guide's icon).
for y in range(y0 + 1, y1 + 1):
    put(x1 + 1, y, PAGE_L if y % 2 else PAGE)
put(x1 + 1, y0 + 1, PAGE_D)
for x in range(x0 + 1, x1 + 2):
    put(x, y1 + 1, PAGE if x % 2 else PAGE_D)
# Outline.
for x in range(x0, x1 + 2):
    if px[x, y0 - 1][3] == 0:
        put(x, y0 - 1, OUTLINE)
put(x0 - 1, y0, OUTLINE)
for y in range(y0, y1 + 2):
    put(x0 - 1, y, OUTLINE)
for y in range(y0, y1 + 2):
    put(x1 + 2, y, OUTLINE)
put(x1 + 1, y0, OUTLINE)
put(x0, y1 + 1, OUTLINE)
# Bail meets the cover through the brass clasp at the top of the spine side.
put(7, 5, BRASS)
put(7, 6, BRASS_D)
put(8, 6, BRASS_D)
# Gold on the cover, as on the guide: a quill stroke running down to the left, and a dot beside it.
for x, y, c in [(10, 8, GOLD_H), (9, 9, GOLD_H), (8, 10, GOLD), (7, 11, GOLD), (6, 12, GOLD_D), (10, 11, GOLD)]:
    put(x, y, c)
# Brass corner caps on the open edge.
put(x1, y0, BRASS_H)
put(x1, y1, BRASS)
img.save(out)
if len(sys.argv) > 2:
    img.resize((256, 256), Image.NEAREST).save(sys.argv[2])

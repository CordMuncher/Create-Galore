#!/usr/bin/env python3
"""Draws the Field Guide's inline text icons (8x8 each) into one strip and writes the font that maps them.
Run: python3 tools/art/guide_icons.py   -> pack/config/paxi/resourcepacks/create-galore-guide/...
Each icon is a glyph in the default font at U+E100 onward, so any Modonomicon text page can use it."""
import json, pathlib
from PIL import Image
OUT = pathlib.Path(__file__).resolve().parents[2] / 'pack/config/paxi/resourcepacks/create-galore-guide/assets'
P = {'.': None, 'k': (40, 32, 28), 'y': (240, 196, 40), 'Y': (184, 132, 20), 'o': (236, 120, 24), 'r': (200, 40, 36),
     'R': (130, 24, 24), 'w': (250, 250, 240), 'g': (150, 150, 150), 'G': (90, 90, 96), 'b': (60, 130, 220),
     'B': (30, 70, 160), 'c': (150, 210, 245), 'n': (120, 80, 40), 'N': (80, 50, 24), 'e': (60, 160, 70), 'E': (30, 100, 40),
     's': (120, 110, 100), 'S': (70, 64, 60)}
ICONS = [  # order = code point order from U+E100; names are what the book text uses as {{name}}
 ('key', ['........', '.yyy....', 'yY.Yy...', 'y...yyyy', 'yY.Yy.Yy', '.yyy..Y.', '........', '........']),
 ('warn', ['...rr...', '..ryyr..', '..rkkr..', '.rykkyr.', '.ryyyyr.', 'ryykkyyr', 'rrrrrrrr', '........']),
 ('tip', ['..yyyy..', '.yywyyy.', '.ywyyyy.', '.yyyyyy.', '..yyyy..', '..gggg..', '..GGGG..', '...gg...']),
 ('deep', ['.GGG....', 'GcccG...', 'GcwcG...', 'GcccG...', '.GGGn...', '....nn..', '.....nn.', '......n.']),
 ('cog', ['..g..g..', '.gggggg.', 'gggGGggg', '.gG..Gg.', '.gG..Gg.', 'gggGGggg', '.gggggg.', '..g..g..']),
 ('stress', ['..GGGG..', '.GwwwwG.', 'GwwwwrwG', 'GwwwrwwG', 'GwwkwwwG', 'GwwwwwwG', '.GGGGGG.', '........']),
 ('bolt', ['....yY..', '...yY...', '..yY....', '.yyyyY..', '...yY...', '..yY....', '.yY.....', '.Y......']),
 ('drop', ['...b....', '...b....', '..bbb...', '..bcb...', '.bcbbb..', '.bbbbb..', '..BBB...', '........']),
 ('flame', ['...o....', '...oo...', '..ooo.o.', '.oooyoo.', '.ooyyyo.', '.oyyyyo.', '..oyyo..', '........']),
 ('snow', ['...c....', '.c.c.c..', '..ccc...', 'ccc.ccc.', '..ccc...', '.c.c.c..', '...c....', '........']),
 ('smog', ['........', '...ss...', '.sssss..', 'ssSssss.', 'sSSssSs.', '.SSSSS..', '........', '........']),
 ('heart', ['........', '.rr.rr..', 'rwrrrrr.', 'rrrrrrr.', '.rrrrr..', '..rrr...', '...r....', '........']),
 ('food', ['....nnn.', '...nnnnn', '...nnnNn', '....nnN.', '...gw...', '..gw....', 'gww.....', '.g......']),
 ('gear', ['nNnNnNnN', 'NggNggNn', 'nggnggnN', 'NnNnNnNn', 'nggnggnN', 'NggNggNn', 'nNnNnNnN', '........']),
 ('compass', ['..GGGG..', '.GwwwwG.', 'GwwrrwwG', 'GwwrrwwG', 'GwwkkwwG', 'GwwkkwwG', '.GwwwwG.', '..GGGG..']),
 ('star', ['...y....', '...y....', 'yyyyyyy.', '.yyyyy..', '..yyy...', '.yy.yy..', '.y...y..', '........']),
]
def main():
    img = Image.new('RGBA', (8 * len(ICONS), 8), (0, 0, 0, 0))
    for i, (name, rows) in enumerate(ICONS):
        assert len(rows) == 8 and all(len(r) == 8 for r in rows), name
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if P[ch]: img.putpixel((i * 8 + x, y), P[ch] + (255,))
    (OUT / 'create_galore/textures/font').mkdir(parents=True, exist_ok=True)
    img.save(OUT / 'create_galore/textures/font/guide_icons.png')
    chars = ''.join(chr(0xE100 + i) for i in range(len(ICONS)))
    font = {'providers': [{'type': 'bitmap', 'file': 'create_galore:font/guide_icons.png', 'ascent': 7, 'height': 8, 'chars': [chars]}]}
    # Modonomicon draws book text with its own font (modonomicon:default); the vanilla one covers tooltips and chat.
    # Font files merge across resource packs, so these only add the glyphs.
    for ns in ('minecraft', 'modonomicon'):
        (OUT / ns / 'font').mkdir(parents=True, exist_ok=True)
        (OUT / ns / 'font/default.json').write_text(json.dumps(font, indent=2, ensure_ascii=True) + '\n')
    print(json.dumps({n: f'U+{0xE100 + i:04X}' for i, (n, _) in enumerate(ICONS)}))
    img.resize((img.width * 12, 96), Image.NEAREST).save('/tmp/claude-0/guide_icons_preview.png')
main()

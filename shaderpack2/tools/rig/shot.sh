#!/usr/bin/env bash
# Saves what the virtual screen shows: shot.sh OUT.png
# Says so when the picture carries the pack's bad frame mark (lib/bad_frame.glsl: a 12-pixel square of one pure
# colour at the middle of the left edge; red = a pass wrote an infinite or not-a-number value into the lit scene,
# green = after the upscaler, blue = the frame's own values, white = the final pass). The mark counts frames, so on
# the rig it stays up for about two and a half minutes after the bad value.
export DISPLAY="${DISPLAY:-:99}"
import -window root "$1"
python3 - "$1" <<'EOF' || true
import sys
try:
    from PIL import Image
except ImportError:
    sys.exit(0)
im = Image.open(sys.argv[1]).convert("RGB")
w, h = im.size
block = [im.getpixel((x, y)) for x in range(10, 19) for y in range(h // 2 - 4, h // 2 + 4)]
first = block[0]
if all(p == first for p in block) and all(c in (0, 255) for c in first) and any(first):
    names = {(255, 0, 0): "red: the lit scene", (0, 255, 0): "green: after the upscaler",
             (0, 0, 255): "blue: the frame's own values", (255, 255, 255): "white: the final pass (or a burnt-out sky)"}
    print("BAD FRAME MARK in %s (%s)" % (sys.argv[1], names.get(first, "mixed: %s" % (first,))), file=sys.stderr)
EOF

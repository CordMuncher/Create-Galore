"""Equirectangular pixel art -> Minecraft panorama_0..5.png (front, right, back, left, up, down), nearest sampling."""
import math, sys, os
from PIL import Image

src = Image.open(sys.argv[1]).convert('RGB')
out = sys.argv[2]
F = int(sys.argv[3]) if len(sys.argv) > 3 else 1024
W, H = src.size
sp = src.load()
os.makedirs(out, exist_ok=True)

def direction(face, u, v):
    return [(u, -v, 1), (1, -v, -u), (-u, -v, -1), (-1, -v, u), (u, 1, v), (u, -1, -v)][face]

for face in range(6):
    im = Image.new('RGB', (F, F)); p = im.load()
    for j in range(F):
        v = (j + 0.5) / F * 2 - 1
        for i in range(F):
            u = (i + 0.5) / F * 2 - 1
            x, y, z = direction(face, u, v)
            lon = math.atan2(x, z)
            lat = math.atan2(y, math.hypot(x, z))
            c = int((lon / (2 * math.pi)) * W) % W
            r = min(H - 1, max(0, int((0.5 - lat / math.pi) * H)))
            p[i, j] = sp[c, r]
    im.save(os.path.join(out, f'panorama_{face}.png'))
print('faces written to', out)

# Pack art

Pixel art for the pack icon and the title-screen panorama, drawn by Claude (Opus). The scripts place every pixel, so the art can be tweaked and rebuilt.

| File | Makes |
|---|---|
| `icon.py` (+ `ship.py`) | 64x64 icon: `python3 icon.py icon.png` also writes `icon@8x.png` |
| `pano.py` (+ `pano_land.py`, `pano_props.py`) | 720x360 equirectangular panorama, 0.5 degree per pixel: `python3 pano.py pano.png` |
| `cube.py` | Cuts the panorama into Minecraft's six faces: `python3 cube.py pano.png out 2048` |

Run them from this folder (Pillow needed). Then copy:

- `icon.png` to `pack/resourcepacks/create-galore-menu/pack.png` and `docs/icon.png`, `icon@8x.png` to `docs/icon-512.png`
- `out/panorama_*.png` to `pack/resourcepacks/create-galore-menu/assets/minecraft/textures/gui/title/background/`

and run `packwiz refresh` in `pack/`.

The title screen looks about 10 degrees down with an 85 degree vertical field of view, so only roughly -50 to +32 degrees of elevation (rows 116 to 280 of the panorama) is ever on screen, and the Minecraft logo covers about +8 to +30 degrees in the middle.

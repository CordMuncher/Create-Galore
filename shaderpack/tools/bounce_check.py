#!/usr/bin/env python3
"""Checks the bounce light (shaders/program/light_flood.csh) against radiosity.

Runs the flood as the pack runs it, on a small grid: a closed room 11 x 6 x 11 blocks with a 3 x 3 window, lit by a
patch of sunlit floor or a sunlit wall, and solves the same room by radiosity (every block face a patch, walls
reflecting half). Prints how far the flood's light on a set of faces is from radiosity's, as a factor, and the scale
that best maps one onto the other (bounce_peak in lib/light_volume.glsl).

    python3 shaderpack/tools/bounce_check.py [--wall 0.85] [--pass 1.0] [--add 1.5] [--read 1.0] [--frames 1200]

Needs numpy. The flood's settings default to the pack's; change them here first to try new ones.
"""
import argparse
import numpy as np

# The facings in the pack's order (light_volume.glsl's facing_dir): +x, -x, +y, -y, +z, -z; facing ^ 1 is opposite.
DIRS = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]


def shifted(a, d):
    """a at v + d for every block v (0 past the grid)."""
    out = np.zeros_like(a)
    src, dst = [slice(None)] * 3, [slice(None)] * 3
    for axis, k in enumerate(d):
        if k > 0:
            src[axis], dst[axis] = slice(k, None), slice(0, -k)
        elif k < 0:
            src[axis], dst[axis] = slice(0, k), slice(-k, None)
    out[tuple(dst)] = a[tuple(src)]
    return out


def flood(solid, added, wall, pass_, frames):
    """The bounce light of each facing in each block once it has settled (light_flood.csh, without the colour)."""
    light = np.zeros((6,) + solid.shape)
    walls = [shifted(solid.astype(float), d) for d in DIRS]
    for _ in range(frames):
        total = light.sum(0)
        new = np.zeros_like(light)
        for f in range(6):
            around = np.zeros(solid.shape)
            for i, d in enumerate(DIRS):
                around += (1 - walls[i]) * shifted(light[f], d)
                if i ^ 1 == f:  # a wall facing back this way sends back the other facings' light as its own
                    around += walls[i] * wall * (total - light[f])
            new[f] = added[f] + around * pass_ / 6
        new[:, solid] = 0
        light = 0.5 * (light + new)
    return light


def radiosity(size, lit, holes, reflect=0.5, sub=2):
    """Irradiance on every face of a closed room of air size[0] x size[1] x size[2], the faces in lit sending 1 (their
    own light, already reflected), the faces in holes open (sending nothing, keeping nothing)."""
    nx, ny, nz = size
    faces = []
    for x in range(nx):
        for z in range(nz):
            faces += [(('floor', x, z), (x + .5, 0, z + .5), (0, 1, 0)), (('ceiling', x, z), (x + .5, ny, z + .5), (0, -1, 0))]
    for y in range(ny):
        for z in range(nz):
            faces += [(('west', y, z), (0, y + .5, z + .5), (1, 0, 0)), (('east', y, z), (nx, y + .5, z + .5), (-1, 0, 0))]
        for x in range(nx):
            faces += [(('north', y, x), (x + .5, y + .5, 0), (0, 0, 1)), (('south', y, x), (x + .5, y + .5, nz), (0, 0, -1))]
    pos, nor, owner = [], [], []
    for i, (_, c, n) in enumerate(faces):
        n, c = np.array(n, float), np.array(c, float)
        t1 = np.array([1.0, 0, 0]) if abs(n[0]) < .5 else np.array([0, 1.0, 0])
        t2 = np.cross(n, t1)
        for u in range(sub):
            for w in range(sub):
                pos.append(c + t1 * ((u + .5) / sub - .5) + t2 * ((w + .5) / sub - .5))
                nor.append(n)
                owner.append(i)
    pos, nor, owner = np.array(pos), np.array(nor), np.array(owner)
    d = pos[None] - pos[:, None]
    r2 = (d ** 2).sum(-1)
    np.fill_diagonal(r2, 1)
    cos_here = np.clip((d * nor[:, None]).sum(-1), 0, None) / np.sqrt(r2)
    cos_there = np.clip(-(d * nor[None]).sum(-1), 0, None) / np.sqrt(r2)
    form = cos_here * cos_there / (np.pi * r2) / sub ** 2
    np.fill_diagonal(form, 0)
    keys = [k for k, _, _ in faces]
    hole = np.array([keys[o] in holes for o in owner])
    form[:, hole] = 0
    own = np.array([1.0 if keys[o] in lit else 0.0 for o in owner])
    exitance = own.copy()
    for _ in range(200):
        exitance = own + np.where(hole, 0.0, reflect) * (form @ exitance)
    irradiance = form @ exitance
    out = {}
    for i, o in enumerate(owner):
        out.setdefault(keys[o], []).append(irradiance[i])
    return {k: float(np.mean(v)) for k, v in out.items()}


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--wall", type=float, default=0.85, help="bounce_wall_reflect")
    ap.add_argument("--pass", dest="pass_", type=float, default=1.0, help="bounce_pass")
    ap.add_argument("--add", type=float, default=1.5, help="bounce_add_offset (0.5, 1.0 or 1.5 here)")
    ap.add_argument("--read", type=float, default=1.0, help="bounce_read_offset (0.5, 1.0 or 1.5 here)")
    ap.add_argument("--frames", type=int, default=1200)
    args = ap.parse_args()

    size = (11, 6, 11)
    nx, ny, nz = size
    window = {('east', y, z) for y in range(2, 5) for z in range(4, 7)}
    # The grid: the room's air inside a one block shell, the window open to three blocks of air outside it.
    solid = np.ones((nx + 4, ny + 2, nz + 2), bool)
    solid[1:nx + 1, 1:ny + 1, 1:nz + 1] = False
    for _, y, z in window:
        solid[nx + 1:nx + 4, y + 1, z + 1] = False
    # Each face: its facing, and the block in front of it.
    front = {'floor': (2, lambda k: (k[1] + 1, 1, k[2] + 1)), 'ceiling': (3, lambda k: (k[1] + 1, ny, k[2] + 1)),
             'west': (0, lambda k: (1, k[1] + 1, k[2] + 1)), 'east': (1, lambda k: (nx, k[1] + 1, k[2] + 1)),
             'north': (4, lambda k: (k[2] + 1, k[1] + 1, 1)), 'south': (5, lambda k: (k[2] + 1, k[1] + 1, nz))}

    def blocks_out(key, offset):
        """The blocks offset blocks out from a face (between two block centres: both, by how near)."""
        f, at = front[key[0]]
        v, d = np.array(at(key)), np.array(DIRS[f])
        t = offset - 0.5
        i, w = int(np.floor(t)), t - np.floor(t)
        return f, [(tuple(v + d * i), 1 - w), (tuple(v + d * (i + 1)), w)]

    scenes = {
        'patch by the window': {('floor', x, z) for x in range(7, 10) for z in range(4, 7)},
        'patch mid floor': {('floor', x, z) for x in range(4, 7) for z in range(4, 7)},
        'sunlit west wall': {('west', y, z) for y in range(0, 4) for z in range(3, 8)},
    }
    probes = [('floor', 8, 5), ('floor', 1, 5), ('floor', 5, 5), ('floor', 5, 9), ('ceiling', 8, 5), ('ceiling', 1, 5),
              ('ceiling', 5, 5), ('west', 2, 5), ('west', 0, 5), ('north', 1, 8), ('north', 1, 2), ('north', 0, 5),
              ('east', 0, 5), ('east', 1, 9)]
    rows = []
    for name, lit in scenes.items():
        reference = radiosity(size, lit, window)
        added = np.zeros((6,) + solid.shape)
        for key in lit:
            f, blocks = blocks_out(key, args.add)
            for b, w in blocks:
                added[f][b] += w
        light = flood(solid, added, args.wall, args.pass_, args.frames)
        for key in probes + [sorted(lit)[len(lit) // 2]]:
            f, blocks = blocks_out(key, args.read)
            # A surface takes every facing's light but its own.
            got = sum(w * (light[:, b[0], b[1], b[2]].sum() - light[f][b]) for b, w in blocks)
            rows.append((name, key, reference[key], got))
    errors = np.log(np.maximum([g for *_, g in rows], 1e-9) / np.array([r for _, _, r, _ in rows]))
    peak = float(np.exp(errors.mean()))
    for (name, key, ref, got), e in zip(rows, errors - np.log(peak)):
        print(f"{name:20s} {key[0]:8s} {str(key[1:]):8s} radiosity {ref:.3f}  flood {got / peak:.3f}  x{np.exp(e):.2f}")
    spread = errors - np.log(peak)
    print(f"bounce_peak {peak:.2f}: typically within x{np.exp(spread.std()):.2f}, at worst x{np.exp(np.abs(spread).max()):.2f}")


if __name__ == "__main__":
    main()

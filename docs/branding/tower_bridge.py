"""Draws the Bridje logo and favicon: Tower Bridge, looking up at the near tower.

Run `python3 docs/branding/tower_bridge.py` from the repo root to rewrite
`docs/public/favicon.svg`, `docs/src/assets/logo.svg` and `docs/public/apple-touch-icon.png`.
The PNG needs `rsvg-convert` and ImageMagick's `magick` on the path.
Pass `--axes` to write `axes.svg` alongside, with the world axes drawn over the logo view.

World axes: x runs along the bridge from the near tower to the far one, y across it towards the viewer, z up.
"""

import math
import subprocess
import sys
from collections import namedtuple
from pathlib import Path

STONE = "#e6dcc8"; STONE_D = "#c7b99e"; ROOF = "#5d6b7c"; ROOF_D = "#46515f"
BLUE = "#8ec3ea"; BLUE_D = "#5f9fd3"; BLUE_L = "#bfe0f7"; WHITE = "#ffffff"; SLOT = "#3d6f9e"
WATER = "#4f94d4"; KEY = "#1d2b45"; GOLD = "#e2a52b"; RED = "#c8102e"

Projection = namedtuple("Projection", "project eye_z")


def orthographic(elev=35.26):
    e = math.radians(elev); c = math.cos(math.pi / 6)
    return Projection(lambda x, y, z: ((x + y) * c, -(x - y) * c * math.sin(e) - z * 1.2247 * math.cos(e)), None)


def perspective(eye, target):
    dot = lambda a, b: sum(a[i] * b[i] for i in range(3))
    norm = lambda a: [v / math.sqrt(dot(a, a)) for v in a]
    cross = lambda a, b: [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
    f = norm([target[i] - eye[i] for i in range(3)]); r = norm(cross(f, [0, 0, 1])); u = cross(r, f)

    def project(x, y, z):
        d = [x - eye[0], y - eye[1], z - eye[2]]; zc = dot(d, f)
        return (-dot(d, r) / zc * 40, -dot(d, u) / zc * 40)
    return Projection(project, eye[2])


def translated(eye, target, right, up):
    """The same view direction with the camera moved `right` and `up` across the picture; `up` is world z, so keep the camera level."""
    rx, ry = target[1] - eye[1], eye[0] - target[0]; n = math.hypot(rx, ry)
    # project() mirrors x, so moving right across the picture is moving along -r
    d = (-rx / n * right, -ry / n * right, up)
    return tuple(eye[i] + d[i] for i in range(3)), tuple(target[i] + d[i] for i in range(3))


W = 3; M = 0.8; A = 1.9; H = 13; ZD = 3.0
CHAIN_Z = ZD + 3.4
T1, T2 = 7, 22.5; L = 30.5
E1, E2 = T1 + A, T2 - A
WZ0, WZ1 = H - 3.6, H - 1.4


def build(proj, keyline=0.45, tildes=True, bascule_deg=30, crowns=False, roundels=False, chain_w=0.75,
          ls=0, R=3.4, hero='front', crown_s=0.75, near=((3.5, 7.5), (13, 9), (23, 7.5)), under=((15.5, 1.0),), axes=False,
          windows=True, chains=True, cornice=True, eave=0.25, pinnacles=True, arch=True):
    P = proj.project
    items = []
    poly = lambda ps, f: items.append(("poly", [P(*p) for p in ps], f))
    line = lambda ps, c, w: items.append(("line", [P(*p) for p in ps], c, w))
    cased = lambda ps, c, w: items.append(("cased", [P(*p) for p in ps], c, w))
    tilde = lambda x, y: items.append(("tilde", P(x, y, 0)))
    roundel = lambda x, y, z, r: items.append(("roundel", P(x, y, z), r))

    def crown(x, y, z, s):
        cx, cy = P(x, y, z)
        p = [(-1, 0), (-1, -1.1), (-0.5, -0.55), (0, -1.5), (0.5, -0.55), (1, -1.1), (1, 0)]
        items.append(("crown", [(cx + u * s, cy + v * s) for u, v in p], GOLD))

    def slab(x0, x1, y0, y1, z0, z1, top, front, end=None):
        below = proj.eye_z is not None and proj.eye_z < z0
        if below: poly([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], BLUE_D)
        poly([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], front)
        if end: poly([(x0, y0, z0), (x0, y1, z0), (x0, y1, z1), (x0, y0, z1)], end)
        if not below: poly([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], top)

    def chain(xa, xb, y, za, zb, sag):
        if not chains: return
        k = 24; ps = []
        for i in range(k + 1):
            t = i / k; ps.append((xa + (xb - xa) * t, y, za + (zb - za) * t - sag * 4 * t * (1 - t)))
        (cased(ps, BLUE, chain_w) if chain_w > 1 else line(ps, BLUE_D, chain_w))
        if roundels and y > 0 and min(xa, xb) < T1: roundel(*ps[-1] if abs(ps[-1][0] - xa) > 0.1 else ps[0], 0.75)
        for i in (range(2, k - 1, 5) if chain_w <= 1 else ()):
            x, _, z = ps[i]
            if z > ZD + 0.3: line([(x, y, z), (x, y, ZD)], BLUE_D, 0.3)

    def span(x0, x1, side):
        zt = CHAIN_Z
        xa, xb, za, zb = (x1, x0, zt, ZD + 0.9) if side < 0 else (x0, x1, zt, ZD + 0.9)
        sag = 1.2 * max(1, (x1 - x0) / 5) ** 0.8
        chain(xa, xb, 0, za, zb, sag)
        slab(x0, x1, 0, W, ZD - 0.9, ZD, WHITE, BLUE, BLUE_D if side < 0 else None)
        chain(xa, xb, W, za, zb, sag)

    def tower(t):
        x0, x1, y0, y1 = t - A, t + A, -M, W + M
        front_c, end_c = (STONE, STONE_D) if hero == 'front' else (STONE_D, STONE)
        b = H + 0.6; hw = H if cornice else b
        poly([(x0, y1, 0), (x1, y1, 0), (x1, y1, hw), (x0, y1, hw)], front_c)
        poly([(x0, y0, 0), (x0, y1, 0), (x0, y1, hw), (x0, y0, hw)], end_c)
        if hero == 'end' and windows:
            for cy in (W / 2 - 0.8, W / 2 + 0.8):
                poly([(x0, cy - 0.3, H - 5.2), (x0, cy + 0.3, H - 5.2), (x0, cy + 0.3, H - 1.8), (x0, cy - 0.3, H - 1.8)], SLOT)
        k = 12
        if arch: poly([(x0, 0.2, ZD - 0.9)] + [(x0, 0.2 + (W - 0.4) * (i / k), ZD + 1.6 + 1.1 * math.sin(math.pi * i / k)) for i in range(k + 1)] + [(x0, W - 0.2, ZD - 0.9)], SLOT)
        for cx in ((t - 0.75, t + 0.75) if windows else ()):
            poly([(cx - 0.3, y1, H - 5.2), (cx + 0.3, y1, H - 5.2), (cx + 0.3, y1, H - 1.8), (cx - 0.3, y1, H - 1.8)], SLOT)
        if cornice:
            poly([(x0 - 0.25, y1 + 0.25, H), (x1 + 0.25, y1 + 0.25, H), (x1 + 0.25, y1 + 0.25, H + 0.6), (x0 - 0.25, y1 + 0.25, H + 0.6)], STONE_D)
            poly([(x0 - 0.25, y0 - 0.25, H), (x0 - 0.25, y1 + 0.25, H), (x0 - 0.25, y1 + 0.25, H + 0.6), (x0 - 0.25, y0 - 0.25, H + 0.6)], STONE_D)
        apex = (t, W / 2, H + 0.6 + R); e = eave
        poly([(x0 - e, y0 - e, b), (x0 - e, y1 + e, b), apex], ROOF_D)
        poly([(x0 - e, y1 + e, b), (x1 + e, y1 + e, b), apex], ROOF)
        for cx, cy in (((x0 - e, y1 + e), (x1 + e, y1 + e), (x0 - e, y0 - e)) if pinnacles else ()):
            line([(cx, cy, b), (cx, cy, b + 1.6)], ROOF, 0.55)
        if crowns: crown(*apex, crown_s)

    def leaf(hinge, dirn, deg):
        th = math.radians(deg); l = (E2 - E1) / 2 - 0.15; n = 0.55
        x0, z0 = hinge, ZD
        x1, z1 = hinge + dirn * l * math.cos(th), ZD + l * math.sin(th)
        under_x, under_z = dirn * n * math.sin(th), -n * math.cos(th)
        poly([(x0, W, z0), (x1, W, z1), (x1 + under_x, W, z1 + under_z), (x0 + under_x, W, z0 + under_z)], BLUE_D)
        poly([(x0, 0, z0), (x1, 0, z1), (x1, W, z1), (x0, W, z0)], BLUE if dirn > 0 else BLUE_D)

    def shield():
        mx, mz, hw, ht, k, yf = (E1 + E2) / 2, (WZ0 + WZ1) / 2 + 0.2, 1.25, 1.75, 10, W - 0.2
        edge = lambda i, s: (mx + s * hw * math.cos(math.pi / 2 * i / k), mz + 0.2 - (ht + 0.2) * math.sin(math.pi / 2 * i / k))
        outline = [(mx - hw, mz + ht), (mx + hw, mz + ht)] + [edge(i, 1) for i in range(k + 1)] + [edge(i, -1) for i in range(k, -1, -1)]
        poly([(x, yf, z) for x, z in outline], GOLD)

    # painter's order: furthest from the viewer first
    if tildes:
        for x, y in under: tilde(x, y)
    span(T2 + A, L, +1)
    tower(T2)
    leaf(E2, -1, bascule_deg)
    slab(E1, E2, 0.2, W - 0.2, WZ0, WZ1, BLUE_L, BLUE)
    shield()
    leaf(E1, +1, bascule_deg)
    tower(T1)
    span(ls, T1 - A, -1)
    if tildes:
        for x, y in near: tilde(x, y)
    if axes:
        o = (T1, W / 2, ZD)
        for d, c, lab in (((1, 0, 0), "#d62728", "+x"), ((-1, 0, 0), "#d62728", "-x"), ((0, 1, 0), "#2ca02c", "+y"),
                          ((0, -1, 0), "#2ca02c", "-y"), ((0, 0, 1), "#1f5fd6", "+z"), ((0, 0, -1), "#1f5fd6", "-z")):
            items.append(("axis", [P(*o), P(*(o[i] + d[i] * 7 for i in range(3)))], c, lab))
    return items, keyline


def emit(path, items, keyline, proj, fit, tw=2.1, pad=1.0, fit_proj=None, zoom=1.0):
    """Writes items to a 32x32 SVG, scaled so the world points in `fit` fill the frame; anything else runs off its edges.

    `fit_proj` frames `fit` as another camera sees it, so moving `proj` away from it moves the picture rather than re-fitting it;
    `zoom` then scales about the frame's centre.
    """
    pts = lambda ps: " ".join(f"{x:.2f},{y:.2f}" for x, y in ps)
    framer = fit_proj or proj
    xs = [framer.project(*p)[0] for p in fit]; ys = [framer.project(*p)[1] for p in fit]
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    side = max(maxx - minx, maxy - miny); cx, cy = (minx + maxx) / 2, (miny + maxy) / 2
    sc = (32 - 2 * pad) * zoom / side
    T = lambda p: ((p[0] - cx) * sc + 16, (p[1] - cy) * sc + 16)
    items = [(it[0], T(it[1]) if it[0] in ("tilde", "roundel") else [T(p) for p in it[1]], *it[2:]) for it in items]
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" stroke-linejoin="round" stroke-linecap="round">']
    ks = f' stroke="{KEY}" stroke-width="{keyline}"' if keyline else ''
    for kind, geom, *rest in items:
        if kind == "poly":
            out.append(f'<polygon points="{pts(geom)}" fill="{rest[0]}"{ks}/>')
        elif kind == "cased":
            out.append(f'<polyline points="{pts(geom)}" fill="none" stroke="{KEY}" stroke-width="{rest[1] + keyline * 2:.2f}"/>')
            out.append(f'<polyline points="{pts(geom)}" fill="none" stroke="{rest[0]}" stroke-width="{rest[1]}"/>')
        elif kind == "crown":
            out.append(f'<polygon points="{pts(geom)}" fill="{rest[0]}" stroke="{KEY}" stroke-width="{keyline * 0.7:.2f}"/>')
        elif kind == "roundel":
            (x, y), r = geom, rest[0]
            out.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="{r}" fill="{RED}" stroke="{GOLD}" stroke-width="{r * 0.4:.2f}"/>')
        elif kind == "axis":
            (x0, y0), (x1, y1) = geom; c, lab = rest
            dash = ' stroke-dasharray="0.8 0.5"' if lab.startswith("-") else ''
            out.append(f'<line x1="{x0:.2f}" y1="{y0:.2f}" x2="{x1:.2f}" y2="{y1:.2f}" stroke="{c}" stroke-width="0.35"{dash}/>')
            out.append(f'<circle cx="{x1:.2f}" cy="{y1:.2f}" r="0.45" fill="{c}"/>')
            out.append(f'<text x="{x1 + 0.6:.2f}" y="{y1 - 0.5:.2f}" font-size="1.6" font-family="sans-serif" font-weight="bold" fill="{c}" stroke="white" stroke-width="0.3" paint-order="stroke">{lab}</text>')
        elif kind == "line":
            out.append(f'<polyline points="{pts(geom)}" fill="none" stroke="{rest[0]}" stroke-width="{rest[1]}"/>')
        elif kind == "tilde":
            x, y = geom; h = tw / 2
            out.append(f'<path d="M{x - tw:.2f},{y:.2f} q{h:.2f},{-tw * 0.55:.2f} {tw:.2f},0 t{tw:.2f},0" fill="none" stroke="{WATER}" stroke-width="{tw * 0.42:.2f}"/>')
    out.append('</svg>')
    Path(path).write_text("\n".join(out))


FAVICON_VIEWPOINT = ((-8, 14.5, 9.5), (T1, W / 2, 9.5))
FAVICON_FRAMING = perspective(*FAVICON_VIEWPOINT)
FAVICON_EYE, FAVICON_TARGET = translated(*FAVICON_VIEWPOINT, right=3, up=1.5)
FAVICON_CAMERA = perspective(FAVICON_EYE, FAVICON_TARGET)
CAMERA = perspective((*FAVICON_EYE[:2], 4.5), (*FAVICON_TARGET[:2], 13))
FAVICON_DETAIL = dict(windows=False, chains=False, cornice=False, eave=0, pinnacles=False, arch=False)
VIEW = dict(crowns=True, crown_s=1.5, chain_w=1.1, R=5, ls=-2, hero='end', tildes=False)
ROOF_TOP = H + 0.6 + 5 + 1.2


def frame(zbase):
    """The near tower from `zbase` up, plus the far tower's roof: raising `zbase` crops the base and zooms in."""
    return [(T1 - A, -M, zbase), (T1 - A, W + M, zbase), (T1 + A, W + M, zbase), (T1, 1.5, ROOF_TOP), (T2, 1.5, ROOF_TOP)]


def near_tower(zbase):
    """The near tower alone from `zbase` up; the far tower shows only where it falls inside that frame."""
    return [(T1 - A, -M, zbase), (T1 - A, W + M, zbase), (T1 + A, W + M, zbase), (T1 + A, -M, zbase), (T1, 1.5, ROOF_TOP)]


def main():
    docs = Path(__file__).resolve().parent.parent
    items, k = build(CAMERA, **VIEW)
    emit(docs / "src/assets/logo.svg", items, k, CAMERA, frame(1.5), pad=0.8)
    items, k = build(FAVICON_CAMERA, **VIEW, **FAVICON_DETAIL)
    emit(docs / "public/favicon.svg", items, k, FAVICON_CAMERA, near_tower(7.0), pad=0.6, fit_proj=FAVICON_FRAMING, zoom=1.15)
    png = subprocess.run(["rsvg-convert", "-w", "156", "-h", "156", str(docs / "src/assets/logo.svg")], check=True, capture_output=True).stdout
    subprocess.run(["magick", "-", "-background", "white", "-gravity", "center", "-extent", "180x180", "-flatten",
                    "-define", "png:exclude-chunks=date,time", str(docs / "public/apple-touch-icon.png")], input=png, check=True)
    if "--axes" in sys.argv:
        items, k = build(CAMERA, axes=True, **VIEW)
        emit("axes.svg", items, k, CAMERA, frame(1.5), pad=0.8)


if __name__ == "__main__":
    main()

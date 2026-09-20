#!/usr/bin/env python3
"""Generates every SubTrack icon asset from one description of the mark.

The mark is twelve gold coins laid out in a ring on a deep emerald ground. It is
specified once, on a 220-unit square canvas, and this script scales that single
description into each target: the adaptive icon's vector layers, the monochrome
layer for Android 13 themed icons, the notification silhouette, the pre-API-26
PNG fallbacks and the 512x512 Play Store tile.

Run from the repository root:

    python tools/icon/generate_icons.py

Pillow is the only dependency, and it is used for the raster targets only; the
vector drawables are written as plain text. Nothing here reads or writes
anything outside `app/src/main/res` and `docs/store`.
"""

from __future__ import annotations

import math
import os
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover - the raster half simply cannot run
    Image = ImageDraw = None


# --- the mark, on its own 220-unit canvas ------------------------------------
# These numbers are the design. Everything below is derived from them.

CANVAS = 220.0          # the square the mark is described on
CENTRE = 110.0          # its centre, on both axes
COINS = 12              # one per month
COIN_R = 15.0           # coin radius
RING_R = 52.0           # centre of the canvas to centre of a coin
GAP = 4.0               # ground showing between two coins
SAFE_R = 73.0           # nothing may sit further out than this

GROUND = "#0D1A14"      # phase 14a's dark emerald, fixed in both schemes
COIN = "#D4AF37"        # phase 14a's gold

# Coin centres are 2 * RING_R * sin(pi / COINS) apart, which is less than two
# coin radii, so neighbours overlap. A coin is therefore drawn as its own disc
# with a bite taken out of it by each neighbour: a disc of CUT_R centred on the
# neighbour. CUT_R is picked so that exactly GAP units of ground survive between
# the two coins.
PITCH = 2.0 * RING_R * math.sin(math.pi / COINS)
CUT_R = (PITCH + GAP) / 2.0


def coin_centres(count, ring, cx, cy):
    """Centres of `count` coins on a ring, the first one straight up at twelve o'clock."""
    out = []
    for i in range(count):
        t = 2.0 * math.pi * i / count
        out.append((cx + ring * math.sin(t), cy - ring * math.cos(t)))
    return out


# --- geometry helpers --------------------------------------------------------

def _intersections(p, rp, q, rq):
    """The two points where circle (p, rp) crosses circle (q, rq)."""
    dx, dy = q[0] - p[0], q[1] - p[1]
    d = math.hypot(dx, dy)
    a = (rp * rp - rq * rq + d * d) / (2.0 * d)
    h2 = rp * rp - a * a
    if h2 <= 0.0:
        raise ValueError("circles do not cross - the geometry is inconsistent")
    h = math.sqrt(h2)
    xm, ym = p[0] + a * dx / d, p[1] + a * dy / d
    return [(xm + h * dy / d, ym - h * dx / d), (xm - h * dy / d, ym + h * dx / d)]


def _angle(centre, point):
    return math.atan2(point[1] - centre[1], point[0] - centre[0]) % (2.0 * math.pi)


def _on_circle(centre, radius, point, tol=1e-6):
    return abs(math.hypot(point[0] - centre[0], point[1] - centre[1]) - radius) < tol


def _fmt(v):
    s = "{:.4f}".format(v).rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"


def _arc(radius, large, sweep, to):
    return "A{0},{0} 0 {1},{2} {3},{4}".format(
        _fmt(radius), large, sweep, _fmt(to[0]), _fmt(to[1]))


def carved_coin_path(centre, radius, cuts, cut_radius):
    """Path data for one coin: its own disc, less a bite from each neighbour.

    `cuts` are the two neighbouring coin centres. The result is a closed path of
    four arcs - two on the coin's own circle, two on the neighbours' cut circles -
    so the gap between coins is genuinely empty rather than painted over in the
    ground colour. That is what lets the same shape serve as the monochrome layer,
    where anything painted would be tinted the same as the coin itself.
    """
    marks = []
    for cut in cuts:
        for pt in _intersections(centre, radius, cut, cut_radius):
            marks.append((_angle(centre, pt), pt))
    marks.sort(key=lambda m: m[0])

    # The four crossings split the coin's circle into four arcs; two survive.
    kept = []
    for i in range(len(marks)):
        a1, p1 = marks[i]
        a2, p2 = marks[(i + 1) % len(marks)]
        span = (a2 - a1) % (2.0 * math.pi)
        mid_a = a1 + span / 2.0
        mid = (centre[0] + radius * math.cos(mid_a), centre[1] + radius * math.sin(mid_a))
        inside = any(math.hypot(mid[0] - c[0], mid[1] - c[1]) < cut_radius for c in cuts)
        if not inside:
            kept.append((p1, p2, span))
    if len(kept) != 2:
        raise ValueError("expected two surviving arcs, found {}".format(len(kept)))

    def connector(frm, to):
        """The bite between two surviving arcs, drawn on whichever cut circle carries it."""
        cut = next(c for c in cuts if _on_circle(c, cut_radius, frm, 1e-6))
        b1, b2 = _angle(cut, frm), _angle(cut, to)
        for sweep in (1, 0):
            span = (b2 - b1) % (2.0 * math.pi) if sweep else (b1 - b2) % (2.0 * math.pi)
            mid_a = b1 + span / 2.0 if sweep else b1 - span / 2.0
            mid = (cut[0] + cut_radius * math.cos(mid_a), cut[1] + cut_radius * math.sin(mid_a))
            # The bite has to run through the coin, not around the outside of it.
            if math.hypot(mid[0] - centre[0], mid[1] - centre[1]) < radius:
                return _arc(cut_radius, 1 if span > math.pi else 0, sweep, to)
        raise ValueError("no connecting arc lies inside the coin")

    (s1, e1, span1), (s2, e2, span2) = kept
    return (
        "M{},{} ".format(_fmt(s1[0]), _fmt(s1[1]))
        + _arc(radius, 1 if span1 > math.pi else 0, 1, e1) + " "
        + connector(e1, s2) + " "
        + _arc(radius, 1 if span2 > math.pi else 0, 1, e2) + " "
        + connector(e2, s1) + " Z"
    )


def ring_paths(viewport, count=COINS, ring=RING_R, coin=COIN_R, gap=GAP):
    """The whole mark, scaled from the 220-unit canvas to `viewport`."""
    k = viewport / CANVAS
    return dp_paths(viewport, count, ring * k, coin * k, gap * k)


def dp_paths(viewport, count, ring_units, coin_units, gap_units):
    """The same construction, with the sizes given in the target's own units."""
    cx = cy = viewport / 2.0
    centres = coin_centres(count, ring_units, cx, cy)
    pitch = 2.0 * ring_units * math.sin(math.pi / count)
    cut = (pitch + gap_units) / 2.0
    return [
        carved_coin_path(centres[i], coin_units, (centres[i - 1], centres[(i + 1) % count]), cut)
        for i in range(count)
    ]


# --- vector drawables --------------------------------------------------------

VECTOR_HEAD = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/icon/generate_icons.py - edit the script, not this file. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="{size}"
    android:viewportHeight="{size}">
"""


def vector(size, paths, colour):
    body = "".join(
        '    <path\n        android:fillColor="{}"\n        android:pathData="{}" />\n'.format(
            colour, p)
        for p in paths
    )
    return VECTOR_HEAD.format(size=_fmt(size)) + body + "</vector>\n"


# --- raster targets ----------------------------------------------------------

SUPERSAMPLE = 8
MARK_FRACTION = 0.88   # mark diameter as a share of the tile, matching how much
                       # of the adaptive icon's masked area the mark fills


def _hex(colour):
    c = colour.lstrip("#")
    return int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)


def raster(size, shape):
    """One raster tile. `shape` is 'square', 'rounded' or 'circle'."""
    if Image is None:
        raise RuntimeError("Pillow is required for the raster targets")
    s = size * SUPERSAMPLE
    tile = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(tile)
    if shape == "circle":
        draw.ellipse([0, 0, s - 1, s - 1], fill=_hex(GROUND) + (255,))
    elif shape == "rounded":
        draw.rounded_rectangle([0, 0, s - 1, s - 1], radius=s * 0.1875,
                               fill=_hex(GROUND) + (255,))
    else:
        draw.rectangle([0, 0, s - 1, s - 1], fill=_hex(GROUND) + (255,))

    # Scale the 220-unit description so the mark spans MARK_FRACTION of the tile.
    unit = (MARK_FRACTION * s / 2.0) / (RING_R + COIN_R)
    centres = coin_centres(COINS, RING_R * unit, s / 2.0, s / 2.0)
    r, cut = COIN_R * unit, CUT_R * unit
    for i, c in enumerate(centres):
        mask = Image.new("L", (s, s), 0)
        md = ImageDraw.Draw(mask)
        md.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], fill=255)
        for j in (i - 1, (i + 1) % COINS):
            a = centres[j]
            md.ellipse([a[0] - cut, a[1] - cut, a[0] + cut, a[1] + cut], fill=0)
        tile.paste(_hex(COIN) + (255,), (0, 0), mask)
    return tile.resize((size, size), Image.LANCZOS)


# --- targets -----------------------------------------------------------------

RES = os.path.join("app", "src", "main", "res")
LAUNCHER_DENSITIES = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                      ("xxhdpi", 144), ("xxxhdpi", 192)]

# The notification icon is its own drawing. A straight scale-down of the mark
# puts the four-unit gap at 0.66dp, which closes at every density below xhdpi,
# so the separation is roughly doubled and the mark is drawn to a 22dp live area.
NOTIF_VIEWPORT = 24.0
NOTIF_RING = 8.54
NOTIF_COIN = 2.46
NOTIF_GAP = 1.3


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("  " + path)


def main():
    if not os.path.isdir(RES):
        print("run this from the repository root", file=sys.stderr)
        return 1

    print("vector drawables")
    bg = (
        VECTOR_HEAD.format(size="108")
        + '    <path\n        android:fillColor="@color/ic_launcher_ground"\n'
          '        android:pathData="M0,0h108v108h-108z" />\n</vector>\n'
    )
    write(os.path.join(RES, "drawable", "ic_launcher_background.xml"), bg)

    paths108 = ring_paths(108.0)
    write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"),
          vector(108.0, paths108, "@color/ic_launcher_coin"))
    write(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"),
          vector(108.0, paths108, "@color/ic_launcher_monochrome"))

    notif = dp_paths(NOTIF_VIEWPORT, COINS, NOTIF_RING, NOTIF_COIN, NOTIF_GAP)
    write(os.path.join(RES, "drawable", "ic_notification.xml"),
          vector(NOTIF_VIEWPORT, notif, "@color/ic_notification_tint"))

    print("launcher PNGs")
    for density, size in LAUNCHER_DENSITIES:
        for name, shape in (("ic_launcher", "rounded"), ("ic_launcher_round", "circle")):
            out = os.path.join(RES, "mipmap-" + density, name + ".png")
            os.makedirs(os.path.dirname(out), exist_ok=True)
            raster(size, shape).save(out, "PNG", optimize=True)
            print("  {}  {}x{}".format(out, size, size))

    print("store tile")
    store = os.path.join("docs", "store", "icon-512.png")
    os.makedirs(os.path.dirname(store), exist_ok=True)
    raster(512, "square").save(store, "PNG", optimize=True)
    print("  {}  512x512".format(store))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

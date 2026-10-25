#!/usr/bin/env python3
"""アプリアイコン (A のモノグラム) の PNG と SVG を生成する。

    usage: python3 tools/make_icons.py

形状はこのファイルの定数が唯一の定義。出力:
  assets/icon.svg, docs/images/icon.png          リポジトリ用
  scrobbler/res/mipmap-*dpi/ic_launcher.png      アプリ用 (角丸込みの従来形式)
アダプティブアイコン用の scrobbler/res/drawable/ic_launcher_foreground.xml は
手書きで、ここの形状を 0.6 倍に縮めて同じパスを使っている。
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent

BG = (17, 18, 21)
GRADIENT = [(0.0, (240, 242, 244)), (0.55, (169, 174, 181)), (1.0, (215, 218, 221))]
COPPER = (200, 121, 74)
TILE_RADIUS = 22  # 100 単位の座標系
LETTER = [(50, 14), (80, 84), (66, 84), (61, 72), (39, 72), (34, 84), (20, 84)]
COUNTER = [(50, 36), (42, 58), (58, 58)]
PLAY = [(47, 61.5), (47, 68.5), (54, 65)]
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def hex_(c):
    return "#%02x%02x%02x" % c


def gradient_at(t):
    for (t0, c0), (t1, c1) in zip(GRADIENT, GRADIENT[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0)
            return tuple(round(a + (b - a) * f) for a, b in zip(c0, c1))
    return GRADIENT[-1][1]


def render(size):
    s = size * 4  # 4 倍で描いて縮小し、エッジを滑らかにする
    k = s / 100

    def pts(poly):
        return [(x * k, y * k) for x, y in poly]

    tile = Image.new("L", (s, s), 0)
    ImageDraw.Draw(tile).rounded_rectangle((0, 0, s - 1, s - 1), TILE_RADIUS * k, fill=255)

    ramp = Image.new("RGB", (s, s))
    px = ramp.load()
    for y in range(s):
        for x in range(s):
            px[x, y] = gradient_at((x + y) / (2 * s - 2))
    letter = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(letter)
    d.polygon(pts(LETTER), fill=255)
    d.polygon(pts(COUNTER), fill=0)

    img = Image.new("RGB", (s, s), BG)
    img.paste(ramp, mask=letter)
    ImageDraw.Draw(img).polygon(pts(PLAY), fill=COPPER)
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    out.paste(img, mask=tile)
    return out.resize((size, size), Image.LANCZOS)


def svg():
    p = lambda poly: " ".join(f"{x:g},{y:g}" for x, y in poly)
    stops = "".join(
        f'<stop offset="{t:g}" stop-color="{hex_(c)}"/>' for t, c in GRADIENT
    )
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">\n'
        f'  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">{stops}</linearGradient></defs>\n'
        f'  <rect width="100" height="100" rx="{TILE_RADIUS}" fill="{hex_(BG)}"/>\n'
        f'  <path fill="url(#g)" fill-rule="evenodd" d="M{p(LETTER)}Z M{p(COUNTER)}Z"/>\n'
        f'  <polygon fill="{hex_(COPPER)}" points="{p(PLAY)}"/>\n'
        "</svg>\n"
    )


def main():
    (ROOT / "assets").mkdir(exist_ok=True)
    (ROOT / "assets/icon.svg").write_text(svg(), encoding="utf-8")
    render(512).save(ROOT / "docs/images/icon.png", optimize=True)
    for name, size in DENSITIES.items():
        d = ROOT / f"scrobbler/res/mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        render(size).save(d / "ic_launcher.png", optimize=True)


if __name__ == "__main__":
    main()

"""captcha 内部件：纯 stdlib PNG 渲染（captcha.md 二-2 渲染样式自由 CP9——绑各栈图形库能力，
python 栈以零 Pillow 依赖的位图字模 + zlib/struct 编码实现，答案字符集/长度才是契约强制）。"""

import base64
import random
import struct
import zlib

# 5x7 位图字模（'#'/`.`），仅覆盖契约去混淆 32 字符集（二-2：A-Z 去 I/O + 2-9）
_FONT: dict[str, str] = {
    "A": ".###./#...#/#...#/#####/#...#/#...#/#...#",
    "B": "####./#...#/#...#/####./#...#/#...#/####.",
    "C": ".###./#...#/#..../#..../#..../#...#/.###.",
    "D": "####./#...#/#...#/#...#/#...#/#...#/####.",
    "E": "#####/#..../#..../####./#..../#..../#####",
    "F": "#####/#..../#..../####./#..../#..../#....",
    "G": ".###./#...#/#..../#.###/#...#/#...#/.###.",
    "H": "#...#/#...#/#...#/#####/#...#/#...#/#...#",
    "J": "....#/....#/....#/....#/#...#/#...#/.###.",
    "K": "#...#/#..#./#.#../##.../#.#../#..#./#...#",
    "L": "#..../#..../#..../#..../#..../#..../#####",
    "M": "#...#/##.##/#.#.#/#...#/#...#/#...#/#...#",
    "N": "#...#/##..#/#.#.#/#..##/#...#/#...#/#...#",
    "P": "####./#...#/#...#/####./#..../#..../#....",
    "Q": ".###./#...#/#...#/#...#/#.#.#/#..#./.##.#",
    "R": "####./#...#/#...#/####./#.#../#..#./#...#",
    "S": ".####/#..../#..../.###./....#/....#/####.",
    "T": "#####/..#../..#../..#../..#../..#../..#..",
    "U": "#...#/#...#/#...#/#...#/#...#/#...#/.###.",
    "V": "#...#/#...#/#...#/#...#/#...#/.#.#./..#..",
    "W": "#...#/#...#/#...#/#.#.#/#.#.#/##.##/#...#",
    "X": "#...#/#...#/.#.#./..#../.#.#./#...#/#...#",
    "Y": "#...#/#...#/.#.#./..#../..#../..#../..#..",
    "Z": "#####/....#/...#./..#../.#.../#..../#####",
    "2": ".###./#...#/....#/...#./..#../.#.../#####",
    "3": "####./....#/....#/.###./....#/....#/####.",
    "4": "...#./..##./.#.#./#..#./#####/...#./...#.",
    "5": "#####/#..../#..../####./....#/....#/####.",
    "6": ".###./#..../#..../####./#...#/#...#/.###.",
    "7": "#####/....#/...#./..#../.#.../.#.../.#...",
    "8": ".###./#...#/#...#/.###./#...#/#...#/.###.",
    "9": ".###./#...#/#...#/.####/....#/....#/.###.",
}


def _chunk(tag: bytes, data: bytes) -> bytes:
    return (
        struct.pack(">I", len(data))
        + tag
        + data
        + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    )


def _line(px, w, h, x0, y0, x1, y1, color):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        if 0 <= x0 < w and 0 <= y0 < h:
            px[y0][x0] = color
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def render_png_base64(text: str, *, width: int = 150, height: int = 50, scale: int = 4) -> str:
    """渲染答案文本为 PNG 裸 base64（无 data: 前缀，二-2/六-1 口径）。

    样式：白底 + 噪点 + 干扰线 + 随机深色位图字符——渲染样式各栈自由（CP9）。
    """
    rnd = random.Random()
    px = [[(255, 255, 255)] * width for _ in range(height)]

    for _ in range(160):  # 噪点
        px[rnd.randrange(height)][rnd.randrange(width)] = (170, 170, 170)
    for _ in range(3):  # 干扰线
        c = (rnd.randrange(180), rnd.randrange(180), rnd.randrange(180))
        _line(
            px,
            width,
            height,
            rnd.randrange(width),
            rnd.randrange(height),
            rnd.randrange(width),
            rnd.randrange(height),
            c,
        )

    cw = 5 * scale  # 单字符像素宽
    gap = 8
    x0 = max(4, (width - len(text) * cw - (len(text) - 1) * gap) // 2)
    y0 = max(2, (height - 7 * scale) // 2)
    for i, ch in enumerate(text):
        glyph = _FONT.get(ch)
        if glyph is None:
            raise ValueError(f"字符 {ch!r} 不在契约字符集")
        rows = glyph.split("/")
        color = (40 + rnd.randrange(120), 40 + rnd.randrange(120), 40 + rnd.randrange(120))
        for r, row in enumerate(rows):
            for c, bit in enumerate(row):
                if bit == "#":
                    for sy in range(scale):
                        for sx in range(scale):
                            px[y0 + r * scale + sy][x0 + i * (cw + gap) + c * scale + sx] = color

    raw = bytearray()
    for row in px:
        raw.append(0)
        for r, g, b in row:
            raw += bytes((r, g, b))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", ihdr)
        + _chunk(b"IDAT", zlib.compress(bytes(raw)))
        + _chunk(b"IEND", b"")
    )
    return base64.b64encode(png).decode("ascii")

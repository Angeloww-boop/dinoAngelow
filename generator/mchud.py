"""HUD de Minecraft dibujado aca para el video in game: fuente parecida a la del juego (con los codigos & y &#rrggbb
del server), hotbar, corazones, hambre, experiencia, mira, chat, barra de accion, barra de jefe, carteles de nombre
e items en la mano. Las medidas son las del juego en pixeles de GUI (por la escala G)."""
import math
import os

from PIL import Image, ImageDraw

from build import ROOT

IMG = os.path.join(ROOT, "docs", "guia", "img")

# ================================================================ fuente
_G = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "B": ["####.", "#...#", "####.", "#...#", "#...#", "#...#", "####."],
    "C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    "E": ["#####", "#....", "###..", "#....", "#....", "#....", "#####"],
    "F": ["#####", "#....", "###..", "#....", "#....", "#....", "#...."],
    "G": [".####", "#....", "#..##", "#...#", "#...#", "#...#", ".###."],
    "H": ["#...#", "#...#", "#####", "#...#", "#...#", "#...#", "#...#"],
    "I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
    "J": ["....#", "....#", "....#", "....#", "#...#", "#...#", ".###."],
    "K": ["#...#", "#..#.", "###..", "#..#.", "#...#", "#...#", "#...#"],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"],
    "N": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "P": ["####.", "#...#", "####.", "#....", "#....", "#....", "#...."],
    "Q": [".###.", "#...#", "#...#", "#...#", "#...#", "#..#.", ".##.#"],
    "R": ["####.", "#...#", "####.", "#...#", "#...#", "#...#", "#...#"],
    "S": [".####", "#....", ".###.", "....#", "....#", "#...#", ".###."],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "V": ["#...#", "#...#", "#...#", "#...#", ".#.#.", ".#.#.", "..#.."],
    "W": ["#...#", "#...#", "#...#", "#...#", "#.#.#", "##.##", "#...#"],
    "X": ["#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#", "#...#"],
    "Y": ["#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "Z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
    "a": [".....", ".....", ".###.", "....#", ".####", "#...#", ".####"],
    "b": ["#....", "#....", "#.##.", "##..#", "#...#", "#...#", "####."],
    "c": [".....", ".....", ".###.", "#...#", "#....", "#...#", ".###."],
    "d": ["....#", "....#", ".##.#", "#..##", "#...#", "#...#", ".####"],
    "e": [".....", ".....", ".###.", "#...#", "#####", "#....", ".####"],
    "f": ["..##", ".#..", "####", ".#..", ".#..", ".#..", ".#.."],
    "g": [".....", ".....", ".####", "#...#", "#...#", ".####", "....#", "####."],
    "h": ["#....", "#....", "#.##.", "##..#", "#...#", "#...#", "#...#"],
    "i": ["#", ".", "#", "#", "#", "#", "#"],
    "j": ["....#", ".....", "....#", "....#", "....#", "#...#", "#...#", ".###."],
    "k": ["#...", "#...", "#..#", "#.#.", "##..", "#.#.", "#..#"],
    "l": ["#.", "#.", "#.", "#.", "#.", "#.", ".#"],
    "m": [".....", ".....", "##.#.", "#.#.#", "#.#.#", "#...#", "#...#"],
    "n": [".....", ".....", "####.", "#...#", "#...#", "#...#", "#...#"],
    "o": [".....", ".....", ".###.", "#...#", "#...#", "#...#", ".###."],
    "p": [".....", ".....", "#.##.", "##..#", "#...#", "####.", "#....", "#...."],
    "q": [".....", ".....", ".##.#", "#..##", "#...#", ".####", "....#", "....#"],
    "r": [".....", ".....", "#.##.", "##..#", "#....", "#....", "#...."],
    "s": [".....", ".....", ".####", "#....", ".###.", "....#", "####."],
    "t": [".#.", ".#.", "###", ".#.", ".#.", ".#.", "..#"],
    "u": [".....", ".....", "#...#", "#...#", "#...#", "#...#", ".####"],
    "v": [".....", ".....", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
    "w": [".....", ".....", "#...#", "#...#", "#.#.#", "#.#.#", ".####"],
    "x": [".....", ".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#"],
    "y": [".....", ".....", "#...#", "#...#", "#...#", ".####", "....#", "####."],
    "z": [".....", ".....", "#####", "...#.", "..#..", ".#...", "#####"],
    "0": [".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."],
    "1": ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", "#####"],
    "2": [".###.", "#...#", "....#", "..##.", ".#...", "#...#", "#####"],
    "3": [".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."],
    "4": ["...##", "..#.#", ".#..#", "#...#", "#####", "....#", "....#"],
    "5": ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
    "6": ["..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."],
    "7": ["#####", "#...#", "....#", "...#.", "..#..", "..#..", "..#.."],
    "8": [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
    "9": [".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."],
    ".": [".", ".", ".", ".", ".", ".", "#"],
    ",": [".", ".", ".", ".", ".", ".", "#", "#"],
    ":": [".", "#", ".", ".", ".", ".", "#"],
    ";": [".", "#", ".", ".", ".", ".", "#", "#"],
    "'": ["#", "#"],
    "\"": ["#.#", "#.#"],
    "?": [".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."],
    "(": ["..##", ".#..", "#...", "#...", "#...", ".#..", "..##"],
    ")": ["##..", "..#.", "...#", "...#", "...#", "..#.", "##.."],
    "[": ["###", "#..", "#..", "#..", "#..", "#..", "###"],
    "]": ["###", "..#", "..#", "..#", "..#", "..#", "###"],
    "/": ["....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#...."],
    "-": [".....", ".....", ".....", "#####"],
    "+": [".....", "..#..", "..#..", "#####", "..#..", "..#.."],
    "=": [".....", ".....", "#####", ".....", ".....", "#####"],
    "_": [".....", ".....", ".....", ".....", ".....", ".....", ".....", "#####"],
    "%": ["#...#", "....#", "...#.", "..#..", ".#...", "#....", "#...#"],
    "<": ["...#", "..#.", ".#..", "#...", ".#..", "..#.", "...#"],
    ">": ["#...", ".#..", "..#.", "...#", "..#.", ".#..", "#..."],
    "|": ["#", "#", "#", "#", "#", "#", "#", "#"],
    "»": [".....", "#.#..", ".#.#.", "..#.#", ".#.#.", "#.#.."],
    "·": [".", ".", ".", "#"],
    "•": ["..", "..", "##", "##"],
    "✔": ["......#", ".....#.", "#...#..", ".#.#...", "..#...."],
    "✘": [".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#"],
    "→": [".......", "....#..", ".....#.", "#######", ".....#.", "....#.."],
    "∞": [".......", ".......", ".##.##.", "#..#..#", ".##.##."],
    "♀": [".###.", "#...#", "#...#", ".###.", "..#..", ".###.", "..#.."],
    "♂": ["..###", "...##", ".##.#", "#..#.", "#..#.", ".##..", "....."],
    "❤": [".......", ".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."],
}
_ACC = {"á": ("a", ["...#.", "..#.."]), "é": ("e", ["...#.", "..#.."]), "í": ("i", ["#", "."]),
        "ó": ("o", ["...#.", "..#.."]), "ú": ("u", ["...#.", "..#.."]), "ñ": ("n", [".##.#", "#.##."]),
        "Á": ("A", ["...#.", "..#.."]), "É": ("E", ["...#.", "..#.."]), "Í": ("I", [".#.", "#.."]),
        "Ó": ("O", ["...#.", "..#.."]), "Ú": ("U", ["...#.", "..#.."]), "Ñ": ("N", [".##.#", "#.##."])}


def glyph(ch):
    """-> (filas, ancho, fila de arriba). Las letras van de la fila 0 (arriba) a la 6 (base); 7 = descendente."""
    if ch in _G:
        rows = _G[ch]
        return rows, len(rows[0]), 0
    if ch in _ACC:
        base, acc = _ACC[ch]
        rows = _G[base]
        if base.islower():
            if base == "i":
                return ["#", "."] + rows[2:], 1, 0
            return acc + rows[2:], len(rows[0]), 0
        w = len(rows[0])
        acc = [a.ljust(w, ".")[:w] for a in acc]
        return acc + rows, w, -2
    return None, 3, 0


MC_COLORS = {"0": (0, 0, 0), "1": (0, 0, 170), "2": (0, 170, 0), "3": (0, 170, 170), "4": (170, 0, 0),
             "5": (170, 0, 170), "6": (255, 170, 0), "7": (170, 170, 170), "8": (85, 85, 85), "9": (85, 85, 255),
             "a": (85, 255, 85), "b": (85, 255, 255), "c": (255, 85, 85), "d": (255, 85, 255), "e": (255, 255, 85),
             "f": (255, 255, 255)}


def runs(text, base=(255, 255, 255)):
    """'&9&lDINO &8» &#ff00d9hola' -> [(caracter, color, negrita)]."""
    out = []
    col, bold = base, False
    i = 0
    while i < len(text):
        c = text[i]
        if c == "&" and i + 1 < len(text):
            n = text[i + 1].lower()
            if n == "#" and i + 8 <= len(text):
                h = text[i + 2:i + 8]
                col = tuple(int(h[k:k + 2], 16) for k in (0, 2, 4))
                bold = False
                i += 8
                continue
            if n in MC_COLORS:
                col, bold = MC_COLORS[n], False
                i += 2
                continue
            if n == "l":
                bold = True
                i += 2
                continue
            if n == "r":
                col, bold = base, False
                i += 2
                continue
        out.append((c, col, bold))
        i += 1
    return out


def text_width(text):
    w = 0
    for c, col, bold in runs(text):
        if c == " ":
            w += 4
        else:
            w += glyph(c)[1] + 1 + (1 if bold else 0)
    return w


_TXT = {}


def text_img(text, G, shadow=True, base=(255, 255, 255)):
    """Renglon de texto en una imagen RGBA (alto 10 px de GUI, con lugar para la sombra y las tildes)."""
    key = (text, G, shadow, base)
    if key in _TXT:
        return _TXT[key]
    w = text_width(text) + 2
    im = Image.new("RGBA", (max(1, w * G), 12 * G), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    passes = ((1, True), (0, False)) if shadow else ((0, False),)
    for off, is_shadow in passes:
        x = 0
        for c, col, bold in runs(text, base):
            if c == " ":
                x += 4
                continue
            rows, gw, top = glyph(c)
            if rows is None:
                x += gw + 1
                continue
            cc = tuple(v // 4 for v in col) if is_shadow else col
            for b in ((0, 1) if bold else (0,)):
                for ry, row in enumerate(rows):
                    for rx, px in enumerate(row):
                        if px == "#":
                            X = (x + rx + b + off) * G
                            Y = (2 + top + ry + off) * G
                            d.rectangle([X, Y, X + G - 1, Y + G - 1], fill=cc + (255,))
            x += gw + 1 + (1 if bold else 0)
    _TXT[key] = im
    return im


def draw_text(frame, text, x, y, G, alpha=1.0, shadow=True):
    """y = arriba del renglon (como el juego: la letra arranca 0 px abajo)."""
    im = text_img(text, G, shadow)
    paste(frame, im, int(x), int(y - 2 * G), alpha)
    return im.size[0]


def paste(frame, im, x, y, alpha=1.0):
    if alpha <= 0:
        return
    if alpha < 1:
        im = im.copy()
        a = im.getchannel("A").point(lambda v: int(v * alpha))
        im.putalpha(a)
    frame.alpha_composite(im, (int(x), int(y))) if x >= 0 and y >= 0 and x + im.size[0] <= frame.size[0] \
        and y + im.size[1] <= frame.size[1] else _paste_clip(frame, im, int(x), int(y))


def _paste_clip(frame, im, x, y):
    W, H = frame.size
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(W, x + im.size[0]), min(H, y + im.size[1])
    if x0 >= x1 or y0 >= y1:
        return
    frame.alpha_composite(im.crop((x0 - x, y0 - y, x1 - x, y1 - y)), (x0, y0))


# ================================================================ iconos (pixel art 16x16 de items de vanilla)
_PAL = {"K": (24, 18, 14), "R": (198, 52, 46), "r": (150, 30, 30), "P": (240, 150, 140), "W": (240, 238, 226),
        "w": (190, 186, 172), "S": (210, 214, 220), "s": (150, 156, 166), "D": (90, 96, 106), "B": (122, 82, 44),
        "b": (84, 54, 28), "Y": (226, 182, 82), "y": (176, 128, 48), "G": (96, 170, 70), "g": (60, 120, 50),
        "M": (212, 200, 152), "m": (166, 150, 106), "O": (220, 120, 40)}
ICONS = {
    "carne": [
        "................", "................", "......KKKKK.....", "....KKRRRRRKK...", "...KRRRRPRRRRK..",
        "..KRRPPRRRRrRRK.", "..KRRRRRRRPRRRK.", ".KRRrRRRRRRRRWK.", ".KRRRRRPPRRRRWK.", ".KRRRRRRRRrRWWK.",
        "..KrRRRRRRRRWWK.", "..KrrRRRRRRWWK..", "...KrrrRRWWWK...", "....KKrrWWKK....", "......KKKK......",
        "................"],
    "espada": [
        "..............KK", ".............KSK", "............KSsK", "...........KSsK.", "..........KSsK..",
        ".........KSsK...", "........KSsK....", ".......KSsK.....", "..KK..KSsK......", "..KDKKSsK.......",
        "...KDsSK........", "...KbDK.........", "..KbKKDK........", ".KbK..KDK.......", "KYK....KK.......",
        "KK.............."],
    "mapa": [
        "................", ".KKKKKKKKKKKKKK.", ".KMMMMMMMMMMMMK.", ".KMGGgMMMMyMMMK.", ".KMGGGGMMMMMMMK.",
        ".KMMGGgMMBBMMMK.", ".KMMMMMMMBMMMMK.", ".KMMMMRMMMMGGMK.", ".KMMMRRRMMMGgMK.", ".KMyMMRMMMMMMMK.",
        ".KMMMMMMMGGMMMK.", ".KMMMMMMGGgMMMK.", ".KMMMyMMMMMMMMK.", ".KmmmmmmmmmmmmK.", ".KKKKKKKKKKKKKK.",
        "................"],
    "pan": [
        "................", "................", "................", "................", ".........KKKK...",
        ".......KKYYYYK..", ".....KKYyYYYyK..", "....KYYYYyYYYK..", "...KYyYYYYYyK...", "..KYYYYyYYYYK...",
        "..KYYYYYYyYK....", "..KyYYYYYYK.....", "...KyyyyyK......", "....KKKKK.......", "................",
        "................"],
}


def icon(name, size):
    """Icono de item a size x size: los propios del plugin salen de docs/guia/img/item_<nombre>.png."""
    path = os.path.join(IMG, "item_%s.png" % name)
    if "/" in name:   # textura de item del pack (por ejemplo raptor/egg_icon)
        path = os.path.join(ROOT, "resourcepack", "assets", "dinoearth", "textures", "item", name + ".png")
    if os.path.exists(path):
        return Image.open(path).convert("RGBA").resize((size, size), Image.NEAREST)
    rows = ICONS[name]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c != ".":
                im.putpixel((x, y), _PAL[c] + (255,))
    return im.resize((size, size), Image.NEAREST)


# ================================================================ HUD
HEART = [".KK...KK.", "KRRK.KRRK", "KRWRKRRRK", "KRRRRRRRK", "KRRRRRRrK", ".KRRRRrK.", "..KRRrK..",
         "...KrK...", "....K...."]
DRUM = ["....KKK..", "...KBBBK.", "..KBBbBBK", ".KBBBBBBK", ".KBbBBBK.", "KWKBBBK..", "KWWKKK...",
        ".KWWK....", "..KK....."]
_HP = {"K": (30, 10, 10), "R": (220, 30, 30), "r": (160, 16, 16), "W": (255, 220, 220), "B": (176, 106, 52),
       "b": (220, 150, 90)}


def _pix(rows, pal, G, empty=False):
    h, w = len(rows), len(rows[0])
    im = Image.new("RGBA", (w * G, h * G), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == ".":
                continue
            col = pal[c]
            if empty and c != "K":
                col = (40, 34, 34)
            if empty and c == "K":
                col = (14, 10, 10)
            d.rectangle([x * G, y * G, x * G + G - 1, y * G + G - 1], fill=col + (255,))
    return im


class Hud:
    def __init__(self, W, H, G=3):
        self.W, self.H, self.G = W, H, G
        g = G
        self.heart = _pix(HEART, _HP, g)
        self.heart_e = _pix(HEART, _HP, g, empty=True)
        self.drum = _pix(DRUM, _HP, g)
        self.icons = {}

    def ico(self, name, size):
        k = (name, size)
        if k not in self.icons:
            self.icons[k] = icon(name, size)
        return self.icons[k]

    def hotbar(self, frame, items, sel, hearts=20, food=20, xp=0.35, level=12, alpha=1.0):
        G, W, H = self.G, self.W, self.H
        x0 = W // 2 - 91 * G
        y0 = H - 22 * G
        bar = Image.new("RGBA", (182 * G, 22 * G), (0, 0, 0, 0))
        d = ImageDraw.Draw(bar)
        d.rectangle([0, 0, 182 * G - 1, 22 * G - 1], fill=(20, 20, 20, 150))
        d.rectangle([0, 0, 182 * G - 1, 22 * G - 1], outline=(70, 70, 70, 255), width=G)
        for i in range(9):
            sx = (1 + i * 20) * G
            d.rectangle([sx, G, sx + 20 * G - 1, 21 * G - 1], outline=(140, 140, 140, 210), width=G)
            d.rectangle([sx + G, 2 * G, sx + 19 * G - 1, 20 * G - 1], outline=(52, 52, 52, 230), width=G)
        paste(frame, bar, x0, y0, alpha)
        for i, it in enumerate(items[:9]):
            if not it:
                continue
            name, count = it if isinstance(it, tuple) else (it, 1)
            ix, iy = x0 + (3 + i * 20) * G, y0 + 3 * G
            paste(frame, self.ico(name, 16 * G), ix, iy, alpha)
            if count > 1:
                s = str(count)
                draw_text(frame, s, ix + (17 - text_width(s)) * G, iy + 9 * G, G, alpha)
        # seleccionado
        sx = x0 + (sel * 20 - 1) * G
        sel_im = Image.new("RGBA", (24 * G, 24 * G), (0, 0, 0, 0))
        d = ImageDraw.Draw(sel_im)
        d.rectangle([0, 0, 24 * G - 1, 24 * G - 1], outline=(255, 255, 255, 255), width=G)
        d.rectangle([G, G, 23 * G - 1, 23 * G - 1], outline=(170, 170, 170, 255), width=G)
        paste(frame, sel_im, sx, y0 - G, alpha)
        # experiencia
        xb = Image.new("RGBA", (182 * G, 5 * G), (0, 0, 0, 0))
        d = ImageDraw.Draw(xb)
        d.rectangle([0, 0, 182 * G - 1, 5 * G - 1], fill=(20, 20, 20, 255))
        d.rectangle([G, G, 181 * G - 1, 4 * G - 1], fill=(40, 60, 30, 255))
        d.rectangle([G, G, G + int(180 * G * xp) - 1, 4 * G - 1], fill=(128, 255, 32, 255))
        paste(frame, xb, x0, y0 - 7 * G, alpha)
        ls = str(level)
        lw = text_width(ls)
        lx = W // 2 - lw * G // 2
        ly = y0 - 15 * G
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            paste(frame, text_img(ls, G, False, (0, 0, 0)), lx + dx * G, ly - 2 * G + dy * G, alpha)
        paste(frame, text_img(ls, G, False, (128, 255, 32)), lx, ly - 2 * G, alpha)
        # corazones y hambre
        hy = y0 - 17 * G
        for i in range(10):
            hx = x0 + i * 8 * G
            paste(frame, self.heart_e, hx, hy, alpha)
            if hearts >= (i + 1) * 2:
                paste(frame, self.heart, hx, hy, alpha)
            elif hearts > i * 2:
                paste(frame, self.heart.crop((0, 0, 5 * G, 9 * G)), hx, hy, alpha)
        for i in range(10):
            fx = x0 + 182 * G - (i + 1) * 8 * G - G
            if food >= (i + 1) * 2:
                paste(frame, self.drum, fx, hy, alpha)

    def crosshair(self, frame):
        G = self.G
        cx, cy = self.W // 2, self.H // 2
        d = ImageDraw.Draw(frame)
        d.rectangle([cx - 4 * G, cy - G // 2, cx + 4 * G + G - 1, cy - G // 2 + G - 1], fill=(235, 235, 235, 255))
        d.rectangle([cx - G // 2, cy - 4 * G, cx - G // 2 + G - 1, cy + 4 * G + G - 1], fill=(235, 235, 235, 255))

    def actionbar(self, frame, text, alpha=1.0, shift=0):
        G = self.G
        w = text_width(text)
        draw_text(frame, text, self.W // 2 - w * G // 2, self.H - (68 + shift) * G, G, alpha)

    def chat(self, frame, lines, now):
        """lines: [(t, texto)]: se ven 10 s y se desvanecen en el ultimo segundo (como el juego)."""
        G = self.G
        vis = [(t, s) for t, s in lines if t <= now < t + 10]
        y = self.H - 40 * G
        for t, s in reversed(vis[-10:]):
            a = min(1.0, (t + 10 - now)) * min(1.0, (now - t) / 0.12)
            bg = Image.new("RGBA", (320 * G, 9 * G), (0, 0, 0, int(128 * a)))
            paste(frame, bg, 0, y - 9 * G)
            draw_text(frame, s, 2 * G, y - 8 * G, G, a)
            y -= 9 * G

    def bossbar(self, frame, text, progress, color=(236, 0, 184), alpha=1.0):
        G = self.G
        x0 = self.W // 2 - 91 * G
        y = 12 * G
        bar = Image.new("RGBA", (182 * G, 5 * G), (0, 0, 0, 0))
        d = ImageDraw.Draw(bar)
        dark = tuple(int(c * 0.35) for c in color)
        d.rectangle([0, 0, 182 * G - 1, 5 * G - 1], fill=dark + (255,))
        d.rectangle([0, 0, int(182 * G * progress) - 1, 5 * G - 1], fill=color + (255,))
        d.rectangle([0, 0, int(182 * G * progress) - 1, G - 1], fill=tuple(min(255, c + 60) for c in color) + (255,))
        paste(frame, bar, x0, y, alpha)
        w = text_width(text)
        draw_text(frame, text, self.W // 2 - w * G // 2, y - 9 * G, G, alpha)

    def title(self, frame, text, sub=None, alpha=1.0, scale=None):
        """/title del juego: texto grande al medio (escala x4 de la GUI) y subtitulo x2."""
        G = self.G
        k = scale or 4
        im = text_img(text, G * k // 2)
        paste(frame, im, self.W // 2 - im.size[0] // 2, self.H // 2 - im.size[1] - 4 * G, alpha)
        if sub:
            im2 = text_img(sub, G)
            paste(frame, im2, self.W // 2 - im2.size[0] // 2, self.H // 2 + 4 * G, alpha)


def nametag(text, G):
    """Cartel de nombre de entidad: texto sobre fondo negro transparente."""
    t = text_img(text, G, shadow=False)
    w, h = t.size[0] + 2 * G, 10 * G
    im = Image.new("RGBA", (w, h), (0, 0, 0, 64))
    im.alpha_composite(t, (G, -G))
    return im


def held_item(name, size, swing=0.0, bob=(0, 0)):
    """Item en la mano derecha en primera persona: icono inclinado con grosor (como el render 3D del juego)."""
    ic = icon(name, 64)
    big = ic.resize((size, size), Image.NEAREST)
    ang = 22 + 40 * swing
    rot = big.rotate(ang, resample=Image.NEAREST, expand=True)
    # grosor: copias oscuras corridas
    out = Image.new("RGBA", (rot.size[0] + size // 12, rot.size[1] + size // 12), (0, 0, 0, 0))
    dark = rot.copy()
    px = dark.load()
    data = [(int(r * 0.55), int(g * 0.55), int(b * 0.55), a) for (r, g, b, a) in dark.getdata()]
    dark.putdata(data)
    th = max(1, size // 16)
    for k in range(th, 0, -1):
        out.alpha_composite(dark, (k, k))
    out.alpha_composite(rot, (0, 0))
    return out

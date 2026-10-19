"""Logo de DinoEarth: el T-Rex rugiendo (render del modelo real del plugin) delante de la Tierra pixelada (el mapa del
server) y "DINOEARTH" en letras de bloques con relieve, con los colores del server (rosa #ff00d9 y azul &9).

Uso:  python generator/logo.py   -> docs/logo/ (logo transparente, banner con fondo, icono del server 64 y 512)
Necesita Pillow.
"""
import math
import os
import random

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter

from build import ROOT, build_species
from preview import bone_matrices, mat_t
from rexlit import render_lit
import species_trex

OUT = os.path.join(ROOT, "docs", "logo")
EARTH = os.path.join(ROOT, "generator", "mapa", "tierra.webp")

PINK = (255, 0, 217)
BLUE = (85, 85, 255)
INK = (16, 8, 30)

# ---------------------------------------------------------------- letras de bloques (5 x 7)
FONT = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    "C": [".####", "#....", "#....", "#....", "#....", "#....", ".####"],
    "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    "G": [".####", "#....", "#....", "#..##", "#...#", "#...#", ".###."],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
    "J": ["..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."],
    "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "N": ["#...#", "##..#", "#.#.#", "#.#.#", "#..##", "#...#", "#...#"],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "P": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    "Q": [".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"],
    "R": ["####.", "#...#", "#...#", "####.", "#..#.", "#...#", "#...#"],
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "V": ["#...#", "#...#", "#...#", "#...#", ".#.#.", ".#.#.", "..#.."],
    "W": ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"],
    "X": ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
    "Y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
    "Z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
    " ": ["...", "...", "...", "...", "...", "...", "..."],
    # con tilde: 9 renglones (los 2 de arriba van sobre la letra)
    "Á": ["...#.", "..#..", ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "É": ["...#.", "..#..", "#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "Í": ["..#", ".#.", "###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
    "Ó": ["...#.", "..#..", ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "Ú": ["...#.", "..#..", "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "Ñ": [".##.#", "#.##.", "#...#", "##..#", "#.#.#", "#.#.#", "#..##", "#...#", "#...#"],
    ",": ["..", "..", "..", "..", "..", ".#", "#."],
    ":": [".", "#", ".", ".", ".", "#", "."],
    "-": ["....", "....", "....", "####", "....", "....", "...."],
    "%": ["##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##"],
    "+": [".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."],
    ".": [".", ".", ".", ".", ".", ".", "#"],
    "0": [".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."],
    "1": [".#.", "##.", ".#.", ".#.", ".#.", ".#.", "###"],
    "2": [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
    "3": ["####.", "....#", "....#", ".###.", "....#", "....#", "####."],
    "4": ["#...#", "#...#", "#...#", "#####", "....#", "....#", "....#"],
    "5": ["#####", "#....", "#....", "####.", "....#", "....#", "####."],
    "6": [".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."],
    "7": ["#####", "....#", "...#.", "..#..", "..#..", "..#..", "..#.."],
    "8": [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
    "9": [".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."],
}


def text_mask(text, block, gap=1):
    """Mascara (L) del texto en bloques de block x block px, con gap bloques entre letras. Si alguna letra tiene
    tilde, la mascara tiene 2 renglones mas arriba (las demas letras quedan alineadas abajo)."""
    w = sum((len(FONT[c][0]) + gap) for c in text) - gap
    rows = max(len(FONT[c]) for c in text)
    img = Image.new("L", (w * block, rows * block), 0)
    d = ImageDraw.Draw(img)
    x0 = 0
    for c in text:
        g = FONT[c]
        top = rows - len(g)
        for y, row in enumerate(g):
            y += top
            for x, ch in enumerate(row):
                if ch == "#":
                    d.rectangle(((x0 + x) * block, y * block, (x0 + x + 1) * block - 1, (y + 1) * block - 1), 255)
        x0 += len(g[0]) + gap
    return img


def rnd_cell(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 974634) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def block_word(text, block, top, mid, bottom, depth_col, outline=INK, depth=None, out_w=None, seed=1):
    """Palabra de bloques en 3D: cara con degrade y bisel por bloque, profundidad hacia abajo y un contorno grueso."""
    rnd = random.Random(seed)
    depth = depth if depth is not None else max(3, int(block * 0.75))
    out_w = out_w if out_w is not None else max(3, block // 4)
    m = text_mask(text, block)
    W, H = m.size[0] + depth + out_w * 2, m.size[1] + depth + out_w * 2
    ox, oy = out_w, out_w
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # profundidad: la mascara repetida corrida hacia abajo (y un poco a la derecha), cada vez mas oscura
    for k in range(depth, 0, -1):
        t = k / depth
        col = lerp(depth_col, tuple(int(c * 0.45) for c in depth_col), t)
        layer = Image.new("RGBA", m.size, col + (255,))
        canvas.paste(layer, (ox + k // 3, oy + k), m)
    # cara: degrade vertical + bisel de cada bloque + un poco de ruido (textura de bloque)
    face = Image.new("RGBA", m.size, (0, 0, 0, 0))
    fp = face.load()
    mp = m.load()
    for y in range(m.size[1]):
        t = y / max(1, m.size[1] - 1)
        base = lerp(top, mid, t / 0.5) if t < 0.5 else lerp(mid, bottom, (t - 0.5) / 0.5)
        for x in range(m.size[0]):
            if not mp[x, y]:
                continue
            bx, by = x % block, y % block
            k = 1.0
            edge = max(1, block // 9)
            # bisel de la letra entera (no de cada bloque): borde de arriba claro, de abajo oscuro
            up = y - edge >= 0 and mp[x, y - edge]
            down = y + edge < m.size[1] and mp[x, y + edge]
            left = x - edge >= 0 and mp[x - edge, y]
            right = x + edge < m.size[0] and mp[x + edge, y]
            if not up or not left:
                k = 1.25
            elif not down or not right:
                k = 0.8
            # textura suave de bloque (pixeles de 1/4 de bloque con un poco de variacion)
            q = max(1, block // 4)
            k *= 0.95 + 0.1 * rnd_cell(x // q, y // q, seed)
            fp[x, y] = tuple(max(0, min(255, int(c * k))) for c in base) + (255,)
    canvas.alpha_composite(face, (ox, oy))
    # contorno: dilatar la silueta entera y ponerla atras
    sil = canvas.split()[3].point(lambda a: 255 if a > 0 else 0)
    grow = sil.filter(ImageFilter.MaxFilter(out_w * 2 + 1))
    back = Image.new("RGBA", canvas.size, outline + (255,))
    result = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    result.paste(back, (0, 0), grow)
    result.alpha_composite(canvas)
    return result


def pixel_text(text, block, color, outline=INK):
    """Texto chico de pixeles (subtitulos): plano, con sombra corrida y contorno."""
    m = text_mask(text, block)
    pad, off = block, max(1, block // 2)
    W, H = m.size[0] + pad * 2 + off, m.size[1] + pad * 2 + off
    face = Image.new("L", (W, H), 0)
    face.paste(m, (pad, pad))
    shadow = Image.new("L", (W, H), 0)
    shadow.paste(m, (pad + off, pad + off))
    sil = ImageChops.lighter(face, shadow).filter(ImageFilter.MaxFilter(max(3, off * 2 + 1)))
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    img.paste(Image.new("RGBA", (W, H), outline + (255,)), (0, 0), sil)
    img.paste(Image.new("RGBA", (W, H), tuple(int(c * 0.45) for c in color) + (255,)), (0, 0), shadow)
    img.paste(Image.new("RGBA", (W, H), color + (255,)), (0, 0), face)
    return img


# ---------------------------------------------------------------- la Tierra
def globe(size, lon0=-58.0, lat0=-8.0, cells=72):
    """Globo pixelado (cells x cells) con el mapa del server proyectado (ortografica), luz de arriba a la izquierda,
    borde rosa (atmosfera) y escalado sin suavizar (bloques)."""
    tex = Image.open(EARTH).convert("RGB")
    # el webp tiene 4 px de borde negro a los costados
    tex = tex.crop((4, 0, tex.size[0] - 4, tex.size[1]))
    tw, th = tex.size
    tp = tex.load()
    small = Image.new("RGBA", (cells, cells), (0, 0, 0, 0))
    sp = small.load()
    la0, lo0 = math.radians(lat0), math.radians(lon0)
    light = (-0.55, 0.6, 0.58)
    ln = math.sqrt(sum(v * v for v in light))
    light = tuple(v / ln for v in light)
    r = cells / 2 - 0.5
    for j in range(cells):
        for i in range(cells):
            x = (i - r) / r
            y = (r - j) / r
            d2 = x * x + y * y
            if d2 > 1:
                continue
            z = math.sqrt(1 - d2)
            # rotar el punto de la vista a coordenadas del globo
            lat = math.asin(y * math.cos(la0) + z * math.sin(la0))
            lon = lo0 + math.atan2(x, z * math.cos(la0) - y * math.sin(la0))
            u = ((lon + math.pi) / (2 * math.pi)) % 1.0
            v = (math.pi / 2 - lat) / math.pi
            c = tp[min(tw - 1, int(u * tw)), min(th - 1, int(v * th))]
            lam = max(0.0, x * light[0] + y * light[1] + z * light[2])
            k = 0.6 + 0.75 * lam
            col = [min(255, int(c[q] * k)) for q in range(3)]
            rim = (1 - z) ** 3
            col = [int(col[q] * (1 - rim * 0.6) + PINK[q] * rim * 0.6) for q in range(3)]
            sp[i, j] = tuple(col) + (255,)
    big = small.resize((size, size), Image.NEAREST)
    # atmosfera: anillo de luz rosa y azul alrededor
    glow = Image.new("RGBA", (size * 2, size * 2), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for k, col, a in ((0.56, BLUE, 70), (0.535, PINK, 120)):
        rr = size * k
        gd.ellipse((size - rr, size - rr, size + rr, size + rr), fill=col + (a,))
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.05))
    out = Image.new("RGBA", glow.size, (0, 0, 0, 0))
    out.alpha_composite(glow)
    out.alpha_composite(big, (size // 2, size // 2))
    return out


# ---------------------------------------------------------------- el T-Rex
# rugido bien plantado: la pata de atras adelantada y la de adelante apenas atras (asi se ven las dos), cabeza
# levantada con la boca bien abierta, cola estirada con una curva
ROAR = {"body": (-6, 0, 0, 0), "head": (-14, 8, 0, 0), "jaw": (40, 0, 0, 0), "tail1": (6, 10, 0, 0),
        "tail2": (4, -14, 0, 0), "leg_l": (-24, 0, 0, 0), "shin_l": (16, 0, 0, 0), "leg_r": (12, 0, 0, 0),
        "shin_r": (4, 0, 0, 0)}


def trex(px_per, variant="verde", yaw=120, pitch=-8, ss=2):
    """Render del T-Rex rugiendo, fondo transparente, recortado. ss = supersampling (bordes suaves)."""
    sets = build_species(species_trex.SPECIES, {})
    s = sets[f"{variant}/male"]
    W, H = int(130 * px_per * ss), int(90 * px_per * ss)
    img = render_lit([(s, bone_matrices(s[0], ROAR, mat_t(0, -25, 0)))], yaw, pitch, px_per * ss, (W, H))
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    p = im.load()
    for y, row in enumerate(img):
        for x, c in enumerate(row):
            if c is not None:
                p[x, y] = c + (255,)
    im = im.crop(im.getbbox())
    if ss > 1:
        im = im.resize((im.size[0] // ss, im.size[1] // ss), Image.LANCZOS)
    a = im.split()[3]
    im = ImageEnhance.Color(ImageEnhance.Contrast(im.convert("RGB")).enhance(1.1)).enhance(1.15).convert("RGBA")
    im.putalpha(a)
    return im


def outlined(img, width, color=INK, glow=None):
    """Contorno de la silueta (y opcionalmente un brillo de color afuera)."""
    pad = width + (glow[1] * 3 if glow else 0)
    W, H = img.size[0] + pad * 2, img.size[1] + pad * 2
    base = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    a = Image.new("L", (W, H), 0)
    a.paste(img.split()[3], (pad, pad))
    a = a.point(lambda v: 255 if v > 40 else 0)
    if glow:
        g = a.filter(ImageFilter.MaxFilter(width * 2 + 1)).filter(ImageFilter.GaussianBlur(glow[1]))
        base.paste(Image.new("RGBA", (W, H), glow[0] + (255,)), (0, 0), g)
    o = a.filter(ImageFilter.MaxFilter(width * 2 + 1))
    base.paste(Image.new("RGBA", (W, H), color + (255,)), (0, 0), o)
    base.alpha_composite(img, (pad, pad))
    return base


# ---------------------------------------------------------------- armado
def logo(scale=1.0):
    """Logo con fondo transparente (ancho ~1600 x scale)."""
    S = lambda v: max(1, int(round(v * scale)))
    dino = block_word("DINO", S(34), (255, 150, 245), PINK, (176, 0, 150), (120, 0, 104), seed=2)
    earth = block_word("EARTH", S(34), (170, 175, 255), BLUE, (40, 40, 190), (24, 24, 120), seed=3)
    gap = S(26)
    word_w = dino.size[0] + gap + earth.size[0]
    word_h = max(dino.size[1], earth.size[1])

    g = globe(S(640))
    rex = outlined(trex(S(9.5)), S(5), INK, glow=(PINK, S(14)))

    W = max(word_w, g.size[0]) + S(80)
    H = S(80) + int(g.size[0] * 0.78) + word_h + S(40)
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gx, gy = (W - g.size[0]) // 2, S(-40)
    out.alpha_composite(g, (gx, gy))
    words_y = gy + int(g.size[1] * 0.7)
    # el T-Rex parado sobre el texto, rugiendo hacia la izquierda, encima del globo
    rx = (W - rex.size[0]) // 2 + S(40)
    ry = words_y + S(62) - rex.size[1]
    out.alpha_composite(rex, (rx, ry))
    wx = (W - word_w) // 2
    out.alpha_composite(dino, (wx, words_y))
    out.alpha_composite(earth, (wx + dino.size[0] + gap, words_y))
    return out.crop(out.getbbox())


def background(W, H):
    """Fondo del banner: noche violeta con un resplandor rosa abajo y estrellitas cuadradas."""
    bg = Image.new("RGBA", (W, H))
    p = bg.load()
    for y in range(H):
        t = y / H
        col = lerp((10, 6, 28), (44, 10, 64), t)
        for x in range(W):
            p[x, y] = col + (255,)
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((W * 0.15, H * 0.35, W * 0.85, H * 1.3), fill=PINK + (90,))
    bg.alpha_composite(glow.filter(ImageFilter.GaussianBlur(W * 0.08)))
    rnd = random.Random(7)
    d = ImageDraw.Draw(bg)
    for _ in range(int(W * H / 9000)):
        x, y = rnd.randrange(W), rnd.randrange(int(H * 0.8))
        s = rnd.choice((2, 2, 3, 4))
        c = rnd.choice(((255, 255, 255), (255, 170, 240), (170, 180, 255)))
        d.rectangle((x, y, x + s - 1, y + s - 1), fill=c + (rnd.randrange(120, 255),))
    return bg


def icon(size):
    """Icono cuadrado (server-icon): globo + cabeza del T-Rex rugiendo, sin texto."""
    big = 512
    out = background(big, big)
    g = globe(int(big * 0.78), cells=48)
    out.alpha_composite(g, ((big - g.size[0]) // 2, int(big * 0.12) - g.size[0] // 4))
    rex = outlined(trex(9), 5, INK, glow=(PINK, 12))
    # solo la parte de arriba (cabeza y pecho), grande
    rex = rex.crop((0, 0, int(rex.size[0] * 0.62), int(rex.size[1] * 0.62)))
    k = big * 0.95 / rex.size[0]
    rex = rex.resize((int(rex.size[0] * k), int(rex.size[1] * k)), Image.LANCZOS)
    out.alpha_composite(rex, (int(big * 0.04), big - rex.size[1] + int(big * 0.06)))
    return out.resize((size, size), Image.LANCZOS if size < big else Image.NEAREST)


def main():
    os.makedirs(OUT, exist_ok=True)
    lg = logo(1.0)
    lg.save(os.path.join(OUT, "dinoearth-logo.png"))
    W, H = 1920, 1080
    banner = background(W, H)
    k = min(W * 0.86 / lg.size[0], H * 0.9 / lg.size[1])
    l2 = lg.resize((int(lg.size[0] * k), int(lg.size[1] * k)), Image.LANCZOS)
    banner.alpha_composite(l2, ((W - l2.size[0]) // 2, (H - l2.size[1]) // 2))
    banner.convert("RGB").save(os.path.join(OUT, "dinoearth-banner.png"))
    icon(512).save(os.path.join(OUT, "dinoearth-icono-512.png"))
    icon(64).convert("RGB").save(os.path.join(OUT, "server-icon.png"))
    print("logo ->", OUT)


if __name__ == "__main__":
    main()

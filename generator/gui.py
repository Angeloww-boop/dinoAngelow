"""Menus con diseño de la Dinopedia (libreta de paleontologo).

Minecraft no deja cambiar la textura de un menu de cofre sin cambiarla para todos los cofres, asi que el dibujo va en
el TITULO del menu: una fuente propia donde cada "letra" es una imagen (el fondo entero, el render grande de un dino)
y espacios negativos para mover el lapiz a cualquier lugar. El texto que se escribe sobre las paginas usa copias de la
fuente de vanilla corridas hacia abajo (una fuente por renglon, ROWS).

Coordenadas: todo en pixeles de la interfaz con (0, 0) en la esquina del menu de 176 x 222 (cofre de 6 filas). El
titulo se dibuja en (8, 6); una imagen con "ascent" A y alto H queda con su borde de arriba en y = 6 + 7 - A.
El casillero (col, fila) tiene el item en x = 8 + 18 col, y = 18 + 18 fila (16 x 16).

Escribe las texturas y fuentes en el pack y plugin/src/main/resources/gui.json, de donde el plugin saca que letra es
cada imagen y cuanto avanza cada cosa. Lo llama build.py.
"""
import json
import math
import os

from build import NS, ROOT, png_bytes, write_if_changed

GUI_JSON = os.path.join(ROOT, "plugin", "src", "main", "resources", "gui.json")
WIDTHS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "font_widths.json")

W, H = 176, 222
TITLE_X, TITLE_Y = 8, 6
RENDER_X, RENDER_Y, RENDER = 16, 20, 64          # foto grande del dino (pagina izquierda)
ROWS = [6, 20, 29, 31, 42, 53, 56, 64, 65, 75, 86, 92, 97, 101]
MISSION_ROWS = [0, 2, 4]                          # fila de casilleros de cada tarjeta de mision       # renglones donde se puede escribir (y de arriba del texto)

# ---------------------------------------------------------------- colores
LEATHER = (98, 60, 34)
LEATHER_D = (62, 37, 21)
STITCH = (176, 138, 86)
PAPER = (232, 218, 182)
PAPER_L = (246, 238, 214)
INK = (60, 40, 24)
INK_SOFT = (150, 124, 88)
RULE = (204, 184, 142)
PHOTO_TOP = (222, 210, 176)
PHOTO_BOT = (196, 176, 134)
TAPE = (236, 226, 170)
GOLD = (214, 170, 66)


def slot_xy(col, row):
    return 8 + 18 * col, 18 + 18 * row


def hash2(x, y, seed=0):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def value_noise(x, y, cell, seed):
    gx, gy = x / cell, y / cell
    x0, y0 = int(math.floor(gx)), int(math.floor(gy))
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = hash2(x0, y0, seed), hash2(x0 + 1, y0, seed)
    c, d = hash2(x0, y0 + 1, seed), hash2(x0 + 1, y0 + 1, seed)
    return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3])


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


class Img:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def set(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = tuple(c[:3]) + (a,)

    def get(self, x, y):
        return self.px[y][x]

    def blend(self, x, y, c, t):
        if 0 <= x < self.w and 0 <= y < self.h:
            p = self.px[y][x]
            self.px[y][x] = mix(p, c, t) + (p[3] or 255,)

    def rect(self, x0, y0, x1, y1, color):
        """Rectangulo con bordes incluidos; color puede ser una funcion (x, y) -> rgb."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, color(x, y) if callable(color) else color)

    def png(self):
        return png_bytes(self.px)


# ---------------------------------------------------------------- texturas de fondo
def leather(x, y):
    grain = value_noise(x, y, 5, 3) * 0.10 + hash2(x, y, 4) * 0.06
    return shade(LEATHER, 0.9 + grain)


def paper(x, y, x0, y0, x1, y1, spine=None):
    """Papel con fibras, manchas suaves y los bordes un poco mas oscuros."""
    k = 0.985 + value_noise(x, y, 9, 11) * 0.04 + (hash2(x, y, 12) - 0.5) * 0.025
    edge = min(x - x0, x1 - x, y - y0, y1 - y)
    if edge < 5:
        k -= (5 - edge) * 0.018
    if spine is not None:
        d = abs(x - spine)
        if d < 9:
            k -= (9 - d) * 0.016
    stain = value_noise(x, y, 23, 17)
    if stain > 0.72:
        k -= (stain - 0.72) * 0.35
    return shade(PAPER, k)


def cover(img):
    """Tapas de cuero con costura y esquinas redondeadas (como el menu de vanilla)."""
    img.rect(0, 0, W - 1, H - 1, leather)
    for x in range(W):
        for y in (0, H - 1):
            img.set(x, y, LEATHER_D)
    for y in range(H):
        for x in (0, W - 1):
            img.set(x, y, LEATHER_D)
    for x in range(3, W - 3):
        if x % 4 < 2:
            img.set(x, 2, STITCH)
            img.set(x, H - 3, STITCH)
    for y in range(3, H - 3):
        if y % 4 < 2:
            img.set(2, y, STITCH)
            img.set(W - 3, y, STITCH)
    for cx, cy, dx, dy in ((0, 0, 1, 1), (W - 1, 0, -1, 1), (0, H - 1, 1, -1), (W - 1, H - 1, -1, -1)):
        for i, j in ((0, 0), (1, 0), (0, 1)):
            img.set(cx + dx * i, cy + dy * j, (0, 0, 0), 0)
        img.set(cx + dx, cy + dy, LEATHER_D)


def page(img, x0, y0, x1, y1, spine=None):
    img.rect(x0, y0, x1, y1, lambda x, y: paper(x, y, x0, y0, x1, y1, spine))
    for x in range(x0, x1 + 1):        # cantos de las hojas de abajo
        img.set(x, y1 + 1, shade(PAPER, 0.78))
        img.set(x, y1 + 2, shade(PAPER, 0.66) if x % 3 else shade(PAPER, 0.74))


def slot_frame(img, col, row, ring=False):
    x0, y0 = slot_xy(col, row)
    if ring:     # sello: circulo punteado
        cx, cy = x0 + 7.5, y0 + 7.5
        for y in range(y0 - 1, y0 + 17):
            for x in range(x0 - 1, x0 + 17):
                d = math.hypot(x - cx, y - cy)
                if 7.4 <= d <= 8.4 and (math.atan2(y - cy, x - cx) * 6 / math.pi) % 1 < 0.6:
                    img.blend(x, y, INK_SOFT, 0.75)
                elif d < 7.4:
                    img.blend(x, y, shade(PAPER, 0.86), 0.5)
        return
    for y in range(y0 - 1, y0 + 17):
        for x in range(x0 - 1, x0 + 17):
            p = img.get(x, y)
            if x == x0 - 1 or y == y0 - 1:
                c = shade(p, 0.74)
            elif x == x0 + 16 or y == y0 + 16:
                c = mix(p, PAPER_L, 0.7)
            else:
                c = shade(p, 0.9)
            img.set(x, y, c)


def rule(img, x0, x1, y, color=RULE, dashed=False):
    for x in range(x0, x1 + 1):
        if not dashed or x % 3 < 2:
            img.blend(x, y, color, 0.85)


def footprint(img, cx, cy, flip, t=0.22):
    """Huella de tres dedos, apenas marcada (marca de agua)."""
    pts = [(0, 2), (-1, 2), (1, 2), (0, 1), (0, 3), (-1, 3), (1, 3), (-2, 0), (-3, -1), (0, -1), (0, -2),
           (2, 0), (3, -1), (-1, 1), (1, 1)]
    for dx, dy in pts:
        img.blend(cx + (dx if not flip else -dx), cy + dy, INK, t)


def inventory_area(img):
    """Parte de abajo: el inventario del jugador sobre una hoja suelta."""
    page(img, 5, 127, W - 6, H - 8)
    for row in range(3):
        for col in range(9):
            x0, y0 = 8 + 18 * col, 140 + 18 * row
            frame_at(img, x0, y0)
    for col in range(9):
        frame_at(img, 8 + 18 * col, 198)


def frame_at(img, x0, y0):
    for y in range(y0 - 1, y0 + 17):
        for x in range(x0 - 1, x0 + 17):
            p = img.get(x, y)
            if x == x0 - 1 or y == y0 - 1:
                c = shade(p, 0.74)
            elif x == x0 + 16 or y == y0 + 16:
                c = mix(p, PAPER_L, 0.7)
            else:
                c = shade(p, 0.9)
            img.set(x, y, c)


def bg_index():
    img = Img(W, H)
    cover(img)
    page(img, 5, 4, W - 6, 124)
    rule(img, 8, W - 9, 15)
    rule(img, 8, W - 9, 17, dashed=True)
    for row in (1, 3):
        for col in range(1, 8):
            slot_frame(img, col, row)
    # rastro de huellas que cruza la pagina (abajo)
    for i in range(9):
        footprint(img, 16 + i * 18, 114 - (i % 2) * 5 - i // 3, i % 2 == 1)
    inventory_area(img)
    return img


def bg_missions():
    """Pagina de misiones: tres tarjetas, cada una con su icono a la izquierda y lugar para cambiarla a la derecha."""
    img = Img(W, H)
    cover(img)
    page(img, 5, 4, W - 6, 124)
    rule(img, 8, W - 9, 15)
    rule(img, 8, W - 9, 17, dashed=True)
    for row in MISSION_ROWS:
        x0, y0 = slot_xy(0, row)
        top, bottom = y0 - 2, y0 + 17
        for y in range(top, bottom + 1):
            for x in range(7, W - 8):
                edge = y in (top, bottom) or x in (7, W - 9)
                img.blend(x, y, INK_SOFT if edge else PAPER_L, 0.55 if edge else 0.35)
        slot_frame(img, 0, row)
    inventory_area(img)
    return img


def spread(stamps):
    """Libro abierto: foto del dino a la izquierda, datos a la derecha."""
    img = Img(W, H)
    cover(img)
    page(img, 5, 4, 86, 124, spine=87.5)
    page(img, 89, 4, W - 6, 124, spine=87.5)
    for y in range(4, 127):
        img.set(87, y, shade(LEATHER_D, 0.8))
        img.set(88, y, shade(LEATHER_D, 0.9))
    rule(img, 8, 83, 16)
    rule(img, 92, W - 9, 16)
    # foto con borde y cinta
    fx0, fy0, fx1, fy1 = RENDER_X - 3, RENDER_Y - 3, RENDER_X + RENDER + 2, RENDER_Y + RENDER + 2
    for y in range(fy0 + 1, fy1 + 2):
        for x in range(fx0 + 1, fx1 + 2):
            img.blend(x, y, INK, 0.25)          # sombra de la foto
    img.rect(fx0, fy0, fx1, fy1, lambda x, y: shade(PAPER_L, 0.99 + hash2(x, y, 31) * 0.02))
    for y in range(RENDER_Y, RENDER_Y + RENDER):
        t = (y - RENDER_Y) / RENDER
        for x in range(RENDER_X, RENDER_X + RENDER):
            c = mix(PHOTO_TOP, PHOTO_BOT, t * t)
            if y >= RENDER_Y + 56:     # piso
                c = shade(PHOTO_BOT, 0.93 + hash2(x, y, 41) * 0.05)
            img.set(x, y, shade(c, 0.97 + value_noise(x, y, 6, 43) * 0.05))
    cx, cy = RENDER_X + RENDER / 2, RENDER_Y + 60.5
    for y in range(RENDER_Y + 56, RENDER_Y + RENDER):
        for x in range(RENDER_X, RENDER_X + RENDER):
            d = ((x + 0.5 - cx) / 25) ** 2 + ((y + 0.5 - cy) / 3.2) ** 2
            if d < 1:
                img.blend(x, y, INK, 0.35 * (1 - d))
    for (tx, ty), sgn in (((fx0 - 2, fy0 - 2), 1), ((fx1 - 9, fy0 - 2), -1)):   # cinta en las esquinas
        for i in range(12):
            for j in range(5):
                x = tx + i if sgn > 0 else tx + 11 - i
                img.blend(x, ty + j + i // 3, TAPE, 0.55)
    # pagina derecha: renglones
    for t in [20, 31, 42, 53, 64, 75] + ([] if stamps else [86, 97]):
        rule(img, 92, W - 9, t + 9)
    if stamps:
        for col in range(4):
            slot_frame(img, col, 4)
        for col in range(1, 4):
            slot_frame(img, col, 5)
        for row in (4, 5):
            for col in (5, 6, 7):
                slot_frame(img, col, row, ring=True)
    else:
        for i in range(4):
            footprint(img, 14 + i * 18, 108 - (i % 2) * 4, i % 2 == 0)
    inventory_area(img)
    return img


# ---------------------------------------------------------------- iconos chicos (8 x 8) para los renglones
def icon_sheet():
    """Iconos que se escriben como letras. Devuelve (imagen, [(clave, letra)], alto de celda)."""
    names = ["heart", "sword", "speed", "size", "meat", "leaf", "fish", "star", "pip_on", "pip_off",
             "seg_on", "seg_off", "·", "♂", "♀", "…", "check", "skull"]
    img = Img(8 * 16, 8 * 2)
    out = []
    for i, name in enumerate(names):
        ox, oy = (i % 16) * 8, (i // 16) * 8
        for (x, y), c in draw_icon(name).items():
            img.set(ox + x, oy + y, c)
        char = name if len(name) == 1 else chr(0xE100 + i)
        out.append((name, char))
    return img, out


def draw_icon(name):
    p = {}

    def put(pts, c):
        for x, y in pts:
            p[(x, y)] = c

    def grid(rows, palette):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != ".":
                    p[(x, y)] = palette[ch]

    white, grey = (255, 255, 255), (200, 200, 200)
    if name == "heart":
        grid([".rr.rr.", "rRrrrrr", "rRrrrrr", "rrrrrrr", ".rrrrr.", "..rrr..", "...r..."],
             {"r": (196, 48, 48), "R": (240, 140, 130)})
    elif name == "sword":
        grid(["......s", ".....sS", "....sS.", ".h.sS..", "..hS...", "..wh...", ".w..h.."],
             {"s": (200, 204, 212), "S": (140, 144, 156), "h": (120, 80, 40), "w": (90, 60, 30)})
    elif name == "speed":
        grid(["b..b...", ".b..b..", "..b..b.", "...b..b", "..b..b.", ".b..b..", "b..b..."],
             {"b": (70, 140, 200)})
    elif name == "size":
        grid([".......", "kkkkkkk", "kYkYkYk", "kYYYYYk", "kkkkkkk", ".......", "......."],
             {"k": (110, 80, 50), "Y": (226, 196, 120)})
    elif name == "meat":
        grid(["..rrr..", ".rRrrr.", ".rrrrr.", "..rrr..", "...bb..", "...b.b.", "....b.."],
             {"r": (170, 70, 50), "R": (220, 130, 110), "b": (236, 226, 200)})
    elif name == "leaf":
        grid(["....gg.", "..ggGg.", ".gggGg.", ".ggGgg.", ".gGgg..", ".Gg....", "G......"],
             {"g": (90, 150, 60), "G": (50, 100, 40)})
    elif name == "fish":
        grid([".......", "..bbb..", ".bbbbb.b", "bWbbbbbb", ".bbbbb.b", "..bbb..", "......."],
             {"b": (90, 140, 180), "W": (20, 20, 30)})
    elif name == "star":
        grid(["...y...", "...y...", "yyyyyyy", ".yyyyy.", "..yyy..", ".yy.yy.", "y.....y"],
             {"y": (232, 184, 60)})
    elif name == "pip_on":
        grid([".....", "wwwww", "wwwww", "wwwww", "wwwww", "wwwww"], {"w": white})
        p[(0, 1)] = p[(4, 1)] = p[(0, 5)] = p[(4, 5)] = grey
    elif name == "pip_off":
        grid([".....", "wwwww", "w...w", "w...w", "w...w", "wwwww"], {"w": white})
    elif name == "seg_on":
        grid(["....", "wwww", "wwww", "wwww", "wwww", "wwww"], {"w": white})
    elif name == "seg_off":
        grid(["....", "wwww", "w..w", "w..w", "w..w", "wwww"], {"w": white})
    elif name == "·":
        grid(["..", "..", "..", "ww", "ww"], {"w": white})
    elif name == "♂":
        grid(["...wwww", ".....ww", "....w.w", ".www...", "w...w..", "w...w..", ".www..."], {"w": white})
    elif name == "♀":
        grid([".www.", "w...w", "w...w", ".www.", "..w..", ".www.", "..w.."], {"w": white})
    elif name == "…":
        grid([".....", ".....", ".....", ".....", ".....", "w.w.w"], {"w": white})
    elif name == "check":
        grid(["......g", ".....gg", "g...gg.", "gg.gg..", ".ggg...", "..g...."], {"g": (60, 140, 60)})
    elif name == "skull":
        grid([".www.", "wwwww", "w.w.w", "wwwww", ".w.w."], {"w": (230, 226, 210)})
    return p


def minibars():
    """Barritas de 16 x 3 debajo de cada especie del indice: 0..8 octavos completos."""
    img = Img(16, 8 * 9)          # celdas de 16 x 8 (alto 8: el ascent no puede pasar el alto)
    for k in range(9):
        for x in range(16):
            for y in range(3):
                on = x < k * 2
                edge = y in (0, 2) or x in (0, 15)
                if on:
                    img.set(x, k * 8 + y, (255, 255, 255) if not (y == 0) else (228, 228, 228))
                elif edge:
                    img.set(x, k * 8 + y, (150, 150, 150))
    return img


# ---------------------------------------------------------------- iconos 16 x 16 (botones y sellos, son items)
def button_icons():
    import items
    from icons import book
    out = {"book": book()}

    def arrow(left):
        c = items.Canvas()
        pts = [(12.5, 8), (3.5, 8)] if left else [(3.5, 8), (12.5, 8)]
        c.stroke(pts, 1.7, lambda t, x, y: INK)
        tip = pts[1]
        d = -1 if left else 1
        c.stroke([(tip[0] - d * 4, 4), tip, (tip[0] - d * 4, 12)], 1.7, lambda t, x, y: INK)
        return c.finish(outline=1.6)

    out["prev"], out["next"] = arrow(True), arrow(False)

    def lens(on):
        c = items.Canvas()
        rim = GOLD if on else (120, 110, 96)
        c.stroke([(10.5, 10.5), (14, 14)], 2.6, lambda t, x, y: (96, 62, 34))
        c.fill(lambda x, y: math.hypot(x - 6.5, y - 6.5) <= 5.2,
               lambda x, y: rim if math.hypot(x - 6.5, y - 6.5) > 3.6 else
               ((176, 214, 226) if on else (180, 186, 190)))
        if on:
            for x, y in ((5, 5), (4, 6), (5, 7)):
                c.dot(x, y, (250, 250, 250))
        return c.finish()

    out["track"], out["track_off"] = lens(True), lens(False)

    def help_icon():
        c = items.Canvas()
        c.fill(lambda x, y: 2 <= x <= 14 and 1 <= y <= 15, lambda x, y: shade(PAPER, 1.0 - (y - 1) * 0.008))
        for y, row in enumerate(["..ttt..", ".t...t.", ".....t.", "....t..", "...t...", ".......", "...t..."]):
            for x, ch in enumerate(row):
                if ch == "t":
                    c.dot(5 + x, 4 + y, INK)
        return c.finish(outline=0.6)

    out["help"] = help_icon()

    def missions_icon():
        c = items.Canvas()
        c.fill(lambda x, y: 3 <= x <= 12 and 2 <= y <= 14, lambda x, y: shade(PAPER, 1.0 - (y - 2) * 0.01))
        c.fill(lambda x, y: (2 <= x <= 13) and (y <= 3 or y >= 13) and 1 <= y <= 15,
               lambda x, y: (150, 104, 60))
        for i, yy in enumerate((5, 8, 11)):
            c.dot(5, yy, (60, 140, 60) if i < 2 else (150, 140, 120))
            for xx in range(7, 11):
                c.dot(xx, yy, INK)
        return c.finish(outline=0.6)

    def reroll_icon():
        c = items.Canvas()
        c.stroke([(12, 6), (10, 3.5), (6.5, 3.2), (3.8, 5.5), (3.2, 8.5)], 1.8, lambda t, x, y: GOLD)
        c.stroke([(4, 10), (6, 12.5), (9.5, 12.8), (12.2, 10.5), (12.8, 7.5)], 1.8, lambda t, x, y: GOLD)
        for x, y in ((12, 3), (13, 4), (13, 5), (11, 6), (12, 6)):
            c.dot(x, y, GOLD)
        for x, y in ((3, 12), (2, 11), (2, 10), (4, 10), (3, 10)):
            c.dot(x, y, GOLD)
        return c.finish(outline=0.5)

    out["missions"] = missions_icon()
    out["reroll"] = reroll_icon()
    for i, col in enumerate(RANK_COLORS):
        out[f"rank{i}"] = medal(col)
    return out


RANK_COLORS = [(150, 104, 70), (176, 120, 60), (180, 186, 194), (226, 182, 70), (110, 200, 190), (190, 120, 220)]


def medal(col):
    import items
    c = items.Canvas()
    c.fill(lambda x, y: y < 7 and abs(x - 8) <= 3.2 and abs((x - 8) + (y - 3) * 0.0) <= 3.5,
           lambda x, y: (176, 50, 50) if x < 8 else (60, 80, 170))
    c.fill(lambda x, y: math.hypot(x - 8, y - 10.5) <= 4.6,
           lambda x, y: shade(col, 1.15 - 0.06 * (x - 6) - 0.05 * (y - 8)))
    for x, y in ((8, 9), (7, 10), (8, 10), (9, 10), (8, 11), (7, 12), (9, 12)):
        c.dot(x, y, shade(col, 0.7))
    return c.finish()


STAMPS = {
    "visto": ((70, 110, 150), "eye"),
    "colores": ((160, 90, 60), "palette"),
    "raro": ((150, 80, 170), "gem"),
    "domesticado": ((60, 130, 70), "heart"),
    "criado": ((180, 140, 60), "egg"),
    "jinete": ((130, 80, 50), "saddle"),
    "hombro": ((90, 120, 160), "feather"),
    "cazado": ((150, 50, 40), "swords"),
}


def stamp(color, symbol, on):
    """Sello de tinta redondo; apagado = gris y lavado."""
    import items
    c = items.Canvas()
    ink = color if on else (168, 158, 140)
    paper_in = mix(PAPER_L, color, 0.12) if on else (226, 218, 198)
    c.fill(lambda x, y: math.hypot(x - 8, y - 8) <= 7.4,
           lambda x, y: ink if math.hypot(x - 8, y - 8) > 6.0 else paper_in)
    for x, y in symbol_pixels(symbol):
        c.dot(x, y, ink if on else (150, 140, 122))
    if on:
        for y in range(16):        # tinta despareja
            for x in range(16):
                if c.px[y][x] is not None and hash2(x, y, 77) > 0.86:
                    c.px[y][x] = mix(c.px[y][x], PAPER_L, 0.35)
    px = c.finish(outline=0.85 if on else 0.92)
    if not on:
        px = [[(p[0], p[1], p[2], 200) if p[3] else p for p in row] for row in px]
    return px


def symbol_pixels(symbol):
    rows = {
        "eye": ["........", "..xxxx..", ".x....x.", "x..xx..x", "x..xx..x", ".x....x.", "..xxxx.."],
        "palette": [".xx..xx.", "xxxxxxxx", ".xx..xx.", "........", "...xx...", "..xxxx..", "...xx..."],
        "gem": ["........", "..xxxx..", ".x.x.x.x", "xxxxxxxx", ".x....x.", "..x..x..", "...xx..."],
        "heart": ["........", ".xx..xx.", "xxxxxxxx", "xxxxxxxx", ".xxxxxx.", "..xxxx..", "...xx..."],
        "egg": ["...xx...", "..x..x..", ".x....x.", ".x.x..x.", ".x....x.", ".x..x.x.", "..xxxx.."],
        "saddle": ["..xxxx..", ".xx..xx.", "xx....xx", "x......x", "x......x", "xx....xx", "x......x"],
        "feather": [".....xx.", "....xxx.", "...xxx..", "..xxx...", ".xxx....", ".x......", "x......."],
        "swords": ["x......x", ".x....x.", "..x..x..", "...xx...", "..x..x..", "xx....xx", "x......x"],
    }[symbol]
    return [(4 + x, 4 + y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch == "x"]


# ---------------------------------------------------------------- render grande de cada dino (64 x 64)
def big_render(img):
    """Del render crudo (lista de filas, None = vacio) a 64 x 64 con las patas abajo y borde de tinta."""
    ys = [y for y, row in enumerate(img) if any(p is not None for p in row)]
    xs = [x for x in range(len(img[0])) if any(row[x] is not None for row in img)]
    x0, x1, y0, y1 = min(xs), max(xs) + 1, min(ys), max(ys) + 1
    inner_w, inner_h = RENDER - 6, RENDER - 7
    k = max((x1 - x0) / inner_w, (y1 - y0) / inner_h)
    w, h = (x1 - x0) / k, (y1 - y0) / k
    left, top = (RENDER - w) / 2, (RENDER - 3) - h
    cell = [[None] * RENDER for _ in range(RENDER)]
    for j in range(RENDER):
        for i in range(RENDER):
            sx0, sy0 = x0 + (i - left) * k, y0 + (j - top) * k
            total, n, acc = 0, 0, [0, 0, 0]
            for y in range(int(sy0), int(sy0 + k) + 1):
                for x in range(int(sx0), int(sx0 + k) + 1):
                    if not (y0 <= y < y1 and x0 <= x < x1):
                        continue
                    total += 1
                    p = img[y][x]
                    if p is not None:
                        n += 1
                        for c in range(3):
                            acc[c] += p[c]
            if total and n / max(total, 1) >= 0.45:
                cell[j][i] = (acc[0] // n, acc[1] // n, acc[2] // n)
    out = [[(0, 0, 0, 0)] * RENDER for _ in range(RENDER)]
    for j in range(RENDER):
        for i in range(RENDER):
            p = cell[j][i]
            if p is not None:
                out[j][i] = p + (255,)
            elif any(0 <= j + dy < RENDER and 0 <= i + dx < RENDER and cell[j + dy][i + dx] is not None
                     for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[j][i] = INK + (255,)
    return out


def actual_width(pix, x0, cw, y0, ch):
    """Igual que Minecraft: columna mas a la derecha con algun pixel visible + 1."""
    for x in range(cw - 1, -1, -1):
        if any(pix[y0 + y][x0 + x][3] for y in range(ch)):
            return x + 1
    return 0


# ---------------------------------------------------------------- todo junto
def build_gui(species, raws, files):
    """raws: {(especie, color): render crudo} (de icons.build_icons)."""
    with open(WIDTHS, encoding="utf-8") as fh:
        vanilla = json.load(fh)
    tex = f"assets/{NS}/textures/gui"
    font_dir = f"assets/{NS}/font"
    data = {"x0": TITLE_X, "rows": {}, "images": {}, "icons": {}, "space": {}, "widths": {}}

    # espacios: potencias de 2 para adelante y para atras
    adv = {}
    for i in range(10):
        adv[chr(0xF800 + i)] = -(1 << i)
        adv[chr(0xF820 + i)] = 1 << i
        data["space"][str(-(1 << i))] = chr(0xF800 + i)
        data["space"][str(1 << i)] = chr(0xF820 + i)
    files[f"{font_dir}/gui_space.json"] = json.dumps({"providers": [{"type": "space", "advances": adv}]},
                                                     ensure_ascii=False, indent=1).encode()
    data["space_font"] = f"{NS}:gui_space"

    # imagenes: fondos y renders
    providers = []
    next_char = [0xE000]

    def image(key, pix, top, path):
        char = chr(next_char[0])
        next_char[0] += 1
        files[f"{tex}/{path}.png"] = png_bytes(pix)
        h = len(pix)
        providers.append({"type": "bitmap", "file": f"{NS}:gui/{path}.png", "ascent": TITLE_Y + 7 - top,
                          "height": h, "chars": [char]})
        data["images"][key] = [char, actual_width(pix, 0, len(pix[0]), 0, h) + 1]

    image("bg/index", bg_index().px, 0, "bg_index")
    image("bg/species", spread(True).px, 0, "bg_species")
    image("bg/card", spread(False).px, 0, "bg_card")
    image("bg/missions", bg_missions().px, 0, "bg_missions")
    for sp in species:
        sid = sp["id"]
        cells = [(vid, big_render(raws[(sid, vid)])) for vid in sp["variants"]]
        sheet = [sum((list(c[1][y]) for c in cells), []) for y in range(RENDER)]
        chars = ""
        for i, (vid, cell) in enumerate(cells):
            ch = chr(next_char[0])
            next_char[0] += 1
            chars += ch
            data["images"][f"render/{sid}/{vid}"] = [ch, actual_width(sheet, i * RENDER, RENDER, 0, RENDER) + 1]
        files[f"{tex}/render/{sid}.png"] = png_bytes(sheet)
        providers.append({"type": "bitmap", "file": f"{NS}:gui/render/{sid}.png",
                          "ascent": TITLE_Y + 7 - RENDER_Y, "height": RENDER, "chars": [chars]})
    files[f"{font_dir}/gui_img.json"] = json.dumps({"providers": providers}, ensure_ascii=False, indent=1).encode()
    data["image_font"] = f"{NS}:gui_img"

    # renglones: fuente de vanilla corrida + iconos chicos + barritas
    sheet, icons = icon_sheet()
    files[f"{tex}/icons.png"] = sheet.png()
    bars = minibars()
    files[f"{tex}/minibars.png"] = bars.png()
    icon_rows = ["".join(ch for _, ch in icons[:16]), "".join(ch for _, ch in icons[16:]).ljust(16, "\u0000")]
    for i, (key, ch) in enumerate(icons):
        ox, oy = (i % 16) * 8, (i // 16) * 8
        data["icons"][key] = [ch, actual_width(sheet.px, ox, 8, oy, 8) + 1]
    bar_chars = "".join(chr(0xE180 + k) for k in range(9))
    for k in range(9):
        data["icons"][f"mbar{k}"] = [bar_chars[k], 17]
    for t in ROWS:
        shift = t - TITLE_Y
        providers = [
            {"type": "space", "advances": {" ": 4}},
            {"type": "bitmap", "file": f"{NS}:gui/icons.png", "ascent": 7 - shift, "height": 8, "chars": icon_rows},
            {"type": "bitmap", "file": f"{NS}:gui/minibars.png", "ascent": 7 - shift, "height": 8,
             "chars": list(bar_chars)},
            {"type": "bitmap", "file": "minecraft:font/accented.png", "ascent": vanilla["accented"]["ascent"] - shift,
             "height": vanilla["accented"]["height"], "chars": vanilla["accented"]["chars"]},
            {"type": "bitmap", "file": "minecraft:font/ascii.png", "ascent": vanilla["ascii"]["ascent"] - shift,
             "chars": vanilla["ascii"]["chars"]},
        ]
        files[f"{font_dir}/gui_t{t}.json"] = json.dumps({"providers": providers}, ensure_ascii=False, indent=1).encode()
        data["rows"][str(t)] = f"{NS}:gui_t{t}"
    widths = {" ": 4}
    widths.update(vanilla["ascii"]["widths"])
    for ch, w in vanilla["accented"]["widths"].items():
        widths.setdefault(ch, w)
    for key, (ch, w) in data["icons"].items():
        if len(key) == 1:
            widths[ch] = w
    # solo lo que se usa en español (el resto de accented.png no hace falta en el plugin)
    keep = set(" !\"#$%&'()*+,-./0123456789:;<=>?@[\\]^_`{|}~ºª«»°±") | set(
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz") | set("áéíóúüñÁÉÍÓÚÜÑ·♂♀…")
    data["widths"] = {ch: w for ch, w in sorted(widths.items()) if ch in keep}

    # flechas para la barra de accion (rastrear)
    hud = Img(8 * 9, 8)
    arrows = ""
    up = ["...x...", "..xxx..", ".x.x.x.", "x..x..x", "...x...", "...x...", "...x..."]
    diag = ["..xxxxx", ".....xx", "....x.x", "...x..x", "..x...x", ".x.....", "x......"]
    for i in range(8):        # 0 = adelante (arriba), en sentido horario de a 45 grados
        pts = [(x, y) for y, row in enumerate(up if i % 2 == 0 else diag) for x, ch in enumerate(row) if ch == "x"]
        for _ in range(i // 2):
            pts = [(6 - y, x) for x, y in pts]      # 90 grados en sentido horario
        for x, y in pts:
            hud.set(i * 8 + x, y, (240, 200, 90))
        arrows += chr(0xE1A0 + i)
    for y, row in enumerate(["x..x..x", "x..x..x", ".x.x.x.", "..xxx..", "..xxx..", "...x...", "......."]):
        for x, ch in enumerate(row):
            if ch == "x":
                hud.set(64 + x, y, (200, 180, 140))
    arrows += chr(0xE1A8)
    files[f"{tex}/hud.png"] = hud.png()
    files[f"{font_dir}/hud.json"] = json.dumps({"providers": [
        {"type": "bitmap", "file": f"{NS}:gui/hud.png", "ascent": 7, "height": 8, "chars": [arrows]},
        {"type": "reference", "id": "minecraft:default"}]}, ensure_ascii=False, indent=1).encode()
    data["hud_font"] = f"{NS}:hud"
    data["hud"] = list(arrows)

    # botones y sellos (items)
    from icons import put
    for key, pix in button_icons().items():
        put(files, f"gui/{key}", pix)
    for key, (color, symbol) in STAMPS.items():
        put(files, f"gui/stamp/{key}", stamp(color, symbol, True))
        put(files, f"gui/stamp/{key}_off", stamp(color, symbol, False))
    files[f"assets/{NS}/items/gui/blank.json"] = json.dumps({"model": {"type": "minecraft:empty"}}).encode()

    data["layout"] = {"render_x": RENDER_X, "render_y": RENDER_Y, "render": RENDER}
    text = json.dumps(data, ensure_ascii=False, indent=1, sort_keys=True) + "\n"
    write_if_changed(GUI_JSON, text.encode("utf-8"))

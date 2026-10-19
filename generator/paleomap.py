"""Mapa del Paleontologo: la imagen que se dibuja en el item mapa (128 x 128, como un mapa de Minecraft).

Estilo de la Dinopedia: un mapa de explorador en papel, con tapas de cuero cosido. El mar es papel con lineas de agua
junto a las costas, la tierra va en tonos sepia (mas oscuro donde hay selva) con la costa en tinta, y arriba y abajo
hay un rastro de huellas como marca de agua. Las zonas de dinos NO se dibujan aca: el plugin pone una huella roja en el
centro de cada region del config (asi siguen al config si cambia).

Todos los colores son de la paleta de los mapas de Minecraft (el server convierte cada pixel al color mas cercano:
si se usan colores de afuera de la paleta el papel sale manchado).

Fuente: generator/mapa/tierra.webp, un render del mapa del server (equirectangular: de 180 O a 180 E y de 90 N a 90 S,
con 4 px de borde negro a cada lado).

Uso (necesita Pillow, esta en generator/.venv):
    generator/.venv/Scripts/python generator/paleomap.py [carpeta para una vista previa ampliada]
Escribe plugin/src/main/resources/mapa/tierra.png
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SOURCE = os.path.join(HERE, "mapa", "tierra.webp")
OUT = os.path.join(ROOT, "plugin", "src", "main", "resources", "mapa", "tierra.png")

BORDER_X = 4                  # columnas negras a cada lado del render
SIZE = 128                    # un mapa de Minecraft
# donde va el mundo (360 x 180 grados): lo mismo que PaleoMap.java (WORLD_X, WORLD_Y, WORLD_W, WORLD_H)
WX, WY, WW, WH = 6, 35, 116, 58

# colores de la paleta de los mapas
PAPER = (247, 233, 163)       # mar y bandas
PAPER_D = (213, 201, 140)     # tierra y lineas de agua
OLIVE = (174, 164, 115)       # selva y bosques
INK_SOFT = (130, 123, 86)     # costas y huellas de marca de agua
INK = (54, 40, 27)            # tinta oscura
ICE = (220, 220, 220)         # hielo
LEATHER = (91, 60, 34)
LEATHER_D = (53, 35, 24)
STITCH = (152, 123, 103)


def is_water(r, g, b):
    """Azules (oceano, costas y lineas de rutas) pero no el hielo blanco ni la tierra."""
    return b > g + 4 and b > r + 15 and r + g + b < 600


def classify():
    """Para cada pixel del mundo (WW x WH): 'w' agua, 'i' hielo, 'f' selva/bosque, 'l' tierra."""
    src = Image.open(SOURCE).convert("RGB")
    w, h = src.size
    src = src.crop((BORDER_X, 0, w - BORDER_X, h))
    w, h = src.size
    px = src.load()
    grid = [[None] * WW for _ in range(WH)]
    for cy in range(WH):
        y0, y1 = cy * h // WH, max(cy * h // WH + 1, (cy + 1) * h // WH)
        for cx in range(WW):
            x0, x1 = cx * w // WW, max(cx * w // WW + 1, (cx + 1) * w // WW)
            water, land, n = 0, [0, 0, 0], 0
            for y in range(y0, y1):
                for x in range(x0, x1):
                    r, g, b = px[x, y]
                    if r + g + b < 90:          # restos del borde negro del render (abajo a la derecha): hielo
                        r, g, b = 248, 253, 254
                    if is_water(r, g, b):
                        water += 1
                    else:
                        land[0] += r
                        land[1] += g
                        land[2] += b
                        n += 1
            if water >= n:
                grid[cy][cx] = "w"
                continue
            r, g, b = land[0] / n, land[1] / n, land[2] / n
            if r + g + b > 600:
                grid[cy][cx] = "i"
            elif g > r * 1.02 and g > b:
                grid[cy][cx] = "f"
            else:
                grid[cy][cx] = "l"
    return grid


def build():
    grid = classify()
    img = Image.new("RGB", (SIZE, SIZE), PAPER)
    put = img.putpixel

    def kind(x, y):
        if 0 <= x < WW and 0 <= y < WH:
            return grid[y][x]
        return "w"

    for y in range(WH):
        for x in range(WW):
            k = grid[y][x]
            X, Y = WX + x, WY + y
            if k == "w":
                # lineas de agua: un anillo punteado a dos pixeles de la costa
                near = any(kind(x + dx, y + dy) != "w" for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)))
                touching = any(kind(x + dx, y + dy) != "w" for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if near and not touching and (x + y) % 2 == 0:
                    put((X, Y), PAPER_D)
                continue
            coast = any(kind(x + dx, y + dy) == "w" for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if coast:
                put((X, Y), INK_SOFT)
            elif k == "i":
                put((X, Y), ICE)
            elif k == "f":
                put((X, Y), OLIVE)
            else:
                put((X, Y), PAPER_D)

    # arriba: cinta de cuero cosido con las puntas en cola de golondrina (en vez de un titulo)
    rx0, rx1, ry0, ry1 = 30, SIZE - 31, 13, 21
    for y in range(ry0, ry1 + 1):
        for x in range(rx0, rx1 + 1):
            put((x, y), LEATHER_D if y in (ry0, ry1) else LEATHER)
        if ry0 < y < ry1:
            put((rx0, y), LEATHER_D)
            put((rx1, y), LEATHER_D)
    for x in range(rx0 + 3, rx1 - 2):
        if x % 3 != 0:
            put((x, ry0 + 2), STITCH)
            put((x, ry1 - 2), STITCH)
    for side, x0 in ((-1, rx0 - 1), (1, rx1 + 1)):      # puntas plegadas por detras
        for i in range(7):
            x = x0 + side * i
            top, bottom = ry0 + 2, ry1 + 2
            notch = (ry0 + ry1) // 2 + 2
            for y in range(top, bottom + 1):
                if i >= 4 and abs(y - notch) <= i - 4:
                    continue                                  # corte en V de la punta
                put((x, y), LEATHER_D if y in (top, bottom) or i == 6 else (74, 49, 29) if i < 2 else LEATHER)

    # abajo: un rastro de huellas que cruza la hoja (izquierda, derecha, izquierda...) y la rosa de los vientos
    foot = [(-1, 0), (1, 0), (0, 0), (-2, -1), (0, -2), (2, -1), (0, 1)]
    for i, x in enumerate(range(14, SIZE - 34, 9)):
        y = SIZE - 20 + (3 if i % 2 else 0)
        for dx, dy in foot:
            put((x + dx, y + dy), INK_SOFT)
    cx, cy = SIZE - 20, SIZE - 19
    for d in range(-5, 6):
        put((cx + d, cy), INK_SOFT)
        put((cx, cy + d), INK_SOFT)
    for d in (-2, -1, 1, 2):
        put((cx + d, cy + d), INK_SOFT)
        put((cx + d, cy - d), INK_SOFT)
    for dy in range(-5, -1):                                 # el norte, en tinta oscura
        put((cx, cy + dy), INK)
    put((cx - 1, cy - 4), INK)
    put((cx + 1, cy - 4), INK)

    # tapas de cuero con costura (como el borde de la Dinopedia)
    for y in range(SIZE):
        for x in range(SIZE):
            edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            if edge == 0:
                put((x, y), LEATHER_D)
            elif edge < 4:
                put((x, y), LEATHER)
            if edge == 2 and ((x + y) // 2) % 2 == 0:
                put((x, y), STITCH)
            if edge == 4:
                put((x, y), PAPER_D)
    return img


def main():
    img = build()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    if os.path.exists(OUT) and Image.open(OUT).convert("RGB").tobytes() == img.tobytes():
        print("sin cambios", OUT)
    else:
        img.save(OUT, optimize=True)
        print("escrito", OUT)
    if len(sys.argv) > 1:
        img.resize((SIZE * 6, SIZE * 6), Image.NEAREST).save(os.path.join(sys.argv[1], "tierra_preview.png"))


if __name__ == "__main__":
    main()

"""Mapa del Paleontologo: la imagen de la Tierra que se dibuja en el item mapa (128 x 128, como un mapa de Minecraft).

Fuente: generator/mapa/tierra.webp, un render del mapa del server (equirectangular: de 180 O a 180 E y de 90 N a 90 S,
con 4 px de borde negro a cada lado). El agua (azules y las lineas celestes de las rutas) queda de un solo color; la
tierra conserva su color promedio. El mundo ocupa 128 x 64 en el medio; arriba y abajo quedan bandas para el titulo.
Las regiones de fosiles NO se dibujan aca: las pinta el plugin desde el config (asi siguen al config si cambia).

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

BORDER_X = 4                 # columnas negras a cada lado del render
SIZE = 128                   # un mapa de Minecraft
WORLD_H = SIZE // 2          # la Tierra es 2:1
TOP = (SIZE - WORLD_H) // 2  # 32: banda de arriba
WATER = (45, 45, 180)        # el "agua" de la paleta de los mapas de Minecraft
BAND = (213, 201, 140)       # pergamino (arena de la paleta)
ICE = (248, 253, 254)


def is_water(r, g, b):
    """Azules (oceano, costas y lineas de rutas) pero no el hielo blanco ni la tierra."""
    return b > g + 4 and b > r + 15 and r + g + b < 600


def build():
    src = Image.open(SOURCE).convert("RGB")
    w, h = src.size
    src = src.crop((BORDER_X, 0, w - BORDER_X, h))
    w, h = src.size
    px = src.load()
    out = Image.new("RGB", (SIZE, SIZE), BAND)
    dst = out.load()
    for cy in range(WORLD_H):
        y0, y1 = cy * h // WORLD_H, max(cy * h // WORLD_H + 1, (cy + 1) * h // WORLD_H)
        for cx in range(SIZE):
            x0, x1 = cx * w // SIZE, max(cx * w // SIZE + 1, (cx + 1) * w // SIZE)
            water = 0
            land = [0, 0, 0, 0]
            for y in range(y0, y1):
                for x in range(x0, x1):
                    r, g, b = px[x, y]
                    if r + g + b < 90:          # restos del borde negro del render (abajo a la derecha): hielo
                        r, g, b = ICE
                    if is_water(r, g, b):
                        water += 1
                    else:
                        land[0] += r
                        land[1] += g
                        land[2] += b
                        land[3] += 1
            if water >= land[3]:
                dst[cx, TOP + cy] = WATER
            else:
                n = land[3]
                dst[cx, TOP + cy] = (land[0] // n, land[1] // n, land[2] // n)
    return out


def main():
    img = build()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    data = None
    if os.path.exists(OUT):
        old = Image.open(OUT).convert("RGB")
        if old.tobytes() == img.tobytes():
            data = "igual"
    if data is None:
        img.save(OUT, optimize=True)
        print("escrito", OUT)
    else:
        print("sin cambios", OUT)
    if len(sys.argv) > 1:
        img.resize((SIZE * 6, SIZE * 6), Image.NEAREST).save(os.path.join(sys.argv[1], "tierra_preview.png"))


if __name__ == "__main__":
    main()

"""
Fondo del menu "Guia Dino" (DeluxeMenus, menus/guiadino.yml del server, no del plugin).

DeluxeMenus solo arma el titulo como texto con colores, sin elegir fuente: por eso este fondo va en la fuente
minecraft:default con tres letras del area privada de Unicode (las de la Dinopedia van en dinoearth:gui_img, que
el plugin elige con Adventure). El titulo del menu tiene que ser exactamente:

    "&f\\uEA00\\uEA01\\uEA02&8Guía Dino"

  \\uEA00  espacio de -8: el lapiz va del borde del titulo (x = 8) al borde del menu (x = 0)
  \\uEA01  el fondo de 176 x 222 (avanza 177), en blanco (&f) para que no lo tiña el gris del titulo
  \\uEA02  espacio de -169: vuelve a x = 8 para escribir el titulo encima, sobre la hoja

Los items van en los casilleros dibujados (SLOTS) y el de cerrar en el 49. Mismo estilo que la Dinopedia
(gui.py): tapas de cuero, hoja de papel y un rastro de huellas que une los temas en orden.
"""
import json

from build import png_bytes
from gui import (H, INK, TITLE_Y, W, Img, cover, footprint, inventory_area, page, rule, slot_frame)

NEG_IN, BG, NEG_OUT = "", "", ""

# temas en orden: fila 1 de izquierda a derecha y fila 3 de derecha a izquierda (el rastro baja por la derecha)
SLOTS = [(1, 1), (3, 1), (5, 1), (7, 1), (7, 3), (5, 3), (3, 3), (1, 3)]
CLOSE = (4, 5)


def trail(img, points):
    for i, (x, y) in enumerate(points):
        footprint(img, x, y, i % 2 == 1, 0.3)


def bg_guide():
    img = Img(W, H)
    cover(img)
    page(img, 5, 4, W - 6, 124)
    rule(img, 8, W - 9, 15)
    rule(img, 8, W - 9, 17, dashed=True)
    for col, row in SLOTS:
        slot_frame(img, col, row)
    slot_frame(img, *CLOSE)
    # huellas entre un tema y el siguiente
    steps = []
    for a, b in zip(SLOTS, SLOTS[1:]):
        (c0, r0), (c1, r1) = a, b
        if r0 == r1:      # mismo renglon: dos huellas en el hueco entre los casilleros
            x0 = 8 + 18 * min(c0, c1) + 17
            steps += [(x0 + 5, 18 + 18 * r0 + 6), (x0 + 13, 18 + 18 * r0 + 11)]
        else:             # bajada por la derecha
            x = 8 + 18 * c0 + 5
            steps += [(x, 18 + 18 * 2 + 3), (x + 6, 18 + 18 * 2 + 10)]
    trail(img, steps)
    # rastro que se pierde abajo, como en el indice (sin pisar el boton de cerrar)
    for i in range(9):
        if i in (3, 4, 5):
            continue
        footprint(img, 16 + i * 18, 114 - (i % 2) * 5 - i // 3, i % 2 == 1)
    inventory_area(img)
    return img


def build_guide(files):
    img = bg_guide()
    files["assets/minecraft/textures/font/dinoearth_guia.png"] = png_bytes(img.px)
    font = {"providers": [
        {"type": "space", "advances": {NEG_IN: -8, NEG_OUT: -(W + 1 - 8)}},
        # borde de arriba de la letra = y del titulo + 7 - ascent -> ascent = 13 la pone en y = 0
        {"type": "bitmap", "file": "minecraft:font/dinoearth_guia.png", "ascent": TITLE_Y + 7, "height": H,
         "chars": [BG]},
    ]}
    files["assets/minecraft/font/default.json"] = json.dumps(font, ensure_ascii=False, indent=1).encode()
    return img

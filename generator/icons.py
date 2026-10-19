"""Iconos de la Dinopedia: cada color de cada especie renderizado con el mismo motor de preview.py
(fondo transparente, 32x32), una silueta gris para las especies sin descubrir y el libro.
Lo llama build.py despues de armar todos los modelos.
"""
import json

from build import NS, png_bytes
from preview import dino, mat_t, render

SIZE = 32   # en el menu se ven a 16 px x la escala de la interfaz: 32 alcanza y ocupa 1/4 que 64
CANVAS = 300
# pose y angulo de camara (yaw, pitch) de cada especie; el resto usa la de reposo
POSE = {"pteranodon": ("vuelo", 120, 28), "dimorphodon": ("vuelo", 120, 28), "mosasaurus": ("nadar", 125, 22)}
SILHOUETTE = (46, 50, 58)


def icon(sets, key, scale, pose, yaw, pitch):
    return shrink(raw_render(sets, key, scale, pose, yaw, pitch))


def raw_render(sets, key, scale, pose, yaw, pitch):
    """Render grande con fondo transparente (None): sirve para el icono y para la foto de la Dinopedia."""
    rig = sets[key][0]
    cubes = [c for b in rig.bones for c in b.cubes]
    radius = max(max(abs(c.frm[i]), abs(c.to[i])) for c in cubes for i in (0, 2))
    height = max(c.to[1] for c in cubes)
    ppx = CANVAS * 0.8 / (max(2 * radius, height) * scale)
    return render(dino(sets, key, pose, root=mat_t(0, -height / 2, 0), k=scale), yaw, pitch, ppx=ppx, cy_frac=0.5,
                  canvas=(CANVAS, CANVAS), bg=None)


def shrink(img):
    """Recorta lo dibujado, lo centra en un cuadrado y lo achica a SIZE x SIZE (pixel art, sin bordes borrosos)."""
    ys = [y for y, row in enumerate(img) if any(p is not None for p in row)]
    xs = [x for x in range(len(img[0])) if any(row[x] is not None for row in img)]
    x0, x1, y0, y1 = min(xs), max(xs) + 1, min(ys), max(ys) + 1
    side = max(x1 - x0, y1 - y0) * 1.04
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    out = []
    for j in range(SIZE):
        row = []
        for i in range(SIZE):
            sx0, sx1 = cx - side / 2 + side * i / SIZE, cx - side / 2 + side * (i + 1) / SIZE
            sy0, sy1 = cy - side / 2 + side * j / SIZE, cy - side / 2 + side * (j + 1) / SIZE
            total, n, acc = 0, 0, [0, 0, 0]
            for y in range(int(sy0), int(sy1) + 1):
                for x in range(int(sx0), int(sx1) + 1):
                    if not (0 <= y < len(img) and 0 <= x < len(img[0])):
                        continue
                    total += 1
                    p = img[y][x]
                    if p is not None:
                        n += 1
                        for c in range(3):
                            acc[c] += p[c]
            if total and n / total >= 0.4:
                row.append((acc[0] // n, acc[1] // n, acc[2] // n, 255))
            else:
                row.append((0, 0, 0, 0))
        out.append(row)
    return out


def silhouette(pix):
    out = []
    for row in pix:
        out.append([(0, 0, 0, 0) if p[3] == 0 else
                    tuple(int(SILHOUETTE[c] + (p[0] + p[1] + p[2]) / 3 * 0.12) for c in range(3)) + (255,)
                    for p in row])
    return out


def book():
    """Dinopedia: libro de cuero cosido con esquinas doradas y una piedra de ambar (con su mosquito) en la tapa (16x16)."""
    T = (0, 0, 0, 0)
    pal = {
        ".": T,
        "D": (58, 33, 18), "L": (112, 66, 36), "l": (134, 82, 46), "S": (84, 48, 26),   # cuero, borde y lomo
        "s": (186, 146, 92),                                                               # costura
        "P": (234, 224, 196), "p": (204, 190, 156),                                        # hojas
        "G": (232, 188, 76), "g": (168, 124, 40),                                          # dorado
        "A": (226, 136, 28), "a": (176, 88, 18), "B": (252, 196, 80), "W": (255, 240, 184),  # ambar
        "m": (70, 34, 12),                                                                 # mosquito
    }
    rows = [
        "................",
        "..DDDDDDDDDDDD..",
        "..GgLLLLLLLLgGP.",
        "..gSsLsLsLsLLgP.",
        "..DSLLLLLLLLsLp.",
        "..DSsLLggggLLLP.",
        "..DSLLgaBWAgsLp.",
        "..DSsLgAAmAgLLP.",
        "..DSLLgaAAagsLp.",
        "..DSsLLgaagLLLP.",
        "..DSLLLLggLLsLp.",
        "..DSsLLLLLLLLLP.",
        "..gSLsLsLsLsLgp.",
        "..GgLLLLLLLLgGp.",
        "..DDDDDDDDDDDD..",
        "................",
    ]
    return [[pal[c] if len(pal[c]) == 4 else pal[c] + (255,) for c in row] for row in rows]


def put(files, path, pix):
    files[f"assets/{NS}/textures/item/{path}.png"] = png_bytes(pix)
    files[f"assets/{NS}/models/item/{path}.json"] = json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{path}"}}).encode()
    files[f"assets/{NS}/items/{path}.json"] = json.dumps(
        {"model": {"type": "minecraft:model", "model": f"{NS}:item/{path}"}}).encode()


def build_icons(species, sets, files):
    """Devuelve los renders crudos {(especie, color): imagen} para las fotos de la Dinopedia (gui.py)."""
    raws = {}
    for sp in species:
        sid = sp["id"]
        pose, yaw, pitch = POSE.get(sp.get("plus_of", sid), ("reposo", 125, 15))
        first = None
        for vid in sp["variants"]:
            raw = raw_render(sets, f"{sid}/{vid}/male", sp["scale"], pose, yaw, pitch)
            raws[(sid, vid)] = raw
            pix = shrink(raw)
            put(files, f"icon/{sid}/{vid}", pix)
            first = first or pix
        put(files, f"icon/{sid}/unknown", silhouette(first))
    put(files, "dinopedia", book())
    return raws

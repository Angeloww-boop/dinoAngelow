"""
Genera el resource pack de todos los dinosaurios (modelos + texturas de cada
variante, montura y huevo) y el rig que usa el plugin. Los sonidos los genera sounds.py.

Uso:  python generator/build.py
"""
import json
import math
import os
import shutil
import struct
import zipfile
import zlib

import feeder
import nest
import geometry as G
import species_ankylosaurus
import species_carnotaurus
import species_compsognathus
import species_dilophosaurus
import species_dimorphodon
import species_gallimimus
import species_giganotosaurus
import species_mosasaurus
import species_parasaurolophus
import species_pteranodon
import species_raptor
import species_spinosaurus
import species_stegosaurus
import species_trex
import species_triceratops

ALL_SPECIES = [species_raptor.SPECIES, species_triceratops.SPECIES,
               species_dilophosaurus.SPECIES, species_compsognathus.SPECIES,
               species_trex.SPECIES, species_pteranodon.SPECIES,
               species_parasaurolophus.SPECIES, species_gallimimus.SPECIES,
               species_stegosaurus.SPECIES, species_ankylosaurus.SPECIES,
               species_giganotosaurus.SPECIES, species_carnotaurus.SPECIES,
               species_dimorphodon.SPECIES, species_spinosaurus.SPECIES, species_mosasaurus.SPECIES]

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK_DIR = os.path.join(ROOT, "resourcepack")
PACK_ZIP = os.path.join(ROOT, "DinoEarth-ResourcePack.zip")
RIG_DIR = os.path.join(ROOT, "plugin", "src", "main", "resources", "rigs")
BUNDLED_ZIP = os.path.join(ROOT, "plugin", "src", "main", "resources", "pack", "DinoEarth-ResourcePack.zip")
NS = "dinoearth"

TEETH = (240, 234, 214)
MOUTH = (122, 40, 44)
MOUTH_DARK = (74, 22, 26)
CLAW = (46, 40, 36)
WHITE = (250, 250, 242)
IVORY = (228, 216, 184)
HORN_TIP = (92, 80, 62)
BEAK = (64, 56, 48)


# ---------------------------------------------------------------- utilidades
def hash3(x, y, z, seed=0):
    h = (int(x) * 374761393 + int(y) * 668265263 + int(z) * 2147483647 + seed * 974634) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def scale(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


def smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def rotate(p, axis, ang, origin):
    """Rotacion regla de la mano derecha (igual que Minecraft)."""
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + origin[0], y + origin[1], z + origin[2])


# local <-> modelo. El item_display gira el modelo 180 grados en Y, asi que
# invertimos X y Z alrededor del joint y centramos el joint en (8, 8, 8).
def to_model(p, joint):
    return (8 - (p[0] - joint[0]), 8 + (p[1] - joint[1]), 8 - (p[2] - joint[2]))


def to_local(m, joint):
    return (joint[0] - (m[0] - 8), joint[1] + (m[1] - 8), joint[2] - (m[2] - 8))


def model_cube(cube, joint, grown=True):
    """Cubo en coordenadas de modelo. grown=False: sin lo que agrando defight (para la textura)."""
    lo, hi = cube.bounds() if grown else (cube.frm, cube.to)
    a = to_model(lo, joint)
    b = to_model(hi, joint)
    # lo agrandado por defight no puede pasarse del limite de Minecraft (-16..32)
    frm = tuple(max(-16.0, min(a[i], b[i])) if grown else min(a[i], b[i]) for i in range(3))
    to = tuple(min(32.0, max(a[i], b[i])) if grown else max(a[i], b[i]) for i in range(3))
    rot = None
    if cube.rot:
        axis, ang, o = cube.rot
        rot = (axis, ang if axis == "y" else -ang, to_model(o, joint))
    return frm, to, rot


# Caras en coordenadas de modelo con la convencion de UV de Minecraft.
def faces_of(f, t):
    dx, dy, dz = t[0] - f[0], t[1] - f[1], t[2] - f[2]
    return [
        ("north", (0, 0, -1), dx, dy, lambda u, v: (t[0] - u, t[1] - v, f[2])),
        ("south", (0, 0, 1), dx, dy, lambda u, v: (f[0] + u, t[1] - v, t[2])),
        ("west", (-1, 0, 0), dz, dy, lambda u, v: (f[0], t[1] - v, f[2] + u)),
        ("east", (1, 0, 0), dz, dy, lambda u, v: (t[0], t[1] - v, t[2] - u)),
        ("up", (0, 1, 0), dx, dz, lambda u, v: (f[0] + u, t[1], f[2] + v)),
        ("down", (0, -1, 0), dx, dz, lambda u, v: (f[0] + u, f[1], t[2] - v)),
    ]


# ---------------------------------------------------------------- pintura
class Ctx:
    def __init__(self, pal=None, face=None, male=False, pattern="stripes", cracked=False, egg=None, saddle=None,
                 food=None):
        self.pal = pal
        self.face = face or {}
        self.male = male
        self.pattern = pattern
        self.cracked = cracked
        self.egg = egg
        self.saddle = saddle or G.SADDLE_COLORS
        self.food = food


def rel(cube, p0, axis):
    """Posicion 0..1 de p0 dentro del cubo sobre un eje (sin rotar)."""
    return (p0[axis] - cube.frm[axis]) / max(0.001, cube.to[axis] - cube.frm[axis])


def border(cube, p0, n):
    """Distancia (px) de p0 al borde mas cercano de la cara en la que esta."""
    ax = max(range(3), key=lambda i: abs(n[i]))
    return min(min(p0[i] - cube.frm[i], cube.to[i] - p0[i]) for i in range(3) if i != ax)


def paint_saddle(ctx, cube, n, p0, edge, noise):
    """Materiales de las monturas (la paleta es de cada especie)."""
    pal, tag = ctx.saddle, cube.tag
    x, y, z = p0
    b = border(cube, p0, n)
    col = pal[tag]
    if tag == "saddle":
        if n[1] > 0.5 and (edge < 0.6 or rel(cube, p0, 2) < 0.06 or rel(cube, p0, 2) > 0.94):
            col = scale(col, 1.25)  # costura del borde
    elif tag == "plate":
        # placa de metal: bisel oscuro, remaches en las esquinas, brillo arriba
        col = scale(col, 1.18 if n[1] > 0.5 else 0.92)
        if b < 0.5:
            col = scale(pal["plate"], 0.7)
        elif b < 1.25 and hash3(math.floor(x), math.floor(y), math.floor(z), 41) > 0.55:
            col = scale(pal["metal"], 1.1)
        return scale(col, 0.97 + 0.06 * hash3(math.floor(x * 2), math.floor(y * 2), math.floor(z * 2), 42))
    elif tag == "spike":
        axis = {"x": 0, "y": 1, "z": 2}[cube.tip or "y"]
        return scale(mix(col, HORN_TIP, smooth(0.45, 1.0, rel(cube, p0, axis))), noise)
    elif tag == "cloth":
        if b < 1.0:
            col = pal["trim"]                       # guarda
        elif b < 1.5:
            col = scale(col, 0.75)
        elif (math.floor(x) + math.floor(y) + math.floor(z)) % 4 == 0:
            col = scale(col, 0.88)                  # trama
        return scale(col, 0.96 + 0.06 * hash3(math.floor(x * 2), math.floor(y * 2), math.floor(z * 2), 43))
    elif tag == "wood":
        long_axis = max(range(3), key=lambda i: cube.to[i] - cube.frm[i])
        across = [p0[i] for i in range(3) if i != long_axis][0]
        if math.sin(across * 2.4 + 2 * hash3(0, 0, math.floor(p0[long_axis] / 3), 44)) > 0.6:
            col = scale(col, 0.82)                  # veta
        if b < 0.4:
            col = scale(col, 0.75)
    elif tag == "rope":
        col = scale(col, 0.8 if (math.floor(x * 2) + math.floor(y * 2) + math.floor(z * 2)) % 3 == 0 else 1.05)
    return scale(col, noise)


def paint(ctx, bone, cube, n, p, p0, edge):
    """Color de un texel. n = normal local, p = punto local (px) ya rotado,
    p0 = el mismo punto sin la rotacion del cubo, edge = distancia al borde lateral."""
    x, y, z = p
    noise = 0.93 + 0.12 * hash3(math.floor(x * 2), math.floor(y * 2), math.floor(z * 2), 7)
    tag = cube.tag

    if tag in G.SADDLE_TAGS:
        return paint_saddle(ctx, cube, n, p0, edge, noise)
    if tag == "food":
        return scale(paint_food(ctx.food, p), noise)
    if tag == "egg":
        return paint_egg(ctx, p)
    if tag == "claw":
        return scale(CLAW, 1.0 + 0.25 * hash3(x, y, z, 3))
    if tag == "beak":
        beak = ctx.pal.get("beak", BEAK) if ctx.pal else BEAK
        return scale(beak, noise * (1.15 if n[1] > 0.5 else 1.0))
    if tag == "horn":
        axis = {"x": 0, "y": 1, "z": 2}[cube.tip or "y"]
        if axis == 0:   # cuernos laterales: la punta es la cara mas alejada del centro
            near, far = sorted((abs(cube.frm[0]), abs(cube.to[0])))
            t = (abs(p0[0]) - near) / max(0.001, far - near)
        else:
            t = rel(cube, p0, axis)
        return scale(mix(IVORY, HORN_TIP, smooth(0.5, 1.0, t)), noise)

    pal, face = ctx.pal, ctx.face
    accent2 = pal.get("accent2", pal["accent"])
    if tag == "dark":
        return scale(pal["dark"], noise * 0.9)
    if tag == "accent":
        return scale(pal["accent"], (0.8 + 0.4 * rel(cube, p0, 1)) * noise)
    if tag == "crest":
        return scale(mix(pal["accent"], accent2, smooth(0.4, 1.0, rel(cube, p0, 1))), noise)
    if tag == "frill":
        return scale(paint_frill(ctx, cube, n, p0, accent2), noise)
    if tag == "membrane":
        return scale(paint_membrane(ctx, n, p0), noise)
    if tag == "fan":
        if abs(x) > 9.5 or abs(n[0]) > 0.5 or abs(n[1]) > 0.5:
            return scale(pal["dark"], noise)
        col = pal["accent"] if math.floor((y - 26) / 2.5) % 2 == 0 else accent2
        return scale(mix(col, pal["dark"], 0.25 * smooth(6, 9.5, abs(x))), noise)

    # interior de la boca (+ dientes en los bordes)
    if cube.mouth == "down" and n[1] < -0.5 or cube.mouth == "up" and n[1] > 0.5:
        if face.get("mouth_teeth") and edge < 0.8 and z > face["teeth_z"]:
            phase = 0 if cube.mouth == "down" else 1
            return TEETH if (math.floor(z) + phase) % 2 == 0 else MOUTH_DARK
        return scale(MOUTH, noise) if abs(x) < 1.0 else scale(MOUTH_DARK, noise)

    # contrasombra: panza clara, lomo oscuro
    t = rel(cube, p0, 1)
    if n[1] < -0.5:
        col = pal["belly"]
    elif n[1] > 0.5:
        col = pal["base"]
    else:
        col = mix(pal["belly"], pal["base"], (t - 0.15) / 0.35)

    upper = n[1] > 0.5 or (abs(n[1]) <= 0.5 and t > 0.5)
    stripe = mix(pal["stripe"], pal["accent"], 0.3) if ctx.male else pal["stripe"]
    coord = z if cube.stripes == "z" else y

    if ctx.pattern == "stripes":
        wob = 1.6 * hash3(math.floor(x / 2), 0, math.floor(coord / 3), 11)
        if upper and math.sin(coord * 0.85 + wob) > 0.45:
            col = mix(col, stripe, 0.85)
    elif ctx.pattern == "bands":
        wob = 1.2 * hash3(math.floor(x / 3), 0, math.floor(coord / 5), 13)
        if upper and math.sin(coord * 0.36 + wob) > 0.5:
            col = mix(col, stripe, 0.6)
        # arrugas / escamas grandes
        col = scale(col, 0.92 + 0.12 * hash3(math.floor(x / 2), math.floor(y / 2), math.floor(z / 2), 17))
    elif ctx.pattern == "spots":
        if upper and hash3(math.floor(x / 1.5), math.floor(y / 1.5), math.floor(z / 1.5), 5) > 0.78:
            col = mix(col, stripe, 0.75)

    # linea dorsal (de color acento en el macho)
    if n[1] > 0.5 and abs(x) < 1.0:
        col = mix(col, pal["accent"], 0.6) if ctx.male else mix(col, pal["dark"], 0.5)

    col = scale(col, noise)

    if bone.name == "head":
        col = paint_face(ctx, cube, n, p, col)
    elif bone.name == "jaw" and face.get("jaw_teeth_y") is not None:
        if abs(n[0]) > 0.5 and y >= face["jaw_teeth_y"] and z > face["jaw_teeth_z"]:
            col = TEETH if math.floor(z) % 2 == 1 else col
    return col


def paint_frill(ctx, cube, n, p0, accent2):
    """Gola del triceratops: frente colorido (con 'ojos' en el macho), dorso de piel."""
    pal = ctx.pal
    cx = (cube.frm[0] + cube.to[0]) / 2
    cy = (cube.frm[1] + cube.to[1]) / 2
    hx = (cube.to[0] - cube.frm[0]) / 2
    hy = (cube.to[1] - cube.frm[1]) / 2
    u = (p0[0] - cx) / hx
    v = (p0[1] - cy) / hy
    d = math.sqrt(u * u + v * v * 0.8)
    if abs(n[2]) < 0.5:            # canto
        return pal["dark"]
    if n[2] < 0:                   # dorso
        return mix(pal["base"], pal["stripe"], 0.6 if d > 0.8 else 0.3)
    if d > 0.88:
        return mix(pal["dark"], pal["stripe"], 0.3)
    if d > 0.62:                   # borde de color
        return pal["accent"]
    col = mix(pal["base"], accent2, 0.3 + 0.25 * d)
    if ctx.male:
        # rayos que salen desde la base de la gola
        ang = math.atan2(u, v + 1.1)
        if math.sin(ang * 9) > 0.35:
            col = mix(col, pal["accent"], 0.75)
    return col


def paint_food(food, p):
    """Comida del comedero: carne marmolada, heno con pasto, pescado con lomo oscuro."""
    x, y, z = p
    base, alt = food["base"], food["alt"]
    if food["kind"] == "meat":
        return alt if math.sin(x * 1.3 + z * 0.7 + y) > 0.72 else base
    if food["kind"] == "plants":
        col = base if math.floor(x * 1.5 + hash3(0, 0, math.floor(z), 51) * 2) % 2 == 0 else scale(base, 0.82)
        return alt if hash3(math.floor(x), math.floor(y * 2), math.floor(z), 52) > 0.8 else col
    col = mix(base, alt, 0.7) if y > 3 and (math.floor(x) + math.floor(z)) % 3 == 0 else base
    return scale(col, 1.1) if hash3(math.floor(x * 2), math.floor(y * 2), math.floor(z * 2), 53) > 0.85 else col


def paint_membrane(ctx, n, p0):
    """Membrana del ala: mas oscura hacia la punta y el borde de atras, con fibras a lo largo."""
    pal = ctx.pal
    x, y, z = p0
    wing = pal.get("wing", pal["base"])
    tip = pal.get("wing_tip", pal["dark"])
    if n[1] < -0.5:
        wing = mix(wing, pal["belly"], 0.45)       # de abajo es mas clara
    col = mix(wing, tip, smooth(30, 46, abs(x)))
    col = mix(col, tip, 0.35 * smooth(-1, -8, z))
    if math.sin(abs(x) * 0.9 + z * 1.6) > 0.8:
        col = scale(col, 0.9)                       # fibras
    return col


def inside(box, a, b):
    return box is not None and box[0] <= a < box[1] and box[2] <= b < box[3]


def paint_face(ctx, cube, n, p, col):
    x, y, z = p
    pal, face = ctx.pal, ctx.face
    if abs(n[0]) > 0.5 and cube.mouth == "down":
        if inside(face.get("highlight"), z, y):
            return WHITE
        if inside(face.get("eye"), z, y):
            return pal["pupil"] if inside(face["pupil"], z, y) else pal["iris"]
        if inside(face.get("eye_stripe"), z, y):
            return scale(pal["dark"], 0.85)
        if inside(face.get("brow"), z, y):
            return scale(pal["dark"], 0.9)
        ty = face.get("teeth_y")
        if ty and ty[0] <= y < ty[1] and z > face["teeth_z"]:
            return TEETH if math.floor(z) % 2 == 0 else MOUTH_DARK
    nos = face.get("nostril")
    if nos and n[2] > 0.5 and cube.mouth == "down" and z > nos[0] and nos[1] <= y < nos[2] and 0.75 <= abs(x) < 1.75:
        return scale(pal["dark"], 0.6)
    return col


def paint_egg(ctx, p):
    x, y, z = p
    c = ctx.egg
    col = c["shell"]
    if hash3(math.floor(x * 1.5), math.floor(y * 1.5), math.floor(z * 1.5), 21) > 0.82:
        col = mix(col, c["spot"], 0.75)
    col = scale(col, 0.95 + 0.08 * hash3(math.floor(x * 2), math.floor(y * 2), math.floor(z * 2), 22))
    if ctx.cracked:
        k = c.get("k", 1.0)
        ang = math.atan2(x, z)
        tri = abs(((ang * 6 / math.pi) % 2) - 1)
        yc = (6.0 + 1.6 * tri) * k
        if abs(y - yc) < 0.55:
            return c["crack"]
        for a0 in (0.6, 2.4, -1.5):
            if abs(ang - a0) < 0.16 and yc < y < yc + 2.5 * k:
                return c["crack"]
    return col


# ---------------------------------------------------------------- atlas
class Atlas:
    def __init__(self):
        self.rects = []

    def add(self, w, h, painter):
        self.rects.append([w, h, painter, 0, 0])
        return self.rects[-1]

    def pack(self, width, pad=1):
        """Estantes de ancho fijo. Devuelve el alto que ocupa, o None si alguna cara no entra a lo ancho."""
        x = y = row_h = 0
        for r in sorted(self.rects, key=lambda r: (-r[1], -r[0])):
            w, h = r[0] + pad * 2, r[1] + pad * 2
            if w > width:
                return None
            if x + w > width:
                x, y, row_h = 0, y + row_h, 0
            r[3], r[4] = x + pad, y + pad
            x += w
            row_h = max(row_h, h)
        return y + row_h

    def pack_tight(self):
        """Elige el rectangulo mas chico (lados multiplos de 16) donde entran todas las caras.
        Antes eran cuadrados potencia de 2 y el ~60 % quedaba vacio: todo eso es memoria de video
        que ocupa el atlas de texturas del cliente sin dibujar nada."""
        best = None
        for width in range(32, 1025, 16):
            height = self.pack(width)
            if height is None:
                continue
            height = max(16, -(-height // 16) * 16)
            key = (width * height, abs(width - height))   # menos area; si empata, mas cuadrado
            if best is None or key < best[0]:
                best = (key, width, height)
        _, width, height = best
        self.pack(width)
        return width, height


def build_set(rig, ctx, tex_id, prefix, files, display=None):
    """Arma textura + modelos de un rig. Devuelve (rig, pix, size, {hueso: elementos})."""
    G.defight(rig.bones)
    atlas = Atlas()
    face_refs = []
    models = {}
    for bone in rig.bones:
        elements = []
        for cube in bone.cubes:
            f, t, rot = model_cube(cube, bone.joint)
            f0, t0, _ = model_cube(cube, bone.joint, grown=False)
            if min(f) < -16 or max(t) > 32:
                raise ValueError(f"{prefix}/{bone.name}: cubo {cube.frm}->{cube.to} a mas de 24 px del joint")
            el = {"from": [round(v, 4) for v in f], "to": [round(v, 4) for v in t], "faces": {}}
            if rot:
                el["rotation"] = {"angle": rot[1], "axis": rot[0], "origin": [round(v, 4) for v in rot[2]]}
            for name, nrm, fw, fh, point in faces_of(f0, t0):
                W, H = max(1, math.ceil(fw - 1e-6)), max(1, math.ceil(fh - 1e-6))
                local_n = (-nrm[0], nrm[1], -nrm[2])

                def painter(i, j, fw=fw, fh=fh, W=W, H=H, point=point, cube=cube, bone=bone,
                            n=local_n, rot=rot, name=name):
                    u, v = (i + 0.5) / W * fw, (j + 0.5) / H * fh
                    m0 = point(u, v)
                    m = rotate(m0, rot[0], rot[1], rot[2]) if rot else m0
                    edge = min(u, fw - u) if name in ("up", "down") else 99
                    return paint(ctx, bone, cube, n, to_local(m, bone.joint), to_local(m0, bone.joint), edge)

                rect = atlas.add(W, H, painter)
                el["faces"][name] = {"texture": "#0"}
                face_refs.append((el["faces"][name], rect, W, H))
            elements.append(el)
        models[bone.name] = elements

    tw, th = atlas.pack_tight()
    size = (tw, th)

    # 1 texel de borde repetido alrededor de cada cara para que el mipmap no sangre
    pix = [[(0, 0, 0, 0)] * tw for _ in range(th)]
    for w, h, painter, rx, ry in atlas.rects:
        block = [[painter(i, j) for i in range(w)] for j in range(h)]
        for j in range(-1, h + 1):
            for i in range(-1, w + 1):
                c = block[min(max(j, 0), h - 1)][min(max(i, 0), w - 1)]
                yy, xx = ry + j, rx + i
                if 0 <= yy < th and 0 <= xx < tw:
                    pix[yy][xx] = (int(c[0]), int(c[1]), int(c[2]), 255)

    # las UV van de 0 a 16 en cada eje, sea cual sea el tamaño de la textura
    ku, kv = 16.0 / tw, 16.0 / th
    for face_, rect, W, H in face_refs:
        rx, ry = rect[3], rect[4]
        face_["uv"] = [round(rx * ku, 4), round(ry * kv, 4), round((rx + W) * ku, 4), round((ry + H) * kv, 4)]

    files[f"assets/{NS}/textures/item/{tex_id}.png"] = png_bytes(pix)
    for name, elements in models.items():
        model = {
            "credit": "DinoEarth - generado por generator/build.py",
            "texture_size": [tw, th],
            "textures": {"0": f"{NS}:item/{tex_id}", "particle": f"{NS}:item/{tex_id}"},
            "elements": elements,
        }
        if display:
            model["display"] = display
        files[f"assets/{NS}/models/item/{prefix}/{name}.json"] = json.dumps(model, separators=(",", ":")).encode()
        item_def = {"model": {"type": "minecraft:model", "model": f"{NS}:item/{prefix}/{name}"}}
        files[f"assets/{NS}/items/{prefix}/{name}.json"] = json.dumps(item_def).encode()
    return rig, pix, size, models


def egg_icon(c):
    """Icono 2D de 16x16 para el huevo en el inventario."""
    pix = [[(0, 0, 0, 0)] * 16 for _ in range(16)]

    def solid(x, y):
        dy = (y + 0.5 - 8.6) / 6.4
        rx = 4.6 * (1 - 0.18 * max(0.0, -dy))  # mas angosto arriba
        return (x + 0.5 - 8) ** 2 / rx ** 2 + dy * dy <= 1.0

    for y in range(16):
        for x in range(16):
            if not solid(x, y):
                continue
            edge = not all(solid(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            col = c["shell"]
            if hash3(x, y, 0, 31) > 0.8:
                col = mix(col, c["spot"], 0.8)
            col = scale(col, 1.12 - 0.025 * (x - 5) - 0.035 * (y - 4))
            if edge:
                col = scale(mix(c["shell"], c["spot"], 0.6), 0.7)
            pix[y][x] = (col[0], col[1], col[2], 255)
    pix[4][6] = pix[5][6] = (255, 252, 240, 255)  # brillo
    return pix


# ---------------------------------------------------------------- build
def build_species(sp, files):
    sid = sp["id"]
    sets = {}
    # la montura se apoya sobre el cuerpo: sus caras se separan junto con las del adulto
    saddle_rig = sp["saddle"]() if sp["saddle"] else None
    for vid, pal in sp["variants"].items():
        for model, make in sp["forms"].items():
            rig = make()
            if saddle_rig and model != "baby":
                G.defight(rig.bones + saddle_rig.bones)
            ctx = Ctx(pal, rig.face, male=model == "male", pattern=rig.pattern)
            sets[f"{vid}/{model}"] = build_set(rig, ctx, f"{sid}/{vid}_{model}", f"{sid}/{vid}/{model}", files)
    if sp["saddle"]:
        ctx = Ctx(saddle=G.saddle_palette(sp.get("saddle_colors")))
        sets["saddle"] = build_set(saddle_rig, ctx, f"{sid}/saddle", f"{sid}/saddle", files)
    if sp["egg"]:
        egg_rig = G.egg(sp["egg"]["k"])
        sets["egg"] = build_set(egg_rig, Ctx(egg=sp["egg"]), f"{sid}/egg", f"{sid}/egg", files)
        sets["egg_cracked"] = build_set(egg_rig, Ctx(cracked=True, egg=sp["egg"]), f"{sid}/egg_cracked",
                                        f"{sid}/egg_cracked", files)
        files[f"assets/{NS}/textures/item/{sid}/egg_icon.png"] = png_bytes(egg_icon(sp["egg"]))
        files[f"assets/{NS}/models/item/{sid}/egg_item.json"] = json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{sid}/egg_icon"}}).encode()
        files[f"assets/{NS}/items/{sid}/egg_item.json"] = json.dumps(
            {"model": {"type": "minecraft:model", "model": f"{NS}:item/{sid}/egg_item"}}).encode()
    write_rig(sp)
    return sets


def build_feeders(files):
    """Comederos: un modelo por dieta y nivel de comida. El lleno es tambien el item del inventario."""
    sets = {}
    for diet, food in feeder.DIETS.items():
        for level in range(feeder.LEVELS):
            name = f"{diet}_{level}"
            sets[f"feeder/{name}"] = build_set(feeder.rig(diet, level), Ctx(food=food), f"feeder/{name}",
                                               f"feeder/{name}", files, display=feeder.DISPLAY)
    return sets


def build_nest(files):
    """Nido salvaje (uno solo para todas las especies: los huevos los pone el plugin)."""
    return {"nest": build_set(nest.rig(), Ctx(saddle={**G.SADDLE_COLORS, **nest.PALETTE}), "nest", "nest", files)}


def build():
    files = {}
    all_sets = {}
    for sp in ALL_SPECIES:
        for key, s in build_species(sp, files).items():
            all_sets[f"{sp['id']}/{key}"] = s
    all_sets.update(build_feeders(files))
    all_sets.update(build_nest(files))
    import icons  # aca y no arriba: icons usa el render de preview.py, que importa este modulo
    raws = icons.build_icons(ALL_SPECIES, all_sets, files)
    import items  # drops, monturas, silbato y jaula (tambien escribe items.json para el plugin)
    items.build_items(ALL_SPECIES, all_sets, files)
    import gui  # menus con diseño de la Dinopedia (fondos, fotos, fuentes; escribe gui.json para el plugin)
    gui.build_gui(ALL_SPECIES, raws, files)

    files["pack.mcmeta"] = json.dumps({
        # 46 = 1.21.4 (primera version con items/ y item_model), 69 = 1.21.9, 75 = 1.21.11.
        # Rango amplio porque los jugadores pueden entrar con otra version (ViaVersion).
        "pack": {"description": "DinoEarth - dinosaurios", "pack_format": 46,
                 "supported_formats": [46, 99], "min_format": 46, "max_format": 99}
    }, indent=2).encode()

    # Solo se reescribe lo que cambio de verdad. Cada maquina comprime distinto (el zlib de Python en
    # Windows no es el de Linux), asi que un PNG con los mismos pixeles se deja como estaba: si no, el
    # hash del pack cambiaria al regenerar en otra compu sin haber cambiado nada.
    stale = set()
    for sub in ("models", "items", "textures", "font"):
        for base, _, names in os.walk(os.path.join(PACK_DIR, "assets", NS, sub)):
            stale.update(os.path.normpath(os.path.join(base, n)) for n in names)
    for rel_path, data in files.items():
        out = os.path.normpath(os.path.join(PACK_DIR, rel_path))
        stale.discard(out)
        write_if_changed(out, data)
    for old in stale:      # archivos de modelos que ya no existen
        os.remove(old)

    # zip determinista (orden y fechas fijas): el hash solo cambia si cambia el contenido,
    # asi los jugadores no vuelven a descargar el pack si no hace falta
    entries = []
    for base, _, names in os.walk(PACK_DIR):
        for name in names:
            full = os.path.join(base, name)
            with open(full, "rb") as fh:
                entries.append((os.path.relpath(full, PACK_DIR).replace(os.sep, "/"), fh.read()))
    entries.sort()
    if not zip_has(PACK_ZIP, entries):
        with zipfile.ZipFile(PACK_ZIP, "w", zipfile.ZIP_DEFLATED) as z:
            for arc, data in entries:
                info = zipfile.ZipInfo(arc, date_time=(2024, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                z.writestr(info, data)

    # una copia viaja adentro del jar: el plugin la usa para comprobar (o servir) el pack
    with open(PACK_ZIP, "rb") as fh:
        write_if_changed(BUNDLED_ZIP, fh.read())

    n_models = sum(1 for k in files if "/models/" in k)
    print(f"{len(ALL_SPECIES)} especies, {len(all_sets)} sets, {n_models} modelos -> {PACK_ZIP}")
    return all_sets


def write_rig(sp):
    sid = sp["id"]

    def form(rig):
        cubes = [c for b in rig.bones for c in b.cubes]
        return {"height": max(c.to[1] for c in cubes),
                # hasta donde llega el modelo en horizontal (para la caja de culling de las displays)
                "radius": max(max(abs(c.frm[i]), abs(c.to[i])) for c in cubes for i in (0, 2)),
                "bones": [{"name": b.name, "parent": b.parent, "joint": list(b.joint)} for b in rig.bones]}

    rig = {
        "species": sid,
        "scale": sp["scale"],
        "item": NS + ":" + sid + "/{variant}/{model}/{bone}",
        "variants": [{"id": k, "name": v["name"], "weight": v["weight"]} for k, v in sp["variants"].items()],
        "forms": {"adult": form(sp["forms"]["female"]())},
    }
    if "baby" in sp["forms"]:
        rig["forms"]["baby"] = form(sp["forms"]["baby"]())
    if sp["saddle"]:
        b = sp["saddle"]().bones[0]
        rig["saddle"] = {"name": b.name, "parent": b.parent, "joint": list(b.joint), "item": f"{NS}:{sid}/saddle/saddle"}
    if sp["egg"]:
        rig["egg"] = {"item": f"{NS}:{sid}/egg/egg", "cracked": f"{NS}:{sid}/egg_cracked/egg",
                      "icon": f"{NS}:{sid}/egg_item", "size": sp["egg"]["k"]}
    write_if_changed(os.path.join(RIG_DIR, sid + ".json"), json.dumps(rig, indent=2).encode())


# ---------------------------------------------------------------- escritura estable
def png_pixels(data):
    """Encabezado + pixeles descomprimidos de un PNG (para comparar sin importar el compresor)."""
    i, hdr, idat = 8, b"", b""
    while i < len(data):
        n = struct.unpack(">I", data[i:i + 4])[0]
        tag = data[i + 4:i + 8]
        if tag == b"IHDR":
            hdr = data[i + 8:i + 8 + n]
        elif tag == b"IDAT":
            idat += data[i + 8:i + 8 + n]
        i += 12 + n
    return hdr, zlib.decompress(idat)


def same_content(old, new, name):
    if old == new:
        return True
    if name.endswith(".png"):
        try:
            return png_pixels(old) == png_pixels(new)
        except (zlib.error, struct.error):
            return False
    return False


def write_if_changed(path, data):
    """Escribe el archivo solo si su contenido cambio (siempre en binario: nada de CRLF en Windows)."""
    try:
        with open(path, "rb") as fh:
            if same_content(fh.read(), data, path):
                return False
    except OSError:
        pass
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(data)
    return True


def zip_has(path, entries):
    """¿El zip existente ya tiene exactamente estas entradas? Entonces no se rehace (mismo hash)."""
    try:
        with zipfile.ZipFile(path) as z:
            if z.namelist() != [arc for arc, _ in entries]:
                return False
            return all(z.read(arc) == data for arc, data in entries)
    except (OSError, zipfile.BadZipFile):
        return False


def png_bytes(pix):
    h, w = len(pix), len(pix[0])
    raw = b"".join(b"\x00" + bytes(v for px in row for v in px) for row in pix)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    build()

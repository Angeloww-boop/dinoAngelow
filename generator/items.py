"""
Items propios de DinoEarth: lo que suelta cada especie al morir (cuero, garras, dientes...), la montura
de cada especie como item (se craftean con Shopkeepers), el silbato de ordenes y la jaula.

Dibuja los iconos (16x16 en pixel art; las monturas se renderizan en 3D desde su modelo) y escribe
plugin/src/main/resources/items.json con nombres, descripciones y drops: el plugin no tiene nada de
esto escrito a mano. Lo llama build.py.
"""
import json
import math
import os

import geometry as G
from build import NS, ROOT, png_bytes, write_if_changed

ITEMS_JSON = os.path.join(ROOT, "plugin", "src", "main", "resources", "items.json")
ITEMS_YML = os.path.join(ROOT, "plugin", "src", "main", "resources", "items.yml")

IVORY = (232, 222, 192)
BONE_TIP = (110, 92, 70)
IRON = (150, 154, 160)

# Lo que suelta cada especie: (id, nombre, forma, color, minimo, maximo, probabilidad)
DROPS = {
    "raptor": [("cuero_raptor", "Cuero de velociraptor", "hide", None, 1, 3, 1.0),
               ("garra_raptor", "Garra de velociraptor", "claw", None, 1, 2, 0.5)],
    "triceratops": [("cuero_triceratops", "Cuero de triceratops", "hide", None, 2, 4, 1.0),
                    ("cuerno_triceratops", "Cuerno de triceratops", "horn", None, 1, 1, 0.5)],
    "dilophosaurus": [("glandula_dilophosaurus", "Glándula venenosa", "gland", "accent2", 1, 1, 0.7),
                      ("cresta_dilophosaurus", "Cresta de dilophosaurus", "crest", "accent", 1, 1, 0.4)],
    "compsognathus": [("diente_compsognathus", "Diente de compsognathus", "tooth", None, 1, 1, 0.5)],
    "trex": [("cuero_trex", "Cuero de T-Rex", "hide", None, 3, 5, 1.0),
             ("diente_trex", "Diente de T-Rex", "tooth", None, 1, 3, 0.8)],
    "pteranodon": [("membrana_pteranodon", "Membrana de pteranodon", "membrane", "wing", 1, 2, 1.0),
                   ("cresta_pteranodon", "Cresta de pteranodon", "crest", "accent", 1, 1, 0.4)],
    "parasaurolophus": [("cuero_parasaurolophus", "Cuero de parasaurolophus", "hide", None, 1, 3, 1.0),
                        ("cresta_parasaurolophus", "Cresta de parasaurolophus", "crest", "accent", 1, 1, 0.4)],
    "gallimimus": [("cuero_gallimimus", "Cuero de gallimimus", "hide", None, 1, 2, 1.0),
                   ("pluma_gallimimus", "Pluma de gallimimus", "feather", "accent", 1, 3, 0.7)],
    "stegosaurus": [("cuero_stegosaurus", "Cuero de stegosaurus", "hide", None, 2, 4, 1.0),
                    ("placa_stegosaurus", "Placa de stegosaurus", "plate", "accent", 1, 2, 0.6)],
    "ankylosaurus": [("cuero_ankylosaurus", "Cuero de ankylosaurus", "hide", None, 2, 4, 1.0),
                     ("mazo_ankylosaurus", "Mazo de ankylosaurus", "club", None, 1, 1, 0.3)],
    "giganotosaurus": [("cuero_giganotosaurus", "Cuero de giganotosaurus", "hide", None, 3, 5, 1.0),
                       ("diente_giganotosaurus", "Diente de giganotosaurus", "tooth", None, 1, 3, 0.8)],
    "carnotaurus": [("cuero_carnotaurus", "Cuero de carnotaurus", "hide", None, 1, 3, 1.0),
                    ("cuerno_carnotaurus", "Cuerno de carnotaurus", "horn", None, 1, 2, 0.5)],
    "dimorphodon": [("membrana_dimorphodon", "Membrana de dimorphodon", "membrane", "wing", 1, 1, 0.8)],
    "spinosaurus": [("cuero_spinosaurus", "Cuero de spinosaurus", "hide", None, 2, 4, 1.0),
                    ("espina_spinosaurus", "Espina de la vela", "crest", "accent", 1, 2, 0.5)],
    "mosasaurus": [("cuero_mosasaurus", "Cuero de mosasaurus", "hide", None, 3, 5, 1.0),
                   ("diente_mosasaurus", "Diente de mosasaurus", "tooth", None, 1, 3, 0.8)],
}


# ---------------------------------------------------------------- dibujo 16x16
def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


class Canvas:
    def __init__(self):
        self.px = [[None] * 16 for _ in range(16)]

    def dot(self, x, y, col):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[y][x] = col

    def fill(self, inside, color):
        """inside(x, y) en el centro del pixel; color(x, y) -> rgb."""
        for y in range(16):
            for x in range(16):
                if inside(x + 0.5, y + 0.5):
                    self.px[y][x] = color(x + 0.5, y + 0.5)

    def stroke(self, points, width, color):
        """Linea gruesa por una lista de (x, y); width puede ser numero o funcion de t (0..1)."""
        for y in range(16):
            for x in range(16):
                cx, cy = x + 0.5, y + 0.5
                best = None
                for i in range(len(points) - 1):
                    (ax, ay), (bx, by) = points[i], points[i + 1]
                    dx, dy = bx - ax, by - ay
                    L = dx * dx + dy * dy or 1e-9
                    s = max(0.0, min(1.0, ((cx - ax) * dx + (cy - ay) * dy) / L))
                    d = math.hypot(cx - (ax + s * dx), cy - (ay + s * dy))
                    t = (i + s) / (len(points) - 1)
                    if best is None or d < best[0]:
                        best = (d, t)
                d, t = best
                w = width(t) if callable(width) else width
                if d <= w / 2:
                    self.px[y][x] = color(t, cx, cy)

    def finish(self, outline=0.45):
        """Borde oscuro de 1 pixel alrededor de lo dibujado (look de item de Minecraft)."""
        out = [row[:] for row in self.px]
        for y in range(16):
            for x in range(16):
                if self.px[y][x] is not None:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and self.px[ny][nx] is not None:
                        out[y][x] = shade(self.px[ny][nx], outline)
                        break
        return [[(0, 0, 0, 0) if p is None else (p[0], p[1], p[2], 255) for p in row] for row in out]


def bezier(p0, p1, p2, n=12):
    pts = []
    for i in range(n + 1):
        t = i / n
        pts.append(((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
                    (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]))
    return pts


def light(x, y):
    """Luz desde arriba a la izquierda."""
    return 1.12 - 0.03 * (x - 4) - 0.025 * (y - 4)


def draw(shape, pal, key):
    c = Canvas()
    base, stripe, dark = pal["base"], pal["stripe"], pal["dark"]
    col = pal.get(key or "accent", pal["accent"]) if key else base
    if shape == "hide":
        def inside(x, y):
            blob = ((x - 8) / 5.6) ** 2 + ((y - 8.5) / 4.8) ** 2 <= 1
            legs = any(math.hypot(x - lx, y - ly) <= 1.7 for lx, ly in ((3.2, 4.2), (12.8, 4.2), (3.4, 13), (12.6, 13)))
            return blob or legs
        c.fill(inside, lambda x, y: shade(stripe if math.sin(x * 1.25 + y * 0.35) > 0.55 else base, light(x, y)))
    elif shape in ("claw", "horn", "tooth"):
        if shape == "claw":
            pts, w = bezier((3.5, 12.5), (4, 4), (12.5, 3)), lambda t: 3.6 * (1 - t) + 0.9
        elif shape == "horn":
            pts, w = bezier((3, 13.5), (11.5, 12), (12.5, 2.5)), lambda t: 4.2 * (1 - t) + 0.9
        else:   # colmillo curvo: raiz arriba, punta abajo
            pts, w = bezier((5.5, 2), (11.5, 6), (8.5, 14)), lambda t: 4.8 * (1 - t) ** 0.9 + 0.8
        if shape == "tooth":
            c.stroke(pts, w, lambda t, x, y: shade((156, 124, 96) if t < 0.16 else mix(IVORY, (250, 246, 232), t),
                                                   light(x, y)))
        else:
            c.stroke(pts, w, lambda t, x, y: shade(mix(IVORY, BONE_TIP, (t - 0.45) / 0.55), light(x, y)))
    elif shape == "plate":
        poly = [(8, 1.5), (13.5, 7.5), (11, 14.5), (5, 14.5), (2.5, 7.5)]

        def inside(x, y):
            n, ok = len(poly), False
            for i in range(n):
                (ax, ay), (bx, by) = poly[i], poly[i - 1]
                if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
                    ok = not ok
            return ok
        c.fill(inside, lambda x, y: shade(mix(col, base, max(0, (y - 9) / 8)), light(x, y)))
    elif shape == "club":
        c.stroke([(3, 13.5), (8, 8)], 2.6, lambda t, x, y: shade(base, light(x, y)))
        c.fill(lambda x, y: math.hypot(x - 9.8, y - 6.2) <= 4.3,
               lambda x, y: shade(mix(dark, base, 0.5 + 0.5 * math.sin(x * 2) * math.cos(y * 2)), light(x, y)))
    elif shape == "feather":
        ang = math.atan2(-10, 8)

        def inside(x, y):
            u = (x - 8) * math.cos(ang) + (y - 8) * math.sin(ang)
            v = -(x - 8) * math.sin(ang) + (y - 8) * math.cos(ang)
            return (u / 7) ** 2 + (v / 2.6) ** 2 <= 1
        c.fill(inside, lambda x, y: shade(mix(col, base, 0.3 if (x + y) % 3 == 0 else 0), light(x, y)))
        c.stroke([(3.5, 13.5), (12, 3)], 0.9, lambda t, x, y: IVORY)
    elif shape == "membrane":
        poly = [(2, 3), (14, 4.5), (5, 14)]

        def inside(x, y):
            (x1, y1), (x2, y2), (x3, y3) = poly
            d = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3)
            a = ((y2 - y3) * (x - x3) + (x3 - x2) * (y - y3)) / d
            b = ((y3 - y1) * (x - x3) + (x1 - x3) * (y - y3)) / d
            return a >= 0 and b >= 0 and 1 - a - b >= 0
        c.fill(inside, lambda x, y: shade(mix(col, base, 0.15 * math.sin(x + y)), light(x, y) * 0.95))
        c.stroke([(2, 3), (14, 4.5)], 1.6, lambda t, x, y: shade(IVORY, 0.9))
    elif shape == "gland":
        c.fill(lambda x, y: math.hypot(x - 8, (y - 9.5) * 1.1) <= 4.8,
               lambda x, y: shade(col, light(x, y) + (0.35 if math.hypot(x - 6.3, y - 7.6) < 1.3 else 0)))
        c.stroke([(8, 4.8), (8.5, 2)], 1.4, lambda t, x, y: shade(col, 0.8))
    elif shape == "crest":
        c.stroke(bezier((3, 13), (5, 3), (13.5, 3.5)), lambda t: 3.4 - 1.2 * t,
                 lambda t, x, y: shade(mix(col, pal.get("accent2", col), t), light(x, y)))
    return c.finish()


def whistle():
    c = Canvas()
    c.stroke([(2.5, 9.5), (13, 6.5)], 3.2, lambda t, x, y: shade(IVORY, light(x, y)))
    for x, y in ((10, 6), (10, 7)):
        c.dot(x, y, (60, 44, 34))           # agujero
    c.stroke(bezier((2.5, 9.5), (1.5, 13), (5, 14.5)), 0.9, lambda t, x, y: (150, 52, 40))   # cordon
    return c.finish()


def cage(full=False):
    c = Canvas()
    wood = (120, 84, 50)
    c.fill(lambda x, y: 2 <= x < 14 and (2 <= y < 4 or 12 <= y < 14), lambda x, y: shade(wood, light(x, y)))
    c.fill(lambda x, y: 2 <= x < 14 and 4 <= y < 12 and int(x) % 3 == 2, lambda x, y: shade(IRON, light(x, y)))
    if full:   # un dino chiquito adentro
        for x, y in ((5, 9), (6, 9), (7, 9), (8, 9), (9, 8), (10, 7), (10, 6), (11, 6), (4, 8), (3, 7),
                     (6, 10), (6, 11), (8, 10), (8, 11), (7, 8), (6, 8)):
            c.dot(x, y, (176, 120, 60))
    c.dot(7, 1, (80, 80, 86))
    c.dot(8, 1, (80, 80, 86))
    return c.finish()


# ---------------------------------------------------------------- monturas (render 3D)
def saddle_icon(sets, sid, scale):
    """Icono de la montura renderizando su modelo, como los iconos de la Dinopedia."""
    import icons
    rig, pix, size, models = sets[f"{sid}/saddle"]
    alone = G.Rig([G.Bone(b.name, b.joint, None, b.cubes) for b in rig.bones])   # sin el cuerpo de padre
    fake = {f"{sid}/saddle_icon": (alone, pix, size, models)}
    return icons.icon(fake, f"{sid}/saddle_icon", scale, "reposo", 135, 30)


def put(files, path, pix):
    files[f"assets/{NS}/textures/item/{path}.png"] = png_bytes(pix)
    files[f"assets/{NS}/models/item/{path}.json"] = json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{path}"}}).encode()
    files[f"assets/{NS}/items/{path}.json"] = json.dumps(
        {"model": {"type": "minecraft:model", "model": f"{NS}:item/{path}"}}).encode()


def build_items(species, sets, files):
    """items.json = datos (modelo, material, drops); items.yml = textos (nombre y lore con el formato del server)."""
    items, drops, texts = [], {}, {}
    names = {sp["id"]: sp["name"] for sp in species}
    for sp in species:
        sid = sp["id"]
        pal = next(iter(sp["variants"].values()))
        drops[sid] = []
        for iid, name, shape, key, lo, hi, chance in DROPS.get(sid, []):
            put(files, f"items/{iid}", draw(shape, pal, key))
            items.append({"id": iid, "kind": "drop", "species": sid, "material": "PAPER", "max_stack": 64,
                          "model": f"{NS}:items/{iid}"})
            texts[iid] = (name, [f"Lo suelta el &#ff00d9{names[sid]}&f.", "Se usa para craftear monturas."])
            drops[sid].append({"item": iid, "min": lo, "max": hi, "chance": chance})
        if sp["saddle"]:
            iid = f"montura_{sid}"
            put(files, f"items/{iid}", saddle_icon(sets, sid, sp["scale"]))
            items.append({"id": iid, "kind": "saddle", "species": sid, "material": "SADDLE", "max_stack": 1,
                          "model": f"{NS}:items/{iid}"})
            texts[iid] = (f"Montura de {names[sid]}", [f"Click derecho sobre tu &#ff00d9{names[sid]}&f adulto.",
                                                      "Con tijeras se la sacás y te la devuelve."])
    put(files, "items/silbato", whistle())
    items.append({"id": "silbato", "kind": "whistle", "material": "STICK", "max_stack": 1,
                  "model": f"{NS}:items/silbato"})
    texts["silbato"] = ("Silbato de dinos", ["Click derecho a tu dino &8» &#ff00d9darle órdenes&f.",
                                             "Agachado y click derecho al aire &8» &#ff00d9órdenes a todos",
                                             "&#ff00d9tus dinos&f a 16 bloques."])
    put(files, "items/jaula", cage())
    put(files, "items/jaula_llena", cage(full=True))
    items.append({"id": "jaula", "kind": "cage", "material": "PAPER", "max_stack": 16,
                  "model": f"{NS}:items/jaula", "model_full": f"{NS}:items/jaula_llena"})
    texts["jaula"] = ("Jaula para dinos", ["Click derecho a tu dino &8» &#ff00d9lo guarda adentro&f.",
                                           "Click derecho al piso con la jaula llena &8» &#ff00d9lo suelta&f.",
                                           "Quien lo suelta queda como dueño."])
    write_if_changed(ITEMS_JSON, json.dumps({"items": items, "drops": drops}, indent=2, ensure_ascii=False).encode())
    write_if_changed(ITEMS_YML, items_yml(texts).encode("utf-8"))
    return items


def q(text):
    return "'" + text.replace("'", "''") + "'"


def items_yml(texts):
    """Formato del server: nombre &#ff00d9, lore con '&8| &f' y separadores '&f'."""
    out = ["# Nombre y descripcion de los items de DinoEarth. Los genera generator/items.py; el dueño los puede cambiar.",
           "# Formato: display_name y lore como en los menus (&8| &f para cada linea, &f para separar).", ""]
    for iid, (name, lore) in texts.items():
        out.append(f"{iid}:")
        out.append(f"  display_name: {q('&#ff00d9' + name)}")
        out.append("  lore:")
        out.append("    - '&f'")
        for line in lore:
            out.append(f"    - {q('&8| &f' + line)}")
        out.append("    - '&f'")
    out.append(STATIC_ITEMS)
    return "\n".join(out) + "\n"


# items que arma el plugin (no son de items.json): huevo, comedero, Dinopedia y la jaula con un dino adentro
STATIC_ITEMS = """
# ---------------------------------------------------------------- items del plugin
huevo:
  display_name: '&#ff00d9Huevo de %especie%'
  lore:
    - '&f'
    - '&8| &fPonelo en el suelo y esperá a que eclosione.'
    - '&8| &fLa cría sale de un color al azar y es tuya.'
    - '&f'
huevo-cria:
  display_name: '&#ff00d9Huevo de %especie%'
  lore:
    - '&f'
    - '&8| &fMadre &8» &#ff00d9%madre%'
    - '&8| &fPadre &8» &#ff00d9%padre%'
    - '&8| &fGeneración &8» &#ff00d9%generacion%'
    - '&f'
    - '&8| &fPonelo en el suelo y esperá a que eclosione.'
    - '&8| &fLa cría hereda el color y los rasgos de los padres.'
    - '&f'
huevo-nido:
  display_name: '&#ff00d9Huevo salvaje de %especie%'
  lore:
    - '&f'
    - '&8| &fSacado de un nido salvaje.'
    - '&8| &fMadre &8» &#ff00d9%madre%'
    - '&f'
    - '&8| &fPonelo en el suelo y esperá a que eclosione.'
    - '&8| &fLa cría nace domesticada por vos.'
    - '&f'
comedero:
  display_name: '&#ff00d9Comedero de %dieta%'
  lore:
    - '&f'
    - '&8| &fPonelo en el suelo y llenalo con &#ff00d9%comida%&f.'
    - '&8| &fLos dinos domesticados con hambre vienen a comer solos.'
    - '&8| &fAgachado carga la pila entera. No se puede vaciar.'
    - '&f'
dinopedia:
  display_name: '&#ff00d9Dinopedia'
  lore:
    - '&f'
    - '&8| &fClick derecho &8» &#ff00d9abrir&f.'
    - '&8| &fClick derecho a un dinosaurio &8» &#ff00d9su ficha&f.'
    - '&8| &fEn la mano anotás dinos desde más lejos, ves'
    - '&8| &fhuellas de especies que no conocés y la lupa'
    - '&8| &fte marca hacia dónde ir.'
    - '&f'
jaula-llena:
  display_name: '&#ff00d9Jaula con %dino%'
  lore:
    - '&f'
    - '&8| &fEspecie &8» &#ff00d9%especie% %color%'
    - '&8| &fSexo &8» &#ff00d9%sexo%'
    - '&8| &fEdad &8» &#ff00d9%edad%'
    - '&8| &fVida &8» &#ff00d9%vida%%'
    - '%montura%'
    - '&f'
    - '&8| &fClick derecho al piso para soltarlo.'
    - '&8| &fQuien lo suelta queda como dueño.'
    - '&f'
jaula-con-montura: '&8| &fTiene &#ff00d9montura&f.'
mapa:
  display_name: '&#ff00d9Mapa del Paleontólogo'
  lore:
    - '&f'
    - '&8| &fEn la mano &8» &#ff00d9tus coordenadas reales&f y la región.'
    - '&8| &fClick derecho &8» &#ff00d9regiones de fósiles&f, qué dinos'
    - '&8| &fhay en cada una y hacia dónde quedan.'
    - '&f'
"""

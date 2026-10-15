"""
Piezas comunes para modelar dinosaurios.

Todo esta en "espacio local" en pixeles (16 px = 1 bloque):
  +X = izquierda/derecha, +Y = arriba, +Z = adelante (hacia donde mira el dino).
  Origen = centro del hitbox, a nivel del suelo.

Cada hueso (bone) es una item_display separada en el juego. Su "joint" es el
punto de giro: las animaciones rotan el hueso alrededor de ese punto. Ningun
cubo puede alejarse mas de 24 px de su joint (limite de Minecraft).

Cada especie vive en su propio archivo (species_*.py) y define un dict SPECIES.
"""
import math


class Cube:
    def __init__(self, frm, to, tag="skin", rot=None, mouth=None, stripes="z", tip=None):
        self.frm = tuple(float(v) for v in frm)
        self.to = tuple(float(v) for v in to)
        # skin | claw | dark | accent | crest | horn | beak | frill | fan | membrane | egg
        # montura: saddle | saddle_dark | strap | metal | plate | spike | cloth | wood | rope
        self.tag = tag
        self.rot = rot          # (eje, angulo, origen) en espacio local, o None
        self.mouth = mouth      # 'up' / 'down': esa cara es el interior de la boca
        self.stripes = stripes  # eje sobre el que corren las rayas de la piel
        self.tip = tip          # cuernos: hacia donde esta la punta ('x', 'y', 'z')
        # cuanto se agranda cada cara para no quedar en el mismo plano que otra (lo pone defight)
        self.grow_lo = [0.0, 0.0, 0.0]
        self.grow_hi = [0.0, 0.0, 0.0]

    def bounds(self):
        """Limites reales del cubo en el modelo (con lo que lo agrando defight)."""
        return (tuple(self.frm[i] - self.grow_lo[i] for i in range(3)),
                tuple(self.to[i] + self.grow_hi[i] for i in range(3)))

    def mirrored(self):
        f, t = self.frm, self.to
        rot = None
        if self.rot:
            axis, ang, o = self.rot
            rot = (axis, ang if axis == "x" else -ang, (-o[0], o[1], o[2]))
        return Cube((-t[0], f[1], f[2]), (-f[0], t[1], t[2]), self.tag, rot, self.mouth, self.stripes, self.tip)


class Bone:
    def __init__(self, name, joint, parent, cubes):
        self.name = name
        self.joint = tuple(float(v) for v in joint)
        self.parent = parent
        self.cubes = cubes


class Rig:
    """Conjunto de huesos + donde van los detalles pintados de la cara."""

    def __init__(self, bones, face=None, pattern="stripes"):
        self.bones = bones
        self.face = face or {}
        self.pattern = pattern

    def bone(self, name):
        return next(b for b in self.bones if b.name == name)


def mirror(cubes):
    return [c.mirrored() for c in cubes]


# ---------------------------------------------------------------- caras superpuestas (z-fighting)
def _rotate(p, rot):
    """Rotacion regla de la mano derecha alrededor de un origen (igual que build.rotate)."""
    if not rot:
        return p
    axis, ang, o = rot
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def _faces(cube):
    """Caras del cubo ya rotadas: (eje, lado, normal, esquinas)."""
    f, t = cube.bounds()
    out = []
    for axis in range(3):
        a, b = [i for i in range(3) if i != axis]
        for side, val in ((-1, f[axis]), (1, t[axis])):
            corners = []
            for ua, ub in ((f[a], f[b]), (t[a], f[b]), (t[a], t[b]), (f[a], t[b])):
                p = [0.0, 0.0, 0.0]
                p[axis], p[a], p[b] = val, ua, ub
                corners.append(_rotate(tuple(p), cube.rot))
            n = [0.0, 0.0, 0.0]
            n[axis] = side
            if cube.rot:
                n = list(_rotate(tuple(n), (cube.rot[0], cube.rot[1], (0, 0, 0))))
            out.append((axis, side, tuple(n), corners))
    return out


def coplanar_pairs(bones, eps=0.01, min_area=0.05):
    """Pares de caras de cubos distintos en el mismo plano, mirando al mismo lado y superpuestas:
    ahi la placa de video no sabe cual dibujar adelante y la textura 'tiembla'."""
    items = []
    for bone in bones:
        for cube in bone.cubes:
            for axis, side, n, cs in _faces(cube):
                items.append((cube, axis, side, n, sum(n[i] * cs[0][i] for i in range(3)), cs))
    found = []
    for i in range(len(items)):
        ca, axa, sa, na, da, csa = items[i]
        dom = max(range(3), key=lambda k: abs(na[k]))
        u, v = [k for k in range(3) if k != dom]
        boxa = (min(p[u] for p in csa), max(p[u] for p in csa), min(p[v] for p in csa), max(p[v] for p in csa))
        for j in range(i + 1, len(items)):
            cb, axb, sb, nb, db, csb = items[j]
            if cb is ca or abs(da - db) > eps or sum(na[k] * nb[k] for k in range(3)) < 0.999:
                continue
            boxb = (min(p[u] for p in csb), max(p[u] for p in csb), min(p[v] for p in csb), max(p[v] for p in csb))
            w = min(boxa[1], boxb[1]) - max(boxa[0], boxb[0])
            h = min(boxa[3], boxb[3]) - max(boxa[2], boxb[2])
            if w > 0 and h > 0 and w * h > min_area:
                found.append((ca, axa, sa, cb, axb, sb, w * h))
    return found


def _volume(cube):
    f, t = cube.bounds()
    return (t[0] - f[0]) * (t[1] - f[1]) * (t[2] - f[2])


def defight(bones, delta=0.08, rounds=4):
    """Separa las caras coplanares: empuja hacia afuera la del cubo mas chico (0.08 px, no se nota a
    la vista pero alcanza para que la placa de video sepa cual va adelante). Devuelve cuantas toco."""
    order = {id(c): k for k, c in enumerate(c for b in bones for c in b.cubes)}
    moved = 0
    for _ in range(rounds):
        pairs = coplanar_pairs(bones)
        if not pairs:
            break
        pushed = set()
        for ca, axa, sa, cb, axb, sb, _area in pairs:
            small = (ca, axa, sa) if (_volume(ca), order[id(ca)]) <= (_volume(cb), order[id(cb)]) else (cb, axb, sb)
            cube, axis, side = small
            if (id(cube), axis, side) in pushed:
                continue
            pushed.add((id(cube), axis, side))
            if side > 0:
                cube.grow_hi[axis] += delta
            else:
                cube.grow_lo[axis] += delta
            moved += 1
    return moved


def face(eye, pupil, highlight=None, eye_stripe=None, brow=None, teeth_y=None, teeth_z=99.0,
         jaw_teeth_y=None, jaw_teeth_z=99.0, nostril=None, mouth_teeth=False):
    """Donde se pintan ojos, dientes y fosas nasales (coordenadas z, y locales)."""
    return dict(eye=eye, pupil=pupil, highlight=highlight, eye_stripe=eye_stripe, brow=brow,
                teeth_y=teeth_y, teeth_z=teeth_z, jaw_teeth_y=jaw_teeth_y, jaw_teeth_z=jaw_teeth_z,
                nostril=nostril, mouth_teeth=mouth_teeth)


def egg(k=1.0):
    """Huevo apilando capas; k = tamaño relativo."""
    layers = [(2.5, 0.0, 1.0), (3.5, 1.0, 3.0), (4.0, 3.0, 6.0), (3.5, 6.0, 8.5), (2.5, 8.5, 10.0), (1.25, 10.0, 10.75)]
    return Rig([Bone("egg", (0, 0, 0), None, [
        Cube((-r * k, y0 * k, -r * k), (r * k, y1 * k, r * k), tag="egg") for r, y0, y1 in layers
    ])])


# Materiales de las monturas. Cada especie puede cambiar cualquiera con SPECIES["saddle_colors"].
#   saddle = cuero del asiento (con costura), saddle_dark = respaldo/pomo, strap = correas,
#   metal = hebillas y argollas, plate = placas de armadura (con remaches), spike = puas de hueso,
#   cloth = manta (trim = su guarda), wood = madera, rope = soga
SADDLE_COLORS = dict(saddle=(122, 74, 42), saddle_dark=(84, 50, 28), strap=(62, 42, 30), metal=(206, 194, 150),
                     plate=(120, 122, 128), spike=(226, 214, 186), cloth=(150, 40, 40), trim=(220, 180, 70),
                     wood=(128, 92, 56), rope=(190, 162, 112))
SADDLE_TAGS = ("saddle", "saddle_dark", "strap", "metal", "plate", "spike", "cloth", "wood", "rope")


def saddle_palette(over=None):
    return {**SADDLE_COLORS, **(over or {})}

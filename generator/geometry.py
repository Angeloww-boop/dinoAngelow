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


class Cube:
    def __init__(self, frm, to, tag="skin", rot=None, mouth=None, stripes="z", tip=None):
        self.frm = tuple(float(v) for v in frm)
        self.to = tuple(float(v) for v in to)
        # skin | claw | dark | accent | crest | horn | beak | frill | fan |
        # saddle | saddle_dark | strap | metal | egg
        self.tag = tag
        self.rot = rot          # (eje, angulo, origen) en espacio local, o None
        self.mouth = mouth      # 'up' / 'down': esa cara es el interior de la boca
        self.stripes = stripes  # eje sobre el que corren las rayas de la piel
        self.tip = tip          # cuernos: hacia donde esta la punta ('x', 'y', 'z')

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


def face(eye, pupil, highlight=None, eye_stripe=None, brow=None, teeth_y=None, teeth_z=99.0,
         jaw_teeth_y=None, jaw_teeth_z=99.0, nostril=None, mouth_teeth=False):
    """Donde se pintan ojos, dientes y fosas nasales (coordenadas z, y locales)."""
    return dict(eye=eye, pupil=pupil, highlight=highlight, eye_stripe=eye_stripe, brow=brow,
                teeth_y=teeth_y, teeth_z=teeth_z, jaw_teeth_y=jaw_teeth_y, jaw_teeth_z=jaw_teeth_z,
                nostril=nostril, mouth_teeth=mouth_teeth)


def saddle_cubes(seat, cantle, pommel, strap_x, strap_y, strap_z, belly_y):
    """Montura generica: asiento, respaldo, pomo, correas a los costados y cincha."""
    strap_r = [
        Cube((strap_x, strap_y[0], strap_z[0]), (strap_x + 0.4, strap_y[1], strap_z[1]), tag="strap"),
        Cube((strap_x + 0.35, strap_y[0] + 2, strap_z[0] + 0.25), (strap_x + 0.6, strap_y[0] + 3.25, strap_z[1] - 0.25),
             tag="metal"),
    ]
    return [
        Cube(seat[0], seat[1], tag="saddle"),
        Cube(cantle[0], cantle[1], tag="saddle_dark"),
        Cube(pommel[0], pommel[1], tag="saddle_dark"),
        Cube((-strap_x, belly_y - 0.4, strap_z[0]), (strap_x, belly_y, strap_z[1]), tag="strap"),
    ] + strap_r + mirror(strap_r)


def egg(k=1.0):
    """Huevo apilando capas; k = tamaño relativo."""
    layers = [(2.5, 0.0, 1.0), (3.5, 1.0, 3.0), (4.0, 3.0, 6.0), (3.5, 6.0, 8.5), (2.5, 8.5, 10.0), (1.25, 10.0, 10.75)]
    return Rig([Bone("egg", (0, 0, 0), None, [
        Cube((-r * k, y0 * k, -r * k), (r * k, y1 * k, r * k), tag="egg") for r, y0, y1 in layers
    ])])


SADDLE_COLORS = dict(saddle=(122, 74, 42), saddle_dark=(84, 50, 28), strap=(62, 42, 30), metal=(206, 194, 150))

"""Velociraptor: bipedo chico, neutral y domesticable, se puede montar."""
from geometry import Cube, Bone, Rig, mirror, face, saddle_cubes


def adult(male=False):
    arm_r = [
        Cube((4.0, 13.0, 7.0), (5.5, 17.5, 9.5)),                 # brazo
        Cube((3.75, 11.0, 8.5), (5.25, 13.0, 13.0)),              # antebrazo
        Cube((3.9, 9.5, 11.5), (5.1, 11.0, 13.0), tag="claw"),     # garras de la mano
    ]
    body = Bone("body", (0, 17, 0), None, [
        Cube((-4.5, 14.0, -9.0), (4.5, 22.0, -1.0)),              # cadera
        Cube((-4.5, 13.0, -1.0), (4.5, 21.5, 5.0)),               # torso
        Cube((-4.0, 12.0, 5.0), (4.0, 21.0, 10.0)),               # pecho
        Cube((-3.0, 17.0, 9.0), (3.0, 22.0, 12.0)),               # base del cuello
    ] + arm_r + mirror(arm_r))

    head_cubes = [
        Cube((-2.75, 18.0, 6.5), (2.75, 28.5, 12.0), rot=("x", 22.5, (0, 19.5, 9.5)), stripes="y"),  # cuello
        Cube((-3.0, 27.0, 10.0), (3.0, 32.0, 17.0), mouth="down"),               # craneo
        Cube((-2.25, 27.0, 17.0), (2.25, 30.5, 22.0), mouth="down"),             # hocico
        Cube((-3.25, 30.75, 12.5), (3.25, 32.25, 16.0), tag="dark"),             # cejas
        Cube((-2.5, 25.0, 10.0), (2.5, 27.0, 12.5)),                             # garganta
    ]
    if male:
        # cresta de plumas/puas del macho, inclinadas hacia atras
        cheek = [Cube((2.9, 28.5, 10.0), (3.75, 30.5, 11.25), tag="accent")]
        head_cubes += [
            Cube((-0.5, 31.5, 9.5), (0.5, 34.5, 11.0), tag="accent", rot=("x", -22.5, (0, 32, 10.25))),
            Cube((-0.5, 31.5, 11.5), (0.5, 34.0, 13.0), tag="accent", rot=("x", -22.5, (0, 32, 12.25))),
            Cube((-0.5, 32.0, 13.5), (0.5, 33.5, 15.0), tag="accent", rot=("x", -22.5, (0, 32, 14.25))),
        ] + cheek + mirror(cheek)

    leg_r = [
        Cube((3.5, 11.0, -9.0), (8.0, 21.0, -1.5), stripes="y"),                                   # muslo
        Cube((4.5, 4.0, -6.0), (6.5, 13.0, -3.0), rot=("x", 22.5, (5.5, 8.5, -4.5)), stripes="y"),  # canilla
        Cube((4.75, 1.0, -7.5), (6.25, 6.0, -5.5), rot=("x", -22.5, (5.5, 3.5, -6.5)), stripes="y"),  # metatarso
        Cube((3.75, 0.0, -6.5), (7.25, 1.75, 0.0)),                                                 # pie
        Cube((4.0, 0.0, 0.0), (7.0, 1.0, 1.0), tag="claw"),                                         # garras del pie
        Cube((3.75, 1.75, -2.5), (4.75, 4.25, -1.25), tag="claw", rot=("x", -22.5, (4.25, 1.75, -1.9))),  # garra hoz
    ]
    bones = [
        body,
        Bone("head", (0, 19.5, 9.5), "body", head_cubes),
        Bone("jaw", (0, 27, 12), "head", [Cube((-2.0, 25.5, 12.0), (2.0, 27.0, 21.5), mouth="up")]),
        Bone("tail1", (0, 19, -8), "body", [
            Cube((-3.5, 15.5, -17.0), (3.5, 21.5, -7.0)),
            Cube((-2.5, 16.5, -24.0), (2.5, 21.0, -16.5)),
        ]),
        Bone("tail2", (0, 18.75, -23.5), "tail1", [
            Cube((-1.75, 17.0, -30.0), (1.75, 20.5, -23.0)),
            Cube((-1.25, 17.5, -34.0), (1.25, 20.0, -29.5)),
            Cube((-0.75, 18.0, -36.5), (0.75, 19.5, -33.5)),
        ]),
        Bone("leg_r", (5.5, 18, -5), None, leg_r),
        Bone("leg_l", (-5.5, 18, -5), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(13, 16, 29, 31), pupil=(14, 15, 29, 31), eye_stripe=(11, 13, 29, 30.5),
                           brow=(12, 17, 31, 32), teeth_y=(27, 28), teeth_z=12.5, jaw_teeth_y=26.0, jaw_teeth_z=13,
                           nostril=(21, 29, 30), mouth_teeth=True))


def baby():
    """Cria: cabeza y ojos grandes, cola y patas cortas (~1 bloque de alto a escala 1)."""
    arm_r = [
        Cube((2.75, 6.0, 4.0), (3.5, 8.0, 5.5)),
        Cube((2.5, 5.0, 5.0), (3.25, 6.0, 7.0)),
    ]
    leg_r = [
        Cube((2.0, 5.0, -4.0), (4.5, 10.0, 0.5), stripes="y"),
        Cube((2.5, 1.25, -3.0), (4.0, 5.5, -1.0), stripes="y"),
        Cube((2.0, 0.0, -3.0), (4.5, 1.25, 1.0)),
        Cube((2.25, 0.0, 1.0), (4.25, 0.75, 1.75), tag="claw"),
    ]
    bones = [
        Bone("body", (0, 9, 0), None, [
            Cube((-3.0, 6.0, -4.0), (3.0, 11.5, 4.0)),
            Cube((-2.5, 5.5, 3.0), (2.5, 10.5, 6.0)),
        ] + arm_r + mirror(arm_r)),
        Bone("head", (0, 10, 5), "body", [
            Cube((-1.75, 9.0, 4.0), (1.75, 14.0, 7.0), rot=("x", 22.5, (0, 10, 5.5)), stripes="y"),
            Cube((-3.0, 13.0, 5.0), (3.0, 18.0, 11.0), mouth="down"),
            Cube((-2.0, 13.0, 11.0), (2.0, 16.0, 14.0), mouth="down"),
        ]),
        Bone("jaw", (0, 13, 8), "head", [Cube((-1.75, 12.0, 8.0), (1.75, 13.0, 13.5), mouth="up")]),
        Bone("tail1", (0, 9, -4), "body", [Cube((-2.0, 6.5, -10.0), (2.0, 10.5, -3.5))]),
        Bone("tail2", (0, 8.5, -9.5), "tail1", [
            Cube((-1.25, 7.0, -15.0), (1.25, 9.5, -9.5)),
            Cube((-0.75, 7.5, -18.0), (0.75, 9.0, -14.5)),
        ]),
        Bone("leg_r", (3.25, 9, -1.5), None, leg_r),
        Bone("leg_l", (-3.25, 9, -1.5), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(7, 10, 14, 17), pupil=(8, 10, 14, 16), highlight=(9, 10, 15, 16), nostril=(13, 15, 16)),
               pattern="spots")


def saddle():
    return Rig([Bone("saddle", (0, 17, 0), "body", saddle_cubes(
        seat=((-4.75, 21.5, -6.0), (4.75, 22.75, 3.0)),
        cantle=((-3.0, 22.75, -6.0), (3.0, 24.25, -4.5)),
        pommel=((-1.5, 22.75, 1.5), (1.5, 24.0, 3.0)),
        strap_x=4.5, strap_y=(13.0, 21.5), strap_z=(-0.5, 1.0), belly_y=13.0))])


# Cada variante: base, rayas, panza, oscuro, iris, pupila, acento (cresta del macho)
VARIANTS = {
    "arena":   dict(name="Arena", weight=30, base=(176, 140, 92), stripe=(88, 60, 37), belly=(220, 202, 162),
                    dark=(104, 74, 46), iris=(236, 178, 40), pupil=(16, 12, 10), accent=(178, 62, 40)),
    "gris":    dict(name="Gris azulado", weight=20, base=(100, 108, 118), stripe=(44, 66, 104), belly=(170, 174, 176),
                    dark=(70, 74, 82), iris=(222, 192, 64), pupil=(16, 12, 10), accent=(60, 112, 176)),
    "verde":   dict(name="Selva", weight=20, base=(102, 126, 70), stripe=(46, 64, 34), belly=(192, 198, 142),
                    dark=(58, 76, 42), iris=(232, 160, 30), pupil=(16, 12, 10), accent=(214, 160, 40)),
    "rojizo":  dict(name="Rojizo", weight=15, base=(162, 96, 62), stripe=(92, 46, 30), belly=(216, 182, 142),
                    dark=(100, 56, 36), iris=(242, 202, 64), pupil=(16, 12, 10), accent=(232, 124, 40)),
    "oscuro":  dict(name="Oscuro", weight=12, base=(80, 74, 66), stripe=(156, 124, 72), belly=(150, 140, 120),
                    dark=(48, 44, 40), iris=(234, 84, 30), pupil=(16, 12, 10), accent=(204, 40, 40)),
    "albino":  dict(name="Albino", weight=3, base=(234, 228, 216), stripe=(208, 198, 184), belly=(246, 242, 234),
                    dark=(202, 192, 180), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(232, 150, 162)),
}

SPECIES = dict(
    id="raptor", name="Velociraptor", scale=1.0,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    egg=dict(k=1.0, shell=(232, 224, 198), spot=(150, 116, 80), crack=(60, 44, 34)),
    variants=VARIANTS,
)

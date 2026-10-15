"""Stegosaurus: herbivoro con dos filas de placas en el lomo y puas en la cola. Si le pegas por
atras, te da un coletazo con puas. Se modela a escala 1 y se muestra a 0.85.
"""
from geometry import Cube, Bone, Rig, mirror, face


def plates(spec, side):
    """spec: [(z, base_y, alto)]; alternan izquierda/derecha empezando por side."""
    out = []
    for i, (z, y, h) in enumerate(spec):
        x = 1.25 if (i % 2 == 0) == (side > 0) else -1.25
        w = 2.0 + h * 0.35
        out.append(Cube((x - 0.5, y, z - w), (x + 0.5, y + h, z + w), tag="crest", stripes="y"))
    return out


def adult(male=False):
    k = 1.15 if male else 1.0
    back = [(15.0, 26.5, 3.0), (2.0, 32.0, 6.0 * k), (-4.0, 34.0, 8.0 * k), (-9.0, 36.0, 9.0 * k),
            (-13.5, 36.0, 8.0 * k)]
    body = Bone("body", (0, 26, 0), None, [
        Cube((-9.0, 14.0, -14.0), (9.0, 32.0, 12.0)),            # torso
        Cube((-8.5, 26.0, -14.0), (8.5, 37.0, 0.0)),             # cadera (lo mas alto)
        Cube((-7.5, 14.0, 10.0), (7.5, 27.0, 17.0)),             # pecho
        Cube((-5.0, 16.0, 16.0), (5.0, 24.0, 22.0)),             # cuello
    ] + plates(back, 1))
    head = Bone("head", (0, 21, 20), "body", [
        Cube((-4.0, 16.0, 18.0), (4.0, 23.0, 24.0)),
        Cube((-3.5, 15.0, 23.0), (3.5, 21.0, 30.0), mouth="down"),
        Cube((-2.5, 14.5, 30.0), (2.5, 18.5, 33.0), tag="beak"),
    ])
    jaw = Bone("jaw", (0, 15.5, 25), "head", [Cube((-3.0, 13.5, 25.0), (3.0, 15.5, 32.5), mouth="up")])
    spike_r = [
        Cube((2.5, 28.0, -47.0), (10.0, 29.5, -45.5), tag="horn", tip="x", rot=("z", 22.5, (2.5, 28.75, -46.25))),
        Cube((2.0, 28.5, -51.0), (9.0, 29.75, -49.5), tag="horn", tip="x", rot=("z", 22.5, (2.0, 29.1, -50.25))),
    ]
    hind_r = [
        Cube((4.5, 12.0, -13.0), (12.0, 30.0, -1.0), stripes="y"),
        Cube((5.5, 2.0, -11.0), (11.0, 13.0, -4.0), stripes="y"),
        Cube((5.0, 0.0, -12.0), (11.5, 2.5, -3.0)),
        Cube((5.5, 0.0, -3.0), (11.0, 1.5, -2.0), tag="claw"),
    ]
    front_r = [
        Cube((5.0, 8.0, 10.0), (9.5, 21.0, 16.0), stripes="y"),
        Cube((5.5, 2.0, 11.0), (9.0, 9.0, 15.0), stripes="y"),
        Cube((5.0, 0.0, 10.5), (9.5, 2.0, 16.0)),
    ]
    bones = [
        body, head, jaw,
        Bone("tail1", (0, 32, -13), "body", [Cube((-6.5, 24.0, -30.0), (6.5, 34.0, -12.0))]
             + plates([(-17.0, 33.0, 7.0 * k), (-23.5, 31.5, 5.0 * k)], -1)),
        Bone("tail2", (0, 29, -29), "tail1", [
            Cube((-4.0, 25.0, -42.0), (4.0, 31.0, -28.0)),
            Cube((-2.5, 26.0, -52.0), (2.5, 30.0, -41.0)),
        ] + spike_r + mirror(spike_r)),
        Bone("leg_fr", (7, 20, 13), None, front_r),
        Bone("leg_fl", (-7, 20, 13), None, mirror(front_r)),
        Bone("leg_br", (8, 24, -7), None, hind_r),
        Bone("leg_bl", (-8, 24, -7), None, mirror(hind_r)),
    ]
    return Rig(bones, face(eye=(26, 27.5, 18.5, 20), pupil=(26.75, 27.5, 18.5, 20)), pattern="bands")


def baby():
    hind_r = [Cube((2.5, 0.0, -6.5), (6.5, 10.0, -1.0), stripes="y")]
    front_r = [Cube((2.5, 0.0, 2.0), (5.5, 8.0, 6.0), stripes="y")]
    bones = [
        Bone("body", (0, 9, 0), None, [
            Cube((-5.0, 5.0, -7.0), (5.0, 13.0, 6.0)),
            Cube((-4.5, 11.0, -6.0), (4.5, 14.5, 0.0)),
        ] + plates([(3.0, 12.5, 2.0), (-1.5, 14.0, 3.0), (-5.0, 14.0, 2.5)], 1)),
        Bone("head", (0, 9, 6), "body", [
            Cube((-3.0, 6.0, 5.0), (3.0, 12.0, 11.5), mouth="down"),
            Cube((-2.0, 6.0, 11.5), (2.0, 9.5, 14.0), tag="beak"),
        ]),
        Bone("jaw", (0, 6.5, 8), "head", [Cube((-2.5, 5.0, 8.0), (2.5, 6.5, 13.5), mouth="up")]),
        Bone("tail1", (0, 11, -6.5), "body", [Cube((-3.0, 7.5, -14.0), (3.0, 12.0, -6.0))]),
        Bone("tail2", (0, 10, -13.5), "tail1", [Cube((-1.75, 8.0, -20.0), (1.75, 11.0, -13.0))]),
        Bone("leg_fr", (3.5, 8, 4), None, front_r),
        Bone("leg_fl", (-3.5, 8, 4), None, mirror(front_r)),
        Bone("leg_br", (4.5, 9, -4), None, hind_r),
        Bone("leg_bl", (-4.5, 9, -4), None, mirror(hind_r)),
    ]
    return Rig(bones, face(eye=(8, 10.5, 9, 11.5), pupil=(9, 10.5, 9, 11), highlight=(9.75, 10.5, 10.75, 11.5)),
               pattern="spots")


def saddle():
    """Montura entre las placas: asiento bajo con respaldo acolchado y protecciones de cuero."""
    side_r = [
        Cube((9.0, 24.0, 3.0), (9.5, 32.5, 10.5), tag="cloth"),
        Cube((9.0, 13.5, 5.5), (9.4, 24.0, 7.5), tag="strap"),
        Cube((9.35, 17.0, 5.75), (9.7, 18.5, 7.25), tag="metal"),
    ]
    return Rig([Bone("saddle", (0, 26, 0), "body", [
        Cube((-9.0, 32.0, 3.5), (9.0, 32.5, 11.0), tag="cloth"),
        Cube((-5.5, 32.5, 4.0), (5.5, 34.0, 10.0), tag="saddle"),
        Cube((-5.0, 34.0, 3.75), (5.0, 37.0, 5.0), tag="saddle_dark"),
        Cube((-1.5, 34.0, 8.75), (1.5, 35.75, 10.0), tag="saddle_dark"),
        Cube((-9.0, 13.1, 5.5), (9.0, 13.5, 7.5), tag="strap"),
    ] + side_r + mirror(side_r))])


VARIANTS = {
    "oliva":   dict(name="Oliva", weight=30, base=(118, 126, 82), stripe=(84, 92, 56), belly=(186, 186, 140),
                    dark=(62, 68, 42), iris=(220, 170, 60), pupil=(16, 12, 10), accent=(196, 70, 44),
                    accent2=(236, 170, 70)),
    "pardo":   dict(name="Pardo", weight=26, base=(140, 112, 84), stripe=(102, 80, 58), belly=(200, 182, 150),
                    dark=(76, 60, 44), iris=(214, 162, 60), pupil=(16, 12, 10), accent=(220, 120, 40),
                    accent2=(250, 210, 110)),
    "gris":    dict(name="Gris", weight=22, base=(116, 120, 122), stripe=(84, 88, 92), belly=(186, 188, 186),
                    dark=(62, 66, 70), iris=(222, 190, 70), pupil=(16, 12, 10), accent=(70, 120, 170),
                    accent2=(170, 210, 230)),
    "rojizo":  dict(name="Rojizo", weight=19, base=(150, 98, 70), stripe=(110, 66, 46), belly=(208, 176, 140),
                    dark=(86, 54, 38), iris=(232, 196, 70), pupil=(16, 12, 10), accent=(236, 200, 80),
                    accent2=(255, 240, 170)),
    "albino":  dict(name="Albino", weight=3, base=(232, 226, 214), stripe=(208, 200, 188), belly=(246, 242, 234),
                    dark=(198, 190, 178), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(236, 150, 164),
                    accent2=(250, 226, 226)),
}

SPECIES = dict(
    id="stegosaurus", name="Stegosaurus", scale=0.85,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    saddle_colors=dict(saddle=(120, 80, 50), saddle_dark=(78, 52, 32), strap=(60, 44, 30), metal=(186, 186, 190),
                       cloth=(90, 110, 60), trim=(220, 196, 120)),
    egg=dict(k=1.3, shell=(200, 196, 170), spot=(120, 112, 80), crack=(54, 48, 38)),
    variants=VARIANTS,
)

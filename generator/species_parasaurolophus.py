"""Parasaurolophus: herbivoro de pico de pato con una cresta en tubo. Presa: anda en manada y huye;
cuando uno se asusta trompetea y sale corriendo toda la manada. Se modela a escala 1 y se muestra a 0.85.
"""
from geometry import Cube, Bone, Rig, mirror, face


def adult(male=False):
    crest_back = 10.0 if male else 15.0
    body = Bone("body", (0, 26, 0), None, [
        Cube((-9.0, 14.0, -16.0), (9.0, 32.0, 12.0)),            # torso
        Cube((-8.5, 26.0, -16.0), (8.5, 35.0, -2.0)),            # cadera
        Cube((-8.0, 16.0, 10.0), (8.0, 30.0, 18.0)),             # hombros
        Cube((-6.0, 20.0, 17.0), (6.0, 30.0, 23.0)),             # base del cuello
    ])
    head = Bone("head", (0, 27, 21), "body", [
        Cube((-4.5, 24.0, 19.0), (4.5, 40.0, 26.0), rot=("x", 22.5, (0, 27, 22)), stripes="y"),  # cuello
        Cube((-4.5, 37.0, 25.0), (4.5, 44.0, 33.0), mouth="down"),               # craneo
        Cube((-4.0, 36.0, 33.0), (4.0, 41.0, 40.0), mouth="down"),               # hocico
        Cube((-4.5, 36.0, 39.0), (4.5, 38.5, 43.0), tag="beak"),                  # pico de pato
        Cube((-1.5, 42.0, crest_back), (1.5, 45.0, 30.0), tag="crest", rot=("x", 22.5, (0, 43.5, 30))),  # cresta
    ])
    jaw = Bone("jaw", (0, 37.5, 28), "head", [
        Cube((-3.75, 34.5, 28.0), (3.75, 37.5, 40.0), mouth="up"),
        Cube((-4.0, 34.5, 39.0), (4.0, 36.5, 42.5), tag="beak"),
    ])
    hind_r = [
        Cube((4.5, 12.0, -15.0), (12.5, 30.0, -3.0), stripes="y"),
        Cube((5.5, 2.0, -12.0), (11.0, 13.0, -6.0), stripes="y"),
        Cube((5.0, 0.0, -13.0), (11.5, 2.5, -4.0)),
        Cube((5.5, 0.0, -4.0), (11.0, 1.5, -3.0), tag="claw"),
    ]
    front_r = [
        Cube((5.0, 10.0, 11.0), (10.0, 24.0, 17.0), stripes="y"),
        Cube((5.5, 2.0, 12.0), (9.5, 11.0, 16.0), stripes="y"),
        Cube((5.0, 0.0, 11.5), (10.0, 2.0, 17.0)),
    ]
    bones = [
        body, head, jaw,
        Bone("tail1", (0, 30, -15), "body", [Cube((-7.0, 22.0, -32.0), (7.0, 33.0, -14.0))]),
        Bone("tail2", (0, 28, -31), "tail1", [
            Cube((-5.0, 23.0, -45.0), (5.0, 31.0, -30.0)),
            Cube((-3.0, 24.5, -54.0), (3.0, 29.5, -44.0)),
        ]),
        Bone("leg_fr", (7, 22, 14), None, front_r),
        Bone("leg_fl", (-7, 22, 14), None, mirror(front_r)),
        Bone("leg_br", (8, 24, -9), None, hind_r),
        Bone("leg_bl", (-8, 24, -9), None, mirror(hind_r)),
    ]
    return Rig(bones, face(eye=(28, 30.5, 40, 42), pupil=(29.25, 30.5, 40, 42), brow=(27.5, 31, 42, 43)))


def baby():
    hind_r = [Cube((2.5, 0.0, -7.0), (6.5, 10.0, -1.5), stripes="y"),
              Cube((2.75, 0.0, -1.5), (6.25, 1.0, -0.75), tag="claw")]
    front_r = [Cube((2.5, 0.0, 2.0), (5.5, 8.0, 6.0), stripes="y")]
    bones = [
        Bone("body", (0, 9, 0), None, [
            Cube((-5.0, 5.0, -7.0), (5.0, 13.0, 6.5)),
            Cube((-4.5, 11.0, -6.5), (4.5, 14.0, -1.0)),
        ]),
        Bone("head", (0, 10, 6), "body", [
            Cube((-2.25, 8.5, 5.0), (2.25, 15.0, 8.5), rot=("x", 22.5, (0, 10, 6.5)), stripes="y"),
            Cube((-3.25, 13.5, 7.5), (3.25, 19.0, 13.5), mouth="down"),
            Cube((-2.75, 13.0, 13.5), (2.75, 16.5, 17.0), mouth="down"),
            Cube((-3.0, 13.0, 16.5), (3.0, 14.75, 18.5), tag="beak"),
            Cube((-0.75, 18.0, 6.5), (0.75, 19.75, 11.0), tag="crest"),
        ]),
        Bone("jaw", (0, 13.5, 10), "head", [Cube((-2.5, 11.75, 10.0), (2.5, 13.5, 18.0), mouth="up")]),
        Bone("tail1", (0, 11, -6.5), "body", [Cube((-3.5, 7.5, -14.0), (3.5, 12.5, -6.0))]),
        Bone("tail2", (0, 10, -13.5), "tail1", [Cube((-2.25, 8.0, -21.0), (2.25, 11.5, -13.0))]),
        Bone("leg_fr", (3.5, 8, 4), None, front_r),
        Bone("leg_fl", (-3.5, 8, 4), None, mirror(front_r)),
        Bone("leg_br", (4.5, 9, -4), None, hind_r),
        Bone("leg_bl", (-4.5, 9, -4), None, mirror(hind_r)),
    ]
    return Rig(bones, face(eye=(10, 12.5, 15.5, 18), pupil=(11, 12.5, 15.5, 17.5), highlight=(11.75, 12.5, 17, 18)),
               pattern="spots")


def saddle():
    """Montura de viaje: manta a rayas, asiento con respaldo y un cuerno de llamada colgado al costado."""
    side_r = [
        Cube((9.0, 24.0, -1.0), (9.5, 32.5, 9.0), tag="cloth"),
        Cube((9.0, 13.5, 4.0), (9.4, 24.0, 6.0), tag="strap"),
        Cube((9.35, 17.0, 4.25), (9.7, 18.5, 5.75), tag="metal"),
    ]
    horn = [
        Cube((9.5, 26.0, 0.0), (11.0, 27.5, 6.0), tag="spike", tip="z"),        # cuerno de llamada
        Cube((9.4, 27.5, 2.5), (9.8, 30.0, 3.25), tag="rope"),
    ]
    return Rig([Bone("saddle", (0, 26, 0), "body", [
        Cube((-9.0, 32.0, -2.0), (9.0, 32.5, 10.0), tag="cloth"),
        Cube((-6.0, 32.5, 0.0), (6.0, 34.0, 8.5), tag="saddle"),
        Cube((-5.0, 34.0, -0.5), (5.0, 37.0, 1.0), tag="saddle_dark"),
        Cube((-1.75, 34.0, 7.0), (1.75, 36.0, 8.5), tag="saddle_dark"),
        Cube((-9.0, 13.1, 4.0), (9.0, 13.5, 6.0), tag="strap"),
    ] + side_r + mirror(side_r) + horn)])


# base, rayas, panza, oscuro, iris, pupila, acento (cresta), acento2 (punta de la cresta)
VARIANTS = {
    "verde":   dict(name="Verde", weight=30, base=(104, 132, 84), stripe=(70, 96, 58), belly=(196, 196, 150),
                    dark=(56, 72, 46), iris=(222, 170, 50), pupil=(16, 12, 10), accent=(220, 150, 40),
                    accent2=(250, 220, 120)),
    "pardo":   dict(name="Pardo", weight=26, base=(146, 116, 84), stripe=(110, 84, 58), belly=(210, 190, 156),
                    dark=(80, 62, 44), iris=(214, 162, 60), pupil=(16, 12, 10), accent=(196, 64, 44),
                    accent2=(240, 170, 90)),
    "azul":    dict(name="Azul", weight=20, base=(96, 112, 136), stripe=(66, 80, 104), belly=(188, 194, 204),
                    dark=(50, 58, 74), iris=(226, 196, 70), pupil=(16, 12, 10), accent=(232, 110, 50),
                    accent2=(250, 200, 110)),
    "rojizo":  dict(name="Rojizo", weight=21, base=(152, 92, 66), stripe=(112, 62, 44), belly=(214, 180, 144),
                    dark=(84, 50, 36), iris=(232, 196, 70), pupil=(16, 12, 10), accent=(250, 210, 90),
                    accent2=(255, 244, 190)),
    "albino":  dict(name="Albino", weight=3, base=(234, 228, 216), stripe=(210, 202, 190), belly=(246, 242, 234),
                    dark=(200, 192, 180), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(236, 150, 164),
                    accent2=(250, 226, 226)),
}

SPECIES = dict(
    id="parasaurolophus", name="Parasaurolophus", scale=0.85,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    saddle_colors=dict(saddle=(140, 96, 60), saddle_dark=(92, 62, 40), strap=(70, 52, 36), metal=(206, 176, 96),
                       cloth=(196, 150, 60), trim=(120, 40, 36), rope=(196, 170, 120), spike=(230, 218, 190)),
    egg=dict(k=1.25, shell=(206, 214, 190), spot=(110, 132, 92), crack=(56, 50, 40)),
    variants=VARIANTS,
)

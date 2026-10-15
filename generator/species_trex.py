"""Tyrannosaurus rex: bipedo gigante. Salvaje caza jugadores y ruge (los asusta: lentitud + oscuridad).
Solo se domestica criandolo desde el huevo; adulto se monta y ruge con la barra espaciadora.

Se modela a escala 1 y el plugin lo muestra a SPECIES['scale'] (1.15): ~3,3 bloques de alto y
~7,3 de largo. Las patas van en dos huesos (muslo + canilla) por el limite de 24 px.
"""
from geometry import Cube, Bone, Rig, mirror, face


def adult(male=False):
    arm_r = [
        Cube((7.0, 21.0, 15.0), (8.5, 25.5, 18.0)),                  # brazo
        Cube((6.75, 18.5, 16.5), (8.25, 21.5, 21.0)),                # antebrazo
        Cube((6.9, 17.5, 20.0), (8.1, 18.5, 21.5), tag="claw"),       # dos dedos
    ]
    body = Bone("body", (0, 30, 0), None, [
        Cube((-8.0, 22.0, -10.0), (8.0, 36.0, 2.0)),                 # cadera
        Cube((-9.0, 17.0, 0.0), (9.0, 35.0, 13.0)),                  # torso
        Cube((-7.5, 19.0, 12.0), (7.5, 33.5, 20.0)),                 # pecho
        Cube((-6.0, 25.0, 18.0), (6.0, 35.0, 23.5)),                 # base del cuello
    ] + arm_r + mirror(arm_r))

    head_cubes = [
        Cube((-5.0, 27.0, 18.0), (5.0, 40.0, 26.0), rot=("x", 22.5, (0, 32, 22)), stripes="y"),  # cuello
        Cube((-6.0, 36.0, 24.0), (6.0, 45.0, 35.0), mouth="down"),             # craneo
        Cube((-6.75, 37.5, 25.0), (6.75, 42.0, 30.0)),                         # musculos de la mandibula
        Cube((-4.75, 36.0, 35.0), (4.75, 43.0, 45.0), mouth="down"),           # hocico
        Cube((-6.5, 43.5, 27.0), (6.5, 45.5, 32.5), tag="dark"),               # cejas
        Cube((-4.5, 33.5, 23.0), (4.5, 36.0, 31.0)),                           # garganta
    ]
    if male:
        # cuernitos sobre los ojos y rugosidades en el hocico, de color
        horn_r = [Cube((3.5, 45.5, 29.5), (5.5, 47.0, 31.5), tag="accent")]
        head_cubes += horn_r + mirror(horn_r) + [Cube((-1.0, 43.0, 37.0), (1.0, 44.0, 42.0), tag="accent")]

    thigh_r = [Cube((4.0, 18.0, -12.0), (12.0, 34.0, 3.0), stripes="y")]
    shin_r = [
        Cube((5.25, 7.0, -9.0), (10.75, 20.0, -3.0), rot=("x", 22.5, (8, 13.5, -6)), stripes="y"),     # canilla
        Cube((5.75, 2.0, -12.25), (10.25, 9.0, -8.25), rot=("x", -22.5, (8, 5.5, -10.25)), stripes="y"),  # metatarso
        Cube((5.5, 0.0, -11.0), (10.5, 2.5, -1.0)),                                                   # pie
        Cube((5.75, 0.0, -1.0), (10.25, 1.5, 0.5), tag="claw"),                                       # garras
    ]
    bones = [
        body,
        Bone("head", (0, 32, 21), "body", head_cubes),
        Bone("jaw", (0, 36.5, 27), "head", [
            Cube((-5.0, 33.0, 27.0), (5.0, 36.5, 44.0), mouth="up"),
            Cube((-4.0, 32.25, 30.0), (4.0, 33.0, 42.0)),            # menton
        ]),
        Bone("tail1", (0, 31, -9), "body", [
            Cube((-7.0, 23.5, -26.0), (7.0, 35.5, -8.0)),
            Cube((-5.5, 25.0, -33.0), (5.5, 34.0, -25.0)),
        ]),
        Bone("tail2", (0, 29.5, -32), "tail1", [
            Cube((-4.25, 26.0, -44.0), (4.25, 33.0, -32.0)),
            Cube((-3.0, 27.0, -51.0), (3.0, 31.5, -43.0)),
            Cube((-1.75, 27.75, -56.0), (1.75, 30.5, -50.0)),
        ]),
        Bone("leg_r", (7.5, 30, -4), None, thigh_r),
        Bone("shin_r", (8, 19, -4), "leg_r", shin_r),
        Bone("leg_l", (-7.5, 30, -4), None, mirror(thigh_r)),
        Bone("shin_l", (-8, 19, -4), "leg_l", mirror(shin_r)),
    ]
    return Rig(bones, face(eye=(30, 32.5, 41, 43), pupil=(31.25, 32, 41, 43), eye_stripe=(27, 30, 41.5, 42.5),
                           teeth_y=(36, 37), teeth_z=34, jaw_teeth_y=35.5, jaw_teeth_z=30,
                           nostril=(43, 41, 42), mouth_teeth=True), pattern="bands")


def baby():
    """Cria: cabezona, patas cortas, brazitos ridiculos."""
    arm_r = [
        Cube((3.0, 8.0, 5.0), (3.6, 9.5, 6.0)),
        Cube((2.9, 7.25, 5.5), (3.5, 8.0, 7.0)),
    ]
    leg_r = [
        Cube((2.0, 5.5, -4.5), (5.5, 11.5, 1.0), stripes="y"),
        Cube((2.5, 1.5, -3.5), (4.5, 6.0, -1.5), stripes="y"),
        Cube((2.0, 0.0, -3.5), (5.0, 1.5, 1.5)),
        Cube((2.25, 0.0, 1.5), (4.75, 0.75, 2.25), tag="claw"),
    ]
    bones = [
        Bone("body", (0, 10, 0), None, [
            Cube((-3.5, 7.0, -4.5), (3.5, 13.0, 4.0)),
            Cube((-3.0, 7.5, 3.5), (3.0, 12.5, 6.5)),
        ] + arm_r + mirror(arm_r)),
        Bone("head", (0, 11.5, 5.5), "body", [
            Cube((-2.25, 10.0, 4.5), (2.25, 15.0, 8.0), rot=("x", 22.5, (0, 11.5, 6)), stripes="y"),
            Cube((-3.75, 14.0, 6.5), (3.75, 20.0, 13.0), mouth="down"),
            Cube((-2.75, 14.0, 13.0), (2.75, 18.0, 16.5), mouth="down"),
        ]),
        Bone("jaw", (0, 14, 9), "head", [Cube((-2.5, 12.5, 9.0), (2.5, 14.0, 16.0), mouth="up")]),
        Bone("tail1", (0, 11, -4), "body", [Cube((-2.5, 8.0, -11.0), (2.5, 12.5, -3.5))]),
        Bone("tail2", (0, 10, -10.5), "tail1", [
            Cube((-1.5, 8.5, -17.0), (1.5, 11.5, -10.5)),
            Cube((-0.75, 9.25, -20.0), (0.75, 10.75, -16.5)),
        ]),
        Bone("leg_r", (3.5, 10.5, -1.5), None, leg_r),
        Bone("leg_l", (-3.5, 10.5, -1.5), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(9.5, 12, 16.5, 19), pupil=(10.5, 12, 16.5, 18.5), highlight=(11, 12, 18, 19),
                           nostril=(15.5, 16, 17)), pattern="spots")


def saddle():
    """Montura de guerra: cuero oscuro, placas de hierro en la grupa y los hombros, puas de hueso y manija."""
    spikes = [Cube((x - 0.75, 37.0, -4.5), (x + 0.75, 40.5, -3.0), tag="spike", tip="y",
                   rot=("x", -22.5, (x, 37.0, -3.75))) for x in (-4.0, 0.0, 4.0)]
    side_r = [
        Cube((9.0, 27.0, 4.0), (9.5, 35.5, 12.0), tag="cloth"),                  # manta colgando
        Cube((9.0, 17.0, 6.5), (9.4, 27.0, 8.5), tag="strap"),                   # cincha
        Cube((9.35, 21.0, 6.75), (9.75, 22.5, 8.25), tag="metal"),               # argolla
        Cube((7.5, 27.5, 12.5), (8.25, 34.0, 18.5), tag="plate"),                # hombrera
    ]
    return Rig([Bone("saddle", (0, 30, 0), "body", [
        Cube((-8.5, 35.0, 0.5), (8.5, 35.5, 12.5), tag="cloth"),                 # manta
        Cube((-5.5, 35.5, 2.5), (5.5, 36.75, 10.5), tag="saddle"),                             # asiento
        Cube((-4.5, 36.75, 2.5), (4.5, 39.0, 4.0), tag="saddle_dark"),           # respaldo
        Cube((-7.0, 36.0, -6.0), (7.0, 37.0, 1.5), tag="plate"),                 # placa de la grupa
        Cube((-6.5, 33.5, 13.0), (6.5, 34.5, 19.0), tag="plate"),                # placa del pecho
        Cube((-1.5, 36.75, 9.0), (1.5, 38.5, 10.5), tag="saddle_dark"),          # pomo
        Cube((-2.5, 38.5, 9.25), (2.5, 39.25, 10.25), tag="metal"),              # manija
        Cube((-9.0, 16.6, 6.5), (9.0, 17.0, 8.5), tag="strap"),                  # cincha por la panza
    ] + spikes + side_r + mirror(side_r))])


# base, rayas, panza, oscuro, iris, pupila, acento (cuernitos del macho), acento2
VARIANTS = {
    "pardo":   dict(name="Pardo", weight=30, base=(120, 96, 70), stripe=(78, 60, 44), belly=(186, 164, 128),
                    dark=(64, 50, 38), iris=(222, 150, 40), pupil=(16, 12, 10), accent=(170, 60, 40),
                    accent2=(220, 150, 60)),
    "oscuro":  dict(name="Oscuro", weight=25, base=(74, 70, 64), stripe=(46, 44, 40), belly=(150, 140, 122),
                    dark=(36, 34, 32), iris=(230, 120, 30), pupil=(16, 12, 10), accent=(190, 44, 36),
                    accent2=(230, 120, 40)),
    "verde":   dict(name="Verde musgo", weight=20, base=(92, 104, 66), stripe=(58, 66, 42), belly=(176, 172, 132),
                    dark=(48, 54, 36), iris=(226, 170, 40), pupil=(16, 12, 10), accent=(200, 120, 40),
                    accent2=(236, 196, 80)),
    "rojizo":  dict(name="Rojizo", weight=17, base=(140, 84, 58), stripe=(92, 50, 34), belly=(204, 170, 132),
                    dark=(80, 46, 32), iris=(236, 190, 60), pupil=(16, 12, 10), accent=(60, 44, 36),
                    accent2=(30, 24, 20)),
    "albino":  dict(name="Albino", weight=3, base=(230, 224, 212), stripe=(206, 198, 186), belly=(244, 240, 232),
                    dark=(196, 188, 176), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(232, 150, 162),
                    accent2=(250, 236, 226)),
}

SPECIES = dict(
    id="trex", name="Tyrannosaurus rex", scale=1.15,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    saddle_colors=dict(saddle=(58, 40, 34), saddle_dark=(36, 26, 22), strap=(40, 30, 26), metal=(150, 150, 158),
                       plate=(112, 114, 122), spike=(226, 214, 186), cloth=(122, 24, 24), trim=(36, 30, 28)),
    egg=dict(k=1.5, shell=(214, 204, 186), spot=(104, 84, 66), crack=(50, 40, 32)),
    variants=VARIANTS,
)

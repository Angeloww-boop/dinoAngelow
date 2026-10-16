"""Spinosaurus: el gran pescador. Bipedo anfibio con una vela enorme en el lomo, hocico de cocodrilo y una
cola aplanada como remo. Camina en tierra y nada rapido; come pescado. Se domestica con pescado y se monta
(tambien en el agua). Se modela a escala 1 y se muestra a 1.2: ~4 bloques de alto con la vela y ~7,4 de largo.
La vela es un hueso aparte (sail) porque llega a mas de 24 px del cuerpo.
"""
from geometry import Cube, Bone, Rig, mirror, face
from species_pteranodon import scaled


def adult(male=False):
    arm_r = [
        Cube((6.5, 19.0, 14.0), (8.5, 24.0, 17.0)),                   # brazo
        Cube((6.25, 15.5, 15.5), (8.0, 19.5, 20.0)),                  # antebrazo
        Cube((6.5, 14.5, 19.0), (7.75, 15.5, 21.5), tag="claw"),       # garra del pulgar
    ]
    body = Bone("body", (0, 28, 0), None, [
        Cube((-7.0, 21.0, -10.0), (7.0, 33.0, 2.0)),                  # cadera
        Cube((-8.0, 17.0, 0.0), (8.0, 32.0, 14.0)),                   # torso
        Cube((-6.5, 19.0, 13.0), (6.5, 30.0, 20.0)),                  # pecho
        Cube((-5.0, 23.0, 18.0), (5.0, 31.0, 23.0)),                  # base del cuello
    ] + arm_r + mirror(arm_r))
    top = 55.0 if male else 52.0
    sail = Bone("sail", (0, 34, 2), "body", [
        Cube((-0.6, 31.0, -8.0), (0.6, top - 4.0, 12.0), tag="crest", stripes="y"),   # vela
        Cube((-0.5, top - 4.0, -4.0), (0.5, top, 8.0), tag="crest"),                  # joroba de arriba
        Cube((-0.5, 31.0, -12.0), (0.5, 42.0, -8.0), tag="crest"),                    # baja hacia la cola
        Cube((-0.5, 31.0, 12.0), (0.5, 43.0, 16.0), tag="crest"),                     # y hacia el cuello
    ])
    head_cubes = [
        Cube((-4.0, 24.0, 19.0), (4.0, 34.0, 27.0), rot=("x", 22.5, (0, 29, 23)), stripes="y"),  # cuello
        Cube((-4.0, 31.0, 25.0), (4.0, 38.0, 33.0), mouth="down"),             # craneo
        Cube((-2.75, 32.0, 33.0), (2.75, 36.0, 41.5), mouth="down"),           # hocico largo
        Cube((-3.25, 31.5, 41.5), (3.25, 35.5, 45.0), mouth="down"),           # punta (roseta de dientes)
        Cube((-4.25, 36.5, 26.0), (4.25, 38.25, 30.0), tag="dark"),            # cejas
    ]
    head_cubes.append(Cube((-1.0, 38.0, 27.0), (1.0, 40.5 if male else 39.25, 31.0), tag="accent"))   # crestita
    thigh_r = [Cube((3.5, 17.0, -11.0), (11.0, 31.0, 2.0), stripes="y")]
    shin_r = [
        Cube((4.5, 7.0, -8.0), (9.5, 19.0, -2.5), rot=("x", 22.5, (7, 13, -5.25)), stripes="y"),      # canilla
        Cube((5.0, 2.0, -11.25), (9.0, 8.5, -7.5), rot=("x", -22.5, (7, 5.25, -9.4)), stripes="y"),   # metatarso
        Cube((4.75, 0.0, -10.0), (9.25, 2.0, -0.5)),                                                  # pie (palmeado)
        Cube((5.0, 0.0, -0.5), (9.0, 1.25, 1.0), tag="claw"),
    ]
    bones = [
        body, sail,
        Bone("head", (0, 29, 21), "body", head_cubes),
        Bone("jaw", (0, 32, 27), "head", [
            Cube((-3.5, 29.5, 27.0), (3.5, 32.0, 40.0), mouth="up"),
            Cube((-3.0, 29.5, 40.0), (3.0, 32.0, 44.5), mouth="up"),
        ]),
        Bone("tail1", (0, 29, -9), "body", [
            Cube((-6.0, 22.5, -26.0), (6.0, 33.0, -8.0)),
            Cube((-4.5, 24.0, -33.0), (4.5, 32.0, -25.0)),
        ]),
        Bone("tail2", (0, 28, -32), "tail1", [
            Cube((-3.0, 24.5, -45.0), (3.0, 31.5, -32.0)),
            Cube((-2.0, 25.5, -54.0), (2.0, 30.5, -44.0)),
            Cube((-0.5, 31.5, -52.0), (0.5, 35.0, -36.0), tag="crest"),          # aleta de arriba (remo)
            Cube((-0.5, 21.5, -50.0), (0.5, 24.5, -40.0), tag="crest"),          # y de abajo
        ]),
        Bone("leg_r", (6.5, 28, -4), None, thigh_r),
        Bone("shin_r", (7, 18, -4), "leg_r", shin_r),
        Bone("leg_l", (-6.5, 28, -4), None, mirror(thigh_r)),
        Bone("shin_l", (-7, 18, -4), "leg_l", mirror(shin_r)),
    ]
    return Rig(bones, face(eye=(29, 31, 35, 36.5), pupil=(29.75, 30.75, 35, 36.5), eye_stripe=(26.5, 29, 35.25, 36),
                           teeth_y=(32, 33), teeth_z=33, jaw_teeth_y=31.25, jaw_teeth_z=30,
                           nostril=(44, 34, 35), mouth_teeth=True), pattern="bands")


def baby():
    rig = scaled(adult(False), 0.42, 1.35, face_keys=("eye", "pupil", "eye_stripe"))
    e = rig.face["eye"]
    rig.face["highlight"] = (e[1] - 0.5, e[1], e[3] - 0.5, e[3])
    for key in ("teeth_y", "jaw_teeth_y", "nostril"):
        rig.face[key] = None
    rig.face["mouth_teeth"] = False
    rig.pattern = "spots"
    return rig


def saddle():
    """Montura de pescador: asiento delante de la vela, soga y una red colgando, boya de madera."""
    side_r = [
        Cube((8.0, 22.0, 15.0), (8.5, 31.0, 17.5), tag="strap"),                 # cincha del pecho
        Cube((8.3, 25.0, 15.25), (8.8, 26.5, 17.25), tag="metal"),
        Cube((8.0, 24.0, 2.0), (8.6, 30.5, 9.0), tag="rope"),                    # red
        Cube((8.4, 21.5, 4.0), (9.6, 24.0, 7.0), tag="wood"),                    # boya
    ]
    return Rig([Bone("saddle", (0, 28, 0), "body", [
        Cube((-7.0, 31.0, 13.0), (7.0, 31.5, 21.0), tag="cloth"),                 # manta
        Cube((-4.5, 31.5, 14.0), (4.5, 32.75, 20.0), tag="saddle"),               # asiento
        Cube((-3.5, 32.75, 14.0), (3.5, 34.5, 15.25), tag="saddle_dark"),         # respaldo contra la vela
        Cube((-1.25, 32.75, 18.75), (1.25, 34.5, 20.0), tag="saddle_dark"),       # pomo
        Cube((-8.5, 16.6, 15.0), (8.5, 17.0, 17.5), tag="strap"),                 # cincha por la panza
    ] + side_r + mirror(side_r))])


# base, rayas, panza, oscuro, iris, pupila, acento (vela abajo), acento2 (vela arriba)
VARIANTS = {
    "pantano": dict(name="Pantano", weight=30, base=(98, 104, 74), stripe=(66, 72, 50), belly=(178, 170, 132),
                    dark=(50, 54, 38), iris=(226, 170, 40), pupil=(16, 12, 10), accent=(150, 64, 44),
                    accent2=(224, 128, 56)),
    "rio":     dict(name="Río", weight=26, base=(96, 110, 120), stripe=(66, 78, 88), belly=(190, 192, 184),
                    dark=(46, 54, 62), iris=(230, 190, 60), pupil=(16, 12, 10), accent=(56, 96, 140),
                    accent2=(110, 170, 200)),
    "arcilla": dict(name="Arcilla", weight=24, base=(146, 104, 76), stripe=(104, 70, 50), belly=(210, 182, 146),
                    dark=(80, 54, 40), iris=(236, 190, 60), pupil=(16, 12, 10), accent=(170, 40, 36),
                    accent2=(236, 170, 70)),
    "ceniza":  dict(name="Ceniza", weight=17, base=(84, 82, 80), stripe=(56, 54, 54), belly=(164, 158, 150),
                    dark=(38, 36, 36), iris=(222, 120, 40), pupil=(16, 12, 10), accent=(196, 170, 60),
                    accent2=(240, 220, 120)),
    "albino":  dict(name="Albino", weight=3, base=(232, 226, 214), stripe=(208, 200, 188), belly=(246, 242, 234),
                    dark=(198, 190, 178), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(236, 150, 164),
                    accent2=(250, 222, 226)),
}

SPECIES = dict(
    id="spinosaurus", name="Spinosaurus", scale=1.2,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    saddle_colors=dict(saddle=(124, 92, 60), saddle_dark=(84, 60, 40), strap=(60, 46, 34), metal=(176, 160, 120),
                       cloth=(30, 96, 110), trim=(220, 200, 140), wood=(150, 110, 64), rope=(198, 176, 120)),
    egg=dict(k=1.4, shell=(196, 200, 182), spot=(86, 104, 90), crack=(46, 50, 44)),
    variants=VARIANTS,
)

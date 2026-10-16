"""Mosasaurus: reptil marino gigante (no es un dinosaurio, pero va en la Dinopedia). Vive en el agua: nada con
la cola y cuatro aletas, y salvaje ataca a los que nadan cerca. En tierra queda varado y se arrastra hasta el
agua. Se domestica con pescado (mejor desde un bote) y se monta bajo el agua. No pone huevos: paria crias vivas,
asi que no tiene nido ni huevo. Se modela a escala 1 y se muestra a 1.5: ~9 bloques de largo.
"""
from geometry import Cube, Bone, Rig, mirror, face
from species_pteranodon import scaled


def adult(male=False):
    body = Bone("body", (0, 10, 0), None, [
        Cube((-7.0, 3.0, -12.0), (7.0, 16.0, 10.0)),                  # torso
        Cube((-6.0, 4.0, 9.0), (6.0, 15.0, 18.0)),                    # pecho
        Cube((-1.0, 16.0, -6.0), (1.0, 17.0, 6.0), tag="dark"),        # cresta del lomo
    ])
    head_cubes = [
        Cube((-5.0, 5.0, 16.0), (5.0, 15.0, 22.0)),                   # cuello
        Cube((-4.5, 8.0, 21.0), (4.5, 15.0, 32.0), mouth="down"),     # craneo
        Cube((-3.25, 8.5, 32.0), (3.25, 13.5, 40.0), mouth="down"),   # hocico
        Cube((-4.75, 13.5, 24.0), (4.75, 15.25, 29.0), tag="dark"),   # cejas
    ]
    if male:
        head_cubes.append(Cube((-0.75, 13.5, 32.0), (0.75, 14.5, 38.0), tag="accent"))   # quilla del hocico
    flip_f = [
        Cube((6.0, 6.0, 8.0), (16.0, 7.5, 14.0)),                     # aleta delantera
        Cube((16.0, 6.25, 8.5), (20.0, 7.25, 12.5), tag="dark"),
    ]
    flip_b = [
        Cube((5.5, 5.0, -11.0), (13.0, 6.5, -5.0)),                   # aleta trasera
        Cube((13.0, 5.25, -10.5), (16.0, 6.25, -6.5), tag="dark"),
    ]
    fin = 18.0 if male else 17.0
    bones = [
        body,
        Bone("head", (0, 11, 17), "body", head_cubes),
        Bone("jaw", (0, 8.5, 22), "head", [
            Cube((-4.0, 5.0, 22.0), (4.0, 8.5, 33.0), mouth="up"),
            Cube((-3.0, 5.5, 33.0), (3.0, 8.5, 39.5), mouth="up"),
        ]),
        Bone("tail1", (0, 10, -11), "body", [
            Cube((-5.5, 4.0, -28.0), (5.5, 15.0, -11.0)),
            Cube((-4.25, 5.0, -34.0), (4.25, 14.0, -27.0)),
        ]),
        Bone("tail2", (0, 9.5, -33), "tail1", [
            Cube((-3.0, 5.5, -46.0), (3.0, 13.0, -33.0)),
            Cube((-2.0, 6.0, -54.0), (2.0, 11.5, -45.0)),
            Cube((-0.75, 11.5, -57.0), (0.75, fin, -50.0), tag="crest"),   # aleta de la cola (lobulo de arriba)
            Cube((-0.75, 1.0, -55.0), (0.75, 6.0, -50.0), tag="crest"),    # y de abajo
        ]),
        Bone("leg_fr", (6, 7, 12), "body", flip_f),
        Bone("leg_fl", (-6, 7, 12), "body", mirror(flip_f)),
        Bone("leg_br", (5.5, 6, -7), "body", flip_b),
        Bone("leg_bl", (-5.5, 6, -7), "body", mirror(flip_b)),
    ]
    return Rig(bones, face(eye=(27, 29, 12, 13.5), pupil=(27.5, 28.5, 12, 13.5), eye_stripe=(25, 27, 12.25, 13),
                           teeth_y=(8, 9.25), teeth_z=24, jaw_teeth_y=7.5, jaw_teeth_z=24,
                           nostril=(39, 11.5, 12.5), mouth_teeth=True), pattern="bands")


def baby():
    rig = scaled(adult(False), 0.4, 1.3, face_keys=("eye", "pupil", "eye_stripe"))
    e = rig.face["eye"]
    rig.face["highlight"] = (e[1] - 0.5, e[1], e[3] - 0.5, e[3])
    for key in ("teeth_y", "jaw_teeth_y", "nostril"):
        rig.face[key] = None
    rig.face["mouth_teeth"] = False
    rig.pattern = "spots"
    return rig


def saddle():
    """Arnes de buceo: asiento chato con manija, correas que dan la vuelta al cuerpo y placas de bronce."""
    side_r = [
        Cube((7.0, 3.5, 3.0), (7.4, 16.0, 5.0), tag="strap"),
        Cube((7.3, 9.0, 3.25), (7.7, 10.5, 4.75), tag="metal"),
        Cube((6.5, 12.0, -3.0), (7.1, 16.0, 2.0), tag="plate"),
    ]
    return Rig([Bone("saddle", (0, 10, 0), "body", [
        Cube((-5.0, 16.0, -4.0), (5.0, 17.25, 5.0), tag="saddle"),           # asiento
        Cube((-4.0, 17.25, -4.0), (4.0, 18.5, -2.75), tag="saddle_dark"),    # respaldo
        Cube((-0.75, 17.25, 3.5), (0.75, 19.5, 4.5), tag="saddle_dark"),     # poste
        Cube((-2.25, 19.5, 3.5), (2.25, 20.25, 4.5), tag="metal"),           # manija
        Cube((-7.0, 2.6, 3.0), (7.0, 3.0, 5.0), tag="strap"),                # por la panza
    ] + side_r + mirror(side_r))])


# base (lomo), rayas, panza, oscuro, iris, pupila, acento (aleta)
VARIANTS = {
    "abismo":  dict(name="Abismo", weight=30, base=(62, 78, 98), stripe=(42, 54, 70), belly=(196, 200, 196),
                    dark=(30, 38, 50), iris=(226, 190, 60), pupil=(16, 12, 10), accent=(36, 50, 66),
                    accent2=(80, 110, 140)),
    "arrecife": dict(name="Arrecife", weight=25, base=(58, 108, 104), stripe=(38, 76, 74), belly=(206, 214, 200),
                     dark=(28, 52, 52), iris=(236, 170, 40), pupil=(16, 12, 10), accent=(220, 120, 60),
                     accent2=(250, 190, 100)),
    "tiburon": dict(name="Tiburón", weight=24, base=(118, 124, 130), stripe=(88, 94, 100), belly=(226, 226, 222),
                    dark=(60, 64, 70), iris=(30, 24, 20), pupil=(10, 8, 8), accent=(70, 76, 84),
                    accent2=(140, 146, 152)),
    "noche":   dict(name="Noche", weight=18, base=(40, 40, 48), stripe=(26, 26, 32), belly=(150, 146, 150),
                    dark=(18, 18, 22), iris=(220, 60, 40), pupil=(16, 12, 10), accent=(110, 40, 120),
                    accent2=(180, 90, 190)),
    "albino":  dict(name="Albino", weight=3, base=(232, 230, 224), stripe=(210, 206, 200), belly=(248, 246, 242),
                    dark=(200, 194, 188), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(236, 150, 164),
                    accent2=(250, 222, 226)),
}

SPECIES = dict(
    id="mosasaurus", name="Mosasaurus", scale=1.5,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    saddle_colors=dict(saddle=(96, 70, 48), saddle_dark=(60, 44, 30), strap=(44, 40, 36), metal=(196, 150, 80),
                       plate=(176, 130, 70)),
    egg=None,
    variants=VARIANTS,
)

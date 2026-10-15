"""Gallimimus: el "avestruz" de los dinosaurios. Presa: huye en manada; domesticado es la montura
mas rapida del juego (no pelea mucho).
"""
from geometry import Cube, Bone, Rig, mirror, face


def adult(male=False):
    arm_r = [
        Cube((3.0, 15.5, 4.5), (4.0, 19.5, 6.5)),
        Cube((3.1, 13.0, 5.5), (3.9, 15.5, 9.0)),
        Cube((3.15, 12.25, 8.0), (3.85, 13.0, 9.75), tag="claw"),
    ]
    body = Bone("body", (0, 22, 0), None, [
        Cube((-3.5, 17.0, -6.0), (3.5, 24.0, 5.0)),               # torso
        Cube((-3.5, 19.0, -8.0), (3.5, 25.0, -3.0)),              # cadera
        Cube((-3.0, 16.5, 4.0), (3.0, 23.0, 8.0)),                # pecho
    ] + arm_r + mirror(arm_r))
    head_cubes = [
        Cube((-1.5, 20.0, 6.0), (1.5, 35.0, 9.0), rot=("x", 45, (0, 22, 7.5)), stripes="y"),   # cuello largo
        Cube((-2.0, 30.0, 15.0), (2.0, 33.5, 19.0), mouth="down"),           # craneo
        Cube((-1.25, 30.0, 19.0), (1.25, 32.5, 23.5), tag="beak"),           # pico
    ]
    if male:
        head_cubes.append(Cube((-0.5, 33.5, 15.0), (0.5, 35.0, 18.0), tag="accent"))   # plumitas en la cabeza
    leg_r = [
        Cube((2.0, 14.0, -5.5), (5.5, 23.0, 1.0), stripes="y"),                                        # muslo
        Cube((2.75, 6.0, -3.5), (4.75, 15.0, -1.0), rot=("x", 22.5, (3.75, 10.5, -2.25)), stripes="y"),  # canilla
        Cube((3.0, 1.0, -5.5), (4.5, 7.0, -3.75), rot=("x", -22.5, (3.75, 4.0, -4.6)), stripes="y"),    # metatarso
        Cube((2.5, 0.0, -4.5), (5.0, 1.0, 1.0)),
        Cube((2.75, 0.0, 1.0), (4.75, 0.6, 1.75), tag="claw"),
    ]
    bones = [
        body,
        Bone("head", (0, 22, 7), "body", head_cubes),
        Bone("jaw", (0, 30.5, 17), "head", [Cube((-1.1, 29.5, 17.0), (1.1, 30.5, 23.0), tag="beak")]),
        Bone("tail1", (0, 23, -7), "body", [Cube((-2.5, 19.5, -18.0), (2.5, 24.0, -6.0))]),
        Bone("tail2", (0, 22, -17.5), "tail1", [
            Cube((-1.75, 20.0, -28.0), (1.75, 23.5, -17.0)),
            Cube((-1.0, 20.5, -35.0), (1.0, 22.5, -27.5)),
        ]),
        Bone("leg_r", (3.5, 21, -2), None, leg_r),
        Bone("leg_l", (-3.5, 21, -2), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(16.5, 18.5, 31.5, 33), pupil=(17.25, 18.5, 31.5, 33), eye_stripe=(15, 16.5, 31.75, 32.75)),
               pattern="spots")


def baby():
    leg_r = [
        Cube((1.5, 5.5, -3.0), (3.75, 10.5, 0.5), stripes="y"),
        Cube((2.0, 1.0, -2.0), (3.25, 6.0, -0.75), stripes="y"),
        Cube((1.5, 0.0, -2.5), (3.75, 1.0, 1.5)),
    ]
    bones = [
        Bone("body", (0, 10, 0), None, [Cube((-2.5, 8.0, -3.5), (2.5, 13.0, 3.5))]),
        Bone("head", (0, 11, 3), "body", [
            Cube((-1.25, 10.0, 2.5), (1.25, 17.0, 5.0), rot=("x", 45, (0, 11, 3.5)), stripes="y"),
            Cube((-2.25, 14.0, 6.5), (2.25, 18.5, 11.0), mouth="down"),
            Cube((-1.0, 14.25, 11.0), (1.0, 16.25, 13.5), tag="beak"),
        ]),
        Bone("jaw", (0, 14.25, 9), "head", [Cube((-0.9, 13.5, 9.0), (0.9, 14.25, 13.0), tag="beak")]),
        Bone("tail1", (0, 11, -3), "body", [Cube((-1.75, 9.0, -9.0), (1.75, 12.0, -2.5))]),
        Bone("tail2", (0, 10.5, -8.5), "tail1", [Cube((-1.0, 9.5, -14.0), (1.0, 11.5, -8.5))]),
        Bone("leg_r", (2.5, 10, -1), None, leg_r),
        Bone("leg_l", (-2.5, 10, -1), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(8, 10, 15.5, 17.5), pupil=(8.75, 10, 15.5, 17), highlight=(9.5, 10, 17, 17.5)),
               pattern="spots")


def saddle():
    """Montura de carrera minima: asiento chico, estribos colgando y riendas cortas."""
    side_r = [
        Cube((3.5, 17.0, 0.5), (3.9, 24.0, 1.75), tag="strap"),
        Cube((3.6, 14.5, 0.25), (4.6, 15.25, 2.0), tag="metal"),          # estribo
        Cube((3.7, 15.25, 0.75), (4.1, 17.0, 1.5), tag="strap"),
    ]
    return Rig([Bone("saddle", (0, 22, 0), "body", [
        Cube((-3.75, 24.0, -5.0), (3.75, 24.5, 3.5), tag="cloth"),
        Cube((-3.0, 24.5, -4.0), (3.0, 25.5, 2.5), tag="saddle"),
        Cube((-2.5, 25.5, -4.25), (2.5, 26.75, -3.0), tag="saddle_dark"),
        Cube((-1.0, 25.5, 1.5), (1.0, 27.0, 2.5), tag="saddle_dark"),
        Cube((-3.5, 16.6, 0.5), (3.5, 17.0, 1.75), tag="strap"),
    ] + side_r + mirror(side_r))])


VARIANTS = {
    "arena":   dict(name="Arena", weight=30, base=(196, 168, 120), stripe=(150, 120, 80), belly=(232, 220, 190),
                    dark=(110, 86, 60), iris=(226, 150, 40), pupil=(16, 12, 10), accent=(200, 70, 40)),
    "gris":    dict(name="Gris", weight=24, base=(140, 140, 136), stripe=(96, 98, 100), belly=(214, 212, 204),
                    dark=(70, 72, 74), iris=(230, 180, 60), pupil=(16, 12, 10), accent=(60, 120, 180)),
    "verde":   dict(name="Verde", weight=22, base=(124, 146, 90), stripe=(84, 104, 60), belly=(210, 210, 160),
                    dark=(62, 78, 44), iris=(222, 170, 50), pupil=(16, 12, 10), accent=(240, 180, 40)),
    "naranja": dict(name="Naranja", weight=21, base=(200, 128, 70), stripe=(150, 86, 46), belly=(236, 206, 166),
                    dark=(110, 66, 38), iris=(240, 210, 80), pupil=(16, 12, 10), accent=(70, 50, 40)),
    "albino":  dict(name="Albino", weight=3, base=(238, 234, 224), stripe=(214, 208, 196), belly=(248, 246, 240),
                    dark=(204, 196, 186), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(236, 150, 164)),
}

SPECIES = dict(
    id="gallimimus", name="Gallimimus", scale=1.0,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    saddle_colors=dict(saddle=(170, 120, 70), saddle_dark=(110, 74, 44), strap=(80, 56, 36), metal=(214, 200, 150),
                       cloth=(40, 110, 120), trim=(230, 220, 190)),
    egg=dict(k=1.05, shell=(240, 232, 214), spot=(180, 150, 110), crack=(70, 56, 44)),
    variants=VARIANTS,
)

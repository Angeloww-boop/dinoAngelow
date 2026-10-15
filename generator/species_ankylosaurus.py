"""Ankylosaurus: tanque acorazado, bajo y ancho, con maza en la cola. Las flechas casi no le hacen
nada y el coletazo tira lejos. Se modela a escala 1 y se muestra a 0.85.
"""
from geometry import Cube, Bone, Rig, mirror, face


def adult(male=False):
    spike = 3.5 if male else 3.0
    side_r = [Cube((12.0, 15.5, z), (12.0 + spike, 17.5, z + 2.5), tag="horn", tip="x") for z in (-11.0, -5.0, 1.0, 7.0)]
    knobs = [Cube((x - 1.0, 28.0, z - 1.0), (x + 1.0, 30.0, z + 1.0), tag="horn", tip="y")
             for x in (-9.0, -3.0, 3.0, 9.0) for z in (-10.0, -4.0, 2.0, 8.0) if not (abs(x) < 5 and -5 < z < 7)]
    body = Bone("body", (0, 20, 0), None, [
        Cube((-12.0, 10.0, -14.0), (12.0, 26.0, 12.0)),          # torso
        Cube((-12.5, 25.0, -13.0), (12.5, 28.5, 11.0)),            # caparazon (lleva el dibujo de la piel)
        Cube((-7.0, 13.0, 11.0), (7.0, 22.0, 17.0)),             # cuello
    ] + knobs + side_r + mirror(side_r))
    horn_r = [
        Cube((5.0, 18.0, 15.0), (5.0 + spike, 20.0, 17.5), tag="horn", tip="x"),     # cuernos de atras de la cabeza
        Cube((5.5, 13.0, 19.0), (5.5 + spike * 0.7, 15.0, 21.0), tag="horn", tip="x"),  # mejillas
    ]
    head = Bone("head", (0, 17, 15), "body", [
        Cube((-6.0, 12.0, 15.0), (6.0, 20.0, 25.0), mouth="down"),
        Cube((-4.0, 11.5, 25.0), (4.0, 16.0, 28.0), tag="beak"),
        Cube((-6.25, 19.5, 15.5), (6.25, 21.0, 23.0), tag="dark"),    # placa de la cabeza
    ] + horn_r + mirror(horn_r))
    jaw = Bone("jaw", (0, 12.5, 19), "head", [Cube((-5.0, 10.5, 19.0), (5.0, 12.5, 27.0), mouth="up")])
    tail_spike_r = [Cube((5.0, 20.0, -24.0), (7.5, 21.5, -21.5), tag="horn", tip="x")]
    hind_r = [
        Cube((6.0, 8.0, -13.0), (13.0, 20.0, -3.0), stripes="y"),
        Cube((6.5, 1.0, -12.0), (12.5, 9.0, -4.0), stripes="y"),
        Cube((6.0, 0.0, -13.0), (13.0, 2.0, -3.0)),
        Cube((6.5, 0.0, -3.0), (12.5, 1.0, -2.0), tag="claw"),
    ]
    front_r = [
        Cube((6.5, 7.0, 4.0), (12.5, 18.0, 11.0), stripes="y"),
        Cube((7.0, 1.0, 5.0), (12.0, 8.0, 10.5), stripes="y"),
        Cube((6.5, 0.0, 4.5), (12.5, 2.0, 11.5)),
    ]
    bones = [
        body, head, jaw,
        Bone("tail1", (0, 22, -13), "body", [Cube((-5.0, 17.0, -30.0), (5.0, 25.0, -12.0))]
             + tail_spike_r + mirror(tail_spike_r)),
        Bone("tail2", (0, 20.5, -29), "tail1", [
            Cube((-3.0, 18.0, -44.0), (3.0, 23.0, -28.0)),
            Cube((-6.0, 16.0, -52.0), (6.0, 24.5, -43.0), tag="horn", tip="z"),   # la maza
        ]),
        Bone("leg_fr", (9, 15, 7), None, front_r),
        Bone("leg_fl", (-9, 15, 7), None, mirror(front_r)),
        Bone("leg_br", (9, 16, -8), None, hind_r),
        Bone("leg_bl", (-9, 16, -8), None, mirror(hind_r)),
    ]
    return Rig(bones, face(eye=(21, 22.5, 17, 18.5), pupil=(21.75, 22.5, 17, 18.5)), pattern="bands")


def baby():
    hind_r = [Cube((3.0, 0.0, -6.5), (7.0, 7.0, -1.0), stripes="y")]
    front_r = [Cube((3.0, 0.0, 2.0), (6.5, 6.0, 6.0), stripes="y")]
    bones = [
        Bone("body", (0, 7, 0), None, [
            Cube((-6.0, 4.0, -7.0), (6.0, 11.0, 6.0)),
            Cube((-6.25, 10.5, -6.5), (6.25, 12.5, 5.5)),
        ]),
        Bone("head", (0, 8, 6), "body", [
            Cube((-3.5, 5.0, 5.0), (3.5, 11.0, 11.5), mouth="down"),
            Cube((-2.5, 5.0, 11.5), (2.5, 8.0, 13.5), tag="beak"),
        ]),
        Bone("jaw", (0, 5.5, 8), "head", [Cube((-3.0, 4.25, 8.0), (3.0, 5.5, 13.0), mouth="up")]),
        Bone("tail1", (0, 8, -6.5), "body", [Cube((-2.5, 6.0, -13.0), (2.5, 10.0, -6.0))]),
        Bone("tail2", (0, 8, -12.5), "tail1", [
            Cube((-1.5, 6.5, -18.0), (1.5, 9.5, -12.5)),
            Cube((-3.0, 5.5, -21.0), (3.0, 10.0, -17.5), tag="horn", tip="z"),
        ]),
        Bone("leg_fr", (4.5, 6, 4), None, front_r),
        Bone("leg_fl", (-4.5, 6, 4), None, mirror(front_r)),
        Bone("leg_br", (4.5, 7, -4), None, hind_r),
        Bone("leg_bl", (-4.5, 7, -4), None, mirror(hind_r)),
    ]
    return Rig(bones, face(eye=(8, 10, 8, 10), pupil=(8.75, 10, 8, 9.75), highlight=(9.5, 10, 9.5, 10)), pattern="spots")


def saddle():
    """Plataforma blindada: asiento sobre el caparazon con placas de hierro y estandarte atras."""
    side_r = [
        Cube((12.5, 18.0, -2.0), (13.0, 29.0, 6.0), tag="plate"),
        Cube((12.5, 9.5, 1.0), (12.9, 18.0, 3.0), tag="strap"),
    ]
    return Rig([Bone("saddle", (0, 20, 0), "body", [
        Cube((-8.0, 28.5, -4.0), (8.0, 29.25, 7.0), tag="cloth"),
        Cube((-6.0, 29.25, -3.0), (6.0, 30.75, 6.0), tag="saddle"),
        Cube((-5.5, 30.75, -3.5), (5.5, 33.75, -2.0), tag="plate"),
        Cube((-1.75, 30.75, 4.5), (1.75, 32.75, 6.0), tag="saddle_dark"),
        Cube((4.0, 30.75, -5.0), (5.0, 39.25, -4.0), tag="wood"),               # palo del estandarte
        Cube((5.0, 34.75, -4.75), (10.0, 38.75, -4.25), tag="cloth"),           # estandarte
        Cube((-12.5, 9.1, 1.0), (12.5, 9.5, 3.0), tag="strap"),
    ] + side_r + mirror(side_r))])


VARIANTS = {
    "pardo":   dict(name="Pardo", weight=30, base=(128, 108, 84), stripe=(96, 78, 58), belly=(190, 172, 140),
                    dark=(78, 64, 50), iris=(214, 162, 60), pupil=(16, 12, 10), accent=(196, 90, 44)),
    "musgo":   dict(name="Musgo", weight=25, base=(104, 116, 80), stripe=(74, 84, 56), belly=(176, 178, 138),
                    dark=(58, 66, 44), iris=(222, 170, 50), pupil=(16, 12, 10), accent=(220, 160, 50)),
    "piedra":  dict(name="Piedra", weight=22, base=(124, 124, 120), stripe=(92, 92, 90), belly=(184, 184, 178),
                    dark=(70, 70, 70), iris=(222, 190, 70), pupil=(16, 12, 10), accent=(80, 120, 160)),
    "arcilla": dict(name="Arcilla", weight=20, base=(160, 104, 74), stripe=(118, 72, 50), belly=(214, 180, 144),
                    dark=(94, 58, 40), iris=(232, 196, 70), pupil=(16, 12, 10), accent=(70, 50, 40)),
    "albino":  dict(name="Albino", weight=3, base=(232, 226, 214), stripe=(208, 200, 188), belly=(246, 242, 234),
                    dark=(204, 196, 184), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(236, 150, 164)),
}

SPECIES = dict(
    id="ankylosaurus", name="Ankylosaurus", scale=0.85,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    saddle_colors=dict(saddle=(96, 66, 44), saddle_dark=(62, 44, 30), strap=(50, 38, 28), metal=(170, 170, 176),
                       plate=(126, 128, 134), cloth=(150, 34, 34), trim=(230, 196, 90), wood=(110, 80, 50)),
    egg=dict(k=1.25, shell=(190, 180, 160), spot=(96, 86, 70), crack=(50, 44, 36)),
    variants=VARIANTS,
)

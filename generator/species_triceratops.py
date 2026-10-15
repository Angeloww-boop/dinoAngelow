"""Triceratops: cuadrupedo grande, herbivoro, neutral (embiste si lo atacan), montura tanque.

Se modela a escala 1 y el plugin lo muestra a SPECIES['scale'] (0.85), asi queda de
~2.2 bloques de alto y ~5.3 de largo sin pasarse del limite de 24 px por hueso.
"""
from geometry import Cube, Bone, Rig, mirror, face


def adult(male=False):
    horn_len = 48.0 if male else 44.5
    spike_top = 47.0 if male else 45.5
    frill_rot = ("x", -22.5, (0, 22, 21))

    body = Bone("body", (0, 24, 0), None, [
        Cube((-12.0, 12.0, -18.0), (12.0, 34.0, 14.0)),          # torso
        Cube((-11.0, 30.0, -16.0), (11.0, 37.0, -2.0)),          # cadera (la parte mas alta del lomo)
        Cube((-11.0, 14.0, 10.0), (11.0, 31.0, 20.0)),           # hombros
        Cube((-9.0, 15.0, 19.0), (9.0, 28.0, 24.0)),             # base del cuello
    ])

    brow_r = [Cube((3.0, 27.0, 31.0), (6.0, 30.0, horn_len), tag="horn", tip="z",
                   rot=("x", -22.5, (4.5, 28.5, 31.0)))]
    cheek_r = [Cube((8.0, 17.0, 29.0), (10.0, 20.0, 32.0), tag="horn", tip="x")]
    spikes = [Cube((x - 1.0, 43.5, 19.75), (x + 1.0, spike_top, 21.75), tag="horn", tip="y", rot=frill_rot)
              for x in (-12.0, -6.0, 0.0, 6.0, 12.0)]
    side_spikes = [Cube((15.5, 24.0 + i * 6, 19.75), (17.5, 26.0 + i * 6, 21.75), tag="horn", tip="x", rot=frill_rot)
                   for i in range(3)]
    head = Bone("head", (0, 24, 24), "body", [
        Cube((-8.0, 15.0, 18.0), (8.0, 29.0, 26.0)),                              # cuello
        Cube((-8.0, 16.0, 25.0), (8.0, 30.0, 38.0), mouth="down"),               # craneo
        Cube((-5.5, 15.0, 38.0), (5.5, 25.0, 44.0), mouth="down"),               # hocico
        Cube((-3.5, 13.0, 44.0), (3.5, 22.0, 47.5), tag="beak"),                  # pico
        Cube((-1.5, 25.0, 39.0), (1.5, 30.0, 42.0), tag="horn", tip="y"),         # cuerno nasal
        Cube((-6.0, 13.0, 24.0), (6.0, 16.0, 30.0)),                              # garganta
        Cube((-16.0, 20.0, 19.0), (16.0, 44.0, 22.5), tag="frill", rot=frill_rot),  # gola
    ] + brow_r + mirror(brow_r) + cheek_r + mirror(cheek_r) + spikes + side_spikes + mirror(side_spikes))

    jaw = Bone("jaw", (0, 16, 30), "head", [
        Cube((-5.5, 11.5, 30.0), (5.5, 16.0, 43.0), mouth="up"),
        Cube((-3.0, 11.5, 43.0), (3.0, 14.5, 46.5), tag="beak"),
    ])

    hind_r = [
        Cube((5.5, 10.0, -18.0), (13.5, 28.0, -4.0), stripes="y"),     # muslo
        Cube((6.5, 2.0, -15.0), (12.5, 11.0, -7.0), stripes="y"),      # pierna
        Cube((6.0, 0.0, -16.0), (13.0, 2.5, -6.0)),                    # pata
        Cube((6.5, 0.0, -6.0), (12.5, 1.5, -5.0), tag="claw"),         # uñas
    ]
    front_r = [
        Cube((6.5, 9.0, 9.0), (13.5, 24.0, 18.0), stripes="y"),
        Cube((7.0, 2.0, 10.0), (13.0, 10.0, 16.5), stripes="y"),
        Cube((6.5, 0.0, 9.5), (13.5, 2.5, 17.5)),
        Cube((7.0, 0.0, 17.5), (13.0, 1.5, 18.5), tag="claw"),
    ]
    bones = [
        body, head, jaw,
        Bone("tail1", (0, 30, -16), "body", [Cube((-8.0, 22.0, -34.0), (8.0, 34.0, -14.0))]),
        Bone("tail2", (0, 27, -33), "tail1", [
            Cube((-5.0, 22.0, -45.0), (5.0, 30.0, -32.0)),
            Cube((-3.0, 22.5, -52.0), (3.0, 27.5, -44.0)),
        ]),
        Bone("leg_fr", (10, 22, 14), None, front_r),
        Bone("leg_fl", (-10, 22, 14), None, mirror(front_r)),
        Bone("leg_br", (9, 24, -11), None, hind_r),
        Bone("leg_bl", (-9, 24, -11), None, mirror(hind_r)),
    ]
    return Rig(bones, face(eye=(33, 35, 25, 27), pupil=(33, 34, 25, 27), eye_stripe=(30, 33, 25.5, 26.5),
                           brow=(32, 36, 27, 28)), pattern="bands")


def baby():
    frill_rot = ("x", -22.5, (0, 11, 6))
    brow_r = [Cube((2.0, 14.0, 11.0), (3.5, 15.5, 13.0), tag="horn", tip="y")]
    hind_r = [
        Cube((2.5, 0.0, -7.5), (7.0, 10.0, -1.0), stripes="y"),
        Cube((2.75, 0.0, -1.0), (6.75, 1.0, -0.25), tag="claw"),
    ]
    front_r = [
        Cube((2.75, 0.0, 1.5), (6.75, 9.0, 6.5), stripes="y"),
        Cube((3.0, 0.0, 6.5), (6.5, 1.0, 7.25), tag="claw"),
    ]
    bones = [
        Bone("body", (0, 9, 0), None, [
            Cube((-6.0, 5.0, -8.0), (6.0, 14.0, 7.0)),
            Cube((-5.5, 12.0, -7.0), (5.5, 15.0, -1.0)),
        ]),
        Bone("head", (0, 10, 7), "body", [
            Cube((-5.0, 7.0, 6.0), (5.0, 16.0, 14.0), mouth="down"),
            Cube((-3.5, 7.0, 14.0), (3.5, 13.0, 17.0), mouth="down"),
            Cube((-2.0, 6.0, 17.0), (2.0, 11.0, 18.5), tag="beak"),
            Cube((-0.75, 13.0, 15.0), (0.75, 14.5, 16.5), tag="horn", tip="y"),
            Cube((-7.0, 10.0, 5.0), (7.0, 19.0, 7.0), tag="frill", rot=frill_rot),
        ] + brow_r + mirror(brow_r)),
        Bone("jaw", (0, 7, 10), "head", [
            Cube((-3.5, 4.5, 10.0), (3.5, 7.0, 17.0), mouth="up"),
            Cube((-1.75, 4.5, 17.0), (1.75, 6.5, 18.5), tag="beak"),
        ]),
        Bone("tail1", (0, 12, -7), "body", [Cube((-4.0, 8.0, -14.0), (4.0, 14.0, -6.5))]),
        Bone("tail2", (0, 11, -13.5), "tail1", [Cube((-2.5, 8.5, -20.0), (2.5, 12.5, -13.0))]),
        Bone("leg_fr", (4.5, 8, 4), None, front_r),
        Bone("leg_fl", (-4.5, 8, 4), None, mirror(front_r)),
        Bone("leg_br", (4.5, 9, -4), None, hind_r),
        Bone("leg_bl", (-4.5, 9, -4), None, mirror(hind_r)),
    ]
    return Rig(bones, face(eye=(10, 13, 12, 15), pupil=(11, 13, 12, 14), highlight=(12, 13, 14, 15)), pattern="spots")


def saddle():
    """Montura de carga: manta grande, baranda de madera a los costados, respaldo alto y alforjas."""
    side_r = [
        Cube((12.0, 25.5, 4.0), (12.5, 34.5, 11.0), tag="cloth"),                 # manta colgando
        Cube((12.0, 13.0, 1.0), (12.4, 34.0, 3.0), tag="strap"),                  # cincha
        Cube((12.35, 16.0, 1.25), (12.7, 17.75, 2.75), tag="metal"),              # hebilla
        Cube((12.5, 23.5, 4.5), (15.0, 30.0, 10.5), tag="saddle"),                              # alforja
        Cube((14.9, 26.5, 6.75), (15.25, 28.0, 8.25), tag="metal"),               # cierre de la alforja
        Cube((12.4, 29.0, 4.75), (15.1, 30.5, 10.25), tag="saddle_dark"),         # tapa de la alforja
        Cube((7.0, 34.5, -1.0), (8.5, 39.0, 0.5), tag="wood"),                    # poste de atras
        Cube((7.0, 34.5, 8.0), (8.5, 38.0, 9.5), tag="wood"),                     # poste de adelante
        Cube((7.25, 37.5, -1.0), (8.25, 38.75, 9.5), tag="wood"),                 # baranda
    ]
    return Rig([Bone("saddle", (0, 24, 0), "body", [
        Cube((-12.0, 34.0, -3.0), (12.0, 34.5, 11.5), tag="cloth"),               # manta
        Cube((-6.5, 34.5, -1.0), (6.5, 36.0, 9.5), tag="saddle"),                               # asiento
        Cube((-6.0, 36.0, -1.5), (6.0, 39.5, 0.0), tag="saddle_dark"),            # respaldo alto
        Cube((-6.5, 39.5, -1.75), (6.5, 40.25, 0.25), tag="wood"),                # remate del respaldo
        Cube((-2.0, 36.0, 7.5), (2.0, 38.0, 9.5), tag="saddle_dark"),             # pomo
        Cube((-12.0, 11.6, 1.0), (12.0, 12.0, 3.0), tag="strap"),                 # cincha por la panza
    ] + side_r + mirror(side_r))])


VARIANTS = {
    "pardo":   dict(name="Pardo", weight=30, base=(132, 112, 86), stripe=(96, 78, 58), belly=(180, 164, 134),
                    dark=(80, 66, 50), iris=(204, 150, 60), pupil=(16, 12, 10), accent=(198, 86, 40),
                    accent2=(236, 200, 96)),
    "oliva":   dict(name="Oliva", weight=25, base=(114, 120, 78), stripe=(78, 86, 52), belly=(172, 172, 130),
                    dark=(64, 70, 44), iris=(212, 170, 50), pupil=(16, 12, 10), accent=(180, 62, 44),
                    accent2=(230, 204, 104)),
    "pizarra": dict(name="Pizarra", weight=22, base=(106, 110, 114), stripe=(72, 76, 82), belly=(166, 168, 166),
                    dark=(58, 62, 66), iris=(204, 182, 80), pupil=(16, 12, 10), accent=(56, 108, 156),
                    accent2=(222, 222, 204)),
    "rojizo":  dict(name="Rojizo", weight=20, base=(150, 98, 70), stripe=(106, 64, 44), belly=(206, 172, 136),
                    dark=(92, 56, 38), iris=(222, 182, 70), pupil=(16, 12, 10), accent=(226, 142, 40),
                    accent2=(252, 224, 144)),
    "albino":  dict(name="Albino", weight=3, base=(230, 224, 210), stripe=(208, 200, 186), belly=(244, 240, 232),
                    dark=(196, 188, 176), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(232, 152, 164),
                    accent2=(250, 240, 232)),
}

SPECIES = dict(
    id="triceratops", name="Triceratops", scale=0.85,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    saddle_colors=dict(saddle=(110, 72, 44), saddle_dark=(72, 46, 28), strap=(58, 40, 28), metal=(190, 192, 196),
                       cloth=(44, 72, 128), trim=(222, 196, 112), wood=(128, 92, 56)),
    egg=dict(k=1.3, shell=(196, 200, 176), spot=(96, 108, 80), crack=(50, 46, 36)),
    variants=VARIANTS,
)

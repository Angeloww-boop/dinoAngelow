"""Carnotaurus: el "toro carnivoro" argentino. Cabeza corta y alta con dos cuernos sobre los ojos,
bracitos minimos y patas largas: el corredor mas rapido de los carnivoros. Embiste con los cuernos.
"""
from geometry import Cube, Bone, Rig, mirror, face


def adult(male=False):
    horn = 5.5 if male else 4.5
    arm_r = [
        Cube((4.5, 19.0, 10.0), (5.25, 21.0, 11.5)),
        Cube((4.5, 18.0, 11.0), (5.25, 19.0, 12.5)),
    ]
    bumps = [Cube((-0.6, 28.5, z), (0.6, 29.5, z + 1.5), tag="accent") for z in (-6.0, -2.0, 2.0, 6.0)]
    body = Bone("body", (0, 24, 0), None, [
        Cube((-5.0, 20.0, -9.0), (5.0, 29.0, 1.0)),
        Cube((-5.5, 17.0, 0.0), (5.5, 28.0, 9.0)),
        Cube((-4.5, 18.0, 8.0), (4.5, 27.0, 13.0)),
        Cube((-3.5, 22.0, 12.0), (3.5, 29.0, 15.5)),
    ] + arm_r + mirror(arm_r) + bumps)
    horn_r = [Cube((2.0, 37.0, 17.5), (2.0 + horn, 39.0, 19.5), tag="horn", tip="x",
                   rot=("z", 22.5, (2.0, 38.0, 18.5)))]
    head = Bone("head", (0, 25, 14), "body", [
        Cube((-3.0, 22.0, 12.0), (3.0, 33.0, 17.0), rot=("x", 22.5, (0, 25, 14.5)), stripes="y"),
        Cube((-3.5, 31.0, 16.0), (3.5, 38.0, 23.0), mouth="down"),
        Cube((-3.0, 31.0, 23.0), (3.0, 36.5, 27.0), mouth="down"),
        Cube((-3.75, 36.5, 17.0), (3.75, 38.25, 21.0), tag="dark"),
    ] + horn_r + mirror(horn_r))
    leg_r = [
        Cube((3.0, 14.0, -9.0), (8.0, 27.0, 0.0), stripes="y"),
        Cube((3.75, 5.0, -6.0), (6.75, 15.0, -2.5), rot=("x", 22.5, (5.25, 10.0, -4.25)), stripes="y"),
        Cube((4.0, 1.0, -8.0), (6.5, 7.0, -5.5), rot=("x", -22.5, (5.25, 4.0, -6.75)), stripes="y"),
        Cube((3.25, 0.0, -7.0), (7.25, 1.75, 1.0)),
        Cube((3.5, 0.0, 1.0), (7.0, 1.0, 2.0), tag="claw"),
    ]
    bones = [
        body, head,
        Bone("jaw", (0, 31.5, 18), "head", [Cube((-3.0, 29.5, 18.0), (3.0, 31.5, 26.5), mouth="up")]),
        Bone("tail1", (0, 25, -8), "body", [
            Cube((-4.0, 20.5, -20.0), (4.0, 27.0, -7.0)),
            Cube((-3.0, 21.5, -28.0), (3.0, 26.0, -19.5)),
        ]),
        Bone("tail2", (0, 23.5, -27.5), "tail1", [
            Cube((-2.0, 21.5, -38.0), (2.0, 25.0, -27.0)),
            Cube((-1.25, 22.0, -44.0), (1.25, 24.5, -37.5)),
        ]),
        Bone("leg_r", (5, 24, -4), None, leg_r),
        Bone("leg_l", (-5, 24, -4), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(19.5, 21.5, 34.5, 36.5), pupil=(20.25, 21, 34.5, 36.5), eye_stripe=(17, 19.5, 35, 36),
                           teeth_y=(31, 32), teeth_z=22, jaw_teeth_y=31.0, jaw_teeth_z=20,
                           nostril=(26, 34, 35), mouth_teeth=True), pattern="spots")


def baby():
    leg_r = [
        Cube((1.75, 5.0, -3.5), (4.25, 10.5, 0.5), stripes="y"),
        Cube((2.25, 1.0, -2.5), (3.75, 5.5, -1.0), stripes="y"),
        Cube((1.75, 0.0, -3.0), (4.25, 1.0, 1.5)),
    ]
    horn_r = [Cube((1.75, 17.0, 7.5), (3.75, 18.25, 9.0), tag="horn", tip="x")]
    bones = [
        Bone("body", (0, 10, 0), None, [Cube((-3.0, 7.0, -4.0), (3.0, 12.5, 4.5))]),
        Bone("head", (0, 11, 4), "body", [
            Cube((-1.75, 9.5, 3.5), (1.75, 14.5, 6.5), rot=("x", 22.5, (0, 11, 4.5)), stripes="y"),
            Cube((-3.0, 13.0, 5.5), (3.0, 18.0, 11.0), mouth="down"),
            Cube((-2.25, 13.0, 11.0), (2.25, 16.5, 13.5), mouth="down"),
        ] + horn_r + mirror(horn_r)),
        Bone("jaw", (0, 13, 8), "head", [Cube((-2.0, 11.75, 8.0), (2.0, 13.0, 13.0), mouth="up")]),
        Bone("tail1", (0, 10.5, -3.5), "body", [Cube((-2.0, 8.0, -10.0), (2.0, 11.5, -3.0))]),
        Bone("tail2", (0, 9.5, -9.5), "tail1", [Cube((-1.25, 8.5, -16.0), (1.25, 10.5, -9.5))]),
        Bone("leg_r", (3, 10, -1.5), None, leg_r),
        Bone("leg_l", (-3, 10, -1.5), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(8, 10, 15, 17), pupil=(8.75, 10, 15, 17), highlight=(9.5, 10, 16.5, 17)), pattern="spots")


def saddle():
    """Montura de correr: asiento bajo y largo, con protector de cuero para la nuca y estribos."""
    side_r = [
        Cube((5.5, 20.0, 0.5), (5.9, 28.0, 2.0), tag="strap"),
        Cube((5.6, 15.5, 0.25), (6.75, 16.25, 2.25), tag="metal"),
        Cube((5.75, 16.25, 0.75), (6.15, 20.0, 1.5), tag="strap"),
    ]
    return Rig([Bone("saddle", (0, 24, 0), "body", [
        Cube((-5.75, 28.0, -5.0), (5.75, 28.5, 7.0), tag="cloth"),
        Cube((-4.5, 28.5, -3.5), (4.5, 29.75, 5.5), tag="saddle"),
        Cube((-3.5, 29.75, -3.75), (3.5, 31.5, -2.5), tag="saddle_dark"),
        Cube((-3.75, 29.0, 9.0), (3.75, 29.75, 13.5), tag="saddle"),             # protector de la nuca
        Cube((-1.25, 29.75, 4.0), (1.25, 31.5, 5.5), tag="saddle_dark"),
        Cube((-5.5, 16.6, 0.5), (5.5, 17.0, 2.0), tag="strap"),
    ] + side_r + mirror(side_r))])


VARIANTS = {
    "rojo":    dict(name="Rojo", weight=30, base=(160, 74, 56), stripe=(100, 40, 30), belly=(220, 176, 140),
                    dark=(84, 40, 30), iris=(240, 200, 60), pupil=(16, 12, 10), accent=(40, 30, 26)),
    "pardo":   dict(name="Pardo", weight=26, base=(140, 110, 80), stripe=(92, 70, 48), belly=(204, 186, 152),
                    dark=(74, 58, 42), iris=(226, 160, 40), pupil=(16, 12, 10), accent=(180, 60, 40)),
    "gris":    dict(name="Gris", weight=22, base=(118, 116, 112), stripe=(76, 74, 72), belly=(190, 186, 178),
                    dark=(60, 58, 56), iris=(230, 170, 40), pupil=(16, 12, 10), accent=(200, 50, 40)),
    "verde":   dict(name="Verde", weight=19, base=(96, 116, 74), stripe=(58, 74, 44), belly=(180, 184, 140),
                    dark=(50, 60, 38), iris=(232, 180, 50), pupil=(16, 12, 10), accent=(220, 110, 40)),
    "albino":  dict(name="Albino", weight=3, base=(232, 226, 214), stripe=(208, 200, 188), belly=(246, 242, 234),
                    dark=(198, 190, 178), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(236, 150, 164)),
}

SPECIES = dict(
    id="carnotaurus", name="Carnotaurus", scale=1.0,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    saddle_colors=dict(saddle=(80, 52, 36), saddle_dark=(50, 34, 24), strap=(44, 32, 24), metal=(200, 200, 206),
                       cloth=(200, 170, 60), trim=(40, 90, 150)),
    egg=dict(k=1.15, shell=(214, 196, 180), spot=(150, 80, 60), crack=(60, 40, 32)),
    variants=VARIANTS,
)

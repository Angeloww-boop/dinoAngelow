"""Giganotosaurus: el gigante argentino, rival del T-Rex. Craneo largo y angosto, brazos mas largos
con tres garras y una cresta baja en el lomo. Salvaje te caza; solo se domestica desde el huevo.
Se modela a escala 1 y se muestra a 1.2. Patas en dos huesos (muslo + canilla) por el limite de 24 px.
"""
from geometry import Cube, Bone, Rig, mirror, face


def adult(male=False):
    arm_r = [
        Cube((6.5, 21.0, 14.0), (8.0, 26.0, 17.0)),
        Cube((6.25, 17.5, 15.5), (7.75, 21.5, 21.5)),
        Cube((6.4, 16.25, 20.5), (7.6, 17.5, 23.0), tag="claw"),
    ]
    ridge = [Cube((-0.75, top, z - 2.0), (0.75, top + h, z + 2.0), tag="accent")
             for z, top, h in ((14.0, 33.5, 1.5), (8.0, 35.0, 2.0), (2.0, 35.0, 2.0), (-4.0, 36.0, 1.5))]
    body = Bone("body", (0, 30, 0), None, [
        Cube((-7.5, 22.0, -10.0), (7.5, 36.0, 2.0)),
        Cube((-8.5, 17.0, 0.0), (8.5, 35.0, 13.0)),
        Cube((-7.0, 19.0, 12.0), (7.0, 33.5, 20.0)),
        Cube((-5.5, 25.0, 18.0), (5.5, 35.0, 23.5)),
    ] + arm_r + mirror(arm_r) + ridge)
    head_cubes = [
        Cube((-4.5, 27.0, 18.0), (4.5, 40.0, 26.0), rot=("x", 22.5, (0, 32, 22)), stripes="y"),
        Cube((-5.0, 36.0, 24.0), (5.0, 44.0, 34.0), mouth="down"),             # craneo
        Cube((-4.0, 36.0, 34.0), (4.0, 42.0, 45.0), mouth="down"),             # hocico largo
        Cube((-5.5, 42.5, 26.0), (5.5, 44.5, 32.0), tag="dark"),               # cejas
        Cube((-4.0, 33.5, 23.0), (4.0, 36.0, 31.0)),
    ]
    if male:
        crest = [Cube((-0.6, 42.0, z), (0.6, 43.5, z + 2.5), tag="accent") for z in (35.0, 38.5, 42.0)]
        head_cubes += crest
    thigh_r = [Cube((4.0, 18.0, -12.0), (11.5, 34.0, 3.0), stripes="y")]
    shin_r = [
        Cube((5.5, 7.0, -8.5), (10.0, 20.0, -3.5), rot=("x", 22.5, (8, 13.5, -6)), stripes="y"),
        Cube((6.0, 2.0, -12.0), (9.75, 9.0, -8.5), rot=("x", -22.5, (8, 5.5, -10.25)), stripes="y"),
        Cube((5.5, 0.0, -11.0), (10.5, 2.5, -1.0)),
        Cube((5.75, 0.0, -1.0), (10.25, 1.5, 0.5), tag="claw"),
    ]
    bones = [
        body,
        Bone("head", (0, 32, 21), "body", head_cubes),
        Bone("jaw", (0, 36.5, 27), "head", [
            Cube((-4.25, 33.25, 27.0), (4.25, 36.5, 44.0), mouth="up"),
            Cube((-3.5, 32.5, 30.0), (3.5, 33.25, 42.0)),
        ]),
        Bone("tail1", (0, 31, -9), "body", [
            Cube((-6.5, 23.5, -26.0), (6.5, 35.0, -8.0)),
            Cube((-5.0, 25.0, -33.0), (5.0, 34.0, -25.0)),
        ]),
        Bone("tail2", (0, 29.5, -32), "tail1", [
            Cube((-4.0, 26.0, -45.0), (4.0, 33.0, -32.0)),
            Cube((-2.75, 27.0, -52.0), (2.75, 31.5, -44.0)),
            Cube((-1.5, 27.75, -56.0), (1.5, 30.5, -51.0)),
        ]),
        Bone("leg_r", (7.5, 30, -4), None, thigh_r),
        Bone("shin_r", (8, 19, -4), "leg_r", shin_r),
        Bone("leg_l", (-7.5, 30, -4), None, mirror(thigh_r)),
        Bone("shin_l", (-8, 19, -4), "leg_l", mirror(shin_r)),
    ]
    return Rig(bones, face(eye=(29.5, 32, 40.5, 42.5), pupil=(30.75, 31.5, 40.5, 42.5), eye_stripe=(26, 29.5, 41, 42),
                           teeth_y=(36, 37), teeth_z=33, jaw_teeth_y=35.5, jaw_teeth_z=29,
                           nostril=(43, 39.5, 40.5), mouth_teeth=True), pattern="stripes")


def baby():
    arm_r = [Cube((2.9, 7.75, 5.0), (3.5, 9.75, 7.0))]
    leg_r = [
        Cube((2.0, 5.5, -4.5), (5.0, 11.5, 1.0), stripes="y"),
        Cube((2.5, 1.5, -3.5), (4.5, 6.0, -1.5), stripes="y"),
        Cube((2.0, 0.0, -3.5), (5.0, 1.5, 1.5)),
    ]
    bones = [
        Bone("body", (0, 10, 0), None, [
            Cube((-3.25, 7.0, -4.5), (3.25, 13.0, 4.0)),
            Cube((-2.75, 7.5, 3.5), (2.75, 12.5, 6.5)),
        ] + arm_r + mirror(arm_r)),
        Bone("head", (0, 11.5, 5.5), "body", [
            Cube((-2.0, 10.0, 4.5), (2.0, 15.0, 8.0), rot=("x", 22.5, (0, 11.5, 6)), stripes="y"),
            Cube((-3.25, 14.0, 6.5), (3.25, 19.5, 13.0), mouth="down"),
            Cube((-2.5, 14.0, 13.0), (2.5, 17.5, 17.5), mouth="down"),
        ]),
        Bone("jaw", (0, 14, 9), "head", [Cube((-2.25, 12.5, 9.0), (2.25, 14.0, 17.0), mouth="up")]),
        Bone("tail1", (0, 11, -4), "body", [Cube((-2.5, 8.0, -11.0), (2.5, 12.5, -3.5))]),
        Bone("tail2", (0, 10, -10.5), "tail1", [Cube((-1.5, 8.5, -18.0), (1.5, 11.5, -10.5))]),
        Bone("leg_r", (3.5, 10.5, -1.5), None, leg_r),
        Bone("leg_l", (-3.5, 10.5, -1.5), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(9.5, 12, 16, 18.5), pupil=(10.5, 12, 16, 18), highlight=(11, 12, 17.5, 18.5)),
               pattern="spots")


def saddle():
    """Montura de caza: cuero claro, pieles en la manta, colmillos de adorno y argollas de bronce."""
    fangs = [Cube((x - 0.5, 37.0, -4.0), (x + 0.5, 39.5, -3.0), tag="spike", tip="y",
                  rot=("x", -22.5, (x, 37.0, -3.5))) for x in (-3.0, 3.0)]
    side_r = [
        Cube((8.5, 27.0, 3.0), (9.0, 35.5, 11.0), tag="cloth"),
        Cube((8.5, 17.0, 6.0), (8.9, 27.0, 8.0), tag="strap"),
        Cube((8.85, 21.0, 6.25), (9.25, 22.5, 7.75), tag="metal"),
    ]
    return Rig([Bone("saddle", (0, 30, 0), "body", [
        Cube((-8.5, 35.0, 0.5), (8.5, 35.5, 12.0), tag="cloth"),
        Cube((-5.5, 35.5, 2.0), (5.5, 36.75, 10.0), tag="saddle"),
        Cube((-4.5, 36.75, 1.5), (4.5, 39.0, 3.0), tag="saddle_dark"),
        Cube((-1.5, 36.75, 8.5), (1.5, 38.5, 10.0), tag="saddle_dark"),
        Cube((-2.5, 38.5, 8.75), (2.5, 39.25, 9.75), tag="metal"),
        Cube((-8.5, 16.6, 6.0), (8.5, 17.0, 8.0), tag="strap"),
    ] + fangs + side_r + mirror(side_r))])


VARIANTS = {
    "arena":   dict(name="Arena", weight=30, base=(170, 136, 96), stripe=(118, 88, 60), belly=(220, 200, 160),
                    dark=(96, 72, 50), iris=(226, 150, 40), pupil=(16, 12, 10), accent=(180, 60, 40)),
    "gris":    dict(name="Gris", weight=24, base=(112, 112, 116), stripe=(70, 72, 80), belly=(184, 182, 176),
                    dark=(56, 58, 62), iris=(232, 170, 40), pupil=(16, 12, 10), accent=(196, 40, 36)),
    "rojizo":  dict(name="Rojizo", weight=22, base=(156, 92, 62), stripe=(104, 56, 38), belly=(214, 180, 142),
                    dark=(86, 50, 34), iris=(240, 200, 70), pupil=(16, 12, 10), accent=(40, 34, 30)),
    "verde":   dict(name="Verde", weight=21, base=(98, 112, 72), stripe=(62, 74, 46), belly=(180, 180, 140),
                    dark=(52, 60, 40), iris=(226, 170, 40), pupil=(16, 12, 10), accent=(220, 130, 40)),
    "albino":  dict(name="Albino", weight=3, base=(232, 226, 214), stripe=(208, 200, 188), belly=(246, 242, 234),
                    dark=(198, 190, 178), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(236, 150, 164)),
}

SPECIES = dict(
    id="giganotosaurus", name="Giganotosaurus", scale=1.2,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    saddle_colors=dict(saddle=(166, 120, 76), saddle_dark=(110, 76, 46), strap=(84, 60, 38), metal=(206, 168, 88),
                       cloth=(214, 196, 160), trim=(120, 84, 50), spike=(236, 226, 200)),
    egg=dict(k=1.5, shell=(222, 206, 176), spot=(140, 100, 70), crack=(56, 42, 32)),
    variants=VARIANTS,
)

"""Dilophosaurus: bipedo hostil con doble cresta y la gola del cuello que se abre para escupir veneno.

La gola son dos huesos (frill_r / frill_l) modelados ABIERTOS (como un abanico de
frente); el plugin los pliega hacia atras pegados al cuello cuando esta tranquilo.
"""
from geometry import Cube, Bone, Rig, mirror, face


def adult(male=False):
    crest_h = 36.5 if male else 35.0
    arm_r = [
        Cube((4.0, 12.0, 7.0), (5.5, 17.5, 9.5)),
        Cube((3.75, 9.5, 8.5), (5.25, 12.0, 14.0)),
        Cube((3.9, 8.0, 12.5), (5.1, 9.5, 14.0), tag="claw"),
    ]
    crest_r = [
        Cube((1.0, 32.5, 11.0), (1.75, 33.75, 13.0), tag="crest"),
        Cube((1.0, 32.5, 13.0), (1.75, crest_h, 21.0), tag="crest"),
        Cube((1.0, 32.5, 21.0), (1.75, 33.75, 23.5), tag="crest"),
    ]
    leg_r = [
        Cube((3.5, 11.0, -9.0), (8.0, 21.0, -1.5), stripes="y"),
        Cube((4.5, 3.5, -6.0), (6.5, 13.0, -3.0), rot=("x", 22.5, (5.5, 8.25, -4.5)), stripes="y"),
        Cube((4.75, 1.0, -7.5), (6.25, 6.0, -5.5), rot=("x", -22.5, (5.5, 3.5, -6.5)), stripes="y"),
        Cube((3.75, 0.0, -6.5), (7.25, 1.75, 0.5)),
        Cube((4.0, 0.0, 0.5), (7.0, 1.0, 1.5), tag="claw"),
    ]
    fan_r = [
        Cube((2.5, 17.5, 11.0), (8.0, 34.0, 11.5), tag="fan"),
        Cube((2.5, 19.5, 11.05), (10.5, 32.0, 11.45), tag="fan"),
        Cube((2.5, 22.0, 11.1), (12.0, 29.5, 11.4), tag="fan"),
    ]
    bones = [
        Bone("body", (0, 17, 0), None, [
            Cube((-4.5, 14.0, -9.0), (4.5, 22.0, -1.0)),
            Cube((-4.5, 13.0, -1.0), (4.5, 21.5, 5.0)),
            Cube((-4.0, 12.0, 5.0), (4.0, 21.0, 10.0)),
            Cube((-3.0, 17.0, 9.0), (3.0, 22.0, 12.0)),
        ] + arm_r + mirror(arm_r)),
        Bone("head", (0, 19.5, 9.5), "body", [
            Cube((-2.5, 18.0, 6.5), (2.5, 30.5, 11.5), rot=("x", 22.5, (0, 19.5, 9.5)), stripes="y"),  # cuello largo
            Cube((-2.75, 28.0, 11.0), (2.75, 32.5, 18.0), mouth="down"),             # craneo
            Cube((-2.0, 28.0, 18.0), (2.0, 31.0, 24.5), mouth="down"),               # hocico
            Cube((-2.25, 26.0, 10.5), (2.25, 28.0, 13.5)),                           # garganta
        ] + crest_r + mirror(crest_r)),
        Bone("jaw", (0, 28, 13), "head", [Cube((-1.75, 26.5, 13.0), (1.75, 28.0, 23.5), mouth="up")]),
        Bone("frill_r", (2.5, 26, 11.25), "head", fan_r),
        Bone("frill_l", (-2.5, 26, 11.25), "head", mirror(fan_r)),
        Bone("tail1", (0, 19, -8), "body", [
            Cube((-3.5, 15.5, -17.0), (3.5, 21.5, -7.0)),
            Cube((-2.5, 16.5, -24.0), (2.5, 21.0, -16.5)),
        ]),
        Bone("tail2", (0, 18.75, -23.5), "tail1", [
            Cube((-1.75, 17.0, -31.0), (1.75, 20.5, -23.0)),
            Cube((-1.25, 17.5, -36.0), (1.25, 20.0, -30.5)),
            Cube((-0.75, 18.0, -39.0), (0.75, 19.5, -35.5)),
        ]),
        Bone("leg_r", (5.5, 18, -5), None, leg_r),
        Bone("leg_l", (-5.5, 18, -5), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(14, 17, 29, 31), pupil=(15, 16, 29, 31), eye_stripe=(11, 14, 29.5, 30.5),
                           teeth_y=(28, 29), teeth_z=13.5, jaw_teeth_y=27.0, jaw_teeth_z=14,
                           nostril=(24, 29, 30), mouth_teeth=True), pattern="spots")


VARIANTS = {
    "amarillo": dict(name="Amarillo", weight=35, base=(156, 150, 82), stripe=(56, 60, 36), belly=(214, 208, 152),
                     dark=(70, 70, 40), iris=(232, 118, 30), pupil=(16, 12, 10), accent=(220, 60, 40),
                     accent2=(244, 204, 60)),
    "verde":    dict(name="Verde", weight=30, base=(92, 122, 80), stripe=(38, 58, 40), belly=(182, 192, 142),
                     dark=(48, 70, 46), iris=(232, 172, 40), pupil=(16, 12, 10), accent=(234, 180, 40),
                     accent2=(70, 44, 30)),
    "pardo":    dict(name="Pardo", weight=30, base=(142, 102, 70), stripe=(70, 48, 32), belly=(212, 182, 142),
                     dark=(82, 56, 38), iris=(222, 162, 40), pupil=(16, 12, 10), accent=(60, 140, 182),
                     accent2=(232, 232, 212)),
    "albino":   dict(name="Albino", weight=3, base=(232, 228, 214), stripe=(204, 196, 182), belly=(246, 242, 234),
                     dark=(198, 190, 178), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(236, 120, 140),
                     accent2=(250, 236, 226)),
}

SPECIES = dict(
    id="dilophosaurus", name="Dilophosaurus", scale=0.85,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True)),
    saddle=None, egg=None, variants=VARIANTS,
)

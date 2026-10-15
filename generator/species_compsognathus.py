"""Compsognathus: dinosaurio diminuto que anda en grupos y ataca a jugadores heridos o solos."""
from geometry import Cube, Bone, Rig, mirror, face


def adult(male=False):
    arm_r = [Cube((1.25, 3.0, 3.5), (1.75, 4.5, 4.75))]
    leg_r = [
        Cube((0.75, 2.5, -2.5), (2.5, 5.5, 0.25), stripes="y"),
        Cube((1.1, 0.5, -2.25), (2.1, 2.75, -1.0), stripes="y"),
        Cube((0.85, 0.0, -2.0), (2.35, 0.6, 1.0)),
    ]
    head = [
        Cube((-1.0, 5.5, 3.5), (1.0, 9.5, 5.5), rot=("x", 22.5, (0, 6, 4.5)), stripes="y"),
        Cube((-1.5, 8.5, 5.0), (1.5, 10.5, 8.0), mouth="down"),
        Cube((-1.0, 8.5, 8.0), (1.0, 10.0, 10.0), mouth="down"),
    ]
    if male:
        head.append(Cube((-0.25, 10.5, 5.5), (0.25, 11.25, 7.5), tag="accent"))
    bones = [
        Bone("body", (0, 5, 0), None, [
            Cube((-1.75, 3.5, -3.0), (1.75, 6.75, 3.0)),
            Cube((-1.5, 3.0, 2.5), (1.5, 6.0, 4.5)),
        ] + arm_r + mirror(arm_r)),
        Bone("head", (0, 6, 4), "body", head),
        Bone("jaw", (0, 8.5, 6), "head", [Cube((-0.9, 7.75, 6.0), (0.9, 8.5, 9.75), mouth="up")]),
        Bone("tail1", (0, 5.5, -3), "body", [Cube((-1.25, 4.0, -8.0), (1.25, 6.5, -2.5))]),
        Bone("tail2", (0, 5.25, -7.5), "tail1", [
            Cube((-0.75, 4.5, -13.0), (0.75, 6.0, -7.5)),
            Cube((-0.4, 4.75, -15.0), (0.4, 5.75, -12.5)),
        ]),
        Bone("leg_r", (1.5, 5, -1), None, leg_r),
        Bone("leg_l", (-1.5, 5, -1), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(6, 7, 9, 10), pupil=(6, 7, 9, 10)))


VARIANTS = {
    "verde":  dict(name="Verde", weight=40, base=(112, 142, 72), stripe=(56, 78, 40), belly=(204, 204, 152),
                   dark=(50, 70, 40), iris=(20, 16, 12), pupil=(20, 16, 12), accent=(226, 170, 40)),
    "pardo":  dict(name="Pardo", weight=30, base=(150, 120, 84), stripe=(86, 64, 42), belly=(214, 196, 160),
                   dark=(82, 62, 42), iris=(20, 16, 12), pupil=(20, 16, 12), accent=(200, 70, 40)),
    "gris":   dict(name="Gris", weight=25, base=(118, 124, 128), stripe=(62, 70, 90), belly=(190, 192, 190),
                   dark=(64, 68, 76), iris=(20, 16, 12), pupil=(20, 16, 12), accent=(70, 140, 190)),
    "albino": dict(name="Albino", weight=3, base=(234, 230, 220), stripe=(210, 202, 190), belly=(246, 244, 236),
                   dark=(200, 192, 182), iris=(200, 40, 50), pupil=(200, 40, 50), accent=(232, 150, 162)),
}

SPECIES = dict(
    id="compsognathus", name="Compsognathus", scale=0.9,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True)),
    saddle=None, egg=None, variants=VARIANTS,
)

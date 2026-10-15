"""Dimorphodon: pterosaurio chiquito de cabeza grande y dientes, con cola larga terminada en una paleta.
Se domestica con pescado y viaja en el hombro del dueño (avisa si hay monstruos cerca).
Mismos huesos que el pteranodon (el plugin lo anima igual) a escala 0.5.
"""
from geometry import Cube, Bone, Rig, mirror, face
from species_pteranodon import scaled


def adult(male=False):
    wing_r = [
        Cube((2.5, 14.25, 1.5), (14.0, 15.75, 3.5)),
        Cube((14.0, 14.5, 1.75), (19.0, 15.5, 3.25)),
        Cube((18.0, 14.0, 3.25), (19.5, 15.0, 4.5), tag="claw"),
    ]
    mem_r = [
        Cube((2.5, 14.75, -5.0), (11.0, 15.25, 1.75), tag="membrane"),
        Cube((11.0, 14.75, -3.5), (19.0, 15.25, 1.75), tag="membrane"),
    ]
    wing_r2 = [
        Cube((19.0, 14.5, 1.75), (30.0, 15.5, 3.0)),
        Cube((30.0, 14.6, 2.0), (34.0, 15.4, 2.8)),
    ]
    mem_r2 = [
        Cube((19.0, 14.8, -3.0), (27.0, 15.2, 1.75), tag="membrane"),
        Cube((27.0, 14.8, -1.0), (33.0, 15.2, 2.0), tag="membrane"),
    ]
    leg_r = [
        Cube((1.0, 8.5, -3.5), (2.75, 12.5, -0.75), stripes="y"),
        Cube((1.25, 2.0, -3.0), (2.25, 9.0, -1.75), stripes="y"),
        Cube((0.75, 0.0, -3.5), (2.75, 1.0, 0.5)),
        Cube((0.9, 0.0, 0.5), (2.6, 0.6, 1.25), tag="claw"),
    ]
    vane = 4.0 if male else 3.25
    head_cubes = [
        Cube((-1.5, 14.0, 4.0), (1.5, 19.0, 6.5), rot=("x", 22.5, (0, 15, 5)), stripes="y"),
        Cube((-2.25, 17.5, 5.5), (2.25, 23.0, 11.0), mouth="down"),         # cabezona
        Cube((-1.75, 17.5, 11.0), (1.75, 21.5, 15.0), mouth="down"),        # hocico
    ]
    if male:
        head_cubes.append(Cube((-0.4, 23.0, 6.0), (0.4, 24.0, 10.0), tag="crest"))
    bones = [
        Bone("body", (0, 14, 0), None, [
            Cube((-2.5, 11.0, -4.0), (2.5, 16.0, 3.5)),
            Cube((-2.75, 11.5, 2.0), (2.75, 16.5, 5.0)),
            Cube((-0.75, 13.0, -16.0), (0.75, 14.25, -4.0)),                    # cola larga
            Cube((-0.25, 13.6 - vane, -19.5), (0.25, 13.6 + vane, -15.5), tag="crest"),  # paleta de la cola
        ]),
        Bone("head", (0, 15, 4.5), "body", head_cubes),
        Bone("jaw", (0, 17.5, 8), "head", [Cube((-1.5, 16.0, 8.0), (1.5, 17.5, 14.5), mouth="up")]),
        Bone("wing_r", (2.5, 15, 2.5), "body", wing_r),
        Bone("mem_r", (2.5, 15, 2.5), "wing_r", mem_r),
        Bone("wing_r2", (19, 15, 2.5), "wing_r", wing_r2),
        Bone("mem_r2", (19, 15, 2.5), "wing_r2", mem_r2),
        Bone("wing_l", (-2.5, 15, 2.5), "body", mirror(wing_r)),
        Bone("mem_l", (-2.5, 15, 2.5), "wing_l", mirror(mem_r)),
        Bone("wing_l2", (-19, 15, 2.5), "wing_l", mirror(wing_r2)),
        Bone("mem_l2", (-19, 15, 2.5), "wing_l2", mirror(mem_r2)),
        Bone("leg_r", (1.75, 12, -2), None, leg_r),
        Bone("leg_l", (-1.75, 12, -2), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(8.5, 10, 20.5, 22), pupil=(9, 9.75, 20.5, 22), teeth_y=(17.5, 18.25), teeth_z=10,
                           jaw_teeth_y=17.0, jaw_teeth_z=9, mouth_teeth=True), pattern="plain")


def baby():
    rig = scaled(adult(False), 0.6, 1.25)
    e = rig.face["eye"]
    rig.face["highlight"] = (e[1] - 0.4, e[1], e[3] - 0.4, e[3])
    rig.pattern = "spots"
    return rig


# base, rayas, panza, oscuro, iris, pupila, acento/acento2 (paleta y cresta), ala, punta del ala
VARIANTS = {
    "loro":    dict(name="Loro", weight=26, base=(70, 140, 90), stripe=(50, 110, 70), belly=(220, 210, 120),
                    dark=(30, 60, 40), iris=(240, 200, 40), pupil=(10, 8, 8), accent=(220, 50, 40),
                    accent2=(250, 200, 60), wing=(60, 110, 170), wing_tip=(30, 50, 90)),
    "tucan":   dict(name="Tucán", weight=24, base=(40, 40, 44), stripe=(30, 30, 34), belly=(240, 230, 200),
                    dark=(20, 20, 22), iris=(120, 200, 230), pupil=(10, 8, 8), accent=(250, 150, 30),
                    accent2=(250, 220, 60), wing=(46, 46, 52), wing_tip=(20, 20, 24)),
    "pardo":   dict(name="Pardo", weight=26, base=(150, 116, 82), stripe=(116, 88, 60), belly=(214, 196, 160),
                    dark=(70, 54, 40), iris=(230, 180, 50), pupil=(10, 8, 8), accent=(200, 80, 40),
                    accent2=(240, 190, 90), wing=(120, 92, 66), wing_tip=(66, 48, 36)),
    "cielo":   dict(name="Cielo", weight=21, base=(130, 170, 200), stripe=(100, 140, 172), belly=(230, 236, 240),
                    dark=(50, 70, 90), iris=(240, 210, 80), pupil=(10, 8, 8), accent=(250, 120, 140),
                    accent2=(255, 200, 210), wing=(110, 150, 190), wing_tip=(50, 70, 100)),
    "albino":  dict(name="Albino", weight=3, base=(240, 236, 228), stripe=(222, 216, 208), belly=(250, 248, 244),
                    dark=(210, 200, 190), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(236, 150, 164),
                    accent2=(250, 220, 220), wing=(226, 216, 208), wing_tip=(206, 190, 182)),
}

SPECIES = dict(
    id="dimorphodon", name="Dimorphodon", scale=0.5,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=None,
    egg=dict(k=0.6, shell=(230, 234, 222), spot=(90, 150, 110), crack=(56, 54, 50)),
    variants=VARIANTS,
)

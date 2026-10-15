"""Pteranodon: reptil volador. Neutral, se domestica con pescado, se monta y vuela.

Las alas se modelan ABIERTAS (como en vuelo). En el suelo el plugin las pliega: el brazo
(wing_*) baja y hace de pata delantera, el dedo largo (wing_*2) se dobla hacia atras y las
membranas (mem_*) se achican contra el hueso. Cada ala son 4 huesos por el limite de 24 px.
La cria sale del adulto achicado (con la cabeza mas grande).
"""
from geometry import Cube, Bone, Rig, mirror, face

HEAD = ("head", "jaw")


def adult(male=False):
    crest = (Cube((-0.5, 26.5, -2.0), (0.5, 30.0, 10.0), tag="crest", rot=("x", 22.5, (0, 27, 10))) if male else
             Cube((-0.5, 26.5, 3.0), (0.5, 28.75, 10.0), tag="crest", rot=("x", 22.5, (0, 27, 10))))
    wing_r = [
        Cube((3.0, 18.25, 2.0), (14.0, 20.0, 4.75)),                     # humero y antebrazo
        Cube((14.0, 18.5, 2.25), (25.5, 19.5, 4.25)),
        Cube((24.0, 18.0, 4.25), (25.5, 19.0, 6.0), tag="claw"),         # dedos de la mano
    ]
    mem_r = [
        Cube((3.0, 18.75, -10.0), (14.0, 19.25, 2.25), tag="membrane"),
        Cube((14.0, 18.75, -8.0), (25.0, 19.25, 2.25), tag="membrane"),
    ]
    wing_r2 = [
        Cube((25.0, 18.5, 2.5), (40.0, 19.5, 4.0)),                      # el dedo largo del ala
        Cube((40.0, 18.6, 2.6), (47.0, 19.4, 3.6)),
    ]
    mem_r2 = [
        Cube((25.0, 18.8, -7.0), (34.0, 19.2, 2.5), tag="membrane"),
        Cube((34.0, 18.8, -4.0), (41.0, 19.2, 2.6), tag="membrane"),
        Cube((41.0, 18.8, -1.0), (47.0, 19.2, 2.6), tag="membrane"),
    ]
    leg_r = [
        Cube((1.5, 11.0, -4.5), (3.25, 16.0, -1.5), stripes="y"),        # muslo
        Cube((1.75, 2.5, -3.75), (2.75, 11.5, -2.5), stripes="y"),       # canilla
        Cube((1.25, 0.0, -4.5), (3.25, 1.0, -1.0)),                      # pie
        Cube((1.4, 0.0, -1.0), (3.1, 0.6, -0.25), tag="claw"),
    ]
    bones = [
        Bone("body", (0, 18, 0), None, [
            Cube((-3.0, 14.0, -5.0), (3.0, 20.0, 4.0)),                  # torso
            Cube((-3.5, 14.5, 1.5), (3.5, 20.5, 6.0)),                   # pecho
            Cube((-2.0, 15.0, -7.5), (2.0, 18.5, -5.0)),                 # pelvis
            Cube((-0.75, 16.0, -10.0), (0.75, 17.25, -7.5)),             # colita
            Cube((-2.0, 16.5, 5.5), (2.0, 20.0, 7.5)),                   # base del cuello
        ]),
        Bone("head", (0, 19, 6.5), "body", [
            Cube((-1.5, 17.5, 5.5), (1.5, 26.0, 8.5), rot=("x", 22.5, (0, 19, 7)), stripes="y"),  # cuello
            Cube((-2.0, 24.0, 8.0), (2.0, 28.0, 13.0), mouth="down"),            # craneo
            Cube((-1.1, 24.5, 13.0), (1.1, 26.5, 22.0), tag="beak"),             # pico
            Cube((-0.6, 24.75, 22.0), (0.6, 26.0, 29.0), tag="beak"),
            crest,
        ]),
        Bone("jaw", (0, 24.5, 11), "head", [
            Cube((-1.0, 23.25, 11.0), (1.0, 24.5, 22.0), tag="beak"),
            Cube((-0.5, 23.5, 22.0), (0.5, 24.5, 28.0), tag="beak"),
        ]),
        Bone("wing_r", (3, 19, 3), "body", wing_r),
        Bone("mem_r", (3, 19, 3), "wing_r", mem_r),
        Bone("wing_r2", (25, 19, 3.25), "wing_r", wing_r2),
        Bone("mem_r2", (25, 19, 3.25), "wing_r2", mem_r2),
        Bone("wing_l", (-3, 19, 3), "body", mirror(wing_r)),
        Bone("mem_l", (-3, 19, 3), "wing_l", mirror(mem_r)),
        Bone("wing_l2", (-25, 19, 3.25), "wing_l", mirror(wing_r2)),
        Bone("mem_l2", (-25, 19, 3.25), "wing_l2", mirror(mem_r2)),
        Bone("leg_r", (2.25, 15.5, -3), None, leg_r),
        Bone("leg_l", (-2.25, 15.5, -3), None, mirror(leg_r)),
    ]
    return Rig(bones, face(eye=(10.5, 12, 26, 27.5), pupil=(11, 11.75, 26, 27.5)), pattern="plain")


def scaled(rig, k, head_k, face_keys=("eye", "pupil", "highlight")):
    """Copia del rig achicada k veces; la cabeza (y la mandibula) ademas se agranda head_k."""
    hj = rig.bone("head").joint

    def T(p, big):
        if big:
            p = tuple(hj[i] + (p[i] - hj[i]) * head_k for i in range(3))
        return tuple(v * k for v in p)

    bones = []
    for b in rig.bones:
        big = b.name in HEAD
        cubes = []
        for c in b.cubes:
            rot = (c.rot[0], c.rot[1], T(c.rot[2], big)) if c.rot else None
            cubes.append(Cube(T(c.frm, big), T(c.to, big), c.tag, rot, c.mouth, c.stripes, c.tip))
        bones.append(Bone(b.name, T(b.joint, big), b.parent, cubes))
    fc = dict(rig.face)
    for key in face_keys:
        box = fc.get(key)   # (z0, z1, y0, y1)
        if box:
            a, b = T((0, box[2], box[0]), True), T((0, box[3], box[1]), True)
            fc[key] = (a[2], b[2], a[1], b[1])
    return Rig(bones, fc, rig.pattern)


def baby():
    rig = scaled(adult(False), 0.45, 1.4)
    rig.face["highlight"] = (rig.face["eye"][1] - 0.5, rig.face["eye"][1], rig.face["eye"][3] - 0.5, rig.face["eye"][3])
    rig.pattern = "spots"
    return rig


def saddle():
    """Arnes de vuelo: asiento chato, manija de bronce para agarrarse y correas cruzadas en el pecho."""
    side_r = [
        Cube((3.5, 14.5, 3.5), (3.85, 20.5, 4.5), tag="strap"),          # correa del pecho
        Cube((3.8, 17.0, 3.6), (4.1, 18.25, 4.4), tag="metal"),          # argolla
        Cube((3.0, 14.0, -3.5), (3.35, 20.0, -2.5), tag="strap"),        # correa de atras
        Cube((1.5, 21.0, 2.0), (2.25, 23.0, 2.75), tag="metal"),         # poste de la manija
        Cube((3.25, 19.75, -3.25), (3.6, 20.25, 3.75), tag="rope"),      # soga que une las correas
    ]
    return Rig([Bone("saddle", (0, 18, 0), "body", [
        Cube((-3.25, 20.0, -3.5), (3.25, 21.0, 2.5), tag="saddle"),                    # asiento
        Cube((-2.5, 21.0, -3.5), (2.5, 22.0, -2.5), tag="saddle_dark"),  # respaldo bajito
        Cube((-2.25, 23.0, 2.0), (2.25, 23.75, 2.75), tag="metal"),      # manija
        Cube((-3.5, 14.1, 3.5), (3.5, 14.5, 4.5), tag="strap"),          # por debajo del pecho
        Cube((-3.0, 13.6, -3.5), (3.0, 14.0, -2.5), tag="strap"),        # por debajo de la panza
    ] + side_r + mirror(side_r))])


# base, rayas, panza, oscuro, iris, pupila, acento/acento2 (cresta), ala, punta del ala, pico
VARIANTS = {
    "gaviota": dict(name="Gaviota", weight=30, base=(222, 222, 216), stripe=(196, 196, 190), belly=(244, 244, 238),
                    dark=(60, 60, 64), iris=(30, 24, 20), pupil=(10, 8, 8), accent=(196, 48, 40),
                    accent2=(236, 140, 60), wing=(150, 152, 156), wing_tip=(44, 44, 48), beak=(214, 180, 100)),
    "pardo":   dict(name="Pardo", weight=28, base=(150, 118, 86), stripe=(120, 92, 66), belly=(210, 190, 156),
                    dark=(70, 54, 40), iris=(30, 24, 20), pupil=(10, 8, 8), accent=(214, 120, 40),
                    accent2=(240, 200, 90), wing=(122, 92, 66), wing_tip=(66, 48, 36), beak=(196, 164, 104)),
    "pizarra": dict(name="Pizarra", weight=22, base=(100, 104, 112), stripe=(80, 84, 92), belly=(176, 178, 182),
                    dark=(40, 42, 48), iris=(220, 160, 40), pupil=(10, 8, 8), accent=(226, 190, 50),
                    accent2=(244, 230, 140), wing=(70, 74, 82), wing_tip=(28, 30, 34), beak=(170, 150, 110)),
    "celeste": dict(name="Celeste", weight=17, base=(160, 180, 196), stripe=(130, 150, 168), belly=(226, 232, 236),
                    dark=(56, 66, 80), iris=(30, 24, 20), pupil=(10, 8, 8), accent=(40, 96, 170),
                    accent2=(120, 190, 220), wing=(108, 128, 148), wing_tip=(40, 50, 66), beak=(206, 196, 150)),
    "albino":  dict(name="Albino", weight=3, base=(240, 236, 228), stripe=(222, 216, 208), belly=(250, 248, 244),
                    dark=(210, 200, 190), iris=(222, 44, 54), pupil=(120, 12, 22), accent=(236, 150, 164),
                    accent2=(250, 220, 220), wing=(226, 216, 208), wing_tip=(206, 190, 182), beak=(240, 226, 196)),
}

SPECIES = dict(
    id="pteranodon", name="Pteranodon", scale=1.1,
    forms=dict(female=lambda: adult(False), male=lambda: adult(True), baby=baby),
    saddle=saddle,
    saddle_colors=dict(saddle=(178, 136, 92), saddle_dark=(120, 86, 56), strap=(60, 92, 118), metal=(206, 168, 88),
                       rope=(196, 170, 120)),
    egg=dict(k=0.9, shell=(222, 226, 214), spot=(120, 140, 150), crack=(56, 54, 50)),
    variants=VARIANTS,
)

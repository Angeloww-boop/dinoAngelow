"""Nido salvaje: un anillo de ramas con paja en el fondo. Los huevos los pone el plugin encima (cada uno es una
item_display con el huevo de la especie), asi un solo modelo sirve para todas.
"""
from geometry import Cube, Bone, Rig

# ramas (wood) y paja (rope), con los materiales de las monturas
PALETTE = dict(wood=(104, 76, 48), rope=(196, 172, 108))


def rig():
    twigs = [
        # cuatro lados del anillo
        Cube((-9.0, 0.0, 8.0), (9.0, 3.0, 11.0), tag="wood"),
        Cube((-9.0, 0.0, -11.0), (9.0, 3.0, -8.0), tag="wood"),
        Cube((8.0, 0.0, -8.0), (11.0, 3.0, 8.0), tag="wood"),
        Cube((-11.0, 0.0, -8.0), (-8.0, 3.0, 8.0), tag="wood"),
        # esquinas giradas 45 grados (que parezca redondo)
        Cube((5.5, 0.5, 5.5), (9.5, 3.25, 8.5), tag="wood", rot=("y", 45, (7.5, 1.5, 7.0))),
        Cube((-9.5, 0.5, 5.5), (-5.5, 3.25, 8.5), tag="wood", rot=("y", -45, (-7.5, 1.5, 7.0))),
        Cube((5.5, 0.5, -8.5), (9.5, 3.25, -5.5), tag="wood", rot=("y", -45, (7.5, 1.5, -7.0))),
        Cube((-9.5, 0.5, -8.5), (-5.5, 3.25, -5.5), tag="wood", rot=("y", 45, (-7.5, 1.5, -7.0))),
        # ramitas sueltas que sobresalen
        Cube((-12.0, 2.0, 1.0), (-6.0, 3.0, 2.0), tag="wood", rot=("y", 22.5, (-9.0, 2.5, 1.5))),
        Cube((6.0, 2.25, -3.0), (12.5, 3.25, -2.0), tag="wood", rot=("y", -22.5, (9.0, 2.75, -2.5))),
        Cube((-2.0, 2.5, 9.0), (-1.0, 3.5, 13.0), tag="wood"),
        # paja del fondo
        Cube((-8.0, 0.0, -8.0), (8.0, 1.0, 8.0), tag="rope"),
    ]
    return Rig([Bone("nest", (0, 0, 0), None, twigs)])

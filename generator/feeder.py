"""Comedero: un bebedero de madera que se llena de comida. Uno por dieta (carne, plantas, pescado)
y 4 niveles (0 = vacio .. 3 = lleno). En el juego es una entidad (sin inventario): la comida que se
le pone se convierte en puntos y el modelo cambia segun cuanto le queda.
"""
from geometry import Cube, Bone, Rig, mirror

# kind = como se pinta la comida (build.paint_food)
DIETS = {
    "meat":   dict(name="carne", kind="meat", base=(176, 58, 52), alt=(236, 206, 196)),
    "plants": dict(name="plantas", kind="plants", base=(204, 176, 72), alt=(118, 152, 62)),
    "fish":   dict(name="pescado", kind="fish", base=(160, 178, 190), alt=(86, 106, 126)),
}
LEVELS = 4
FILL = {1: 4.0, 2: 5.25, 3: 6.5}

# como se ve en el inventario y en la mano (la item_display del mundo no usa esto)
DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.8, 0.8, 0.8]},
    "ground": {"translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "fixed": {"scale": [0.8, 0.8, 0.8]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1, 0], "scale": [0.45, 0.45, 0.45]},
}


def rig(diet, level):
    leg = [Cube((5.0, 0.0, 3.0), (6.5, 2.0, 4.5), tag="wood")]
    band = [Cube((3.5, 2.5, -5.25), (4.5, 7.0, -5.0), tag="metal"),
            Cube((3.5, 2.5, 5.0), (4.5, 7.0, 5.25), tag="metal")]
    cubes = [
        Cube((-7.0, 2.0, -5.0), (7.0, 3.0, 5.0), tag="wood"),     # fondo
        Cube((-7.0, 3.0, -5.0), (7.0, 7.0, -4.0), tag="wood"),    # paredes
        Cube((-7.0, 3.0, 4.0), (7.0, 7.0, 5.0), tag="wood"),
        Cube((-7.0, 3.0, -4.0), (-6.0, 7.0, 4.0), tag="wood"),
        Cube((6.0, 3.0, -4.0), (7.0, 7.0, 4.0), tag="wood"),
    ] + leg + mirror(leg) + [Cube((c.frm[0], c.frm[1], -c.to[2]), (c.to[0], c.to[1], -c.frm[2]), c.tag)
                             for c in leg + mirror(leg)] + band + mirror(band)
    if level > 0:
        h = FILL[level]
        cubes.append(Cube((-6.0, 3.0, -4.0), (6.0, h, 4.0), tag="food"))
        kind = DIETS[diet]["kind"]
        if level >= 2:
            if kind == "meat":
                cubes += [Cube((-4.0, h, -2.0), (-1.0, h + 1.25, 1.0), tag="food"),
                          Cube((1.5, h, 0.0), (4.5, h + 1.0, 3.0), tag="food")]
            elif kind == "fish":
                cubes += [Cube((-3.0, h, -1.0), (2.0, h + 1.0, 1.0), tag="food"),
                          Cube((2.0, h, -1.5), (3.5, h + 0.75, 1.5), tag="food")]
            else:
                cubes += [Cube((-4.5, h, -2.5), (-1.5, h + 1.0, 0.5), tag="food"),
                          Cube((1.0, h, -1.0), (4.0, h + 0.75, 2.0), tag="food")]
        if level == 3:
            if kind == "meat":   # un hueso asomando
                cubes += [Cube((-0.5, h, -3.0), (0.5, h + 2.5, -2.0), tag="spike", tip="y"),
                          Cube((-1.0, h + 2.5, -3.25), (1.0, h + 3.0, -1.75), tag="spike")]
            elif kind == "fish":
                cubes += [Cube((-1.0, h + 0.5, 1.5), (3.0, h + 1.5, 3.0), tag="food"),
                          Cube((-2.25, h + 0.5, 1.25), (-1.0, h + 1.25, 3.25), tag="food")]
            else:                # espigas
                cubes += [Cube((x, h, z), (x + 0.5, h + 2.5, z + 0.5), tag="food")
                          for x, z in ((-3.0, 2.0), (-1.0, 2.5), (2.5, -3.0), (4.0, 1.0))]
    return Rig([Bone("feeder", (0, 0, 0), None, cubes)])

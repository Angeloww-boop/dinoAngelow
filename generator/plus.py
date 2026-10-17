"""Linea Plus: dinos con habilidad (para vender como pack). Cada uno sale de una especie base con su misma forma, pero
con la paleta de la linea (negro con detalles rojos, ojos rojos, montura negra con costuras rojas y herrajes dorados)
y una pieza extra que el normal no tiene (pues, crestas, plumas o placas), asi se distinguen de lejos.

Lo usa build.py: PLUS_SPECIES se suma a la lista de especies (rigs, texturas, iconos, fotos de la Dinopedia).
"""
import copy

from geometry import Cube

import species_ankylosaurus
import species_dimorphodon
import species_gallimimus
import species_parasaurolophus
import species_pteranodon
import species_raptor
import species_spinosaurus
import species_triceratops

# paleta de la linea: se pisa cada clave que tenga la especie base
PALETTE = dict(base=(40, 35, 37), stripe=(178, 28, 36), belly=(84, 75, 74), dark=(18, 16, 18), iris=(255, 84, 40),
               pupil=(22, 6, 6), accent=(218, 38, 42), accent2=(124, 18, 24), wing=(48, 41, 43), wing_tip=(178, 28, 36),
               beak=(66, 57, 54))
SADDLE = dict(saddle=(42, 36, 38), saddle_dark=(22, 18, 20), strap=(126, 22, 28), metal=(216, 174, 66),
              cloth=(158, 24, 32), trim=(216, 174, 66), plate=(46, 41, 43), spike=(198, 32, 38), wood=(56, 46, 42),
              rope=(118, 22, 26))
EGG = dict(shell=(46, 40, 42), spot=(190, 30, 38), crack=(238, 72, 58))
LIMIT = 23.5   # ningun cubo a mas de 24 px de su joint (por eje)


def fits(bone, cube):
    lo, hi = cube.frm, cube.to
    return all(abs(v - bone.joint[i]) <= LIMIT for i in range(3) for v in (lo[i], hi[i]))


def add(rig, bone_name, cubes):
    bone = rig.bone(bone_name)
    for c in cubes:
        if fits(bone, c):
            bone.cubes.append(c)


def spikes(zs, y, h, x=0.0, w=1.0, d=1.25, tag="accent", tilt=-22.5):
    """Hilera de pues finas sobre el lomo (o la cabeza), inclinadas hacia atras."""
    out = []
    for z in zs:
        rot = ("x", tilt, (x, y, z)) if tilt else None
        out.append(Cube((x - w / 2, y, z - d / 2), (x + w / 2, y + h, z + d / 2), tag=tag, rot=rot))
    return out


# ---------------------------------------------------------------- pieza extra de cada uno
def extra_raptor(rig):
    add(rig, "head", spikes([10, 11.6, 13.2, 14.8], 31.5, 3.5))                 # cresta alta de plumas rojas
    add(rig, "body", spikes([-7, -4, -1, 2, 5], 21.5, 1.75))                     # pues en el lomo


def extra_triceratops(rig):
    add(rig, "body", spikes([-12, -6, 0, 6, 12], 36.5, 3, w=1.5, d=2))           # cresta dorsal de placas


def extra_spinosaurus(rig):
    add(rig, "tail1", spikes([-12, -17, -22, -27], 32.5, 2.5, w=1.25, d=2))      # pues en la cola


def extra_dimorphodon(rig):
    add(rig, "head", spikes([6.5, 8.25], 23.5, 2, w=0.75, d=1))                  # cresta roja


def extra_parasaurolophus(rig):
    add(rig, "body", spikes([-10, -4, 2, 8, 14], 34.5, 2, w=1.25, d=1.5))        # anillos rojos en el lomo


def extra_pteranodon(rig):
    add(rig, "head", [Cube((-0.5, 29.5, -1.0), (0.5, 32.5, 4.0), tag="accent")])  # segunda cresta


def extra_gallimimus(rig):
    add(rig, "head", spikes([8, 10, 12], 34.5, 2, w=0.75, d=1))                  # copete
    add(rig, "tail2", [Cube((-2.5, 22.5, -36.0), (2.5, 23.0, -32.0), tag="accent"),   # abanico de plumas en la cola
                       Cube((-1.5, 23.0, -38.0), (1.5, 23.5, -35.0), tag="accent")])


def extra_ankylosaurus(rig):
    add(rig, "body", spikes([-10, -4, 2, 8, 14], 29.5, 3, x=4, w=1.5, d=1.5, tilt=0))   # pues grandes del caparazon
    add(rig, "body", spikes([-10, -4, 2, 8, 14], 29.5, 3, x=-4, w=1.5, d=1.5, tilt=0))


# ---------------------------------------------------------------- armado
def make(base_module, pid, name, extra):
    base = base_module.SPECIES
    sp = dict(base)
    sp["id"] = pid
    sp["name"] = name
    sp["plus_of"] = base["id"]
    first = copy.deepcopy(next(iter(base["variants"].values())))
    for k in list(first):
        if k in PALETTE:
            first[k] = PALETTE[k]
    first.update(name="Carmesí", weight=100)
    sp["variants"] = {"carmesi": first}
    forms = dict(base["forms"])
    for f in ("female", "male"):
        if f in forms:
            def build(make_form=forms[f]):
                rig = make_form()
                extra(rig)
                return rig
            forms[f] = build
    sp["forms"] = forms
    if base.get("saddle_colors") is not None or base.get("saddle"):
        sp["saddle_colors"] = dict(base.get("saddle_colors") or {}, **SADDLE)
    if base.get("egg"):
        sp["egg"] = dict(base["egg"], **EGG)
    return sp


PLUS_SPECIES = [
    make(species_raptor, "raptor_plus", "Velociraptor Sombra", extra_raptor),
    make(species_triceratops, "triceratops_plus", "Triceratops Cosechador", extra_triceratops),
    make(species_spinosaurus, "spinosaurus_plus", "Spinosaurus Pescador", extra_spinosaurus),
    make(species_dimorphodon, "dimorphodon_plus", "Dimorphodon Buscador", extra_dimorphodon),
    make(species_parasaurolophus, "parasaurolophus_plus", "Parasaurolophus Heraldo", extra_parasaurolophus),
    make(species_pteranodon, "pteranodon_plus", "Pteranodon Halcón", extra_pteranodon),
    make(species_gallimimus, "gallimimus_plus", "Gallimimus Relámpago", extra_gallimimus),
    make(species_ankylosaurus, "ankylosaurus_plus", "Ankylosaurus Leñador", extra_ankylosaurus),
]

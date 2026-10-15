"""
Revisa el z-fighting de todos los modelos: caras de cubos distintos en el mismo plano, mirando al
mismo lado y superpuestas (la textura "tiembla"). build.py las separa solo con geometry.defight;
esto muestra cuantas habia y confirma que no queda ninguna.

Uso:  python generator/zfight.py        (-v para ver cada par que queda)
"""
import sys

import build
import geometry as G


def rigs():
    for sp in build.ALL_SPECIES:
        saddle = sp["saddle"]() if sp["saddle"] else None
        for model, make in sp["forms"].items():
            rig = make()
            bones = rig.bones + (saddle.bones if saddle and model != "baby" else [])
            yield f"{sp['id']}/{model}", bones


if __name__ == "__main__":
    before = after = 0
    for name, bones in rigs():
        b = len(G.coplanar_pairs(bones))
        G.defight(bones)
        left = G.coplanar_pairs(bones)
        before += b
        after += len(left)
        if left or "-v" in sys.argv:
            print(f"{name}: {b} -> {len(left)}")
            if "-v" in sys.argv:
                for ca, axa, sa, cb, axb, sb, area in left:
                    print(f"    {ca.frm}->{ca.to}  ~  {cb.frm}->{cb.to}  area={area:.2f}")
    print(f"caras superpuestas: {before} antes, {after} despues")

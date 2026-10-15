"""
Sintetiza los sonidos de los dinosaurios (sin grabaciones: todo es sintesis) y
los guarda como .ogg en el resource pack, junto con sounds.json y los subtitulos.
Los del raptor y los huevos estan aca; los de las otras especies en sounds_species.py.

Necesita numpy + soundfile (estan en generator/.venv):
    generator/.venv/Scripts/python generator/sounds.py
Despues correr build.py para regenerar el zip.
"""
import json
import os
import shutil

import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "resourcepack", "assets", "dinoearth")


from synth import *  # noqa: F401,F403


# ---------------------------------------------------------------- sonidos del raptor
def honk(dur, a, b, c, **kw):
    return voice(dur, [(0, a), (0.35, b), (1, c)], [(0, 0), (0.12, 1), (0.6, 0.85), (1, 0)], **kw)


def ambient():
    a1 = seq([honk(0.22, 520, 650, 470), honk(0.3, 560, 720, 440)], [0.09])
    barks = [honk(0.1, 760 + 60 * i, 820 + 70 * i, 640 + 50 * i, rough=0.2, vib=(9, 0.02)) for i in range(4)]
    a2 = seq(barks, [0.05, 0.06, 0.05])
    purr = voice(0.75, [(0, 150), (1, 135)], [(0, 0), (0.1, 0.8), (0.8, 0.7), (1, 0)], harmonics=40,
                 tilt=0.7, sub=0.5, rough=0.9, rough_hz=26, vib=(4, 0.02),
                 peaks=((400, 1.0, 250), (1300, 0.6, 400)), breath=0.35, breath_band=(600, 4000), drive=2.5)
    a3 = seq([purr, honk(0.18, 700, 900, 980, rough=0.25)], [0.06])
    return [a1, a2, a3]


def screech(dur, top, end, rough=0.45):
    return voice(dur, [(0, top * 0.55), (0.18, top), (0.55, top * 0.92), (1, end)],
                 [(0, 0), (0.06, 0.9), (0.2, 1), (0.75, 0.8), (1, 0)],
                 harmonics=32, tilt=0.75, sub=0.45, rough=rough, rough_hz=55, vib=(6.5, 0.045),
                 peaks=((950, 1.0, 350), (2400, 0.9, 500), (3800, 0.55, 800)), breath=0.4, drive=3.0)


def roar():
    r1 = screech(1.3, 760, 520)
    r2 = seq([honk(0.16, 500, 620, 560), screech(0.95, 840, 600, rough=0.55)], [0.05])
    return [r1, r2]


def hurt():
    out = []
    for top in (1250, 1380, 1150):
        out.append(voice(0.3, [(0, top * 0.75), (0.3, top), (1, top * 0.65)],
                         [(0, 0), (0.08, 1), (0.5, 0.8), (1, 0)], tilt=0.7, sub=0.2, rough=0.5,
                         rough_hz=70, peaks=((1100, 1.0, 400), (2800, 0.8, 600)), breath=0.35, drive=3.2))
    return out


def death():
    cry = voice(1.6, [(0, 700), (0.15, 820), (0.6, 480), (1, 210)],
                [(0, 0), (0.06, 1), (0.5, 0.75), (0.85, 0.35), (1, 0)], harmonics=30, sub=0.6,
                rough=0.7, rough_hz=32, vib=(5, 0.06), breath=0.45, drive=2.8)
    n = len(cry)
    t = np.arange(n) / SR
    rattle = 1 - 0.6 * (t / t[-1]) ** 2 * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 18 * t)))
    return [cry * rattle]


def attack():
    out = []
    for f in (130, 115):
        hiss = norm(spectral(noise(int(0.4 * SR)), band(2500, 9000))) * curve(
            [(0, 0), (0.1, 0.7), (0.7, 0.5), (1, 0)], int(0.4 * SR))
        growl = voice(0.4, [(0, f), (1, f * 1.3)], [(0, 0), (0.15, 1), (0.8, 0.8), (1, 0)], harmonics=40,
                      tilt=0.6, sub=0.6, rough=1.0, rough_hz=34, peaks=((500, 1.0, 300), (1500, 0.6, 500)),
                      breath=0.2, drive=3.0)
        snap = place(0.08, [(0, click(0.03, 300, 6000), 1.0), (0, thump(0.08, (180, 90), 0.8), 0.6)])
        out.append(place(0.55, [(0, 0.6 * hiss + growl, 0.9), (0.38, snap, 1.0)]))
    return out


def step():
    return [thump(0.16, (70, 40)), thump(0.14, (80, 45), 0.6), thump(0.18, (62, 38), 0.3)]


def baby_ambient():
    out = []
    for count, base in ((3, 1900), (2, 2300), (4, 1700)):
        tweets = []
        for i in range(count):
            a = base * rng.uniform(0.9, 1.1)
            tweets.append(voice(rng.uniform(0.06, 0.1), [(0, a), (0.5, a * 1.25), (1, a * 0.95)],
                                [(0, 0), (0.2, 1), (1, 0)], harmonics=8, tilt=1.4, sub=0.0, rough=0.1,
                                vib=(14, 0.03), peaks=((2400, 1.0, 900), (4200, 0.4, 900)), breath=0.08, drive=1.6))
        out.append(seq(tweets, [rng.uniform(0.04, 0.09) for _ in range(count - 1)]))
    return out


def baby_hurt():
    return [voice(0.2, [(0, 2000), (0.3, 2600), (1, 1700)], [(0, 0), (0.15, 1), (1, 0)], harmonics=10,
                  tilt=1.2, sub=0.0, rough=0.3, rough_hz=80, peaks=((2600, 1.0, 900),), breath=0.15, drive=2.2)
            for _ in range(2)]


def egg_crack():
    out = []
    for k in range(3):
        items = []
        t = 0.0
        for _ in range(rng.integers(3, 6)):
            items.append((t, click(rng.uniform(0.003, 0.009), 1200, 9000), rng.uniform(0.5, 1.0)))
            t += rng.uniform(0.02, 0.07)
        out.append(place(t + 0.05, items))
    return out


def egg_hatch():
    cracks = place(0.3, [(rng.uniform(0, 0.25), click(0.01, 600, 8000), rng.uniform(0.6, 1)) for _ in range(9)])
    pop = thump(0.12, (220, 120), 1.0) * 0.6
    chirp = baby_ambient()[0]
    return [place(0.9, [(0, cracks, 1.0), (0.28, pop, 1.0), (0.38, chirp, 0.8)])]


# ---------------------------------------------------------------- salida
from sounds_species import SPECIES_SOUNDS, SPECIES_SOUNDS_2, SPECIES_SOUNDS_3  # noqa: E402

# grupo -> [(clave, generador, subtitulo es, subtitulo en)]
SOUNDS = {
    "raptor": [
        ("ambient", ambient, "Velociraptor llama", "Velociraptor calls"),
        ("roar", roar, "Velociraptor ruge", "Velociraptor screeches"),
        ("hurt", hurt, "Velociraptor herido", "Velociraptor hurts"),
        ("death", death, "Velociraptor muere", "Velociraptor dies"),
        ("attack", attack, "Velociraptor muerde", "Velociraptor bites"),
        ("step", step, "Pasos de raptor", "Raptor footsteps"),
        ("baby_ambient", baby_ambient, "Cría de raptor pía", "Baby raptor chirps"),
        ("baby_hurt", baby_hurt, "Cría de raptor herida", "Baby raptor hurts"),
    ],
    **SPECIES_SOUNDS,
    "egg": [
        ("crack", egg_crack, "Huevo se agrieta", "Egg cracks"),
        ("hatch", egg_hatch, "Huevo eclosiona", "Egg hatches"),
    ],
    # despues de los huevos, asi los sonidos que ya existian no cambian (comparten el generador al azar)
    **SPECIES_SOUNDS_2,
    **SPECIES_SOUNDS_3,
}
DRY = ("step", "crack", "flap")   # sin reverb


def main():
    root = os.path.join(ASSETS, "sounds")
    shutil.rmtree(root, ignore_errors=True)
    sounds_json, es, en = {}, {}, {}
    total = 0
    for group, entries in SOUNDS.items():
        out = os.path.join(root, group)
        os.makedirs(out, exist_ok=True)
        for key, fn, sub_es, sub_en in entries:
            names = []
            for i, audio in enumerate(fn(), 1):
                audio = fade(audio if key in DRY else room(audio))
                audio = norm(audio, 0.89).astype(np.float32)
                name = f"{key}{i}"
                sf.write(os.path.join(out, name + ".ogg"), audio, SR, format="OGG", subtype="VORBIS")
                names.append(f"dinoearth:{group}/{name}")
            sub = f"subtitles.dinoearth.{group}.{key}"
            sounds_json[f"{group}.{key}"] = {"subtitle": sub, "sounds": names}
            es[sub], en[sub] = sub_es, sub_en
            total += len(names)
    with open(os.path.join(ASSETS, "sounds.json"), "w", encoding="utf-8") as fh:
        json.dump(sounds_json, fh, indent=2)
    lang = os.path.join(ASSETS, "lang")
    os.makedirs(lang, exist_ok=True)
    for code, data in (("en_us", en), ("es_es", es), ("es_ar", es), ("es_mx", es), ("es_cl", es), ("es_uy", es)):
        with open(os.path.join(lang, code + ".json"), "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2, ensure_ascii=False)
    print(f"{total} sonidos en {len(SOUNDS)} grupos")


if __name__ == "__main__":
    main()

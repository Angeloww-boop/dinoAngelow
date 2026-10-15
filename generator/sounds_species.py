"""Sonidos sintetizados del triceratops, dilophosaurus y compsognathus."""
from synth import (band, click, curve, noise, norm, place, rng, seq, spectral, thump, voice, SR)


# ---------------------------------------------------------------- triceratops
def low(dur, f0, env, rough=0.5, rough_hz=22, peaks=((300, 1.0, 180), (850, 0.6, 300), (1900, 0.25, 500)),
        breath=0.25, drive=2.0, sub=0.5):
    """Voz grave de animal grande: gruñidos y bramidos."""
    return voice(dur, f0, env, harmonics=50, tilt=0.75, sub=sub, rough=rough, rough_hz=rough_hz, vib=(3.5, 0.02),
                 jitter=0.015, peaks=peaks, breath=breath, breath_band=(300, 2500), drive=drive)


def huff(dur, gain=1.0):
    n = int(dur * SR)
    return norm(spectral(noise(n), band(150, 1600))) * curve([(0, 0), (0.08, 1), (1, 0)], n) * gain


def tri_ambient():
    a = low(0.8, [(0, 85), (0.3, 112), (1, 80)], [(0, 0), (0.15, 1), (0.7, 0.8), (1, 0)])
    b = seq([low(0.35, [(0, 95), (1, 88)], [(0, 0), (0.2, 1), (1, 0)]),
             low(0.5, [(0, 100), (0.5, 120), (1, 92)], [(0, 0), (0.2, 1), (1, 0)])], [0.12])
    c = place(1.0, [(0, huff(0.5), 0.5), (0.35, low(0.6, [(0, 78), (1, 70)], [(0, 0), (0.25, 1), (1, 0)]), 1.0)])
    return [a, b, c]


def tri_roar():
    return [low(1.5, [(0, 70), (0.25, 125), (0.7, 115), (1, 75)], [(0, 0), (0.1, 1), (0.75, 0.9), (1, 0)],
                rough=0.8, rough_hz=28, breath=0.4, drive=2.8)]


def tri_hurt():
    return [low(0.45, [(0, 150), (0.3, 170), (1, 100)], [(0, 0), (0.1, 1), (1, 0)], rough=0.7, drive=2.6)
            for _ in range(2)]


def tri_death():
    return [low(2.0, [(0, 130), (0.2, 145), (1, 48)], [(0, 0), (0.08, 1), (0.6, 0.6), (1, 0)], rough=0.9, rough_hz=18,
                breath=0.45, drive=2.4)]


def tri_attack():
    return [place(0.6, [(0, huff(0.3), 0.8), (0.18, thump(0.3, (90, 35), 1.0), 1.0)])]


def tri_step():
    return [thump(0.28, (52, 28), 0.5), thump(0.3, (48, 26), 0.35), thump(0.26, (56, 30), 0.6)]


def tri_baby_ambient():
    return [voice(0.3, [(0, a), (0.4, a * 1.15), (1, a * 0.9)], [(0, 0), (0.2, 1), (1, 0)], harmonics=16, tilt=1.1,
                  sub=0.1, rough=0.2, vib=(8, 0.05), peaks=((900, 1.0, 400), (2200, 0.5, 600)), breath=0.15, drive=1.6)
            for a in (320, 380, 290)]


def tri_baby_hurt():
    return [voice(0.22, [(0, 420), (1, 330)], [(0, 0), (0.15, 1), (1, 0)], harmonics=16, tilt=1.0, sub=0.1,
                  rough=0.4, peaks=((1000, 1.0, 400),), breath=0.2, drive=2.0)]


# ---------------------------------------------------------------- dilophosaurus
def hoot(dur, f):
    """Ulular suave, casi una nota pura (el llamado del dilophosaurus)."""
    return voice(dur, [(0, f * 0.92), (0.4, f), (1, f * 0.85)], [(0, 0), (0.25, 1), (1, 0)], harmonics=6, tilt=2.2,
                 sub=0.0, rough=0.05, vib=(5, 0.015), peaks=((f, 1.0, 250), (f * 2, 0.3, 300)), breath=0.12,
                 breath_band=(300, 2000), drive=1.2)


def rattle(dur, f0):
    """Sonajero aspero: la vibracion de cuando abre la gola."""
    return voice(dur, f0, [(0, 0), (0.08, 1), (0.8, 0.85), (1, 0)], harmonics=26, tilt=0.8, sub=0.3, rough=1.0,
                 rough_hz=26, vib=(12, 0.05), peaks=((1000, 1.0, 400), (2500, 0.8, 600), (4000, 0.4, 800)),
                 breath=0.55, drive=3.0)


def dilo_ambient():
    a = seq([hoot(0.28, 420), hoot(0.28, 400), hoot(0.4, 380)], [0.1, 0.12])
    b = seq([hoot(0.25, 460), rattle(0.6, [(0, 620), (1, 520)])], [0.08])
    return [a, b]


def dilo_roar():
    return [rattle(1.1, [(0, 520), (0.3, 700), (1, 560)])]


def dilo_spit():
    n = int(0.35 * SR)
    hiss = norm(spectral(noise(n), band(1200, 7000))) * curve([(0, 0), (0.05, 1), (0.4, 0.5), (1, 0)], n)
    wet = place(0.35, [(0.02 + 0.03 * i, click(0.008, 400, 3000), 0.7) for i in range(4)])
    return [hiss * 0.8 + wet, hiss * 0.7 + place(0.35, [(0.05, click(0.012, 300, 2500), 1.0)])]


def dilo_hurt():
    return [voice(0.3, [(0, 900), (0.3, 1150), (1, 700)], [(0, 0), (0.1, 1), (1, 0)], tilt=0.8, sub=0.25, rough=0.6,
                  rough_hz=60, peaks=((1000, 1.0, 400), (2500, 0.7, 600)), breath=0.35, drive=2.8) for _ in range(2)]


def dilo_death():
    return [voice(1.4, [(0, 650), (0.15, 760), (1, 240)], [(0, 0), (0.08, 1), (0.6, 0.6), (1, 0)], sub=0.5, rough=0.9,
                  rough_hz=22, vib=(8, 0.06), breath=0.4, drive=2.6)]


def dilo_attack():
    growl = voice(0.3, [(0, 260), (1, 330)], [(0, 0), (0.15, 1), (1, 0)], rough=1.0, rough_hz=30,
                  peaks=((700, 1.0, 300), (1800, 0.6, 500)), breath=0.5, drive=3.0)
    return [place(0.45, [(0, growl, 0.9), (0.28, thump(0.08, (200, 100), 0.9), 1.0)])]


def dilo_step():
    return [thump(0.16, (66, 38), 0.5), thump(0.15, (74, 42), 0.4)]


# ---------------------------------------------------------------- compsognathus
def tweet(f, dur=0.07, up=1.2):
    return voice(dur, [(0, f), (0.5, f * up), (1, f * 0.95)], [(0, 0), (0.2, 1), (1, 0)], harmonics=6, tilt=1.6,
                 sub=0.0, rough=0.15, rough_hz=90, vib=(18, 0.03), peaks=((f * 1.1, 1.0, 900),), breath=0.1, drive=1.5)


def compy_ambient():
    out = []
    for n, base in ((4, 2600), (3, 3000), (6, 2300)):
        out.append(seq([tweet(base * rng.uniform(0.9, 1.1), rng.uniform(0.04, 0.07)) for _ in range(n)],
                       [rng.uniform(0.02, 0.06) for _ in range(n - 1)]))
    return out


def compy_roar():
    return [seq([tweet(2800 + 150 * (i % 3), 0.045, 1.3) for i in range(10)], [0.015] * 9)]


def compy_hurt():
    return [tweet(3400, 0.14, 1.3), tweet(3100, 0.12, 1.4)]


def compy_death():
    return [voice(0.5, [(0, 3000), (1, 1400)], [(0, 0), (0.1, 1), (1, 0)], harmonics=6, tilt=1.4, sub=0.0,
                  rough=0.3, peaks=((2500, 1.0, 1000),), breath=0.15, drive=1.8)]


def compy_attack():
    chitter = seq([tweet(2900, 0.035, 1.3) for _ in range(4)], [0.01] * 3)
    return [place(0.3, [(0, chitter, 0.8), (0.2, click(0.01, 1500, 8000), 1.0)])]


def compy_step():
    return [click(0.004, 2000, 9000) * 0.5, click(0.005, 1500, 8000) * 0.5]


SPECIES_SOUNDS = {
    "triceratops": [
        ("ambient", tri_ambient, "Triceratops gruñe", "Triceratops grunts"),
        ("roar", tri_roar, "Triceratops brama", "Triceratops bellows"),
        ("hurt", tri_hurt, "Triceratops herido", "Triceratops hurts"),
        ("death", tri_death, "Triceratops muere", "Triceratops dies"),
        ("attack", tri_attack, "Triceratops embiste", "Triceratops charges"),
        ("step", tri_step, "Pisadas pesadas", "Heavy footsteps"),
        ("baby_ambient", tri_baby_ambient, "Cría de triceratops bala", "Baby triceratops bleats"),
        ("baby_hurt", tri_baby_hurt, "Cría de triceratops herida", "Baby triceratops hurts"),
    ],
    "dilophosaurus": [
        ("ambient", dilo_ambient, "Dilophosaurus ulula", "Dilophosaurus hoots"),
        ("roar", dilo_roar, "Dilophosaurus sisea", "Dilophosaurus rattles"),
        ("spit", dilo_spit, "Dilophosaurus escupe", "Dilophosaurus spits"),
        ("hurt", dilo_hurt, "Dilophosaurus herido", "Dilophosaurus hurts"),
        ("death", dilo_death, "Dilophosaurus muere", "Dilophosaurus dies"),
        ("attack", dilo_attack, "Dilophosaurus muerde", "Dilophosaurus bites"),
        ("step", dilo_step, "Pasos de dilophosaurus", "Dilophosaurus footsteps"),
    ],
    "compsognathus": [
        ("ambient", compy_ambient, "Compsognathus chilla", "Compsognathus chirps"),
        ("roar", compy_roar, "Compsognathus parlotea", "Compsognathus chitters"),
        ("hurt", compy_hurt, "Compsognathus herido", "Compsognathus hurts"),
        ("death", compy_death, "Compsognathus muere", "Compsognathus dies"),
        ("attack", compy_attack, "Compsognathus muerde", "Compsognathus bites"),
        ("step", compy_step, "Pasitos", "Tiny footsteps"),
    ],
}

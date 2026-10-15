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


# ---------------------------------------------------------------- tyrannosaurus rex
def rex_voice(dur, f0, env, rough=0.8, drive=2.8, breath=0.35):
    """Muy grave, con subarmonico fuerte: se siente mas de lo que se oye."""
    return voice(dur, f0, env, harmonics=60, tilt=0.7, sub=0.9, rough=rough, rough_hz=19, vib=(3, 0.025),
                 jitter=0.02, peaks=((180, 1.0, 120), (520, 0.7, 220), (1300, 0.3, 450)), breath=breath,
                 breath_band=(200, 2000), drive=drive)


def rex_ambient():
    a = rex_voice(1.4, [(0, 48), (0.4, 62), (1, 44)], [(0, 0), (0.2, 1), (0.75, 0.8), (1, 0)], rough=0.6)
    b = place(1.5, [(0, huff(0.6, 1.0), 0.7), (0.5, rex_voice(0.9, [(0, 55), (1, 46)], [(0, 0), (0.3, 1), (1, 0)]), 1.0)])
    c = seq([rex_voice(0.4, [(0, 60), (1, 52)], [(0, 0), (0.25, 1), (1, 0)]),
             rex_voice(0.7, [(0, 64), (0.5, 72), (1, 50)], [(0, 0), (0.2, 1), (1, 0)])], [0.15])
    return [a, b, c]


def rex_roar():
    out = []
    for top in (150, 132):
        body = rex_voice(2.4, [(0, 60), (0.18, top), (0.6, top * 0.85), (1, 58)],
                         [(0, 0), (0.08, 1), (0.7, 0.9), (1, 0)], rough=1.0, drive=3.2, breath=0.5)
        scream = voice(2.4, [(0, 240), (0.2, top * 2.6), (0.6, top * 2.2), (1, 180)],
                       [(0, 0), (0.12, 0.8), (0.6, 0.7), (1, 0)], harmonics=30, tilt=0.8, sub=0.4, rough=0.9,
                       rough_hz=31, vib=(4, 0.04), peaks=((700, 1.0, 300), (1600, 0.7, 500)), breath=0.5, drive=3.0)
        out.append(norm(body) + 0.45 * norm(scream))
    return out


def rex_hurt():
    return [rex_voice(0.7, [(0, 110), (0.3, 150), (1, 80)], [(0, 0), (0.1, 1), (1, 0)], rough=0.9, drive=3.0)
            for _ in range(2)]


def rex_death():
    return [rex_voice(3.0, [(0, 120), (0.15, 140), (0.7, 60), (1, 32)], [(0, 0), (0.06, 1), (0.6, 0.7), (1, 0)],
                      rough=1.0, breath=0.55)]


def rex_attack():
    out = []
    for f in (70, 60):
        chomp = place(0.25, [(0, thump(0.25, (f * 2, f), 1.0), 1.0), (0.02, click(0.02, 400, 5000), 0.8)])
        out.append(place(0.75, [(0, huff(0.4, 1.0), 0.7), (0.12, rex_voice(0.4, [(0, f), (1, f * 1.4)],
                                                                             [(0, 0), (0.3, 1), (1, 0)]), 0.8),
                                (0.42, chomp, 1.0)]))
    return out


def rex_step():
    return [thump(0.45, (38, 20), 0.6), thump(0.5, (34, 19), 0.5), thump(0.42, (40, 22), 0.7)]


def rex_baby_ambient():
    return [voice(0.35, [(0, a), (0.4, a * 1.2), (1, a * 0.85)], [(0, 0), (0.2, 1), (1, 0)], harmonics=20, tilt=1.0,
                  sub=0.3, rough=0.5, rough_hz=40, vib=(9, 0.05), peaks=((900, 1.0, 400), (2000, 0.5, 600)),
                  breath=0.3, drive=2.2) for a in (380, 330, 420)]


def rex_baby_hurt():
    return [voice(0.25, [(0, 520), (0.3, 640), (1, 400)], [(0, 0), (0.15, 1), (1, 0)], harmonics=18, tilt=1.0,
                  sub=0.2, rough=0.6, peaks=((1100, 1.0, 400),), breath=0.25, drive=2.4) for _ in range(2)]


# ---------------------------------------------------------------- pteranodon
def caw(dur, f, rough=0.7):
    """Graznido aspero ("kraa") de ave marina grande."""
    return voice(dur, [(0, f * 0.8), (0.25, f), (1, f * 0.7)], [(0, 0), (0.08, 1), (0.6, 0.8), (1, 0)],
                 harmonics=30, tilt=0.8, sub=0.3, rough=rough, rough_hz=48, vib=(6, 0.03),
                 peaks=((1100, 1.0, 350), (2400, 0.7, 500), (3600, 0.4, 700)), breath=0.35, drive=2.8)


def ptera_ambient():
    a = seq([caw(0.28, 520), caw(0.34, 480)], [0.12])
    b = caw(0.55, 450)
    c = seq([caw(0.14, 600, 0.5), caw(0.14, 620, 0.5), caw(0.3, 540)], [0.06, 0.08])
    return [a, b, c]


def ptera_roar():
    return [voice(1.1, [(0, 500), (0.2, 900), (0.6, 820), (1, 520)], [(0, 0), (0.06, 1), (0.75, 0.85), (1, 0)],
                  harmonics=30, tilt=0.75, sub=0.35, rough=0.8, rough_hz=52, vib=(7, 0.04),
                  peaks=((1200, 1.0, 400), (2800, 0.8, 600)), breath=0.45, drive=3.0)]


def ptera_hurt():
    return [caw(0.3, f, 0.9) for f in (760, 700)]


def ptera_death():
    return [voice(1.5, [(0, 700), (0.15, 820), (1, 260)], [(0, 0), (0.06, 1), (0.6, 0.6), (1, 0)], harmonics=28,
                  sub=0.4, rough=0.9, rough_hz=30, vib=(6, 0.06), breath=0.4, drive=2.6)]


def ptera_attack():
    snap = place(0.12, [(0, click(0.012, 800, 7000), 1.0), (0.05, click(0.01, 1000, 8000), 0.7)])
    return [place(0.45, [(0, caw(0.25, 640, 0.9), 0.7), (0.24, snap, 1.0)])]


def ptera_step():
    return [click(0.008, 600, 5000) * 0.5, click(0.01, 500, 4000) * 0.45]


def flap_once(dur=0.32, lo=150, hi=1400):
    n = int(dur * SR)
    return norm(spectral(noise(n), band(lo, hi))) * curve([(0, 0), (0.35, 1), (0.6, 0.4), (1, 0)], n)


def ptera_flap():
    return [place(0.4, [(0, flap_once(), 1.0), (0.05, thump(0.2, (60, 40), 0.2), 0.4)]),
            place(0.4, [(0, flap_once(0.36, 120, 1200), 1.0)])]


def ptera_baby_ambient():
    return [seq([tweet(1500, 0.08, 1.1), tweet(1400, 0.1, 1.1)], [0.05]), tweet(1300, 0.14, 1.2)]


def ptera_baby_hurt():
    return [tweet(1900, 0.16, 1.3)]


SPECIES_SOUNDS_2 = {
    "trex": [
        ("ambient", rex_ambient, "T-Rex gruñe", "T-Rex rumbles"),
        ("roar", rex_roar, "T-Rex ruge", "T-Rex roars"),
        ("hurt", rex_hurt, "T-Rex herido", "T-Rex hurts"),
        ("death", rex_death, "T-Rex muere", "T-Rex dies"),
        ("attack", rex_attack, "T-Rex muerde", "T-Rex bites"),
        ("step", rex_step, "Pisadas que hacen temblar el suelo", "Earth-shaking footsteps"),
        ("baby_ambient", rex_baby_ambient, "Cría de T-Rex chilla", "Baby T-Rex squeaks"),
        ("baby_hurt", rex_baby_hurt, "Cría de T-Rex herida", "Baby T-Rex hurts"),
    ],
    "pteranodon": [
        ("ambient", ptera_ambient, "Pteranodon grazna", "Pteranodon caws"),
        ("roar", ptera_roar, "Pteranodon chilla", "Pteranodon screeches"),
        ("hurt", ptera_hurt, "Pteranodon herido", "Pteranodon hurts"),
        ("death", ptera_death, "Pteranodon muere", "Pteranodon dies"),
        ("attack", ptera_attack, "Pteranodon picotea", "Pteranodon pecks"),
        ("step", ptera_step, "Pasos de pteranodon", "Pteranodon footsteps"),
        ("flap", ptera_flap, "Aleteo", "Wings flap"),
        ("baby_ambient", ptera_baby_ambient, "Cría de pteranodon pía", "Baby pteranodon chirps"),
        ("baby_hurt", ptera_baby_hurt, "Cría de pteranodon herida", "Baby pteranodon hurts"),
    ],
}


# ---------------------------------------------------------------- parasaurolophus
def trumpet(dur, f, rise=1.12):
    """Trompetazo resonante: la cresta hueca funciona como un tubo."""
    return voice(dur, [(0, f * 0.9), (0.2, f * rise), (0.8, f), (1, f * 0.85)],
                 [(0, 0), (0.1, 1), (0.75, 0.85), (1, 0)], harmonics=40, tilt=0.95, sub=0.2, rough=0.12,
                 rough_hz=20, vib=(4.5, 0.02), peaks=((f * 2, 1.0, 120), (f * 4, 0.6, 200), (f * 6, 0.3, 300)),
                 breath=0.15, breath_band=(200, 1500), drive=1.6)


def para_ambient():
    return [trumpet(1.1, 160), seq([trumpet(0.5, 150), trumpet(0.8, 170)], [0.15]),
            low(0.6, [(0, 120), (1, 105)], [(0, 0), (0.3, 1), (1, 0)], rough=0.3)]


def para_roar():
    return [trumpet(1.8, 180, 1.2), seq([trumpet(0.4, 190), trumpet(1.3, 175, 1.18)], [0.1])]


def para_hurt():
    return [trumpet(0.45, 240, 1.25) for _ in range(2)]


def para_death():
    return [voice(2.0, [(0, 220), (0.2, 240), (1, 90)], [(0, 0), (0.08, 1), (0.6, 0.6), (1, 0)], harmonics=40,
                  tilt=0.9, sub=0.3, rough=0.4, rough_hz=18, peaks=((400, 1.0, 150), (800, 0.6, 250)), breath=0.3,
                  drive=1.8)]


def para_attack():
    return [place(0.5, [(0, huff(0.3), 0.8), (0.15, thump(0.25, (110, 45), 0.9), 1.0)])]


# ---------------------------------------------------------------- gallimimus
def boom(dur, f):
    """Retumbo grave de ave grande (como el avestruz)."""
    return voice(dur, [(0, f), (0.5, f * 1.05), (1, f * 0.9)], [(0, 0), (0.25, 1), (1, 0)], harmonics=20, tilt=1.4,
                 sub=0.6, rough=0.2, vib=(3, 0.02), peaks=((f * 2, 1.0, 80), (f * 3, 0.4, 120)), breath=0.1,
                 breath_band=(100, 800), drive=1.5)


def galli_ambient():
    return [seq([boom(0.35, 95), boom(0.45, 90)], [0.12]),
            seq([caw(0.12, 900, 0.3), caw(0.12, 950, 0.3), caw(0.16, 880, 0.3)], [0.05, 0.06]),
            boom(0.6, 85)]


def galli_roar():
    return [seq([caw(0.15, 1100, 0.5) for _ in range(5)], [0.04] * 4)]


def galli_hurt():
    return [caw(0.22, 1250, 0.7), caw(0.2, 1150, 0.7)]


def galli_death():
    return [voice(1.0, [(0, 1100), (0.2, 1200), (1, 400)], [(0, 0), (0.08, 1), (1, 0)], harmonics=20, sub=0.2,
                  rough=0.6, peaks=((1200, 1.0, 400),), breath=0.3, drive=2.2)]


def galli_attack():
    return [place(0.3, [(0, caw(0.15, 1000, 0.8), 0.7), (0.12, click(0.012, 800, 7000), 1.0)])]


def galli_step():
    return [thump(0.12, (90, 50), 0.5), thump(0.1, (100, 55), 0.4)]


# ---------------------------------------------------------------- stegosaurus y ankylosaurus
def stego_ambient():
    return [low(0.9, [(0, 70), (0.4, 88), (1, 64)], [(0, 0), (0.2, 1), (1, 0)], rough=0.6, rough_hz=16),
            place(1.0, [(0, huff(0.5), 0.6), (0.4, low(0.5, [(0, 76), (1, 68)], [(0, 0), (0.3, 1), (1, 0)]), 1.0)])]


def stego_roar():
    return [low(1.3, [(0, 64), (0.3, 104), (1, 70)], [(0, 0), (0.1, 1), (0.75, 0.9), (1, 0)], rough=0.85,
                rough_hz=20, breath=0.45, drive=2.6)]


def stego_hurt():
    return [low(0.4, [(0, 130), (0.3, 150), (1, 90)], [(0, 0), (0.1, 1), (1, 0)], rough=0.7, drive=2.6)]


def stego_death():
    return [low(2.0, [(0, 110), (0.2, 125), (1, 42)], [(0, 0), (0.08, 1), (0.6, 0.6), (1, 0)], rough=0.9,
                rough_hz=16, breath=0.45, drive=2.4)]


def tail_whack():
    """Coletazo: silbido del aire + golpe seco."""
    n = int(0.25 * SR)
    swish = norm(spectral(noise(n), band(300, 3000))) * curve([(0, 0), (0.6, 1), (1, 0)], n)
    return [place(0.5, [(0, swish, 0.6), (0.22, thump(0.25, (120, 50), 1.0), 1.0)])]


def anky_ambient():
    return [place(0.8, [(0, huff(0.35, 1.0), 0.8), (0.3, huff(0.3, 1.0), 0.6)]),
            low(0.7, [(0, 82), (1, 70)], [(0, 0), (0.3, 1), (1, 0)], rough=0.5)]


def anky_roar():
    return [low(1.1, [(0, 90), (0.3, 120), (1, 80)], [(0, 0), (0.1, 1), (1, 0)], rough=0.9, rough_hz=24,
                breath=0.5, drive=2.8)]


def anky_hurt():
    return [low(0.35, [(0, 150), (1, 110)], [(0, 0), (0.1, 1), (1, 0)], rough=0.6, drive=2.4)]


def anky_death():
    return [low(1.6, [(0, 120), (1, 45)], [(0, 0), (0.08, 1), (1, 0)], rough=0.9, breath=0.5, drive=2.4)]


def heavy_step():
    return [thump(0.3, (50, 26), 0.5), thump(0.32, (46, 25), 0.4)]


# ---------------------------------------------------------------- giganotosaurus y carnotaurus
def giga_ambient():
    return [rex_voice(1.2, [(0, 56), (0.4, 74), (1, 50)], [(0, 0), (0.2, 1), (1, 0)], rough=0.7),
            seq([rex_voice(0.35, [(0, 66), (1, 58)], [(0, 0), (0.25, 1), (1, 0)]),
                 rex_voice(0.6, [(0, 70), (0.5, 80), (1, 56)], [(0, 0), (0.2, 1), (1, 0)])], [0.12])]


def giga_roar():
    out = []
    for top in (190, 170):
        body = rex_voice(2.0, [(0, 70), (0.15, top), (0.6, top * 0.85), (1, 64)], [(0, 0), (0.08, 1), (0.7, 0.9), (1, 0)],
                         rough=1.0, drive=3.2, breath=0.55)
        hiss = voice(2.0, [(0, 300), (0.2, top * 3), (1, 220)], [(0, 0), (0.1, 0.9), (0.6, 0.7), (1, 0)], harmonics=30,
                     tilt=0.75, sub=0.3, rough=1.0, rough_hz=40, peaks=((900, 1.0, 400), (2200, 0.8, 600)), breath=0.6,
                     drive=3.0)
        out.append(norm(body) + 0.55 * norm(hiss))
    return out


def carno_growl(dur, f):
    return voice(dur, [(0, f), (0.4, f * 1.3), (1, f * 0.9)], [(0, 0), (0.15, 1), (0.8, 0.8), (1, 0)], harmonics=40,
                 tilt=0.7, sub=0.6, rough=0.9, rough_hz=30, vib=(5, 0.03), peaks=((400, 1.0, 200), (1100, 0.7, 400)),
                 breath=0.35, drive=2.8)


def carno_ambient():
    return [carno_growl(0.8, 110), place(1.0, [(0, huff(0.4), 0.7), (0.3, carno_growl(0.5, 120), 1.0)])]


def carno_roar():
    return [seq([carno_growl(0.25, 160), carno_growl(1.0, 190)], [0.06])]


def carno_hurt():
    return [carno_growl(0.3, 240), carno_growl(0.28, 220)]


def carno_death():
    return [carno_growl(1.5, 180) * curve([(0, 1), (1, 0.4)], int(1.5 * SR))]


def carno_attack():
    return [place(0.5, [(0, carno_growl(0.3, 150), 0.8), (0.25, click(0.02, 400, 5000), 1.0),
                        (0.25, thump(0.15, (150, 70), 0.8), 0.8)])]


# ---------------------------------------------------------------- dimorphodon
def dimo_ambient():
    return [seq([caw(0.1, 1500, 0.4), caw(0.12, 1650, 0.4)], [0.05]), caw(0.2, 1400, 0.5)]


def dimo_roar():
    """El chillido de alarma (avisa de monstruos)."""
    return [seq([caw(0.09, 1900, 0.6) for _ in range(4)], [0.03] * 3)]


def step_mid():
    return [thump(0.2, (64, 36), 0.5), thump(0.18, (70, 40), 0.4)]


SPECIES_SOUNDS_3 = {
    "parasaurolophus": [
        ("ambient", para_ambient, "Parasaurolophus llama", "Parasaurolophus calls"),
        ("roar", para_roar, "Parasaurolophus trompetea", "Parasaurolophus trumpets"),
        ("hurt", para_hurt, "Parasaurolophus herido", "Parasaurolophus hurts"),
        ("death", para_death, "Parasaurolophus muere", "Parasaurolophus dies"),
        ("attack", para_attack, "Parasaurolophus empuja", "Parasaurolophus shoves"),
        ("step", tri_step, "Pisadas pesadas", "Heavy footsteps"),
        ("baby_ambient", tri_baby_ambient, "Cría de parasaurolophus bala", "Baby parasaurolophus bleats"),
        ("baby_hurt", tri_baby_hurt, "Cría de parasaurolophus herida", "Baby parasaurolophus hurts"),
    ],
    "gallimimus": [
        ("ambient", galli_ambient, "Gallimimus retumba", "Gallimimus booms"),
        ("roar", galli_roar, "Gallimimus da la alarma", "Gallimimus alarm calls"),
        ("hurt", galli_hurt, "Gallimimus herido", "Gallimimus hurts"),
        ("death", galli_death, "Gallimimus muere", "Gallimimus dies"),
        ("attack", galli_attack, "Gallimimus picotea", "Gallimimus pecks"),
        ("step", galli_step, "Pasos de gallimimus", "Gallimimus footsteps"),
        ("baby_ambient", ptera_baby_ambient, "Cría de gallimimus pía", "Baby gallimimus chirps"),
        ("baby_hurt", ptera_baby_hurt, "Cría de gallimimus herida", "Baby gallimimus hurts"),
    ],
    "stegosaurus": [
        ("ambient", stego_ambient, "Stegosaurus gruñe", "Stegosaurus grunts"),
        ("roar", stego_roar, "Stegosaurus brama", "Stegosaurus bellows"),
        ("hurt", stego_hurt, "Stegosaurus herido", "Stegosaurus hurts"),
        ("death", stego_death, "Stegosaurus muere", "Stegosaurus dies"),
        ("attack", tail_whack, "Coletazo con púas", "Spiked tail swing"),
        ("step", heavy_step, "Pisadas pesadas", "Heavy footsteps"),
        ("baby_ambient", tri_baby_ambient, "Cría de stegosaurus bala", "Baby stegosaurus bleats"),
        ("baby_hurt", tri_baby_hurt, "Cría de stegosaurus herida", "Baby stegosaurus hurts"),
    ],
    "ankylosaurus": [
        ("ambient", anky_ambient, "Ankylosaurus resopla", "Ankylosaurus snorts"),
        ("roar", anky_roar, "Ankylosaurus brama", "Ankylosaurus bellows"),
        ("hurt", anky_hurt, "Ankylosaurus herido", "Ankylosaurus hurts"),
        ("death", anky_death, "Ankylosaurus muere", "Ankylosaurus dies"),
        ("attack", tail_whack, "Golpe de maza", "Tail club smash"),
        ("step", heavy_step, "Pisadas pesadas", "Heavy footsteps"),
        ("baby_ambient", tri_baby_ambient, "Cría de ankylosaurus bala", "Baby ankylosaurus bleats"),
        ("baby_hurt", tri_baby_hurt, "Cría de ankylosaurus herida", "Baby ankylosaurus hurts"),
    ],
    "giganotosaurus": [
        ("ambient", giga_ambient, "Giganotosaurus gruñe", "Giganotosaurus rumbles"),
        ("roar", giga_roar, "Giganotosaurus ruge", "Giganotosaurus roars"),
        ("hurt", rex_hurt, "Giganotosaurus herido", "Giganotosaurus hurts"),
        ("death", rex_death, "Giganotosaurus muere", "Giganotosaurus dies"),
        ("attack", rex_attack, "Giganotosaurus muerde", "Giganotosaurus bites"),
        ("step", rex_step, "Pisadas que hacen temblar el suelo", "Earth-shaking footsteps"),
        ("baby_ambient", rex_baby_ambient, "Cría de giganotosaurus chilla", "Baby giganotosaurus squeaks"),
        ("baby_hurt", rex_baby_hurt, "Cría de giganotosaurus herida", "Baby giganotosaurus hurts"),
    ],
    "carnotaurus": [
        ("ambient", carno_ambient, "Carnotaurus gruñe", "Carnotaurus growls"),
        ("roar", carno_roar, "Carnotaurus ruge", "Carnotaurus roars"),
        ("hurt", carno_hurt, "Carnotaurus herido", "Carnotaurus hurts"),
        ("death", carno_death, "Carnotaurus muere", "Carnotaurus dies"),
        ("attack", carno_attack, "Carnotaurus embiste", "Carnotaurus rams"),
        ("step", step_mid, "Pasos de carnotaurus", "Carnotaurus footsteps"),
        ("baby_ambient", rex_baby_ambient, "Cría de carnotaurus chilla", "Baby carnotaurus squeaks"),
        ("baby_hurt", rex_baby_hurt, "Cría de carnotaurus herida", "Baby carnotaurus hurts"),
    ],
    "dimorphodon": [
        ("ambient", dimo_ambient, "Dimorphodon grazna", "Dimorphodon squawks"),
        ("roar", dimo_roar, "Dimorphodon chilla", "Dimorphodon shrieks"),
        ("hurt", ptera_baby_hurt, "Dimorphodon herido", "Dimorphodon hurts"),
        ("death", ptera_death, "Dimorphodon muere", "Dimorphodon dies"),
        ("attack", ptera_attack, "Dimorphodon muerde", "Dimorphodon bites"),
        ("step", ptera_step, "Pasitos", "Tiny footsteps"),
        ("flap", ptera_flap, "Aleteo", "Wings flap"),
        ("baby_ambient", ptera_baby_ambient, "Cría de dimorphodon pía", "Baby dimorphodon chirps"),
        ("baby_hurt", ptera_baby_hurt, "Cría de dimorphodon herida", "Baby dimorphodon hurts"),
    ],
}


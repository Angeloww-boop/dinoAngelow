"""Trailer de DinoEarth: todas las especies caminando en su ambiente y despues la linea Plus con sus habilidades.
Todo sale de los modelos reales del plugin (render iluminado de rexlit.py), las letras de bloques del logo y los
sonidos del resource pack; la musica se sintetiza aca.

Uso:
  python generator/video.py preview   -> docs/video/preview.png (un cuadro de cada escena, para revisar rapido)
  python generator/video.py           -> docs/video/dinoearth-trailer.mp4 (1920x1080, 30 fps)
  python generator/video.py vertical [preview]  -> lo mismo en 1080x1920 (TikTok, Reels, Shorts)
Necesita Pillow, numpy, soundfile y ffmpeg.
"""
import math
import os
import random
import subprocess
import sys
from multiprocessing import Pool

import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw, ImageFilter

import build
import logo as L
from build import ROOT, build_species
from preview import bone_matrices, mat_mul, mat_s, mat_t
from rexlit import render_lit

W, H = 1920, 1080          # escenario (en el vertical es el cuadrado del medio)
FPS = 30
VERTICAL = False
VW, VH, STAGE_Y = 1080, 1920, 420
SR = 44100
OUT = os.path.join(ROOT, "docs", "video")
SOUNDS = os.path.join(ROOT, "resourcepack", "assets", "dinoearth", "sounds")
PINK, BLUE, WHITE = L.PINK, L.BLUE, (255, 255, 255)
GROUND = int(H * 0.82)


def ease(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def clamp(v, a, b):
    return max(a, min(b, v))


# ================================================================ poses (como Dino.java, simplificadas)
def biped(t, walk=1.0, roar=0.0, swing=34, f=1.1, k=1.0):
    ph = 2 * math.pi * f * t
    s = walk
    sp, cp = math.sin(ph), math.cos(ph)
    breath = math.sin(t * 2.4)
    p = {
        "leg_r": (sp * swing * s, 0, 0, 0), "leg_l": (-sp * swing * s, 0, 0, 0),
        "shin_r": (30 * s * max(0, -cp), 0, 0, 0), "shin_l": (30 * s * max(0, cp), 0, 0, 0),
        "body": (4 * s - 6 * roar, 0, sp * 2.5 * s, breath * 0.25 * k - abs(cp) * 1.2 * s * k),
        "head": (-4 * s + math.sin(2 * ph) * 3 * s - 18 * roar, 6 * roar, 0, 0),
        "jaw": (38 * roar + math.sin(t * 40) * 3 * roar, 0, 0, 0),
        "tail1": (3 * s + breath * 1.5 + 4 * roar, math.sin(t * 1.8) * 7 * (1 - s) + sp * 6 * s, 0, 0),
        "tail2": (2 * s - breath * 1.5, math.sin(t * 1.8 - 0.9) * 11 * (1 - s) + math.sin(ph - 0.9) * 10 * s, 0, 0),
    }
    return p


def quad(t, walk=1.0, roar=0.0, f=0.9, k=1.0):
    ph = 2 * math.pi * f * t
    s = walk
    sp, cp = math.sin(ph), math.cos(ph)
    breath = math.sin(t * 1.8)
    return {
        "leg_fr": (sp * 26 * s, 0, 0, 0), "leg_bl": (sp * 26 * s, 0, 0, 0),
        "leg_fl": (-sp * 26 * s, 0, 0, 0), "leg_br": (-sp * 26 * s, 0, 0, 0),
        "body": (-3 * roar, 0, sp * 1.5 * s, breath * 0.3 * k - abs(cp) * 1.0 * s * k),
        "head": (math.sin(2 * ph) * 2.5 * s - 18 * roar, 0, 0, 0),
        "jaw": (22 * roar, 0, 0, 0),
        "tail1": (breath * 1.2, math.sin(t * 1.5) * 5 * (1 - s) + sp * 5 * s, 0, 0),
        "tail2": (0, math.sin(t * 1.5 - 0.8) * 8 * (1 - s) + math.sin(ph - 0.8) * 8 * s, 0, 0),
    }


def flyer(t, flap=1.0, fast=False, roar=0.0):
    fp = t * (2 * math.pi) * (2.4 if fast else 1.4)
    beat = math.sin(fp) * flap
    tip = math.sin(fp - 0.7) * flap
    return {
        "leg_r": (75, 0, 0, 0), "leg_l": (75, 0, 0, 0), "head": (12 - 30 * roar, 0, 0, 0), "jaw": (30 * roar, 0, 0, 0),
        "wing_r": (0, 0, 4 + 34 * beat, 0), "wing_l": (0, 0, -(4 + 34 * beat), 0),
        "wing_r2": (0, 0, -3 + 18 * tip, 0), "wing_l2": (0, 0, 3 - 18 * tip, 0),
        "body": (0, 0, 0, -beat * 0.6),
    }


def swimmer(t, s=0.7, bite=0.0):
    ph = t * (2 * math.pi) * (0.6 + 0.5 * s)
    w, w2 = math.sin(ph), math.sin(ph - 1.1)
    amp = 0.35 + s
    paddle = math.sin(ph * 0.5)
    return {
        "head": (-12 * bite, -w * 4 * amp, 0, 0), "jaw": (45 * bite, 0, 0, 0),
        "tail1": (0, w * 10 * amp, 0, 0), "tail2": (0, w2 * 18 * amp, 0, 0),
        "body": (0, 0, w * 2, math.sin(t * 1.5) * 0.3),
        "leg_fr": (0, -12 * paddle, 6 + 16 * paddle, 0), "leg_fl": (0, 12 * paddle, -(6 + 16 * paddle), 0),
        "leg_br": (0, 0, 10 * math.sin(ph * 0.5 + 1.2), 0), "leg_bl": (0, 0, -10 * math.sin(ph * 0.5 + 1.2), 0),
    }


def frill(p, open_):
    """Gola del dilophosaurus: plegada contra el cuello o abierta (con temblor)."""
    q = math.sin(open_ * 40) * 4 * open_
    sc = 0.3 + 0.7 * open_
    p["frill_r"] = (0, 80 * (1 - open_) + q, 0, 0, sc)
    p["frill_l"] = (0, -80 * (1 - open_) - q, 0, 0, sc)
    return p


# ================================================================ modelos
ALL = {sp["id"]: sp for sp in build.ALL_SPECIES}
_SETS = {}


def model_set(sid, variant, form="male"):
    """form=None: un set sin forma (egg, egg_cracked, saddle)."""
    key = (sid, variant, form)
    if key not in _SETS:
        sets = build_species(ALL[sid], {})
        for k, v in sets.items():
            if "/" in k:
                vid, f = k.split("/")
                _SETS[(sid, vid, f)] = v
            else:
                _SETS[(sid, k, None)] = v
    return _SETS[key]


EXTRA = {}


def extra_set(name):
    """Modelos que no son de una especie: el nido y los comederos."""
    if not EXTRA:
        files = {}
        EXTRA.update(build.build_nest(files))
        EXTRA.update(build.build_feeders(files))
    return EXTRA[name]


def extents(sid):
    rig = ALL[sid]["forms"]["male"]()
    cubes = [c for b in rig.bones for c in b.cubes]
    return (min(c.frm[2] for c in cubes), max(c.to[2] for c in cubes), max(c.to[1] for c in cubes),
            max(max(abs(c.frm[0]), abs(c.to[0])) for c in cubes))


def dino_layer(instances, yaw, pitch, ppx, canvas, origin_row, rim=PINK):
    """instances: [(sid, variant, pose, (dx, dy, dz) en px de modelo)] -> RGBA del tamaño canvas; el origen de la
    escena queda en (canvas_w/2, origin_row)."""
    parts = []
    for inst in instances:
        sid, var, pose, off = inst[:4]
        opt = inst[4] if len(inst) > 4 else {}
        sc = opt.get("scale", 1.0)
        if sc <= 0.01:
            continue
        if sid.startswith("@"):
            s = extra_set(sid[1:])
            k = sc
        else:
            s = model_set(sid, var, opt.get("form", "male"))
            k = ALL[sid]["scale"] * sc
        root = mat_mul(mat_t(*off), mat_s(k))
        mats = bone_matrices(s[0], pose, root)
        parts.append((s, mats))
        if opt.get("saddle"):
            sad = model_set(sid, "saddle", None)
            parts.append((sad, bone_matrices(sad[0], {}, None, parents={"body": mats["body"]})))
    cw, ch = canvas
    buf = bytearray(cw * ch * 4)
    render_lit(parts, yaw, pitch, ppx, canvas, cy_frac=origin_row / ch, buf=buf, rim_col=rim)
    return Image.frombytes("RGBA", canvas, bytes(buf))


# ================================================================ sprites de pixeles
SPRITES = {
    "wheat": (["..y.y...", ".yYyYy..", "..yYy...", ".yYyYy..", "..yYy...", "...g....", "...g....", "...g...."],
              {"y": (214, 178, 70), "Y": (240, 214, 110), "g": (110, 150, 50)}),
    "fish": (["........", ".....bb.", "..bbbbbb", ".bwbBBb.", "bbbbbbbb", "..bbBBb.", ".....bb.", "........"],
             {"b": (110, 150, 190), "B": (80, 110, 150), "w": (20, 20, 30)}),
    "note": (["..###...", "..#.##..", "..#..#..", "..#.....", "..#.....", "###.....", "###.....", "........"],
             {"#": (255, 255, 255)}),
    "heart": (["........", ".rr.rr..", "rRrrrrr.", "rrrrrrr.", ".rrrrr..", "..rrr...", "...r....", "........"],
              {"r": (230, 40, 60), "R": (255, 150, 160)}),
    "diamond": (["........", "..cccc..", ".cCcccc.", "cCccccDc", ".cccccD.", "..cccD..", "...cD...", "........"],
                {"c": (90, 230, 230), "C": (220, 255, 255), "D": (40, 150, 160)}),
    "gold": (["........", "........", "..yyyyy.", ".yYYyyy.", "yyyyyyO.", "OOOOOO..", "........", "........"],
             {"y": (250, 210, 60), "Y": (255, 250, 170), "O": (190, 130, 30)}),
    "emerald": (["........", "...gg...", "..gGgg..", ".gGgggg.", ".gggggD.", "..ggDD..", "...gD...", "........"],
                {"g": (60, 210, 100), "G": (190, 255, 200), "D": (20, 120, 50)}),
    "log": (["........", ".bbbbbb.", "bBbbbbBb", "bbbbbbbb", "bBbbbbBb", ".bbbbbb.", "........", "........"],
            {"b": (120, 84, 50), "B": (170, 130, 80)}),
    "leaf": (["........", "...gg...", "..gGgg..", ".ggggg..", "..ggg...", "...g....", "........", "........"],
             {"g": (70, 150, 50), "G": (130, 200, 80)}),
    "spark": (["...y....", "...yy...", "..yyy...", ".yyYyyy.", "...yyy..", "...yy...", "....y...", "........"],
              {"y": (255, 230, 90), "Y": (255, 255, 230)}),
    "meat": (["........", "..rrrr..", ".rRRrrr.", "rRrrrrrw", "rrrrrrww", ".rrrrw..", "...ww...", "........"],
             {"r": (190, 50, 50), "R": (240, 120, 110), "w": (240, 230, 210)}),
    "flag": (["kwkwkwkw", "wkwkwkwk", "kwkwkwkw", "wkwkwkwk", "p.......", "p.......", "p.......", "p......."],
             {"k": (20, 20, 20), "w": (250, 250, 250), "p": (120, 90, 60)}),
    "chest": (["bbbbbbbbbbbb", "bBBBBBBBBBBb", "bBBBBBBBBBBb", "bbbbbyybbbbb", "bBBBByyBBBBb", "bBBBBBBBBBBb",
               "bBBBBBBBBBBb", "bbbbbbbbbbbb"],
              {"b": (90, 60, 30), "B": (160, 110, 60), "y": (230, 200, 90)}),
}
_SPR = {}


def sprite(name, size):
    key = (name, size)
    if key not in _SPR:
        rows, pal = SPRITES[name]
        im = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
        p = im.load()
        for y, r in enumerate(rows):
            for x, ch in enumerate(r):
                if ch in pal:
                    p[x, y] = pal[ch] + (255,)
        _SPR[key] = im.resize((len(rows[0]) * size, len(rows) * size), Image.NEAREST)
    return _SPR[key]


def blit(frame, im, x, y, alpha=1.0):
    if alpha <= 0:
        return
    if alpha < 1:
        im = im.copy()
        im.putalpha(im.split()[3].point(lambda a: int(a * alpha)))
    frame.alpha_composite(im, (int(x - im.size[0] / 2), int(y - im.size[1] / 2)))


# ================================================================ ambientes (capas que se desplazan)
def gradient(stops, w=None, h=None):
    w, h = w or W, h or H
    im = Image.new("RGB", (1, h))
    p = im.load()
    for y in range(h):
        t = y / (h - 1)
        for i in range(len(stops) - 1):
            (t0, c0), (t1, c1) = stops[i], stops[i + 1]
            if t0 <= t <= t1:
                p[0, y] = L.lerp(c0, c1, (t - t0) / max(1e-6, t1 - t0))
                break
    return im.resize((w, h)).convert("RGBA")


def ridge(width, base, amp, color, seed, step=24, rough=0.5):
    """Silueta (montañas o arboles) en escalones de pixeles, que se repite sin corte cada `width`."""
    rnd = random.Random(seed)
    im = Image.new("RGBA", (width, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    n = width // step
    freqs = [(rnd.uniform(0, 6.28), rnd.choice((1, 2, 3, 5))) for _ in range(4)]
    for i in range(n):
        x = i / n
        v = sum(math.sin(2 * math.pi * f * x + ph) / f for ph, f in freqs)
        v += rough * rnd.uniform(-1, 1)
        top = int(base - amp * (0.5 + 0.35 * v))
        top -= top % 8
        if top < H:
            d.rectangle((i * step, top, (i + 1) * step - 1, H), fill=color + (255,))
    return im


def trees(width, base, color, seed, n=14):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (width, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for _ in range(n):
        x = rnd.randrange(width)
        h = rnd.randint(140, 260)
        b = 16
        d.rectangle((x - b, base - h, x + b, base), fill=color + (255,))           # tronco
        for k in range(3):
            r = rnd.randint(70, 110) - k * 18
            cy = base - h - k * 50 + 30
            d.rectangle((x - r, cy - 40, x + r, cy + 40), fill=color + (255,))   # copa en escalones
    return im


def blocks(width, top, colors, seed, size=48, rows=6, crack=None):
    """Suelo de bloques: primer renglon con pasto (o lo que diga colors['top'])."""
    rnd = random.Random(seed)
    im = Image.new("RGBA", (width, H), (0, 0, 0, 0))
    p = im.load()
    for by in range(rows):
        for bx in range(width // size):
            base = colors["top"] if by == 0 else colors["fill"]
            for y in range(size):
                yy = top + by * size + y
                if yy >= H:
                    break
                for x in range(size):
                    xx = bx * size + x
                    c = base
                    if by == 0 and y > size * 0.3 + (x * 7 % 5):
                        c = colors["fill"]
                    px = (x // 6, y // 6)
                    k = 0.88 + 0.22 * L.rnd_cell(bx * 9 + px[0], by * 9 + px[1], seed)
                    if crack and L.rnd_cell(bx * 9 + px[0], by * 9 + px[1], seed + 5) > 0.93:
                        c = crack
                        k = 1.0
                    p[xx, yy] = tuple(min(255, int(v * k)) for v in c) + (255,)
            if top + by * size < H:
                for x in range(size):                   # junta oscura entre bloques
                    p[bx * size + x, top + by * size] = tuple(int(v * 0.7) for v in colors["fill"]) + (255,)
    return im


def scrolled(frame, layer, offset):
    w = layer.size[0]
    o = int(offset) % w
    frame.alpha_composite(layer.crop((o, 0, min(w, o + W), H)), (0, 0))
    if o + W > w:
        frame.alpha_composite(layer.crop((0, 0, o + W - w, H)), (w - o, 0))


class Env:
    def __init__(self, kind, plus=False):
        self.kind, self.plus = kind, plus
        TW = 3840
        if kind in ("land", "river"):
            if plus:
                self.sky = gradient([(0, (14, 2, 6)), (0.55, (70, 8, 16)), (0.8, (160, 26, 34)), (1, (60, 6, 10))])
                far, mid, near = (48, 8, 14), (30, 4, 8), (20, 3, 6)
                ground = dict(top=(46, 40, 44), fill=(30, 26, 30))
                crack = (200, 30, 30)
                self.sun = (230, 60, 50)
            else:
                self.sky = gradient([(0, (22, 10, 50)), (0.5, (110, 40, 120)), (0.78, (240, 110, 150)),
                                     (1, (255, 170, 120))])
                far, mid, near = (96, 44, 110), (58, 26, 74), (36, 18, 50)
                ground = dict(top=(96, 168, 64), fill=(122, 86, 58))
                crack = None
                self.sun = (255, 150, 120)
            self.layers = [(ridge(TW, int(H * 0.72), 260, far, 1, step=32), 0.12),
                           (trees(TW, int(H * 0.8), mid, 2), 0.35),
                           (ridge(TW, int(H * 0.83), 90, near, 3, step=16, rough=1.0), 0.6)]
            if kind == "land":
                self.layers.append((blocks(TW, GROUND, ground, 4, crack=crack), 1.0))
            else:
                self.layers.append((blocks(TW, int(H * 0.74), ground, 4, rows=1, crack=crack), 0.8))
        elif kind == "sky":
            if plus:
                self.sky = gradient([(0, (20, 2, 8)), (0.6, (110, 16, 26)), (1, (210, 60, 50))])
                cloud = (120, 40, 46)
                self.sun = (240, 80, 60)
            else:
                self.sky = gradient([(0, (40, 60, 150)), (0.6, (150, 120, 210)), (1, (255, 170, 190))])
                cloud = (255, 236, 246)
                self.sun = (255, 230, 180)
            self.layers = [(ridge(TW, int(H * 1.02), 160, tuple(int(c * 0.6) for c in self.sky.getpixel((0, H - 1))[:3]), 7, step=32), 0.1),
                           (self.clouds(TW, cloud, 8, 0.55), 0.25), (self.clouds(TW, cloud, 9, 1.0), 0.7)]
        elif kind == "water":
            self.sky = gradient([(0, (40, 140, 180)), (0.35, (14, 60, 110)), (1, (4, 14, 40))])
            self.sun = None
            sand = dict(top=(200, 180, 120), fill=(140, 120, 90))
            self.layers = [(ridge(TW, int(H * 0.9), 160, (8, 30, 60), 11, step=32), 0.15),
                           (self.kelp(TW, 12), 0.45), (blocks(TW, int(H * 0.9), sand, 13, rows=3), 1.0)]

    @staticmethod
    def clouds(width, color, seed, a):
        rnd = random.Random(seed)
        im = Image.new("RGBA", (width, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        for _ in range(9):
            x, y = rnd.randrange(width), rnd.randrange(int(H * 0.08), int(H * 0.7))
            for k in range(4):
                w, h = rnd.randint(80, 220), rnd.randint(32, 64)
                dx, dy = rnd.randint(-120, 120), rnd.randint(-30, 10)
                d.rectangle((x + dx - w, y + dy - h, x + dx + w, y + dy + h), fill=color + (int(170 * a),))
        return im

    @staticmethod
    def kelp(width, seed):
        rnd = random.Random(seed)
        im = Image.new("RGBA", (width, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        for _ in range(30):
            x = rnd.randrange(width)
            h = rnd.randint(120, 380)
            for y in range(0, h, 16):
                dx = int(math.sin(y / 40 + x) * 10)
                d.rectangle((x + dx, int(H * 0.92) - y - 16, x + dx + 14, int(H * 0.92) - y), fill=(30, 90, 50, 255))
        return im

    def draw(self, frame, travel, t):
        frame.alpha_composite(self.sky)
        if self.sun:
            d = ImageDraw.Draw(frame)
            cx, cy, r = int(W * 0.72), int(H * 0.5), 150
            for k, a in ((1.6, 40), (1.3, 70), (1.0, 255)):
                rr = int(r * k)
                d.rectangle((cx - rr, cy - rr, cx + rr, cy + rr), fill=self.sun + (a,)) if k == 1.0 else None
            glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            ImageDraw.Draw(glow).ellipse((cx - 330, cy - 330, cx + 330, cy + 330), fill=self.sun + (90,))
            frame.alpha_composite(glow.filter(ImageFilter.GaussianBlur(60)))
            ImageDraw.Draw(frame).rectangle((cx - r, cy - r, cx + r, cy + r), fill=self.sun + (255,))
        if self.kind == "water":
            rays = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            d = ImageDraw.Draw(rays)
            for i in range(6):
                x = (i * 380 + t * 40) % (W + 600) - 300
                d.polygon(((x, 0), (x + 120, 0), (x + 420, H), (x + 260, H)), fill=(160, 220, 255, 22))
            frame.alpha_composite(rays)
        for layer, speed in self.layers:
            scrolled(frame, layer, travel * speed)
        if self.kind == "river":
            self.water_back(frame, t)

    def water_back(self, frame, t):
        top = int(H * 0.74) + 20
        d = ImageDraw.Draw(frame)
        col = (60, 20, 30) if self.plus else (40, 90, 150)
        d.rectangle((0, top, W, H), fill=col + (255,))
        for i in range(40):
            x = (i * 97 + t * 60) % W
            y = top + 20 + (i * 53) % (H - top - 30)
            d.rectangle((x, y, x + 60, y + 4), fill=(255, 255, 255, 40))

    def water_front(self, frame, t, surface):
        """Agua por delante del dino (para que se vea metido en el rio)."""
        ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(ov)
        col = (90, 20, 30) if self.plus else (50, 110, 170)
        d.rectangle((0, surface, W, H), fill=col + (150,))
        for i in range(0, W, 32):
            y = surface + int(math.sin(i / 90 + t * 3) * 5)
            d.rectangle((i, y - 3, i + 31, y + 3), fill=(220, 240, 255, 160))
        frame.alpha_composite(ov)

    def bubbles(self, frame, t, seed=3):
        rnd = random.Random(seed)
        d = ImageDraw.Draw(frame)
        for _ in range(40):
            x0, sp, ph = rnd.uniform(0, W), rnd.uniform(60, 160), rnd.uniform(0, 30)
            y = H - ((t + ph) * sp) % (H + 50)
            x = x0 + math.sin((t + ph) * 2) * 10
            s = rnd.choice((6, 8, 10))
            d.rectangle((x, y, x + s, y + s), outline=(200, 240, 255, 180), width=2)


# ================================================================ textos
_TEXT = {}


def title(name, color, block=12):
    key = ("t", name, color, block)
    if key not in _TEXT:
        top = tuple(min(255, int(c * 0.6 + 255 * 0.4)) for c in color)
        bot = tuple(int(c * 0.65) for c in color)
        _TEXT[key] = L.block_word(name, block, top, color, bot, tuple(int(c * 0.45) for c in color), seed=len(name))
    return _TEXT[key]


def caption(text, block=5, color=WHITE):
    key = ("c", text, block, color)
    if key not in _TEXT:
        _TEXT[key] = L.pixel_text(text, block, color)
    return _TEXT[key]


def draw_titles(frame, lt, dur, name, sub, color):
    if VERTICAL:
        return
    a = ease(lt / 0.35) * (1 - ease((lt - (dur - 0.3)) / 0.3))
    x = 90 - int(700 * (1 - ease(lt / 0.35)))
    t = title(name, color)
    blit_tl(frame, t, x, 70, a)
    c = caption(sub)
    xs = 96 - int(700 * (1 - ease((lt - 0.12) / 0.35)))
    blit_tl(frame, c, xs, 70 + t.size[1] + 6, a)


def blit_tl(frame, im, x, y, alpha=1.0):
    if alpha <= 0:
        return
    if alpha < 1:
        im = im.copy()
        im.putalpha(im.split()[3].point(lambda v: int(v * alpha)))
    frame.alpha_composite(im, (int(x), int(y)))


# ================================================================ escenas
class Scene:
    """Una especie (o una tarjeta). draw(lt) arma el cuadro a los lt segundos de empezada la escena."""
    dur = 3.2

    def sounds(self):
        return []      # [(segundo dentro de la escena, archivo o array, volumen)]


SPECIES_SCENES = [
    # id, color, entorno, tipo, nombre, frase, (yaw0, yaw1), rugido
    ("raptor", "rojizo", "land", "biped", "VELOCIRAPTOR", "CAZADOR RÁPIDO Y SALTARÍN", (112, 132), True),
    ("triceratops", "pardo", "land", "quad", "TRICERATOPS", "MONTADO ARROLLA TODO A SU PASO", (115, 135), True),
    ("dilophosaurus", "amarillo", "land", "dilo", "DILOPHOSAURUS", "ABRE LA GOLA Y ESCUPE VENENO", (112, 130), True),
    ("compsognathus", "verde", "land", "pack", "COMPSOGNATHUS", "CHIQUITOS, PERO EN MANADA", (115, 130), False),
    ("trex", "verde", "land", "biped", "TYRANNOSAURUS REX", "EL REY: SU RUGIDO PARALIZA", (115, 132), True),
    ("pteranodon", "gaviota", "sky", "flyer", "PTERANODON", "TE LLEVA VOLANDO POR EL MAPA", (130, 150), False),
    ("parasaurolophus", "azul", "land", "quad", "PARASAUROLOPHUS", "TROMPETEA Y HUYE EN MANADA", (112, 130), True),
    ("gallimimus", "naranja", "land", "runner", "GALLIMIMUS", "LA MONTURA MÁS RÁPIDA", (110, 128), False),
    ("stegosaurus", "oliva", "land", "quad", "STEGOSAURUS", "PÚAS EN LA COLA", (115, 135), False),
    ("ankylosaurus", "musgo", "land", "quad", "ANKYLOSAURUS", "UN TANQUE CON MAZA", (112, 130), True),
    ("giganotosaurus", "arena", "land", "biped", "GIGANOTOSAURUS", "EL GIGANTE ARGENTINO", (115, 132), True),
    ("carnotaurus", "rojo", "land", "biped", "CARNOTAURUS", "EL TORO CARNÍVORO", (112, 130), True),
    ("dimorphodon", "loro", "sky", "flyer", "DIMORPHODON", "VUELA A TU LADO", (130, 150), False),
    ("spinosaurus", "pantano", "river", "biped", "SPINOSAURUS", "EL GRAN PESCADOR", (115, 132), True),
    ("mosasaurus", "arrecife", "water", "swim", "MOSASAURUS", "EL TERROR DE LOS MARES", (120, 140), True),
]

PLUS_SCENES = [
    ("raptor_plus", "land", "biped", "VELOCIRAPTOR SOMBRA", "DASH: SE LANZA HACIA ADELANTE", "dash"),
    ("triceratops_plus", "land", "quad", "TRICERATOPS COSECHADOR", "COSECHA Y REPLANTA SOLO", "cosecha"),
    ("spinosaurus_plus", "river", "biped", "SPINOSAURUS PESCADOR", "PESCA PARA SU DUEÑO", "pesca"),
    ("dimorphodon_plus", "sky", "flyer", "DIMORPHODON BUSCADOR", "JUNTA LOS ÍTEMS DEL SUELO", "buscador"),
    ("parasaurolophus_plus", "land", "quad", "PARASAUROLOPHUS HERALDO", "CANTO: VELOCIDAD Y CURACIÓN", "canto"),
    ("pteranodon_plus", "sky", "flyer", "PTERANODON HALCÓN", "TURBO DE VUELO", "turbo"),
    ("gallimimus_plus", "land", "runner", "GALLIMIMUS RELÁMPAGO", "ARRANQUE: VELOCIDAD EXTRA", "arranque"),
    ("ankylosaurus_plus", "land", "quad", "ANKYLOSAURUS LEÑADOR", "TALA ÁRBOLES DE UN GOLPE", "tala"),
]

RED = (230, 30, 44)


class DinoScene(Scene):
    def __init__(self, sid, variant, env, kind, name, sub, yaws, roar, plus=False, ability=None):
        self.sid, self.variant, self.kind, self.name, self.sub = sid, variant, kind, name, sub
        self.yaws, self.roar, self.plus, self.ability = yaws, roar, plus, ability
        self.env = ENVS[(env, plus)]
        self.envkind = env
        self.dur = 3.6 if plus else 3.2
        z0, z1, h, xr = extents(sid)
        k = ALL[sid]["scale"]
        self.length = (z1 - z0) * k
        self.height = h * k
        if kind == "flyer":
            self.length = max(self.length, xr * 2 * k * 0.8)
        square = W < H * 1.3
        target = W * ((0.62 if square else 0.52) if kind in ("flyer",) else (0.86 if square else 0.68))
        if kind == "pack":
            self.length *= 2.2
        self.ppx = min(target / (self.length * 0.9 + 8), H * 0.54 / (self.height + 4))
        if kind == "swim":
            self.ppx = min(target / (self.length * 0.85), H * 0.5 / (self.height + 4))
        self.screen_origin = (W * (0.62 if kind == "flyer" else 0.55),
                              GROUND + (int(H * 0.08) if env == "river" else 0)
                              if kind not in ("flyer", "swim") else H * (0.6 if kind == "flyer" else 0.55))
        self.fit()

    def fit(self):
        """Encuadre: lienzo grande (nada se corta al renderizar), y se mide la silueta en varios momentos de la
        escena; si en algun momento no entra en el cuadro (por ancho o por alto, debajo de los titulos), se achica.
        Despues se centra a lo ancho."""
        cw, ch = int(W * 1.8), int(H * 1.5)
        self.canvas = (cw, ch)
        self.origin_row = int(ch * (0.5 if self.kind in ("flyer", "swim") else 0.8))
        self.xfix = 0
        box = None
        for f in (0.0, 0.2, 0.4, 0.55, 0.7, 0.85, 0.97):
            bb = self.layer(self.dur * f).getbbox()
            if bb:
                box = bb if box is None else (min(box[0], bb[0]), min(box[1], bb[1]), max(box[2], bb[2]),
                                              max(box[3], bb[3]))
        if not box:
            return
        cx0, cy0 = cw / 2, self.origin_row
        k_w = W * 0.94 / max(1, box[2] - box[0])
        if self.kind in ("flyer", "swim"):
            k_h = H * 0.6 / max(1, box[3] - box[1])
        else:
            room = self.screen_origin[1] - H * 0.2           # del suelo hasta debajo de los titulos
            k_h = room / max(1, cy0 - box[1])
        k = min(1.0, k_w, k_h)
        self.ppx *= k
        self.xfix = -(((box[0] + box[2]) / 2 - cx0) * k)

    # ---------------- pose y movimiento en el tiempo
    def walk_amount(self, lt):
        if self.ability in ("arranque", "dash", "turbo"):
            return 1.0
        if self.roar:
            return 1.0 - ease((lt - 1.8) / 0.3)
        return 1.0

    def roar_amount(self, lt):
        if not self.roar or self.plus:
            return 0.0
        return ease((lt - 2.0) / 0.25) * (1 - ease((lt - 3.0) / 0.2))

    def pose(self, lt):
        w, r = self.walk_amount(lt), self.roar_amount(lt)
        k = self.height / 32
        if self.ability == "canto":
            r = ease((lt - 1.0) / 0.25) * (1 - ease((lt - 2.8) / 0.3))
            w = 1 - ease((lt - 0.7) / 0.3)
        if self.ability == "tala":
            w = 1 - ease((lt - 0.8) / 0.3)
        if self.kind in ("biped", "dilo"):
            p = biped(lt, w, r, k=k)
            if self.kind == "dilo":
                frill(p, ease((lt - 1.7) / 0.3))
            return p
        if self.kind == "runner":
            fast = 1.8 if self.ability == "arranque" and lt > 1.0 else 1.25
            return biped(lt, w, r, swing=46, f=fast, k=k)
        if self.kind == "pack":
            return biped(lt, w, r, swing=30, f=1.8, k=k)
        if self.kind == "quad":
            p = quad(lt, w, r, k=k)
            if self.ability == "tala":
                sw = math.sin(clamp((lt - 1.3) / 0.45, 0, 1) * math.pi * 2) * (1 - clamp((lt - 1.3) / 0.9, 0, 1))
                p["tail1"] = (p["tail1"][0], p["tail1"][1] + 38 * sw, 0, 0)
                p["tail2"] = (p["tail2"][0], p["tail2"][1] + 48 * sw, 0, 0)
            return p
        if self.kind == "flyer":
            return flyer(lt, 1.0, fast=self.ability == "turbo" and lt > 1.2)
        if self.kind == "swim":
            bite = ease((lt - 2.0) / 0.2) * (1 - ease((lt - 2.6) / 0.25)) if self.roar else 0
            return swimmer(lt, 0.8, bite)
        return {}

    def instances(self, lt):
        p = self.pose(lt)
        if self.kind == "pack":
            out = []
            for i, (dx, dz, ph) in enumerate(((0, 0, 0), (18, -26, 0.37), (-16, 24, 0.71))):
                out.append((self.sid, self.variant, biped(lt + ph, self.walk_amount(lt), 0, swing=30, f=1.8,
                                                          k=self.height / 32), (dx, 0, dz)))
            return out
        return [(self.sid, self.variant, p, (0, 0, 0))]

    def yaw(self, lt):
        u = lt / self.dur
        return self.yaws[0] + (self.yaws[1] - self.yaws[0]) * ease(u)

    def layer(self, lt):
        # camara un poco por arriba (como se ve en el juego): de abajo los nidos y los dinos de atras quedaban raros
        return dino_layer(self.instances(lt), self.yaw(lt), 10 if self.kind not in ("flyer",) else 6, self.ppx,
                          self.canvas, self.origin_row, rim=RED if self.plus else PINK)

    def travel(self, lt):
        speed = 320
        if self.ability in ("arranque",) and lt > 1.0:
            return 320 * 1.0 + (lt - 1.0) * 1100
        if self.ability == "turbo" and lt > 1.2:
            return 320 * 1.2 + (lt - 1.2) * 1300
        if self.kind == "flyer":
            speed = 420
        if self.kind == "swim":
            speed = 160
        if self.roar and not self.plus:
            # se frena para rugir
            a = clamp(lt, 0, 1.8)
            b = clamp(lt - 1.8, 0, 0.3)
            return speed * (a + b * (1 - b / 0.6))
        return speed * lt

    # ---------------- cuadro
    def draw(self, lt):
        frame = Image.new("RGBA", (W, H))
        self.env.draw(frame, -self.travel(lt), lt)
        ox, oy = self.screen_origin
        bob = 0
        if self.kind == "flyer":
            bob = math.sin(lt * 2.2) * 18
        if self.kind == "swim":
            bob = math.sin(lt * 1.3) * 14
        dash = 0
        if self.ability == "dash":
            dd = 380 * W / 1920
            dash = -ease((lt - 1.3) / 0.35) * dd + ease((lt - 2.4) / 0.8) * dd
        layer = self.layer(lt)
        px = int(ox - self.canvas[0] / 2 + self.xfix + dash)
        py = int(oy - self.origin_row + bob)
        if self.kind not in ("flyer", "swim"):
            self.shadow(frame, layer, px, py, oy)
        self.effects_back(frame, lt, px, py, layer)
        if self.ability == "dash" and 1.25 < lt < 2.0:
            for k, a in ((0.12, 0.25), (0.07, 0.45)):
                dprev = -ease((lt - k - 1.3) / 0.35) * 380 * W / 1920
                ghost = self.layer(lt - k)
                g = ghost.copy()
                g.putalpha(g.split()[3].point(lambda v: int(v * a)))
                tint = Image.new("RGBA", g.size, RED + (0,))
                tint.putalpha(g.split()[3].point(lambda v: int(v * 0.6)))
                g.alpha_composite(tint)
                frame.alpha_composite(g, (int(ox - self.canvas[0] / 2 + self.xfix + dprev), py))
        frame.alpha_composite(layer, (px, py))
        if self.envkind == "river":
            self.env.water_front(frame, lt, int(H * 0.74) + 120)
        if self.envkind == "water":
            self.env.bubbles(frame, lt)
        self.effects_front(frame, lt, px, py, layer)
        draw_titles(frame, lt, self.dur, self.name, self.sub, RED if self.plus else PINK)
        return frame

    def shadow(self, frame, layer, px, py, ground):
        bb = layer.getbbox()
        if not bb:
            return
        w = (bb[2] - bb[0]) * 0.75
        cx = px + (bb[0] + bb[2]) / 2
        sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(sh).ellipse((cx - w / 2, ground - 22, cx + w / 2, ground + 22), fill=(0, 0, 0, 110))
        frame.alpha_composite(sh.filter(ImageFilter.GaussianBlur(10)))

    # ---------------- efectos de las habilidades (Plus)
    def head_pos(self, layer, px, py):
        """Punto aproximado de la cabeza: el borde izquierdo de arriba de la silueta."""
        bb = layer.getbbox()
        if not bb:
            return W / 2, H / 2
        return px + bb[0] + (bb[2] - bb[0]) * 0.12, py + bb[1] + (bb[3] - bb[1]) * 0.2

    def effects_back(self, frame, lt, px, py, layer):
        ab = self.ability
        rnd = random.Random(7)
        if ab == "cosecha":
            # trigo plantado que el triceratops va cosechando (aparece maduro, salta como item y vuelve a crecer)
            for i in range(22):
                x0 = i * 130 + 40
                x = (x0 + self.travel(lt)) % (W + 200) - 100
                grown = 1.0
                passed = x > W * 0.45
                spr = sprite("wheat", 10 if not passed else 6)
                blit(frame, spr, x, GROUND - spr.size[1] / 2 + 6)
        if ab == "tala":
            tx = W * 0.9
            fell = clamp((lt - 1.6) / 0.6, 0, 1)
            if fell < 1:
                d = ImageDraw.Draw(frame)
                a = int(255 * (1 - fell))
                d.rectangle((tx - 22, GROUND - 300, tx + 22, GROUND), fill=(110, 76, 44, a))
                for k in range(3):
                    r = 120 - k * 30
                    cy = GROUND - 300 - k * 70
                    d.rectangle((tx - r, cy - 60, tx + r, cy + 40), fill=(60, 130, 50, a))
        if ab == "pesca":
            pass
        if ab == "turbo" and lt > 1.2:
            self.speed_lines(frame, lt, RED, 30)
        if ab in ("arranque",) and lt > 1.0:
            self.speed_lines(frame, lt, (255, 230, 120), 26)

    def effects_front(self, frame, lt, px, py, layer):
        ab = self.ability
        rnd = random.Random(11)
        hx, hy = self.head_pos(layer, px, py)
        if ab == "dash" and 1.2 < lt < 2.2:
            self.speed_lines(frame, lt, RED, 22)
        if ab == "cosecha":
            for i in range(14):
                t0 = 0.4 + i * 0.2
                u = (lt - t0) / 0.8
                if 0 <= u <= 1:
                    x = W * 0.5 + rnd.uniform(-260, 260)
                    y = GROUND - 30 - 260 * math.sin(u * math.pi * 0.9) - 120 * u
                    blit(frame, sprite("wheat", 8), x + 300 * u, y, 1 - ease((u - 0.8) / 0.2))
            chest = sprite("chest", 9)
            blit(frame, chest, W - 200, GROUND - 60)
            n = int(clamp((lt - 0.6) / 2.4, 0, 1) * 64)
            blit_tl(frame, caption(f"+{n}", 6, (255, 230, 120)), W - 260, GROUND - 190)
        if ab == "pesca":
            for i in range(7):
                t0 = 0.5 + i * 0.4
                u = (lt - t0) / 0.9
                if 0 <= u <= 1:
                    x0 = W * 0.15 + i * 90
                    x = x0 + (hx - x0) * u
                    y = H * 0.9 - 420 * math.sin(u * math.pi) + (hy - H * 0.9) * u * 0.6
                    blit(frame, sprite("fish", 9), x, y, 1 - ease((u - 0.85) / 0.15))
                    if u < 0.15:
                        self.splash(frame, x0, H * 0.86, u / 0.15)
        if ab == "buscador":
            for i in range(16):
                t0 = 0.3 + i * 0.16
                u = clamp((lt - t0) / 0.7, 0, 1.2)
                if u <= 0:
                    continue
                name = ("diamond", "gold", "emerald")[i % 3]
                x0 = rnd.uniform(120, W - 120)
                y0 = H * 0.92
                x = x0 + (hx + 80 - x0) * ease(u)
                y = y0 + (hy + 120 - y0) * ease(u) - math.sin(u * math.pi) * 80
                if u < 1:
                    blit(frame, sprite(name, 9), x, y)
                    self.trail(frame, x, y, x0, y0, u)
        if ab == "canto" and lt > 1.0:
            for k in range(3):
                r = ((lt - 1.0 - k * 0.45) % 1.35) * 520
                if r > 0:
                    ring = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                    ImageDraw.Draw(ring).ellipse((hx - r, hy - r * 0.55, hx + r, hy + r * 0.55),
                                                 outline=RED + (int(200 * (1 - r / 700)),), width=10)
                    frame.alpha_composite(ring)
            for i in range(14):
                t0 = 1.0 + i * 0.13
                u = (lt - t0) / 1.2
                if 0 <= u <= 1:
                    x = hx + rnd.uniform(-120, 160) + math.sin(u * 8 + i) * 30
                    y = hy - 40 - 380 * u
                    blit(frame, sprite("note" if i % 3 else "heart", 9), x, y, 1 - ease((u - 0.7) / 0.3))
        if ab == "arranque" and lt > 1.0:
            for i in range(8):
                ph = (lt * 6 + i * 0.37) % 1
                if ph < 0.35:
                    self.bolt(frame, px + layer.size[0] * rnd.uniform(0.3, 0.7), py + layer.size[1] * rnd.uniform(0.4, 0.8), i)
        if ab == "turbo" and lt > 1.2:
            bb = layer.getbbox()
            if bb:
                tx, ty = px + bb[2] - 40, py + (bb[1] + bb[3]) / 2
                for i in range(18):
                    u = ((lt * 3 + i / 18) % 1)
                    x = tx + u * 520
                    y = ty + math.sin(i * 1.7 + lt * 9) * 40 * u
                    s = int(26 * (1 - u)) + 4
                    c = L.lerp((255, 230, 120), RED, u)
                    ImageDraw.Draw(frame).rectangle((x, y, x + s, y + s), fill=c + (int(230 * (1 - u)),))
        if ab == "tala":
            tx = W * 0.9
            u = (lt - 1.6) / 1.4
            if 0 <= u <= 1:
                for i in range(18):
                    vx, vy = rnd.uniform(-420, 420), rnd.uniform(-900, -300)
                    x = tx + vx * u
                    y = GROUND - 220 + vy * u + 1400 * u * u
                    blit(frame, sprite("log" if i % 2 else "leaf", 10), x, y, 1 - ease((u - 0.75) / 0.25))

    @staticmethod
    def speed_lines(frame, lt, color, n):
        rnd = random.Random(5)
        ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(ov)
        for i in range(n):
            y = rnd.uniform(H * 0.15, H * 0.9)
            ln = rnd.uniform(160, 420)
            x = (rnd.uniform(0, W) + lt * rnd.uniform(1800, 2600)) % (W + ln) - ln
            d.rectangle((x, y, x + ln, y + rnd.choice((4, 6, 8))), fill=color + (rnd.randint(90, 170),))
        frame.alpha_composite(ov)

    @staticmethod
    def splash(frame, x, y, u):
        d = ImageDraw.Draw(frame)
        for k in range(6):
            a = k / 6 * math.pi
            r = 30 + 60 * u
            d.rectangle((x + math.cos(a) * r - 6, y - math.sin(a) * r - 6, x + math.cos(a) * r + 6,
                         y - math.sin(a) * r + 6), fill=(220, 240, 255, int(220 * (1 - u))))

    @staticmethod
    def trail(frame, x, y, x0, y0, u):
        d = ImageDraw.Draw(frame)
        for k in range(1, 6):
            v = max(0, u - k * 0.04)
            tx = x0 + (x - x0) * (v / max(u, 1e-3))
            ty = y0 + (y - y0) * (v / max(u, 1e-3))
            s = 10 - k
            d.rectangle((tx - s, ty - s, tx + s, ty + s), fill=(255, 255, 255, 140 - k * 22))

    @staticmethod
    def bolt(frame, x, y, seed):
        rnd = random.Random(seed * 13 + int(x))
        d = ImageDraw.Draw(frame)
        pts = [(x, y)]
        for _ in range(6):
            x += rnd.uniform(-40, 40)
            y += rnd.uniform(-50, -20)
            pts.append((x, y))
        d.line(pts, fill=(255, 250, 180, 255), width=7)
        d.line(pts, fill=(255, 255, 255, 255), width=3)

    def sounds(self):
        base = self.sid.replace("_plus", "")
        out = [(0.25, f"{base}/ambient1", 0.55)]
        if self.roar and not self.plus:
            out.append((2.0, f"{base}/roar1", 0.9))
        if self.kind in ("biped", "quad", "dilo", "runner") and not self.plus:
            for k in range(3):
                out.append((0.45 + k * 0.6, f"{base}/step{1 + k % 2}", 0.35))
        if self.kind == "flyer":
            out.append((0.5, f"{base}/flap1", 0.4))
            out.append((1.6, f"{base}/flap1", 0.4))
        if self.kind == "swim":
            out.append((0.6, f"{base}/swim1", 0.6))
        sfx = {"dash": [(1.3, "whoosh", 0.9)], "turbo": [(1.2, "whoosh", 1.0), (1.3, "roar", 0.5)],
               "arranque": [(1.0, "zap", 0.7), (1.4, "zap", 0.6), (1.9, "zap", 0.6)],
               "canto": [(1.0, f"{base}/roar1", 0.9), (1.1, "chime", 0.8)],
               "cosecha": [(0.6 + i * 0.25, "pop", 0.5) for i in range(9)],
               "pesca": [(0.5 + i * 0.4, "splash", 0.6) for i in range(7)],
               "buscador": [(1.0 + i * 0.16, "pop", 0.45) for i in range(14)],
               "tala": [(1.3, f"{base}/attack1", 0.9), (1.6, "crack", 0.9)]}
        for at, name, vol in sfx.get(self.ability, []):
            out.append((at, base + "/roar1" if name == "roar" else name, vol))
        return out


class CardScene(Scene):
    """Tarjeta de seccion: palabra grande de bloques sobre el fondo."""

    def __init__(self, text, color, plus=False, dur=1.8, sub=None):
        self.text, self.color, self.plus, self.dur, self.sub = text, color, plus, dur, sub
        self.bg = L.background(W, H) if not plus else self.red_bg()

    @staticmethod
    def red_bg():
        bg = gradient([(0, (10, 2, 4)), (0.6, (50, 6, 10)), (1, (110, 16, 22))])
        return bg

    def draw(self, lt):
        frame = self.bg.copy()
        if self.plus:
            rnd = random.Random(3)
            d = ImageDraw.Draw(frame)
            for _ in range(70):
                x0, sp, ph = rnd.uniform(0, W), rnd.uniform(80, 220), rnd.uniform(0, 20)
                y = H - ((lt + ph) * sp) % (H + 40)
                s = rnd.choice((4, 6, 8))
                d.rectangle((x0, y, x0 + s, y + s), fill=(255, rnd.randint(60, 150), 40, 200))
        im = title(self.text, self.color, block=26)
        k = (0.9 + 0.1 * ease(lt / 0.4)) * min(1.0, W * 0.9 / im.size[0])
        im2 = im.resize((int(im.size[0] * k), int(im.size[1] * k)), Image.NEAREST)
        a = ease(lt / 0.3) * (1 - ease((lt - (self.dur - 0.25)) / 0.25))
        y = H / 2 - im2.size[1] / 2 - (40 if self.sub else 0)
        blit_tl(frame, im2, W / 2 - im2.size[0] / 2, y, a)
        if self.sub:
            c = caption(self.sub, 7)
            if c.size[0] > W * 0.92:
                kc = W * 0.92 / c.size[0]
                c = c.resize((int(c.size[0] * kc), int(c.size[1] * kc)), Image.LANCZOS)
            blit_tl(frame, c, W / 2 - c.size[0] / 2, y + im2.size[1] + 20, ease((lt - 0.25) / 0.3) * a)
        return frame

    def sounds(self):
        return [(0.0, "boom", 0.9)]


class LogoScene(Scene):
    def __init__(self, dur=4.5, outro=False):
        self.dur, self.outro = dur, outro
        self.bg = L.background(W, H)
        self.logo = Image.open(os.path.join(ROOT, "docs", "logo", "dinoearth-logo.png")).convert("RGBA")
        k = min(W * 0.84 / self.logo.size[0], H * 0.86 / self.logo.size[1])
        self.logo = self.logo.resize((int(self.logo.size[0] * k), int(self.logo.size[1] * k)), Image.LANCZOS)

    def draw(self, lt):
        frame = self.bg.copy()
        start = 0.0 if self.outro else 0.9
        a = ease((lt - start) / 0.6)
        if not self.outro:
            a *= 1 - ease((lt - (self.dur - 0.35)) / 0.35)
        k = 1.06 - 0.06 * ease((lt - start) / 2.5)
        im = self.logo.resize((int(self.logo.size[0] * k), int(self.logo.size[1] * k)), Image.LANCZOS)
        blit_tl(frame, im, W / 2 - im.size[0] / 2, H / 2 - im.size[1] / 2, a)
        if lt < start:
            frame = Image.new("RGBA", (W, H), (0, 0, 0, 255))
        if self.outro:
            frame = Image.blend(frame, Image.new("RGBA", (W, H), (0, 0, 0, 255)), ease((lt - (self.dur - 0.8)) / 0.8))
        return frame

    def sounds(self):
        return [(0.85, "trex/roar1", 1.0), (0.85, "boom", 1.0)] if not self.outro else [(0.0, "trex/roar2", 0.9),
                                                                                          (0.0, "boom", 0.8)]


# ================================================================ como se juega (items y sistemas)
IMG = os.path.join(ROOT, "docs", "guia", "img")
_PIC = {}


def picture(name, height=None, width=None, nearest=True):
    """Imagen de docs/guia/img (capturas de los menus, items) escalada."""
    key = (name, height, width)
    if key not in _PIC:
        im = Image.open(os.path.join(IMG, name)).convert("RGBA")
        if height or width:
            k = height / im.size[1] if height else width / im.size[0]
            im = im.resize((max(1, int(im.size[0] * k)), max(1, int(im.size[1] * k))),
                           Image.NEAREST if nearest and k >= 1 else Image.LANCZOS)
        _PIC[key] = im
    return _PIC[key]


def card_shadow(frame, im, x, y, alpha=1.0):
    """Captura con sombra (como una tarjeta apoyada)."""
    if alpha <= 0:
        return
    sh = Image.new("RGBA", (im.size[0] + 60, im.size[1] + 60), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rectangle((30, 30, 30 + im.size[0], 30 + im.size[1]), fill=(0, 0, 0, int(140 * alpha)))
    frame.alpha_composite(sh.filter(ImageFilter.GaussianBlur(14)), (int(x) - 18, int(y) - 14))
    blit_tl(frame, im, x, y, alpha)


def poof(frame, x, y, u, n=14, spread=160, seed=1):
    """Nube de humo cuadrada (aparecer, desaparecer, guardar en la jaula)."""
    if not 0 <= u <= 1:
        return
    rnd = random.Random(seed)
    d = ImageDraw.Draw(frame)
    for _ in range(n):
        a = rnd.uniform(0, math.tau)
        r = spread * (0.3 + 0.7 * ease(u)) * rnd.uniform(0.5, 1)
        sz = rnd.randint(14, 30) * (1 - u * 0.6)
        cx, cy = x + math.cos(a) * r, y + math.sin(a) * r * 0.6 - 40 * u
        d.rectangle((cx - sz, cy - sz, cx + sz, cy + sz), fill=(240, 240, 250, int(220 * (1 - u))))


def slide_in(lt, t0, t1, dist):
    """Desplazamiento de algo que entra en t0 y sale en t1 (0 = en su lugar)."""
    return -dist * (1 - ease((lt - t0) / 0.35)) + dist * ease((lt - t1) / 0.35)


class FeatureScene(DinoScene):
    """Escena de un sistema del plugin con dinos en el mundo (domesticar, montura, silbato, jaula...)."""

    def __init__(self, feature, sid, variant, kind, name, sub, env="land", dur=3.6, yaws=(115, 130)):
        self.feature = feature
        super().__init__(sid, variant, env, kind, name, sub, yaws, False)
        self.dur = dur
        if feature == "whistle":
            self.ppx *= 0.62
        if feature == "egg":
            self.ppx = 20.0 * H / 1080
        if feature == "nest":
            self.ppx *= 0.9
        if feature == "race":
            self.ppx *= 0.8
        self.fit()

    def walk_amount(self, lt):
        f = self.feature
        if f == "tame":
            return 1 - ease((lt - 0.9) / 0.3)
        if f in ("saddle", "whistle", "race"):
            return 1.0
        return 0.0

    def travel(self, lt):
        f = self.feature
        if f == "tame":
            return 320 * min(lt, 1.05)
        if f in ("saddle", "whistle"):
            return 320 * lt
        if f == "race":
            return 0 if lt < 1.0 else (lt - 1.0) * 1200
        return 0

    def pose(self, lt):
        f = self.feature
        k = self.height / 32
        if f == "race":
            w = 0.0 if lt < 1.0 else 1.0
            return biped(lt, w, 0, swing=46, f=1.9, k=k)
        p = biped(lt, self.walk_amount(lt), 0, k=k)
        if f == "tame":
            e = ease((lt - 1.4) / 0.2) * (1 - ease((lt - 1.9) / 0.2))      # baja la cabeza y come
            p["head"] = (p["head"][0] + 30 * e, 0, 0, 0)
            p["jaw"] = (abs(math.sin(lt * 14)) * 22 * e, 0, 0, 0)
            r = ease((lt - 2.3) / 0.2) * (1 - ease((lt - 3.1) / 0.2))      # y ruge contento
            p["head"] = (p["head"][0] - 20 * r, 0, 0, 0)
            p["jaw"] = (p["jaw"][0] + 30 * r, 0, 0, 0)
        if f == "feeder":
            e = 1 - ease((lt - 3.0) / 0.3)
            p["head"] = (32 * e, 0, 0, 0)
            p["jaw"] = (abs(math.sin(lt * 9)) * 24 * e, 0, 0, 0)
            p["body"] = (6 * e, 0, 0, p["body"][3])
        if f in ("nest", "recall"):
            t0 = 1.6 if f == "nest" else 2.2
            r = ease((lt - t0) / 0.25) * (1 - ease((lt - t0 - 1.1) / 0.25))
            p["head"] = (p["head"][0] - 22 * r, 8 * r, 0, 0)
            p["jaw"] = (38 * r + math.sin(lt * 40) * 3 * r, 0, 0, 0)
        return p

    def instances(self, lt):
        f = self.feature
        p = self.pose(lt)
        if f == "saddle":
            return [(self.sid, self.variant, p, (0, 0, 0), {"saddle": lt > 1.15})]
        if f == "whistle":
            out = []
            for sid, var, (dx, dz), ph in (("raptor", "rojizo", (0, 0), 0), ("carnotaurus", "rojo", (34, -70), 0.33),
                                           ("gallimimus", "naranja", (-30, -140), 0.66)):
                out.append((sid, var, biped(lt + ph, 1.0, 0, swing=34 if sid != "gallimimus" else 46, f=1.1),
                            (dx, 0, dz)))
            return out
        if f == "recall":
            sc = ease((lt - 1.4) / 0.25)
            return [(self.sid, self.variant, p, (0, 0, 0), {"scale": sc})]
        if f == "cage":
            sc = 1 - ease((lt - 1.1) / 0.35) + ease((lt - 2.7) / 0.3)
            return [(self.sid, self.variant, p, (0, 0, 0), {"scale": sc})]
        if f == "egg":
            out = []
            if lt < 1.85:
                wob = math.sin(lt * 22) * (10 if 0.6 < lt % 0.9 < 0.85 or lt > 1.5 else 0)
                out.append(("raptor", "egg_cracked" if lt > 1.0 else "egg", {"egg": (wob * 0.4, 0, wob, 0)},
                            (0, 0, 0), {"form": None}))
            else:
                g = ease((lt - 1.85) / 0.25)
                bp = biped(lt, 0.0, 0, k=0.5)
                bp["head"] = (math.sin(lt * 6) * 6, 0, 0, 0)
                out.append(("raptor", "verde", bp, (0, 0, 0), {"form": "baby", "scale": 0.3 + 0.7 * g}))
            return out
        if f == "nest":
            out = [(self.sid, self.variant, p, (-6, 0, -28)), ("@nest", None, {}, (34, 0, 30), {"scale": 1.5})]
            for ex, ez in ((-4.5, -3), (4.5, -2), (0, 4.5)):
                out.append(("trex", "egg", {}, (34 + ex * 1.5, 1.5, 30 + ez * 1.5), {"form": None, "scale": 0.85}))
            return out
        if f == "feeder":
            return [(self.sid, self.variant, p, (0, 0, 0)), ("@feeder/meat_3", None, {}, (0, 0, 33), {"scale": 1.6})]
        if f == "race":
            out = []
            for var, dx, dz, ph in (("naranja", 26, 6, 0), ("arena", -26, -12, 0.31)):
                w = 0.0 if lt < 1.0 else 1.0
                out.append(("gallimimus", var, biped(lt + ph, w, 0, swing=46, f=1.9), (dx, 0, dz)))
            return out
        return [(self.sid, self.variant, p, (0, 0, 0))]

    def effects_back(self, frame, lt, px, py, layer):
        if self.feature == "race":
            if lt > 1.0:
                self.speed_lines(frame, lt, (255, 255, 255), 24)
            fl = sprite("flag", 14)
            blit(frame, fl, W * 0.12 + math.sin(lt * 6) * 4, GROUND - fl.size[1] / 2)

    def effects_front(self, frame, lt, px, py, layer):
        f = self.feature
        hx, hy = self.head_pos(layer, px, py)
        rnd = random.Random(21)
        if f == "tame":
            u = (lt - 0.9) / 0.5
            if u <= 1.05:
                x = W * 0.05 + (hx - W * 0.05) * ease(u)
                y = H * 0.35 + (hy + 40 - H * 0.35) * ease(u) - math.sin(clamp(u, 0, 1) * math.pi) * 120
                blit(frame, sprite("meat", 12), x, y, 1 - ease((u - 0.95) / 0.1))
            for i in range(12):
                v = (lt - 2.0 - i * 0.06) / 1.0
                if 0 <= v <= 1:
                    x = hx + rnd.uniform(-140, 200)
                    y = hy - 30 - 260 * v
                    blit(frame, sprite("heart", 9), x, y, 1 - ease((v - 0.7) / 0.3))
        if f == "saddle":
            icon = picture("item_montura_trex.png", height=int(190 * H / 1080))
            u = (lt - 0.3) / 0.85
            if 0 <= u <= 1:
                bb = layer.getbbox()
                tx = px + (bb[0] + bb[2]) / 2 if bb else W / 2
                ty = py + bb[1] + (bb[3] - bb[1]) * 0.35 if bb else H / 2
                x = W * 0.85 + (tx - W * 0.85) * ease(u)
                y = H * 0.25 + (ty - H * 0.25) * ease(u)
                blit(frame, icon, x, y, 1 - ease((u - 0.9) / 0.1))
            bb = layer.getbbox()
            if bb:
                poof(frame, px + (bb[0] + bb[2]) / 2, py + bb[1] + (bb[3] - bb[1]) * 0.35, (lt - 1.15) / 0.5, seed=4)
        if f == "whistle":
            icon = picture("item_silbato.png", height=int(170 * H / 1080))
            a = ease((lt - 0.2) / 0.3)
            blit(frame, icon, W * 0.86, H * 0.3 + math.sin(lt * 8) * 6 * a, a)
            for i in range(6):          # notas del silbato
                v = (lt - 0.4 - i * 0.12) / 0.9
                if 0 <= v <= 1:
                    blit(frame, sprite("note", 7), W * 0.86 - 60 - 140 * v, H * 0.24 - 120 * v, 1 - v)
            menu = picture("menu_c_silbato.png", height=int(H * 0.62))
            card_shadow(frame, menu, 60 + slide_in(lt, 0.8, 2.9, 900), H * 0.2)
        if f == "recall":
            menu = picture("menu_c_llamar.png", height=int(H * 0.62))
            card_shadow(frame, menu, 60 + slide_in(lt, 0.2, 1.2, 900), H * 0.2)
            bb = layer.getbbox()
            if bb:
                poof(frame, px + (bb[0] + bb[2]) / 2, py + (bb[1] + bb[3]) / 2, (lt - 1.35) / 0.6, n=20, spread=260)
        if f == "cage":
            bb = self.layer(0.0).getbbox()
            cx = px + (bb[0] + bb[2]) / 2 if bb else W / 2
            cy = py + (bb[1] + bb[3]) / 2 if bb else H / 2
            u = (lt - 0.4) / 0.6
            empty = picture("item_jaula.png", height=int(200 * H / 1080))
            full = picture("item_jaula_llena.png", height=int(240 * H / 1080))
            if 0 <= u < 1.15 and lt < 1.45:
                blit(frame, empty, W * 0.85 + (cx - W * 0.85) * ease(u), H * 0.2 + (cy - H * 0.2) * ease(u))
            poof(frame, cx, cy, (lt - 1.1) / 0.5, n=20, spread=240, seed=2)
            if 1.45 <= lt < 2.7:
                b = abs(math.sin((lt - 1.45) * 6)) * 30 * (1 - ease((lt - 1.45) / 1.0))
                blit(frame, full, cx, cy - b)
            poof(frame, cx, cy, (lt - 2.65) / 0.5, n=20, spread=240, seed=3)
        if f == "egg":
            u = (lt - 1.8) / 0.6
            if 0 <= u <= 1:
                cx = W * 0.55
                for i in range(16):
                    a = rnd.uniform(-math.pi, 0)
                    r = 260 * ease(u) * rnd.uniform(0.5, 1)
                    x = cx + math.cos(a) * r
                    y = GROUND - 120 + math.sin(a) * r + 300 * u * u
                    ImageDraw.Draw(frame).rectangle((x - 9, y - 9, x + 9, y + 9), fill=(236, 226, 200, int(255 * (1 - u))))
            if lt > 2.2:
                a = ease((lt - 2.2) / 0.3)
                lines = ["GENES", "VIDA 108%", "VELOCIDAD 96%", "TAMAÑO 112%", "MADRE: VERDE", "PADRE: ALBINO"]
                panel_w, line_h = int(470 * H / 1080), int(58 * H / 1080)
                x0, y0 = W - panel_w - 60, H * 0.18
                bg = Image.new("RGBA", (panel_w, line_h * len(lines) + 40), (20, 10, 30, int(200 * a)))
                frame.alpha_composite(bg, (int(x0), int(y0)))
                for i, ln in enumerate(lines):
                    c = caption(ln, 6, PINK if i == 0 else WHITE)
                    blit_tl(frame, c, x0 + 14, y0 + 14 + i * line_h, ease((lt - 2.2 - i * 0.08) / 0.25))
        if f == "feeder":
            for i in range(10):
                v = ((lt * 1.3 + i * 0.1) % 1)
                x = hx + rnd.uniform(-60, 60)
                y = hy + 60 - 80 * v + 160 * v * v
                ImageDraw.Draw(frame).rectangle((x - 7, y - 7, x + 7, y + 7), fill=(200, 60, 60, int(220 * (1 - v))))
        if f == "race" and lt < 1.0:
            n = 3 - int(lt / 0.34)
            c = title(str(n), PINK, block=30)
            blit(frame, c, W / 2, H * 0.38, 1 - ease(((lt % 0.34) - 0.22) / 0.12))

    def sounds(self):
        snd = {
            "tame": [(0.3, "raptor/ambient1", 0.5), (1.4, "eat", 0.8), (2.3, "raptor/roar1", 0.8), (2.0, "chime", 0.6)],
            "saddle": [(0.3, "whoosh", 0.6), (1.15, "boom", 0.6), (1.4, "trex/step1", 0.5), (2.0, "trex/step2", 0.5)],
            "whistle": [(0.4, "whistle", 0.9), (0.8, "page", 0.6), (1.5, "raptor/ambient1", 0.5)],
            "recall": [(0.2, "page", 0.6), (0.9, "whistle", 0.9), (1.35, "whoosh", 0.7), (2.2, "raptor/roar1", 0.8)],
            "cage": [(0.4, "whoosh", 0.5), (1.1, "chain", 0.9), (2.65, "chain", 0.8), (2.8, "raptor/ambient1", 0.5)],
            "egg": [(0.5, "egg/crack1", 0.8), (1.0, "egg/crack2", 0.8), (1.5, "egg/crack3", 0.8), (1.8, "egg/hatch1", 1.0),
                    (2.2, "chime", 0.5)],
            "nest": [(0.3, "trex/ambient1", 0.6), (1.6, "trex/roar1", 1.0)],
            "feeder": [(0.3, "eat", 0.7), (1.1, "eat", 0.7), (1.9, "eat", 0.7), (2.7, "eat", 0.6)],
            "race": [(0.0, "beep", 0.8), (0.34, "beep", 0.8), (0.68, "beep", 0.8), (1.0, "beep2", 0.9),
                     (1.0, "whoosh", 0.8), (1.6, "gallimimus/ambient1", 0.5)],
        }
        return snd.get(self.feature, [])


class PanelScene(Scene):
    """Capturas de los menus sobre el fondo del logo (Dinopedia, misiones y mapa)."""

    def __init__(self, feature, name, sub, dur=4.0):
        self.feature, self.name, self.sub, self.dur = feature, name, sub, dur
        self.bg = L.background(W, H)

    def draw(self, lt):
        frame = self.bg.copy()
        square = W < H * 1.3
        h = int(H * (0.55 if square else 0.66))
        if self.feature == "dinopedia":
            book = picture("item_dinopedia.png", height=int(230 * H / 1080))
            blit(frame, book, W * 0.1, H * 0.55 + math.sin(lt * 3) * 8, ease(lt / 0.3))
            for i, name in enumerate(("menu_c_indice.png", "menu_c_especie.png", "menu_c_ficha.png")):
                t0 = 0.35 + i * 0.8
                im = picture(name, height=h)
                step = W * (0.17 if square else 0.22)
                x = W * (0.16 if square else 0.2) + i * step + slide_in(lt, t0, 99, -1400)
                card_shadow(frame, im, x, H * (0.24 if square else 0.2) + i * 18, ease((lt - t0) / 0.3))
        if self.feature == "missions":
            im = picture("menu_c_misiones.png", height=h)
            card_shadow(frame, im, W * 0.05 + slide_in(lt, 0.3, 99, -1200), H * 0.2, ease((lt - 0.3) / 0.3))
            m = Image.open(os.path.join(ROOT, "plugin", "src", "main", "resources", "mapa", "tierra.png")).convert("RGBA")
            mh = int(h * (0.78 if square else 1.0))
            m = m.resize((mh, mh), Image.NEAREST)
            card_shadow(frame, m, W * 0.95 - mh + slide_in(lt, 1.0, 99, 1200), H * (0.36 if square else 0.2),
                        ease((lt - 1.0) / 0.3))
        draw_titles(frame, lt, self.dur, self.name, self.sub, PINK)
        return frame

    def sounds(self):
        if self.feature == "dinopedia":
            return [(0.35, "page", 0.8), (1.15, "page", 0.8), (1.95, "page", 0.8), (2.6, "chime", 0.5)]
        return [(0.3, "page", 0.8), (1.0, "page", 0.8), (1.6, "chime", 0.6)]


FEATURES = [
    ("tame", "raptor", "rojizo", "biped", "DOMESTICAR", "DALE SU COMIDA Y SE VUELVE TUYO"),
    ("saddle", "trex", "verde", "biped", "MONTURAS", "CADA ESPECIE CON LA SUYA, SE MONTA Y SE MANEJA"),
    ("dinopedia",),
    ("whistle", "raptor", "rojizo", "biped", "SILBATO", "SEGUIME, QUEDATE, PATRULLÁ Y MODO DE COMBATE"),
    ("recall", "raptor", "oscuro", "biped", "LLAMAR", "TRAÉ A TUS DINOS DESDE LEJOS"),
    ("cage", "raptor", "gris", "biped", "JAULA", "GUARDALO, LLEVALO O VENDELO"),
    ("egg", "raptor", "verde", "biped", "HUEVOS Y GENÉTICA", "LAS CRÍAS HEREDAN COLOR Y RASGOS"),
    ("nest", "trex", "pardo", "biped", "NIDOS SALVAJES", "SACALE UN HUEVO... SI TE ANIMÁS"),
    ("feeder", "raptor", "arena", "biped", "COMEDEROS", "COMEN SOLOS, SIN INVENTARIO NI DUPES"),
    ("missions",),
    ("race", "gallimimus", "naranja", "runner", "CARRERAS", "CARRERAS PAREJAS CON PREMIOS Y RÉCORDS"),
]


# ================================================================ armado
ENVS = {}


def scenes():
    for env in ("land", "sky", "water", "river"):
        for plus in (False, True):
            if env == "water" and plus:
                continue
            ENVS[(env, plus)] = Env(env, plus)
    out = [LogoScene(4.5), CardScene("15 ESPECIES", PINK, sub="CADA UNA CON SU MODELO, COLORES Y MONTURA")]
    for sid, var, env, kind, name, sub, yaws, roar in SPECIES_SCENES:
        out.append(DinoScene(sid, var, env, kind, name, sub, yaws, roar))
    out.append(CardScene("CÓMO SE JUEGA", BLUE, sub="TODO LO QUE TRAE EL PLUGIN"))
    for f in FEATURES:
        if f[0] == "dinopedia":
            out.append(PanelScene("dinopedia", "DINOPEDIA", "ANOTÁ ESPECIES, COLORES, SELLOS Y RANGOS"))
        elif f[0] == "missions":
            out.append(PanelScene("missions", "MISIONES Y MAPA", "MISIONES DIARIAS Y DINOS EN TODO EL MUNDO"))
        else:
            feat, sid, var, kind, name, sub = f
            out.append(FeatureScene(feat, sid, var, kind, name, sub))
    out.append(CardScene("LÍNEA PLUS", RED, plus=True, dur=2.0, sub="DINOS CON HABILIDADES"))
    for sid, env, kind, name, sub, ability in PLUS_SCENES:
        out.append(DinoScene(sid, "carmesi", env, kind, name, sub, (115, 132), False, plus=True, ability=ability))
    out.append(LogoScene(4.0, outro=True))
    return out


SCENES = []
TIMELINE = []


_VBG = []


def vertical(sc, lt, stage):
    """Cuadro vertical 1080x1920: la escena en un cuadrado al medio, el titulo grande arriba y el logo abajo."""
    if not _VBG:
        _VBG.append(L.background(VW, VH))
        lg = Image.open(os.path.join(ROOT, "docs", "logo", "dinoearth-logo.png")).convert("RGBA")
        k = VW * 0.62 / lg.size[0]
        _VBG.append(lg.resize((int(lg.size[0] * k), int(lg.size[1] * k)), Image.LANCZOS))
    frame = _VBG[0].copy()
    frame.alpha_composite(stage, (0, STAGE_Y))
    edge = Image.new("RGBA", (VW, 8), (255, 0, 217, 200))
    frame.alpha_composite(edge, (0, STAGE_Y - 8))
    frame.alpha_composite(edge, (0, STAGE_Y + stage.size[1]))
    name, sub = getattr(sc, "name", None), getattr(sc, "sub", None)
    if name and not isinstance(sc, CardScene):
        color = RED if getattr(sc, "plus", False) else PINK
        a = ease(lt / 0.35) * (1 - ease((lt - (sc.dur - 0.3)) / 0.3))
        t = title(name, color, block=12)
        kt = min(1.5, VW * 0.92 / t.size[0])
        t = t.resize((int(t.size[0] * kt), int(t.size[1] * kt)), Image.LANCZOS if kt < 1 else Image.NEAREST)
        y0 = STAGE_Y // 2 - t.size[1] // 2 - 40
        blit_tl(frame, t, VW / 2 - t.size[0] / 2 - int(500 * (1 - ease(lt / 0.35))), y0, a)
        if sub:
            c = caption(sub, 5)
            kc = min(1.3, VW * 0.92 / c.size[0])
            c = c.resize((int(c.size[0] * kc), int(c.size[1] * kc)), Image.LANCZOS if kc < 1 else Image.NEAREST)
            blit_tl(frame, c, VW / 2 - c.size[0] / 2, y0 + t.size[1] + 14, ease((lt - 0.12) / 0.35) * a)
    if not isinstance(sc, LogoScene):
        lg = _VBG[1]
        frame.alpha_composite(lg, (VW // 2 - lg.size[0] // 2, STAGE_Y + stage.size[1] + (VH - STAGE_Y - stage.size[1]
                                                                                         - lg.size[1]) // 2))
    return frame


def frame_at(i):
    t = i / FPS
    for start, sc in TIMELINE:
        if start <= t < start + sc.dur:
            lt = t - start
            fr = sc.draw(lt)
            if VERTICAL:
                fr = vertical(sc, lt, fr)
            # entra desde negro (3 cuadros) para que los cortes no sean bruscos
            if lt < 0.1 and not isinstance(sc, LogoScene):
                fr = Image.blend(Image.new("RGBA", fr.size, (0, 0, 0, 255)), fr, lt / 0.1)
            return fr.convert("RGB").tobytes()
    return bytes(out_size()[0] * out_size()[1] * 3)


def out_size():
    return (VW, VH) if VERTICAL else (W, H)


# ================================================================ audio
def load(name):
    path = os.path.join(SOUNDS, name + ".ogg")
    if not os.path.exists(path):
        return None
    a, sr = sf.read(path, dtype="float32")
    if a.ndim > 1:
        a = a.mean(axis=1)
    if sr != SR:
        idx = np.arange(0, len(a), sr / SR)
        a = np.interp(idx, np.arange(len(a)), a).astype(np.float32)
    return a


def sfx(name):
    n = lambda d: int(d * SR)
    t = lambda d: np.arange(n(d)) / SR
    rng = np.random.default_rng(len(name))
    if name == "boom":
        x = t(1.6)
        return (np.sin(2 * np.pi * (60 * x - 15 * x * x)) * np.exp(-x * 2.5) + 0.3 * rng.standard_normal(len(x)) *
                np.exp(-x * 9)).astype(np.float32)
    if name == "whoosh":
        x = t(0.8)
        noise = rng.standard_normal(len(x))
        env = np.sin(np.pi * x / 0.8) ** 2
        cut = np.cumsum(noise) / 40
        return (np.diff(np.concatenate([[0], cut])) * 30 * env * 0.3 + noise * env * 0.15).astype(np.float32)
    if name == "zap":
        x = t(0.35)
        return (rng.standard_normal(len(x)) * np.exp(-x * 14) * 0.7 +
                np.sign(np.sin(2 * np.pi * 900 * x * (1 - x))) * np.exp(-x * 10) * 0.25).astype(np.float32)
    if name == "chime":
        x = t(1.6)
        out = np.zeros(len(x))
        for k, f in enumerate((880, 1108.7, 1318.5, 1760)):
            d = np.clip(x - k * 0.12, 0, None)
            out += np.sin(2 * np.pi * f * d) * np.exp(-d * 3) * (d > 0)
        return (out * 0.25).astype(np.float32)
    if name == "pop":
        x = t(0.12)
        return (np.sin(2 * np.pi * (900 - 3000 * x) * x) * np.exp(-x * 40) * 0.6).astype(np.float32)
    if name == "splash":
        x = t(0.5)
        noise = rng.standard_normal(len(x))
        return (noise * np.exp(-x * 8) * 0.5).astype(np.float32)
    if name == "eat":
        out = np.zeros(int(0.5 * SR))
        for k in range(3):
            x = t(0.09)
            out[int(k * 0.15 * SR):int(k * 0.15 * SR) + len(x)] += rng.standard_normal(len(x)) * np.exp(-x * 40) * 0.6
        return out.astype(np.float32)
    if name == "whistle":
        x = t(0.7)
        f = 1700 + 500 * np.sin(np.clip(x / 0.25, 0, 1) * math.pi / 2)
        env = np.clip(x / 0.03, 0, 1) * np.clip((0.7 - x) / 0.15, 0, 1)
        ph = np.cumsum(2 * np.pi * f / SR)
        return ((np.sin(ph) + 0.15 * rng.standard_normal(len(x))) * env * 0.45).astype(np.float32)
    if name == "page":
        x = t(0.3)
        noise = rng.standard_normal(len(x))
        return (np.diff(np.concatenate([[0], noise])) * np.sin(np.pi * x / 0.3) ** 2 * 0.35).astype(np.float32)
    if name == "chain":
        x = t(0.7)
        out = np.zeros(len(x))
        for k, f in enumerate((1430, 2210, 3170, 1870)):
            d = np.clip(x - k * 0.07, 0, None)
            out += np.sin(2 * np.pi * f * d) * np.exp(-d * 18) * (d > 0)
        return (out * 0.3).astype(np.float32)
    if name in ("beep", "beep2"):
        x = t(0.18 if name == "beep" else 0.45)
        f = 880 if name == "beep" else 1760
        return (np.sign(np.sin(2 * np.pi * f * x)) * np.clip((x[-1] - x) / 0.05, 0, 1) * 0.2).astype(np.float32)
    if name == "crack":
        x = t(0.6)
        noise = rng.standard_normal(len(x))
        return (noise * np.exp(-x * 12) * 0.8 + np.sin(2 * np.pi * 70 * x) * np.exp(-x * 6) * 0.6).astype(np.float32)
    return None


def music(total):
    """Musica epica sintetizada: bombo y tambores en 100 bpm, bajo y un ostinato en La menor; en la parte Plus
    se suman chinchines y sube una octava."""
    N = int(total * SR)
    out = np.zeros(N, dtype=np.float32)
    beat = 60 / 100
    rng = np.random.default_rng(4)
    tt = np.arange(int(0.5 * SR)) / SR
    kick = np.sin(2 * np.pi * (95 * tt - 70 * tt * tt)) * np.exp(-tt * 7)
    tom = (np.sin(2 * np.pi * 140 * tt) * np.exp(-tt * 9) + 0.4 * rng.standard_normal(len(tt)) * np.exp(-tt * 25))
    hat = rng.standard_normal(int(0.06 * SR)) * np.exp(-np.arange(int(0.06 * SR)) / SR * 70)
    notes = [220.0, 261.6, 329.6, 261.6, 196.0, 246.9, 293.7, 246.9]
    plus_start = TIMELINE[[i for i, (s, sc) in enumerate(TIMELINE) if isinstance(sc, CardScene) and sc.plus][0]][0]
    start = 0.9
    t = start
    i = 0
    while t < total - 3.5:
        k = int(t * SR)
        def add(sig, gain):
            e = min(N, k + len(sig))
            out[k:e] += (sig[:e - k] * gain).astype(np.float32)
        add(kick, 0.9)
        if i % 4 in (1, 3):
            add(tom, 0.45)
        intense = t >= plus_start
        if intense:
            for h in (0, 0.5):
                kh = int((t + h * beat) * SR)
                e = min(N, kh + len(hat))
                out[kh:e] += hat[:e - kh] * 0.18
        for half in (0, 1):
            f = notes[(i * 2 + half) % 8] * (2 if intense else 1)
            d = np.arange(int(beat / 2 * SR)) / SR
            pl = (np.sin(2 * np.pi * f * d) + 0.4 * np.sin(4 * np.pi * f * d)) * np.exp(-d * 6)
            kk = int((t + half * beat / 2) * SR)
            e = min(N, kk + len(pl))
            out[kk:e] += pl[:e - kk] * 0.16
        t += beat
        i += 1
    # bajo sostenido que acompaña (La y Sol segun la mitad del compas)
    x = np.arange(N) / SR
    root = np.where((x // (beat * 8)) % 2 == 0, 55.0, 49.0)
    phase = np.cumsum(2 * np.pi * root / SR)
    bass = (np.sin(phase) + 0.3 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase)) * 0.22
    env = np.clip((x - start) / 2, 0, 1) * np.clip((total - 3 - x) / 2, 0, 1)
    out += (bass * env).astype(np.float32)
    return out


def soundtrack(total):
    N = int(total * SR) + SR
    mix = np.zeros(N, dtype=np.float32)
    m = music(total)
    mix[:len(m)] += m * 0.55
    for start, sc in TIMELINE:
        for at, name, vol in sc.sounds():
            a = sfx(name)
            if a is None:
                a = load(name)
            if a is None:
                print("falta el sonido", name)
                continue
            k = int((start + at) * SR)
            e = min(N, k + len(a))
            mix[k:e] += a[:e - k] * vol
    peak = np.max(np.abs(mix)) or 1
    mix = mix / peak * 0.9
    return mix[:int(total * SR)]


# ================================================================ main
def setup():
    global SCENES, TIMELINE
    SCENES = scenes()
    t = 0.0
    TIMELINE = []
    for sc in SCENES:
        TIMELINE.append((t, sc))
        t += sc.dur
    return t


def configure(vert):
    """Vertical: el escenario es un cuadrado de 1080 (las escenas se arman ahi) y vertical() lo pone en 1080x1920."""
    global VERTICAL, W, H, GROUND
    VERTICAL = vert
    W, H = (1080, 1080) if vert else (1920, 1080)
    GROUND = int(H * 0.82)


def main():
    args = sys.argv[1:]
    vert = "vertical" in args
    args = [a for a in args if a != "vertical"]
    configure(vert)
    os.makedirs(OUT, exist_ok=True)
    total = setup()
    tag = "-vertical" if vert else ""
    ow, oh = out_size()
    if args and args[0] == "preview":
        shots = []
        tw, th = (270, 480) if vert else (480, 270)
        for start, sc in TIMELINE:
            lt = sc.dur * (0.75 if isinstance(sc, DinoScene) else 0.6)
            if isinstance(sc, DinoScene) and (sc.ability or isinstance(sc, FeatureScene)):
                lt = sc.dur * 0.55
            fr = sc.draw(lt)
            if vert:
                fr = vertical(sc, lt, fr)
            shots.append(fr.convert("RGB").resize((tw, th), Image.LANCZOS))
        cols = 8 if vert else 5
        sheet = Image.new("RGB", (tw * cols, th * ((len(shots) + cols - 1) // cols)))
        for i, sh in enumerate(shots):
            sheet.paste(sh, ((i % cols) * tw, (i // cols) * th))
        path = os.path.join(OUT, f"preview{tag}.png")
        sheet.save(path)
        print("preview ->", path, f"({total:.1f} s)")
        return
    if len(args) > 1 and args[0] == "frame":
        Image.frombytes("RGB", (ow, oh), frame_at(int(float(args[1]) * FPS))).save(os.path.join(OUT, f"frame{tag}.png"))
        return
    wav = os.path.join(OUT, f"audio{tag}.wav")
    sf.write(wav, soundtrack(total), SR)
    frames = int(total * FPS)
    mp4 = os.path.join(OUT, f"dinoearth-trailer{tag}.mp4")
    ff = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{ow}x{oh}",
                           "-r", str(FPS), "-i", "-", "-i", wav, "-c:v", "libx264", "-preset", "medium", "-crf", "20",
                           "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart",
                           mp4], stdin=subprocess.PIPE)
    with Pool(os.cpu_count()) as pool:
        for i, data in enumerate(pool.imap(frame_at, range(frames), chunksize=4)):
            ff.stdin.write(data)
            if i % 150 == 0:
                print(f"cuadro {i}/{frames}", flush=True)
    ff.stdin.close()
    ff.wait()
    os.remove(wav)
    print("video ->", mp4, f"({total:.1f} s)")


if __name__ == "__main__":
    main()

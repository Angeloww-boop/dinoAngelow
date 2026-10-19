"""Video de DinoEarth "como si fuera in game": camara de jugador (primera persona y F5), mundo de bloques, HUD,
chat y menus del server, con los modelos reales de los dinos del pack. Simulado: no hay cliente de Minecraft en la
nube, asi que el mundo y la interfaz se dibujan aca (mc3d.py y mchud.py) imitando al juego.

Uso:
  python generator/ingame.py preview        -> docs/video/ingame-preview.png (un cuadro de cada toma)
  python generator/ingame.py frame <seg>    -> docs/video/ingame-frame.png
  python generator/ingame.py                -> docs/video/dinoearth-ingame.mp4 (1920x1080, 30 fps)
"""
import math
import os
import subprocess
import sys
from multiprocessing import Pool

import numpy as np
from PIL import Image, ImageDraw

import mc3d as M
import mchud as U
import video as V
from build import ROOT
from preview import bone_matrices, mat_mul, mat_s, mat_t

W, H = 1920, 1080
FPS = 30
G = 3
OUT = os.path.join(ROOT, "docs", "video")
PINK = "&#ff00d9"
PRE = "&9&lDINOEARTH &8» "


def ease(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def lerp(a, b, k):
    return a + (b - a) * k


def catmull(keys, t):
    """keys: [(t, valor o tupla)] -> valor interpolado suave (Catmull-Rom), constante fuera del rango."""
    if t <= keys[0][0]:
        return keys[0][1]
    if t >= keys[-1][0]:
        return keys[-1][1]
    i = 0
    while keys[i + 1][0] < t:
        i += 1
    t0, t1 = keys[i][0], keys[i + 1][0]
    u = (t - t0) / (t1 - t0)
    p0 = np.asarray(keys[max(0, i - 1)][1], dtype=float)
    p1 = np.asarray(keys[i][1], dtype=float)
    p2 = np.asarray(keys[i + 1][1], dtype=float)
    p3 = np.asarray(keys[min(len(keys) - 1, i + 2)][1], dtype=float)
    r = 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u ** 3)
    return r if r.shape else float(r)


def angle_lerp(a, b, k):
    d = (b - a + 180) % 360 - 180
    return a + d * k


def yaw_of(dx, dz):
    """Yaw de Minecraft para mirar en la direccion (dx, dz)."""
    return math.degrees(math.atan2(-dx, dz))


# ================================================================ mundo
SEA = 62
WORLD = None
RIDE_Z = 150


def _line(x0, z0, x1, z1, r, step=4.0):
    n = max(1, int(math.hypot(x1 - x0, z1 - z0) / step))
    return [(x0 + (x1 - x0) * k / n, z0 + (z1 - z0) * k / n, r) for k in range(n + 1)]


# lugares de rodaje: terreno parejo (a SEA + 3) y sin arboles
PADS = ([(112, 196, 11), (190, 92, 10), (129, 118, 8), (199, 186, 6), (214, 100, 6)]
        + _line(150, 178, 212, 178, 7) + _line(186, 136, 198, 220, 5) + _line(150, 220, 150, 192, 7)
        + _line(166, 108, 218, 78, 13) + [(70, 150, 14)])


def _shape(h, X, Z):
    """Llanura con lomas, costa y mar al este (x > 250), un claro largo para correr montado (z ~ RIDE_Z)."""
    h = h.astype(float)
    k = np.clip((X - 236) / 30.0, 0, 1)
    h = h * (1 - k) + (SEA - 9 + (h - SEA) * 0.3) * k
    band = np.exp(-((Z - RIDE_Z) / 13.0) ** 2) * (X < 236)
    h = h * (1 - 0.85 * band) + (SEA + 3) * 0.85 * band
    wsum = np.zeros_like(h)
    for (cx, cz, r) in PADS:
        d = np.hypot(X - cx, Z - cz)
        wsum = np.maximum(wsum, np.clip(1 - (d - r) / 7.0, 0, 1))
    wsum = wsum * wsum * (3 - 2 * wsum)
    return h * (1 - wsum) + (SEA + 3) * wsum


CLEAR = [(cx, cz, r + 1.5) for (cx, cz, r) in PADS] + [(x, RIDE_Z, 9) for x in range(20, 240, 8)]


def world():
    global WORLD
    if WORLD is None:
        M.make_textures()
        w = M.World(300, sea=SEA, seed=11, shape=_shape)
        w.populate(trees=0.016, plants=0.14, keep_clear=CLEAR)
        w.mesh()
        # mar y fondo afuera del mundo (para que el horizonte del este siga siendo agua)
        S, big = w.size, 700.0
        fl = M.Quads()
        fl.add((-big, SEA - 10, S + big), (S + 2 * big, 0, 0), (0, 0, -(S + 2 * big)), "gravel",
               (0, 0, (S + 2 * big) * 16, (S + 2 * big) * 16))
        wa = M.Quads()
        y = SEA + 0.875
        for (x0, z0, x1, z1) in ((-big, -big, 0, S + big), (S, -big, S + big, S + big), (0, -big, S, 0), (0, S, S, S + big)):
            wa.add((x0, y, z1), (x1 - x0, 0, 0), (0, 0, -(z1 - z0)), "water", (0, 0, (x1 - x0) * 16, (z1 - z0) * 16),
                   cull=False)
        w.terrain = _cat(w.terrain, fl.arrays())
        w.water_q = _cat(w.water_q, wa.arrays())
        WORLD = w
    return WORLD


def _cat(a, b):
    if a is None:
        return b
    return {k: np.concatenate([a[k], b[k]]) for k in a}


def gy(x, z):
    return world().smooth_height(x, z)


# ================================================================ dinos
SEATS = {}


def seat_of(sid):
    """Punto del asiento de la montura (px del modelo, relativo al hueso saddle) y su hueso."""
    if sid not in SEATS:
        rig = V.model_set(sid, "saddle", None)[0]
        best = None
        for b in rig.bones:
            for c in b.cubes:
                if c.frm[0] <= 0 <= c.to[0] and (c.to[0] - c.frm[0]) >= 6 and (c.to[2] - c.frm[2]) >= 4:
                    if best is None or c.to[1] > best[1]:
                        best = (b.name, c.to[1], (c.frm[2] + c.to[2]) / 2)
        SEATS[sid] = best
    return SEATS[sid]


class Dino:
    def __init__(self, sid, var, form="male", scale=1.0, saddle=False):
        self.sid, self.var, self.form, self.saddle = sid, var, form, saddle
        if sid.startswith("@"):
            self.k, self.set = scale, V.extra_set(sid[1:])
        else:
            self.k, self.set = V.ALL[sid]["scale"] * scale, V.model_set(sid, var, form)

    def add(self, q, pos, yaw, pose, pitch=0.0, roll=0.0, tint=(1, 1, 1)):
        """Agrega las caras y devuelve el placer (para ubicar jinete, particulas)."""
        mats = bone_matrices(self.set[0], pose, mat_s(1.0))
        parts = [(self.set, mats)]
        self.mats = mats
        if self.saddle:
            sad = V.model_set(self.sid, "saddle", None)
            smats = bone_matrices(sad[0], {}, None, parents={"body": mats["body"]})
            parts.append((sad, smats))
            self.smats = smats
        f = M.placer(pos, yaw, self.k, pitch, roll)
        M.rig_quads(q, parts, f, tint)
        self.f = f
        return f

    def point(self, bone, p):
        """Punto p (px del rig, absoluto) pegado al hueso, en el mundo (despues de add)."""
        mats = self.smats if bone == "saddle" else self.mats
        m, joint = mats[bone]
        d = (p[0] - joint[0], p[1] - joint[1], p[2] - joint[2])
        r = [m[i][0] * d[0] + m[i][1] * d[1] + m[i][2] * d[2] + m[i][3] for i in range(3)]
        return np.array(self.f(r))

    def seat(self):
        """Donde van los pies del jinete sentado (la cadera queda sobre el asiento, como el juego)."""
        bone, y, z = seat_of(self.sid)
        return self.point(bone, (0, y, z)) - np.array([0, 0.62, 0])

    def head(self):
        rig = self.set[0]
        b = rig.bone("head")
        return self.point("head", (b.joint[0], b.joint[1] + 2, b.joint[2] + 6))


_DINOS = {}


def dino(sid, var, form="male", scale=1.0, saddle=False):
    k = (sid, var, form, scale, saddle)
    if k not in _DINOS:
        _DINOS[k] = Dino(sid, var, form, scale, saddle)
    return _DINOS[k]


# ================================================================ jugador (modelo de Minecraft, 32 px de alto)
SKIN = os.path.join(ROOT, "generator", "assets", "skin_angeloww.png")   # la skin de Angeloww_
STEVE = True   # modelo Steve (brazos de 4 px) aunque la skin venga en formato Alex (3 px)


def _skin_parts():
    """Recorta la skin de 64x64 como el juego: cada cara de cada parte (y la capa de afuera) es su textura."""
    sk = np.array(Image.open(SKIN).convert("RGBA"))
    slim = sk[20:32, 54:56, 3].max() == 0
    aw = 3 if slim and not STEVE else 4

    def faces(name, u, v, w, h, d, outer=False):
        if w == 4 and slim:   # brazo de una skin Alex
            if not STEVE:
                return faces(name, u, v, 3, h, d, outer)
            out = faces(name, u, v, 3, h, d, outer)
            for k in ("f", "b", "t", "d"):   # caras de 3 px estiradas a 4 (como los conversores Alex -> Steve)
                tid = M.TEX_ID[out[k]]
                a = M.TEX[tid]
                M.TEX[tid] = np.ascontiguousarray(np.concatenate([a[:, :2], a[:, 1:]], axis=1))
                M.MIPS[tid] = None
            return out
        regions = {"t": (u + d, v, w, d), "d": (u + d + w, v, w, d), "r": (u, v + d, d, h), "f": (u + d, v + d, w, h),
                   "l": (u + d + w, v + d, d, h), "b": (u + 2 * d + w, v + d, w, h)}
        out = {}
        for k, (x, y, ww, hh) in regions.items():
            a = sk[y:y + hh, x:x + ww].copy()
            if not outer:
                a[..., 3] = 255   # la capa de adentro es opaca
            if k == "d":
                a = a[::-1]
            M.add_tex("skin_%s_%s" % (name, k), a)
            out[k] = "skin_%s_%s" % (name, k)
        return out
    P = {
        "head": faces("head", 0, 0, 8, 8, 8), "hat": faces("hat", 32, 0, 8, 8, 8, True),
        "body": faces("body", 16, 16, 8, 12, 4), "jacket": faces("jacket", 16, 32, 8, 12, 4, True),
        "arm_r": faces("arm_r", 40, 16, 4, 12, 4), "sleeve_r": faces("sleeve_r", 40, 32, 4, 12, 4, True),
        "arm_l": faces("arm_l", 32, 48, 4, 12, 4), "sleeve_l": faces("sleeve_l", 48, 48, 4, 12, 4, True),
        "leg_r": faces("leg_r", 0, 16, 4, 12, 4), "pants_r": faces("pants_r", 0, 32, 4, 12, 4, True),
        "leg_l": faces("leg_l", 16, 48, 4, 12, 4), "pants_l": faces("pants_l", 0, 48, 4, 12, 4, True),
    }
    return P, aw


_SKIN = []


def _rot(rx=0.0, ry=0.0, rz=0.0):
    a, b, c = math.radians(rx), math.radians(ry), math.radians(rz)
    Rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return Ry @ Rx @ Rz


def _obox(q, lo, hi, pivot, R, f, tex, grow=0.0):
    """Caja de lo a hi (px), girada R alrededor de pivot, al mundo con f. tex: cara -> textura (front = +Z, el
    costado derecho del jugador es -X). grow: la capa de afuera es un poco mas grande (como el juego)."""
    lo, hi, pivot = (np.asarray(v, float) for v in (lo, hi, pivot))
    lo, hi = lo - grow, hi + grow
    size = hi - lo

    def tw(p):
        return np.array(f(tuple(R @ (p - pivot) + pivot)))
    names = {"south": "f", "north": "b", "west": "r", "east": "l", "up": "t", "down": "d"}
    for name, (p0, u, v) in M.FACES.items():
        p0, u, v = (np.asarray(a, float) for a in (p0, u, v))
        A = lo + p0 * size
        P0, PU, PV = tw(A), tw(A + u * size), tw(A + v * size)
        t = tex[names[name]]
        arr = M.TEX[M.TEX_ID[t]]
        q.add(tuple(P0), tuple(PU - P0), tuple(PV - P0), t, (0, arr.shape[0], arr.shape[1], 0), cull=not grow)


def player_quads(q, pos, yaw, head_pitch=0.0, walk=0.0, t=0.0, sit=False, swing=0.0, arms_up=0.0):
    if not _SKIN:
        _SKIN.append(_skin_parts())
    P, aw = _SKIN[0]
    f = M.placer(pos, yaw, 0.9375)
    ph = math.sin(t * 2 * math.pi * 1.6) * walk
    leg = ph * 40
    I = np.eye(3)
    hr = _rot(rx=head_pitch)
    _obox(q, (-4, 24, -4), (4, 32, 4), (0, 24, 0), hr, f, P["head"])
    _obox(q, (-4, 24, -4), (4, 32, 4), (0, 24, 0), hr, f, P["hat"], 0.5)
    _obox(q, (-4, 12, -2), (4, 24, 2), (0, 12, 0), I, f, P["body"])
    _obox(q, (-4, 12, -2), (4, 24, 2), (0, 12, 0), I, f, P["jacket"], 0.25)
    # angulos hacia adelante (rx negativo = el brazo o la pierna va para adelante)
    ar = -leg + 70 * swing + 150 * arms_up + (30 if sit else 0)
    al = leg + 150 * arms_up + (30 if sit else 0)
    rr = _rot(rx=-ar, rz=5 if not sit else 0)
    rl = _rot(rx=-al, rz=-5 if not sit else 0)
    # el derecho del jugador es -X
    _obox(q, (-4 - aw, 12, -2), (-4, 24, 2), (-5, 22, 0), rr, f, P["arm_r"])
    _obox(q, (-4 - aw, 12, -2), (-4, 24, 2), (-5, 22, 0), rr, f, P["sleeve_r"], 0.25)
    _obox(q, (4, 12, -2), (4 + aw, 24, 2), (5, 22, 0), rl, f, P["arm_l"])
    _obox(q, (4, 12, -2), (4 + aw, 24, 2), (5, 22, 0), rl, f, P["sleeve_l"], 0.25)
    if sit:
        lr, ll = _rot(rx=-80, ry=-12), _rot(rx=-80, ry=12)
    else:
        lr, ll = _rot(rx=-leg), _rot(rx=leg)
    _obox(q, (-4, 0, -2), (0, 12, 2), (-2, 12, 0), lr, f, P["leg_r"])
    _obox(q, (-4, 0, -2), (0, 12, 2), (-2, 12, 0), lr, f, P["pants_r"], 0.25)
    _obox(q, (0, 0, -2), (4, 12, 2), (2, 12, 0), ll, f, P["leg_l"])
    _obox(q, (0, 0, -2), (4, 12, 2), (2, 12, 0), ll, f, P["pants_l"], 0.25)


# ================================================================ particulas
def _spr(rows, pal):
    h, w = len(rows), len(rows[0])
    a = np.zeros((h, w, 4), dtype=np.uint8)
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c != ".":
                a[y, x] = pal[c] + (255,)
    return a


HEART = _spr(U.HEART, U._HP)
SMOKE = [np.full((4, 4, 4), (c, c, c, 220), dtype=np.uint8) for c in (240, 210, 180, 150)]
BUBBLE = _spr([".WW.", "W..W", "W..W", ".WW."], {"W": (220, 235, 255)})


def chunk_of(name, k):
    """Pedacito de textura (particula de bloque roto)."""
    t = M.TEX[M.TEX_ID[name]]
    y, x = (k * 5) % (t.shape[0] - 4), (k * 7) % (t.shape[1] - 4)
    return t[y:y + 4, x:x + 4].copy()


def burst(n, seed, t, origin, spread=1.0, up=1.0, life=1.2, gravity=2.0):
    """Particulas que salen de origin: [(pos, edad 0..1, k)]."""
    rng = np.random.RandomState(seed)
    out = []
    for k in range(n):
        d = rng.uniform(-1, 1, 3)
        d[1] = abs(d[1]) * up + 0.3
        v = d * spread * rng.uniform(0.6, 1.4)
        tt = t - rng.uniform(0, 0.15)
        if 0 <= tt < life:
            p = np.array(origin, float) + v * tt
            p[1] -= 0.5 * gravity * tt * tt
            out.append((p, tt / life, k))
    return out


# ================================================================ armado de un cuadro
class Scene3D:
    def __init__(self, cam, t=0.0, underwater=False, fov=70.0):
        self.cam, self.t, self.under = cam, t, underwater
        self.q = M.Quads()
        self.blend = M.Quads()
        self.sprites = []   # (pos, alto, img, alpha)
        self.tags = []      # (pos, texto)
        self.outline = False   # primera persona: contorno negro del bloque que apunta la mira (como el juego)

    def render(self):
        w = world()
        R = M.Raster(W, H)
        if self.under:
            R.fog, R.fog_range, R.max_dist = (0.09, 0.22, 0.5), (1.0, 24.0), 32.0
            R.begin(self.cam, sky_top=(20, 52, 120), sky_fog=(23, 56, 128), void=(10, 30, 70), sun=False)
        else:
            R.fog_range, R.max_dist = (70.0, 118.0), 125.0
            R.begin(self.cam)
        R.draw(w.terrain)
        R.draw(self.q.arrays())
        R.draw(w.plant_q)
        R.apply_fog()
        for (p, s, img, a) in sorted(self.sprites, key=lambda e: -np.linalg.norm(np.asarray(e[0]) - self.cam.pos)):
            R.billboard(p, s, img, a)
        # agua animada: la textura baja de a poco
        wq = w.water_q
        if wq is not None:
            wq = dict(wq)
            st = wq["st"].copy()
            st[:, 1] += self.t * 6
            st[:, 3] += self.t * 6
            wq["st"] = st
        R.draw(wq, blend=True, fog_now=True)
        if not self.under:
            R.draw(w.clouds(self.t), blend=True, fog_now=True, alpha=0.8)
        R.draw(self.blend.arrays(), blend=True, fog_now=True)
        self.R = R
        img = Image.fromarray(R.image()).convert("RGBA")
        if self.outline or getattr(self.cam, "first_person", False):
            block_outline(img, R, self.cam)
        for p, text in self.tags:
            pr = R.project(p)
            if pr and pr[2] < 40 and R.depth[min(H - 1, max(0, int(pr[1]))), min(W - 1, max(0, int(pr[0])))] > pr[2] - 1:
                k = max(1, int(round(G * 6 / max(pr[2], 3))))
                tag = U.nametag(text, k)
                U.paste(img, tag, pr[0] - tag.size[0] / 2, pr[1] - tag.size[1])
        return img




# ================================================================ caminos y camaras
class Path:
    """Camino suave por puntos (x, z) o (x, y, z): posicion y yaw segun la distancia recorrida."""

    def __init__(self, pts):
        P = np.array(pts, dtype=float)
        dense = []
        for i in range(len(P) - 1):
            p0, p1, p2, p3 = P[max(0, i - 1)], P[i], P[i + 1], P[min(len(P) - 1, i + 2)]
            for u in np.linspace(0, 1, 24, endpoint=False):
                dense.append(0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u
                                    + (-p0 + 3 * p1 - 3 * p2 + p3) * u ** 3))
        dense.append(P[-1])
        self.p = np.array(dense)
        seg = np.linalg.norm(np.diff(self.p, axis=0), axis=1)
        self.s = np.concatenate([[0], np.cumsum(seg)])
        self.length = float(self.s[-1])

    def pos(self, s):
        s = min(max(s, 0.0), self.length)
        i = min(len(self.s) - 2, max(0, int(np.searchsorted(self.s, s)) - 1))
        k = (s - self.s[i]) / max(1e-9, self.s[i + 1] - self.s[i])
        return self.p[i] + (self.p[i + 1] - self.p[i]) * k

    def yaw(self, s, look=1.0):
        a, b = self.pos(s - look), self.pos(s + look)
        d = b - a
        return yaw_of(d[0], d[-1])


def run_dist(t, v, t0=0.0, acc=1.0, stops=()):
    """Distancia recorrida a velocidad v arrancando en t0 (acelera en acc s). stops: [(desde, hasta)] frenadas."""
    dt = 1 / 120
    s, x = 0.0, t0
    if t <= t0:
        return 0.0
    n = int((t - t0) / dt)
    for k in range(n):
        tt = t0 + k * dt
        f = min(1.0, (tt - t0) / acc) if acc > 0 else 1.0
        for a, b in stops:
            if a <= tt < b:
                f *= max(0.0, 1 - min(1, (tt - a) / 0.5)) * 1.0
            elif b <= tt < b + 0.8:
                f *= (tt - b) / 0.8
        s += v * f * dt
    return s


def cam(pos, yaw, pitch, fov=70.0, roll=0.0):
    return M.Cam(pos, yaw, pitch, fov, roll)


def fp_cam(feet, yaw, pitch, walked=0.0, sneak=False, shake=0.0, t=0.0, fov=70.0, roll=0.0):
    """Primera persona con el balanceo al caminar del juego."""
    eye = np.asarray(feet, float) + np.array([0, 1.27 if sneak else 1.62, 0])
    c = M.Cam(eye, yaw, pitch, fov, roll)
    ph = walked * math.pi / 1.6
    off = c.r * math.sin(ph) * 0.045 + c.u * (-abs(math.cos(ph)) * 0.06 + 0.03)
    if shake:
        off = off + np.array([math.sin(t * 61), math.sin(t * 47 + 1), math.sin(t * 53 + 2)]) * shake
    c.pos = c.pos + off
    c.first_person = True
    return c


def f5_back(eye, yaw, pitch, dist=4.0, fov=70.0, shake=0.0, t=0.0):
    c = M.Cam(eye, yaw, pitch, fov)
    c.pos = np.asarray(eye, float) - c.f * dist
    if shake:
        c.pos = c.pos + np.array([math.sin(t * 61), math.sin(t * 47 + 1), math.sin(t * 53 + 2)]) * shake
    return c


def f5_front(eye, yaw, pitch, dist=4.0, fov=70.0, shake=0.0, t=0.0):
    f = M.Cam(eye, yaw, pitch).f
    c = M.Cam(eye, yaw + 180, -pitch, fov)
    c.pos = np.asarray(eye, float) + f * dist
    if shake:
        c.pos = c.pos + np.array([math.sin(t * 61), math.sin(t * 47 + 1), math.sin(t * 53 + 2)]) * shake
    return c


def gait(kind, t, walk=1.0, roar=0.0, f=None):
    if kind == "biped":
        return V.biped(t, walk, roar, f=f or 1.1)
    if kind == "quad":
        return V.quad(t, walk, roar, f=f or 0.9)
    if kind == "flyer":
        return V.flyer(t, walk, roar=roar)
    if kind == "swim":
        return V.swimmer(t, walk, roar)
    return {}


def sit_pose(t):
    p = V.biped(t, 0.0)
    p["body"] = (8, 0, 0, -5)
    p["leg_r"] = (-55, 0, 0, -3)
    p["leg_l"] = (-55, 0, 0, -3)
    p["shin_r"] = (90, 0, 0, 0)
    p["shin_l"] = (90, 0, 0, 0)
    return p


# ================================================================ HUD comun
HUD = U.Hud(W, H, G)
ITEMS = ["dinopedia", "carne", "silbato", "jaula", "montura_trex", "espada", "mapa", ("pan", 16), None]


_ITEM3D = {}
HELD = [0.56, -0.50, 0.86, -38, -8, 32, 0.66]   # x, y, z en la camara, giro y, giro x, giro z, escala


def item_mesh(name):
    """Item "generado" como el juego: la textura adelante y atras, y un borde de 1 px por cada pixel del contorno."""
    if name in _ITEM3D:
        return _ITEM3D[name]
    im = U.icon(name, 64)
    n = 32 if (os.path.exists(os.path.join(U.IMG, "item_%s.png" % name)) or "/" in name) else 16
    a = np.array(im.resize((n, n), Image.NEAREST))
    key = "held_" + name
    M.add_tex(key, a)
    t = 1.0 / 16
    k = 1.0 / n
    quads = [((-0.5, -0.5, t / 2), (1, 0, 0), (0, 1, 0), (0, n, n, 0)),
             ((0.5, -0.5, -t / 2), (-1, 0, 0), (0, 1, 0), (n, n, 0, 0))]
    op = a[..., 3] > 40
    for j in range(n):
        for i in range(n):
            if not op[j, i]:
                continue
            x0, x1 = -0.5 + i * k, -0.5 + (i + 1) * k
            yt, yb = 0.5 - j * k, 0.5 - (j + 1) * k
            st = (i + 0.2, j + 0.2, i + 0.8, j + 0.8)
            if j == 0 or not op[j - 1, i]:
                quads.append(((x0, yt, t / 2), (k, 0, 0), (0, 0, -t), st))
            if j == n - 1 or not op[j + 1, i]:
                quads.append(((x0, yb, -t / 2), (k, 0, 0), (0, 0, t), st))
            if i == 0 or not op[j, i - 1]:
                quads.append(((x0, yb, t / 2), (0, k, 0), (0, 0, -t), st))
            if i == n - 1 or not op[j, i + 1]:
                quads.append(((x1, yb, -t / 2), (0, k, 0), (0, 0, t), st))
    _ITEM3D[name] = (key, quads)
    return _ITEM3D[name]


def held3d(fr, name, swing=0.0, equip=0.0, walked=0.0):
    """Item en la mano derecha en primera persona, en 3D (con grosor y luz por cara) encima de la escena."""
    key, quads = item_mesh(name)
    cam = M.Cam((0, 0, 0), 0, 0, 70)
    R = M.Raster(W, H)
    R.begin(cam, sun=False)
    R.color[:] = 0
    s = math.sin(math.pi * min(1.0, max(0.0, swing)))
    ph = walked * math.pi / 1.6
    # posicion en camara (x derecha, y arriba, z adelante), como la mano derecha del juego
    T = np.array([HELD[0] - 0.30 * s + math.sin(ph) * 0.012,
                  HELD[1] + 0.06 * s - 0.10 * s * s - abs(math.cos(ph)) * 0.012 - 0.6 * equip, HELD[2] - 0.06 * s])
    Rm = _rot(ry=HELD[3] + 18 * s, rx=HELD[4] - 22 * s) @ _rot(rz=HELD[5] + 38 * s)
    sc = HELD[6]
    q = M.Quads()
    for (p0, u, v, st) in quads:
        P0 = T + Rm @ (np.array(p0) * sc)
        U_ = Rm @ (np.array(u) * sc)
        V_ = Rm @ (np.array(v) * sc)
        # camara -> mundo
        w0 = cam.r * P0[0] + cam.u * P0[1] + cam.f * P0[2]
        wu = cam.r * U_[0] + cam.u * U_[1] + cam.f * U_[2]
        wv = cam.r * V_[0] + cam.u * V_[1] + cam.f * V_[2]
        q.add(tuple(w0), tuple(wu), tuple(wv), key, st, (1.3, 1.3, 1.3), False, True)
    R.draw(q.arrays())
    mask = np.isfinite(R.depth)
    if not mask.any():
        return
    ys, xs = np.nonzero(mask)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    rgba = np.zeros((y1 - y0, x1 - x0, 4), dtype=np.uint8)
    rgba[..., :3] = np.clip(R.color[y0:y1, x0:x1], 0, 255)
    rgba[..., 3] = mask[y0:y1, x0:x1] * 255
    fr.alpha_composite(Image.fromarray(rgba), (int(x0), int(y0)))


def hud(fr, now, sel=0, items=None, held=None, swing=0.0, equip=0.0, walked=0.0, chat=(), action=None,
        action_a=1.0, boss=None, cross=True, hearts=20, food=20, mount=0, xp=0.35, level=12):
    items = items or ITEMS
    if held:
        held3d(fr, held, swing, equip, walked)
    if cross:
        HUD.crosshair(fr)
    HUD.hotbar(fr, items, sel, hearts=hearts, food=0 if mount else food, xp=xp, level=level)
    if mount:
        x0 = W // 2 - 91 * G
        hy = H - 22 * G - 17 * G
        for row in range(mount):
            for i in range(10):
                U.paste(fr, HUD.heart, x0 + 182 * G - (i + 1) * 8 * G - G, hy - row * 10 * G)
    if chat:
        HUD.chat(fr, chat, now)
    if action:
        HUD.actionbar(fr, action, action_a)
    if boss:
        HUD.bossbar(fr, boss[0], boss[1])
    return fr


def window(t, a, b, fade=0.25):
    """1 entre a y b, con entrada y salida suaves (para textos)."""
    if t < a or t > b:
        return 0.0
    return min(1.0, (t - a) / fade, (b - t) / fade)


def swing_at(t, times, dur=0.3):
    for t0 in times:
        if t0 <= t < t0 + dur:
            return (t - t0) / dur
    return 0.0


def equip_at(t, times, dur=0.3):
    """Al cambiar de item, el de la mano baja y sube (0 = arriba, 1 = abajo)."""
    for t0 in times:
        if t0 <= t < t0 + dur:
            u = (t - t0) / dur
            return 1 - abs(2 * u - 1)
    return 0.0


def black(fr, a):
    if a > 0:
        fr.alpha_composite(Image.new("RGBA", fr.size, (0, 0, 0, int(255 * min(1, a)))))
    return fr


# ================================================================ menus
_MENU = {}


def menu_img(name):
    if name not in _MENU:
        im = Image.open(os.path.join(U.IMG, "menu_c_%s.png" % name)).convert("RGBA")
        k = 222 * G / im.size[1]
        _MENU[name] = (im.resize((int(im.size[0] * k), int(im.size[1] * k)), Image.LANCZOS), im.size[0] / 552.0 * k)
    return _MENU[name]


_TIP = {}


def tooltip(i):
    """Las cajas de tooltips.png (capturas con la fuente real): 0 Triceratops, 1 Rastrear, 2 Sello, 3 Mision."""
    if i not in _TIP:
        im = Image.open(os.path.join(U.IMG, "tooltips.png")).convert("RGBA")
        boxes = [(20, 18, 542, 214), (560, 18, 1082, 193), (20, 230, 542, 344), (560, 230, 1082, 364)]
        c = im.crop(boxes[i])
        _TIP[i] = c.resize((int(c.size[0] * G / 2), int(c.size[1] * G / 2)), Image.NEAREST)
    return _TIP[i]


CURSOR = ["K..........", "KK.........", "KWK........", "KWWK.......", "KWWWK......", "KWWWWK.....",
          "KWWWWWK....", "KWWWWWWK...", "KWWWWWWWK..", "KWWWWWWWWK.", "KWWWWWKKKKK", "KWWKWWK....",
          "KWK.KWWK...", "KK..KWWK...", "K....KWWK..", ".....KWWK..", "......KK..."]


def cursor_img():
    im = Image.new("RGBA", (11, 17), (0, 0, 0, 0))
    for y, r in enumerate(CURSOR):
        for x, c in enumerate(r):
            if c != ".":
                im.putpixel((x, y), (0, 0, 0, 255) if c == "K" else (255, 255, 255, 255))
    return im.resize((22 * 2 // 2 * 1, 34), Image.NEAREST)


def draw_menu(fr, name, cur=None, tip=None, alpha=1.0):
    """Pantalla de inventario: fondo oscurecido + el menu al medio; cur = (x, y) en px de la captura 552x690."""
    ov = Image.new("RGBA", fr.size, (16, 16, 16, int(200 * alpha)))
    fr.alpha_composite(ov)
    im, k = menu_img(name)
    x0, y0 = W // 2 - im.size[0] // 2, H // 2 - im.size[1] // 2
    U.paste(fr, im, x0, y0, alpha)
    if cur is not None:
        cx, cy = x0 + cur[0] * k, y0 + cur[1] * k
        if tip is not None:
            t = tooltip(tip)
            U.paste(fr, t, cx + 18 * G // 2 + 6, cy - 16 * G // 2 - 4, alpha)
        U.paste(fr, cursor_img(), cx, cy, alpha)
    return fr


def block_outline(img, R, cam, reach=4.5):
    """Si la mira apunta a un bloque del piso a menos de 4,5 bloques (y no hay un dino adelante), dibuja sus
    aristas en negro transparente, con prueba de profundidad."""
    w = world()
    hit = None
    for k in range(1, int(reach / 0.02)):
        p = cam.pos + cam.f * (k * 0.02)
        if p[1] <= w.height(p[0], p[2]):
            hit = (math.floor(p[0]), w.height(p[0], p[2]) - 1, math.floor(p[2]), k * 0.02)
            break
    if hit is None:
        return
    bx, by, bz, dist = hit
    if R.depth[H // 2, W // 2] < dist * float(cam.f @ cam.f) - 0.08:
        return   # hay una entidad entre la mira y el bloque
    e = 0.004
    lo = np.array([bx - e, by - e, bz - e])
    hi = np.array([bx + 1 + e, by + 1 + e, bz + 1 + e])
    corners = [np.array([(lo, hi)[i >> 2 & 1][0], (lo, hi)[i >> 1 & 1][1], (lo, hi)[i & 1][2]]) for i in range(8)]
    edges = [(a, b) for a in range(8) for b in range(a + 1, 8) if bin(a ^ b).count("1") == 1]
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    for a, b in edges:
        pts = []
        for u in np.linspace(0, 1, 30):
            pp = corners[a] + (corners[b] - corners[a]) * u
            pr = R.project(pp)
            if pr is None:
                pts.append(None)
                continue
            xi, yi = int(pr[0]), int(pr[1])
            vis = 0 <= xi < W and 0 <= yi < H and R.depth[yi, xi] >= pr[2] - 0.06
            pts.append((pr[0], pr[1]) if vis else None)
        for p1, p2 in zip(pts, pts[1:]):
            if p1 and p2:
                d.line([p1, p2], fill=(0, 0, 0, 105), width=2)
    img.alpha_composite(ov)


# ================================================================ tomas
class Shot:
    dur = 5.0

    def frame(self, lt):
        raise NotImplementedError

    def sounds(self):
        return []

    def fades(self, fr, lt, a=0.15, b=0.15):
        return black(fr, max(1 - lt / a if a else 0, 1 - (self.dur - lt) / b if b else 0))


_LOGO = []


def logo(width):
    if not _LOGO:
        _LOGO.append(Image.open(os.path.join(ROOT, "docs", "logo", "dinoearth-logo.png")).convert("RGBA"))
    im = _LOGO[0]
    k = width / im.size[0]
    return im.resize((int(im.size[0] * k), int(im.size[1] * k)), Image.LANCZOS)


class Intro(Shot):
    """Vuelo de camara sobre el lago: una manada de parasaurolophus y un T-Rex que ruge. Aparece el logo."""
    dur = 6.0

    def __init__(self):
        self.herd = Path([(166, 108), (178, 100), (190, 92), (204, 84), (216, 78)])

    def frame(self, lt):
        para = dino("parasaurolophus", "azul")
        lead = 4 + lt * 2.4
        hx, hz = self.herd.pos(lead - 4)
        side = M.Cam((0, 0, 0), self.herd.yaw(lead - 4), 0).r
        mx_, mz_ = self.herd.pos(lead - 7)
        cp = np.array([mx_ + side[0] * 14, SEA + 11.5 - lt * 0.3, mz_ + side[2] * 14])
        target = np.array([mx_ + 2.5, SEA + 4.0, mz_ - 1.5])
        s = Scene3D(M.look_at(cp, target), lt)
        for k, (off, lat) in enumerate(((0, 0), (-5, 2.5), (-9, -2), (-13, 1.5))):
            d = lead + off
            x, z = self.herd.pos(d)
            yaw = self.herd.yaw(d)
            fw = M.Cam((0, 0, 0), yaw, 0).r
            pos = (x + fw[0] * lat, gy(x + fw[0] * lat, z + fw[2] * lat), z + fw[2] * lat)
            para.add(s.q, pos, yaw, gait("quad", lt + k * 0.37, 0.8))
        rex = dino("trex", "oscuro")
        roar = window(lt, 1.4, 3.6, 0.35)
        rex.add(s.q, (206, gy(206, 96), 96), yaw_of(-1, -0.5), gait("biped", lt, 0.0, roar))
        fr = s.render()
        a = window(lt, 1.6, 6.5, 0.7)
        if a > 0:
            lg = logo(int(W * 0.40))
            U.paste(fr, lg, W // 2 - lg.size[0] // 2, int(H * 0.04), a)
        return self.fades(fr, lt, 0.8, 0.2)

    def sounds(self):
        return [(0.0, "wind", 0.5), (1.5, "trex/roar1", 1.0), (1.55, "boom", 0.5), (3.6, "parasaurolophus/ambient1", 0.5)]


class Walk(Shot):
    """Primera persona con la Dinopedia en la mano: pasa una manada de gallimimus y la lupa marca un triceratops."""
    dur = 8.0
    SPEED = 4.3

    def __init__(self):
        self.galli = Path([(186, 140), (189, 158), (192, 176), (194, 196), (197, 216)])

    def player(self, lt):
        x = 160 + self.SPEED * lt
        return np.array([x, gy(x, 178), 178.0]), self.SPEED * lt

    def frame(self, lt):
        feet, walked = self.player(lt)
        yaw = catmull([(0, -90), (1.4, -92), (2.6, -128), (4.0, -95), (5.4, -62), (6.6, -70), (8, -72)], lt)
        pitch = catmull([(0, 4), (3, 2), (6, 6), (8, 7)], lt)
        s = Scene3D(fp_cam(feet, yaw, pitch, walked), lt)
        g = dino("gallimimus", "naranja")
        for k, (off, lat) in enumerate(((0, 0), (-4, 2.0), (-6.5, -1.8), (-10, 0.8))):
            d = (lt - 1.2) * 10 + off
            if -2 < d < self.galli.length:
                x, z = self.galli.pos(d)
                yaw_g = self.galli.yaw(d)
                r = M.Cam((0, 0, 0), yaw_g, 0).r
                x, z = x + r[0] * lat, z + r[2] * lat
                g.add(s.q, (x, gy(x, z), z), yaw_g, gait("biped", lt + k * 0.21, 1.0, f=2.0))
        tri = dino("triceratops", "pardo")
        graze = 0.5 + 0.5 * math.sin(lt * 1.3)
        p = gait("quad", lt, 0.0)
        p["head"] = (18 * graze, 0, 0, 0)
        tri.add(s.q, (199, gy(199, 186), 186), yaw_of(-1, 0.3), p)
        fr = s.render()
        act, aa = None, 0
        if 2.1 <= lt < 5.0:
            act, aa = "&8| &fNueva especie en la Dinopedia&8: &#ff00d9Gallimimus&f.", window(lt, 2.1, 5.0, 0.2)
        elif lt >= 5.3:
            dist = int(math.hypot(199 - feet[0], 186 - feet[2]))
            act, aa = "&8| &f→ &#ff00d9Triceratops&f a &#ff00d9%d&f metros." % dist, window(lt, 5.3, 9.0, 0.2)
        hud(fr, lt, 0, held="dinopedia", walked=walked, action=act, action_a=aa)
        return self.fades(fr, lt)

    def sounds(self):
        out = [(0.0, "wind", 0.25), (1.3, "gallimimus/ambient1", 0.7), (2.1, "chime", 0.5), (2.6, "gallimimus/ambient2", 0.6),
               (5.3, "page", 0.6), (6.4, "triceratops/ambient1", 0.6)]
        out += [(k * 0.37, "step", 0.25) for k in range(int(self.dur / 0.37))]
        out += [(1.5 + k * 0.2, "gallimimus/step%d" % (1 + k % 3), 0.35) for k in range(16)]
        return out


_BG = {}


def background(key, fn):
    if key not in _BG:
        _BG[key] = fn()
    return _BG[key].copy()


class Dex(Shot):
    """Abre la Dinopedia: indice, tooltip del triceratops, su pagina y la lupa para rastrear."""
    dur = 7.0

    def frame(self, lt):
        def bg():
            w = Walk()
            feet, walked = w.player(w.dur)
            s = Scene3D(fp_cam(feet, -72, 7, walked), 0.0)
            tri = dino("triceratops", "pardo")
            p = gait("quad", 0, 0.0)
            p["head"] = (12, 0, 0, 0)
            tri.add(s.q, (199, gy(199, 186), 186), yaw_of(-1, 0.3), p)
            return s.render()
        fr = background("dex", bg)
        sw = swing_at(lt, [0.05])
        if lt < 0.3 or lt > 6.6:
            hud(fr, lt, 0, held="dinopedia", swing=sw, walked=0)
            return self.fades(fr, lt)
        hud(fr, lt, 0, held="dinopedia", walked=0)
        if lt < 3.3:
            cur = tuple(catmull([(0.3, (290, 470)), (1.0, (300, 420)), (2.0, (172, 148)), (3.3, (174, 150))], lt))
            draw_menu(fr, "indice", cur, 0 if lt > 2.0 else None)
        else:
            cur = tuple(catmull([(3.3, (174, 150)), (4.0, (300, 250)), (5.0, (488, 300)), (6.6, (490, 302))], lt))
            draw_menu(fr, "especie", cur, 1 if lt > 5.0 else None)
        return self.fades(fr, lt)

    def sounds(self):
        return [(0.05, "page", 0.7), (3.25, "click", 0.6), (3.3, "page", 0.6), (6.6, "page", 0.4)]


class Tame(Shot):
    """Domar un raptor con carne cruda: humo la primera vez, corazones la segunda, y se sienta."""
    dur = 8.0

    def frame(self, lt):
        feet = np.array([105.5, gy(105.5, 196), 196.0])
        rap = dino("raptor", "rojizo")
        approach = Path([(125, 189), (118, 193), (109.8, 196)])
        d = min(approach.length, lt * 5.5)
        x, z = approach.pos(d)
        walking = 1.0 if d < approach.length - 0.1 else 0.0
        ryaw = approach.yaw(d) if walking else angle_lerp(approach.yaw(approach.length), 90, min(1, (lt - 2.0) / 0.4))
        rpos = (x, gy(x, z), z)
        look = np.array([x, rpos[1] + 1.0, z])
        eye = feet + np.array([0, 1.62, 0])
        dd = look - eye
        yaw = catmull([(0, -100), (1.5, yaw_of(dd[0], dd[2])), (8, yaw_of(dd[0], dd[2]))], lt)
        pitch = catmull([(0, 6), (2.0, 16), (8, 20)], lt)
        s = Scene3D(fp_cam(feet, yaw, pitch, 0), lt)
        if lt < 5.0:
            p = gait("biped", lt, walking, f=1.8)
            if not walking:
                p["head"] = (14 + 8 * math.sin(lt * 5), 0, 0, 0)   # olfatea
        else:
            p = sit_pose(lt)
        rap.add(s.q, rpos, ryaw, p)
        head = rap.head()
        for (pp, age, k) in burst(8, 3, lt - 2.7, head + np.array([0, 0.3, 0]), 0.5, 1.2, 1.4, -0.6):
            s.sprites.append((pp, 0.22, SMOKE[k % 4], 1 - age))
        for (pp, age, k) in burst(9, 5, lt - 4.3, head + np.array([0, 0.4, 0]), 0.7, 1.2, 1.8, -0.4):
            s.sprites.append((pp, 0.2, HEART, 1 - age * age))
        if lt > 5.3:
            s.tags.append((np.array([rpos[0], rpos[1] + 1.9, rpos[2]]), "&fRex"))
        fr = s.render()
        sel = 0 if lt < 0.7 else 1
        held = "dinopedia" if lt < 0.85 else "carne"
        chat = [(4.6, PRE + "&fSello &#ff00d9Domador&f de &#ff00d9Velociraptor &8(&#ff00d9+30&f de experiencia&8)&f.")]
        act = "&8| &fAhora se llama &#ff00d9Rex&f." if lt > 6.0 else None
        hud(fr, lt, sel, held=held, swing=swing_at(lt, [2.5, 4.1]), equip=equip_at(lt, [0.7]), chat=chat,
            action=act, action_a=window(lt, 6.0, 9, 0.2))
        return self.fades(fr, lt)

    def sounds(self):
        return [(0.3, "raptor/ambient1", 0.7), (0.7, "click", 0.3), (2.55, "eat", 0.8), (2.75, "fizz", 0.4),
                (4.15, "eat", 0.8), (4.3, "chime", 0.6), (4.4, "raptor/ambient2", 0.7), (4.6, "orb", 0.6),
                (6.0, "page", 0.4)] + [(k * 0.18, "raptor/step%d" % (1 + k % 3), 0.3) for k in range(11)]


class Ride(Shot):
    """Montado en el T-Rex en F5: corre por la llanura, ruge (vista de frente) y espanta a una manada."""
    dur = 12.0
    V_RUN = 9.0
    STOP = (6.0, 8.2)

    def dist(self, t):
        return run_dist(t, self.V_RUN, 0.0, 1.0, (self.STOP,))

    def frame(self, lt):
        x0 = 30.0
        d = self.dist(lt)
        x, z = x0 + d, RIDE_Z + math.sin(d * 0.05) * 2.0
        yaw = yaw_of(1, math.cos(d * 0.05) * 0.1)
        speed = (self.dist(lt + 0.05) - self.dist(lt - 0.05)) / 0.1
        walk = min(1.0, speed / self.V_RUN)
        roar = window(lt, 6.5, 8.1, 0.3)
        shake = 0.12 * window(lt, 6.6, 7.9, 0.2)
        rex = dino("trex", "verde", saddle=True)
        q = M.Quads()
        pose = V.biped(d / 6.5, walk, roar, swing=40, f=1.0)
        rex.add(q, (x, gy(x, z), z), yaw, pose)
        seat = rex.seat()
        eye = seat + np.array([0, 1.62 * 0.9375 - 0.35, 0])
        if 6.0 <= lt < 9.0:
            c = f5_front(eye, yaw, 12, 7.5, shake=shake, t=lt)
        else:
            c = f5_back(eye, yaw, 19, 6.0, fov=70 + 6 * walk, shake=shake, t=lt)
        s = Scene3D(c, lt)
        s.q = q
        player_quads(s.q, tuple(seat), yaw, head_pitch=10, sit=True)
        para = dino("parasaurolophus", "verde")
        for k, (hx, hz) in enumerate(((118, 146), (122, 153), (126, 148), (129, 155), (133, 150))):
            run = max(0.0, lt - 7.6 - k * 0.12)
            ang = (hz - RIDE_Z) * 0.05
            dx, dz = math.cos(ang), math.sin(ang) * 1.6
            nrm = math.hypot(dx, dz)
            dx, dz = dx / nrm, dz / nrm
            px, pz = hx - 16 + dx * run * 6.5, hz + dz * run * 6.5
            if run > 0:
                pyaw = yaw_of(dx, dz)
                pp = gait("quad", lt + k * 0.3, 1.0, f=1.6)
            else:
                pyaw = yaw_of(-1, 0.2 * (k - 2))
                pp = gait("quad", lt + k, 0.0)
                pp["head"] = (14, 0, 0, 0)
            para.add(s.q, (px, gy(px, pz), pz), pyaw, pp)
        fr = s.render()
        hud(fr, lt, 4, cross=False, mount=3)
        return self.fades(fr, lt)

    def sounds(self):
        out = [(0.0, "wind", 0.3), (0.2, "trex/ambient1", 0.6), (6.5, "trex/roar1", 1.0), (6.55, "boom", 0.6),
               (7.4, "parasaurolophus/roar1", 0.7), (7.8, "parasaurolophus/ambient2", 0.5)]
        t, k = 0.25, 0
        while t < self.dur:
            if not (self.STOP[0] + 0.3 < t < self.STOP[1]):
                out.append((t, "trex/step%d" % (1 + k % 3), 0.55))
            t += 0.5
            k += 1
        return out


class Fly(Shot):
    """Pteranodon en vuelo hacia el mar, con el Mapa del Paleontologo en la mano (barra de jefe)."""
    dur = 9.0

    def __init__(self):
        self.path = Path([(112, 96, 150), (140, 95, 141), (172, 92, 156), (205, 88, 146), (238, 84, 152), (275, 80, 148)])
        self.v = self.path.length / (self.dur + 0.5)

    def frame(self, lt):
        d = self.v * lt
        p = self.path.pos(d)
        a, b = self.path.pos(d - 2), self.path.pos(d + 2)
        yaw = yaw_of(b[0] - a[0], b[2] - a[2])
        y0, y1 = self.path.yaw(d - 3), self.path.yaw(d + 3)
        turn = ((y1 - y0 + 180) % 360) - 180
        roll = max(-25, min(25, -turn * 1.4))
        flap = 1.0 if (lt < 2.0 or 4.4 < lt < 6.0) else 0.12
        pt = dino("pteranodon", "gaviota", saddle=True)
        q = M.Quads()
        pt.add(q, tuple(p), yaw, gait("flyer", lt, flap), pitch=0, roll=roll)
        seat = pt.seat()
        eye = seat + np.array([0, 1.25, 0])
        c = f5_back(eye, yaw, 12, 5.0, fov=74)
        s = Scene3D(c, lt)
        s.q = q
        player_quads(s.q, tuple(seat), yaw, head_pitch=6, sit=True)
        fr = s.render()
        prog = 0.35 + 0.5 * ease(lt / self.dur)
        lat, lon = 38.9 + lt * 0.05, -100.4 + lt * 0.12
        boss = ("&#ff00d9%.1f&f, &#ff00d9%.1f &8- &fEstás en &#ff00d9Oeste de Norteamérica" % (lat, lon), prog)
        hud(fr, lt, 6, cross=False, mount=2, boss=boss)
        return self.fades(fr, lt)

    def sounds(self):
        return [(0.0, "wind", 0.6), (0.3, "pteranodon/ambient1", 0.7), (0.2, "flap", 0.5), (0.9, "flap", 0.5),
                (1.6, "flap", 0.5), (4.5, "flap", 0.5), (5.2, "flap", 0.5), (5.9, "flap", 0.4),
                (3.4, "pteranodon/roar1", 0.6)]


class Swim(Shot):
    """Mosasaurus bajo el agua en F5, entre algas, burbujas y peces."""
    dur = 7.0

    def __init__(self):
        self.path = Path([(256, 58.5, 118), (266, 57.5, 136), (262, 58.0, 156), (272, 57.0, 176), (280, 57.5, 192)])

    def frame(self, lt):
        d = 7.0 * lt + 2
        p = self.path.pos(d)
        yaw = self.path.yaw(d, 2.0)
        mo = dino("mosasaurus", "arrecife", saddle=True)
        q = M.Quads()
        bite = window(lt, 4.2, 5.2, 0.25)
        mo.add(q, tuple(p), yaw, gait("swim", lt, 0.8, bite))
        seat = mo.seat()
        eye = seat + np.array([0, 1.25, 0])
        c = f5_back(eye, yaw + 8 * math.sin(lt * 0.6), 10, 5.5, fov=72)
        s = Scene3D(c, lt, underwater=True)
        s.q = q
        player_quads(s.q, tuple(seat), yaw, head_pitch=4, sit=True)
        for (pp, age, k) in burst(30, 9, (lt % 2.0) + 0.0, seat + np.array([0, 0.5, 0]), 0.6, 2.5, 2.0, -1.5):
            s.sprites.append((pp, 0.12, BUBBLE, 1 - age))
        rng = np.random.RandomState(4)
        for k in range(18):   # peces tropicales
            base = np.array([p[0] + rng.uniform(-6, 6), p[1] + rng.uniform(-1.5, 2.5), p[2] + rng.uniform(4, 18)])
            ang = rng.uniform(0, 6.28) + lt * 0.6
            fp = base + np.array([math.cos(ang), 0, math.sin(ang)]) * 1.2
            col = [(250, 140, 30), (240, 220, 60), (80, 180, 230), (230, 80, 120)][k % 4]
            s.sprites.append((fp, 0.18, FISH[col], 1.0))
        fr = s.render()
        hud(fr, lt, 1, cross=False, mount=3)
        return self.fades(fr, lt)

    def sounds(self):
        return [(0.0, "underwater", 0.4), (0.6, "mosasaurus/swim1", 0.7), (2.8, "mosasaurus/swim2", 0.6),
                (4.2, "mosasaurus/attack1", 0.8), (5.6, "mosasaurus/ambient1", 0.6)]


def _fish(col):
    a = np.zeros((4, 7, 4), dtype=np.uint8)
    rows = ["..CCC..", "CCCCCWK", "CCCCCCC", "..CCC.."]
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c == "C":
                a[y, x] = col + (255,)
            elif c == "W":
                a[y, x] = (255, 255, 255, 255)
            elif c == "K":
                a[y, x] = (20, 20, 20, 255)
    return a


FISH = {c: _fish(c) for c in [(250, 140, 30), (240, 220, 60), (80, 180, 230), (230, 80, 120)]}


class Nest(Shot):
    """Nido salvaje: se acerca, saca un huevo, la madre lo ve, ruge y lo corre."""
    dur = 8.0
    NEST = (190.0, 92.0)

    def frame(self, lt):
        nx, nz = self.NEST
        ny = gy(nx, nz)
        start = np.array([179.0, 0, 97.0])
        stop = np.array([nx - 2.2, 0, nz + 1.0])
        k = ease(min(1.0, lt / 3.0))
        fx, fz = lerp(start[0], stop[0], k), lerp(start[2], stop[2], k)
        walked = 3.2 * min(lt, 3.0)
        flee = max(0.0, lt - 6.0)
        mother = dino("raptor", "gris", "female")
        mpath_wait = np.array([nx + 6, nz - 5])
        # la madre: pasea, se da vuelta (4.0), ruge (4.3) y carga (5.0)
        to_player = np.array([fx - mpath_wait[0], fz - mpath_wait[1]])
        dirv = np.array([stop[0] - mpath_wait[0], stop[2] - mpath_wait[1]])
        dist0 = float(np.linalg.norm(dirv))
        dirv /= dist0
        if lt < 4.0:
            mx, mz = mpath_wait[0] + math.sin(lt * 0.6) * 1.5, mpath_wait[1] + math.cos(lt * 0.6)
            myaw = yaw_of(math.cos(lt * 0.6) * 1.5, -math.sin(lt * 0.6))
            mp = gait("biped", lt, 0.4)
        else:
            # todo sobre la linea madre -> jugador: el jugador huye por ella y la madre lo alcanza
            a_p = dist0 + flee * 5.6
            a_m = min(max(0.0, lt - 5.0) * 7.5, a_p - 2.7)
            mx, mz = mpath_wait[0] + dirv[0] * a_m, mpath_wait[1] + dirv[1] * a_m
            myaw = angle_lerp(yaw_of(math.cos(4.0 * 0.6), -math.sin(4.0 * 0.6)), yaw_of(dirv[0], dirv[1]),
                              min(1, (lt - 4.0) / 0.3))
            mp = gait("biped", lt, 1.0 if lt > 5.0 else 0.0, window(lt, 4.3, 5.1, 0.15) + window(lt, 7.0, 7.6, 0.1), f=2.0)
        if flee > 0:
            fx, fz = fx + dirv[0] * flee * 5.6, fz + dirv[1] * flee * 5.6
            walked += flee * 5.6
        feet = np.array([fx, gy(fx, fz), fz])
        eye = feet + np.array([0, 1.62, 0])
        look_nest = np.array([nx, ny + 0.2, nz]) - eye
        look_mom = np.array([mx, gy(mx, mz) + 1.2, mz]) - eye
        yaw_n, pitch_n = yaw_of(look_nest[0], look_nest[2]), math.degrees(-math.atan2(look_nest[1], math.hypot(look_nest[0], look_nest[2])))
        yaw_m = yaw_of(look_mom[0], look_mom[2])
        if lt < 4.0:
            yaw, pitch = catmull([(0, yaw_n + 8), (3.0, yaw_n), (4.0, yaw_n)], lt), catmull([(0, 15), (3, pitch_n), (4, pitch_n)], lt)
        elif lt < 6.0:
            yaw, pitch = angle_lerp(yaw_n, yaw_m, ease((lt - 4.0) / 0.5)), lerp(pitch_n, 4, ease((lt - 4.0) / 0.5))
        elif lt < 6.9:
            yaw, pitch = angle_lerp(yaw_m, yaw_m + 180, ease((lt - 6.0) / 0.45)), 8
        else:   # mira para atras: la madre encima
            yaw = angle_lerp(yaw_m + 180, yaw_of(mx - fx, mz - fz), ease((lt - 6.9) / 0.3))
            pitch = 2
        hurt = window(lt, 7.3, 7.75, 0.12)
        c = fp_cam(feet, yaw, pitch, walked, fov=70 + 10 * min(1, flee * 2), roll=8 * hurt, shake=0.05 * hurt, t=lt)
        s = Scene3D(c, lt)
        nest = dino("@nest", None, None, 1.0)
        nest.add(s.q, (nx, ny, nz), 20, {})
        egg = dino("raptor", "egg", None, 0.5)
        eggs = [(-0.3, -0.14), (0.3, -0.16), (0.02, 0.3)]
        n = 3 if lt < 3.75 else 2
        for (ox, oz) in eggs[:n]:
            egg.add(s.q, (nx + ox, ny + 0.12, nz + oz), 30 + ox * 90, {})
        if 3.75 <= lt < 4.6:
            for (pp, age, k) in burst(10, 2, lt - 3.75, (nx + 0.02, ny + 0.4, nz + 0.27), 1.2, 1.0, 0.8, 6):
                s.sprites.append((pp, 0.12, chunk_of("grass_top" if k % 2 else "log", k), 1.0))
        mother.add(s.q, (mx, gy(mx, mz), mz), myaw, mp)
        fr = s.render()
        items = list(ITEMS)
        if lt >= 3.75:
            items[8] = "raptor/egg_icon"
        act, aa = None, 0
        if 1.4 <= lt < 3.7:
            act, aa = "&8| &fNido de &#ff00d9Velociraptor&f con &#ff00d93&f huevo(s), cuidado con la madre.", window(lt, 1.4, 3.7, 0.2)
        elif lt >= 3.8:
            act, aa = "&8| &fSacaste un huevo de &#ff00d9Velociraptor&f y la madre te vio, corré.", window(lt, 3.8, 9, 0.15)
        hud(fr, lt, 5, items=items, held="espada", swing=swing_at(lt, [3.55]), walked=walked, action=act,
            action_a=aa, hearts=20 if lt < 7.3 else 15)
        return self.fades(fr, lt)

    def sounds(self):
        out = [(0.0, "wind", 0.25), (3.6, "pop", 0.7), (3.65, "crack", 0.3), (4.3, "raptor/roar1", 1.0),
               (5.0, "raptor/attack1", 0.6), (6.0, "whoosh", 0.4), (7.3, "hurt", 0.9), (7.35, "raptor/attack2", 0.8)]
        out += [(k * 0.4, "step", 0.25) for k in range(8)] + [(6.0 + k * 0.28, "step", 0.3) for k in range(7)]
        out += [(5.0 + k * 0.16, "raptor/step%d" % (1 + k % 3), 0.4) for k in range(18)]
        return out


class Hatch(Shot):
    """El huevo en el piso: se mueve, se rompe y nace la cria."""
    dur = 6.5
    EGG = (130.0, 118.0)

    def frame(self, lt):
        ex, ez = self.EGG
        ey = gy(ex, ez)
        feet = np.array([ex - 2.6, gy(ex - 2.6, ez + 0.6), ez + 0.6])
        eye = feet + np.array([0, 1.62, 0])
        dd = np.array([ex, ey + 0.3, ez]) - eye
        yaw = yaw_of(dd[0], dd[2])
        pitch = math.degrees(-math.atan2(dd[1], math.hypot(dd[0], dd[2]))) - 6
        s = Scene3D(fp_cam(feet, yaw + 2 * math.sin(lt * 0.5), pitch, 0), lt)
        wob = (math.sin(lt * 26) * 9 * window(lt, 0.9, 1.5, 0.1) + math.sin(lt * 30) * 12 * window(lt, 1.9, 2.7, 0.1))
        if lt < 2.6:
            dino("raptor", "egg", None).add(s.q, (ex, ey, ez), 30, {}, roll=wob)
        elif lt < 3.1:
            dino("raptor", "egg_cracked", None).add(s.q, (ex, ey, ez), 30, {}, roll=wob * 0.3)
        else:
            u = lt - 3.1
            hop = max(0.0, math.sin(min(u, 0.5) / 0.5 * math.pi)) * 0.35
            bx = ex - min(u, 2.6) * 0.45
            baby = dino("raptor", "rojizo", "baby")
            p = gait("biped", lt, 1.0 if 0.6 < u < 2.6 else 0.0, f=2.2)
            if u > 2.6:
                p["head"] = (-12, 0, 0, 0)
            baby.add(s.q, (bx, gy(bx, ez) + hop, ez), angle_lerp(90, yaw + 180, min(1, u / 1.2)), p)
        for (pp, age, k) in burst(14, 8, lt - 3.05, (ex, ey + 0.35, ez), 1.4, 1.5, 0.7, 5):
            s.sprites.append((pp, 0.11, chunk_of("sand", k), 1.0))
        for (pp, age, k) in burst(10, 6, lt - 3.05, (ex, ey + 0.3, ez), 0.6, 1.0, 1.1, -0.5):
            s.sprites.append((pp, 0.25, SMOKE[k % 4], 1 - age))
        fr = s.render()
        if lt < 3.0:
            pc = min(100, 96 + int(lt * 1.5))
            act, aa = "&8| &fHuevo de &#ff00d9Velociraptor&8: &#ff00d9%d%%&f listo." % pc, window(lt, 0.0, 3.0, 0.15)
        else:
            act, aa = "&8| &fNació una cría de &#ff00d9Velociraptor Rojizo &8(&f♀&8)&f.", window(lt, 3.1, 9, 0.15)
        hud(fr, lt, 0, held="dinopedia", action=act, action_a=aa)
        return self.fades(fr, lt)

    def sounds(self):
        return [(0.9, "egg/crack1", 0.8), (1.9, "egg/crack2", 0.8), (2.55, "egg/crack3", 0.9), (3.05, "egg/hatch1", 1.0),
                (3.1, "pop", 0.6), (3.8, "raptor/baby_ambient1", 0.8), (5.6, "raptor/baby_ambient2", 0.7)]


class Whistle(Shot):
    """Silbato: menu de ordenes, sus dinos lo siguen; jaula: guarda a Rex."""
    dur = 10.5

    def frame(self, lt):
        if lt < 1.9:   # primera persona, agachado, abre el menu del silbato
            feet = np.array([150.0, gy(150, 214), 214.0])
            c = fp_cam(feet, 180, 12, 0, sneak=lt > 0.1)
            s = Scene3D(c, lt)
            self.followers(s, 0.0, feet, 180)
            fr = s.render()
            hud(fr, lt, 2, held="silbato", swing=swing_at(lt, [0.25]))
            if 0.45 <= lt < 1.75:
                cur = tuple(catmull([(0.45, (276, 470)), (1.1, (172, 150)), (1.75, (172, 150))], lt))
                draw_menu(fr, "silbato", cur, None)
            return self.fades(fr, lt, 0.15, 0.0)
        if lt < 5.4:   # F5 de frente: camina hacia la camara y lo siguen tres dinos
            u = lt - 1.9
            z = 214 - u * 4.3
            feet = np.array([150.0, gy(150, z), z])
            eye = feet + np.array([0, 1.62, 0])
            c = f5_front(eye, 180 + 10 * math.sin(u * 0.7) + 12, 20, 4.6)
            s = Scene3D(c, lt)
            player_quads(s.q, tuple(feet), 180, head_pitch=-4, walk=1.0, t=u)
            self.followers(s, u, feet, 180)
            fr = s.render()
            hud(fr, lt, 2, cross=False, action="&8| &#ff00d93 dinos&f te siguen.", action_a=window(lt, 1.95, 4.6, 0.2))
            return black(fr, 1 - (lt - 1.9) / 0.12 if lt < 2.02 else 0)
        # primera persona: Rex sentado adelante, cambia a la jaula y lo guarda
        u = lt - 5.4
        feet = np.array([150.0, gy(150, 199), 199.0])
        rx, rz = 150.0, 195.6
        c = fp_cam(feet, 180, 20, 0)
        s = Scene3D(c, lt)
        rap = dino("raptor", "rojizo")
        if u < 1.75:
            rap.add(s.q, (rx, gy(rx, rz), rz), 0, sit_pose(lt))
            s.tags.append((np.array([rx, gy(rx, rz) + 1.9, rz]), "&fRex"))
        for (pp, age, k) in burst(16, 4, u - 1.75, (rx, gy(rx, rz) + 0.8, rz), 1.0, 1.0, 1.0, -0.3):
            s.sprites.append((pp, 0.2, SMOKE[k % 4], 1 - age))
        fr = s.render()
        items = list(ITEMS)
        if u >= 1.75:
            items[3] = "jaula_llena"
        held = "silbato" if u < 0.55 else items[3]
        hud(fr, lt, 2 if u < 0.4 else 3, items=items, held=held, swing=swing_at(u, [1.6]), equip=equip_at(u, [0.4]),
            action="&8| &fGuardaste a &#ff00d9Rex&f en la jaula." if u >= 1.8 else None, action_a=window(u, 1.8, 9, 0.15))
        return self.fades(black(fr, 1 - u / 0.12 if u < 0.12 else 0), lt, 0.0, 0.2)

    def followers(self, s, u, feet, yaw):
        f = M.Cam((0, 0, 0), yaw, 0).f
        r = M.Cam((0, 0, 0), yaw, 0).r
        crew = [(dino("raptor", "rojizo"), "biped", 3.0, 1.6, "Rex", 1.9, 2.0),
                (dino("parasaurolophus", "azul"), "quad", 6.5, -2.4, "Luna", 3.1, 1.3),
                (dino("triceratops", "oliva", "baby"), "quad", 6.0, 2.6, "Pipo", 1.4, 2.0)]
        for d, kind, back, side, name, tagh, fq in crew:
            p = np.asarray(feet, float) - f * back + r * side
            x, z = p[0] + math.sin(u * 2 + back) * 0.15, p[2]
            moving = u > 0
            d.add(s.q, (x, gy(x, z), z), yaw, gait(kind, u + back, 1.0 if moving else 0.0, f=fq))
            s.tags.append((np.array([x, gy(x, z) + tagh, z]), "&f" + name))

    def sounds(self):
        out = [(0.25, "click", 0.4), (0.45, "page", 0.4), (1.1, "click", 0.6), (1.2, "whistle", 0.9),
               (2.0, "raptor/ambient2", 0.5), (3.0, "parasaurolophus/ambient1", 0.5), (3.9, "triceratops/baby_ambient1", 0.5),
               (5.8, "click", 0.3), (7.0, "chain", 0.8), (7.15, "pop", 0.6), (7.2, "orb", 0.4)]
        out += [(1.9 + k * 0.36, "step", 0.25) for k in range(10)]
        return out


class Plus(Shot):
    """Linea Plus: Velociraptor Sombra con dash y Ankylosaurus Leñador que tala un arbol de un golpe."""
    dur = 9.5
    TREE = (205.0, 148.0)

    def frame(self, lt):
        if lt < 4.6:
            dash = window(lt, 1.6, 2.2, 0.08)
            d = run_dist(lt, 7.5, 0.0, 0.6) + 14.0 * max(0.0, min(lt, 2.2) - 1.6) + 0.0
            x, z = 140 + d, RIDE_Z
            yaw = -90
            rp = dino("raptor_plus", "carmesi", saddle=True)
            q = M.Quads()
            rp.add(q, (x, gy(x, z), z), yaw, V.biped(d / 3.2, 1.0, swing=42, f=1.0))
            seat = rp.seat()
            eye = seat + np.array([0, 1.25, 0])
            c = f5_back(eye, yaw, 22, 5.2, fov=70 + 14 * dash)
            s = Scene3D(c, lt)
            s.q = q
            player_quads(s.q, tuple(seat), yaw, head_pitch=8, sit=True)
            rng = np.random.RandomState(3)
            for k in range(26):   # estela roja del dash
                age = lt - 1.6 - k * 0.025
                if 0 <= age < 0.9:
                    back = 14.0 * max(0.0, min(lt, 2.2) - 1.6) * (k / 26)
                    pp = np.array([x - back, gy(x - back, z) + 0.4 + rng.uniform(0, 1.0), z + rng.uniform(-0.6, 0.6)])
                    s.sprites.append((pp, 0.16, DUST_RED, 1 - age / 0.9))
            fr = s.render()
            hud(fr, lt, 4, items=PLUS_ITEMS, cross=False, mount=2, action="&8| &#ff00d9Dash&f.", action_a=window(lt, 1.6, 4.0, 0.15))
            return self.fades(fr, lt, 0.15, 0.12)
        u = lt - 4.6
        tx, tz = self.TREE
        d = min(6.6, run_dist(u, 4.0, 0.0, 0.5))
        x, z = 194.0 + d, RIDE_Z + 0.3
        yaw = -90
        an = dino("ankylosaurus_plus", "carmesi", saddle=True)
        q = M.Quads()
        an.add(q, (x, gy(x, z), z), yaw, V.quad(u, 1.0 if d < 6.5 else 0.0, f=1.1))
        seat = an.seat()
        eye = seat + np.array([0, 1.25, 0])
        c = f5_back(eye, yaw + 62, 16, 8.0)
        s = Scene3D(c, u)
        s.q = q
        player_quads(s.q, tuple(seat), yaw, head_pitch=6, sit=True, swing=swing_at(u, [2.0], 0.35))
        cut = u >= 2.2
        ty = gy(tx, tz)
        if not cut:
            M.box(s.q, (tx, ty, tz), (tx + 1, ty + 5, tz + 1), {"up": "log_top", "down": "log_top", "side": "log"})
            M.box(s.q, (tx - 2, ty + 2, tz - 2), (tx + 3, ty + 4, tz + 3), "leaves", cull=False)
            M.box(s.q, (tx - 1, ty + 4, tz - 1), (tx + 2, ty + 6, tz + 2), "leaves", cull=False)
        for (pp, age, k) in burst(40, 7, u - 2.2, (tx + 0.5, ty + 3.0, tz + 0.5), 2.2, 1.0, 1.1, 9):
            s.sprites.append((pp, 0.16, chunk_of("leaves" if k % 3 else "log", k), 1.0))
        for k in range(5):   # troncos que caen como items
            age = u - 2.25 - k * 0.04
            if age > 0:
                hgt = max(0.0, 2.5 + k * 0.6 - 9 * age * age + 3 * age) if age < 1.2 else 0.0
                pp = (tx + 0.5 + (k - 2) * 0.35, ty + 0.25 + hgt + 0.08 * math.sin(lt * 3 + k), tz + 0.5 + (k % 2) * 0.4)
                s.sprites.append((pp, 0.32, LOG_ITEM, 1.0))
        fr = s.render()
        hud(fr, lt, 4, items=PLUS_ITEMS, cross=False, mount=3, action="&8| &#ff00d9Tala&f." if cut else None,
            action_a=window(u, 2.2, 9, 0.15))
        return self.fades(black(fr, 1 - u / 0.12 if u < 0.12 else 0), lt, 0.0, 0.15)

    def sounds(self):
        out = [(0.2, "raptor_plus/ambient1", 0.6), (1.6, "whoosh", 0.9), (1.6, "zap", 0.5),
               (4.7, "ankylosaurus_plus/ambient1", 0.6), (6.6, "chop", 0.9), (6.8, "crack", 0.6), (6.85, "leaves", 0.7),
               (7.1, "pop", 0.4)]
        out += [(k * 0.24, "raptor_plus/step%d" % (1 + k % 3), 0.35) for k in range(18)]
        out += [(4.6 + k * 0.45, "ankylosaurus_plus/step%d" % (1 + k % 2), 0.5) for k in range(5)]
        return out


PLUS_ITEMS = ["dinopedia", "carne", "silbato", "jaula", "montura_raptor", "espada", "mapa", ("pan", 16), "montura_ankylosaurus"]
DUST_RED = np.array([[[200, 20, 30, 255]] * 3] * 3, dtype=np.uint8)
LOG_ITEM = None


def _log_item():
    t = M.TEX[M.TEX_ID["log"]]
    return t


class Outro(Shot):
    """Vuelta de camara alrededor del T-Rex montado que ruge; logo y a negro."""
    dur = 6.5

    def frame(self, lt):
        x, z = 70.0, RIDE_Z
        y = gy(x, z)
        rex = dino("trex", "oscuro", saddle=True)
        ang = math.radians(75 - lt * 8)
        cp = np.array([x + math.cos(ang) * 8.5, y + 1.4, z + math.sin(ang) * 8.5])
        s = Scene3D(M.look_at(cp, (x, y + 1.3, z)), lt)
        roar = window(lt, 0.8, 2.6, 0.3)
        rex.add(s.q, (x, y, z), -60, V.biped(lt, 0.0, roar))
        player_quads(s.q, tuple(rex.seat()), -60, head_pitch=0, sit=True, arms_up=0.6 * roar)
        fr = s.render()
        a = window(lt, 2.4, 9, 0.8)
        if a > 0:
            grad = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            dg = ImageDraw.Draw(grad)
            for yy in range(H // 2, H):
                dg.line([(0, yy), (W, yy)], fill=(0, 0, 0, int(150 * a * (yy - H / 2) / (H / 2))))
            fr.alpha_composite(grad)
            lg = logo(int(W * 0.30))
            U.paste(fr, lg, W // 2 - lg.size[0] // 2, int(H * 0.64), a)
        return black(fr, max(0, (lt - (self.dur - 1.0)) / 0.9))

    def sounds(self):
        return [(0.0, "wind", 0.4), (0.8, "trex/roar2", 1.0), (0.85, "boom", 0.6), (2.4, "chime", 0.5)]


SHOTS = []
TIMELINE = []


def setup():
    global LOG_ITEM
    world()
    if LOG_ITEM is None:
        LOG_ITEM = _log_item()
    if not SHOTS:
        SHOTS.extend([Intro(), Walk(), Dex(), Tame(), Ride(), Fly(), Swim(), Nest(), Hatch(), Whistle(), Plus(), Outro()])
        t = 0.0
        for sc in SHOTS:
            TIMELINE.append((t, sc))
            t += sc.dur
    return sum(sc.dur for sc in SHOTS)


def frame_at(i):
    t = i / FPS
    for start, sc in TIMELINE:
        if start <= t < start + sc.dur:
            return sc.frame(t - start)
    start, sc = TIMELINE[-1]
    return sc.frame(sc.dur - 1e-3)


# ================================================================ sonido
SR = V.SR


def sfx(name):
    n = lambda d: int(d * SR)
    t = lambda d: np.arange(n(d)) / SR
    rng = np.random.default_rng(abs(hash(name)) % 1000)

    def lowpass(x, k):
        y = np.zeros_like(x)
        acc = 0.0
        a = 1.0 / k
        # filtro de un polo vectorizado por bloques (suficiente para ruido)
        y = np.convolve(x, np.ones(k) / k, mode="same")
        return y
    if name == "wind":
        x = rng.standard_normal(n(12.0))
        y = lowpass(lowpass(x, 60), 60) * 6
        env = 0.6 + 0.4 * np.sin(np.arange(len(y)) / SR * 0.9) ** 2
        return (y * env * 0.5).astype(np.float32)
    if name == "underwater":
        x = rng.standard_normal(n(8.0))
        y = lowpass(lowpass(lowpass(x, 120), 120), 80) * 18
        bub = np.zeros_like(y)
        for k in range(30):
            k0 = int(rng.uniform(0, len(y) - n(0.05)))
            d = t(0.05)
            bub[k0:k0 + len(d)] += np.sin(2 * np.pi * rng.uniform(400, 900) * d * (1 + d * 8)) * np.exp(-d * 60) * 0.3
        return ((y * 0.6 + bub) * 0.6).astype(np.float32)
    if name == "step":
        x = t(0.09)
        return (lowpass(rng.standard_normal(len(x)), 6) * np.exp(-x * 45) * 1.4).astype(np.float32)
    if name == "click":
        x = t(0.05)
        return (np.sin(2 * np.pi * 2000 * x) * np.exp(-x * 120) * 0.5).astype(np.float32)
    if name == "fizz":
        x = t(0.5)
        return (rng.standard_normal(len(x)) * np.exp(-x * 6) * 0.25).astype(np.float32)
    if name == "orb":
        x = t(0.4)
        f = 1400 + 900 * x
        return (np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-x * 8) * 0.35).astype(np.float32)
    if name == "flap":
        x = t(0.35)
        y = lowpass(rng.standard_normal(len(x)), 10)
        return (y * np.sin(np.pi * x / 0.35) ** 2 * 2.2).astype(np.float32)
    if name == "hurt":
        x = t(0.3)
        return ((np.sin(2 * np.pi * (300 - 400 * x) * x) * 0.6 + rng.standard_normal(len(x)) * 0.2) * np.exp(-x * 9)).astype(np.float32)
    if name == "chop":
        x = t(0.25)
        return ((np.sin(2 * np.pi * 180 * x) * 0.8 + rng.standard_normal(len(x)) * 0.5) * np.exp(-x * 22)).astype(np.float32)
    if name == "leaves":
        x = t(0.9)
        return (lowpass(rng.standard_normal(len(x)), 3) * np.exp(-x * 4) * 0.5).astype(np.float32)
    a = V.sfx(name)
    if a is None:
        a = V.load(name)
    if a is None and "/" in name:   # los Plus usan los sonidos de su especie; si falta un numero, el 1
        sp, snd = name.split("/")
        base = sp.replace("_plus", "")
        a = V.load(base + "/" + snd)
        if a is None:
            a = V.load(base + "/" + snd.rstrip("0123456789") + "1")
    return a


def music(total, intense_from):
    """Base de la musica del trailer (mismo estilo), mas baja: lo que manda son los sonidos del juego."""
    N = int(total * SR)
    out = np.zeros(N, dtype=np.float32)
    beat = 60 / 100
    rng = np.random.default_rng(4)
    tt = np.arange(int(0.5 * SR)) / SR
    kick = np.sin(2 * np.pi * (95 * tt - 70 * tt * tt)) * np.exp(-tt * 7)
    tom = (np.sin(2 * np.pi * 140 * tt) * np.exp(-tt * 9) + 0.4 * rng.standard_normal(len(tt)) * np.exp(-tt * 25))
    hat = rng.standard_normal(int(0.06 * SR)) * np.exp(-np.arange(int(0.06 * SR)) / SR * 70)
    notes = [220.0, 261.6, 329.6, 261.6, 196.0, 246.9, 293.7, 246.9]
    t, i = 1.4, 0
    while t < total - 3.0:
        k = int(t * SR)

        def add(sig, gain, k=k):
            e = min(N, k + len(sig))
            out[k:e] += (sig[:e - k] * gain).astype(np.float32)
        add(kick, 0.8)
        if i % 4 in (1, 3):
            add(tom, 0.35)
        intense = t >= intense_from
        if intense:
            for h in (0, 0.5):
                kh = int((t + h * beat) * SR)
                e = min(N, kh + len(hat))
                out[kh:e] += hat[:e - kh] * 0.16
        for half in (0, 1):
            f = notes[(i * 2 + half) % 8] * (2 if intense else 1)
            d = np.arange(int(beat / 2 * SR)) / SR
            pl = (np.sin(2 * np.pi * f * d) + 0.4 * np.sin(4 * np.pi * f * d)) * np.exp(-d * 6)
            kk = int((t + half * beat / 2) * SR)
            e = min(N, kk + len(pl))
            out[kk:e] += pl[:e - kk] * 0.14
        t += beat
        i += 1
    x = np.arange(N) / SR
    root = np.where((x // (beat * 8)) % 2 == 0, 55.0, 49.0)
    phase = np.cumsum(2 * np.pi * root / SR)
    bass = (np.sin(phase) + 0.3 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase)) * 0.2
    env = np.clip((x - 1.4) / 2, 0, 1) * np.clip((total - 2.5 - x) / 2, 0, 1)
    out += (bass * env).astype(np.float32)
    return out


def soundtrack(total):
    N = int(total * SR) + SR
    mix = np.zeros(N, dtype=np.float32)
    plus_start = [st for st, sc in TIMELINE if isinstance(sc, Ride)][0]
    m = music(total, plus_start)
    mix[:len(m)] += m * 0.32
    cache = {}
    for start, sc in TIMELINE:
        for at, name, vol in sc.sounds():
            if name not in cache:
                cache[name] = sfx(name)
            a = cache[name]
            if a is None:
                print("falta el sonido", name)
                continue
            k = int((start + at) * SR)
            # que no se pase de su toma (el viento y el agua duran lo que la toma)
            end = int((start + sc.dur) * SR) if name in ("wind", "underwater") else N
            e = min(N, k + len(a), end)
            if e <= k:
                continue
            seg = a[:e - k] * vol
            if name in ("wind", "underwater"):
                fade = min(len(seg), int(0.25 * SR))
                seg = seg.copy()
                seg[:fade] *= np.linspace(0, 1, fade)
                seg[-fade:] *= np.linspace(1, 0, fade)
            mix[k:e] += seg
    peak = np.max(np.abs(mix)) or 1
    mix = mix / peak * 0.9
    return mix[:int(total * SR)]


# ================================================================ main
def _render(i):
    return frame_at(i).convert("RGB").tobytes()


def main():
    args = sys.argv[1:]
    total = setup()
    os.makedirs(OUT, exist_ok=True)
    if args and args[0] == "preview":
        thumbs = []
        for start, sc in TIMELINE:
            thumbs.append(sc.frame(sc.dur * 0.55).convert("RGB").resize((W // 4, H // 4), Image.LANCZOS))
        cols = 4
        rows = (len(thumbs) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * W // 4, rows * H // 4))
        for k, im in enumerate(thumbs):
            sheet.paste(im, ((k % cols) * W // 4, (k // cols) * H // 4))
        sheet.save(os.path.join(OUT, "ingame-preview.png"))
        print("preview listo", total, "s")
        return
    if args and args[0] == "audio":   # rearma solo el audio de un video ya renderizado
        import soundfile as sf
        wav = os.path.join(OUT, "ingame-audio.wav")
        sf.write(wav, soundtrack(total), SR)
        out = os.path.join(OUT, "dinoearth-ingame.mp4")
        tmp = out + ".tmp.mp4"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", out, "-i", wav, "-map", "0:v", "-map", "1:a",
                        "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", tmp],
                       check=True)
        os.replace(tmp, out)
        os.remove(wav)
        print("audio listo")
        return
    if args and args[0] == "frame":
        t = float(args[1])
        frame_at(int(t * FPS)).convert("RGB").save(os.path.join(OUT, "ingame-frame.png"))
        return
    nframes = int(total * FPS)
    import soundfile as sf
    wav = os.path.join(OUT, "ingame-audio.wav")
    sf.write(wav, soundtrack(total), SR)
    out = os.path.join(OUT, "dinoearth-ingame.mp4")
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % (W, H),
           "-r", str(FPS), "-i", "-", "-i", wav, "-c:v", "libx264", "-preset", "slow", "-crf", "27",
           "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", out]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    with Pool(int(os.environ.get("JOBS", os.cpu_count() or 4))) as pool:
        for k, data in enumerate(pool.imap(_render, range(nframes), chunksize=2)):
            proc.stdin.write(data)
            if k % 60 == 0:
                print("cuadro", k, "de", nframes, flush=True)
    proc.stdin.close()
    proc.wait()
    os.remove(wav)
    print("listo:", out)


if __name__ == "__main__":
    main()

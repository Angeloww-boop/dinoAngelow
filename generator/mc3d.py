"""Motor 3D chiquito para el video "como si fuera in game": camara en perspectiva como la de Minecraft (FOV 70),
mundo de bloques con texturas dibujadas aca (parecidas a las de vanilla, que no se pueden copiar), sombreado por
cara como el juego, niebla de distancia, agua transparente, nubes, y los dinos con los modelos reales del pack.

Todo en numpy: cada cara (cuadrilatero plano) se recorta contra el plano cercano y se rasteriza con 1/z, s/z y t/z
afines en pantalla (textura con perspectiva correcta).
"""
import math

import numpy as np

from build import faces_of, rotate, to_local

NEAR = 0.05


def _n(v):
    v = np.asarray(v, dtype=np.float64)
    return v / (np.linalg.norm(v) or 1.0)


# ================================================================ camara
class Cam:
    """yaw/pitch como Minecraft: yaw 0 mira a +Z, 90 a -X; pitch positivo mira para abajo."""

    def __init__(self, pos, yaw, pitch, fov=70.0, roll=0.0):
        self.pos = np.asarray(pos, dtype=np.float64)
        self.yaw, self.pitch, self.fov = yaw, pitch, fov
        y, p = math.radians(yaw), math.radians(pitch)
        f = np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])
        r = _n(np.cross(f, [0, 1, 0]))
        u = np.cross(r, f)
        if roll:
            a = math.radians(roll)
            r, u = r * math.cos(a) + u * math.sin(a), -r * math.sin(a) + u * math.cos(a)
        self.f, self.r, self.u = f, r, u
        self.R = np.stack([r, u, f])     # mundo -> camara (x derecha, y arriba, z adelante)


def look_at(pos, target, fov=70.0, roll=0.0):
    d = np.asarray(target, dtype=np.float64) - np.asarray(pos, dtype=np.float64)
    yaw = math.degrees(math.atan2(-d[0], d[2]))
    pitch = math.degrees(-math.atan2(d[1], math.hypot(d[0], d[2])))
    return Cam(pos, yaw, pitch, fov, roll)


# ================================================================ texturas
TEX = []          # arrays (h, w, 4) uint8
TEX_ID = {}


def add_tex(name, arr):
    if name in TEX_ID:
        TEX[TEX_ID[name]] = arr
        return TEX_ID[name]
    TEX_ID[name] = len(TEX)
    TEX.append(np.ascontiguousarray(arr, dtype=np.uint8))
    MIPS.append(None)
    return TEX_ID[name]


MIPS = []


def mips(tid):
    """Niveles de mipmap (como el juego): lista de (flat rgba, offsets, anchos, altos) para muestrear por pixel."""
    if MIPS[tid] is None:
        a = TEX[tid].astype(np.float32)
        levels = [a]
        while min(a.shape[0], a.shape[1]) > 1 and len(levels) < 5:
            h2, w2 = a.shape[0] // 2, a.shape[1] // 2
            b = a[:h2 * 2, :w2 * 2].reshape(h2, 2, w2, 2, 4)
            al = b[..., 3:4]
            rgb = (b[..., :3] * al).sum((1, 3)) / np.maximum(al.sum((1, 3)), 1e-3)
            alpha = b[..., 3].mean((1, 3))
            a = np.concatenate([rgb, alpha[..., None]], axis=-1)
            levels.append(a)
        flat = np.concatenate([lv.reshape(-1, 4) for lv in levels]).astype(np.uint8)
        offs, ws, hs, o = [], [], [], 0
        for lv in levels:
            offs.append(o)
            hs.append(lv.shape[0])
            ws.append(lv.shape[1])
            o += lv.shape[0] * lv.shape[1]
        MIPS[tid] = (flat, np.array(offs), np.array(ws), np.array(hs))
    return MIPS[tid]


def _noise16(seed, base, var, size=(16, 16)):
    rng = np.random.RandomState(seed)
    n = rng.rand(*size)
    out = np.zeros(size + (4,), dtype=np.float64)
    for i in range(3):
        out[..., i] = base[i] + (n - 0.5) * 2 * var[i]
    out[..., 3] = 255
    return out


def _clip(a):
    return np.clip(a, 0, 255).astype(np.uint8)


def make_textures():
    """Texturas de 16x16 al estilo de vanilla (dibujadas por programa)."""
    rng = np.random.RandomState(7)
    # pasto (tono de llanura)
    g = _noise16(1, (110, 168, 72), (14, 18, 12))
    spots = rng.rand(16, 16)
    g[spots > 0.86, :3] *= 0.82
    g[spots < 0.08, :3] *= 1.12
    add_tex("grass_top", _clip(g))
    d = _noise16(2, (134, 96, 67), (14, 10, 8))
    sp = rng.rand(16, 16)
    d[sp > 0.85, :3] = (100, 70, 48)
    d[sp < 0.07, :3] = (160, 120, 88)
    add_tex("dirt", _clip(d))
    side = d.copy()
    for x in range(16):
        h = 3 + (rng.rand() < 0.5) + (rng.rand() < 0.3) * 2
        side[:h, x, :3] = g[:h, x, :3] * 0.94
        if rng.rand() < 0.4:
            side[h, x, :3] = g[h % 16, x, :3] * 0.8
    add_tex("grass_side", _clip(side))
    s = _noise16(3, (126, 126, 126), (12, 12, 12))
    sp = rng.rand(16, 16)
    s[sp > 0.88, :3] = (98, 98, 98)
    s[sp < 0.06, :3] = (150, 150, 150)
    add_tex("stone", _clip(s))
    sa = _noise16(4, (219, 207, 160), (8, 8, 8))
    sa[rng.rand(16, 16) > 0.9, :3] = (196, 184, 136)
    add_tex("sand", _clip(sa))
    gr = _noise16(5, (132, 126, 124), (30, 30, 30))
    add_tex("gravel", _clip(gr))
    cl = _noise16(6, (160, 166, 178), (6, 6, 6))
    add_tex("clay", _clip(cl))
    # tronco de roble
    lg = np.zeros((16, 16, 4))
    for x in range(16):
        c = (109, 85, 50) if x % 4 in (0, 1) else (88, 68, 40)
        if x % 7 == 3:
            c = (76, 58, 34)
        lg[:, x, :3] = c
    lg[..., :3] += (rng.rand(16, 16, 1) - 0.5) * 16
    lg[..., 3] = 255
    add_tex("log", _clip(lg))
    lt = np.zeros((16, 16, 4))
    yy, xx = np.mgrid[0:16, 0:16]
    r = np.maximum(abs(xx - 7.5), abs(yy - 7.5))
    lt[..., 0] = np.where(r > 6.5, 109, np.where(r.astype(int) % 2 == 0, 182, 152))
    lt[..., 1] = np.where(r > 6.5, 85, np.where(r.astype(int) % 2 == 0, 148, 122))
    lt[..., 2] = np.where(r > 6.5, 50, np.where(r.astype(int) % 2 == 0, 92, 74))
    lt[..., 3] = 255
    add_tex("log_top", _clip(lt))
    # hojas (con agujeros)
    lv = _noise16(8, (66, 138, 40), (20, 26, 14))
    hole = rng.rand(16, 16)
    lv[hole > 0.82, 3] = 0
    lv[hole < 0.18, :3] *= 0.7
    add_tex("leaves", _clip(lv))
    lvd = lv.copy()
    lvd[..., :3] *= (0.78, 0.86, 0.8)
    add_tex("leaves_dark", _clip(lvd))
    # agua (se anima moviendo la t)
    w = np.zeros((32, 16, 4))
    yy, xx = np.mgrid[0:32, 0:16]
    wave = np.sin(xx * 0.8 + yy * 0.4) + np.sin(yy * 0.9 - xx * 0.3)
    w[..., 0] = 44 + wave * 6
    w[..., 1] = 92 + wave * 10
    w[..., 2] = 210 + wave * 12
    w[..., 3] = 170
    add_tex("water", _clip(w))
    # pastito y flores (cruzadas)
    tg = np.zeros((16, 16, 4))
    for i in range(9):
        x = int(rng.randint(1, 15))
        h = int(rng.randint(5, 14))
        lean = rng.uniform(-0.25, 0.25)
        for k in range(h):
            xi = int(round(x + lean * k))
            if 0 <= xi < 16:
                tg[15 - k, xi] = (88 + 3 * k, 150 + 3 * k, 60, 255)
    add_tex("tallgrass", _clip(tg))
    for name, petal in (("poppy", (206, 34, 36)), ("dandelion", (250, 220, 50)), ("cornflower", (80, 110, 230))):
        fl = np.zeros((16, 16, 4))
        fl[8:16, 7] = (58, 120, 36, 255)
        fl[11, 6] = fl[12, 8] = (58, 120, 36, 255)
        for (dx, dy) in ((0, -1), (-1, 0), (1, 0), (0, 1), (0, 0)):
            fl[6 + dy, 7 + dx] = petal + (255,)
        fl[6, 7] = (250, 230, 120, 255) if name != "dandelion" else (240, 160, 30, 255)
        add_tex(name, _clip(fl))
    kp = np.zeros((16, 16, 4))
    for y in range(16):
        x = int(7 + 2 * math.sin(y * 0.6))
        kp[y, x:x + 2] = (70, 120, 40, 255)
        if y % 4 == 1:
            kp[y, x - 2:x] = (90, 140, 50, 255)
        if y % 4 == 3:
            kp[y, x + 2:x + 4] = (90, 140, 50, 255)
    add_tex("kelp", _clip(kp))
    sg = np.zeros((16, 16, 4))
    for i in range(6):
        x = int(rng.randint(1, 15))
        for k in range(int(rng.randint(6, 15))):
            sg[15 - k, min(15, max(0, x + int(math.sin(k * 0.5 + i) * 1.2)))] = (60, 140, 60, 255)
    add_tex("seagrass", _clip(sg))
    # nube: blanca (el alpha va en la cara)
    add_tex("cloud", np.full((16, 16, 4), (255, 255, 255, 255), dtype=np.uint8))
    add_tex("white", np.full((1, 1, 4), 255, dtype=np.uint8))


# ================================================================ lotes de caras
class Quads:
    """Caras planas: p0, u, v (mundo); textura; (s0, t0, s1, t1) texeles en p0 / p0+u / p0+v; tinte; modo."""

    def __init__(self):
        self.p0, self.u, self.v, self.tex, self.st, self.tint, self.wrap, self.cull = [], [], [], [], [], [], [], []

    def add(self, p0, u, v, tex, st, tint=(1, 1, 1), wrap=True, cull=True):
        self.p0.append(p0)
        self.u.append(u)
        self.v.append(v)
        self.tex.append(TEX_ID[tex] if isinstance(tex, str) else tex)
        self.st.append(st)
        self.tint.append(tint)
        self.wrap.append(wrap)
        self.cull.append(cull)

    def extend(self, other):
        for k in ("p0", "u", "v", "tex", "st", "tint", "wrap", "cull"):
            getattr(self, k).extend(getattr(other, k))

    def arrays(self):
        if not self.p0:
            return None
        return {"p0": np.array(self.p0, dtype=np.float64), "u": np.array(self.u, dtype=np.float64),
                "v": np.array(self.v, dtype=np.float64), "tex": np.array(self.tex, dtype=np.int32),
                "st": np.array(self.st, dtype=np.float64), "tint": np.array(self.tint, dtype=np.float64),
                "wrap": np.array(self.wrap, dtype=bool), "cull": np.array(self.cull, dtype=bool)}


def face_shade(n):
    """Sombreado por cara de Minecraft: arriba 1, abajo 0.5, norte/sur 0.8, este/oeste 0.6 (mezclado si gira)."""
    nx, ny, nz = n[..., 0], n[..., 1], n[..., 2]
    return nx * nx * 0.6 + ny * ny * np.where(ny > 0, 1.0, 0.5) + nz * nz * 0.8


# ================================================================ rasterizador
class Raster:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.color = np.zeros((h, w, 3), dtype=np.float32)
        self.depth = np.full((h, w), np.inf, dtype=np.float32)
        self.fog = (0.7, 0.8, 1.0)
        self.fog_range = (60.0, 110.0)
        self.max_dist = 120.0
        self.stats = 0

    # ------------------------------------------------------------ cielo
    def begin(self, cam, sky_top=(120, 167, 255), sky_fog=(192, 216, 255), void=(150, 170, 200), sun=True):
        self.cam = cam
        self.foc = (self.h / 2) / math.tan(math.radians(cam.fov) / 2)
        self.depth.fill(np.inf)
        ys = (np.arange(self.h) + 0.5 - self.h / 2) / self.foc
        # direccion de cada renglon en el centro de la pantalla -> elevacion
        d = cam.f[None, :] - ys[:, None] * cam.u[None, :]
        elev = d[:, 1] / np.linalg.norm(d, axis=1)
        k = np.clip(elev / 0.35, 0, 1) ** 0.8
        top, fogc, vd = (np.array(c, dtype=np.float32) for c in (sky_top, sky_fog, void))
        col = np.where(elev[:, None] >= 0, fogc + (top - fogc) * k[:, None],
                       fogc + (vd - fogc) * np.clip(-elev / 0.2, 0, 1)[:, None])
        self.color[:] = col[:, None, :]
        if sun:
            sd = _n((-0.45, 0.62, 0.64))
            q = cam.R @ sd
            if q[2] > 0:
                sx, sy = self.w / 2 + q[0] / q[2] * self.foc, self.h / 2 - q[1] / q[2] * self.foc
                r = int(self.foc * 0.075)
                x0, x1 = max(0, int(sx - r)), min(self.w, int(sx + r))
                y0, y1 = max(0, int(sy - r)), min(self.h, int(sy + r))
                if x0 < x1 and y0 < y1:
                    self.color[y0:y1, x0:x1] = self.color[y0:y1, x0:x1] * 0.15 + np.array((255, 252, 220)) * 0.85
                rr = int(r * 1.8)
                x0, x1 = max(0, int(sx - rr)), min(self.w, int(sx + rr))
                y0, y1 = max(0, int(sy - rr)), min(self.h, int(sy + rr))
                if x0 < x1 and y0 < y1:
                    self.color[y0:y1, x0:x1] = self.color[y0:y1, x0:x1] * 0.82 + np.array((255, 250, 210)) * 0.18

    # ------------------------------------------------------------ caras
    def draw(self, q, blend=False, fog_now=False, alpha=1.0, flat=False):
        if q is None:
            return
        cam = self.cam
        p0, u, v = q["p0"], q["u"], q["v"]
        n = np.cross(u, v)
        nl = np.linalg.norm(n, axis=1, keepdims=True)
        nl[nl == 0] = 1
        n = n / nl
        rel = cam.pos[None, :] - p0
        facing = np.einsum("ij,ij->i", n, rel)
        keep = (~q["cull"]) | (facing > 0)
        c = p0 + u / 2 + v / 2
        rad = (np.linalg.norm(u, axis=1) + np.linalg.norm(v, axis=1)) / 2
        keep &= np.linalg.norm(c - cam.pos[None, :], axis=1) - rad < self.max_dist
        if not keep.any():
            return
        idx = np.nonzero(keep)[0]
        corners = np.stack([p0[idx], p0[idx] + u[idx], p0[idx] + u[idx] + v[idx], p0[idx] + v[idx]], axis=1)
        cc = (corners - cam.pos[None, None, :]) @ cam.R.T          # (N, 4, 3)
        x, y, z = cc[..., 0], cc[..., 1], cc[..., 2]
        th = self.w / 2 / self.foc * 1.02
        tv = self.h / 2 / self.foc * 1.02
        out = ((z < NEAR).all(1) | (x > z * th).all(1) | (-x > z * th).all(1) | (y > z * tv).all(1)
               | (-y > z * tv).all(1))
        idx2 = np.nonzero(~out)[0]
        if not len(idx2):
            return
        shade = np.ones(len(idx)) if flat else face_shade(np.where(facing[idx][:, None] >= 0, 1, -1) * n[idx])
        st = q["st"][idx]
        tint = q["tint"][idx] * shade[:, None]
        tex = q["tex"][idx]
        wrap = q["wrap"][idx]
        order = idx2
        if blend:   # de atras para adelante
            order = idx2[np.argsort(-cc[idx2, :, 2].mean(1))]
        for i in order:
            s0, t0, s1, t1 = st[i]
            uvs = ((s0, t0), (s1, t0), (s1, t1), (s0, t1))
            poly = [(cc[i, k, 0], cc[i, k, 1], cc[i, k, 2], uvs[k][0], uvs[k][1]) for k in range(4)]
            if (cc[i, :, 2] < NEAR).any():
                poly = _clip_near(poly)
                if len(poly) < 3:
                    continue
            self._poly(poly, tex[i], tint[i], wrap[i], (min(s0, s1), min(t0, t1), max(s0, s1), max(t0, t1)),
                       blend, fog_now, alpha)

    def _poly(self, poly, tid, tint, wrap, rect, blend, fog_now, alpha):
        tex = TEX[tid]
        W, H, F = self.w, self.h, self.foc
        hw, hh = W / 2, H / 2
        P = []
        for (x, y, z, s, t) in poly:
            iz = 1.0 / z
            P.append((hw + x * iz * F, hh - y * iz * F, iz, s * iz, t * iz))
        xs = [p[0] for p in P]
        ys = [p[1] for p in P]
        x0, x1 = max(0, int(math.floor(min(xs)))), min(W - 1, int(math.ceil(max(xs))))
        y0, y1 = max(0, int(math.floor(min(ys)))), min(H - 1, int(math.ceil(max(ys))))
        if x0 > x1 or y0 > y1:
            return
        # area con signo
        area = 0.0
        m = len(P)
        for k in range(m):
            a, b = P[k], P[(k + 1) % m]
            area += a[0] * b[1] - b[0] * a[1]
        if abs(area) < 1e-9:
            return
        sg = 1.0 if area > 0 else -1.0
        # gradientes afines de (iz, s*iz, t*iz) con el triangulo mas grande
        best, tri = 0, None
        for k in range(1, m - 1):
            a, b, c = P[0], P[k], P[k + 1]
            ar = abs((b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]))
            if ar > best:
                best, tri = ar, (a, b, c)
        if best < 1e-9:
            return
        a, b, c = tri
        det = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])
        grads = []
        for j in (2, 3, 4):
            dx = ((b[j] - a[j]) * (c[1] - a[1]) - (c[j] - a[j]) * (b[1] - a[1])) / det
            dy = ((c[j] - a[j]) * (b[0] - a[0]) - (b[j] - a[j]) * (c[0] - a[0])) / det
            grads.append((dx, dy, a[j] - dx * a[0] - dy * a[1]))
        X = np.arange(x0, x1 + 1, dtype=np.float32) + 0.5
        Y = np.arange(y0, y1 + 1, dtype=np.float32)[:, None] + 0.5
        mask = None
        for k in range(m):
            pa, pb = P[k], P[(k + 1) % m]
            e = ((X - pa[0]) * (pb[1] - pa[1]) - (Y - pa[1]) * (pb[0] - pa[0])) * (-sg)
            mk = e >= -1e-4 * (abs(pb[1] - pa[1]) + abs(pb[0] - pa[0]) + 1)
            mask = mk if mask is None else (mask & mk)
        gx, gy, g0 = grads[0]
        iz = gx * X + gy * Y + g0
        z = 1.0 / np.maximum(iz, 1e-6)
        dsl = self.depth[y0:y1 + 1, x0:x1 + 1]
        mask &= z < dsl
        if not mask.any():
            return
        ry, rx = np.nonzero(mask)
        izm = iz[ry, rx]
        fx = rx.astype(np.float32) + (x0 + 0.5)
        fy = ry.astype(np.float32) + (y0 + 0.5)
        s = (grads[1][0] * fx + grads[1][1] * fy + grads[1][2]) / izm
        t = (grads[2][0] * fx + grads[2][1] * fy + grads[2][2]) / izm
        th_, tw_ = tex.shape[0], tex.shape[1]
        if wrap:
            # nivel de mipmap por pixel: cuantos texeles cubre un pixel (derivadas de s y t en pantalla)
            dsx = (grads[1][0] - s * gx) / izm
            dsy = (grads[1][1] - s * gy) / izm
            dtx = (grads[2][0] - t * gx) / izm
            dty = (grads[2][1] - t * gy) / izm
            rho = np.maximum(np.abs(dsx) + np.abs(dtx), np.abs(dsy) + np.abs(dty))
            flat, offs, ws, hs = mips(tid)
            lod = np.clip(np.floor(np.log2(np.maximum(rho, 1e-6)) + 0.3), 0, len(offs) - 1).astype(np.int32)
            sc = np.float32(0.5) ** lod
            wl, hl = ws[lod], hs[lod]
            si = np.floor(s * sc).astype(np.int32) % wl
            ti = np.floor(t * sc).astype(np.int32) % hl
            texel = flat[offs[lod] + ti * wl + si]
        else:
            si = np.clip(np.floor(s).astype(np.int32), int(rect[0]), max(int(rect[0]), int(math.ceil(rect[2])) - 1))
            ti = np.clip(np.floor(t).astype(np.int32), int(rect[1]), max(int(rect[1]), int(math.ceil(rect[3])) - 1))
            si = np.clip(si, 0, tw_ - 1)
            ti = np.clip(ti, 0, th_ - 1)
            texel = tex[ti, si]
        al = texel[:, 3]
        ok = al > 40
        if not ok.all():
            ry, rx, texel, al, izm = ry[ok], rx[ok], texel[ok], al[ok], izm[ok]
        if not len(ry):
            return
        col = texel[:, :3].astype(np.float32) * np.asarray(tint, dtype=np.float32)
        zz = 1.0 / izm
        if fog_now:
            f0, f1 = self.fog_range
            k = np.clip((zz - f0) / (f1 - f0), 0, 1)[:, None]
            col = col * (1 - k) + np.asarray(self.fog, dtype=np.float32) * 255 * k
        cs = self.color[y0:y1 + 1, x0:x1 + 1]
        if blend:
            a = (al.astype(np.float32) / 255 * alpha)[:, None]
            cs[ry, rx] = cs[ry, rx] * (1 - a) + col * a
        else:
            cs[ry, rx] = col
            dsl[ry, rx] = zz
        self.stats += 1

    # ------------------------------------------------------------ niebla y sprites
    def apply_fog(self):
        f0, f1 = self.fog_range
        k = np.clip((self.depth - f0) / (f1 - f0), 0, 1)
        k[~np.isfinite(self.depth)] = 0
        k = k[..., None]
        self.color[:] = self.color * (1 - k) + np.asarray(self.fog, dtype=np.float32) * 255 * k

    def project(self, p):
        q = self.cam.R @ (np.asarray(p, dtype=np.float64) - self.cam.pos)
        if q[2] < NEAR:
            return None
        return (self.w / 2 + q[0] / q[2] * self.foc, self.h / 2 - q[1] / q[2] * self.foc, q[2])

    def billboard(self, pos, size, img, alpha=1.0, depth_test=True):
        """img: (h, w, 4) uint8. size: alto en bloques."""
        pr = self.project(pos)
        if pr is None:
            return
        sx, sy, z = pr
        hpx = size / z * self.foc
        if hpx < 0.6:
            return
        wpx = hpx * img.shape[1] / img.shape[0]
        x0, x1 = int(round(sx - wpx / 2)), int(round(sx + wpx / 2))
        y0, y1 = int(round(sy - hpx / 2)), int(round(sy + hpx / 2))
        if x1 <= x0:
            x1 = x0 + 1
        if y1 <= y0:
            y1 = y0 + 1
        cx0, cx1, cy0, cy1 = max(0, x0), min(self.w, x1), max(0, y0), min(self.h, y1)
        if cx0 >= cx1 or cy0 >= cy1:
            return
        ix = ((np.arange(cx0, cx1) - x0 + 0.5) / (x1 - x0) * img.shape[1]).astype(np.int32).clip(0, img.shape[1] - 1)
        iy = ((np.arange(cy0, cy1) - y0 + 0.5) / (y1 - y0) * img.shape[0]).astype(np.int32).clip(0, img.shape[0] - 1)
        sub = img[iy[:, None], ix[None, :]]
        a = sub[..., 3:4].astype(np.float32) / 255 * alpha
        if depth_test:
            a = a * (self.depth[cy0:cy1, cx0:cx1, None] > z)
        cs = self.color[cy0:cy1, cx0:cx1]
        cs[:] = cs * (1 - a) + sub[..., :3].astype(np.float32) * a

    def image(self):
        return np.clip(self.color, 0, 255).astype(np.uint8)


def _clip_near(poly):
    out = []
    m = len(poly)
    for k in range(m):
        a, b = poly[k], poly[(k + 1) % m]
        ain, bin_ = a[2] >= NEAR, b[2] >= NEAR
        if ain:
            out.append(a)
        if ain != bin_:
            t = (NEAR - a[2]) / (b[2] - a[2])
            out.append(tuple(a[i] + (b[i] - a[i]) * t for i in range(5)))
    return out


# ================================================================ cajas
FACES = {  # (p0, u, v) de cada cara de la caja [0,1]^3: u = derecha y v = arriba vistas de afuera (u x v = afuera)
    "up": ((0, 1, 1), (1, 0, 0), (0, 0, -1)),
    "down": ((0, 0, 0), (1, 0, 0), (0, 0, 1)),
    "north": ((1, 0, 0), (-1, 0, 0), (0, 1, 0)),    # -Z
    "south": ((0, 0, 1), (1, 0, 0), (0, 1, 0)),     # +Z
    "west": ((0, 0, 0), (0, 0, 1), (0, 1, 0)),      # -X
    "east": ((1, 0, 1), (0, 0, -1), (0, 1, 0)),     # +X
}
DIRS = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}


def box_face(q, lo, hi, name, tex, tint=(1, 1, 1), cull=True, tile=16.0):
    lo, hi = np.asarray(lo, dtype=np.float64), np.asarray(hi, dtype=np.float64)
    size = hi - lo
    p0, u, v = (np.asarray(a, float) for a in FACES[name])
    P0 = lo + p0 * size
    U, V = u * size, v * size
    lu, lv = np.abs(U).sum(), np.abs(V).sum()
    if name in ("up", "down"):
        st = (0, 0, lu * tile, lv * tile)
    else:   # la t arranca en 0 arriba (el borde de pasto del costado queda arriba)
        st = (0, lv * tile, lu * tile, 0)
    q.add(P0, U, V, tex, st, tint, True, cull)


def box(q, lo, hi, tex, tint=(1, 1, 1), cull=True, tile=16.0, skip=()):
    """Caja alineada de lo a hi (bloques) con textura repetida cada bloque. tex: nombre o dict cara->nombre."""
    for name in FACES:
        if name in skip:
            continue
        t = tex.get(name, tex.get("side")) if isinstance(tex, dict) else tex
        box_face(q, lo, hi, name, t, tint, cull, tile)


# ================================================================ mundo
def value_noise(w, h, cell, seed):
    rng = np.random.RandomState(seed)
    gw, gh = w // cell + 2, h // cell + 2
    g = rng.rand(gh, gw)
    ys, xs = np.mgrid[0:h, 0:w] / float(cell)
    x0, y0 = xs.astype(int), ys.astype(int)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = g[y0, x0], g[y0, x0 + 1]
    c, d = g[y0 + 1, x0], g[y0 + 1, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


class World:
    """Terreno por columnas: altura, bloque de arriba, arboles, plantas y agua. x, z de 0 a size."""

    def __init__(self, size=256, sea=62, seed=3, shape=None):
        self.size, self.sea = size, sea
        n = (value_noise(size, size, 48, seed) * 0.62 + value_noise(size, size, 18, seed + 1) * 0.28
             + value_noise(size, size, 7, seed + 2) * 0.10)
        h = sea + 1 + (n - 0.42) * 22
        if shape is not None:
            h = shape(h, np.mgrid[0:size, 0:size][1], np.mgrid[0:size, 0:size][0])
        self.h = np.round(h).astype(np.int32)
        self.top = np.full((size, size), "grass", dtype=object)
        self.top[self.h <= sea + 1] = "sand"
        self.top[self.h < sea - 3] = "gravel"
        self.top[self.h < sea - 7] = "clay"
        self.plants = []    # (x, y, z, tex)
        self.trees = []     # (x, y, z, alto)
        self.solid = {}     # (x, y, z) -> tex (bloques extra: pista, nido, etc.)
        self.rng = np.random.RandomState(seed + 10)

    def height(self, x, z):
        xi, zi = int(math.floor(x)), int(math.floor(z))
        xi, zi = min(max(xi, 0), self.size - 1), min(max(zi, 0), self.size - 1)
        return int(self.h[zi, xi]) + 1   # y del piso (arriba del bloque)

    def smooth_height(self, x, z):
        """Altura del piso suavizada (para que los dinos no salten de golpe al subir un escalon)."""
        x0, z0 = math.floor(x - 0.5), math.floor(z - 0.5)
        fx, fz = x - 0.5 - x0, z - 0.5 - z0
        a, b = self.height(x0, z0), self.height(x0 + 1, z0)
        c, d = self.height(x0, z0 + 1), self.height(x0 + 1, z0 + 1)
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz

    def populate(self, trees=0.012, plants=0.12, keep_clear=()):
        rng = self.rng
        S = self.size
        Z, X = np.mgrid[0:S, 0:S]
        clear = np.zeros((S, S), dtype=bool)
        for cx, cz, r in keep_clear:
            clear |= (X - cx) ** 2 + (Z - cz) ** 2 < r * r
        for z in range(2, S - 2):
            for x in range(2, S - 2):
                if self.top[z, x] != "grass":
                    if self.top[z, x] in ("gravel", "clay", "sand") and self.h[z, x] < self.sea - 1 and rng.rand() < 0.05:
                        self.plants.append((x, self.h[z, x] + 1, z, "kelp" if rng.rand() < 0.5 else "seagrass"))
                    continue
                if clear[z, x]:
                    if rng.rand() < plants * 0.5:
                        self.plants.append((x, self.h[z, x] + 1, z, "tallgrass"))
                    continue
                r = rng.rand()
                if r < trees and all(abs(x - tx) + abs(z - tz) > 5 for tx, _, tz, _ in self.trees[-40:]):
                    self.trees.append((x, self.h[z, x] + 1, z, int(rng.randint(4, 7))))
                elif r < trees + plants:
                    k = rng.rand()
                    tex = "tallgrass" if k < 0.85 else ("poppy" if k < 0.92 else ("dandelion" if k < 0.97 else "cornflower"))
                    self.plants.append((x, self.h[z, x] + 1, z, tex))

    # ------------------------------------------------------------ malla
    def mesh(self):
        """Caras del terreno juntando las de arriba iguales en rectangulos (greedy) y los costados en tiras."""
        q = Quads()
        h, top, S = self.h, self.top, self.size
        done = np.zeros((S, S), dtype=bool)
        tops = {"grass": "grass_top", "sand": "sand", "gravel": "gravel", "clay": "clay"}
        for z in range(S):
            for x in range(S):
                if done[z, x]:
                    continue
                hh, tt = h[z, x], top[z, x]
                x2 = x
                while x2 + 1 < S and not done[z, x2 + 1] and h[z, x2 + 1] == hh and top[z, x2 + 1] == tt and x2 - x < 15:
                    x2 += 1
                z2 = z
                while z2 + 1 < S and z2 - z < 15 and all(not done[z2 + 1, k] and h[z2 + 1, k] == hh and top[z2 + 1, k] == tt
                                                         for k in range(x, x2 + 1)):
                    z2 += 1
                done[z:z2 + 1, x:x2 + 1] = True
                y = hh + 1
                q.add((x, y, z2 + 1), (x2 + 1 - x, 0, 0), (0, 0, -(z2 + 1 - z)), tops[tt],
                      (0, 0, (x2 + 1 - x) * 16, (z2 + 1 - z) * 16))
        # costados: para cada borde entre columnas, del piso del vecino al propio
        for z in range(S):
            for x in range(S):
                hi = h[z, x]
                for (dx, dz), name in DIRS.items():
                    nx, nz = x + dx, z + dz
                    if not (0 <= nx < S and 0 <= nz < S):
                        continue
                    lo = h[nz, nx]
                    if hi <= lo:
                        continue
                    t = top[z, x]
                    box_face(q, (x, hi, z), (x + 1, hi + 1, z + 1), name, "grass_side" if t == "grass" else tops[t])
                    if hi - lo > 1:
                        under = ("dirt" if t == "grass" else tops[t]) if hi - lo < 4 else "stone"
                        box_face(q, (x, lo + 1, z), (x + 1, hi, z + 1), name, under)
        for (x, y, z, ht) in self.trees:
            box(q, (x, y, z), (x + 1, y + ht, z + 1), {"up": "log_top", "down": "log_top", "side": "log"})
            leaves = Quads()
            box(leaves, (x - 2, y + ht - 3, z - 2), (x + 3, y + ht - 1, z + 3), "leaves", cull=False)
            box(leaves, (x - 1, y + ht - 1, z - 1), (x + 2, y + ht + 1, z + 2), "leaves", cull=False)
            q.extend(leaves)
        for (x, y, z), tex in self.solid.items():
            box(q, (x, y, z), (x + 1, y + 1, z + 1), tex)
        self.terrain = q.arrays()
        # plantas: dos caras cruzadas, sin culling
        p = Quads()
        for (x, y, z, tex) in self.plants:
            hgt = 1.0
            if tex == "kelp":
                hgt = float(self.rng.randint(2, max(3, self.sea - y + 1)))
            for (a, b) in (((0.15, 0.85), (0.85, 0.15)), ((0.15, 0.15), (0.85, 0.85))):
                p.add((x + a[0], y + hgt, z + a[1]), (b[0] - a[0], 0, b[1] - a[1]), (0, -hgt, 0), tex,
                      (0, 0, 16, 16 * hgt), cull=False)
        self.plant_q = p.arrays()
        # agua: arriba de cada columna mas baja que el mar
        w = Quads()
        mask = self.h < self.sea
        done = np.zeros_like(mask)
        for z in range(S):
            for x in range(S):
                if not mask[z, x] or done[z, x]:
                    continue
                x2 = x
                while x2 + 1 < S and mask[z, x2 + 1] and not done[z, x2 + 1] and x2 - x < 31:
                    x2 += 1
                z2 = z
                while z2 + 1 < S and z2 - z < 31 and all(mask[z2 + 1, k] and not done[z2 + 1, k] for k in range(x, x2 + 1)):
                    z2 += 1
                done[z:z2 + 1, x:x2 + 1] = True
                y = self.sea + 0.875
                w.add((x, y, z2 + 1), (x2 + 1 - x, 0, 0), (0, 0, -(z2 + 1 - z)), "water",
                      (0, 0, (x2 + 1 - x) * 16, (z2 + 1 - z) * 16), cull=False)
        self.water_q = w.arrays()

    def clouds(self, t, y=110.0, seed=4):
        n = value_noise(64, 64, 5, seed)
        q = Quads()
        off = t * 0.6
        for zi in range(64):
            for xi in range(64):
                if n[zi, xi] > 0.62:
                    x = xi * 12 - 128 + off
                    z = zi * 12 - 128
                    q.add((x, y, z + 12), (12, 0, 0), (0, 0, -12), "cloud", (0, 0, 16, 16), cull=False)
        return q.arrays()


# ================================================================ modelos (dinos, jugador, items)
_ATLAS = {}


def atlas_id(pix):
    key = id(pix)
    if key not in _ATLAS:
        arr = np.array(pix, dtype=np.uint8)
        if arr.shape[-1] == 3:
            arr = np.concatenate([arr, np.full(arr.shape[:2] + (1,), 255, np.uint8)], axis=2)
        _ATLAS[key] = add_tex("atlas%d" % key, arr)
    return _ATLAS[key]


def rig_quads(q, parts, world, tint=(1, 1, 1)):
    """parts: [(set, mats)] como preview.render. world(p) -> punto en el mundo (de px de modelo)."""
    for (rig, pix, size, models), mats in parts:
        tid = atlas_id(pix)
        ku, kv = size[0] / 16.0, size[1] / 16.0
        for name, elements in models.items():
            if name not in mats:
                continue
            joint = rig.bone(name).joint
            m = mats[name][0]
            if abs(m[0][0]) + abs(m[1][1]) + abs(m[2][2]) + abs(m[0][1]) + abs(m[1][0]) < 1e-4:
                continue   # hueso oculto (escala 0)
            for el in elements:
                f, t = el["from"], el["to"]
                rot = el.get("rotation")
                for fname, nrm, fw, fh, point in faces_of(f, t):
                    if fw <= 0 or fh <= 0:
                        continue
                    uv = el["faces"][fname]["uv"]

                    def wp(uu, vv):
                        p = point(uu, vv)
                        if rot:
                            p = rotate(p, rot["axis"], rot["angle"], rot["origin"])
                        p = to_local(p, joint)
                        p = (p[0] - joint[0], p[1] - joint[1], p[2] - joint[2])
                        r = [m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3)]
                        return world(r)

                    a, b, c = wp(0, 0), wp(fw, 0), wp(0, fh)
                    U = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
                    V = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
                    # faces_of: u x v apunta para adentro -> la damos vuelta (v hacia el otro lado) para el culling
                    s0, t0 = uv[0] * ku, uv[1] * kv
                    s1, t1 = uv[2] * ku, uv[3] * kv
                    q.add((a[0] + V[0], a[1] + V[1], a[2] + V[2]), U, (-V[0], -V[1], -V[2]), tid,
                          (s0, t1, s1, t0), tint, False, True)


def placer(pos, yaw, scale=1.0, pitch=0.0, roll=0.0):
    """px de modelo -> mundo: escala /16, giro (yaw como Minecraft: el frente +Z del modelo mira a donde va)."""
    k = scale / 16.0
    y = math.radians(yaw)
    cy, sy = math.cos(y), math.sin(y)
    p_, r_ = math.radians(pitch), math.radians(roll)
    cp, sp = math.cos(p_), math.sin(p_)
    cr, sr = math.cos(r_), math.sin(r_)
    px, py, pz = pos

    def f(p):
        x, yy, z = p[0] * k, p[1] * k, p[2] * k
        if roll:
            x, yy = x * cr - yy * sr, x * sr + yy * cr
        if pitch:   # pitch positivo = nariz abajo
            yy, z = yy * cp - z * sp, yy * sp + z * cp
        # yaw: rotar alrededor de Y para que +Z mire a (-sin yaw, cos yaw)
        return (px + x * cy - z * sy, py + yy, pz + x * sy + z * cy)
    return f

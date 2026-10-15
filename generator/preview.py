"""
Renderiza previews del modelo (sin Minecraft) para revisar forma y textura.

Uso:  python generator/preview.py   -> genera preview.png en la carpeta raiz
"""
import math
import os

from build import build, png_bytes, rotate, to_local, faces_of, ROOT

VIEW_W, VIEW_H, PPX = 460, 330, 6.0
BG = (58, 66, 80)


# ---------------------------------------------------------------- matrices 4x4
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_t(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def mat_s(s):
    return [[s, 0, 0, 0], [0, s, 0, 0], [0, 0, s, 0], [0, 0, 0, 1]]


def mat_r(rx=0.0, ry=0.0, rz=0.0):
    """Rotacion en grados, orden Y * X * Z (igual que el plugin)."""
    def rot(axis, a):
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        if axis == "x":
            return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]
        if axis == "y":
            return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]
        return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    return mat_mul(mat_mul(rot("y", ry), rot("x", rx)), rot("z", rz))


def apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def bone_matrices(rig, pose, root, parents=None):
    """pose: {hueso: (rx, ry, rz, dy)} -> {hueso: (matriz, joint)} (igual que el plugin)."""
    out = dict(parents or {})
    for b in rig.bones:
        rx, ry, rz, dy, *sc = pose.get(b.name, (0, 0, 0, 0))
        if b.parent:
            pm, pj = out[b.parent]
            base = mat_mul(pm, mat_t(b.joint[0] - pj[0], b.joint[1] - pj[1], b.joint[2] - pj[2]))
        else:
            base = mat_mul(root, mat_t(*b.joint))
        m = mat_mul(mat_mul(base, mat_t(0, dy, 0)), mat_r(rx, ry, rz))
        out[b.name] = (mat_mul(m, mat_s(sc[0])) if sc else m, b.joint)
    return out


# ---------------------------------------------------------------- render
def render(parts, yaw, pitch, ppx=PPX, cy_frac=0.62):
    """parts: lista de (set, matrices)."""
    img = [[BG] * VIEW_W for _ in range(VIEW_H)]
    zbuf = [[1e9] * VIEW_W for _ in range(VIEW_H)]
    view = mat_mul(mat_r(rx=-pitch), mat_r(ry=yaw))  # pitch > 0 = camara arriba
    cx, cy = VIEW_W / 2, VIEW_H * cy_frac

    for (rig, pix, size, models), mats in parts:
        k = size / 16.0
        for name, elements in models.items():
            joint = rig.bone(name).joint
            wm = mat_mul(view, mats[name][0])
            for el in elements:
                f, t = el["from"], el["to"]
                rot = el.get("rotation")
                for fname, nrm, fw, fh, point in faces_of(f, t):
                    uv = el["faces"][fname]["uv"]
                    W, H = round((uv[2] - uv[0]) * k), round((uv[3] - uv[1]) * k)
                    ox, oy = round(uv[0] * k), round(uv[1] * k)

                    def to_screen(u, v):
                        m = point(u, v)
                        if rot:
                            m = rotate(m, rot["axis"], rot["angle"], rot["origin"])
                        p = to_local(m, joint)
                        q = apply(wm, (p[0] - joint[0], p[1] - joint[1], p[2] - joint[2]))
                        return (cx - q[0] * ppx, cy - q[1] * ppx, q[2])

                    p0, pu, pv = to_screen(0, 0), to_screen(fw, 0), to_screen(0, fh)
                    ux, uy = pu[0] - p0[0], pu[1] - p0[1]
                    vx, vy = pv[0] - p0[0], pv[1] - p0[1]
                    det = ux * vy - uy * vx
                    if det < 1e-6:   # back-face culling, como el render de items
                        continue
                    shade = {"up": 1.0, "down": 0.5}.get(fname, 0.8 if fname in ("north", "south") else 0.65)
                    xs = [p0[0], pu[0], pv[0], pu[0] + vx]
                    ys = [p0[1], pu[1], pv[1], pu[1] + vy]
                    x0, x1 = max(0, int(min(xs))), min(VIEW_W - 1, int(max(xs)) + 1)
                    y0, y1 = max(0, int(min(ys))), min(VIEW_H - 1, int(max(ys)) + 1)
                    dzu, dzv = pu[2] - p0[2], pv[2] - p0[2]
                    for py in range(y0, y1 + 1):
                        for px in range(x0, x1 + 1):
                            dx, dy = px + 0.5 - p0[0], py + 0.5 - p0[1]
                            a = (dx * vy - dy * vx) / det
                            b = (ux * dy - uy * dx) / det
                            if a < 0 or a > 1 or b < 0 or b > 1:
                                continue
                            z = p0[2] + a * dzu + b * dzv
                            if z >= zbuf[py][px]:
                                continue
                            c = pix[oy + min(H - 1, int(b * H))][ox + min(W - 1, int(a * W))]
                            zbuf[py][px] = z
                            img[py][px] = (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade))
    return img


def sheet(views, cols):
    rows = (len(views) + cols - 1) // cols
    out = [[(30, 34, 42)] * (VIEW_W * cols + cols + 1) for _ in range(VIEW_H * rows + rows + 1)]
    for idx, img in enumerate(views):
        r, c = divmod(idx, cols)
        for y in range(VIEW_H):
            row = out[1 + r * (VIEW_H + 1) + y]
            for x in range(VIEW_W):
                row[1 + c * (VIEW_W + 1) + x] = img[y][x]
    return [[(p[0], p[1], p[2], 255) for p in row] for row in out]


POSES = {
    "reposo": {},
    "caminar": {"leg_r": (-30, 0, 0, 0), "leg_l": (30, 0, 0, 0), "tail1": (0, 10, 0, 0), "tail2": (0, 15, 0, 0),
                "leg_fr": (-25, 0, 0, 0), "leg_bl": (-25, 0, 0, 0), "leg_fl": (25, 0, 0, 0), "leg_br": (25, 0, 0, 0)},
    "ataque": {"head": (25, 0, 0, 0), "jaw": (35, 0, 0, 0), "tail1": (-8, 0, 0, 0), "tail2": (-10, 0, 0, 0)},
    # gola del dilophosaurus plegada (asi esta cuando esta tranquilo)
    "plegado": {"frill_r": (0, 80, 0, 0, 0.3), "frill_l": (0, -80, 0, 0, 0.3)},
    "escupir": {"head": (-20, 0, 0, 0), "jaw": (30, 0, 0, 0)},
}


def dino(sets, key, pose="reposo", root=None, saddle=False, k=1.0):
    s = sets[key]
    base = mat_mul(root or mat_t(0, 0, 0), mat_s(k))
    mats = bone_matrices(s[0], POSES[pose], base)
    parts = [(s, mats)]
    if saddle:
        sad = sets[key.split("/")[0] + "/saddle"]
        parts.append((sad, bone_matrices(sad[0], {}, None, parents={"body": mats["body"]})))
    return parts


if __name__ == "__main__":
    import sys
    sets = build()
    which = sys.argv[1] if len(sys.argv) > 1 else "todos"
    T, D, C = 0.85, 0.85, 0.9
    views = {
        "raptor": [
            render(dino(sets, "raptor/arena/female"), 120, 12),
            render(dino(sets, "raptor/gris/male", "caminar", saddle=True), 135, 18),
            render(dino(sets, "raptor/arena/baby"), 125, 14, ppx=10, cy_frac=0.7),
        ],
        "triceratops": [
            render(dino(sets, "triceratops/pardo/female", k=T), 120, 12, ppx=3.6, cy_frac=0.72),
            render(dino(sets, "triceratops/oliva/male", k=T), 150, 14, ppx=3.6, cy_frac=0.72),
            render(dino(sets, "triceratops/pizarra/male", "caminar", saddle=True, k=T), 60, 18, ppx=3.6, cy_frac=0.72),
            render(dino(sets, "triceratops/rojizo/female", "ataque", k=T), 95, 6, ppx=3.6, cy_frac=0.72),
            render(dino(sets, "triceratops/pardo/baby"), 130, 14, ppx=8, cy_frac=0.72),
            render(dino(sets, "triceratops/egg", root=mat_t(-8, 0, 0)) + dino(sets, "raptor/egg", root=mat_t(8, 0, 0)),
                   160, 20, ppx=10, cy_frac=0.75),
        ],
        "dilophosaurus": [
            render(dino(sets, "dilophosaurus/amarillo/female", "plegado", k=D), 120, 12, ppx=6.5),
            render(dino(sets, "dilophosaurus/amarillo/male", k=D), 150, 12, ppx=6.5),
            render(dino(sets, "dilophosaurus/verde/male", "escupir", k=D), 100, 6, ppx=6.5),
            render(dino(sets, "dilophosaurus/pardo/female", "plegado", k=D), 60, 15, ppx=6.5),
        ],
        "compsognathus": [
            render(dino(sets, "compsognathus/verde/female", k=C), 120, 12, ppx=16, cy_frac=0.7),
            render(dino(sets, "compsognathus/pardo/male", "caminar", k=C), 140, 18, ppx=16, cy_frac=0.7),
            render(dino(sets, "compsognathus/gris/male", "ataque", k=C), 90, 8, ppx=16, cy_frac=0.7),
        ],
    }
    imgs = [v for k, vs in views.items() if which in ("todos", k) for v in vs]
    out = os.path.join(ROOT, "preview.png" if which == "todos" else f"preview_{which}.png")
    with open(out, "wb") as fh:
        fh.write(png_bytes(sheet(imgs, 3)))
    print("preview ->", out)

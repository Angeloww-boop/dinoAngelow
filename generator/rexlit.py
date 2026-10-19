"""Render iluminado de un modelo (para el logo y el video): luz principal, relleno y borde de color por cara,
en vez del sombreado plano de preview.py."""
import math

from build import faces_of, rotate, to_local
from preview import apply, mat_mul, mat_r


def _norm(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / n for c in v)


def render_lit(parts, yaw, pitch, ppx, canvas, cy_frac=0.5, key=(0.55, 0.75, -0.45), key_col=(255, 244, 228),
               fill=(-0.7, -0.2, -0.4), fill_col=(120, 140, 255), rim=(-0.85, 0.5, 0.0), rim_col=(255, 0, 217),
               ambient=0.42, diffuse=0.78, fill_k=0.22, rim_k=0.75):
    CW, CH = canvas
    img = [[None] * CW for _ in range(CH)]
    zbuf = [[1e9] * CW for _ in range(CH)]
    view = mat_mul(mat_r(rx=-pitch), mat_r(ry=yaw))
    cx, cy = CW / 2, CH * cy_frac
    key, fill, rim = _norm(key), _norm(fill), _norm(rim)
    for (rig, pix, size, models), mats in parts:
        ku, kv = size[0] / 16.0, size[1] / 16.0
        for name, elements in models.items():
            joint = rig.bone(name).joint
            wm = mat_mul(view, mats[name][0])
            for el in elements:
                f, t = el["from"], el["to"]
                rot = el.get("rotation")
                for fname, nrm, fw, fh, point in faces_of(f, t):
                    uv = el["faces"][fname]["uv"]
                    W, H = round((uv[2] - uv[0]) * ku), round((uv[3] - uv[1]) * kv)
                    ox, oy = round(uv[0] * ku), round(uv[1] * kv)

                    def view_pt(u, v):
                        m = point(u, v)
                        if rot:
                            m = rotate(m, rot["axis"], rot["angle"], rot["origin"])
                        p = to_local(m, joint)
                        return apply(wm, (p[0] - joint[0], p[1] - joint[1], p[2] - joint[2]))

                    q0, qu, qv = view_pt(0, 0), view_pt(fw, 0), view_pt(0, fh)
                    p0 = (cx - q0[0] * ppx, cy - q0[1] * ppx, q0[2])
                    pu = (cx - qu[0] * ppx, cy - qu[1] * ppx, qu[2])
                    pv = (cx - qv[0] * ppx, cy - qv[1] * ppx, qv[2])
                    ux, uy = pu[0] - p0[0], pu[1] - p0[1]
                    vx, vy = pv[0] - p0[0], pv[1] - p0[1]
                    det = ux * vy - uy * vx
                    if det < 1e-6:
                        continue
                    a3 = (qu[0] - q0[0], qu[1] - q0[1], qu[2] - q0[2])
                    b3 = (qv[0] - q0[0], qv[1] - q0[1], qv[2] - q0[2])
                    n = _norm((a3[1] * b3[2] - a3[2] * b3[1], a3[2] * b3[0] - a3[0] * b3[2], a3[0] * b3[1] - a3[1] * b3[0]))
                    if n[2] > 0:
                        n = (-n[0], -n[1], -n[2])   # que mire a la camara (la cara es visible)
                    dk = max(0.0, sum(n[i] * key[i] for i in range(3)))
                    df = max(0.0, sum(n[i] * fill[i] for i in range(3)))
                    # contraluz: ilumina las caras que quedan de canto a la camara (contornos), mas del lado de rim
                    edge = (1 - abs(n[2])) ** 1.6
                    side = max(0.0, n[0] * rim[0] + n[1] * rim[1])
                    dr = edge * (0.35 + 0.65 * side)
                    light = [ambient + diffuse * dk * key_col[i] / 255 + fill_k * df * fill_col[i] / 255 for i in range(3)]
                    add = [rim_k * dr * rim_col[i] for i in range(3)]
                    xs = [p0[0], pu[0], pv[0], pu[0] + vx]
                    ys = [p0[1], pu[1], pv[1], pu[1] + vy]
                    x0, x1 = max(0, int(min(xs))), min(CW - 1, int(max(xs)) + 1)
                    y0, y1 = max(0, int(min(ys))), min(CH - 1, int(max(ys)) + 1)
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
                            img[py][px] = tuple(min(255, int(c[i] * light[i] + add[i] * c[i] / 255 * 0.6 + add[i] * 0.25))
                                                for i in range(3))
    return img

"""Dibuja los menus de la Dinopedia como los veria el cliente, para revisarlos sin entrar al juego.

Toma los titulos que arma el plugin (GuiPreview en plugin/src/test, mismo codigo que en el server) y los dibuja
leyendo las fuentes del pack igual que Minecraft: cada letra busca su proveedor (space / bitmap / reference), avanza
"ancho real + 1" y una imagen con ascent A queda con su borde de arriba en y = 6 + 7 - A. Abajo va el menu de cofre de
vanilla (sacado del jar del cliente, solo para la vista previa) y arriba los items de cada casillero.

Uso (necesita Pillow: generator/.venv):
    generator/.venv/Scripts/python generator/gui_preview.py pantallas.json carpeta_salida
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

from font_widths import find_jar

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "resourcepack")
TITLE_X, TITLE_Y = 8, 6
NAMED = {"white": "#FFFFFF", "gray": "#AAAAAA", "dark_gray": "#555555", "gold": "#FFAA00", "yellow": "#FFFF55",
         "black": "#000000"}


class Assets:
    def __init__(self, jar):
        self.jar = zipfile.ZipFile(jar)
        self.fonts = {}
        self.images = {}

    def read(self, ns, path):
        if ns == "dinoearth":
            with open(os.path.join(PACK, "assets", ns, path), "rb") as fh:
                return fh.read()
        return self.jar.read(f"assets/{ns}/{path}")

    def image(self, ns, path):
        key = (ns, path)
        if key not in self.images:
            self.images[key] = Image.open(io.BytesIO(self.read(ns, path))).convert("RGBA")
        return self.images[key]

    def font(self, fid):
        if fid in self.fonts:
            return self.fonts[fid]
        ns, path = fid.split(":")
        data = json.loads(self.read(ns, f"font/{path}.json"))
        glyphs = {}
        for p in data["providers"]:
            t = p["type"]
            if t == "space":
                for ch, adv in p["advances"].items():
                    glyphs.setdefault(ch, ("space", adv))
            elif t == "reference":
                for ch, g in self.font(p["id"]).items():
                    glyphs.setdefault(ch, g)
            elif t == "bitmap":
                fns, fpath = p["file"].split(":")
                img = self.image(fns, "textures/" + fpath)
                rows = p["chars"]
                cw, chh = img.width // len(rows[0]), img.height // len(rows)
                height = p.get("height", 8)
                scale = height / chh
                for r, row in enumerate(rows):
                    for c, ch in enumerate(row):
                        if ch == "\u0000" or ch in glyphs:
                            continue
                        cell = img.crop((c * cw, r * chh, (c + 1) * cw, (r + 1) * chh))
                        actual = 0
                        for x in range(cw - 1, -1, -1):
                            if any(cell.getpixel((x, y))[3] for y in range(chh)):
                                actual = x + 1
                                break
                        glyphs[ch] = ("bitmap", int(0.5 + actual * scale) + 1, cell, p["ascent"], height, scale)
        self.fonts[fid] = glyphs
        return glyphs


def color_of(c, inherited):
    if c is None:
        return inherited
    c = NAMED.get(c, c)
    return tuple(int(c[i:i + 2], 16) for i in (1, 3, 5))


def runs(comp, style):
    """Componente JSON de Adventure -> lista de (texto, fuente, color) con el estilo heredado."""
    if isinstance(comp, str):
        yield comp, style["font"], style["color"]
        return
    if isinstance(comp, list):
        for c in comp:
            yield from runs(c, style)
        return
    st = {"font": comp.get("font", style["font"]), "color": color_of(comp.get("color"), style["color"])}
    if comp.get("text"):
        yield comp["text"], st["font"], st["color"]
    for c in comp.get("extra", []):
        yield from runs(c, st)


def draw_text(canvas, assets, comp, x, y, color=(64, 64, 64), font="minecraft:default"):
    pen = x
    for text, fid, col in runs(comp, {"font": font, "color": color}):
        glyphs = assets.font(fid)
        for ch in text:
            g = glyphs.get(ch)
            if g is None:
                print(f"  falta la letra {ch!r} en {fid}")
                pen += 6
                continue
            if g[0] == "space":
                pen += g[1]
                continue
            _, adv, cell, ascent, height, scale = g
            img = cell if scale == 1 else cell.resize((round(cell.width * scale), round(cell.height * scale)), Image.NEAREST)
            r, gch, b, a = img.split()
            r = r.point(lambda v, k=col[0]: v * k // 255)
            gch = gch.point(lambda v, k=col[1]: v * k // 255)
            b = b.point(lambda v, k=col[2]: v * k // 255)
            img = Image.merge("RGBA", (r, gch, b, a))
            canvas.alpha_composite(img, (pen, y + 7 - ascent))
            pen += adv
    return pen


def item_texture(assets, model_id):
    ns, path = model_id.split(":")
    if path.endswith("/?"):
        base = os.path.join(PACK, "assets", ns, "items", path[:-2])
        first = sorted(f for f in os.listdir(base) if f != "unknown.json")[0]
        path = path[:-1] + first[:-5]
    item = json.loads(assets.read(ns, f"items/{path}.json"))["model"]
    if item["type"] == "minecraft:empty":
        return None
    mns, mpath = item["model"].split(":")
    model = json.loads(assets.read(mns, f"models/{mpath}.json"))
    tns, tpath = model["textures"]["layer0"].split(":")
    return assets.image(tns, f"textures/{tpath}.png").resize((16, 16), Image.NEAREST)


def main():
    screens = json.load(open(sys.argv[1], encoding="utf-8"))
    out_dir = sys.argv[2]
    assets = Assets(sys.argv[3] if len(sys.argv) > 3 else find_jar())
    gui = assets.image("minecraft", "textures/gui/container/generic_54.png").crop((0, 0, 176, 222))
    for name, sc in screens.items():
        canvas = Image.new("RGBA", (176 + 40, 222 + 40), (24, 24, 28, 255))
        menu = Image.new("RGBA", (176, 222), (0, 0, 0, 0))
        menu.alpha_composite(gui)
        draw_text(menu, assets, sc["title"], TITLE_X, TITLE_Y)
        for slot, model in sc["slots"].items():
            tex = item_texture(assets, model)
            if tex is not None:
                i = int(slot)
                menu.alpha_composite(tex, (8 + 18 * (i % 9), 18 + 18 * (i // 9)))
        draw_text(menu, assets, {"text": "Inventario"}, 8, 128)
        canvas.alpha_composite(menu, (20, 20))
        canvas = canvas.resize((canvas.width * 3, canvas.height * 3), Image.NEAREST)
        path = os.path.join(out_dir, f"menu_{name}.png")
        canvas.save(path)
        print(path)


if __name__ == "__main__":
    main()

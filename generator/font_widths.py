"""Saca del jar de Minecraft el ancho de cada letra de la fuente de vanilla y lo guarda en font_widths.json.

Los menus con diseño de la Dinopedia escriben texto en cualquier lugar del menu con copias de la fuente de vanilla
corridas hacia abajo (gui.py). Para ubicar cada cosa el plugin tiene que saber cuanto avanza cada letra, y lo calcula
igual que el juego: columna mas a la derecha con algun pixel + 1. No copia ninguna imagen de Mojang: el pack usa las
de vanilla (minecraft:font/ascii.png y accented.png) y aca solo se guardan los anchos.

Solo hace falta correrlo si Mojang cambia la fuente (necesita Pillow: generator/.venv):
    generator/.venv/Scripts/python generator/font_widths.py [ruta al jar del cliente]
"""
import glob
import io
import json
import os
import sys
import zipfile

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "font_widths.json")
FILES = {"ascii": "minecraft:font/ascii.png", "accented": "minecraft:font/accented.png"}


def find_jar():
    base = os.path.join(os.environ.get("APPDATA", ""), ".minecraft", "versions")
    jars = sorted(glob.glob(os.path.join(base, "*", "*.jar")), key=os.path.getmtime, reverse=True)
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            if "assets/minecraft/font/include/default.json" in z.namelist():
                return jar
    raise SystemExit("No encontre un jar de Minecraft: pasale la ruta")


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else find_jar()
    with zipfile.ZipFile(jar) as z:
        providers = json.loads(z.read("assets/minecraft/font/include/default.json"))["providers"]
        out = {}
        for key, file in FILES.items():
            p = next(p for p in providers if p.get("file") == file)
            img = Image.open(io.BytesIO(z.read("assets/minecraft/textures/font/" + file.split("/")[-1]))).convert("RGBA")
            rows = p["chars"]
            cw, ch = img.width // len(rows[0]), img.height // len(rows)
            height = p.get("height", 8)
            scale = height / ch
            widths = {}
            for r, row in enumerate(rows):
                for c, char in enumerate(row):
                    if char in ("\u0000", " "):
                        continue
                    actual = 0
                    for x in range(cw - 1, -1, -1):
                        if any(img.getpixel((c * cw + x, r * ch + y))[3] for y in range(ch)):
                            actual = x + 1
                            break
                    widths[char] = int(0.5 + actual * scale) + 1
            out[key] = {"height": height, "ascent": p["ascent"], "chars": rows, "widths": widths}
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1, sort_keys=True)
    print(f"{jar} -> {OUT}: " + ", ".join(f"{k} {len(v['widths'])}" for k, v in out.items()))


if __name__ == "__main__":
    main()

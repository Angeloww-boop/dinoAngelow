"""Arma la guia completa de DinoEarth en PDF (DinoEarth-Guia.pdf en la raiz del proyecto).

Saca los datos de las especies del codigo (Species.java), de los textos (textos.yml), de los rigs y de los drops
(generator/items.py), asi la guia no queda desactualizada. Las fotos de los dinos salen del pack; las capturas de
los menus estan en docs/guia/img (vistas previas de generator/gui_preview.py). El PDF lo imprime Edge sin ventana.

Uso (necesita Pillow y PyYAML: generator/.venv):
    generator/.venv/Scripts/python docs/guia/build_guia.py
"""
import html
import json
import os
import re
import subprocess
import sys

import yaml
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
RES = os.path.join(ROOT, "plugin", "src", "main", "resources")
IMG = os.path.join(HERE, "img")
OUT_HTML = os.path.join(HERE, "guia.html")
OUT_PDF = os.path.join(ROOT, "DinoEarth-Guia.pdf")
sys.path.insert(0, os.path.join(ROOT, "generator"))

EDGE = [r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Google\Chrome\Application\chrome.exe"]


def e(s):
    return html.escape(str(s))


# ---------------------------------------------------------------- datos
def species_data():
    src = open(os.path.join(ROOT, "plugin/src/main/java/com/dinoearth/species/Species.java"), encoding="utf-8").read()
    textos = yaml.safe_load(open(os.path.join(RES, "textos.yml"), encoding="utf-8"))
    out = []
    for m in re.finditer(r'new Builder\("(\w+)", Body\.(\w+), Temper\.(\w+)\)(.*?)\.build\(\);', src, re.S):
        sid, body, temper, chain = m.groups()
        st = re.search(r'\.stats\(([\d.]+), ([\d.]+), ([\d.]+)\)', chain)
        tame = re.search(r'\.tame\(([\d.]+)\)', chain)
        ride = re.search(r'\.ride\(([\d.]+), ([\d.]+), ([\d.]+)\)', chain)
        fly = re.search(r'\.fly\(([\d.]+), ([\d.]+)\)', chain)
        hunger = re.search(r'\.hunger\(([\d.]+), (\d+)\)', chain)
        group = re.search(r'\.group\((\d+)\)', chain)
        aliases = re.findall(r'"([^"]+)"', (re.search(r'\.aliases\(([^)]*)\)', chain) or [None, ""])[1] or "")
        rig = json.load(open(os.path.join(RES, "rigs", f"{sid}.json"), encoding="utf-8"))
        t = textos["especies"][sid]
        fish = ".food(FISH)" in chain
        diet = "FISH" if fish else "PLANTS" if temper in ("HERBIVORE", "PREY") else "MEAT"
        out.append(dict(
            id=sid, name=t["nombre"], size=t["tamano"], about=t["descripcion"], body=body, temper=temper,
            health=float(st.group(1)), damage=float(st.group(2)), speed=float(st.group(3)),
            tame=float(tame.group(1)) if tame else None, ride=ride.groups() if ride else None,
            fly=fly.groups() if fly else None, hunger=hunger.groups() if hunger else None,
            group=int(group.group(1)) if group else 1, aliases=aliases, diet=diet,
            charges=".charges()" in chain, spits=".spits()" in chain, roars=".roars()" in chain,
            territorial=".territorial()" in chain, eggonly=".eggTameOnly()" in chain,
            tail="SPIKES" if "SPIKES" in chain else "CLUB" if "CLUB" in chain else None,
            armored=".armored()" in chain, perches=".perches()" in chain,
            egg="egg" in rig, baby="baby" in rig["forms"], saddle="saddle" in rig,
            colors=[(v["id"], t["colores"].get(v["id"], v["name"]), v["weight"]) for v in rig["variants"]],
        ))
    return out, textos


def drops_data():
    import items
    return items.DROPS


# ---------------------------------------------------------------- imagenes
def render_images(species):
    os.makedirs(IMG, exist_ok=True)
    tex = os.path.join(ROOT, "resourcepack/assets/dinoearth/textures")
    for sp in species:
        sheet = Image.open(os.path.join(tex, "gui/render", sp["id"] + ".png")).convert("RGBA")
        cell = sheet.crop((0, 0, 64, 64))
        bg = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        bg.alpha_composite(cell)
        bg.resize((256, 256), Image.NEAREST).save(os.path.join(IMG, f"dino_{sp['id']}.png"))
        # todos los colores en una tira
        n = len(sp["colors"])
        strip = Image.new("RGBA", (64 * n, 64), (0, 0, 0, 0))
        strip.alpha_composite(sheet.crop((0, 0, 64 * n, 64)))
        strip.resize((64 * n * 2, 128), Image.NEAREST).save(os.path.join(IMG, f"colores_{sp['id']}.png"))
    # items: drops y monturas
    for f in os.listdir(os.path.join(tex, "item/items")):
        if f.endswith(".png"):
            Image.open(os.path.join(tex, "item/items", f)).convert("RGBA").resize((64, 64), Image.NEAREST).save(
                os.path.join(IMG, "item_" + f))
    for f in ("dinopedia.png",):
        Image.open(os.path.join(tex, "item", f)).convert("RGBA").resize((64, 64), Image.NEAREST).save(
            os.path.join(IMG, "item_" + f))
    for f in os.listdir(os.path.join(tex, "item/gui/stamp")):
        if not f.endswith("_off.png"):
            Image.open(os.path.join(tex, "item/gui/stamp", f)).convert("RGBA").resize((48, 48), Image.NEAREST).save(
                os.path.join(IMG, "sello_" + f))
    # capturas de menus: sin el margen oscuro
    for f in os.listdir(IMG):
        if f.startswith("menu_") and not f.startswith("menu_c_"):
            im = Image.open(os.path.join(IMG, f))
            im.crop((48, 48, im.width - 48, im.height - 48)).save(os.path.join(IMG, "menu_c_" + f[5:]))


# ---------------------------------------------------------------- textos de la guia
TEMPER = {"CARNIVORE": "Neutral: caza animales chicos y se defiende",
          "HERBIVORE": "Tranquilo: embiste si lo atacan",
          "HOSTILE": "Hostil: ataca a los jugadores que ve",
          "PACK": "En manada: ataca a los heridos o si son muchos",
          "PREY": "Asustadizo: huye en manada"}
DIET = {"MEAT": "Carne cruda", "PLANTS": "Plantas", "FISH": "Pescado crudo"}


def species_card(sp, drops, names):
    flags = []
    if sp["territorial"]:
        flags.append("Salvaje es <b>territorial</b>: te persigue aunque no lo ataques.")
    if sp["eggonly"]:
        flags.append("<b>Solo se domestica criándolo desde el huevo</b> (los salvajes no aceptan comida).")
    if sp["roars"]:
        flags.append("<b>Rugido</b>: da lentitud y oscuridad a los que están cerca. Montado, la barra espaciadora ruge en vez de saltar.")
    if sp["charges"]:
        flags.append("<b>Embestida</b>: montado y corriendo arrolla y tira lejos lo que tenga adelante.")
    if sp["spits"]:
        flags.append("<b>Escupe veneno</b>: abre la gola y da ceguera y veneno.")
    if sp["tail"] == "SPIKES":
        flags.append("<b>Cola con púas</b>: al que le pega por atrás le da un coletazo.")
    if sp["tail"] == "CLUB":
        flags.append("<b>Maza en la cola</b>: coletazo que tira lejos, también a los costados.")
    if sp["armored"]:
        flags.append("<b>Acorazado</b>: las flechas y proyectiles le hacen un 35 % del daño.")
    if sp["temper"] == "PREY":
        flags.append("<b>Presa</b>: si uno se asusta avisa y huye toda la manada. Acercate <b>agachado</b> para darle de comer.")
    if sp["temper"] == "PACK":
        flags.append(f"<b>Manada</b>: aparecen de a {sp['group']} y cazan juntos.")
    if sp["perches"]:
        flags.append("<b>Compañero volador</b>: domesticado vuela a tu lado y chilla si hay monstruos a 12 bloques.")
    if sp["fly"]:
        flags.append("<b>Vuela</b>: montado te lleva por el aire. Salvaje y lastimado, se escapa volando.")

    tame = ("Solo naciendo de un huevo" if sp["eggonly"] else
            f"Dándole {DIET[sp['diet']].lower()} ({round(sp['tame'] * 100)} % por bocado)" if sp["tame"] else
            "No se domestica")
    if sp["ride"] and sp["saddle"]:
        w, s, j = sp["ride"]
        ride = f"Sí, con <code>montura_{sp['id']}</code> (camina {w}, corre {s} bloques/tick" + (
            f", salto {j})" if float(j) > 0 else ", sin salto)")
        if sp["fly"]:
            ride += f". Vuela a {sp['fly'][0]} ({sp['fly'][1]} con Ctrl)"
    elif sp["perches"]:
        ride = "No se monta: vuela a tu lado"
    else:
        ride = "No se puede montar"
    hunger = f"{sp['hunger'][0]} min de lleno a muerto de hambre (estómago {sp['hunger'][1]} puntos)" if sp["hunger"] else "No tiene hambre"
    breed = "Sí: macho y hembra adultos domesticados y bien alimentados ponen huevos" if sp["egg"] else "No pone huevos"
    colors = ", ".join(f"{e(n)}{' <span class=rare>(raro)</span>' if wgt <= 5 else ''}" for _, n, wgt in sp["colors"])
    drop_rows = "".join(
        f"<tr><td><img class=ico src='img/item_{iid}.png'></td><td>{e(n)} <code>{iid}</code></td>"
        f"<td>{lo}–{hi}</td><td>{round(ch * 100)} %</td></tr>"
        for iid, n, shape, key, lo, hi, ch in drops.get(sp["id"], []))
    if sp["saddle"]:
        drop_rows += (f"<tr><td><img class=ico src='img/item_montura_{sp['id']}.png'></td><td>Montura de {e(sp['name'])}"
                      f" <code>montura_{sp['id']}</code></td><td colspan=2>Se craftea (Shopkeepers); la suelta si muere ensillado</td></tr>")
    aliases = ", ".join(f"<code>{a}</code>" for a in [sp["id"]] + sp["aliases"])
    return f"""
<section class="species">
  <div class="sp-head">
    <img class="dino" src="img/dino_{sp['id']}.png">
    <div>
      <h3>{e(sp['name'])}</h3>
      <p class="about">{e(sp['about'])}</p>
      <p class="small">Tamaño adulto: <b>{e(sp['size'])}</b> · En comandos: {aliases}</p>
    </div>
  </div>
  <table class="stats">
    <tr><th>Vida</th><td>{sp['health']:g}</td><th>Daño</th><td>{sp['damage']:g}</td><th>Velocidad</th><td>{sp['speed']:g}</td></tr>
    <tr><th>Carácter</th><td colspan=5>{TEMPER[sp['temper']]}</td></tr>
    <tr><th>Come</th><td colspan=5>{DIET[sp['diet']]}</td></tr>
    <tr><th>Domesticar</th><td colspan=5>{tame}</td></tr>
    <tr><th>Montura</th><td colspan=5>{ride}</td></tr>
    <tr><th>Hambre</th><td colspan=5>{hunger}</td></tr>
    <tr><th>Crías</th><td colspan=5>{breed}{'' if sp['baby'] else ' (no tiene forma de cría)'}</td></tr>
    <tr><th>Colores</th><td colspan=5>{colors}</td></tr>
  </table>
  <img class="strip" src="img/colores_{sp['id']}.png">
  {'<ul class="flags">' + ''.join(f'<li>{f}</li>' for f in flags) + '</ul>' if flags else ''}
  {'<table class="drops"><tr><th></th><th>Suelta</th><th>Cant.</th><th>Prob.</th></tr>' + drop_rows + '</table>' if drop_rows else ''}
</section>"""


CSS = """
@page { size: A4; margin: 16mm 15mm 18mm 15mm; }
* { box-sizing: border-box; }
body { font-family: 'Segoe UI', Arial, sans-serif; color: #2b2116; font-size: 10.5pt; line-height: 1.45; }
h1 { font-size: 30pt; margin: 0; color: #5c3a1e; }
h2 { font-size: 17pt; color: #5c3a1e; border-bottom: 3px solid #c9a46a; padding-bottom: 4px; margin-top: 0; }
h3 { font-size: 14pt; margin: 0 0 4px 0; color: #6b3f1d; }
h4 { font-size: 11.5pt; margin: 14px 0 4px 0; color: #6b3f1d; }
.chapter { page-break-before: always; }
.cover { height: 250mm; display: flex; flex-direction: column; justify-content: center; align-items: center;
  text-align: center; background: linear-gradient(#f3e7c9, #e3cf9f); border: 6px solid #6b4423; border-radius: 10px; }
.cover .sub { font-size: 14pt; color: #6b4423; margin-top: 6px; }
.cover .row { display: flex; gap: 6px; margin: 22px 0; flex-wrap: wrap; justify-content: center; max-width: 170mm; }
.cover .row img { width: 30mm; image-rendering: pixelated; }
.cover .meta { font-size: 10pt; color: #6b4423; margin-top: 16px; }
table { border-collapse: collapse; width: 100%; margin: 6px 0 10px 0; }
th, td { border: 1px solid #d8c49b; padding: 4px 6px; text-align: left; vertical-align: top; }
th { background: #efe2c2; color: #4a3018; font-weight: 600; }
code { font-family: Consolas, monospace; background: #f1e8d4; padding: 0 3px; border-radius: 3px; font-size: 9.5pt; }
.species { page-break-inside: avoid; border: 1px solid #d8c49b; border-radius: 8px; padding: 7px 10px; margin: 0 0 8px 0;
  background: #fffaf0; font-size: 9.2pt; line-height: 1.32; }
.species table { margin: 4px 0 4px 0; }
.species th, .species td { padding: 2px 5px; }
.species .flags { margin: 2px 0 4px 0; }
.sp-head { display: flex; gap: 12px; align-items: center; }
.sp-head img.dino { width: 24mm; image-rendering: pixelated; background: radial-gradient(#efe2c2, #fffaf0); border-radius: 6px; }
.about { margin: 2px 0; font-style: italic; }
.small { font-size: 9pt; color: #6d5a42; margin: 2px 0; }
.stats th { width: 22mm; }
.strip { height: 10mm; image-rendering: pixelated; margin: 2px 0 4px 0; }
.flags { margin: 4px 0 6px 0; padding-left: 18px; }
.drops td:first-child, .drops th:first-child { width: 10mm; text-align: center; }
img.ico { width: 7mm; image-rendering: pixelated; vertical-align: middle; }
.rare { color: #8a3fa5; font-weight: 600; }
.shot { image-rendering: pixelated; border: 1px solid #b89a63; border-radius: 4px; }
.two { display: flex; gap: 10px; align-items: flex-start; }
.two > div { flex: 1; }
.note { background: #f3ead6; border-left: 4px solid #c9a46a; padding: 6px 10px; margin: 8px 0; }
.warn { background: #fbe9e4; border-left: 4px solid #c0553a; padding: 6px 10px; margin: 8px 0; }
ul { margin: 4px 0 8px 0; }
li { margin: 2px 0; }
.toc li { margin: 3px 0; }
.stamps img { width: 10mm; image-rendering: pixelated; vertical-align: middle; }
"""


def build():
    species, textos = species_data()
    drops = drops_data()
    render_images(species)
    names = {s["id"]: s["name"] for s in species}
    version = re.search(r"^version: (.+)$", open(os.path.join(RES, "plugin.yml"), encoding="utf-8").read(), re.M).group(1)
    cover_imgs = "".join(f"<img src='img/dino_{s['id']}.png'>" for s in species)
    species_html = "".join(species_card(s, drops, names) for s in species)
    summary_rows = "".join(
        f"<tr><td><img class=ico src='img/dino_{s['id']}.png'></td><td><b>{e(s['name'])}</b></td><td>{e(s['size'])}</td>"
        f"<td>{TEMPER[s['temper']].split(':')[0]}</td><td>{DIET[s['diet']]}</td>"
        f"<td>{'Huevo' if s['eggonly'] else 'Sí' if s['tame'] else 'No'}</td>"
        f"<td>{'Vuela' if s['fly'] else 'Sí' if s['ride'] and s['saddle'] else 'A tu lado' if s['perches'] else 'No'}</td>"
        f"<td>{len(s['colors'])}</td></tr>" for s in species)
    stamp_names = textos["sellos"]
    stamps_html = "".join(
        f"<tr><td class=stamps><img src='img/sello_{k.lower()}.png'></td><td><b>{e(v['nombre'])}</b></td><td>{e(v['tarea'])}</td></tr>"
        for k, v in stamp_names.items())
    ranks = ", ".join(f"<b>{e(r)}</b> ({p} %)" for r, p in zip(textos["rangos"], [0, 10, 25, 45, 70, 100]))

    body = f"""
<div class="cover">
  <h1>DinoEarth</h1>
  <div class="sub">Guía completa del plugin de dinosaurios · versión {e(version)}</div>
  <div class="row">{cover_imgs}</div>
  <div class="meta">Para la modalidad Minecraft Earth de Angeloww_ · Paper 1.21.11 · clientes 1.21.4 en adelante</div>
</div>

<div class="chapter">
<h2>Contenido</h2>
<ol class="toc">
  <li>Qué es DinoEarth</li>
  <li>Instalación y resource pack</li>
  <li>Las 13 especies</li>
  <li>Domesticar, cuidar y criar</li>
  <li>Montar y volar</li>
  <li>Silbato: órdenes, llamar de regreso y avisos</li>
  <li>Ítems: drops, monturas, silbato, jaula y comederos</li>
  <li>Dinopedia: fichas, sellos, rangos, huellas y lupa</li>
  <li>Misiones diarias</li>
  <li>Comandos y permisos</li>
  <li>Configuración (config.yml)</li>
  <li>Textos y menús (YML)</li>
  <li>Rendimiento y límites</li>
  <li>Problemas comunes</li>
</ol>

<h2 style="margin-top:18px">1. Qué es DinoEarth</h2>
<p>DinoEarth agrega <b>13 especies de dinosaurios</b> al server, con modelos y animaciones propias, sin mods en el
cliente: todo viene en un <b>resource pack</b> que el plugin manda solo al entrar. Cada dino nace en uno de varios
<b>colores</b> (uno de ellos raro), es <b>macho o hembra</b> y la mayoría tiene <b>crías</b> que crecen.</p>
<p>Se pueden <b>domesticar</b>, <b>montar</b> (el pteranodon <b>vuela</b>), <b>criar</b> con huevos, darles
<b>órdenes</b> con un silbato, guardarlos en <b>jaulas</b>, regalarlos y seguirles el rastro con la
<b>Dinopedia</b>, un libro con fichas dibujadas, sellos de investigación, rangos y misiones diarias.</p>
<div class="note">Cómo está hecho: cada dino es un lobo invisible (que pone la IA, la vida y la domesticación)
con una pieza del modelo por hueso montada encima. El cliente interpola las poses, así que las animaciones se ven
suaves aunque el server mande poco.</div>
<h4>Resumen de especies</h4>
<table><tr><th></th><th>Especie</th><th>Tamaño</th><th>Carácter</th><th>Come</th><th>Domesticable</th><th>Montura</th><th>Colores</th></tr>
{summary_rows}</table>
</div>

<div class="chapter">
<h2>2. Instalación y resource pack</h2>
<h4>Instalar o actualizar</h4>
<ol>
  <li>Copiá <code>DinoEarth-{e(version)}.jar</code> en <code>plugins/</code> (borrá el jar anterior) y reiniciá el server.</li>
  <li>La primera vez se crean <code>plugins/DinoEarth/config.yml</code>, <code>messages.yml</code>, <code>textos.yml</code>,
      <code>items.yml</code> y la carpeta <code>menus/</code>. Al actualizar, las opciones y textos nuevos se agregan solos
      sin tocar lo que cambiaste (el config anterior queda como <code>config-anterior.yml</code>).</li>
  <li>Configurá el resource pack (abajo) y probá con <code>/dino pack info</code>.</li>
</ol>
<h4>El resource pack</h4>
<p>El plugin le manda el pack a cada jugador al entrar. Hay dos formas:</p>
<table>
<tr><th>Modo</th><th>Cómo funciona</th><th>Cuándo usarlo</th></tr>
<tr><td><code>url</code></td><td>Subís <code>DinoEarth-ResourcePack.zip</code> a un sitio de descarga directa (por ejemplo
mc-packs.net), pegás el link en <code>resource-pack.url</code> y hacés <code>/dino pack reload</code>. El plugin lo descarga
al arrancar, le calcula el hash y <b>avisa si el zip subido no es el de esta versión</b>.</td><td>Si tu hosting no deja abrir puertos.</td></tr>
<tr><td><code>host</code></td><td>El plugin sirve él mismo el pack que trae adentro del jar. No hay que subir nada: alcanza con
cambiar el jar.</td><td>Si podés abrir un puerto (por defecto 8163) y poner la IP o dominio en
<code>resource-pack.host.public-address</code>.</td></tr>
</table>
<div class="warn"><b>Importante:</b> cada vez que una versión dice que <b>el pack cambió</b>, en modo <code>url</code> hay que
subir el zip nuevo y cambiar el link. Si no, los jugadores siguen con el pack viejo: aparecen cuadrados violeta y negro
o letras como cuadraditos en los menús nuevos. <code>/dino pack info</code> muestra el link y el sha1 que se está mandando.</div>
<p>Con <code>resource-pack.required: true</code> quien rechace el pack no puede entrar. Si usás este sistema, sacá
<code>resource-pack</code> de <code>server.properties</code> para que no se descargue dos veces.</p>
<h4>Clientes</h4>
<p>El pack sirve desde Minecraft <b>1.21.4</b> en adelante (detrás de Velocity con ViaVersion entran otras versiones
mientras sean 1.21.4 o más nuevas). No usa brillos ni contornos (compatibles con VulkanMod) y las texturas están
empaquetadas para ocupar poca memoria de video.</p>
</div>

<div class="chapter">
<h2>3. Las 13 especies</h2>
<p>Valores por defecto (todos se pueden cambiar en <code>config.yml</code>, sección <code>species</code>). Las
velocidades de montura van en bloques por tick: un jugador caminando hace ~0,22 y corriendo ~0,28.</p>
{species_html}
</div>

<div class="chapter">
<h2>4. Domesticar, cuidar y criar</h2>
<h4>Domesticar</h4>
<ul>
  <li>Click derecho al dino salvaje con <b>su comida</b>: cada bocado tiene una chance (la de su ficha). Al lograrlo
      salen corazones y queda <b>sentado</b>; click derecho para que te siga.</li>
  <li>Los <b>hostiles</b> (dilophosaurus, compsognathus) no se domestican. El <b>T-Rex</b> y el <b>Giganotosaurus</b>
      salvajes tampoco: hay que criarlos desde el huevo.</li>
  <li>A las <b>presas</b> (parasaurolophus, gallimimus) hay que acercarse <b>agachado</b> o salen corriendo.</li>
  <li>Hay un <b>máximo de domesticados por jugador</b> (20 por defecto). Con el máximo no se puede domesticar,
      aceptar regalos ni soltar jaulas, y los huevos esperan listos hasta que haya lugar.</li>
</ul>
<h4>Sentar, curar y nombrar</h4>
<ul>
  <li><b>Click derecho</b>: se sienta o se para (agachado si tiene montura).</li>
  <li><b>Su comida</b> lo cura. Con hambre, primero come (cualquiera le puede dar de comer).</li>
  <li><b>Nombre</b>: renombrá una etiqueta en un yunque y hacé click derecho a tu dino. Arriba del modelo aparece el
      nombre y "de &lt;dueño&gt;".</li>
</ul>
<h4>Hambre y comederos</h4>
<ul>
  <li>Solo los domesticados tienen hambre: pasan de llenos a muertos de hambre en 1 o 2 horas de juego (según la especie).</li>
  <li><b>Muertos de hambre se echan</b>, se ven mal, no se dejan montar ni crecen. <b>Nunca se mueren.</b></li>
  <li><b>Comedero</b> (de carne, de plantas o de pescado): se pone en el suelo y se carga con click derecho con su comida
      (agachado, la pila entera). No tiene inventario: la comida se convierte en puntos (hasta 600) y no se puede sacar,
      así que no hay dupes. Los domesticados con hambre a menos de 16 bloques <b>van solos a comer</b>. Lo rompe el dueño
      (agachado si tiene comida). Respeta Towny y WorldGuard.</li>
</ul>
<table><tr><th>Comida</th><th>Puntos</th></tr>
<tr><td>Carne de vaca o cerdo</td><td>10</td></tr><tr><td>Cordero, salmón</td><td>8</td></tr>
<tr><td>Pollo, conejo, bacalao, manzana</td><td>6</td></tr><tr><td>Trigo, zanahoria</td><td>5</td></tr>
<tr><td>Remolacha, pez tropical</td><td>4</td></tr><tr><td>Bayas, sandía</td><td>3</td></tr><tr><td>Fardo de heno</td><td>45</td></tr></table>
<h4>Criar</h4>
<ol>
  <li>Dale comida a un <b>macho y una hembra adultos domesticados</b> y bien alimentados: la hembra pone un <b>huevo</b>
      (después hay que esperar 5 minutos para otro).</li>
  <li>Click derecho al suelo con el huevo. Se mueve, se agrieta y a los <b>5 minutos</b> nace una <b>cría</b> de color al
      azar, domesticada por quien puso el huevo. Click derecho al huevo: ver el %. Golpe: recogerlo.</li>
  <li>La cría <b>crece sola en 40 minutos</b> (con el chunk cargado). Darle su comida la hace crecer un 5 %.</li>
</ol>
</div>

<div class="chapter">
<h2>5. Montar y volar</h2>
<ul>
  <li>Cada especie montable tiene <b>su propia montura</b> (ítem <code>montura_&lt;especie&gt;</code>). La montura de
      vanilla no sirve. Click derecho con ella a tu dino <b>adulto</b> (solo el dueño) y otro click derecho para subir.</li>
  <li><b>Controles</b>: WASD, Ctrl corre, espacio salta, Shift baja. Con <b>tijeras</b> le sacás la montura y te la devuelve.</li>
  <li><b>T-Rex y Giganotosaurus</b>: espacio <b>ruge</b> en vez de saltar (lentitud y oscuridad a los cercanos).</li>
  <li><b>Triceratops y Carnotaurus</b>: corriendo <b>embisten</b> y tiran lejos a lo que tengan adelante.</li>
</ul>
<h4>Volar con el pteranodon</h4>
<ul>
  <li>En el suelo, <b>espacio despega</b>.</li>
  <li>En el aire, <b>W vuela hacia donde mirás</b> (mirar abajo es picada, arriba trepar), <b>Ctrl</b> va más rápido,
      <b>espacio aletea</b> para subir y sin W planea bajando.</li>
  <li>Aterriza solo al tocar el suelo. Si te bajás en el aire caés con caída lenta.</li>
</ul>
<h4>El dimorphodon, compañero volador</h4>
<ul>
  <li>No se monta: domesticado y siguiéndote, si te alejás despega solo y <b>vuela a tu lado</b>. Quieto da vueltas
      alrededor tuyo; caminando, corriendo o a caballo va atrás a la derecha, adelantándose a hacia dónde vas.</li>
  <li>Si queda muy lejos (teleport, portal, élitros) aparece al lado tuyo.</li>
  <li><b>Agachado + click derecho con la mano vacía</b>: alterna entre volar con vos y caminar.</li>
  <li>Si hay monstruos a 12 bloques, chilla y te avisa.</li>
</ul>
</div>

<div class="chapter">
<h2>6. Silbato: órdenes, llamar de regreso y avisos</h2>
<div class="two">
<div>
<p>El <b>silbato</b> (<code>/dino item silbato</code>) abre el menú de órdenes:</p>
<ul>
  <li><b>Click derecho a tu dino</b>: órdenes para ese dino.</li>
  <li><b>Agachado + click derecho al aire</b>: órdenes para todos tus dinos a 16 bloques. Si no hay ninguno cerca, abre
      directo "llamar de regreso".</li>
</ul>
<table><tr><th>Orden</th><th>Qué hace</th></tr>
<tr><td>Seguirme</td><td>Te sigue a donde vayas.</td></tr>
<tr><td>Quedarse acá</td><td>Se sienta y espera.</td></tr>
<tr><td>Patrullar este chunk</td><td>Camina por el chunk donde estás parado y no se va de ahí.</td></tr>
<tr><td>Pasivo</td><td>No ataca a nadie, ni aunque lo ataquen.</td></tr>
<tr><td>Defensivo</td><td>Te defiende a vos y a sí mismo (lo normal).</td></tr>
<tr><td>Agresivo</td><td>Además ataca a los monstruos que vea cerca, menos creepers.</td></tr>
</table>
</div>
<div><img class="shot" style="width:78mm" src="img/menu_c_silbato.png">
<p class="small">Vista previa del menú: algunos íconos de vanilla (cama, escudo, reloj) no se dibujan en la vista previa; en el juego sí.</p></div>
</div>
<h4>Mis dinos y llamar de regreso</h4>
<ul>
  <li><code>/dino mis</code>: lista de todos tus domesticados con especie, nombre, mundo, coordenadas, distancia y orden,
      <b>aunque estén en chunks descargados</b>. Al lado de cada uno, un botón <b>[Llamar]</b>.</li>
  <li><b>Llamar dinos lejanos</b> (botón del silbato o <code>/dino llamar</code>): elegís hasta <b>2</b> y vuelven a tu lado
      (después te siguen). Se puede <b>una vez cada 5 minutos</b>.</li>
  <li>Si el dino está en un chunk descargado, el plugin carga <b>solo ese chunk, un momento</b>, lo trae y lo suelta.
      Si llamás a dos del mismo chunk, se carga una sola vez.</li>
</ul>
<div class="two"><div><img class="shot" style="width:78mm" src="img/menu_c_llamar.png"></div>
<div>
<h4>Avisos</h4>
<ul>
  <li>Si <b>atacan a tu dino</b> estando vos a más de 24 bloques, te llega un aviso con coordenadas y quién fue
      (como mucho uno cada 30 segundos por dino).</li>
  <li>Si <b>muere</b>, te avisa dónde y de qué. Si no estabas conectado, el aviso te llega al entrar.</li>
</ul>
<h4>Regalar</h4>
<ul>
  <li><code>/dino dar &lt;jugador&gt;</code> mirando a tu dino. El otro recibe los botones [Aceptar] y [Rechazar] y tiene
      60 segundos.</li>
</ul>
</div></div>
</div>

<div class="chapter">
<h2>7. Ítems</h2>
<p>Todos se pueden dar con <code>/dino item &lt;id&gt; [cantidad] [jugador]</code> (para armar los trades de Shopkeepers).
Los ítems propios <b>no sirven</b> en mesas de crafteo, yunques, hornos ni dispensers: los crafteos los arma el dueño
con Shopkeepers.</p>
<table>
<tr><th></th><th>Ítem</th><th>Para qué sirve</th></tr>
<tr><td><img class=ico src="img/item_silbato.png"></td><td>Silbato <code>silbato</code></td><td>Órdenes y llamar de regreso (capítulo 6).</td></tr>
<tr><td><img class=ico src="img/item_jaula.png"></td><td>Jaula <code>jaula</code></td><td>Click derecho a tu dino con una jaula vacía:
lo guarda adentro (color, sexo, edad, montura, hambre, vida, nombre y modo). Click derecho al piso con la jaula llena:
lo suelta, y <b>quien lo suelta queda como dueño</b> (sirve para vender dinos). Los dinos enjaulados no cuentan para el
límite.</td></tr>
<tr><td><img class=ico src="img/item_dinopedia.png"></td><td>Dinopedia</td><td>El libro de investigación (capítulo 8).
<code>/dino dinopedia [jugador]</code>.</td></tr>
<tr><td></td><td>Huevo</td><td>Se pone en el suelo y nace una cría tuya. <code>/dino egg &lt;especie&gt;</code>.</td></tr>
<tr><td></td><td>Comedero</td><td>De carne, de plantas o de pescado. <code>/dino comedero &lt;dieta&gt;</code>.</td></tr>
<tr><td></td><td>Monturas</td><td>Una por especie montable: <code>montura_raptor</code>, <code>montura_triceratops</code>,
<code>montura_trex</code>, <code>montura_pteranodon</code>, <code>montura_parasaurolophus</code>, <code>montura_gallimimus</code>,
<code>montura_stegosaurus</code>, <code>montura_ankylosaurus</code>, <code>montura_giganotosaurus</code>, <code>montura_carnotaurus</code>.</td></tr>
</table>
<p>Los <b>drops</b> de cada especie (cuero, garras, dientes, cuernos, placas, membranas, plumas, glándula, cresta, maza) están
en su ficha del capítulo 3, con cantidad y probabilidad. Las crías sueltan menos. Si muere ensillado, también suelta su montura.</p>
</div>

<div class="chapter">
<h2>8. Dinopedia</h2>
<p>Un libro con menús dibujados como una libreta de paleontólogo. Cada especie y cada color se anotan solos al ver un dino
de cerca (16 bloques; con la Dinopedia en la mano, 32).</p>
<div class="two">
<div><img class="shot" style="width:86mm" src="img/menu_c_indice.png"><p class="small">Índice: rango, barra de investigación,
especies (silueta las que no viste) y la barrita de sellos de cada una.</p></div>
<div><img class="shot" style="width:86mm" src="img/menu_c_especie.png"><p class="small">Página de especie: foto del color que
elijas, barras de vida, daño, velocidad y tamaño, colores, sellos y la lupa.</p></div>
</div>
<div class="two">
<div><img class="shot" style="width:86mm" src="img/menu_c_ficha.png"><p class="small">Ficha (click derecho a un dino con el libro):
foto, sexo, edad, color, vida, hambre, dueño y órdenes.</p></div>
<div>
<h4>Sellos de investigación</h4>
<p>Cada especie tiene sus sellos (según lo que se puede hacer con ella). Cada sello da <b>30 de experiencia</b> y completar
una especie da <b>150</b> más (configurable, y se pueden poner comandos de premio).</p>
<table>{stamps_html}</table>
</div></div>
<h4>Rangos</h4>
<p>Según el porcentaje de sellos conseguidos: {ranks}.</p>
<h4>Llevar la Dinopedia en la mano</h4>
<ul>
  <li><b>Huellas</b>: si hay cerca (64 bloques) una especie que no conocés, la barra de acción muestra huellas, su tamaño
      y una flecha hacia dónde ir, sin decir qué es.</li>
  <li><b>Lupa</b>: en la página de una especie, activá la lupa y la barra de acción te marca con una flecha dónde está la
      más cercana (hasta 160 bloques) y a cuántos metros, primero los colores que todavía no viste.</li>
</ul>
<img class="shot" style="width:170mm" src="img/tooltips.png">
</div>

<div class="chapter">
<h2>9. Misiones diarias</h2>
<div class="two">
<div>
<ul>
  <li>Cada jugador tiene <b>3 misiones propias por día</b>, distintas a las de los demás. Cambian a la medianoche (hora
      argentina, configurable).</li>
  <li>Solo piden especies que <b>alguien vio en el server en los últimos 3 días</b> (o que están cargadas), así nadie
      recibe una misión imposible.</li>
  <li>Se ven en la Dinopedia (botón <b>Misiones del día</b>) o con <code>/dino misiones</code>.</li>
  <li>Cada avance muestra una <b>bossbar</b> unos segundos; al completar, mensaje y premio.</li>
  <li>Se puede <b>cambiar una misión por día</b> (botón de las flechas doradas).</li>
  <li>Premio por defecto: <b>100 de experiencia</b> por misión y <b>200</b> por completar las 3. Se pueden agregar comandos
      (plata, kits) en <code>missions.reward-commands</code> y <code>missions.all-done-commands</code>.</li>
</ul>
</div>
<div><img class="shot" style="width:86mm" src="img/menu_c_misiones.png"></div>
</div>
<table><tr><th>Misión</th><th>Cómo se cumple</th></tr>
<tr><td>Avistá N &lt;especie&gt;</td><td>Ver de cerca N dinos distintos de esa especie.</td></tr>
<tr><td>Avistá un &lt;especie&gt; &lt;color&gt;</td><td>Ver ese color (uno que todavía no tenés en la Dinopedia).</td></tr>
<tr><td>Domesticá un &lt;especie&gt;</td><td>Domesticarlo con comida.</td></tr>
<tr><td>Criá una cría de huevo</td><td>Que nazca una cría de un huevo tuyo (solo si tenés dinos que ponen huevos).</td></tr>
<tr><td>Recorré N bloques montado</td><td>Andar montado (solo si tenés un dino montable).</td></tr>
<tr><td>Derrotá N &lt;especie&gt;</td><td>Matar salvajes hostiles (dilophosaurus, compsognathus).</td></tr>
<tr><td>Cargá N puntos en comederos</td><td>Cargar comida en comederos (solo si tenés dinos con hambre).</td></tr>
</table>
</div>

<div class="chapter">
<h2>10. Comandos y permisos</h2>
<h4>Todos los jugadores</h4>
<table><tr><th>Comando</th><th>Qué hace</th></tr>
<tr><td><code>/dino mis</code></td><td>Tus dinos con dónde están y botón para llamarlos.</td></tr>
<tr><td><code>/dino llamar [id] [id]</code></td><td>Llamar de regreso (sin ids abre el menú; los ids salen de <code>/dino mis</code>).</td></tr>
<tr><td><code>/dino misiones</code></td><td>Abre tus misiones del día.</td></tr>
<tr><td><code>/dino dar &lt;jugador&gt;</code></td><td>Regalar el dino que estás mirando.</td></tr>
<tr><td><code>/dino aceptar</code> · <code>/dino rechazar</code></td><td>Responder un regalo.</td></tr>
<tr><td><code>/dino pack</code></td><td>Volver a pedir el resource pack.</td></tr>
</table>
<h4>Administradores (permiso <code>dinoearth.admin</code>, OP por defecto)</h4>
<table><tr><th>Comando</th><th>Qué hace</th></tr>
<tr><td><code>/dino spawn &lt;tipo&gt; [color|random] [macho|hembra|random] [adulto|bebe] [cantidad]</code></td><td>Spawnea dinos salvajes.</td></tr>
<tr><td><code>/dino egg &lt;especie&gt; [cantidad]</code></td><td>Te da huevos.</td></tr>
<tr><td><code>/dino grow [%]</code></td><td>Hace crecer a las crías a 10 bloques.</td></tr>
<tr><td><code>/dino tame [jugador]</code></td><td>Domestica el dino que mirás (a vos o a otro). Sirve para T-Rex y Giganotosaurus y no tiene límite.</td></tr>
<tr><td><code>/dino item &lt;id&gt; [cantidad] [jugador]</code></td><td>Monturas, drops, silbato y jaula.</td></tr>
<tr><td><code>/dino comedero &lt;carne|plantas|pescado&gt; [cantidad]</code></td><td>Te da comederos.</td></tr>
<tr><td><code>/dino dinopedia [jugador]</code></td><td>Entrega una Dinopedia.</td></tr>
<tr><td><code>/dino hambre [%]</code></td><td>Pone el hambre de los domesticados a 10 bloques (para probar).</td></tr>
<tr><td><code>/dino remove [radio] [especie]</code></td><td>Borra dinos cercanos.</td></tr>
<tr><td><code>/dino count</code></td><td>Dinos cargados por especie.</td></tr>
<tr><td><code>/dino stats</code></td><td>Cuánto consume el plugin (capítulo 13).</td></tr>
<tr><td><code>/dino pack reload</code> · <code>/dino pack info</code></td><td>Recargar el pack después de cambiar el link · ver qué pack se manda.</td></tr>
<tr><td><code>/dino reload</code></td><td>Recarga messages.yml, textos.yml, items.yml y los menús.</td></tr>
</table>
<h4>Permisos</h4>
<table><tr><th>Permiso</th><th>Para qué</th></tr>
<tr><td><code>dinoearth.admin</code></td><td>Comandos de administración (OP por defecto).</td></tr>
<tr><td><code>dinoearth.limit.&lt;número&gt;</code></td><td>Sube el máximo de domesticados de ese jugador (vale el mayor que tenga). Ideal para rangos VIP.</td></tr>
<tr><td><code>dinoearth.limit.unlimited</code></td><td>Sin límite de domesticados.</td></tr>
</table>
</div>

<div class="chapter">
<h2>11. Configuración (config.yml)</h2>
<table><tr><th>Opción</th><th>Por defecto</th><th>Qué hace</th></tr>
<tr><td><code>resource-pack.mode</code></td><td>url</td><td><code>url</code>, <code>host</code> u <code>off</code> (capítulo 2).</td></tr>
<tr><td><code>resource-pack.url</code></td><td>(vacío)</td><td>Link directo al zip del pack.</td></tr>
<tr><td><code>resource-pack.host.port</code> / <code>public-address</code></td><td>8163 / (vacío)</td><td>Puerto y dirección para el modo host.</td></tr>
<tr><td><code>resource-pack.required</code></td><td>false</td><td>Echa a quien rechace el pack.</td></tr>
<tr><td><code>growth-minutes</code></td><td>40</td><td>Minutos que tarda una cría en ser adulta.</td></tr>
<tr><td><code>hatch-minutes</code></td><td>5</td><td>Minutos que tarda un huevo en nacer.</td></tr>
<tr><td><code>breed-cooldown-minutes</code></td><td>5</td><td>Espera entre un huevo y el siguiente.</td></tr>
<tr><td><code>hunger.enabled</code> / <code>feeder-capacity</code></td><td>true / 600</td><td>Hambre de los domesticados y puntos que entran en un comedero.</td></tr>
<tr><td><code>recall.max-dinos</code> / <code>cooldown-minutes</code></td><td>2 / 5</td><td>Dinos por llamada y espera entre llamadas.</td></tr>
<tr><td><code>alerts.attack-cooldown-seconds</code></td><td>30</td><td>Cada cuánto como mucho avisa que atacan a un dino.</td></tr>
<tr><td><code>limits.max-tamed-per-player</code></td><td>20</td><td>Máximo de domesticados por jugador (0 = sin límite).</td></tr>
<tr><td><code>dinopedia.xp-per-stamp</code> / <code>xp-species-complete</code></td><td>30 / 150</td><td>Experiencia por sello y por especie completa.</td></tr>
<tr><td><code>dinopedia.species-complete-commands</code> / <code>all-complete-commands</code></td><td>[]</td><td>Comandos de premio (<code>%player%</code>, <code>%species%</code>).</td></tr>
<tr><td><code>dinopedia.discover-range</code> / <code>book-discover-range</code></td><td>16 / 32</td><td>Distancia para anotar un dino (sin y con el libro en la mano).</td></tr>
<tr><td><code>dinopedia.tracks-range</code> / <code>track-range</code></td><td>64 / 160</td><td>Alcance de las huellas y de la lupa.</td></tr>
<tr><td><code>missions.timezone</code> / <code>reset-hour</code></td><td>Argentina / 0</td><td>Cuándo cambian las misiones.</td></tr>
<tr><td><code>missions.rerolls-per-day</code></td><td>1</td><td>Misiones que se pueden cambiar por día.</td></tr>
<tr><td><code>missions.recent-days</code></td><td>3</td><td>Días que cuenta una especie como "vista en el server" para las misiones.</td></tr>
<tr><td><code>missions.reward-xp</code> / <code>all-done-xp</code></td><td>100 / 200</td><td>Experiencia por misión y por completar las 3.</td></tr>
<tr><td><code>missions.reward-commands</code> / <code>all-done-commands</code></td><td>[]</td><td>Comandos de premio (<code>%player%</code>, <code>%mision%</code>).</td></tr>
<tr><td><code>species.&lt;id&gt;.*</code></td><td>(ver fichas)</td><td>Por especie: <code>health</code>, <code>damage</code>, <code>speed</code>,
<code>tame-chance</code>, <code>hunger-minutes</code>, <code>ride.walk-speed</code>, <code>ride.sprint-speed</code>, <code>ride.jump</code>,
<code>ride.fly-speed</code>, <code>ride.fly-sprint-speed</code>.</td></tr>
</table>
</div>

<div class="chapter">
<h2>12. Textos y menús (YML)</h2>
<p>Ningún texto que ven los jugadores está escrito en el código: todo sale de archivos en <code>plugins/DinoEarth/</code>,
que podés editar y recargar con <code>/dino reload</code>.</p>
<table><tr><th>Archivo</th><th>Qué tiene</th></tr>
<tr><td><code>messages.yml</code></td><td>Mensajes de <code>chat</code>, <code>actionbar</code> y <code>bossbar</code>.</td></tr>
<tr><td><code>textos.yml</code></td><td>Palabras que van adentro de los mensajes: nombres, tamaños y descripciones de las especies y sus
colores, órdenes, causas de muerte, tiempos, sellos, rangos y misiones.</td></tr>
<tr><td><code>items.yml</code></td><td>Nombre y lore de cada ítem.</td></tr>
<tr><td><code>menus/silbato.yml</code>, <code>menus/llamar.yml</code></td><td>Menús con el formato del server: título, tamaño,
relleno, ítems y slots.</td></tr>
<tr><td><code>menus/dinopedia.yml</code></td><td>La Dinopedia. Es la excepción: el fondo va dibujado, pero el nombre y el lore
de cada ítem siguen el formato. Los textos escritos sobre las páginas van en tinta (sin colores).</td></tr>
</table>
<h4>Formato</h4>
<ul>
  <li>Colores con <code>&amp;</code> y hex <code>&amp;#rrggbb</code>. Texto <code>&amp;f</code>, variables <code>&amp;#ff00d9</code>,
      símbolos y separadores <code>&amp;8</code>.</li>
  <li>Chat: prefijo <code>&amp;9&amp;lDINOEARTH &amp;8»</code> y termina en punto blanco.
      Actionbar: <code>&amp;8| &amp;fTexto &amp;#ff00d9variable&amp;f texto.</code></li>
  <li>Menús: <code>menu_title: '&amp;8| &amp;9Nombre'</code>, relleno de vidrio negro, lore con <code>&amp;8| &amp;f</code> y
      separadores <code>&amp;f</code>, barrier en el slot 49 (Volver o Cerrar), indicadores
      <code>&amp;#41FF00✔ Click para ...</code> y <code>&amp;#EE193B✘ Click para ...</code>.</li>
  <li>Los <code>%placeholders%</code> los completa el plugin (por ejemplo <code>%player%</code>, <code>%especie%</code>).</li>
</ul>
<div class="note">Al actualizar el plugin, los textos nuevos se agregan solos a tus archivos; lo que ya cambiaste no se toca.</div>
</div>

<div class="chapter">
<h2>13. Rendimiento y límites</h2>
<ul>
  <li><b>Si nadie ve a un dino, no se calcula ni se manda su animación.</b> La IA, el crecimiento y los huevos siguen
      andando igual.</li>
  <li>Cuanto más lejos está el jugador más cercano, menos seguido se manda la pose. Pensado para distancia de vista 8
      (128 bloques como mucho).</li>
  <li><b>Dinos quietos</b> (parados o sentados sin hacer nada) mandan la pose mucho menos seguido; el cliente interpola,
      así que siguen respirando suave. Al arrancar a moverse vuelven a la frecuencia normal al toque.</li>
  <li>Un hueso que casi no se movió desde la última vez no se manda.</li>
  <li>El cliente deja de dibujarlos según su tamaño (los chicos desaparecen antes que los grandes).</li>
  <li>Los dinos en chunks descargados no consumen nada: solo quedan anotados en <code>dinos.yml</code>.</li>
</ul>
<table><tr><th>Jugador más cercano</th><th>Moviéndose</th><th>Quieto</th></tr>
<tr><td>menos de 16 bloques</td><td>cada 2 ticks</td><td>cada 6</td></tr>
<tr><td>16 a 32</td><td>cada 3</td><td>cada 8</td></tr>
<tr><td>32 a 64</td><td>cada 5</td><td>cada 12</td></tr>
<tr><td>más de 64</td><td>cada 8</td><td>cada 20</td></tr></table>
<h4>/dino stats</h4>
<p>Muestra los dinos cargados (salvajes y domesticados), las entidades que suman, cuántos ve algún jugador y cuántos están
quietos, los huesos mandados y ahorrados por segundo, la red estimada (total y por jugador), cuánto tarda el tick de
DinoEarth, los domesticados totales, quiénes tienen más y la distancia de vista y simulación del server.</p>
<h4>Archivos de datos</h4>
<table><tr><th>Archivo</th><th>Qué guarda</th></tr>
<tr><td><code>dinos.yml</code></td><td>Cada domesticado con su última posición (para <code>/dino mis</code> y llamar de regreso) y
los avisos pendientes de dueños desconectados.</td></tr>
<tr><td><code>misiones.yml</code></td><td>Qué especies se vieron en el server y cuándo (para elegir misiones posibles).</td></tr>
</table>
<p>Lo de cada jugador (Dinopedia, sellos, misiones) se guarda en el propio jugador, no en archivos.</p>
</div>

<div class="chapter">
<h2>14. Problemas comunes</h2>
<table><tr><th>Problema</th><th>Causa y solución</th></tr>
<tr><td>Cuadrados violeta y negro, o letras como cuadraditos en los menús</td><td>El jugador tiene un <b>pack viejo</b>.
Subí el <code>DinoEarth-ResourcePack.zip</code> nuevo, cambiá <code>resource-pack.url</code> y hacé <code>/dino pack reload</code>.
Revisá con <code>/dino pack info</code> que el sha1 sea el de la versión (o usá el modo <code>host</code>).</td></tr>
<tr><td>Los dinos no se ven</td><td>El pack no se cargó: el jugador lo rechazó o falló la descarga. <code>/dino pack</code> lo vuelve a mandar.
Con <code>resource-pack.required: true</code> nadie entra sin él.</td></tr>
<tr><td>"Resource pack: no está listo"</td><td>Falta <code>resource-pack.url</code> o el link no es de descarga directa.
<code>/dino pack info</code> dice el motivo.</td></tr>
<tr><td>No puedo domesticar más</td><td>Llegaste al máximo de domesticados (<code>limits.max-tamed-per-player</code>). Guardá alguno en
una jaula, regalalo, o pedí un permiso <code>dinoearth.limit.&lt;número&gt;</code>.</td></tr>
<tr><td>Un huevo no nace</td><td>Si el dueño tiene el máximo de dinos, el huevo espera listo hasta que haya lugar.</td></tr>
<tr><td>Un dino no me sigue</td><td>Revisá la orden con el silbato (puede estar en "Quedarse" o "Patrullar") o si está muerto de hambre.</td></tr>
<tr><td>No me salen misiones</td><td>Solo se arman con especies vistas en el server en los últimos días. Cuando haya dinos cerca,
al día siguiente aparecen.</td></tr>
</table>
</div>
"""
    doc = f"<!doctype html><html lang=es><head><meta charset=utf-8><title>DinoEarth - Guía</title><style>{CSS}</style></head>" \
          f"<body>{body}</body></html>"
    with open(OUT_HTML, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(doc)
    edge = next((p for p in EDGE if os.path.exists(p)), None)
    if edge is None:
        raise SystemExit("No encontré Edge ni Chrome para imprimir el PDF; abrí guia.html e imprimilo a PDF.")
    import tempfile
    import time
    work = tempfile.mkdtemp(prefix="dinoearth-guia-")      # perfil y salida aparte: un Edge abierto no molesta
    tmp_pdf = os.path.join(work, "guia.pdf")
    subprocess.run([edge, "--headless=new", "--disable-gpu", "--no-pdf-header-footer", "--no-first-run",
                    f"--user-data-dir={os.path.join(work, 'perfil')}", f"--print-to-pdf={tmp_pdf}",
                    "file:///" + OUT_HTML.replace("\\", "/")], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                   timeout=180)
    for _ in range(50):
        if os.path.exists(tmp_pdf) and os.path.getsize(tmp_pdf) > 0:
            break
        time.sleep(0.2)
    if not os.path.exists(tmp_pdf):
        raise SystemExit("Edge no genero el PDF")
    os.replace(tmp_pdf, OUT_PDF)
    print(OUT_PDF)


if __name__ == "__main__":
    build()

# DinoEarth — contexto para Claude

Plugin de dinosaurios para la modalidad **Minecraft Earth** de Angeloww (en el juego: `Angeloww_`).
Proyecto de varios días: se van agregando especies y mejoras. **Hablar en español rioplatense (vos).**

## Entorno real

- Servidor: **UniverseSpigot** (fork de Paper **1.21.11**) detrás de **Velocity** → entran clientes de otras
  versiones (ViaVersion). Usa Towny y WorldGuard (integración pendiente).
- Cliente del dueño: Minecraft **1.21.9**, Fabric, **VulkanMod** (GTX 1050 notebook). VulkanMod dio
  `VK_ERROR_DEVICE_LOST`; por eso **no usar glowing/contornos** en las displays.
- El pack declara formatos 46–99 (1.21.4 en adelante). No usar nada que no exista en 1.21.4.
- En la PC: Java 21 + Maven, Python 3.14. Sonidos: `generator/.venv` con numpy + soundfile
  (en otra máquina: `python -m venv generator/.venv && generator/.venv/Scripts/pip install numpy soundfile`).

## Cómo funciona

- **Sin ModelEngine.** Cada dino = un **lobo invisible** (IA, hitbox, vida, domesticación) + una
  **item_display por hueso**, montadas como pasajeros del lobo. La pose (y el giro del cuerpo) va en la
  matriz de transformación; el cliente interpola.
- El item_display gira el modelo 180° en Y: el generador invierte X/Z alrededor del joint (`to_model`).
  Rotaciones regla de la mano derecha; rx positivo = baja la cabeza / la pata va hacia atrás.
- Límite de Minecraft: ningún cubo a más de 24 px de su joint; ángulos de elemento solo ±22.5/±45.
- Temperamento por especie (`Species.Temper`): CARNIVORE, HERBIVORE, HOSTILE, PACK; se le sacan goals
  de lobo con la API MobGoals de Paper.
- Montura: `Player#getCurrentInput()` + `wolf.setAware(false)` + velocidad manual. Cada especie tiene su modelo de
  montura (hueso `saddle` hijo de `body`, materiales saddle/strap/metal/plate/spike/cloth/wood/rope); se equipa con la
  montura de vanilla (los crafteos los hace el dueño con Shopkeepers).
- Vuelo (`Body.FLYER`, pteranodon): `setGravity(false)` + velocidad propia (`flyVel`, para que el roce del aire no la
  frene). Alas modeladas abiertas; en el suelo se pliegan (brazo = pata delantera, membranas `mem_*` escala 0.12).
  La inclinacion en vuelo va en `model.extra()`. Al guardar/cargar se vuelve a poner la gravedad.
- Rendimiento: pose solo si alguien la ve (`getTrackedBy`), menos seguido de lejos; view range por tamaño.
- Resource pack: el plugin lo manda solo (`PackManager`, modo `url` o `host`). El zip es determinista y
  una copia va dentro del jar (`plugin/src/main/resources/pack/`).

## Estructura

| Ruta | Qué |
|---|---|
| `generator/species_*.py` | Modelo, colores y montura de cada especie (raptor, triceratops, dilophosaurus, compsognathus, trex, pteranodon) |
| `generator/geometry.py` | Cube/Bone/Rig, huevo y materiales de montura (`SADDLE_COLORS`, cada especie los pisa con `saddle_colors`) |
| `generator/build.py` | Texturas + modelos + `DinoEarth-ResourcePack.zip` + rigs (`plugin/src/main/resources/rigs/*.json`) + copia del zip al jar |
| `generator/preview.py` | Render propio a `preview.png` (`python generator/preview.py <especie>`) — usarlo para revisar modelos |
| `generator/sounds.py`, `sounds_species.py`, `synth.py` | Sonidos sintetizados a `.ogg` |
| `plugin/` | Plugin Maven: `species/Species.java` (stats y especies), `dino/Dino.java` (lógica + animación), `dino/DinoListener.java`, `egg/EggManager.java`, `pack/PackManager.java` |
| `DinoEarth-*.jar`, `DinoEarth-ResourcePack.zip` | Último build, para descargar directo desde GitHub |

## Flujo de trabajo

```
python generator/preview.py                       # regenera pack + preview.png
generator/.venv/Scripts/python generator/sounds.py   # solo si cambian sonidos
python generator/build.py                         # zip final (incluye sonidos)
cd plugin && mvn package                          # jar (lleva rigs y pack adentro)
```

Después de compilar: copiar `plugin/target/DinoEarth-<versión>.jar` a la raíz y borrar el jar anterior.
Subir versión en `plugin/pom.xml` y `plugin/src/main/resources/plugin.yml`.
Al cambiar el pack: el dueño sube el zip a mc-packs.net, cambia `resource-pack.url` y hace `/dino pack reload`.

## Estado (2026-10-15, v0.5.0)

- v0.1 (raptor) probado en el juego: modelo, orientación y animaciones confirmados.
- v0.2–v0.4 **sin probar en el juego**: colores, macho/hembra, crías que crecen, montura, huevos, sonidos,
  triceratops/dilophosaurus/compsognathus, rendimiento, `/dino spawn <tipo> <color> <sexo> <edad>`,
  envío automático del pack (modo url con mc-packs.net).
- v0.5 **sin probar ni compilar** (la sesion en la nube no llega a repo.papermc.io): T-Rex (territorial, solo de
  huevo, rugido que asusta, montado espacio = rugir), Pteranodon (vuela montado y solo), monturas propias por especie.
- Pendiente: probar todo en el server; spawn natural por bioma respetando Towny/WorldGuard; más especies
  (Parasaurolophus, Stegosaurus, Mosasaurus); hambre, órdenes, herencia de color.

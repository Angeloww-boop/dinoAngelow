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
- Temperamento por especie (`Species.Temper`): CARNIVORE, HERBIVORE, HOSTILE, PACK, PREY (huye en manada con el
  pathfinder; agachado te acercas); se le sacan goals de lobo con la API MobGoals de Paper. Extras por especie:
  `TailAttack` (SPIKES/CLUB: coletazo al que pega por atras, `Dino.onStruck`), `armored` (proyectiles x0.35),
  `perches` (dimorphodon en el hombro: NO es pasajero del jugador porque eso traba los teleports; se lo sigue con
  velocidad, sin gravedad ni colision).
- Montura: `Player#getCurrentInput()` + `wolf.setAware(false)` + velocidad manual. Cada especie tiene su modelo de
  montura (hueso `saddle` hijo de `body`, materiales saddle/strap/metal/plate/spike/cloth/wood/rope); se equipa con la
  montura de vanilla (los crafteos los hace el dueño con Shopkeepers).
- Vuelo (`Body.FLYER`, pteranodon): `setGravity(false)` + velocidad propia (`flyVel`, para que el roce del aire no la
  frene). Alas modeladas abiertas; en el suelo se pliegan (brazo = pata delantera, membranas `mem_*` escala 0.12).
  La inclinacion en vuelo va en `model.extra()`. Al guardar/cargar se vuelve a poner la gravedad.
- Hambre (solo domesticados, `Dino.tickHunger`): 0..1 en el PDC, baja en `hunger-minutes`. En 0 se echa
  (`starvingSit`), cabeza/cola caidas, no se monta, no crece; nunca muere. Con hambre busca un comedero de su
  dieta (`Species.Diet`) a 16 bloques con el pathfinder y come.
- Comedero (`feeder/FeederManager`): Interaction + item_display como los huevos, **sin inventario** (la comida se
  convierte en puntos en el PDC: sin dupes). Poner/romper consulta Towny/WG con un BlockPlaceEvent de prueba
  (`Protection`). Dinopedia (`dex/DexManager`): libro → menu de solo lectura (todo click cancelado, se reconoce por
  `InventoryHolder`); lo descubierto va en el PDC del jugador. Iconos: `generator/icons.py` con el render de preview.
- Rendimiento: pose solo si alguien la ve (`getTrackedBy`), menos seguido de lejos; view range por tamaño.
- Resource pack: el plugin lo manda solo (`PackManager`, modo `url` o `host`). El zip es determinista y
  una copia va dentro del jar (`plugin/src/main/resources/pack/`).

## Estructura

| Ruta | Qué |
|---|---|
| `generator/species_*.py` | Modelo, colores y montura de cada especie (13: raptor, triceratops, dilophosaurus, compsognathus, trex, pteranodon, parasaurolophus, gallimimus, stegosaurus, ankylosaurus, giganotosaurus, carnotaurus, dimorphodon) |
| `generator/geometry.py` | Cube/Bone/Rig, huevo y materiales de montura (`SADDLE_COLORS`, cada especie los pisa con `saddle_colors`) |
| `generator/feeder.py`, `generator/icons.py` | Modelo del comedero; iconos de la Dinopedia (los llama build.py) |
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

`build.py` solo reescribe lo que cambió (los PNG se comparan por píxeles: el zlib de Windows y el de Linux
comprimen distinto). Así regenerar en otra máquina no cambia el hash del pack si el contenido es el mismo.
Si la nube no llega a `repo.papermc.io`, el jar se compila en la PC del dueño.

Después de compilar: copiar `plugin/target/DinoEarth-<versión>.jar` a la raíz y borrar el jar anterior.
Subir versión en `plugin/pom.xml` y `plugin/src/main/resources/plugin.yml`.
Al cambiar el pack: el dueño sube el zip a mc-packs.net, cambia `resource-pack.url` y hace `/dino pack reload`.

## Estado (2026-10-15, v0.7.0)

- v0.1 (raptor) probado en el juego: modelo, orientación y animaciones confirmados.
- v0.2–v0.4 **sin probar en el juego**: colores, macho/hembra, crías que crecen, montura, huevos, sonidos,
  triceratops/dilophosaurus/compsognathus, rendimiento, `/dino spawn <tipo> <color> <sexo> <edad>`,
  envío automático del pack (modo url con mc-packs.net).
- v0.5 **sin probar en el juego**: T-Rex (territorial, solo de huevo, rugido que asusta, montado espacio = rugir),
  Pteranodon (vuela montado y solo), monturas propias por especie.
- v0.6 **sin probar en el juego**: hambre, comedero por puntos, Dinopedia.
- v0.7 **sin probar en el juego**: 7 especies nuevas (para, galli, stego, anky, giga, carno, dimorphodon), presas,
  colas, hombro.
- v0.5–v0.7 se escribieron en la nube (sin acceso a repo.papermc.io). El 2026-10-15 se compilaron en la PC contra el
  paper-api 1.21.11 real **sin errores**: el jar de la raiz es ese build (0.7.1: /dino pack info muestra por que no esta listo el pack; 0.7.2: el config.yml viejo se actualiza solo y queda config-anterior.yml).
- Pendiente: probar todo en el server; posible hook de Vulcan (VulcanFlagEvent) si salta Flight/Reach con los dinos; spawn natural por bioma respetando Towny/WorldGuard; más especies
  (Mosasaurus/Spinosaurus acuaticos, Brachiosaurus con varios jinetes); órdenes, herencia de color.

# DinoEarth — contexto para Claude

Plugin de dinosaurios para la modalidad **Minecraft Earth** de Angeloww (en el juego: `Angeloww_`).
Proyecto de varios días: se van agregando especies y mejoras. **Hablar en español rioplatense (vos).**

## Entorno real

- Servidor: **UniverseSpigot** (fork de Paper **1.21.11**) detrás de **Velocity** → entran clientes de otras
  versiones (ViaVersion). Usa Towny y WorldGuard (integración pendiente).
- Cliente del dueño: Minecraft **1.21.9**, Fabric, **VulkanMod** (GTX 1050 notebook). VulkanMod dio
  `VK_ERROR_DEVICE_LOST`; por eso **no usar glowing/contornos** en las displays.
- El pack declara formatos 46–99 (1.21.4 en adelante). No usar nada que no exista en 1.21.4.
- Con la 0.7 (13 especies) el cliente con VulkanMod empezó a mezclar texturas (mobs, menús e inventario con
  rayas de los dinos). En 0.7.4 las texturas se empaquetan ajustadas (rectángulos múltiplos de 16, no cuadrados
  potencia de 2) y los íconos son 32x32: el pack ocupa ~42 % de la memoria de video de antes. Mantenerlo chico.
- En la PC: Java 21 + Maven, Python 3.14. Sonidos: `generator/.venv` con numpy + soundfile
  (en otra máquina: `python -m venv generator/.venv && generator/.venv/Scripts/pip install numpy soundfile`).

## Estilo OBLIGATORIO de textos y menús (pedido por Angeloww, 0.13)

- **Prohibido hardcodear texto visible en el código.** Todo va en YML: `messages.yml` (secciones `chat`, `actionbar`,
  `bossbar`), `textos.yml` (palabras sueltas: especies, colores, órdenes, causas, tiempos, misiones...), `items.yml`
  (nombre/lore de items; lo genera `generator/items.py`), `menus/*.yml`. Se leen con `text/Lang` (`Lang.get()`):
  `chat`, `action`, `bossbar`, `plain/text`, `itemTexts`, `menu("nombre")` (`text/Menu`). Placeholders `%clave%`
  (valores texto, Component o lista de lineas para el lore). Al agregar claves nuevas, `Lang` las suma solas al
  archivo del dueño. Los logs de consola sí pueden quedar en el código.
- Español, **sin "¡" ni "!" en ningún lado**. Chat termina en punto blanco (`&f.` solo si lo anterior no era blanco).
- Colores: texto `&f`, variables/nombres `&#ff00d9`, símbolos y separadores `&8`, prefijo `&9&lDINOEARTH &8»`.
  Actionbar `&8| &fTexto &#ff00d9variable&f texto.`; bossbar `&#ff00d9var &ftexto &8- &ftexto &#ff00d9var`.
- Menús: `menu_title: '&8| &9Nombre'`, `size: 54`, `items:` con `relleno` (black_stained_glass_pane en 0-9, 17, 18,
  26, 27, 35, 36-44, 45-53), lore con `'&8| &f...'` y separadores `'&f'` (nunca vacío), barrier en el 49 (`Volver` si
  es submenu, `Cerrar` si vuelve al juego, color `&#EE193B`, sin lore), indicadores `'&#41FF00✔ Click para abrir'` /
  `'&#EE193B✘ Click para remover'`. La Dinopedia es la excepción acordada (fondo dibujado, sin titulo de texto ni
  relleno) pero sus items siguen el formato.
- Claves YAML: nunca `no`, `si`, `yes`, `on`, `off`, `y`, `n` sin comillas (SnakeYAML las lee como booleanos).
- Para revisar: `TextCheck` (plugin/src/test) parsea todos los textos con Lang.parse y avisa códigos sin entender o ¡/!,
  y revisa que cada `chat/action/bossbar("clave")` del código tenga su texto en ESA sección de messages.yml (en 0.17.0
  los de /dino mapa quedaron en actionbar y en el juego salía "chat.mapa").

## Cómo funciona

- **Sin ModelEngine.** Cada dino = un **lobo invisible** (IA, hitbox, vida, domesticación) + una
  **item_display por hueso**, montadas como pasajeros del lobo. La pose (y el giro del cuerpo) va en la
  matriz de transformación; el cliente interpola.
- El item_display gira el modelo 180° en Y: el generador invierte X/Z alrededor del joint (`to_model`).
  Rotaciones regla de la mano derecha; rx positivo = baja la cabeza / la pata va hacia atrás.
- Límite de Minecraft: ningún cubo a más de 24 px de su joint; ángulos de elemento solo ±22.5/±45.
- **Nunca usar `Display#setTransformationMatrix`**: Minecraft la descompone con SVD y para rotación + escala pareja
  la división en rotación izquierda/derecha salta de un tick a otro (hasta ~180°) → las piezas tiemblan.
  `DinoModel.decompose` arma traslación + una sola rotación + escala (0.7.7).
- **El giro se saca con `getUnnormalizedRotation(...).normalize()`** (`DinoModel.rotationOf`), nunca con
  `getNormalizedRotation`: ese supone una matriz SIN escala, y con escala ≠ 1 (dimorphodon, crías, genes) daba un
  cuaternión deformado de largo ≠ 1; el slerp del cliente lo inflaba a mitad de cada interpolación (hasta +25 %) y los
  dinos "respiraban agitados" en cada envío de pose (0.16.1). `PoseCheck` (plugin/src/test) lo prueba con el mismo
  JOML que el cliente. Altura de montura: sale del atributo SCALE, no de `getHeight()` (que tarda un tick).
- Z-fighting: caras de cubos distintos en el mismo plano titilan. `geometry.defight` (lo llama build.py) empuja
  0.08 px la cara del cubo más chico; `python generator/zfight.py` confirma que no queda ninguna. Partes que se
  pliegan adentro de otras (membranas, gola) se ocultan con `BonePose.hidden` en vez de achicarlas al mínimo.
- Temperamento por especie (`Species.Temper`): CARNIVORE, HERBIVORE, HOSTILE, PACK, PREY (huye en manada con el
  pathfinder; agachado te acercas); se le sacan goals de lobo con la API MobGoals de Paper. Extras por especie:
  `TailAttack` (SPIKES/CLUB: coletazo al que pega por atras, `Dino.onStruck`), `armored` (proyectiles x0.35),
  `perches` (dimorphodon compañero volador, 0.12: NO es pasajero del jugador porque eso traba los teleports;
  `Dino.tickEscort` vuela con velocidad propia hacia un punto al costado del jugador + su velocidad medida x 5 ticks,
  asi no queda atras; muy lejos o trabado = teleport al lado. Despega solo siguiendo si el dueño se aleja 10 bloques).
- Montura: `Player#getCurrentInput()` + `wolf.setAware(false)` + velocidad manual. Cada especie tiene su modelo de
  montura (hueso `saddle` hijo de `body`, materiales saddle/strap/metal/plate/spike/cloth/wood/rope); se equipa con el
  item montura de SU especie (items.json; la de vanilla no sirve; los crafteos los hace el dueño con Shopkeepers).
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
- Menus dibujados (0.10): el dibujo va en el **titulo** del menu de cofre de 6 filas (176 x 222, el titulo arranca en
  (8, 6)). `generator/gui.py` hace fuentes: `gui_img` (fondos y fotos 64x64 de cada color como letras), `gui_space`
  (espacios +-2^n para mover el lapiz) y `gui_t<y>` (renglon: copia de ascii.png/accented.png de vanilla corrida a la
  altura y, mas iconitos y barritas). Verificado en el bytecode de 1.21.9: borde de arriba de la letra = y + 7 - ascent,
  avance = (int)(0.5 + ancho real * escala) + 1, y el menu dibuja el titulo ANTES que los items (quedan arriba).
  ascent nunca mayor que height (por eso las celdas de 8 px). `gui.json` le dice al plugin la letra y el avance de cada
  cosa; `gui/GuiFont` arma el titulo; `dex/DexLayout` dibuja las 3 pantallas sin tocar Bukkit. Para revisar sin el juego:
  `GuiPreview` (plugin/src/test) arma titulos de ejemplo y `generator/gui_preview.py` los dibuja con las fuentes del pack
  (necesita el jar del cliente en .minecraft y Pillow en .venv). Si Mojang cambia la fuente: `generator/font_widths.py`.
- Sellos de la Dinopedia (`DexManager.Stamp`): por especie segun lo que tenga (raro, domesticable, huevo, montura,
  hombro); en el PDC del jugador (`dex_stamps_<especie>`). XP y comandos en `dinopedia.*` del config. Con el libro en la
  mano: descubrir a 32 bloques, huellas de especies no vistas, lupa = flecha en la barra de accion (fuente `hud`).
- Rendimiento: pose solo si alguien la ve (`getTrackedBy`), menos seguido de lejos (escalones 16/32/64 bloques: el
  server tiene distancia de vista 8) y mucho menos si esta quieto (`Dino.isStill`: sin jinete, sin caminar, sin
  animacion en curso; al arrancar a moverse manda pose al toque). `DinoModel` no manda un hueso que casi no cambio
  desde lo ultimo mandado (`same`), y cuenta lo mandado/ahorrado para `/dino stats`; view range por tamaño.
- Limite de domesticados (`DinoManager.tamedLimit/canOwnMore`, cuenta con `DinoRegistry`): comida, regalo aceptado,
  jaula soltada y huevos (esperan listos). `/dino tame` de admin no tiene limite.
- Genetica (0.14, `dino/Genes`): rasgos vida/velocidad/tamaño (multiplican stats en `updateStats`/`modelScale`/montura),
  generacion, mutacion y padres. Mismas claves PDC en lobo, item de huevo, huevo puesto y jaula (`Genes.write/read/copy`).
  La cria se decide al poner el huevo (`DinoListener.onBreed`: `Genes.childColor` + `Genes.child`); el item lleva
  `egg_variant`. Dinos viejos sin genes: domesticados = neutros, salvajes = al azar. Ficha: item `ficha-genes` (slot 51).
- Nidos (0.14, `nest/NestManager`): Interaction + displays (nido + 3 huevos escalados por `DinoRig.eggSize`). Cada
  minuto una hembra adulta salvaje con huevo (sin cooldown) puede armar uno; la madre (`Dino.provoke`) ataca al que se
  acerca o roba (las PREY huyen). Golpe = sacar huevo (genes de la madre + padre salvaje). Solo en naturaleza
  (`integration/Wilderness`: Towny/WorldGuard por reflexion). Es la forma natural de conseguir T-Rex/Giga.
- Nado (0.14, `Species.swims`, `Body.SWIMMER`): como el vuelo, en el agua `setGravity(false)` + `setAware(false)` +
  `flyVel` (`swimAlone`, `rideSwim`, `steer`); la IA del lobo no corre, asi que la mordida (`bite`) y el contraataque
  se hacen a mano. Nunca se ahogan (aire al maximo + se cancela DROWNING). El acuatico (mosasaurus) en tierra queda
  varado (`beached`: busca agua con el pathfinder) y solo caza a jugadores que nadan. Montado: respiracion acuatica.
- Integraciones (0.14, `integration/`): PlaceholderAPI (`DinoPlaceholders`, unica dependencia de compilacion: pom con
  repo extendedclip, scope provided; se registra solo si el plugin esta), Vault (`Money`, por reflexion). Tops:
  `stats/PlayerStats` (jugadores.yml; dinos y genes salen de `DinoRegistry`, que ahora guarda el puntaje de genes),
  `/dino top`.
- Spawn natural (0.17, `spawn/NaturalSpawner` + `spawn/EarthMap`): el server es un **mapa de la Tierra a escala**
  (equirectangular de 73728 x 36864 bloques, de -36864 -18431 a 36863 18431: 204.8 bloques por grado, centro en 0 0,
  1 bloque ~ 543 m; en `natural-spawn.map`). Cada especie aparece en la region real de sus fosiles (cajas de latitud/longitud en
  `natural-spawn.regions`, se suman si se superponen), el mosasaurus en agua honda de cualquier lado (`ocean`) y fuera
  de las regiones unas comunes (`elsewhere`, menos seguido). Cerca de jugadores en survival, a sus espaldas, solo en
  chunks cargados y en naturaleza (`Wilderness`), con topes por jugador y total. Marcados con `keys.natural`; si nadie
  se acerca en `despawn-minutes` desaparecen (sweep cada 20 s, y al volver a cargar un chunk abandonado por
  `EntitiesLoadEvent`, con `natural_seen` en el PDC). Nunca los domesticados, atados, prestados ni madres con nido.
  `/dino mapa` (admin) dice latitud/longitud, region y que aparece. `MapCheck` prueba la cuenta con ciudades reales.
  Regiones (0.19): 8 de fosiles reales + 22 "repartidas por continente" (pedido de Angeloww: que haya dinos en todo el
  mapa; aprovechan parientes reales: tiranosaurio en Alaska/China, pariente del Giga en Africa oriental, del Carnotaurus
  en India/Madagascar). `city-distance` (150): nada a menos de esos bloques de un pueblo de Towny o region de
  WorldGuard; `Wilderness` copia cada minuto los terrenos de Towny (TownyAPI.getTownBlocks, por reflexion) y las cajas
  de las regiones (RegionManager.getRegions, sin __global__) y cada consulta son ~400 busquedas en un HashSet
  (`CityCheck` prueba las cuentas). /dino mapa dice cuantos terrenos/regiones conoce: si da 0 y hay pueblos, fallo la
  reflexion. El menu de regiones del mapa tiene paginas y va de la mas cerca a la mas lejos.
  Para apagar una region: pesos en 0 (si se borra, el ConfigUpdater la vuelve a agregar).
- Mapa del Paleontologo (0.18, `spawn/PaleoMap`): FILLED_MAP con un solo MapView compartido (id en `mapa.yml`, se
  reusa al reiniciar; renderers de vanilla sacados, locked) -> todos los items son iguales (Shopkeepers). Renderer
  contextual: dibuja `mapa/tierra.png` + una huella roja (5 x 3) en el centro de cada region del config UNA vez por
  jugador (y otra vez tras /dino reload) y despues solo mueve la flecha (formula de vanilla). Estilo de la Dinopedia
  (0.19, a pedido: los cuadrados rosas y los titulos con la fuente de los mapas quedaban feos): papel, sepia, costa en
  tinta, cinta y borde de cuero cosido, rastro de huellas y rosa de los vientos; SOLO colores de la paleta de los
  mapas (si no, el server los convierte y el papel sale manchado; paleta en MapPalette de Bukkit). El mundo va en
  x 6..121, y 35..92 (116 x 58): mismas constantes en paleomap.py (WX..WH) y PaleoMap.java (WORLD_*).
  La imagen sale de `generator/mapa/tierra.webp` (render del mapa del server, 4 px de borde negro a los costados) con
  `generator/.venv/Scripts/python generator/paleomap.py`.
  En la mano: barra de JEFE propia del mapa cada 0,5 s (lat/lon, region o la mas cercana con distancia y rumbo; se
  llena al acercarse a la zona). No usar la barra de accion: Towny la usa ("~ Exterior - PVP") y se pisaban (0.19.3). Click derecho: menu
  `menus/regiones.yml` (solo lectura, Holder). Se da con `/dino item mapa`. `MapPreview` (test) lo dibuja con la paleta
  de los mapas de Bukkit para revisarlo sin el juego.
- Chunk que se desactiva (jugador que se aleja): Paper dispara `EntityRemoveFromWorldEvent` con el lobo VIVO y no deja
  borrar las partes en ese momento (avisaba una vez por parte). `DinoListener.onRemove` ya no las borra si el lobo no
  esta muerto (no se guardan: se van con el chunk), y `Dino.buildModel` saca las partes viejas que el lobo todavia
  lleva encima si el chunk se reactiva sin descargarse; antes eso dejaba un modelo duplicado congelado (0.17,
  probado en un Paper 1.21.11 local: 60 partes viejas borradas al reactivar, 0 avisos).
- Resource pack: el plugin lo manda solo (`PackManager`, modo `url` o `host`). El zip es determinista y
  una copia va dentro del jar (`plugin/src/main/resources/pack/`).

## Estructura

| Ruta | Qué |
|---|---|
| `generator/species_*.py` | Modelo, colores y montura de cada especie (15: raptor, triceratops, dilophosaurus, compsognathus, trex, pteranodon, parasaurolophus, gallimimus, stegosaurus, ankylosaurus, giganotosaurus, carnotaurus, dimorphodon, spinosaurus, mosasaurus) |
| `generator/geometry.py` | Cube/Bone/Rig, huevo y materiales de montura (`SADDLE_COLORS`, cada especie los pisa con `saddle_colors`) |
| `generator/feeder.py`, `generator/nest.py`, `generator/icons.py` | Modelo del comedero y del nido; iconos de la Dinopedia (los llama build.py) |
| `generator/gui.py`, `gui_preview.py`, `font_widths.py` | Menus dibujados de la Dinopedia (ver arriba) |
| `generator/paleomap.py` | Imagen de la Tierra del Mapa del Paleontologo (`plugin/src/main/resources/mapa/tierra.png`, necesita Pillow de .venv) |
| `generator/items.py` | Items propios: drops por especie, monturas como item, silbato, jaula. Dibuja sus iconos y escribe `plugin/src/main/resources/items.json` (el plugin lee todo de ahí) |
| `generator/build.py` | Texturas + modelos + `DinoEarth-ResourcePack.zip` + rigs (`plugin/src/main/resources/rigs/*.json`) + copia del zip al jar |
| `generator/preview.py` | Render propio a `preview.png` (`python generator/preview.py <especie>`) — usarlo para revisar modelos |
| `generator/sounds.py`, `sounds_species.py`, `synth.py` | Sonidos sintetizados a `.ogg` |
| `generator/logo.py`, `rexlit.py` | Logo, banner e icono de DinoEarth (`docs/logo/`) con el T-Rex iluminado |
| `generator/video.py` | Trailer de renders (todas las especies + Plus + items), horizontal y vertical (`docs/video/`) |
| `generator/ingame.py`, `mc3d.py`, `mchud.py` | Video "como si fuera in game" (`docs/video/dinoearth-ingame.mp4`): motor 3D numpy en perspectiva con mundo de bloques y los modelos reales, HUD/chat/menus de Minecraft con los textos reales del plugin. Simulado: sin cliente en la nube. `python generator/ingame.py preview`, `frame <seg>`, `audio` (rearma solo el sonido) |
| `docs/prueba-de-carga.md`, `docs/video/guion-grabacion.md` | Guia de la prueba de carga con bots; guion para grabar el video en el server |
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
  paper-api 1.21.11 real **sin errores**: el jar de la raiz es ese build (0.7.1: /dino pack info muestra por que no esta listo el pack; 0.7.2: el config.yml viejo se actualiza solo y queda config-anterior.yml; 0.7.3: /dino tame [jugador] domestica el dino que mirás, incluso los que solo nacen de huevo; 0.7.4: texturas ajustadas; 0.7.5: sin z-fighting, pack sha1 8c5971bd…; 0.7.6: muerte sin temblor, sigue montado y cae apoyado de costado; 0.7.7: arreglo real del temblor de piezas quietas/sentadas, ver setTransformationMatrix).
- v0.8.0 **sin probar en el juego**: drops propios por especie, montura = item por especie (la de vanilla no sirve; los
  crafteos los arma el dueño con Shopkeepers), silbato (seguir/quedarse/patrullar el chunk del jugador + pasivo/defensivo/
  agresivo; patrullar saca el goal FOLLOW_OWNER y lo vuelve a poner con MobGoals: verificar que vuelva a seguir),
  nombre con etiqueta + cartel TextDisplay, `/dino dar` con aceptar/rechazar, jaula que guarda/suelta el dino.
- v0.9.0 **sin probar en el juego**: `registry/DinoRegistry` (dinos.yml con la ultima posicion de cada domesticado;
  se actualiza cada 10 s, al descargarse el chunk por EntityRemoveEvent.UNLOAD y se borra por DEATH/PLUGIN),
  `/dino mis`, silbato de regreso (`RecallManager`: max 2 por llamada, 1 vez cada 5 min; dino en chunk descargado =
  getChunkAtAsync + ticket del plugin solo de ese chunk hasta encontrarlo; `Dino.prepareTeleport` saca el modelo
  porque un lobo con pasajeros no se teletransporta), avisos de ataque (lejos, cada 30 s) y de muerte (guardados si
  el dueño no esta conectado).
- v0.10.0 **sin probar en el juego**: Dinopedia con menus dibujados (indice, pagina de especie con foto grande, ficha
  de cada dino), sellos de investigacion con XP/rangos/comandos, huellas y lupa de rastreo con el libro en la mano.
  Las pantallas se revisaron con gui_preview.py (mismo titulo que arma el plugin, dibujado con las fuentes del pack).
- v0.11.0 **sin probar en el juego**: dinos quietos mandan pose mas espaciada, huesos sin cambios no se mandan,
  `/dino stats`, limite de domesticados por jugador (`limits.max-tamed-per-player` + permisos dinoearth.limit.N).
  El pack no cambio (sha1 a471063d…).
- v0.12.0 **sin probar en el juego**: el dimorphodon ya no va en el hombro, vuela al lado del dueño (simulado en
  Python con la misma cuenta: queda ~1,5 bloques al costado a cualquier velocidad, nunca mas de 3 atras).
- v0.13.0 **sin probar en el juego**: todos los textos y menús pasan a YML con el estilo del server (ver arriba),
  `/dino reload`, menú de llamar con páginas (21 por página) y Volver/Cerrar; misiones diarias personales
  (`mission/MissionManager`: 3 por jugador, solo especies vistas en el server en `recent-days`, 1 cambio por día,
  premios en `missions.*`, PDC del jugador + `misiones.yml` con las especies vistas) en la Dinopedia y `/dino misiones`.
- v0.14.0 **sin probar en el juego**: genetica (rasgos, color heredado, mutacion, padres y generacion en la ficha),
  nidos salvajes con madre, spinosaurus (anfibio) y mosasaurus (acuatico) con nado y montura bajo el agua,
  PlaceholderAPI + Vault + Towny/WG (nidos) + `/dino top`. Escrito en la nube: compila contra las stubs (scratchpad)
  sin errores, pero el jar real hay que armarlo en la PC (`mvn package`, baja tambien placeholderapi 2.11.6 de
  repo.extendedclip.com). Sonidos nuevos generados en Linux (los viejos se restauraron con git para no cambiar el hash).
- v0.15.0 **sin probar en el juego**: carreras parejas (`race/RaceManager`): pistas en carreras.yml (largadas +
  puertas en orden, la ultima es la meta; `Gate.crossed` = segmento tick a tick contra la caja, probado en `RaceCheck`),
  dinos prestados (`keys.rental`, no persistentes, invulnerables, genetica neutra, fuera del registro; DinoListener y el
  silbato no los dejan tocar), `Dino.setFrozen` en la largada, `Dino.startRacing` = estamina, inscripcion por NPC de
  FancyNpcs (comando `dino carrera unirse <pista>`), programadas con anuncio, premios por puesto, records de siempre y
  de la semana, mision CARRERA, sello CORREDOR (solo especies que presta alguna pista; 7.o slot sobre el lomo, 40),
  top `carreras` y placeholders `record_<pista>`/`recordista_<pista>`.
- v0.16.0 **sin probar en el juego**: linea Plus (`generator/plus.py`: misma forma que la base + paleta negro/rojo +
  pieza extra, una sola variante "carmesi"; Species.*_PLUS con `plusOf`/`ability`/`soundId`, eggTameOnly, sin nidos),
  `plus/PlusAbilities` (activas montado con click derecho con item o F; pasivas cosecha/pesca/iman; respeta Protection),
  `Dino.dash/boost/launch`, `/dino egg <especie> [cantidad] [jugador]` desde consola. Atlas +14 %.
- v0.16.1: arreglo del "respiran agitados" / patas que suben y bajan al aparecer (ver `rotationOf` arriba; reproducido
  con un Paper 1.21.11 local que registraba cada pose mandada; los dinos con escala ≠ 1 ahora se ven del tamaño
  diseñado: dimorphodon ~60 % más grande que antes, los de 0.85 ~12 % más, mosasaurus/giga/spino/T-Rex algo más chicos),
  primera pose sin corrimiento (altura de montura por escala), misiones de avistar: los dinos ya contados se guardan en
  el PDC del jugador (`missions_seen`) y no vuelven a contar al salir/entrar o pasar por el lobby (`SeenCheck`).
- v0.17.0: spawn natural por regiones reales del mapa de la Tierra (ver arriba) y arreglo de modelos duplicados al
  reactivarse un chunk. Probado en un Paper 1.21.11 local sin jugadores (`spawnAround` desde un plugin de prueba): cada
  region da solo sus especies, el oceano solo mosasaurus, desaparecen solos y al recargar un chunk abandonado.
  Falta en el juego: el spawn con jugadores de verdad.
- v0.17.1: los textos de `/dino mapa` estaban en la sección actionbar de messages.yml (salía "chat.mapa"); pasan a chat.
  Al actualizar, Lang los agrega solos al messages.yml del server (los `actionbar.mapa*` viejos sobran, se pueden borrar).
- v0.18.0: Mapa del Paleontologo (ver arriba). Probado en un Paper local: el mapa se crea, se reusa el mismo id al
  reiniciar, los items son iguales; la vista previa con la paleta real se reviso. Falta en el juego: verlo en la mano,
  la barra de accion y el menu con un jugador.
- v0.19.0: 22 zonas nuevas por continente, distancia minima a pueblos/regiones (150), menu de regiones con paginas,
  mapa con estilo de la Dinopedia, icono nuevo de la Dinopedia (libro de cuero con ambar, `icons.book`; tambien el
  boton del libro del menu). Pack sha1 0a4549e6… (solo cambiaron dinopedia.png y gui/book.png).
  Falta en el juego: que /dino mapa muestre los terrenos de Towny y regiones de WorldGuard (la reflexion no se pudo
  probar: no estan en el server de prueba local).
- v0.19.1: `/dino mapa probar` (prueba 30 lugares a la espalda sin spawnear y cuenta por que se descarta cada uno:
  chunk, suelo, protegido, ciudad, especies; avisa si el jugador no esta en survival/aventura o si se llego al tope, y
  nombra las regiones de WorldGuard donde se descarto). `natural-spawn.ignored-regions`: regiones de WorldGuard que no
  cuentan como protegidas ni como ciudad (servers de Earth con una region que cubre todo el mapa). Angeloww no vio
  aparecer ninguno con interval 1 y chance 100: la causa todavia no esta confirmada (sospechas: modo creativo o una
  region de WorldGuard en todo el mapa).
- v0.19.2: misiones sin especies en un server nuevo (solo salian de las vistas hace poco): ahora tambien salen de
  las que pueden aparecer cerca (`NaturalSpawner.speciesNear`: zonas a menos de `missions.region-radius` bloques,
  comunes y del agua), y un dia que quedo sin misiones se vuelve a armar solo (como mucho una vez por minuto).
- 2026-10-19: Angeloww confirmo que el spawn natural ya funciona en el server de prueba. Pendiente para la apertura:
  medir con spark con jugadores reales (rendimiento del spawn, las poses y los nidos con gente).
- v0.20.0 **sin probar en el juego**: Ankylosaurus Leñador con animacion al talar (`Dino.chop`/`animateChop`: se
  para de manos girando sobre las patas de atras con `model.extra()`, cabezazo y latigazo de la maza; el arbol cae en
  `Dino.CHOP_IMPACT` = tick 10, revisado con el mismo calculo en Python). Triceratops Cosechador = tractor
  (`PlusAbilities.tractor`, cada `plus.cosecha.tractor-every-ticks`): montado o siguiendo al dueño cosecha y replanta
  en 3x3 (`tractor-radius`) y lo da al inventario del jinete/dueño; tambien bayas; no pisotea la tierra arada
  (`EntityInteractEvent` cancelado). Los textos nuevos de la Dinopedia no pisan los del textos.yml del server: si se
  quieren, cambiarlos a mano (plus-COSECHA, plus-TALA, triceratops_plus.descripcion).
  `/dino bench <2,5,10> [min] [radio]` (`bench/BenchManager`): prueba de carga con los jugadores/bots conectados,
  por etapas; dinos no persistentes alrededor de cada jugador (15 por tick), 10 s de arranque y una muestra cada 10 s
  (tick de DinoEarth, MSPT/TPS, huesos y red estimada) a `plugins/DinoEarth/bench/*.csv`; guia en
  `docs/prueba-de-carga.md` (bots con SoulFire en el server de prueba en offline + spark). Pendiente: correrla.
- Pendiente: probar todo en el server; posible hook de Vulcan (VulcanFlagEvent) si salta Flight/Reach con los dinos; más especies
  (Brachiosaurus con varios jinetes).

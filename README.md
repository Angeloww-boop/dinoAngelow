# DinoEarth

Dinosaurios para la modalidad Earth (Paper 1.21.11; el pack funciona en clientes 1.21.4 – 1.21.11).

## Especies

| Especie | Tamaño | Carácter | Se domestica con | Montura | Crías / huevo | Colores |
|---|---|---|---|---|---|---|
| **Velociraptor** | 2 × 3,6 bloques | Neutral, caza animales chicos | Carne cruda (25 %) | Sí, rápido | Sí | arena, gris, verde, rojizo, oscuro, albino |
| **Triceratops** | 2,2 × 5,3 bloques | Herbívoro, embiste si lo atacan | Trigo, zanahoria, manzana, remolacha, bayas, sandía, fardo (20 %) | Sí, lento; **corriendo arrolla** lo que tenga adelante | Sí | pardo, oliva, pizarra, rojizo, albino |
| **Dilophosaurus** | 2 × 3,3 bloques | **Hostil**: abre la gola y **escupe veneno** (ceguera + veneno) | — | No | No | amarillo, verde, pardo, albino |
| **Compsognathus** | 0,6 × 1,4 bloques | Anda en grupos de 5; ataca si estás **herido** o si son 4 o más | — | No | No | verde, pardo, gris, albino |
| **Tyrannosaurus rex** | 3,4 × 7,3 bloques | Salvaje **te caza** y **ruge**: lentitud + oscuridad a los que estén cerca | **Solo criándolo desde el huevo** | Sí; **espacio = rugido** (no salta) | Sí | pardo, oscuro, verde, rojizo, albino |
| **Pteranodon** | 2 bloques de alto, 6,5 de envergadura | Neutral; sale a **volar en círculos** y aterriza; herido se escapa volando | Pescado crudo: bacalao, salmón, pez tropical (20 %) | Sí, **vuela** | Sí | gaviota, pardo, pizarra, celeste, albino |
| **Parasaurolophus** | 2,4 × 5,2 bloques | **Presa**: anda en manada y huye; el que se asusta **trompetea** y corren todos | Plantas (15 %), acercándote **agachado** | Sí | Sí | verde, pardo, azul, rojizo, albino |
| **Gallimimus** | 2,2 × 3,7 bloques | **Presa**: huye en bandada | Plantas (15 %), acercándote **agachado** | Sí, **la más rápida** | Sí | arena, gris, verde, naranja, albino |
| **Stegosaurus** | 2,4 × 4,5 bloques | Herbívoro; **coletazo con púas** al que le pega por atrás | Plantas (15 %) | Sí, lenta | Sí | oliva, pardo, gris, rojizo, albino |
| **Ankylosaurus** | 1,6 × 4,3 bloques | Herbívoro **acorazado** (las flechas le hacen 35 %); **maza** en la cola que tira lejos | Plantas (15 %) | Sí, muy lenta | Sí | pardo, musgo, piedra, arcilla, albino |
| **Giganotosaurus** | 3,3 × 7,6 bloques | Como el T-Rex: salvaje te caza y ruge; un poco más rápido | **Solo criándolo desde el huevo** | Sí; espacio = rugido | Sí | arena, gris, rojizo, verde, albino |
| **Carnotaurus** | 2,4 × 4,4 bloques | Carnívoro neutral, muy rápido; montado y corriendo **embiste** | Carne cruda (12 %) | Sí, rápida | Sí | rojo, pardo, gris, verde, albino |
| **Dimorphodon** | 0,7 bloques, 2 de ala a ala | Pterosaurio chiquito; vuela y aterriza | Pescado crudo (33 %) | No: **va en tu hombro** y chilla si hay monstruos cerca | Sí | loro, tucán, pardo, cielo, albino |

El albino es raro (3 %). Los machos son un poco más grandes y más coloridos
(cresta en el raptor, cuernos más largos y gola con rayos en el triceratops, crestas más altas en el dilophosaurus,
cuernitos de color en el T-Rex, cresta larga en el pteranodon).

Cada especie montable tiene **su propia montura** (se pone con la montura común):
raptor = de carrera con manta roja · triceratops = de carga con baranda de madera y alforjas ·
T-Rex = de guerra con placas de hierro y púas de hueso · pteranodon = arnés de vuelo con manija.

## Cómo se juega

| Acción | Cómo |
|---|---|
| Domesticar | Click derecho con su comida (ver tabla) |
| Sentar / parar | Click derecho (agachado si tiene montura) |
| Curar | Con su comida (carnívoros: carne; herbívoros y presas: plantas; pteranodon y dimorphodon: pescado) |
| Montura | Cada especie tiene **su propia montura** (item `montura_<especie>`; la de vanilla no sirve). Click derecho con ella al dino adulto (solo el dueño), click derecho para subir. WASD, Ctrl corre, espacio salta, Shift baja. Tijeras: te devuelve la montura |
| Silbato | **Click derecho a tu dino**: menú de órdenes. **Agachado + click derecho al aire**: órdenes a todos tus dinos a 16 bloques. Órdenes: **seguirme**, **quedarse**, **patrullar el chunk donde estás parado** (camina por ese chunk y no se va). Modos: **pasivo** (no ataca a nadie), **defensivo** (lo normal) y **agresivo** (además ataca monstruos cerca, menos creepers) |
| Nombre | Etiqueta con nombre (renombrada en un yunque) + click derecho a tu dino. Arriba del modelo aparece el nombre y "de <dueño>" |
| Regalar | `/dino dar <jugador>` mirando a tu dino. El otro recibe [Aceptar] / [Rechazar] y tiene 60 s |
| Mis dinos | `/dino mis`: todos tus domesticados con especie, nombre, mundo, coordenadas, distancia y orden, aunque estén en chunks descargados. Al lado de cada uno, **[Llamar]** |
| Llamar de regreso | En el silbato, **"Llamar dinos lejanos"** (o `/dino llamar`): elegís hasta **2** y vuelven a tu lado (después te siguen). **Una vez cada 5 minutos.** Si el dino está en un chunk descargado se carga **solo ese chunk, un momento** (nada más) |
| Avisos | Si atacan a tu dino estando vos lejos (más de 24 bloques) te llega un aviso con coordenadas y quién fue (como mucho cada 30 s por dino). Si muere te avisa dónde y de qué; si no estabas conectado, te llega al entrar |
| Jaula | Click derecho a tu dino con una jaula vacía: lo guarda adentro (color, sexo, edad, montura, hambre, vida, nombre y modo). Click derecho al piso con la jaula llena: lo suelta y **quien lo suelta queda como dueño** (sirve para venderlo) |
| Drops | Cada especie suelta lo suyo (cuero, garras, dientes, cuernos, placas, membranas, plumas, glándula...) para craftear sus monturas con Shopkeepers. Lista en `/dino item` |
| Presas | El parasaurolophus y el gallimimus salvajes huyen si te acercás. **Agachado** podés llegar hasta ellos y darles de comer |
| Hombro (dimorphodon) | Domesticado: **agachado + click derecho con la mano vacía** y se sube a tu hombro. Agachado + click izquierdo (o lo mismo de nuevo) y se baja. Si hay monstruos a 12 bloques, chilla y te avisa |
| Volar (pteranodon) | En el suelo, **espacio despega**. En el aire: **W vuela hacia donde mirás** (mirar abajo = picada, arriba = trepar), Ctrl más rápido, **espacio aletea para subir**, sin W planea bajando. Aterriza solo al tocar el suelo. Si te bajás (Shift) en el aire caés con caída lenta |
| T-Rex | Los salvajes no se domestican: hay que conseguir un huevo (`/dino egg trex` o vendido con Shopkeepers), criarlo y esperar a que sea adulto |
| Hambre | Los domesticados se van quedando con hambre (1–2 h de juego de lleno a vacío). Se les da su comida con click derecho. **Muertos de hambre se echan, se ven mal y no se dejan montar** (no se mueren); con hambre tampoco crían |
| Comedero | Se pone en el suelo y se carga con click derecho con su comida (agachado = la pila entera). Los dinos domesticados con hambre a menos de 16 bloques **van solos a comer**. Hay de carne, de plantas y de pescado. No tiene inventario: la comida se convierte en puntos y no se puede sacar. Lo rompe el dueño (agachado si tiene comida) |
| Dinopedia | Libreta de paleontólogo con menús dibujados. **Índice**: las especies (silueta las que no viste), tu rango y una barra de investigación. **Página de cada especie**: foto grande del color que elijas, barras de vida/daño/velocidad/tamaño, colores vistos, sellos y la lupa. **Ficha** (click derecho a un dino con el libro): su foto, sexo, edad, color, vida, hambre, dueño y órdenes. Cada especie y cada color se anotan solos al ver un dino de cerca |
| Sellos y rangos | Cada especie tiene sellos: verla, todos sus colores comunes, el color raro, domesticarla (o derrotarla si no se domestica), criar una de huevo, montarla (o llevarla en el hombro). Cada sello da experiencia, completar una especie da más y podés poner comandos de premio en `config.yml` (`dinopedia`). Con los sellos subís de rango: Aprendiz, Explorador, Rastreador, Naturalista, Paleontólogo y Leyenda jurásica |
| Llevar la Dinopedia | En la mano: anotás dinos desde **32 bloques** (sin ella, 16), te avisa con **huellas** cuando hay una especie que no conocés cerca (y hacia dónde) y, si activaste la **lupa** en la página de una especie, la barra de acción te marca con una flecha dónde está la más cercana y a cuántos metros (primero los colores que te faltan) |
| Criar | Dar comida a un macho y una hembra adultos domesticados → la hembra pone un **huevo** |
| Huevo | Click derecho en el suelo. Se mueve, se agrieta y a los 5 min nace una cría al azar, domesticada por quien lo puso. Click derecho = ver %, golpe = recogerlo |
| Cría | Crece sola (40 min). Darle su comida la hace crecer un 5 % |

## Comandos (OP)

```
/dino spawn <tipo> [color|random] [macho|hembra|random] [adulto|bebe] [cantidad]
/dino egg <especie> [cantidad]
/dino grow [%]                      (crías a 10 bloques)
/dino tame [jugador]                (domestica el dino que mirás; sirve para T-Rex y Giganotosaurus)
/dino item <id> [cantidad] [jugador] (monturas, drops, silbato, jaula: para armar los trades de Shopkeepers)
/dino remove [radio] [especie]
/dino count
/dino comedero <carne|plantas|pescado> [cantidad]
/dino dinopedia [jugador]
/dino hambre [%]                    (pone el hambre de los domesticados a 10 bloques, para probar)
/dino pack [reload|info]
```

Todos los jugadores: `/dino mis`, `/dino llamar [id] [id]`, `/dino dar <jugador>`, `/dino aceptar`, `/dino rechazar`,
`/dino pack`. La espera y el máximo del silbato de regreso se cambian en `config.yml` (`recall`).

Items (`/dino item <id>`): `silbato`, `jaula`, `montura_<especie>` (raptor, triceratops, trex, pteranodon,
parasaurolophus, gallimimus, stegosaurus, ankylosaurus, giganotosaurus, carnotaurus) y los drops: `cuero_<especie>`,
`garra_raptor`, `cuerno_triceratops`, `cuerno_carnotaurus`, `diente_trex`, `diente_giganotosaurus`,
`diente_compsognathus`, `glandula_dilophosaurus`, `cresta_dilophosaurus`, `cresta_pteranodon`,
`cresta_parasaurolophus`, `pluma_gallimimus`, `placa_stegosaurus`, `mazo_ankylosaurus`, `membrana_pteranodon`,
`membrana_dimorphodon`. Los items propios no sirven en crafteos, yunques, hornos ni dispensers: los crafteos se arman
con Shopkeepers.

Especies: `raptor`, `triceratops` (`trike`), `dilophosaurus` (`dilo`), `compsognathus` (`compy`), `trex` (`rex`),
`pteranodon` (`ptera`, `pterodactilo`), `parasaurolophus` (`para`), `gallimimus` (`galli`), `stegosaurus` (`stego`),
`ankylosaurus` (`anky`), `giganotosaurus` (`giga`), `carnotaurus` (`carno`), `dimorphodon` (`dimo`).

## Rendimiento

- Cada dino manda su pose solo a los jugadores que lo tienen cargado, y menos seguido cuanto más lejos está el más cercano:
  cada 2 ticks a menos de 24 bloques, 4 hasta 48, 8 hasta 96 y 16 más lejos. Montado se actualiza cada tick.
  **Si nadie lo ve, no se calcula ni se manda nada** (la IA, el crecimiento y los huevos siguen andando igual).
- El cliente deja de dibujarlos según su tamaño: compsognathus a ~32 bloques, raptor y dilophosaurus a ~64,
  triceratops y pteranodon a ~96, T-Rex a ~128 (las crías, antes). Eso se multiplica por la opción "Distancia de entidades" de cada jugador.

## Instalar / actualizar

1. Copiar el `.jar` a `plugins/` (borrar el anterior) y reiniciar. `plugins/DinoEarth/config.yml` tiene vida, daño,
   velocidad, chance de domesticar y velocidades de montura de cada especie.
2. **Resource pack**: el plugin se lo manda solo a cada jugador al entrar y calcula el hash él mismo.
   Sacá `resource-pack` y `resource-pack-sha1` de `server.properties` y elegí en `config.yml`:
   - `mode: url` → subís `DinoEarth-ResourcePack.zip` (mc-packs.net, GitHub, Dropbox con `?dl=1`) y pegás el link en
     `resource-pack.url`. Si subiste un zip viejo, la consola avisa. Después de subir uno nuevo: `/dino pack reload`.
   - `mode: host` → el plugin sirve el pack que trae adentro (no hay que subir nada). Necesita un puerto abierto
     (`host.port`, 8163 por defecto) y `host.public-address` con la IP o dominio de la máquina.
   - `required: true` → quien rechace el pack no puede jugar.

`/dino pack` lo puede usar cualquier jugador para volver a recibir el pack. `/dino pack info` y `/dino pack reload` son de OP.

El zip es determinista: su hash solo cambia cuando cambia el contenido, así los jugadores no lo re-descargan sin motivo.

## Archivos

| Carpeta / archivo | Qué es |
|---|---|
| `generator/species_*.py` | **Los modelos y colores** de cada especie |
| `generator/geometry.py` | Piezas comunes (cubos, huesos, huevo, materiales de las monturas) |
| `generator/feeder.py` | Modelo del comedero (3 dietas × 4 niveles de comida) |
| `generator/icons.py` | Íconos de la Dinopedia (cada color renderizado, silueta, libro) |
| `generator/gui.py` | Menús dibujados de la Dinopedia: fondos, fotos grandes, fuentes y botones (`font_widths.json` = anchos de las letras de vanilla) |
| `generator/gui_preview.py` | Dibuja los menús como en el juego para revisarlos sin entrar (ver CLAUDE.md) |
| `generator/build.py` | Genera texturas, modelos JSON, el zip del pack y los rigs del plugin |
| `generator/sounds.py`, `sounds_species.py`, `synth.py` | Síntesis de sonidos (necesita `generator/.venv`) |
| `generator/preview.py` | Regenera todo y renderiza `preview.png` (`python generator/preview.py triceratops` = una sola especie, `monturas` = las monturas de cerca) |
| `resourcepack/` | Resource pack generado (se puede abrir en Blockbench) |
| `plugin/` | Plugin de Paper (Maven) |

Para cambiar modelos o sonidos:

```
python generator/preview.py
generator/.venv/Scripts/python generator/sounds.py
python generator/build.py
```

y después `mvn package` en `plugin/` (los rigs van adentro del jar).

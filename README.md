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
| **Dimorphodon** | 0,7 bloques, 2 de ala a ala | Pterosaurio chiquito; vuela y aterriza | Pescado crudo (33 %) | No: **vuela a tu lado** y chilla si hay monstruos cerca | Sí | loro, tucán, pardo, cielo, albino |
| **Spinosaurus** | 4 × 7,4 bloques | Carnívoro neutral **anfibio**: camina y **nada rápido**, no se ahoga | Pescado crudo (10 %) | Sí, **en tierra y en el agua** | Sí | pantano, río, arcilla, ceniza, albino |
| **Mosasaurus** | 1,7 × 9 bloques | **Acuático**: salvaje ataca a los que **nadan** cerca. En tierra queda varado y se arrastra al agua | Pescado crudo (8 %), mejor **desde un bote** | Sí, **bajo el agua** (te da respiración) | No pone huevos | abismo, arrecife, tiburón, noche, albino |

El albino es raro (3 %). Los machos son un poco más grandes y más coloridos
(cresta en el raptor, cuernos más largos y gola con rayos en el triceratops, crestas más altas en el dilophosaurus,
cuernitos de color en el T-Rex, cresta larga en el pteranodon).

Cada especie montable tiene **su propia montura** (se pone con la montura común):
raptor = de carrera con manta roja · triceratops = de carga con baranda de madera y alforjas ·
T-Rex = de guerra con placas de hierro y púas de hueso · pteranodon = arnés de vuelo con manija.

Guía completa con fotos y capturas de los menús: **[DinoEarth-Guia.pdf](DinoEarth-Guia.pdf)** (se regenera con
`generator/.venv/Scripts/python docs/guia/build_guia.py`).

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
| Compañero volador (dimorphodon) | Domesticado y siguiéndote: si te alejás despega solo y **vuela a tu lado** (quieto da vueltas alrededor tuyo; caminando o a caballo va atrás a la derecha, adelantándose a hacia dónde vas). Si queda muy lejos (teleport, portal, élitros) aparece al lado tuyo. **Agachado + click derecho con la mano vacía**: vuela o baja a caminar. Si hay monstruos a 12 bloques, chilla y te avisa |
| Volar (pteranodon) | En el suelo, **espacio despega**. En el aire: **W vuela hacia donde mirás** (mirar abajo = picada, arriba = trepar), Ctrl más rápido, **espacio aletea para subir**, sin W planea bajando. Aterriza solo al tocar el suelo. Si te bajás (Shift) en el aire caés con caída lenta |
| T-Rex | Los salvajes no se domestican: hay que conseguir un huevo (`/dino egg trex` o vendido con Shopkeepers), criarlo y esperar a que sea adulto |
| Hambre | Los domesticados se van quedando con hambre (1–2 h de juego de lleno a vacío). Se les da su comida con click derecho. **Muertos de hambre se echan, se ven mal y no se dejan montar** (no se mueren); con hambre tampoco crían |
| Comedero | Se pone en el suelo y se carga con click derecho con su comida (agachado = la pila entera). Los dinos domesticados con hambre a menos de 16 bloques **van solos a comer**. Hay de carne, de plantas y de pescado. No tiene inventario: la comida se convierte en puntos y no se puede sacar. Lo rompe el dueño (agachado si tiene comida) |
| Dinopedia | Libreta de paleontólogo con menús dibujados. **Índice**: las especies (silueta las que no viste), tu rango y una barra de investigación. **Página de cada especie**: foto grande del color que elijas, barras de vida/daño/velocidad/tamaño, colores vistos, sellos y la lupa. **Ficha** (click derecho a un dino con el libro): su foto, sexo, edad, color, vida, hambre, dueño y órdenes. Cada especie y cada color se anotan solos al ver un dino de cerca |
| Sellos y rangos | Cada especie tiene sellos: verla, todos sus colores comunes, el color raro, domesticarla (o derrotarla si no se domestica), criar una de huevo, montarla (o que vuele a tu lado). Cada sello da experiencia, completar una especie da más y podés poner comandos de premio en `config.yml` (`dinopedia`). Con los sellos subís de rango: Aprendiz, Explorador, Rastreador, Naturalista, Paleontólogo y Leyenda jurásica |
| Misiones del día | Cada jugador tiene **3 misiones propias por día** (distintas a las de los demás): avistar dinos de una especie, ver un color que te falta, domesticar, criar una cría de huevo, recorrer bloques montado, derrotar salvajes o cargar comederos. Solo piden especies que alguien vio en el server en los últimos días. Se ven en la Dinopedia (botón **Misiones del día** o `/dino misiones`), cada avance muestra una bossbar unos segundos y se puede **cambiar una misión por día**. Premios (experiencia y comandos) en `config.yml` (`missions`) |
| Llevar la Dinopedia | En la mano: anotás dinos desde **32 bloques** (sin ella, 16), te avisa con **huellas** cuando hay una especie que no conocés cerca (y hacia dónde) y, si activaste la **lupa** en la página de una especie, la barra de acción te marca con una flecha dónde está la más cercana y a cuántos metros (primero los colores que te faltan) |
| Criar | Dar comida a un macho y una hembra adultos domesticados → la hembra pone un **huevo** con la cría ya decidida (en la descripción del huevo: madre, padre y generación) |
| Genética | Cada dino tiene 3 rasgos: **vida, velocidad y tamaño** (100 % = normal, entre 80 % y 130 %). Los salvajes nacen con rasgos al azar; las crías sacan el promedio de los padres con un poco de suerte (para bien o para mal), así **criando a los mejores con los mejores** salen dinos más fuertes, rápidos o grandes. El **color** sale de uno de los padres (a veces uno cualquiera) y con un 3 % **muta al color raro**. En la ficha (Dinopedia → click derecho al dino) el botón **Genética** muestra los rasgos, la calidad (débil, normal, buena, excelente, legendaria), la generación y los padres. Se configura en `config.yml` (`genetics`) |
| Nidos | Cada tanto una **hembra salvaje adulta** de una especie con huevos arma un **nido** con 1 a 3 huevos y lo cuida. **Golpe = sacarle un huevo** (nace domesticado por quien lo pone, con los genes de la madre), pero los carnívoros y herbívoros **atacan** a quien se acerca a menos de 10 bloques (agachado, la mitad) o les roba; las presas se asustan y huyen. **Es la forma de conseguir T-Rex y Giganotosaurus sin admins.** Click derecho = cuántos huevos quedan. Solo aparecen fuera de pueblos de Towny y regiones de WorldGuard. `config.yml` (`nests`) |
| Nadar (spinosaurus, mosasaurus) | No se ahogan. Montados en el agua: **W nada hacia donde mirás** (mirar abajo = bucear), Ctrl más rápido, espacio sube. Al jinete le da **respiración acuática** mientras va montado. El mosasaurus en tierra casi no se mueve |
| Tops | `/dino top [categoría]`: dinos, sellos, especies, crías, domesticados, nidos, cazados, misiones y genes (cuenta también a los desconectados, se guarda en `jugadores.yml`) |
| Huevo | Click derecho en el suelo. Se mueve, se agrieta y a los 5 min nace la cría, domesticada por quien lo puso. Click derecho = ver %, golpe = recogerlo |
| Cría | Crece sola (40 min). Darle su comida la hace crecer un 5 % |

## Comandos (OP)

```
/dino spawn <tipo> [color|random] [macho|hembra|random] [adulto|bebe] [cantidad]
/dino egg <especie> [cantidad]
/dino grow [%]                      (crías a 10 bloques)
/dino tame [jugador]                (domestica el dino que mirás; sirve para T-Rex y Giganotosaurus)
/dino nido                          (la hembra salvaje que mirás arma un nido ya, para probar)
/dino item <id> [cantidad] [jugador] (monturas, drops, silbato, jaula: para armar los trades de Shopkeepers)
/dino remove [radio] [especie]
/dino count
/dino stats                         (cuánto consume: dinos, entidades, red estimada, tick, dueños con más dinos)
/dino reload                        (recarga messages.yml, textos.yml, items.yml y menus/*.yml)
/dino comedero <carne|plantas|pescado> [cantidad]
/dino dinopedia [jugador]
/dino hambre [%]                    (pone el hambre de los domesticados a 10 bloques, para probar)
/dino pack [reload|info]
```

Todos los jugadores: `/dino mis`, `/dino llamar [id] [id]`, `/dino misiones`, `/dino top [categoría]`, `/dino dar <jugador>`,
`/dino aceptar`, `/dino rechazar`, `/dino pack`. La espera y el máximo del silbato de regreso se cambian en `config.yml` (`recall`).

Límite de domesticados: `limits.max-tamed-per-player` en `config.yml` (20 por defecto, 0 = sin límite). Cuentan todos
los dinos del jugador, cargados o no; los guardados en jaulas no cuentan, pero no se sueltan si ya tiene el máximo.
Con el máximo no puede domesticar, aceptar regalos ni soltar jaulas, y sus huevos esperan listos hasta que tenga lugar.
Para dar más: permiso `dinoearth.limit.<número>` (vale el mayor) o `dinoearth.limit.unlimited`. `/dino tame` de un admin
no tiene límite (avisa si se pasa).

Items (`/dino item <id>`): `silbato`, `jaula`, `montura_<especie>` (raptor, triceratops, trex, pteranodon,
parasaurolophus, gallimimus, stegosaurus, ankylosaurus, giganotosaurus, carnotaurus) y los drops: `cuero_<especie>`,
`garra_raptor`, `cuerno_triceratops`, `cuerno_carnotaurus`, `diente_trex`, `diente_giganotosaurus`,
`diente_compsognathus`, `glandula_dilophosaurus`, `cresta_dilophosaurus`, `cresta_pteranodon`,
`cresta_parasaurolophus`, `pluma_gallimimus`, `placa_stegosaurus`, `mazo_ankylosaurus`, `membrana_pteranodon`,
`membrana_dimorphodon`, `espina_spinosaurus`, `diente_mosasaurus` (monturas también de spinosaurus y mosasaurus). Los items propios no sirven en crafteos, yunques, hornos ni dispensers: los crafteos se arman
con Shopkeepers.

Especies: `raptor`, `triceratops` (`trike`), `dilophosaurus` (`dilo`), `compsognathus` (`compy`), `trex` (`rex`),
`pteranodon` (`ptera`, `pterodactilo`), `parasaurolophus` (`para`), `gallimimus` (`galli`), `stegosaurus` (`stego`),
`ankylosaurus` (`anky`), `giganotosaurus` (`giga`), `carnotaurus` (`carno`), `dimorphodon` (`dimo`),
`spinosaurus` (`spino`), `mosasaurus` (`mosa`).

## Integraciones (se activan solas)

| Plugin | Qué agrega |
|---|---|
| **PlaceholderAPI** | Placeholders para scoreboards, hologramas y tops (abajo) |
| **Vault** (+ un plugin de economía) | Plata en misiones y sellos: `missions.reward-money`, `missions.all-done-money`, `dinopedia.money-per-stamp`, `dinopedia.money-species-complete` |
| **Towny / WorldGuard** | Los nidos solo aparecen fuera de pueblos y regiones (`nests.only-wilderness`). Poner comederos y soltar jaulas ya respetaba la protección |

Placeholders (`%dinoearth_<x>%`):

| `<x>` | Qué devuelve |
|---|---|
| `dinos`, `limite` | Dinos domesticados que tiene y su máximo |
| `especies`, `especies_total`, `sellos`, `sellos_total`, `rango` | Dinopedia |
| `crias`, `domesticados`, `nidos`, `cazados`, `misiones`, `misiones_hoy` | Contadores (crías nacidas, domesticados en total, huevos de nido, dinos derrotados, misiones) |
| `mejor_genes` | El mejor promedio de rasgos de sus dinos (%) |
| `tiene_<especie>` | Cuántos tiene de esa especie (ej. `tiene_trex`) |
| `montando` | La especie que está montando (vacío si ninguna) |
| `cargados` | Dinos cargados en el server |
| `puesto_<categoría>` | Su puesto en un top |
| `top_<categoría>_<n>_nombre`, `top_<categoría>_<n>_valor` | El top: `top_dinos_1_nombre`, `top_sellos_3_valor`... |

Categorías: `dinos`, `sellos`, `especies`, `crias`, `domesticados`, `nidos`, `cazados`, `misiones`, `genes`.

## Línea Plus (dinos con habilidad)

Ocho dinos con la misma forma que su base pero de la **línea Plus**: negros con detalles rojos, ojos rojos, una pieza
extra que el normal no tiene (crestas, púas, plumas, placas) y montura negra con costuras rojas. **Solo salen de
huevo** (no hay salvajes ni nidos): `/dino egg <especie_plus> [cantidad] [jugador]`, que también anda desde la consola
para venderlos (tienda/Tebex). Dos Plus de la misma especie crían Plus. Aceptan su montura propia o la de su base.

Las habilidades activas se usan **montado, con click derecho con cualquier ítem en la mano** (el click derecho al aire
con la mano vacía no le llega al server; lo más cómodo es el silbato) **o con la tecla F** (cambiar de mano). Muestran
cuánto falta para volver a usarse. Tiempos y números en `config.yml` (`plus`).

| Plus | Id | Habilidad |
|---|---|---|
| Velociraptor Sombra | `raptor_plus` | **Dash**: impulso hacia donde mira (8 s) |
| Triceratops Cosechador | `triceratops_plus` | Quieto o patrullando **cosecha y replanta** los cultivos maduros (radio 3) y los guarda en un cofre o barril cercano. Montado: cosecha radio 5 (20 s) |
| Spinosaurus Pescador | `spinosaurus_plus` | Cerca del agua **pesca solo** para su dueño (cada 45 s). Montado: pesca ya (60 s) |
| Dimorphodon Buscador | `dimorphodon_plus` | **Imán**: mientras te acompaña junta los ítems del suelo alrededor tuyo (no los que tiró otro) |
| Parasaurolophus Heraldo | `parasaurolophus_plus` | **Canto**: velocidad y regeneración 10 s y cura a tus dinos cercanos (60 s) |
| Pteranodon Halcón | `pteranodon_plus` | **Turbo**: en el suelo despega alto, volando acelera (10 s) |
| Gallimimus Relámpago | `gallimimus_plus` | **Arranque**: 5 s de velocidad extra (30 s) |
| Ankylosaurus Leñador | `ankylosaurus_plus` | **Tala** el árbol que tiene adelante y suelta la madera (12 s) |

Cosechar y talar respetan Towny y WorldGuard (como si lo hiciera el dueño). En las carreras no hay habilidades: todos
corren con el mismo dino prestado.

## Carreras de dinosaurios

Carreras **parejas**: la pista le presta a todos **el mismo dino** (misma especie, genética neutra), así gana el que
mejor maneja. La pista la armás vos donde quieras (decorala como te guste) y la marcás parado en el lugar:

1. `/dino carrera crear <pista>`.
2. En cada puesto de largada, mirando hacia adelante: `/dino carrera largada <pista>` (uno por corredor).
3. Recorré la pista y en cada control, mirando hacia donde se corre: `/dino carrera puerta <pista> [ancho] [alto]`
   (por defecto 8 de ancho y 4 de alto). **La última puerta es la meta.** Si la largada está detrás de la meta, es una
   carrera de vueltas (`/dino carrera vueltas <pista> <n>`).
4. `/dino carrera especie <pista> <especie>` (por defecto gallimimus; tiene que ser montable y de tierra) y
   `/dino carrera ver <pista>` para ver largadas y puertas con partículas.
5. **NPC de inscripción** con FancyNpcs: agregale la acción de comando del jugador `dino carrera unirse <pista>`.

Cómo se corre: al juntarse el mínimo de corredores (2) se espera 30 s por si se suma alguien más (si se llena, larga al
toque). Todos aparecen montados en la grilla, quietos durante la cuenta regresiva en bossbar. Hay que pasar **todas las
puertas en orden** (no se puede cortar camino). La actionbar muestra puesto, vuelta, tiempo y la **estamina**: correr con
Ctrl la gasta y, vacía, no deja correr hasta recuperar el 30 %. Si te bajás del dino volvés a la última puerta.
Teletransportarse en plena carrera te deja afuera. Cuando llega el primero, el resto tiene 30 s.

Al terminar: podio, **premios por puesto** (`races.rewards`: experiencia, plata con Vault y comandos), **récord de
siempre y de la semana** por pista (`/dino carrera pistas`, PlaceholderAPI `%dinoearth_record_<pista>%` y
`%dinoearth_recordista_<pista>%`), carreras ganadas en `/dino top carreras` (`%dinoearth_carreras%`), la misión diaria
**"Terminá una carrera"** y el sello **Corredor** de la especie de la pista en la Dinopedia.

**Carreras programadas** (`races.schedule`): a ciertas horas (`times: ['20:00', '22:00']`) o cada N minutos; unos
minutos antes se anuncia en el chat con un botón **[Anotarme]** y otro aviso un minuto antes. Si no se juntan
corredores, se cancela. `/dino carrera iniciar <pista>` larga aunque haya uno solo (para probar la pista).

## Textos y menús

Nada de lo que ve el jugador está escrito en el código: todo sale de archivos en `plugins/DinoEarth/` (se crean solos
y al actualizar el plugin se les agregan las claves nuevas sin tocar lo que cambiaste; `/dino reload` los recarga).

| Archivo | Qué tiene |
|---|---|
| `messages.yml` | Mensajes de `chat`, `actionbar` y `bossbar` |
| `textos.yml` | Palabras que se meten adentro de los mensajes: nombres y descripciones de las especies y sus colores, órdenes, causas de muerte, tiempos, misiones, sellos, rangos |
| `items.yml` | Nombre y lore de cada item (drops, monturas, silbato, jaula, huevo, comedero, Dinopedia) |
| `menus/silbato.yml`, `menus/llamar.yml` | Menús con el formato del server (título, tamaño, relleno, items y slots) |
| `menus/dinopedia.yml` | La Dinopedia: es la excepción (el fondo va dibujado), pero el nombre y el lore de cada item siguen el formato |

Formato: prefijo `&9&lDINOEARTH &8»`, texto `&f`, variables `&#ff00d9`, símbolos `&8`, chat terminado en punto
blanco, actionbar `&8| &f...`, lore con `&8| &f` y separadores `&f`, Volver/Cerrar en el slot 49 (`&#EE193B`),
indicadores `&#41FF00✔ Click para ...` y `&#EE193B✘ Click para ...`. Sin `¡` ni `!`.

## Rendimiento

- Cada dino manda su pose solo a los jugadores que lo tienen cargado, y menos seguido cuanto más lejos está el más cercano
  (pensado para distancia de vista 8, o sea 128 bloques como mucho):

  | Más cercano que lo ve | Moviéndose | Quieto (parado o sentado sin hacer nada) |
  |---|---|---|
  | menos de 16 bloques | cada 2 ticks | cada 6 |
  | 16 a 32 | cada 3 | cada 8 |
  | 32 a 64 | cada 5 | cada 12 |
  | más de 64 | cada 8 | cada 20 |

  Montado se actualiza cada tick. El cliente interpola entre poses, así que quieto sigue respirando suave.
  **Si nadie lo ve, no se calcula ni se manda nada** (la IA, el crecimiento y los huevos siguen andando igual).
- Además, un hueso que casi no se movió desde la última vez (menos de 0,03 px o 0,3°) no se manda.
- `/dino stats` muestra cuántos huesos por segundo se mandan y cuántos se ahorran, la red estimada y cuánto tarda el tick.
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
| `generator/nest.py` | Modelo del nido salvaje (los huevos los pone el plugin encima) |
| `generator/icons.py` | Íconos de la Dinopedia (cada color renderizado, silueta, libro) |
| `generator/gui.py` | Menús dibujados de la Dinopedia: fondos, fotos grandes, fuentes y botones (`font_widths.json` = anchos de las letras de vanilla) |
| `generator/gui_preview.py` | Dibuja los menús como en el juego para revisarlos sin entrar (ver CLAUDE.md) |
| `plugin/src/main/resources/messages.yml`, `textos.yml`, `items.yml`, `menus/` | Textos y menús (ver "Textos y menús"); `items.yml` lo genera `generator/items.py` |
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

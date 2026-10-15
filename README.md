# DinoEarth

Dinosaurios para la modalidad Earth (Paper 1.21.11; el pack funciona en clientes 1.21.4 – 1.21.11).

## Especies

| Especie | Tamaño | Carácter | Se domestica con | Montura | Crías / huevo | Colores |
|---|---|---|---|---|---|---|
| **Velociraptor** | 2 × 3,6 bloques | Neutral, caza animales chicos | Carne cruda (25 %) | Sí, rápido | Sí | arena, gris, verde, rojizo, oscuro, albino |
| **Triceratops** | 2,2 × 5,3 bloques | Herbívoro, embiste si lo atacan | Trigo, zanahoria, manzana, remolacha, bayas, sandía, fardo (20 %) | Sí, lento; **corriendo arrolla** lo que tenga adelante | Sí | pardo, oliva, pizarra, rojizo, albino |
| **Dilophosaurus** | 2 × 3,3 bloques | **Hostil**: abre la gola y **escupe veneno** (ceguera + veneno) | — | No | No | amarillo, verde, pardo, albino |
| **Compsognathus** | 0,6 × 1,4 bloques | Anda en grupos de 5; ataca si estás **herido** o si son 4 o más | — | No | No | verde, pardo, gris, albino |

El albino es raro (3 %). Los machos son un poco más grandes y más coloridos
(cresta en el raptor, cuernos más largos y gola con rayos en el triceratops, crestas más altas en el dilophosaurus).

## Cómo se juega

| Acción | Cómo |
|---|---|
| Domesticar | Click derecho con su comida (ver tabla) |
| Sentar / parar | Click derecho (agachado si tiene montura) |
| Curar | Raptor: carne · Triceratops: su comida |
| Montura | Click derecho con una montura (adulto, solo el dueño). Click derecho para subir. WASD, Ctrl corre, espacio salta, Shift baja. Tijeras: sacar la montura |
| Criar | Dar comida a un macho y una hembra adultos domesticados → la hembra pone un **huevo** |
| Huevo | Click derecho en el suelo. Se mueve, se agrieta y a los 5 min nace una cría al azar, domesticada por quien lo puso. Click derecho = ver %, golpe = recogerlo |
| Cría | Crece sola (40 min). Darle su comida la hace crecer un 5 % |

## Comandos (OP)

```
/dino spawn <tipo> [color|random] [macho|hembra|random] [adulto|bebe] [cantidad]
/dino egg <especie> [cantidad]
/dino grow [%]                      (crías a 10 bloques)
/dino remove [radio] [especie]
/dino count
/dino pack [reload|info]
```

Especies: `raptor`, `triceratops` (`trike`), `dilophosaurus` (`dilo`), `compsognathus` (`compy`).

## Rendimiento

- Cada dino manda su pose solo a los jugadores que lo tienen cargado, y menos seguido cuanto más lejos está el más cercano:
  cada 2 ticks a menos de 24 bloques, 4 hasta 48, 8 hasta 96 y 16 más lejos. Montado se actualiza cada tick.
  **Si nadie lo ve, no se calcula ni se manda nada** (la IA, el crecimiento y los huevos siguen andando igual).
- El cliente deja de dibujarlos según su tamaño: compsognathus a ~32 bloques, raptor y dilophosaurus a ~64,
  triceratops a ~96 (las crías, antes). Eso se multiplica por la opción "Distancia de entidades" de cada jugador.

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
| `generator/geometry.py` | Piezas comunes (cubos, huesos, montura, huevo) |
| `generator/build.py` | Genera texturas, modelos JSON, el zip del pack y los rigs del plugin |
| `generator/sounds.py`, `sounds_species.py`, `synth.py` | Síntesis de sonidos (necesita `generator/.venv`) |
| `generator/preview.py` | Regenera todo y renderiza `preview.png` (`python generator/preview.py triceratops` = una sola especie) |
| `resourcepack/` | Resource pack generado (se puede abrir en Blockbench) |
| `plugin/` | Plugin de Paper (Maven) |

Para cambiar modelos o sonidos:

```
python generator/preview.py
generator/.venv/Scripts/python generator/sounds.py
python generator/build.py
```

y después `mvn package` en `plugin/` (los rigs van adentro del jar).

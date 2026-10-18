# DinoEarth en el server: tradeos y armadura

Todo lo que hace falta para armar la tienda con **Shopkeepers** y el **Set del Cazador Jurásico** con
**AdvancedEnchantments**. Los precios son una propuesta: cambialos a gusto.

## 1. Cómo conseguir cada ítem (admin)

Shopkeepers arma los tradeos con los ítems que ponés en el editor, así que primero sacá una copia de cada uno:

| Qué | Comando |
|---|---|
| Drops, monturas, silbato, jaula | `/dino item <id> [cantidad] [jugador]` (sin id te lista todos) |
| Dinopedia | `/dino libro [jugador]` |
| Comedero | `/dino comedero <carne\|plantas\|pescado> [cantidad]` |
| Huevos (sin genes fijos) | `/dino egg <especie> [cantidad] [jugador]` (anda desde la consola) |

Todos los ítems del plugin salen **siempre iguales** (mismo nombre, descripción y marca interna), así que un drop
que suelta un dino matchea con el que pusiste en el tradeo.

**Importante:**
- **No cambies nombres ni descripciones de los drops en `items.yml` después de armar los tradeos.** Los ítems nuevos
  saldrían distintos a los guardados en la tienda y el tradeo dejaría de aceptarlos. Si los cambiás, rearmá los tradeos.
- En el `config.yml` de Shopkeepers poné `use-strict-item-comparison: true`: así exige el ítem exacto y nadie puede
  pagar con un papel renombrado.
- Cada tradeo acepta **como máximo 2 ítems de pago** (cada uno hasta una pila de 64).

## 2. La tienda: "Paleontólogo"

Un admin shop de Shopkeepers: mirá un bloque y `/shopkeeper admin` (o el tipo de mob que quieras, por ejemplo
`/shopkeeper admin wandering_trader`). En el editor, arriba va lo que se vende y abajo los 1 o 2 pagos.

### Monturas (cuero de la especie + la pieza rara)

Las cantidades están pensadas para unas 5 a 7 cacerías de esa especie (los grandes sueltan más cuero por muerte).

| Se vende | Pago 1 | Pago 2 |
|---|---|---|
| `montura_raptor` | 10 `cuero_raptor` | 3 `garra_raptor` |
| `montura_triceratops` | 12 `cuero_triceratops` | 2 `cuerno_triceratops` |
| `montura_parasaurolophus` | 10 `cuero_parasaurolophus` | 2 `cresta_parasaurolophus` |
| `montura_gallimimus` | 8 `cuero_gallimimus` | 6 `pluma_gallimimus` |
| `montura_stegosaurus` | 12 `cuero_stegosaurus` | 4 `placa_stegosaurus` |
| `montura_ankylosaurus` | 12 `cuero_ankylosaurus` | 2 `mazo_ankylosaurus` |
| `montura_carnotaurus` | 10 `cuero_carnotaurus` | 3 `cuerno_carnotaurus` |
| `montura_pteranodon` | 8 `membrana_pteranodon` | 2 `cresta_pteranodon` |
| `montura_spinosaurus` | 12 `cuero_spinosaurus` | 3 `espina_spinosaurus` |
| `montura_trex` | 16 `cuero_trex` | 4 `diente_trex` |
| `montura_giganotosaurus` | 16 `cuero_giganotosaurus` | 4 `diente_giganotosaurus` |
| `montura_mosasaurus` | 16 `cuero_mosasaurus` | 4 `diente_mosasaurus` |

Dilophosaurus, compsognathus y dimorphodon no se montan: sus drops (`glandula_dilophosaurus`,
`cresta_dilophosaurus`, `diente_compsognathus`, `membrana_dimorphodon`) quedan para otras cosas o como moneda extra.

### Herramientas

| Se vende | Pago 1 | Pago 2 |
|---|---|---|
| Dinopedia (`/dino libro`) | 1 libro | 1 pluma |
| `silbato` | 3 lingotes de hierro | 1 pluma |
| `jaula` | 16 barrotes de hierro | 1 diamante |
| Comedero de carne | 1 caldero | 4 cuero (el común de vaca) |
| Comedero de plantas | 1 caldero | 4 cuero (el común de vaca) |
| Comedero de pescado | 1 caldero | 4 cuero (el común de vaca) |

La Dinopedia también la podés dar gratis al entrar por primera vez con otro plugin (kit de bienvenida):
`dino libro %player%` desde la consola.

### Línea Plus y huevos

- **Línea Plus:** no va en la tienda. Se vende afuera (Tebex o lo que uses) con `/dino egg <especie_plus> 1 <jugador>`
  desde la consola. Un Plus acepta **la montura de su especie base** (al Velociraptor Sombra le sirve
  `montura_raptor`), así que las monturas Plus (`montura_raptor_plus`, etc.) son un extra para meter en el paquete.
- **Huevos normales:** recomiendo **no venderlos**. Saltean domesticar y el T-Rex / Giganotosaurus están pensados para
  conseguirse robando huevos de nidos salvajes. Si querés vender alguno, que sea de herbívoros y caro.

### Set del Cazador Jurásico

| Se vende | Pago 1 | Pago 2 |
|---|---|---|
| Máscara del Cazador | 16 `cuero_raptor` | 6 `garra_raptor` |
| Peto del Cazador | 12 `cuero_trex` | 6 `diente_trex` |
| Grebas del Cazador | 12 `cuero_carnotaurus` | 4 `cuerno_carnotaurus` |
| Botas del Cazador | 12 `cuero_gallimimus` | 10 `pluma_gallimimus` |

Cada pieza sale de un dino distinto, así que armar el set completo obliga a salir a cazar varias especies.

## 3. El Set del Cazador Jurásico

Sin texturas: cuero teñido marrón oscuro con el adorno vanilla **"costillas" (rib) en cuarzo**, que parece hueso.
Nombre en `#ff00d9`, descripción con `| ` gris como el resto del server, y Protección III + Irrompibilidad III.

**Pasos:**

1. **Crear las piezas:** los 4 comandos están en `servidor/armadura-cazador.txt`. **Correlos desde la consola**,
   porque son largos (~630 letras) y el chat corta en 256. Probados en un Paper 1.21.11: las 4 piezas salen con tinte,
   adorno, nombre, descripción y encantamientos.
2. **Encantamientos de AdvancedEnchantments:** pegá `servidor/advancedenchantments/enchantments-dinoearth.yml` al final
   de `plugins/AdvancedEnchantments/enchantments.yml` y hacé `/ae reload`.
3. **Ponérselos:** con cada pieza en la mano:

   | Pieza | Comando | Qué hace |
   |---|---|---|
   | Máscara | `/ae enchant visioncazador 1` | Visión nocturna puesta |
   | Peto | `/ae enchant pielescamosa 1` | 15 % menos daño de mobs (los dinos son mobs); nivel 2: 25 % |
   | Grebas | `/ae enchant zancada 1` | Velocidad I puesta; nivel 2: Velocidad II |
   | Botas | `/ae enchant patasderaptor 1` | 40 % menos daño de caída; nivel 2: 70 % |

4. **A la tienda:** poné cada pieza ya encantada como "se vende" en el editor del Paleontólogo.

**Lo que no pude comprobar** (no tengo AdvancedEnchantments acá):
- En los dos de efecto permanente (`visioncazador`, `zancada`) usé `@Self` como objetivo. Buscá `EFFECT_STATIC` en tu
  `enchantments.yml` (alguno de botas o casco que ya venga) y fijate si escribe el objetivo igual. Si no, copiá como lo hace.
- Están en el grupo `UNIQUE`. Si AdvancedEnchantments reparte libros de ese grupo en su encantador, alguien podría
  sacarlos por ahí (igual solo se pueden poner en armadura de cuero). Si no querés eso, fijate en el config de AE
  cómo sacar un encantamiento de los libros, o pasalos a un grupo que no se reparta.

## 4. Usar un NPC de FancyNpcs en vez del mob de Shopkeepers (opcional)

Lo más simple es dejar el mob propio de Shopkeepers. Si querés que la tienda la abra un NPC de FancyNpcs (como el
de las carreras), Shopkeepers tiene `/shopkeeper remote <tienda> <jugador>` (permiso
`shopkeeper.remote.otherplayers`). Ponélo como acción del NPC; la tienda tiene que existir igual, así que dejá el
mob de Shopkeepers escondido en algún lado. La wiki de Shopkeepers no aclara si la consola puede abrirla para otro
jugador, así que probalo antes de dejarlo así.

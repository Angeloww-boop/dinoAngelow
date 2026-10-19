# Guion para grabar el video de DinoEarth

Vos grabás las tomas en el server y yo armo el video: cortes, música, logo y textos (horizontal y vertical).
Cada toma dura **5 a 15 segundos útiles**. Grabá de más, que después se recorta.

## Antes de grabar

**Cliente**
- Con VulkanMod, ReplayMod puede fallar. Lo más seguro es grabar con **OBS** (1920x1080, 60 fps, MP4).
  Si querés ReplayMod (te deja mover la cámara después), sacá VulkanMod solo para grabar.
- Video: FOV **70**, escala de GUI **3**, luz suave **activada**, gráficos **detallados**, distancia de render 12 o más.
- Sin F3 ni chat abierto. **F1** oculta el HUD para las tomas de cine. **F5** cambia la cámara: atrás y de frente.
- Mouse con sensibilidad baja. Los giros lentos se ven mucho mejor.

**Server (en el mundo de prueba o en un lugar lejos de pueblos)**
```
/time set 1000
/gamerule doDaylightCycle false
/weather clear
/gamerule doWeatherCycle false
/dino remove 64
```
- Elegí una **llanura con árboles y un lago o la costa cerca**. Para el mosasaurus hace falta mar hondo.
- Prepará en creativo, pero grabá en **supervivencia**: así se ven corazones, hambre y experiencia.
- Para que los huevos nazcan rápido: en `config.yml` poné `hatch-minutes: 1` y reiniciá
  (`/dino reload` recarga solo textos y menús). Después volvelo a 5.

**Hotbar para todo el video (más o menos este orden)**

| Slot | Item | Cómo |
|---|---|---|
| 1 | Dinopedia | `/dino dinopedia` |
| 2 | Carne cruda (bife de vanilla) | creativo |
| 3 | Silbato | `/dino item silbato` |
| 4 | Jaula | `/dino item jaula` |
| 5 | Montura del T-Rex | `/dino item montura_trex` |
| 6 | Espada | creativo |
| 7 | Mapa del Paleontólogo | `/dino item mapa` |
| 8 | Etiqueta de nombre "Rex" | yunque |

## Tomas

### 1. Apertura (cine, F1)
```
/dino spawn parasaurolophus azul hembra adulto 4
/dino spawn trex oscuro macho adulto
```
- Con ReplayMod: un recorrido de cámara lento al costado de la manada caminando, terminando en el T-Rex.
- Sin ReplayMod: en espectador (`/gamemode spectator`), avanzá despacio al costado de la manada.
- Esperá a que ruja el T-Rex, o montalo después y apretá espacio.

### 2. Paseo con la Dinopedia (primera persona)
```
/dino spawn gallimimus naranja hembra adulto 4
/dino spawn triceratops pardo macho adulto
```
- Dinopedia en la mano, caminando. Los gallimimus huyen si te acercás, así que se cruzan solos.
- Tiene que salir en la barra de acción **"Nueva especie en la Dinopedia"** (usá una cuenta o especie que no la tenga).
- Después aparece la **flecha de rastreo** hacia el triceratops: acercate siguiéndola.

### 3. Abrir la Dinopedia
- Click derecho con el libro. Pasá el mouse despacio por el **Triceratops** (que se vea el tooltip) y abrilo.
- En su página, pasá por los sellos y por la **lupa** de rastrear. 6 a 8 segundos alcanzan.

### 4. Domar un raptor (primera persona)
```
/dino spawn raptor rojizo macho adulto
```
- Bife crudo en la mano, click derecho al raptor hasta que salgan los **corazones** (25 % por carne).
- Ponele la etiqueta **"Rex"**: tiene que verse el cartel con el nombre.
- Si sale el aviso del sello **Domador**, que se vea en el chat.

### 5. T-Rex montado (F5 atrás y F5 de frente)
```
/dino spawn trex verde macho adulto
/dino tame
/dino spawn parasaurolophus verde hembra adulto 5
```
- `/dino tame` mirando al T-Rex. Click derecho con la montura del T-Rex para ponérsela, y click derecho para subir.
- **F5 atrás**: corriendo (control) por la llanura 5 a 8 segundos.
- **F5 de frente**: frená y **espacio = rugido**, que se vea la boca abierta.
- Que la manada de parasaurolophus esté adelante: con el rugido salen corriendo.

### 6. Vuelo en pteranodon (F5 atrás)
```
/dino spawn pteranodon gaviota macho adulto
/dino tame
/dino item montura_pteranodon
```
- **W** va hacia donde mirás (mirá arriba para subir), **espacio** también sube, y **W + correr** aletea más fuerte.
- Volá de la tierra **hacia el mar** con el **mapa del paleontólogo** en la mano: arriba se ve la barra con la latitud, la longitud y la zona.

### 7. Mosasaurus bajo el agua (F5 atrás)
```
/dino spawn mosasaurus arrecife macho adulto
/dino tame
/dino item montura_mosasaurus
```
- En mar hondo (8 bloques o más). Montado no te ahogás. Nadá cerca del fondo, entre algas.

### 8. Nido salvaje (primera persona, en supervivencia)
```
/dino spawn raptor gris hembra adulto
/dino nido
```
- `/dino nido` mirando a la hembra.
- Acercate al nido (aparece "Nido de Velociraptor con 3 huevo(s), cuidado con la madre").
- **Golpealo** para sacar un huevo. La madre te ve: girá, corré y que te muerda.

### 9. La cría nace (primera persona mirando el huevo)
- Poné el huevo robado en el piso (click derecho al suelo) y miralo: sale el porcentaje "Huevo de Velociraptor: N% listo".
- Grabá desde que falta poco: se mueve, se rompe y nace la cría ("Nació una cría de...").

### 10. Silbato y jaula
- Con 2 o 3 dinos tuyos cerca: **agachado + click derecho con el silbato** para darle la orden a todos.
  Abrí el menú y elegí **Seguir**.
- **F5 de frente** caminando hacia la cámara, con los dinos siguiéndote y sus nombres arriba.
- Jaula en la mano, **click derecho a Rex**: se mete en la jaula y el item cambia a "jaula llena".

### 11. Línea Plus
```
/dino egg raptor_plus 1
/dino egg ankylosaurus_plus 1
/dino grow 100
/dino item montura_raptor_plus
/dino item montura_ankylosaurus_plus
```
- Poné los huevos, esperá que nazcan y usá `/dino grow 100` cerca de las crías.
- **Raptor Sombra**: montado y corriendo, **click derecho con cualquier item (o F) = Dash**.
- **Ankylosaurus Leñador**: parado frente a un árbol, **click derecho o F = Tala**: el árbol cae de un golpe.

### 12. Cierre (cine, F1)
- El T-Rex montado en una loma, rugiendo. Cámara girando despacio alrededor, o vos quieto y él de costado.
- El logo y la música los pongo yo.

## Cuando termines
- Pasame los clips (Drive, WeTransfer o lo que te quede cómodo). Nombralos por número de toma: `05-trex.mp4`.
- Si algo no salió, no pasa nada: armo el video con lo que haya.
- Yo hago la edición: orden, cortes, música, logo, textos cortos, sonido nivelado, y versión **horizontal y vertical**.

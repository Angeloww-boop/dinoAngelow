# Prueba de carga de DinoEarth (`/dino bench`)

Sirve para saber cuánto consume el plugin según cuántos jugadores tengan dinos cerca.
Se hace en el **server de prueba**, nunca en el de la modalidad abierta.

## Qué hace el comando

```
/dino bench <dinos por jugador> [minutos por etapa] [radio]
/dino bench parar
```

- `<dinos por jugador>` puede ser un número (`5`) o varias etapas separadas por coma (`2,5,10`).
- En cada etapa pone esa cantidad de dinos salvajes adultos (especies al azar, sin el mosasaurus)
  alrededor de **cada jugador conectado**, entre 6 bloques y el `radio` (por defecto 24), sobre suelo firme.
  Los pone de a 15 por tick para no medir el pico del spawn.
- Espera 10 segundos y después toma una muestra cada 10 segundos durante los `minutos` (por defecto 2).
- Entre etapa y etapa borra todo. Los dinos de la prueba **no se guardan**: si se cae el server no quedan.
- Al final muestra en el chat (y en la consola) el promedio de cada etapa:
  - **DinoEarth ms**: lo que tarda el tick de todos los dinos (IA propia, animación y poses), promedio y el peor.
  - **server ms (MSPT)**: el tick entero del server. Por debajo de 50 ms hay 20 TPS.
  - **TPS**.
  - **Red**: KB/s estimados de poses que recibe cada jugador y en total (unos 90 bytes por hueso mandado).
- Todas las muestras quedan en `plugins/DinoEarth/bench/bench-<fecha>.csv`. Ese archivo me lo pasás y te armo la tabla.

**Importante:** las poses solo se mandan a quien está viendo al dino. Sin jugadores, o con jugadores lejos, el gasto
sale mucho más bajo que el real. Por eso hacen falta **bots** que entren como jugadores.

## Paso a paso

1. **Server de prueba local**: una copia del mundo en tu PC o en el host de prueba, con DinoEarth 0.20.0 y
   [spark](https://spark.lucko.me/).
2. **Dejar entrar bots**: en `server.properties` del server de prueba poné `online-mode=false`, y conectá los bots
   **directo a ese server** (no por Velocity). Que ese server no quede abierto a internet mientras esté así.
3. **Bots**: [SoulFire](https://soulfiremc.com/) es un programa para esto. Dirección `localhost:25565`,
   cantidad 25, 50 o 100, que entren de a uno cada 1 segundo, con el anti-AFK o caminar al azar activado.
   Tu PC tiene que aguantar los bots: si se traba, probá con menos.
4. **Dos situaciones** (conviene medir las dos):
   - **Todos juntos** (peor caso, como un evento): los bots quedan donde aparecen.
   - **Repartidos** (como en la modalidad Earth): `/spreadplayers 0 0 200 3000 false @a` los reparte en un radio
     de 3000 bloques.
5. **Medir**, desde la consola:
   ```
   /spark profiler start --timeout 420
   /dino bench 2,5,10 2
   ```
   Son 3 etapas de 2 minutos (más el arranque): unos 7 minutos. Al terminar, spark te da un link.
6. **Pasame**: el `.csv` de `plugins/DinoEarth/bench/`, el link de spark y cuántos bots había.
   Con eso te digo cuánto gasta y qué conviene ajustar (topes de spawn natural, distancia de vista de los dinos,
   cada cuánto se mandan las poses).

## Cómo leer los números

- **DinoEarth ms** es la parte del plugin. Si con 100 jugadores y 5 dinos cada uno da, por ejemplo, 8 ms, el plugin
  usa un 16 % del tick (50 ms).
- **server ms** incluye todo: los lobos (su IA de vanilla), las entidades, los otros plugins.
  Si sube mucho más que **DinoEarth ms**, lo pesado son las entidades (lobos y displays), no el código del plugin.
- **Red por jugador**: se compara con la conexión de los jugadores. Unos pocos KB/s no molestan a nadie.
  Lo que hay que mirar es el **total** contra la subida del host.

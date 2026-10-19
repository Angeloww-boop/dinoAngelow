package com.dinoearth.spawn;

import com.dinoearth.Keys;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.integration.Wilderness;
import com.dinoearth.species.Species;
import com.dinoearth.text.Lang;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawn natural en el mapa de la Tierra a escala: cada especie aparece en la zona del mundo real donde se encontraron
 * sus fosiles (regiones de latitud/longitud en el config), el mosasaurus en el agua y, fuera de las regiones, unas pocas
 * especies comunes. Aparecen cerca de los jugadores (a sus espaldas, en chunks ya cargados), solo en naturaleza
 * (Towny/WorldGuard), con topes por jugador y en total.
 *
 * Los que aparecen asi quedan marcados ({@link Keys#natural}). Si nadie se les acerca por un rato, desaparecen (los
 * domesticados, prestados, atados y las madres con nido nunca). La ultima vez que alguien estuvo cerca va en el PDC del
 * lobo, asi que tambien se borran al volver a cargar un chunk que quedo abandonado: no se acumulan por el mapa.
 */
public final class NaturalSpawner implements Listener {

    private final JavaPlugin plugin;
    private final DinoManager dinos;
    private final Keys keys;
    private BukkitTask spawnTask, sweepTask;

    private boolean enabled, behind, onlyWild;
    private List<String> worlds = List.of();
    private double chance, minDistance, maxDistance, nearRadius, despawnRadius, cityDistance;
    private int maxNear, maxTotal, oceanDepth;
    private long despawnMs;
    private double oceanChance, elsewhereChance;
    private Map<String, Integer> ocean = Map.of(), elsewhere = Map.of();
    private EarthMap map = new EarthMap(1, 0, 1, 0, List.of());

    public NaturalSpawner(JavaPlugin plugin, DinoManager dinos, Keys keys) {
        this.plugin = plugin;
        this.dinos = dinos;
        this.keys = keys;
        loadConfig();
    }

    public void loadConfig() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("natural-spawn");
        if (spawnTask != null) spawnTask.cancel();
        if (sweepTask != null) sweepTask.cancel();
        enabled = c != null && c.getBoolean("enabled", true);
        if (c == null) return;
        worlds = c.getStringList("worlds");
        chance = clamp(c.getDouble("chance", 0.4), 0, 1);
        minDistance = Math.max(8, c.getDouble("min-distance", 40));
        maxDistance = Math.max(minDistance + 1, c.getDouble("max-distance", 80));
        behind = c.getBoolean("behind-player", true);
        nearRadius = Math.max(16, c.getDouble("near-radius", 128));
        maxNear = Math.max(0, c.getInt("max-near-player", 4));
        maxTotal = Math.max(0, c.getInt("max-total", 120));
        onlyWild = c.getBoolean("only-wilderness", true);
        cityDistance = Math.max(0, c.getDouble("city-distance", 150));
        Wilderness.setIgnored(c.getStringList("ignored-regions"));
        despawnMs = (long) (Math.max(0.5, c.getDouble("despawn-minutes", 5)) * 60_000);
        despawnRadius = Math.max(16, c.getDouble("despawn-radius", 128));
        oceanChance = clamp(c.getDouble("ocean.chance", 0.5), 0, 1);
        oceanDepth = Math.max(1, c.getInt("ocean.min-depth", 4));
        ocean = weights(c.getConfigurationSection("ocean.species"));
        elsewhereChance = clamp(c.getDouble("elsewhere.chance", 0.35), 0, 1);
        elsewhere = weights(c.getConfigurationSection("elsewhere.species"));

        List<EarthMap.Region> regions = new ArrayList<>();
        ConfigurationSection rs = c.getConfigurationSection("regions");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection r = rs.getConfigurationSection(id);
                if (r == null) continue;
                List<Double> lat = r.getDoubleList("latitude"), lon = r.getDoubleList("longitude");
                if (lat.size() != 2 || lon.size() != 2) {
                    plugin.getLogger().warning("natural-spawn.regions." + id + ": latitude y longitude van como [desde, hasta].");
                    continue;
                }
                regions.add(new EarthMap.Region(id, Math.min(lat.get(0), lat.get(1)), Math.max(lat.get(0), lat.get(1)),
                        Math.min(lon.get(0), lon.get(1)), Math.max(lon.get(0), lon.get(1)),
                        weights(r.getConfigurationSection("species"))));
            }
        }
        map = new EarthMap(c.getDouble("map.blocks-per-degree-x", 204.8), c.getDouble("map.x-at-longitude-0", 0),
                c.getDouble("map.blocks-per-degree-z", 204.8), c.getDouble("map.z-at-latitude-0", 0), regions);

        if (!enabled) return;
        long every = Math.max(20, Math.round(c.getDouble("interval-seconds", 30) * 20));
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnTick, every, every);
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 400L, 400L);
    }

    private Map<String, Integer> weights(ConfigurationSection s) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (s == null) return out;
        for (String id : s.getKeys(false)) {
            Species sp = Species.byId(id);
            if (sp == null || sp.rig == null || sp.isPlus()) {
                plugin.getLogger().warning("natural-spawn: especie desconocida o no permitida: " + id);
                continue;
            }
            int w = s.getInt(id, 0);
            if (w > 0) out.put(sp.id, w);
        }
        return out;
    }

    public EarthMap map() {
        return map;
    }

    // ------------------------------------------------------------ aparecer
    private void spawnTick() {
        if (!enabled) return;
        List<Location> natural = new ArrayList<>();
        for (Dino d : dinos.all()) {
            if (isNatural(d.wolf()) && !d.wolf().isTamed()) natural.add(d.wolf().getLocation());
        }
        int total = natural.size();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (total >= maxTotal) return;
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            if (!allowedWorld(p.getWorld()) || r.nextDouble() >= chance) continue;
            Location at = p.getLocation();
            int near = 0;
            for (Location l : natural) {
                if (l.getWorld() == at.getWorld() && l.distanceSquared(at) < nearRadius * nearRadius) near++;
            }
            if (near >= maxNear) continue;
            List<Dino> born = spawnAround(at, Math.min(maxNear - near, maxTotal - total));
            for (Dino d : born) natural.add(d.wolf().getLocation());
            total += born.size();
        }
    }

    private boolean allowedWorld(World w) {
        if (worlds.isEmpty()) return w.getEnvironment() == World.Environment.NORMAL;
        return worlds.contains(w.getName());
    }

    /** Si este mundo es el mapa de la Tierra (donde valen las coordenadas reales). */
    public boolean isMapWorld(World w) {
        return allowedWorld(w);
    }

    /** El mundo del mapa de la Tierra: el primero de "worlds", o el primer mundo normal. */
    public World mapWorld() {
        for (String name : worlds) {
            World w = Bukkit.getWorld(name);
            if (w != null) return w;
        }
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) return w;
        }
        return Bukkit.getWorlds().get(0);
    }

    public Map<String, Integer> oceanSpecies() {
        return ocean;
    }

    public Map<String, Integer> elsewhereSpecies() {
        return elsewhere;
    }

    /** Nombre de una region para mostrar (textos.yml regiones.<id>, o el id si no tiene). */
    public static String regionName(String id) {
        Lang lang = Lang.get();
        return lang.has("textos.yml", "regiones." + id) ? lang.plain("regiones." + id) : id;
    }

    /** Por que se descarto un lugar (o que salio bien): /dino mapa probar los cuenta. */
    public enum Reason { OK, CHUNK, NO_SPECIES, GROUND, PROTECTED, CITY }

    private record Attempt(Reason reason, Species species, Location spot, boolean water) {
    }

    /**
     * Elige un lugar a la espalda de {@code base} y revisa todo lo que tiene que cumplir. {@code dry}: para probar (no
     * tira las chances de las zonas comunes y el oceano, solo dice si se podria).
     */
    private Attempt attempt(Location base, boolean dry) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World w = base.getWorld();
        double yaw = behind ? base.getYaw() + 180 + r.nextDouble(-75, 75) : r.nextDouble(360);
        double dist = minDistance + r.nextDouble() * (maxDistance - minDistance);
        double rad = Math.toRadians(yaw);
        int x = (int) Math.floor(base.getX() - Math.sin(rad) * dist);
        int z = (int) Math.floor(base.getZ() + Math.cos(rad) * dist);
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return new Attempt(Reason.CHUNK, null, null, false);

        Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        boolean water = top.getType() == Material.WATER;
        Map<String, Integer> pool;
        double roll;
        if (water) {
            pool = ocean;
            roll = oceanChance;
        } else {
            pool = map.speciesAt(x, z);
            roll = 1;
            if (pool.isEmpty()) {
                pool = elsewhere;
                roll = elsewhereChance;
            }
        }
        if (pool.isEmpty() || !dry && r.nextDouble() >= roll) return new Attempt(Reason.NO_SPECIES, null, null, water);
        Species s = Species.byId(EarthMap.pick(pool, r.nextDouble()));
        if (s == null || s.rig == null || s.aquatic() != water) return new Attempt(Reason.NO_SPECIES, null, null, water);
        Location spot = water ? waterSpot(top, s) : landSpot(top, s);
        if (spot == null) return new Attempt(Reason.GROUND, s, null, water);
        if (onlyWild && !Wilderness.isWild(spot)) return new Attempt(Reason.PROTECTED, s, spot, water);
        // lejos de los pueblos de Towny y las regiones de WorldGuard (lo ultimo: es lo que mas cuesta)
        if (cityDistance > 0 && !Wilderness.farFromCities(spot, cityDistance)) return new Attempt(Reason.CITY, s, spot, water);
        return new Attempt(Reason.OK, s, spot, water);
    }

    /**
     * Intenta un grupo cerca de {@code base} (la posicion del jugador; "a sus espaldas" segun su yaw). Devuelve los que
     * aparecieron (vacio si no se pudo). Publico para poder probarlo en un server sin jugadores.
     */
    public List<Dino> spawnAround(Location base, int room) {
        Attempt a = attempt(base, false);
        if (a.reason() != Reason.OK) return List.of();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World w = base.getWorld();
        Species s = a.species();
        Location spot = a.spot();
        boolean water = a.water();

        int count = s.group > 1 ? r.nextInt(Math.max(1, s.group - 2), s.group + 1) : 1;
        count = Math.max(1, Math.min(count, room));
        List<Dino> born = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Location at = spot;
            if (i > 0) {
                int ox = spot.getBlockX() + r.nextInt(-3, 4), oz = spot.getBlockZ() + r.nextInt(-3, 4);
                if (w.isChunkLoaded(ox >> 4, oz >> 4)) {   // nunca cargar un chunk para esto
                    Block b = w.getHighestBlockAt(ox, oz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    Location other = water ? waterSpot(b, s) : landSpot(b, s);
                    if (other != null) at = other;
                }
            }
            at = at.clone();
            at.setYaw(r.nextFloat() * 360f);
            // en las manadas, alguna cria
            float growth = i > 0 && s.hasBabies() && r.nextDouble() < 0.25 ? 0.2f : 1f;
            Dino d = dinos.spawn(s, at, null, null, growth, null);
            if (d == null) continue;
            PersistentDataContainer pdc = d.wolf().getPersistentDataContainer();
            pdc.set(keys.natural, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keys.naturalSeen, PersistentDataType.LONG, System.currentTimeMillis());
            born.add(d);
        }
        return born;
    }

    /** Arriba de un bloque firme, con lugar para el tamaño del dino. */
    private static Location landSpot(Block top, Species s) {
        Material m = top.getType();
        if (!m.isSolid() || m == Material.CACTUS || m == Material.MAGMA_BLOCK || m == Material.POWDER_SNOW) return null;
        int height = (int) Math.ceil(0.85 * s.hitbox) + 1;
        for (int i = 1; i <= height; i++) {
            Block b = top.getRelative(BlockFace.UP, i);
            if (!b.isPassable() || b.isLiquid()) return null;
        }
        if (s.hitbox >= 2) {
            Block feet = top.getRelative(BlockFace.UP);
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block b = feet.getRelative(f);
                if (!b.isPassable() || b.isLiquid()) return null;
            }
        }
        return top.getLocation().add(0.5, 1, 0.5);
    }

    /** Agua honda: unos bloques abajo de la superficie. */
    private Location waterSpot(Block top, Species s) {
        if (top.getType() != Material.WATER) return null;
        int depth = 0;
        Block b = top;
        while (b.getType() == Material.WATER && depth < 64) {
            depth++;
            b = b.getRelative(BlockFace.DOWN);
        }
        if (depth < oceanDepth) return null;
        return top.getLocation().add(0.5, -Math.min(depth - 1, 2), 0.5);
    }

    // ------------------------------------------------------------ desaparecer
    private boolean isNatural(Wolf w) {
        return w.getPersistentDataContainer().has(keys.natural, PersistentDataType.BYTE);
    }

    /** Se puede borrar si queda solo: salvaje y suelto (ni domesticado, ni prestado, ni atado, ni cuidando un nido). */
    private boolean removable(Wolf w) {
        if (!w.isValid() || w.isDead() || w.isTamed() || w.isLeashed() || !isNatural(w)) return false;
        PersistentDataContainer pdc = w.getPersistentDataContainer();
        return !pdc.has(keys.rental) && !pdc.has(keys.guarding);
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        List<Wolf> gone = new ArrayList<>();
        for (Dino d : dinos.all()) {
            Wolf w = d.wolf();
            if (d.isDying() || !removable(w)) continue;
            Location at = w.getLocation();
            boolean near = false;
            for (Player p : at.getWorld().getPlayers()) {
                if (p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(at) < despawnRadius * despawnRadius) {
                    near = true;
                    break;
                }
            }
            PersistentDataContainer pdc = w.getPersistentDataContainer();
            if (near) pdc.set(keys.naturalSeen, PersistentDataType.LONG, now);
            else if (now - pdc.getOrDefault(keys.naturalSeen, PersistentDataType.LONG, now) > despawnMs) gone.add(w);
        }
        for (Wolf w : gone) w.remove();
    }

    /** Un chunk que estuvo abandonado mucho tiempo: sus salvajes naturales ya no tienen que estar. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<Wolf> wolves = new ArrayList<>();
        for (Entity e : event.getEntities()) {
            if (e instanceof Wolf w && isNatural(w)) wolves.add(w);
        }
        if (wolves.isEmpty()) return;
        // al tick siguiente: borrar durante la carga del chunk no esta permitido
        Bukkit.getScheduler().runTask(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Wolf w : wolves) {
                if (!removable(w)) continue;
                long seen = w.getPersistentDataContainer().getOrDefault(keys.naturalSeen, PersistentDataType.LONG, now);
                if (now - seen > despawnMs) w.remove();
            }
        });
    }

    // ------------------------------------------------------------ /dino mapa probar
    /** Prueba {@code tries} lugares a la espalda del jugador sin spawnear nada y dice por que se descarta cada uno. */
    public void probe(Player p, int tries) {
        Lang lang = Lang.get();
        if (!enabled) {
            lang.chat(p, "mapa-apagado");
            return;
        }
        if (!allowedWorld(p.getWorld())) {
            lang.chat(p, "mapa-otro-mundo");
            return;
        }
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
            lang.chat(p, "mapa-prueba-modo", "modo", lang.plain("mapa.modos." + p.getGameMode().name()));
        }
        Location at = p.getLocation();
        int near = 0, total = 0;
        for (Dino d : dinos.all()) {
            if (!isNatural(d.wolf()) || d.wolf().isTamed()) continue;
            total++;
            Location l = d.wolf().getLocation();
            if (l.getWorld() == at.getWorld() && l.distanceSquared(at) < nearRadius * nearRadius) near++;
        }
        if (near >= maxNear) lang.chat(p, "mapa-prueba-tope", "cerca", near, "tope", maxNear);
        if (total >= maxTotal) lang.chat(p, "mapa-prueba-total", "total", total, "tope", maxTotal);
        Map<Reason, Integer> count = new java.util.EnumMap<>(Reason.class);
        Map<String, Integer> could = new LinkedHashMap<>();
        java.util.Set<String> wg = new java.util.TreeSet<>();
        for (int i = 0; i < tries; i++) {
            Attempt a = attempt(at, true);
            count.merge(a.reason(), 1, Integer::sum);
            if (a.reason() == Reason.OK) could.merge(a.species().id, 1, Integer::sum);
            if (a.reason() == Reason.PROTECTED) wg.addAll(Wilderness.regionsAt(a.spot()));
        }
        lang.chat(p, "mapa-prueba", "intentos", tries);
        lang.chat(p, "mapa-prueba-ok", "ok", count.getOrDefault(Reason.OK, 0), "lista", names(could));
        lang.chat(p, "mapa-prueba-motivos", "chunk", count.getOrDefault(Reason.CHUNK, 0),
                "suelo", count.getOrDefault(Reason.GROUND, 0), "protegido", count.getOrDefault(Reason.PROTECTED, 0),
                "ciudad", count.getOrDefault(Reason.CITY, 0), "especies", count.getOrDefault(Reason.NO_SPECIES, 0));
        if (!wg.isEmpty()) lang.chat(p, "mapa-regiones-wg", "lista", String.join(", ", wg));
    }

    // ------------------------------------------------------------ /dino mapa
    /** Donde estas en el mundo real y que dinos aparecen ahi. */
    public void info(Player p) {
        Lang lang = Lang.get();
        Location at = p.getLocation();
        double lat = map.lat(at.getZ()), lon = map.lon(at.getX());
        lang.chat(p, "mapa", "lat", coord(lat, "mapa.norte", "mapa.sur"), "lon", coord(lon, "mapa.este", "mapa.oeste"));
        if (!enabled) {
            lang.chat(p, "mapa-apagado");
            return;
        }
        if (!allowedWorld(at.getWorld())) {
            lang.chat(p, "mapa-otro-mundo");
            return;
        }
        if (onlyWild && !Wilderness.isWild(at)) {
            lang.chat(p, "mapa-protegido");
            List<String> ids = Wilderness.regionsAt(at);
            if (!ids.isEmpty()) lang.chat(p, "mapa-regiones-wg", "lista", String.join(", ", ids));
        }
        int[] cities = Wilderness.cityCounts();
        lang.chat(p, "mapa-ciudades", "terrenos", cities[0], "regiones", cities[1]);
        double city = Wilderness.nearestCity(at, 2000);
        Locale es = Locale.forLanguageTag("es-AR");
        if (city < 0) {
            lang.chat(p, "mapa-sin-ciudad", "max", String.format(es, "%,d", 2000));
        } else if (city < cityDistance) {
            lang.chat(p, "mapa-ciudad-cerca", "distancia", String.format(es, "%,d", Math.round(city)),
                    "minimo", String.format(es, "%,d", Math.round(cityDistance)));
        } else {
            lang.chat(p, "mapa-ciudad", "distancia", String.format(es, "%,d", Math.round(city)));
        }
        List<EarthMap.Region> here = map.regionsAt(at.getX(), at.getZ());
        if (here.isEmpty()) {
            lang.chat(p, "mapa-sin-region", "lista", names(elsewhere));
        } else {
            List<String> regionNames = new ArrayList<>();
            for (EarthMap.Region r : here) regionNames.add(regionName(r.id()));
            lang.chat(p, "mapa-region", "region", String.join(", ", regionNames), "lista", names(map.speciesAt(at.getX(), at.getZ())));
        }
        if (!ocean.isEmpty()) lang.chat(p, "mapa-oceano", "lista", names(ocean));
    }

    /** Nombres de las especies (para listas en el chat o los menus). */
    public static String names(Map<String, Integer> pool) {
        List<String> out = new ArrayList<>();
        for (String id : pool.keySet()) {
            Species s = Species.byId(id);
            if (s != null) out.add(s.name);
        }
        return out.isEmpty() ? "-" : String.join(", ", out);
    }

    /** "34,6° S": la latitud o longitud con su punto cardinal (textos.yml mapa.*). */
    public static String coord(double v, String positive, String negative) {
        return String.format(Locale.forLanguageTag("es-AR"), "%.1f° %s", Math.abs(v),
                Lang.get().plain(v >= 0 ? positive : negative));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

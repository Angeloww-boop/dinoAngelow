package com.dinoearth.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Si un lugar es "naturaleza": fuera de pueblos de Towny y de regiones de WorldGuard. Se usa para los nidos y el spawn
 * natural, que aparecen solos (no hay un jugador para preguntarle a los plugins de proteccion si puede construir).
 * Ademas, la distancia a la "ciudad" mas cerca (pueblo de Towny o region de WorldGuard), para que el spawn natural se
 * mantenga lejos: los terrenos de Towny y las cajas de las regiones se copian cada minuto a memoria, asi cada consulta
 * son unos cientos de busquedas en un HashSet y no cientos de llamadas a Towny/WorldGuard.
 * Todo por reflexion: sin esos plugins (o si cambian su API) se toma como naturaleza.
 */
public final class Wilderness {

    private static final long REFRESH_MS = 60_000;

    private static Object towny;
    private static Method townyWild;
    private static Object wgQuery;
    private static Method wgAdapt;
    private static Method wgRegions;
    private static Method wgSize;
    private static Object wgContainer;
    private static Logger log;
    private static boolean warned, cityWarned;

    // copia de las "ciudades": terrenos de Towny por mundo (x,z del terreno) y cajas de WorldGuard por mundo
    private static Map<String, Set<Long>> townBlocks = new HashMap<>();
    private static int townBlockSize = 16;
    private static Map<String, List<double[]>> wgBoxes = new HashMap<>();
    private static long refreshed;
    /** Regiones de WorldGuard que no cuentan (por ejemplo una que protege todo el mapa), en minusculas. */
    private static Set<String> ignored = Set.of();

    private Wilderness() {
    }

    public static void setup(Logger logger) {
        log = logger;
        towny = null;
        wgQuery = null;
        wgContainer = null;
        refreshed = 0;
        if (Bukkit.getPluginManager().getPlugin("Towny") != null) {
            try {
                Class<?> api = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
                towny = api.getMethod("getInstance").invoke(null);
                townyWild = api.getMethod("isWilderness", Location.class);
                log.info("Towny conectado: los nidos y los dinos salvajes solo aparecen fuera de los pueblos.");
            } catch (ReflectiveOperationException | LinkageError e) {
                towny = null;
                log.warning("No se pudo conectar con Towny: " + e);
            }
        }
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                Object wg = Class.forName("com.sk89q.worldguard.WorldGuard").getMethod("getInstance").invoke(null);
                Object platform = wg.getClass().getMethod("getPlatform").invoke(wg);
                Object container = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform")
                        .getMethod("getRegionContainer").invoke(platform);
                wgContainer = container;
                wgQuery = container.getClass().getMethod("createQuery").invoke(container);
                wgAdapt = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter").getMethod("adapt", Location.class);
                wgRegions = wgQuery.getClass().getMethod("getApplicableRegions",
                        Class.forName("com.sk89q.worldedit.util.Location"));
                wgSize = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet").getMethod("size");
                log.info("WorldGuard conectado: los nidos y los dinos salvajes solo aparecen fuera de las regiones.");
            } catch (ReflectiveOperationException | LinkageError e) {
                wgQuery = null;
                wgContainer = null;
                log.warning("No se pudo conectar con WorldGuard: " + e);
            }
        }
    }

    /** Regiones de WorldGuard que no cuentan como ciudad ni como zona protegida (natural-spawn.ignored-regions). */
    public static void setIgnored(Collection<String> ids) {
        Set<String> s = new HashSet<>();
        for (String id : ids) s.add(id.toLowerCase(java.util.Locale.ROOT));
        ignored = s;
        refreshed = 0;
    }

    public static boolean isWild(Location at) {
        try {
            if (towny != null && !(Boolean) townyWild.invoke(towny, at)) return false;
            if (wgQuery != null) {
                Object set = wgRegions.invoke(wgQuery, wgAdapt.invoke(null, at));
                if ((Integer) wgSize.invoke(set) > 0 && !regionIds(set).isEmpty()) return false;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned && log != null) {
                warned = true;
                log.warning("Fallo la consulta a Towny/WorldGuard (se toma como naturaleza): " + e);
            }
        }
        return true;
    }

    /** Ids de las regiones de WorldGuard que hay en este lugar (sin las ignoradas), para /dino mapa. */
    public static List<String> regionsAt(Location at) {
        if (wgQuery == null) return List.of();
        try {
            return regionIds(wgRegions.invoke(wgQuery, wgAdapt.invoke(null, at)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    private static List<String> regionIds(Object set) throws ReflectiveOperationException {
        List<String> out = new ArrayList<>();
        for (Object r : (Iterable<?>) set) {
            String id = String.valueOf(r.getClass().getMethod("getId").invoke(r));
            String low = id.toLowerCase(java.util.Locale.ROOT);
            if (!low.equals("__global__") && !ignored.contains(low)) out.add(id);
        }
        return out;
    }

    // ------------------------------------------------------------ distancia a las ciudades
    /** Si no hay ningun pueblo de Towny ni region de WorldGuard a menos de {@code radius} bloques (en horizontal). */
    public static boolean farFromCities(Location at, double radius) {
        if (radius <= 0) return true;
        refresh();
        String w = at.getWorld().getName();
        return !nearTownBlocks(townBlocks.get(w), townBlockSize, at.getX(), at.getZ(), radius)
                && !nearBoxes(wgBoxes.get(w), at.getX(), at.getZ(), radius);
    }

    /** Distancia a la ciudad mas cerca hasta {@code max} bloques, o -1 si no hay ninguna (para /dino mapa). */
    public static double nearestCity(Location at, double max) {
        refresh();
        String w = at.getWorld().getName();
        double best = Math.min(nearestTownBlock(townBlocks.get(w), townBlockSize, at.getX(), at.getZ(), max),
                nearestBox(wgBoxes.get(w), at.getX(), at.getZ()));
        return best <= max ? best : -1;
    }

    /** Cuantas ciudades se conocen (terrenos de Towny, regiones de WorldGuard), para el log y /dino mapa. */
    public static int[] cityCounts() {
        refresh();
        int tb = 0, boxes = 0;
        for (Set<Long> s : townBlocks.values()) tb += s.size();
        for (List<double[]> l : wgBoxes.values()) boxes += l.size();
        return new int[]{tb, boxes};
    }

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now - refreshed < REFRESH_MS) return;
        refreshed = now;
        Map<String, Set<Long>> tbs = new HashMap<>();
        Map<String, List<double[]>> boxes = new HashMap<>();
        try {
            if (towny != null) readTowny(tbs);
            if (wgContainer != null) readWorldGuard(boxes);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!cityWarned && log != null) {
                cityWarned = true;
                log.warning("No se pudieron leer los pueblos/regiones para la distancia del spawn natural: " + e);
            }
        }
        townBlocks = tbs;
        wgBoxes = boxes;
    }

    private static void readTowny(Map<String, Set<Long>> out) throws ReflectiveOperationException {
        try {
            townBlockSize = (Integer) Class.forName("com.palmergames.bukkit.towny.TownySettings")
                    .getMethod("getTownBlockSize").invoke(null);
        } catch (ReflectiveOperationException e) {
            townBlockSize = 16;
        }
        Collection<?> all;
        try {
            all = (Collection<?>) towny.getClass().getMethod("getTownBlocks").invoke(towny);
        } catch (NoSuchMethodException e) {
            Object universe = Class.forName("com.palmergames.bukkit.towny.TownyUniverse").getMethod("getInstance").invoke(null);
            all = ((Map<?, ?>) universe.getClass().getMethod("getTownBlocks").invoke(universe)).values();
        }
        for (Object tb : all) {
            int x = (Integer) tb.getClass().getMethod("getX").invoke(tb);
            int z = (Integer) tb.getClass().getMethod("getZ").invoke(tb);
            Object world = tb.getClass().getMethod("getWorld").invoke(tb);
            String name = (String) world.getClass().getMethod("getName").invoke(world);
            out.computeIfAbsent(name, k -> new HashSet<>()).add(key(x, z));
        }
    }

    private static void readWorldGuard(Map<String, List<double[]>> out) throws ReflectiveOperationException {
        Method adaptWorld = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter").getMethod("adapt", World.class);
        Method get = null;
        for (Method m : wgContainer.getClass().getMethods()) {
            if (m.getName().equals("get") && m.getParameterCount() == 1) get = m;
        }
        if (get == null) return;
        for (World w : Bukkit.getWorlds()) {
            Object manager = get.invoke(wgContainer, adaptWorld.invoke(null, w));
            if (manager == null) continue;
            Map<?, ?> regions = (Map<?, ?>) manager.getClass().getMethod("getRegions").invoke(manager);
            for (Map.Entry<?, ?> e : regions.entrySet()) {
                String id = String.valueOf(e.getKey()).toLowerCase(java.util.Locale.ROOT);
                if (id.equals("__global__") || ignored.contains(id)) continue;
                Object r = e.getValue();
                Object min = r.getClass().getMethod("getMinimumPoint").invoke(r);
                Object max = r.getClass().getMethod("getMaximumPoint").invoke(r);
                out.computeIfAbsent(w.getName(), k -> new ArrayList<>())
                        .add(new double[]{coord(min, "x"), coord(min, "z"), coord(max, "x") + 1, coord(max, "z") + 1});
            }
        }
    }

    /** BlockVector3: x()/z() en WorldEdit 7.3+, getX()/getBlockX() en los anteriores. */
    private static double coord(Object v, String axis) throws ReflectiveOperationException {
        String up = axis.toUpperCase();
        for (String name : new String[]{axis, "get" + up, "getBlock" + up}) {
            try {
                return ((Number) v.getClass().getMethod(name).invoke(v)).doubleValue();
            } catch (NoSuchMethodException ignored) {
                // probar el siguiente nombre
            }
        }
        throw new NoSuchMethodException("BlockVector3." + axis);
    }

    // ------------------------------------------------------------ cuentas (sin Bukkit, las prueba CityCheck)
    public static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Distancia de un punto a un rectangulo (0 si esta adentro). */
    public static double toBox(double x, double z, double minX, double minZ, double maxX, double maxZ) {
        double dx = Math.max(Math.max(minX - x, 0), x - maxX);
        double dz = Math.max(Math.max(minZ - z, 0), z - maxZ);
        return Math.hypot(dx, dz);
    }

    /** Si algun terreno (cuadrado de {@code size} bloques) queda a menos de {@code r} del punto. */
    public static boolean nearTownBlocks(Set<Long> blocks, int size, double x, double z, double r) {
        if (blocks == null || blocks.isEmpty()) return false;
        int x0 = (int) Math.floor((x - r) / size), x1 = (int) Math.floor((x + r) / size);
        int z0 = (int) Math.floor((z - r) / size), z1 = (int) Math.floor((z + r) / size);
        for (int tx = x0; tx <= x1; tx++) {
            for (int tz = z0; tz <= z1; tz++) {
                if (blocks.contains(key(tx, tz))
                        && toBox(x, z, tx * size, tz * size, (tx + 1) * size, (tz + 1) * size) < r) return true;
            }
        }
        return false;
    }

    public static double nearestTownBlock(Set<Long> blocks, int size, double x, double z, double max) {
        double best = Double.MAX_VALUE;
        if (blocks == null || blocks.isEmpty()) return best;
        int x0 = (int) Math.floor((x - max) / size), x1 = (int) Math.floor((x + max) / size);
        int z0 = (int) Math.floor((z - max) / size), z1 = (int) Math.floor((z + max) / size);
        for (int tx = x0; tx <= x1; tx++) {
            for (int tz = z0; tz <= z1; tz++) {
                if (blocks.contains(key(tx, tz))) {
                    best = Math.min(best, toBox(x, z, tx * size, tz * size, (tx + 1) * size, (tz + 1) * size));
                }
            }
        }
        return best;
    }

    public static boolean nearBoxes(List<double[]> boxes, double x, double z, double r) {
        return nearestBox(boxes, x, z) < r;
    }

    public static double nearestBox(List<double[]> boxes, double x, double z) {
        double best = Double.MAX_VALUE;
        if (boxes == null) return best;
        for (double[] b : boxes) best = Math.min(best, toBox(x, z, b[0], b[1], b[2], b[3]));
        return best;
    }
}

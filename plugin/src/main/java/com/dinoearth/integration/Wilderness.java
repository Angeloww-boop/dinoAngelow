package com.dinoearth.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Si un lugar es "naturaleza": fuera de pueblos de Towny y de regiones de WorldGuard. Se usa para los nidos, que
 * aparecen solos (no hay un jugador para preguntarle a los plugins de proteccion si puede construir).
 * Todo por reflexion: sin esos plugins (o si cambian su API) se toma como naturaleza.
 */
public final class Wilderness {

    private static Object towny;
    private static Method townyWild;
    private static Object wgQuery;
    private static Method wgAdapt;
    private static Method wgRegions;
    private static Method wgSize;
    private static Logger log;
    private static boolean warned;

    private Wilderness() {
    }

    public static void setup(Logger logger) {
        log = logger;
        towny = null;
        wgQuery = null;
        if (Bukkit.getPluginManager().getPlugin("Towny") != null) {
            try {
                Class<?> api = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
                towny = api.getMethod("getInstance").invoke(null);
                townyWild = api.getMethod("isWilderness", Location.class);
                log.info("Towny conectado: los nidos solo aparecen fuera de los pueblos.");
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
                wgQuery = container.getClass().getMethod("createQuery").invoke(container);
                wgAdapt = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter").getMethod("adapt", Location.class);
                wgRegions = wgQuery.getClass().getMethod("getApplicableRegions",
                        Class.forName("com.sk89q.worldedit.util.Location"));
                wgSize = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet").getMethod("size");
                log.info("WorldGuard conectado: los nidos solo aparecen fuera de las regiones.");
            } catch (ReflectiveOperationException | LinkageError e) {
                wgQuery = null;
                log.warning("No se pudo conectar con WorldGuard: " + e);
            }
        }
    }

    public static boolean isWild(Location at) {
        try {
            if (towny != null && !(Boolean) townyWild.invoke(towny, at)) return false;
            if (wgQuery != null) {
                Object set = wgRegions.invoke(wgQuery, wgAdapt.invoke(null, at));
                if ((Integer) wgSize.invoke(set) > 0) return false;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned && log != null) {
                warned = true;
                log.warning("Fallo la consulta a Towny/WorldGuard (se toma como naturaleza): " + e);
            }
        }
        return true;
    }
}

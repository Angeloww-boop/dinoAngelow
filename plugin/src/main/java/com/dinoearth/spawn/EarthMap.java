package com.dinoearth.spawn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El mapa de la Tierra a escala (equirectangular): pasa de bloques a latitud/longitud y dice en que regiones de fosiles
 * cae cada lugar. Sin Bukkit, para poder probarlo solo (MapCheck).
 *
 * x = bloquesPorGradoX * longitud + xEnLongitud0
 * z = -bloquesPorGradoZ * latitud + zEnLatitud0      (en Minecraft el norte es -z)
 */
public final class EarthMap {

    /** Una zona del mundo real (caja de latitud/longitud) con las especies que aparecen ahi y su peso. */
    public record Region(String id, double latMin, double latMax, double lonMin, double lonMax,
                         Map<String, Integer> species) {

        public boolean contains(double lat, double lon) {
            return lat >= latMin && lat <= latMax && lon >= lonMin && lon <= lonMax;
        }
    }

    private final double perDegreeX, xAtLon0, perDegreeZ, zAtLat0;
    private final List<Region> regions;

    public EarthMap(double perDegreeX, double xAtLon0, double perDegreeZ, double zAtLat0, List<Region> regions) {
        this.perDegreeX = perDegreeX;
        this.xAtLon0 = xAtLon0;
        this.perDegreeZ = perDegreeZ;
        this.zAtLat0 = zAtLat0;
        this.regions = List.copyOf(regions);
    }

    public double lon(double x) {
        return (x - xAtLon0) / perDegreeX;
    }

    public double lat(double z) {
        return (zAtLat0 - z) / perDegreeZ;
    }

    public double x(double lon) {
        return perDegreeX * lon + xAtLon0;
    }

    public double z(double lat) {
        return zAtLat0 - perDegreeZ * lat;
    }

    public List<Region> regions() {
        return regions;
    }

    /** Regiones que contienen este bloque (pueden superponerse: se suman sus especies). */
    public List<Region> regionsAt(double x, double z) {
        double lat = lat(z), lon = lon(x);
        List<Region> out = new ArrayList<>();
        for (Region r : regions) {
            if (r.contains(lat, lon)) out.add(r);
        }
        return out;
    }

    /** Especies (con peso) de todas las regiones que contienen el bloque; vacio si no cae en ninguna. */
    public Map<String, Integer> speciesAt(double x, double z) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Region r : regionsAt(x, z)) r.species().forEach((k, v) -> out.merge(k, v, Integer::sum));
        return out;
    }

    /** Distancia en bloques (horizontal) del bloque x, z a la region (0 si esta adentro). */
    public double distance(Region r, double x, double z) {
        double x0 = this.x(r.lonMin()), x1 = this.x(r.lonMax());
        double z0 = Math.min(this.z(r.latMin()), this.z(r.latMax())), z1 = Math.max(this.z(r.latMin()), this.z(r.latMax()));
        double dx = Math.max(Math.max(x0 - x, 0), x - x1), dz = Math.max(Math.max(z0 - z, 0), z - z1);
        return Math.hypot(dx, dz);
    }

    /** Elige una especie segun los pesos; {@code roll} entre 0 (incluido) y 1. null si no hay ninguna. */
    public static String pick(Map<String, Integer> weights, double roll) {
        int total = 0;
        for (int w : weights.values()) total += Math.max(0, w);
        if (total <= 0) return null;
        double target = roll * total;
        String last = null;
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            if (e.getValue() <= 0) continue;
            last = e.getKey();
            target -= e.getValue();
            if (target < 0) return last;
        }
        return last;   // redondeo con roll casi 1
    }
}

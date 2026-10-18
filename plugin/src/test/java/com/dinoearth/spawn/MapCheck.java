package com.dinoearth.spawn;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Prueba sin servidor el mapa de la Tierra: con la calibracion y las regiones del config.yml de verdad, las coordenadas
 * que paso Angeloww (F3 en el server) tienen que dar su lugar real, y ciudades conocidas tienen que caer en su region.
 * Uso: java -cp ... com.dinoearth.spawn.MapCheck plugin/src/main/resources/config.yml
 */
public final class MapCheck {

    private static int fails;

    public static void main(String[] args) {
        ConfigurationSection c = YamlConfiguration.loadConfiguration(new File(args[0])).getConfigurationSection("natural-spawn");
        List<EarthMap.Region> regions = new ArrayList<>();
        ConfigurationSection rs = c.getConfigurationSection("regions");
        for (String id : rs.getKeys(false)) {
            ConfigurationSection r = rs.getConfigurationSection(id);
            List<Double> lat = r.getDoubleList("latitude"), lon = r.getDoubleList("longitude");
            Map<String, Integer> sp = new LinkedHashMap<>();
            for (String s : r.getConfigurationSection("species").getKeys(false)) sp.put(s, r.getInt("species." + s));
            regions.add(new EarthMap.Region(id, lat.get(0), lat.get(1), lon.get(0), lon.get(1), sp));
        }
        EarthMap map = new EarthMap(c.getDouble("map.blocks-per-degree-x"), c.getDouble("map.x-at-longitude-0"),
                c.getDouble("map.blocks-per-degree-z"), c.getDouble("map.z-at-latitude-0"), regions);

        // esquinas del mapa (las paso Angeloww): -36864 -18431 arriba a la izquierda, 36863 18431 abajo a la derecha
        near("esquina noroeste", map, -36864, -18431, 90, -180, 0.01);
        near("esquina sudeste", map, 36863, 18431, -90, 180, 0.01);
        near("centro", map, 0, 0, 0, 0, 1e-9);
        // coordenadas tomadas en el server (F3), aproximadas: solo tienen que caer cerca
        near("Buenos Aires (F3)", map, -11917, 7431, -34.60, -58.38, 2.0);
        near("Washington (F3, aproximado)", map, -15751, -7918, 38.91, -77.04, 1.0);
        check("Japon (F3, aproximado): longitud de Japon", map.lon(28846) > 129 && map.lon(28846) < 146);
        check("Japon (F3, aproximado): latitud dentro de Japon", map.lat(-7906) > 31 && map.lat(-7906) < 45);

        // ciudades -> region
        region(map, "Buenos Aires", -34.60, -58.38);
        region(map, "Neuquen", -38.95, -68.06, "patagonia");
        region(map, "Trelew (Chubut)", -43.25, -65.31, "patagonia");
        region(map, "Bozeman (Montana)", 45.68, -111.04, "oeste-norteamerica");
        region(map, "Drumheller (Alberta)", 51.46, -112.71, "oeste-norteamerica");
        region(map, "Phoenix (Arizona)", 33.45, -112.07, "sudoeste-eeuu");
        region(map, "Kansas", 38.5, -98.0, "grandes-llanuras");
        region(map, "Ulan Bator", 47.92, 106.92, "gobi");
        region(map, "El Cairo", 30.04, 31.24, "norte-africa");
        region(map, "Erfoud (Marruecos)", 31.43, -4.23, "norte-africa");
        region(map, "Berlin", 52.52, 13.40, "europa-central");
        region(map, "Londres", 51.51, -0.13, "europa-central", "inglaterra");
        region(map, "Lyme Regis (Inglaterra)", 50.72, -2.94, "inglaterra");
        region(map, "Tokio", 35.68, 139.69);
        region(map, "Sidney", -33.87, 151.21);

        // ida y vuelta bloques <-> grados
        check("ida y vuelta", Math.abs(map.lon(map.x(-58.38)) + 58.38) < 1e-9 && Math.abs(map.lat(map.z(-34.6)) + 34.6) < 1e-9);

        // el sorteo respeta los pesos
        Map<String, Integer> w = new LinkedHashMap<>();
        w.put("a", 1);
        w.put("b", 3);
        Map<String, Integer> got = new HashMap<>();
        Random r = new Random(1);
        for (int i = 0; i < 40000; i++) got.merge(EarthMap.pick(w, r.nextDouble()), 1, Integer::sum);
        double share = got.getOrDefault("b", 0) / 40000.0;
        check("pesos 1:3 (b salio " + share + ")", Math.abs(share - 0.75) < 0.02);
        check("roll casi 1", "b".equals(EarthMap.pick(w, 0.9999999999)));
        check("sin especies", EarthMap.pick(Map.of(), 0.5) == null);

        System.out.println(fails == 0 ? "todo ok" : fails + " fallas");
        if (fails > 0) System.exit(1);
    }

    private static void near(String name, EarthMap map, double x, double z, double lat, double lon, double tol) {
        double gotLat = map.lat(z), gotLon = map.lon(x);
        System.out.printf("%-28s lat %7.2f lon %8.2f%n", name, gotLat, gotLon);
        check(name, Math.abs(gotLat - lat) < tol && Math.abs(gotLon - lon) < tol);
    }

    private static void region(EarthMap map, String name, double lat, double lon, String... expected) {
        List<String> ids = new ArrayList<>();
        for (EarthMap.Region r : map.regionsAt(map.x(lon), map.z(lat))) ids.add(r.id());
        System.out.printf("%-28s x %7d z %7d -> %s %s%n", name, Math.round(map.x(lon)), Math.round(map.z(lat)), ids,
                map.speciesAt(map.x(lon), map.z(lat)).keySet());
        check(name + " -> " + List.of(expected), ids.equals(List.of(expected)));
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            fails++;
            System.out.println("FALLA: " + name);
        }
    }
}

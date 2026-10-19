package com.dinoearth.spawn;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.map.MapPalette;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dibuja el Mapa del Paleontologo como lo ve el jugador, sin el juego: la Tierra y las huellas de las regiones del config,
 * pasado por la paleta de los mapas de Minecraft (MapPalette de Bukkit) y con una flecha de ejemplo.
 * Uso: java -cp ... com.dinoearth.spawn.MapPreview plugin/src/main/resources salida.png [lat lon]
 */
public final class MapPreview {

    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        ConfigurationSection c = YamlConfiguration.loadConfiguration(new File(res, "config.yml")).getConfigurationSection("natural-spawn");
        YamlConfiguration textos = YamlConfiguration.loadConfiguration(new File(res, "textos.yml"));
        List<EarthMap.Region> regions = new ArrayList<>();
        ConfigurationSection rs = c.getConfigurationSection("regions");
        for (String id : rs.getKeys(false)) {
            ConfigurationSection r = rs.getConfigurationSection(id);
            List<Double> lat = r.getDoubleList("latitude"), lon = r.getDoubleList("longitude");
            Map<String, Integer> sp = new LinkedHashMap<>();
            for (String s : r.getConfigurationSection("species").getKeys(false)) sp.put(s, r.getInt("species." + s));
            regions.add(new EarthMap.Region(id, lat.get(0), lat.get(1), lon.get(0), lon.get(1), sp));
        }

        BufferedImage map = ImageIO.read(new File(res, "mapa/tierra.png"));
        PaleoMap.drawRegions(regions, (x, y, col) -> map.setRGB(x, y, col.getRGB()));

        // paleta de los mapas de Minecraft, como hace el server al dibujar
        BufferedImage out = new BufferedImage(128 * 5, 128 * 5, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                @SuppressWarnings("deprecation")
                Color col = MapPalette.getColor(MapPalette.matchColor(new Color(map.getRGB(x, y))));
                for (int dy = 0; dy < 5; dy++) for (int dx = 0; dx < 5; dx++) out.setRGB(x * 5 + dx, y * 5 + dy, col.getRGB());
            }
        }
        if (args.length > 3) {   // jugador de ejemplo: un rombo blanco
            double lat = Double.parseDouble(args[2]), lon = Double.parseDouble(args[3]);
            int cx = (int) (PaleoMap.fx(lon) * 5), cy = (int) (PaleoMap.fy(lat) * 5);
            for (int dy = -6; dy <= 6; dy++) for (int dx = -6; dx <= 6; dx++) {
                if (Math.abs(dx) + Math.abs(dy) <= 6 && cx + dx >= 0 && cy + dy >= 0 && cx + dx < 640 && cy + dy < 640) {
                    out.setRGB(cx + dx, cy + dy, Math.abs(dx) + Math.abs(dy) >= 5 ? 0x000000 : 0xFFFFFF);
                }
            }
        }
        ImageIO.write(out, "png", new File(args[1]));
        System.out.println("listo: " + args[1]);
    }
}

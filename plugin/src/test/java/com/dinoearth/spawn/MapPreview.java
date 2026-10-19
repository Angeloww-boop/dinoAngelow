package com.dinoearth.spawn;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MinecraftFont;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dibuja el Mapa del Paleontologo como lo ve el jugador, sin el juego: la Tierra, las regiones del config y los textos,
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
        Color pink = new Color(242, 127, 165);
        for (EarthMap.Region r : regions) {
            int x0 = PaleoMap.px(r.lonMin()), x1 = Math.max(x0, PaleoMap.px(r.lonMax()) - 1);
            int y0 = PaleoMap.py(r.latMax()), y1 = Math.max(y0, PaleoMap.py(r.latMin()) - 1);
            for (int x = x0; x <= x1; x++) {
                map.setRGB(x, y0, pink.getRGB());
                map.setRGB(x, y1, pink.getRGB());
            }
            for (int y = y0; y <= y1; y++) {
                map.setRGB(x0, y, pink.getRGB());
                map.setRGB(x1, y, pink.getRGB());
            }
        }
        text(map, textos.getString("mapa.titulo"), 12);
        text(map, textos.getString("mapa.leyenda"), 128 - 32 + 12);

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
            int cx = (int) ((lon + 180) / 360 * 128 * 5), cy = (int) ((32 + (90 - lat) / 180 * 64) * 5);
            for (int dy = -6; dy <= 6; dy++) for (int dx = -6; dx <= 6; dx++) {
                if (Math.abs(dx) + Math.abs(dy) <= 6 && cx + dx >= 0 && cy + dy >= 0 && cx + dx < 640 && cy + dy < 640) {
                    out.setRGB(cx + dx, cy + dy, Math.abs(dx) + Math.abs(dy) >= 5 ? 0x000000 : 0xFFFFFF);
                }
            }
        }
        ImageIO.write(out, "png", new File(args[1]));
        System.out.println("listo: " + args[1]);
    }

    /** Igual que PaleoMap.text: centrado, sin tildes, con la fuente de los mapas (gris oscuro). */
    private static void text(BufferedImage img, String raw, int y) {
        String s = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (!MinecraftFont.Font.isValid(s)) {
            System.out.println("texto con letras que la fuente no tiene: " + s);
            return;
        }
        int w = MinecraftFont.Font.getWidth(s);
        System.out.println("'" + s + "' mide " + w + " px de 124");
        int x = (128 - w) / 2;
        for (char ch : s.toCharArray()) {
            MapFont.CharacterSprite sp = MinecraftFont.Font.getChar(ch);
            for (int r = 0; r < MinecraftFont.Font.getHeight(); r++) {
                for (int col = 0; col < sp.getWidth(); col++) {
                    if (sp.get(r, col)) img.setRGB(x + col, y + r, new Color(80, 80, 80).getRGB());
                }
            }
            x += sp.getWidth() + 1;
        }
    }
}

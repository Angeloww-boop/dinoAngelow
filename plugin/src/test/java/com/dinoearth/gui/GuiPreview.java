package com.dinoearth.gui;

import com.dinoearth.dex.DexLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma los titulos de los menus de la Dinopedia con datos de ejemplo (el mismo DexLayout que usa el plugin) y los
 * guarda como JSON de componentes. generator/gui_preview.py los dibuja como lo haria el cliente.
 *
 * Uso: java -cp target/classes;target/test-classes;(dependencias) com.dinoearth.gui.GuiPreview gui.json salida.json
 */
public final class GuiPreview {

    public static void main(String[] args) throws Exception {
        GuiFont gui;
        try (var in = new FileInputStream(args[0])) {
            gui = GuiFont.load(in);
        }
        Map<String, Map<String, Object>> screens = new LinkedHashMap<>();

        List<Integer> bars = new ArrayList<>(List.of(8, 5, -1, 3, 8, 1, -1, 2, 0, -1, 4, 6, -1));
        List<Boolean> complete = new ArrayList<>(List.of(true, false, false, false, true, false, false, false, false,
                false, false, false, false));
        String[] ids = {"raptor", "triceratops", "dilophosaurus", "compsognathus", "trex", "pteranodon", "parasaurolophus",
                "gallimimus", "stegosaurus", "ankylosaurus", "giganotosaurus", "carnotaurus", "dimorphodon"};
        Map<String, String> slots = new LinkedHashMap<>();
        int[][] rows = {{10, 11, 12, 13, 14, 15, 16}, {28, 29, 30, 31, 32, 33, 34}};
        for (int i = 0; i < ids.length; i++) {
            int slot = rows[i / 7][i % 7];
            slots.put(String.valueOf(slot), "dinoearth:icon/" + ids[i] + "/" + (bars.get(i) < 0 ? "unknown" : firstVariant(ids[i])));
        }
        slots.put("0", "dinoearth:gui/rank3");
        slots.put("46", "dinoearth:gui/track");
        slots.put("47", "dinoearth:gui/help");
        slots.put("49", "minecraft:barrier");
        slots.put("51", "dinoearth:gui/missions");
        screens.put("indice", screen(DexLayout.index(gui, new DexLayout.IndexView("Dinopedia", "Naturalista",
                TextColor.color(0x9A7414), 31, 66, bars, complete)), slots));

        slots = new LinkedHashMap<>();
        String[] colors = {"pardo", "oliva", "pizarra", "rojizo", "albino"};
        int[] colorSlots = {36, 37, 38, 39, 46, 47, 48};
        for (int i = 0; i < colors.length; i++) {
            slots.put(String.valueOf(colorSlots[i]), "dinoearth:icon/triceratops/" + (i == 4 ? "unknown" : colors[i]));
        }
        String[] stamps = {"visto", "colores_off", "raro_off", "domesticado", "criado_off", "jinete"};
        int[] stampSlots = {41, 42, 43, 50, 51, 52};
        for (int i = 0; i < stamps.length; i++) slots.put(String.valueOf(stampSlots[i]), "dinoearth:gui/stamp/" + stamps[i]);
        slots.put("44", "dinoearth:gui/track_off");
        slots.put("45", "dinoearth:gui/prev");
        slots.put("49", "minecraft:barrier");
        slots.put("53", "dinoearth:gui/next");
        screens.put("especie", screen(DexLayout.species(gui, new DexLayout.SpeciesView("triceratops", "pizarra",
                "Triceratops", "leaf", "Herbívoro", List.of(
                new DexLayout.Stat("heart", 80 / 160.0, TextColor.color(0xB43A32)),
                new DexLayout.Stat("sword", 9 / 15.0, TextColor.color(0x7C6A5A)),
                new DexLayout.Stat("speed", 0.27 / 0.42, TextColor.color(0x3C82BE)),
                new DexLayout.Stat("size", 2.4 / 3.35, TextColor.color(0x8A6A3A))),
                "Tranquilo", "Colores: 4/5")), slots));

        slots = new LinkedHashMap<>();
        slots.put("49", "dinoearth:dinopedia");
        screens.put("ficha", screen(DexLayout.card(gui, new DexLayout.CardView("raptor", "verde", "Garrita",
                "Velociraptor", false, "Hembra", "Cría (45%)", "Selva", false, 0.7, 0.25, TextColor.color(0xC87A28), List.of(
                new DexLayout.Line("de Angeloww_", DexLayout.INK), new DexLayout.Line("Quieto", DexLayout.INK_SOFT),
                new DexLayout.Line("Defensivo", DexLayout.INK_SOFT)))), slots));

        slots = new LinkedHashMap<>();
        slots.put("49", "dinoearth:dinopedia");
        screens.put("ficha_salvaje", screen(DexLayout.card(gui, new DexLayout.CardView("pteranodon", "albino",
                "Pteranodon", "Ficha", true, "Macho", "Adulto", "Albino", true, 1.0, null, null, List.of(
                new DexLayout.Line("Salvaje", DexLayout.INK_RED), new DexLayout.Line("Neutral", DexLayout.INK_SOFT)))),
                slots));

        slots = new LinkedHashMap<>();
        slots.put("0", "dinoearth:icon/triceratops/?");
        slots.put("8", "dinoearth:gui/reroll");
        slots.put("18", "dinoearth:gui/stamp/jinete");
        slots.put("26", "dinoearth:gui/reroll");
        slots.put("36", "dinoearth:icon/raptor/rojizo");
        slots.put("49", "minecraft:barrier");
        screens.put("misiones", screen(DexLayout.missions(gui, new DexLayout.MissionsView("Misiones", "Nuevas en 5 h 20 min",
                List.of(new DexLayout.MissionCard("Avistá 3 Triceratops", 2, 3, false, "Completada"),
                        new DexLayout.MissionCard("Recorré 500 bloques montado", 500, 500, true, "Completada"),
                        new DexLayout.MissionCard("Avistá un Velociraptor Rojizo", 0, 1, false, "Completada")))), slots));

        // menus con el formato del server (leidos de su yml, como el plugin)
        for (String name : List.of("silbato", "llamar")) {
            var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.File(new java.io.File(args[0]).getParentFile(), "menus/" + name + ".yml"));
            slots = new LinkedHashMap<>();
            var items = y.getConfigurationSection("items");
            for (String id : items.getKeys(false)) {
                if (name.equals("silbato") && id.equals("grupo")) continue;
                if (name.equals("llamar") && id.equals("cerrar")) continue;
                String mat = items.getString(id + ".material", "paper");
                String model = items.getString(id + ".model", "minecraft:" + mat);
                if (id.equals("dino") && name.equals("llamar")) model = "dinoearth:icon/raptor/?";
                List<Integer> list = new ArrayList<>();
                for (String r : items.getStringList(id + ".slots")) {
                    String[] ab = r.split("-");
                    int a0 = Integer.parseInt(ab[0].trim()), b0 = ab.length > 1 ? Integer.parseInt(ab[1].trim()) : a0;
                    for (int i = a0; i <= b0; i++) list.add(i);
                }
                if (items.contains(id + ".slot")) list.add(items.getInt(id + ".slot"));
                if (id.equals("dino") && name.equals("llamar")) list = list.subList(0, 3);
                for (int slot : list) slots.put(String.valueOf(slot), model);
            }
            screens.put(name, screen(com.dinoearth.text.Lang.parse(y.getString("menu_title")), slots));
        }

        try (var out = new FileWriter(args[1], StandardCharsets.UTF_8)) {
            out.write(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(screens));
        }
        System.out.println("ok: " + screens.keySet());
    }

    private static String firstVariant(String id) {
        return switch (id) {
            case "raptor" -> "arena";
            case "triceratops" -> "pardo";
            default -> "?";
        };
    }

    private static Map<String, Object> screen(Component title, Map<String, String> slots) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", com.google.gson.JsonParser.parseString(GsonComponentSerializer.gson().serialize(title)));
        m.put("slots", slots);
        return m;
    }
}

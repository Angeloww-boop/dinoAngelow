package com.dinoearth.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee todos los YML de textos con el YamlConfiguration de Bukkit (igual que el plugin) y pasa cada texto por
 * Lang.parse: avisa si quedo algun codigo de color sin entender (&amp; o &amp;#) o si una clave se leyo mal.
 * Uso: java -cp ... com.dinoearth.text.TextCheck plugin/src/main/resources
 */
public final class TextCheck {

    public static void main(String[] args) throws Exception {
        String base = args[0];
        String[] files = {"messages.yml", "textos.yml", "items.yml", "menus/silbato.yml", "menus/llamar.yml",
                "menus/dinopedia.yml"};
        int checked = 0;
        List<String> bad = new ArrayList<>();
        for (String f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(base, f));
            for (String key : y.getKeys(true)) {
                List<String> values = new ArrayList<>();
                if (y.isString(key)) values.add(y.getString(key));
                else if (y.isList(key)) values.addAll(y.getStringList(key));
                for (String v : values) {
                    Component c = Lang.parse(v, "player", "Angeloww_", "dino", Component.text("Garrita"));
                    String plain = PlainTextComponentSerializer.plainText().serialize(c);
                    checked++;
                    if (plain.contains("&") || plain.contains("§")) bad.add(f + " " + key + " -> " + plain);
                    if (plain.contains("!") || plain.contains("¡")) bad.add(f + " " + key + " tiene ¡ o !");
                }
            }
        }
        // un ejemplo de cada tipo, para ver como queda armado
        YamlConfiguration m = YamlConfiguration.loadConfiguration(new File(base, "messages.yml"));
        Component kill = Lang.parse(m.getString("chat.dar-ofrecido"), "player", "Olav");
        System.out.println("chat:      " + PlainTextComponentSerializer.plainText().serialize(kill));
        System.out.println("json:      " + GsonComponentSerializer.gson().serialize(kill));
        boolean pink = hasColor(kill, TextColor.color(0xff00d9));
        System.out.println("tiene &#ff00d9: " + pink);
        if (!pink) bad.add("el hex &#ff00d9 no se entendio");
        Component bar = Lang.parse(m.getString("actionbar.hambre"), "porcentaje", 70);
        System.out.println("actionbar: " + PlainTextComponentSerializer.plainText().serialize(bar));
        Component boss = Lang.parse(m.getString("bossbar.mision"), "mision", "Avistá 3 Triceratops", "progreso", 2, "total", 3);
        System.out.println("bossbar:   " + PlainTextComponentSerializer.plainText().serialize(boss));
        System.out.println("textos revisados: " + checked);
        bad.forEach(b -> System.out.println("MAL: " + b));
        if (!bad.isEmpty()) System.exit(1);
    }

    private static boolean hasColor(Component c, TextColor color) {
        if (color.equals(c.color())) return true;
        for (Component child : c.children()) {
            if (hasColor(child, color)) return true;
        }
        return false;
    }
}

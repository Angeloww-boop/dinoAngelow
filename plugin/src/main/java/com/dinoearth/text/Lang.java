package com.dinoearth.text;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Todos los textos que ve el jugador salen de archivos YML (nada escrito en el codigo):
 * <ul>
 *   <li>messages.yml: mensajes de chat, actionbar y bossbar.</li>
 *   <li>textos.yml: palabras sueltas que se meten en los mensajes (especies, colores, ordenes, tiempos...).</li>
 *   <li>items.yml: nombre y descripcion de los items propios.</li>
 *   <li>menus/*.yml: cada menu (titulo, tamaño, items, slots, lore).</li>
 * </ul>
 * Colores con &amp; y hex &amp;#rrggbb. Placeholders %clave%: los valores pueden ser texto o componentes (nombres de
 * dinos hechos con etiqueta, botones clickeables) o una lista de lineas (se expande en el lore).
 * Al actualizar el plugin se agregan solas las claves nuevas sin pisar lo que el dueño cambio.
 */
public final class Lang {

    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().build();
    private static final String[] FILES = {"messages.yml", "textos.yml", "items.yml",
            "menus/silbato.yml", "menus/llamar.yml", "menus/dinopedia.yml", "menus/regiones.yml"};

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> files = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> barTasks = new HashMap<>();

    private static Lang instance;

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        reload();
    }

    /** El cargado al activar el plugin (para no pasarlo por todos los constructores). */
    public static Lang get() {
        return instance;
    }

    public void reload() {
        for (String name : FILES) files.put(name, load(name));
    }

    /** Copia el archivo si no existe y le agrega las claves nuevas del jar (lo del dueño no se toca). */
    private YamlConfiguration load(String name) {
        File file = new File(plugin.getDataFolder(), name);
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) defaults.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("No se pudo leer " + name + " del jar: " + e.getMessage());
        }
        if (!file.exists()) {
            plugin.saveResource(name, false);
            return defaults;
        }
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        boolean added = false;
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !current.contains(key)) {
                current.set(key, defaults.get(key));
                added = true;
            }
        }
        if (added) {
            try {
                current.save(file);
                plugin.getLogger().info(name + ": se agregaron textos nuevos de esta version.");
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo guardar " + name + ": " + e.getMessage());
            }
        }
        return current;
    }

    public YamlConfiguration file(String name) {
        return files.get(name);
    }

    public Menu menu(String name) {
        return new Menu(this, files.get("menus/" + name + ".yml"));
    }

    // ------------------------------------------------------------ mensajes
    public Component chatMsg(String key, Object... kv) {
        return parse(raw("messages.yml", "chat." + key), kv);
    }

    public void chat(CommandSender to, String key, Object... kv) {
        to.sendMessage(chatMsg(key, kv));
    }

    /** Lista de lineas de chat (ayudas, /dino stats). */
    public void chatLines(CommandSender to, String key, Object... kv) {
        for (String line : files.get("messages.yml").getStringList("chat." + key)) to.sendMessage(parse(line, kv));
    }

    public Component actionMsg(String key, Object... kv) {
        return parse(raw("messages.yml", "actionbar." + key), kv);
    }

    public void action(Player p, String key, Object... kv) {
        p.sendActionBar(actionMsg(key, kv));
    }

    /** Bossbar que se ve unos segundos y se va (si llega otra, reemplaza a la anterior). */
    /** Texto de la seccion bossbar (para barras que maneja otra clase, como la del Mapa del Paleontologo). */
    public Component bossbarMsg(String key, Object... kv) {
        return parse(raw("messages.yml", "bossbar." + key), kv);
    }

    public void bossbar(Player p, String key, float progress, int ticks, Object... kv) {
        Component text = parse(raw("messages.yml", "bossbar." + key), kv);
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(text, progress, BossBar.Color.PINK, BossBar.Overlay.NOTCHED_10);
            bars.put(p.getUniqueId(), bar);
        } else {
            bar.name(text).progress(Math.max(0, Math.min(1, progress)));
        }
        p.showBossBar(bar);
        Integer old = barTasks.remove(p.getUniqueId());
        if (old != null) Bukkit.getScheduler().cancelTask(old);
        BossBar shown = bar;
        barTasks.put(p.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            p.hideBossBar(shown);
            barTasks.remove(p.getUniqueId());
        }, ticks).getTaskId());
    }

    // ------------------------------------------------------------ textos sueltos (textos.yml, items.yml)
    /** Texto de textos.yml con placeholders, tal cual (puede tener colores). */
    public String text(String path, Object... kv) {
        return apply(raw("textos.yml", path), kv);
    }

    /** Texto sin colores (para escribir sobre las paginas dibujadas o para meter en otros mensajes). */
    public String plain(String path, Object... kv) {
        return strip(text(path, kv));
    }

    public boolean has(String file, String path) {
        return files.get(file).contains(path);
    }

    public List<String> list(String file, String path) {
        return files.get(file).getStringList(path);
    }

    public ConfigurationSection section(String file, String path) {
        return files.get(file).getConfigurationSection(path);
    }

    public String raw(String file, String path) {
        String s = files.get(file).getString(path);
        if (s == null) {
            plugin.getLogger().warning("Falta el texto " + path + " en " + file);
            return path;
        }
        return s;
    }

    // ------------------------------------------------------------ items (items.yml)
    /** Nombre y lore de un item propio, con placeholders. */
    public void itemTexts(org.bukkit.inventory.meta.ItemMeta meta, String id, Object... kv) {
        YamlConfiguration y = files.get("items.yml");
        meta.itemName(parse(y.getString(id + ".display_name", id), kv));
        meta.lore(lore(y.getStringList(id + ".lore"), kv));
    }

    /** Nombre de un item propio sin colores (para meterlo adentro de un mensaje). */
    public String itemName(String id) {
        return strip(files.get("items.yml").getString(id + ".display_name", id));
    }

    // ------------------------------------------------------------ armado
    /** Texto con colores y placeholders a componente (sin cursiva, para que sirva en items). */
    public static Component parse(String raw, Object... kv) {
        Map<String, Component> comps = new HashMap<>();
        String s = raw;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = "%" + kv[i] + "%";
            Object v = kv[i + 1];
            if (v instanceof Component c) {
                comps.put(key, c);
            } else {
                s = s.replace(key, v == null ? "" : String.valueOf(v));
            }
        }
        Component out = LEGACY.deserialize(s);
        for (Map.Entry<String, Component> e : comps.entrySet()) {
            Component value = e.getValue();
            out = out.replaceText(TextReplacementConfig.builder().matchLiteral(e.getKey()).replacement(value).build());
        }
        return out.decoration(TextDecoration.ITALIC, false);
    }

    /** Lore: cada linea con placeholders; una linea que es solo un placeholder de lista se expande. */
    public static List<Component> lore(List<String> lines, Object... kv) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            List<?> expanded = null;
            for (int i = 0; i + 1 < kv.length; i += 2) {
                if (kv[i + 1] instanceof List<?> l && t.equals("%" + kv[i] + "%")) expanded = l;
            }
            if (expanded != null) {
                for (Object o : expanded) {
                    out.add(o instanceof Component c ? c.decoration(TextDecoration.ITALIC, false) : parse(String.valueOf(o), kv));
                }
            } else {
                out.add(parse(line, kv));
            }
        }
        return out;
    }

    public static String apply(String raw, Object... kv) {
        String s = raw;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            String value = v instanceof Component c ? PlainTextComponentSerializer.plainText().serialize(c) : String.valueOf(v);
            s = s.replace("%" + kv[i] + "%", value);
        }
        return s;
    }

    public static String strip(String legacy) {
        return PlainTextComponentSerializer.plainText().serialize(LEGACY.deserialize(legacy));
    }

    public void shutdown() {
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue());
        }
        bars.clear();
    }
}

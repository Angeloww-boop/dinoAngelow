package com.dinoearth;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pone al dia un config.yml de una version anterior: Bukkit nunca pisa un config que ya existe,
 * asi que al actualizar el plugin faltarian las secciones nuevas (resource-pack, especies, hambre...).
 * Se parte del config nuevo (con sus comentarios) y se le pasan los valores que el dueño ya tenia.
 * El viejo queda guardado como config-anterior.yml.
 */
final class ConfigUpdater {

    /** Claves sueltas de la version 0.2, que eran del raptor. */
    private static final Map<String, String> LEGACY = Map.of(
            "tame-chance", "species.raptor.tame-chance",
            "ride.walk-speed", "species.raptor.ride.walk-speed",
            "ride.sprint-speed", "species.raptor.ride.sprint-speed",
            "ride.jump", "species.raptor.ride.jump");

    private ConfigUpdater() {
    }

    static void update(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveDefaultConfig();
            return;
        }
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return;
            YamlConfiguration fresh = new YamlConfiguration();
            fresh.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);

            List<String> added = new ArrayList<>();
            for (String key : fresh.getKeys(true)) {
                if (!fresh.isConfigurationSection(key) && !current.contains(key)) added.add(key);
            }
            boolean legacy = LEGACY.keySet().stream().anyMatch(current::contains);
            if (added.isEmpty() && !legacy) return;

            // los valores del dueño pisan los del config nuevo
            for (String key : current.getKeys(true)) {
                if (!current.isConfigurationSection(key) && fresh.contains(key)) fresh.set(key, current.get(key));
            }
            for (Map.Entry<String, String> e : LEGACY.entrySet()) {
                if (current.contains(e.getKey())) fresh.set(e.getValue(), current.get(e.getKey()));
            }

            Files.copy(file.toPath(), new File(plugin.getDataFolder(), "config-anterior.yml").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            fresh.save(file);
            plugin.getLogger().info("config.yml actualizado a la versión " + plugin.getPluginMeta().getVersion()
                    + " (" + added.size() + " opciones nuevas; tus valores se mantienen, el anterior quedó en "
                    + "config-anterior.yml).");
            if (added.contains("resource-pack.url")) {
                plugin.getLogger().warning("Completá resource-pack.url en config.yml con el link del pack "
                        + "y usá /dino pack reload.");
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("No se pudo actualizar config.yml: " + e.getMessage());
        }
    }
}

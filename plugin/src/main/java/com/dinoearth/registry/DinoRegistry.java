package com.dinoearth.registry;

import com.dinoearth.dino.Dino;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Wolf;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lista de todos los dinos domesticados con su ultima posicion conocida, guardada en dinos.yml.
 * Sirve para /dino mis y para el silbato de regreso sin tener que cargar chunks: la posicion se
 * actualiza mientras el dino esta cargado y justo cuando se descarga su chunk.
 * Tambien guarda los avisos (muertes) para los dueños que no estaban conectados.
 */
public final class DinoRegistry {

    public record Entry(UUID id, UUID owner, String species, String variant, boolean male, String name,
                        UUID world, double x, double y, double z, String order, long seen, float score) {
        public int chunkX() {
            return (int) Math.floor(x) >> 4;
        }

        public int chunkZ() {
            return (int) Math.floor(z) >> 4;
        }
    }

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, List<String>> notices = new LinkedHashMap<>();
    private boolean dirty;

    public DinoRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dinos.yml");
        load();
    }

    // ------------------------------------------------------------ dinos
    /** Anota (o actualiza) un dino domesticado que esta cargado. */
    public void update(Dino d) {
        Wolf w = d.wolf();
        UUID owner = w.getOwnerUniqueId();
        if (!w.isTamed() || owner == null) {
            if (entries.remove(w.getUniqueId()) != null) dirty = true;
            return;
        }
        Location at = w.getLocation();
        String name = d.name() != null ? PlainTextComponentSerializer.plainText().serialize(d.name()) : null;
        entries.put(w.getUniqueId(), new Entry(w.getUniqueId(), owner, d.species().id, d.variant(), d.isMale(), name,
                at.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), d.order().name(), System.currentTimeMillis(),
                d.genes() != null ? d.genes().score() : 1f));
        dirty = true;
    }

    /** Solo la posicion (cuando el dino ya no esta en la lista de activos). */
    public void move(UUID id, Location at) {
        Entry e = entries.get(id);
        if (e == null) return;
        entries.put(id, new Entry(e.id(), e.owner(), e.species(), e.variant(), e.male(), e.name(), at.getWorld().getUID(),
                at.getX(), at.getY(), at.getZ(), e.order(), System.currentTimeMillis(), e.score()));
        dirty = true;
    }

    public void remove(UUID id) {
        if (entries.remove(id) != null) dirty = true;
    }

    public Entry get(UUID id) {
        return entries.get(id);
    }

    public java.util.Collection<Entry> all() {
        return java.util.Collections.unmodifiableCollection(entries.values());
    }

    public List<Entry> ownedBy(UUID owner) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.owner().equals(owner)) out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------ avisos para dueños desconectados
    public void addNotice(UUID owner, String text) {
        List<String> list = notices.computeIfAbsent(owner, k -> new ArrayList<>());
        list.add(text);
        while (list.size() > 10) list.remove(0);
        dirty = true;
    }

    public List<String> takeNotices(UUID owner) {
        List<String> list = notices.remove(owner);
        if (list != null) dirty = true;
        return list != null ? list : List.of();
    }

    // ------------------------------------------------------------ archivo
    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection dinos = yml.getConfigurationSection("dinos");
        if (dinos != null) {
            for (String key : dinos.getKeys(false)) {
                ConfigurationSection s = dinos.getConfigurationSection(key);
                try {
                    UUID id = UUID.fromString(key);
                    entries.put(id, new Entry(id, UUID.fromString(s.getString("owner")), s.getString("species"),
                            s.getString("variant"), s.getBoolean("male"), s.getString("name"),
                            UUID.fromString(s.getString("world")), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                            s.getString("order", "FOLLOW"), s.getLong("seen"), (float) s.getDouble("genes", 1)));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("dinos.yml: entrada " + key + " invalida, se ignora.");
                }
            }
        }
        ConfigurationSection pending = yml.getConfigurationSection("notices");
        if (pending != null) {
            for (String key : pending.getKeys(false)) {
                try {
                    notices.put(UUID.fromString(key), new ArrayList<>(pending.getStringList(key)));
                } catch (IllegalArgumentException ignored) {
                    // uuid roto: se pierde ese aviso
                }
            }
        }
    }

    /** Guarda si hubo cambios (se llama cada 30 segundos y al apagar). */
    public void save() {
        if (!dirty) return;
        YamlConfiguration yml = new YamlConfiguration();
        for (Entry e : entries.values()) {
            String k = "dinos." + e.id();
            yml.set(k + ".owner", e.owner().toString());
            yml.set(k + ".species", e.species());
            yml.set(k + ".variant", e.variant());
            yml.set(k + ".male", e.male());
            if (e.name() != null) yml.set(k + ".name", e.name());
            yml.set(k + ".world", e.world().toString());
            yml.set(k + ".x", Math.round(e.x() * 100) / 100.0);
            yml.set(k + ".y", Math.round(e.y() * 100) / 100.0);
            yml.set(k + ".z", Math.round(e.z() * 100) / 100.0);
            yml.set(k + ".order", e.order());
            yml.set(k + ".seen", e.seen());
            yml.set(k + ".genes", Math.round(e.score() * 100) / 100.0);
        }
        for (Map.Entry<UUID, List<String>> n : notices.entrySet()) yml.set("notices." + n.getKey(), n.getValue());
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar dinos.yml: " + ex.getMessage());
        }
    }
}

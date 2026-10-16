package com.dinoearth.stats;

import com.dinoearth.registry.DinoRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Numeros de cada jugador para los tops (/dino top y PlaceholderAPI), guardados en jugadores.yml asi se pueden
 * comparar aunque no esten conectados. Lo de la Dinopedia (sellos, especies) se copia aca cada vez que cambia;
 * los dinos que tiene y su mejor genetica salen del registro de domesticados.
 */
public final class PlayerStats implements Listener {

    /** Categorias de los tops, en el orden en que se listan. */
    public static final List<String> CATEGORIES = List.of("dinos", "sellos", "especies", "crias", "domesticados",
            "nidos", "cazados", "misiones", "genes");
    private static final long CACHE_MS = 10_000;

    public record Row(UUID id, String name, double value) {
    }

    private static PlayerStats instance;

    private final Plugin plugin;
    private final File file;
    private final DinoRegistry registry;
    private final Map<UUID, Map<String, Double>> data = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<String, List<Row>> cache = new HashMap<>();
    private final Map<String, Long> cacheAt = new HashMap<>();
    private Consumer<Player> refresher = p -> { };
    private boolean dirty;

    public PlayerStats(Plugin plugin, DinoRegistry registry) {
        instance = this;
        this.plugin = plugin;
        this.registry = registry;
        this.file = new File(plugin.getDataFolder(), "jugadores.yml");
        load();
    }

    public static PlayerStats get() {
        return instance;
    }

    /** Lo que hay que recalcular de un jugador al entrar (sellos y especies de la Dinopedia). */
    public void setRefresher(Consumer<Player> refresher) {
        this.refresher = refresher;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        names.put(p.getUniqueId(), p.getName());
        dirty = true;
        refresher.accept(p);
    }

    // ------------------------------------------------------------ valores
    public void add(Player p, String stat, double amount) {
        Map<String, Double> m = of(p);
        m.merge(stat, amount, Double::sum);
        dirty = true;
    }

    public void set(Player p, String stat, double value) {
        Map<String, Double> m = of(p);
        Double old = m.put(stat, value);
        if (old == null || old != value) dirty = true;
    }

    private Map<String, Double> of(Player p) {
        names.put(p.getUniqueId(), p.getName());
        return data.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
    }

    public double value(UUID id, String stat) {
        switch (stat) {
            case "dinos" -> {
                return registry.ownedBy(id).size();
            }
            case "genes" -> {
                return registry.ownedBy(id).stream().mapToDouble(DinoRegistry.Entry::score).max().orElse(0) * 100;
            }
            default -> {
                Map<String, Double> m = data.get(id);
                return m != null ? m.getOrDefault(stat, 0.0) : 0;
            }
        }
    }

    public String name(UUID id) {
        String n = names.get(id);
        if (n == null) n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString().substring(0, 8);
    }

    // ------------------------------------------------------------ tops
    /** Los mejores de una categoria (sin los que tienen 0). Se recalcula cada 10 segundos como mucho. */
    public List<Row> top(String stat) {
        long now = System.currentTimeMillis();
        List<Row> rows = cache.get(stat);
        if (rows != null && now - cacheAt.getOrDefault(stat, 0L) < CACHE_MS) return rows;
        Map<UUID, Boolean> everyone = new LinkedHashMap<>();
        for (UUID id : data.keySet()) everyone.put(id, true);
        for (DinoRegistry.Entry e : registry.all()) everyone.put(e.owner(), true);
        List<Row> out = new ArrayList<>();
        for (UUID id : everyone.keySet()) {
            double v = value(id, stat);
            if (v > 0) out.add(new Row(id, name(id), v));
        }
        out.sort((a, b) -> Double.compare(b.value(), a.value()));
        cache.put(stat, out);
        cacheAt.put(stat, now);
        return out;
    }

    /** Puesto del jugador en una categoria (1 = primero), 0 si no esta. */
    public int position(UUID id, String stat) {
        List<Row> rows = top(stat);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(id)) return i + 1;
        }
        return 0;
    }

    // ------------------------------------------------------------ archivo
    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yml.getConfigurationSection("jugadores");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection s = players.getConfigurationSection(key);
                if (s == null) continue;
                String n = s.getString("nombre");
                if (n != null) names.put(id, n);
                Map<String, Double> m = new HashMap<>();
                for (String stat : s.getKeys(false)) {
                    if (!stat.equals("nombre")) m.put(stat, s.getDouble(stat));
                }
                data.put(id, m);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("jugadores.yml: " + key + " no es un uuid, se ignora.");
            }
        }
    }

    public void save() {
        if (!dirty) return;
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Double>> e : data.entrySet()) {
            String k = "jugadores." + e.getKey();
            String n = names.get(e.getKey());
            if (n != null) yml.set(k + ".nombre", n);
            for (Map.Entry<String, Double> v : e.getValue().entrySet()) {
                double d = v.getValue();
                yml.set(k + "." + v.getKey(), d == Math.rint(d) ? (Object) (long) d : (Object) d);
            }
        }
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar jugadores.yml: " + ex.getMessage());
        }
    }
}

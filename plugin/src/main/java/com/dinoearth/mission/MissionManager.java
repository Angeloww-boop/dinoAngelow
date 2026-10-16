package com.dinoearth.mission;

import com.dinoearth.Settings;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.model.DinoRig;
import com.dinoearth.registry.DinoRegistry;
import com.dinoearth.species.Species;
import com.dinoearth.text.Lang;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Misiones diarias de la Dinopedia: 3 por jugador, distintas para cada uno, que cambian todos los dias a la hora del
 * config. Solo piden especies que hay de verdad en el server (vistas por alguien en los ultimos dias o cargadas ahora),
 * asi nadie recibe una mision imposible. El progreso se suma en eventos que ya existen (ver, domesticar, criar,
 * montar, derrotar, cargar comederos): no hay tareas pesadas de fondo. Se guarda en el jugador (PDC).
 */
public final class MissionManager implements Listener {

    public static final int PER_DAY = 3;

    public enum Type { AVISTAR, COLOR, DOMESTICAR, CRIAR, MONTAR, CAZAR, COMEDERO }

    public static final class Mission {
        public final Type type;
        public final String species;
        public final String variant;
        public final int target;
        public int progress;
        public boolean done;
        double ridden;               // bloques montados con decimales (no se guarda: se parte de progress)

        Mission(Type type, String species, String variant, int target) {
            this.type = type;
            this.species = species;
            this.variant = variant;
            this.target = target;
        }

        String encode() {
            return type + "," + (species != null ? species : "-") + "," + (variant != null ? variant : "-") + ","
                    + target + "," + progress + "," + (done ? 1 : 0);
        }

        static Mission decode(String s) {
            try {
                String[] p = s.split(",");
                Mission m = new Mission(Type.valueOf(p[0]), "-".equals(p[1]) ? null : p[1], "-".equals(p[2]) ? null : p[2],
                        Integer.parseInt(p[3]));
                m.progress = Integer.parseInt(p[4]);
                m.done = "1".equals(p[5]);
                m.ridden = m.progress;
                return m;
            } catch (RuntimeException e) {
                return null;
            }
        }

        /** Texto de la mision (textos.yml: misiones.<TIPO>). */
        public String text() {
            Species s = species != null ? Species.byId(species) : null;
            return Lang.get().plain("misiones." + type.name(), "cantidad", target, "especie", s != null ? s.name : "",
                    "color", s != null && variant != null ? s.colorName(variant) : "");
        }

        public Species speciesObj() {
            return species != null ? Species.byId(species) : null;
        }
    }

    public static final class State {
        String day;
        int rerolls;
        final Mission[] list = new Mission[PER_DAY];
        final Set<UUID> sighted = new HashSet<>();     // dinos distintos vistos hoy (para AVISTAR)
        boolean fresh;

        public Mission get(int i) {
            return list[i];
        }

        public int rerolls() {
            return rerolls;
        }
    }

    private static MissionManager instance;

    private final JavaPlugin plugin;
    private final DinoManager dinos;
    private final DinoRegistry registry;
    private final NamespacedKey key;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<String, Long> recent = new HashMap<>();   // especie -> ultima vez que alguien la vio
    private final File dataFile;
    private boolean recentDirty;

    private final ZoneId zone;
    private final int resetHour;
    private final int rerollsPerDay;
    private final int xpEach;
    private final int xpAll;
    private final List<String> commandsEach;
    private final List<String> commandsAll;
    private final long recentMs;
    private final double moneyEach, moneyAll;

    public MissionManager(JavaPlugin plugin, DinoManager dinos, DinoRegistry registry, Settings settings) {
        instance = this;
        this.plugin = plugin;
        this.dinos = dinos;
        this.registry = registry;
        this.key = new NamespacedKey(plugin, "missions");
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("missions");
        ZoneId z;
        try {
            z = ZoneId.of(c != null ? c.getString("timezone", "America/Argentina/Buenos_Aires") : "America/Argentina/Buenos_Aires");
        } catch (RuntimeException e) {
            z = ZoneId.systemDefault();
        }
        zone = z;
        resetHour = c != null ? Math.max(0, Math.min(23, c.getInt("reset-hour", 0))) : 0;
        rerollsPerDay = c != null ? Math.max(0, c.getInt("rerolls-per-day", 1)) : 1;
        xpEach = c != null ? Math.max(0, c.getInt("reward-xp", 100)) : 100;
        xpAll = c != null ? Math.max(0, c.getInt("all-done-xp", 200)) : 200;
        commandsEach = c != null ? c.getStringList("reward-commands") : List.of();
        moneyEach = c != null ? Math.max(0, c.getDouble("reward-money", 0)) : 0;
        moneyAll = c != null ? Math.max(0, c.getDouble("all-done-money", 0)) : 0;
        commandsAll = c != null ? c.getStringList("all-done-commands") : List.of();
        recentMs = (long) (c != null ? Math.max(1, c.getDouble("recent-days", 3)) : 3) * 86_400_000L;

        dataFile = new File(plugin.getDataFolder(), "misiones.yml");
        if (dataFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
            ConfigurationSection r = y.getConfigurationSection("especies-vistas");
            if (r != null) for (String k : r.getKeys(false)) recent.put(k, r.getLong(k));
        }
        // cambio de dia para los que estan conectados, y guardar las especies vistas
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                State st = state(p);
                if (st.fresh) announce(p, st);
            }
            save();
        }, 1200L, 1200L);
    }

    public static MissionManager get() {
        return instance;
    }

    public int xpEach() {
        return xpEach;
    }

    // ------------------------------------------------------------ dia y estado
    private String cachedDay;
    private long dayCheckedAt;

    /** Dia de las misiones (se recalcula cada 10 s: se pregunta en cada avistamiento). */
    private String today() {
        long now = System.currentTimeMillis();
        if (cachedDay == null || now - dayCheckedAt > 10_000) {
            cachedDay = ZonedDateTime.now(zone).minusHours(resetHour).toLocalDate().toString();
            dayCheckedAt = now;
        }
        return cachedDay;
    }

    /** Milisegundos hasta que cambian las misiones. */
    public long msUntilReset() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.toLocalDate().atStartOfDay(zone).plusHours(resetHour);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next).toMillis();
    }

    public State state(Player p) {
        String day = today();
        State st = states.get(p.getUniqueId());
        if (st == null) {
            st = load(p);
            states.put(p.getUniqueId(), st);
        }
        if (!day.equals(st.day)) {
            st.day = day;
            st.rerolls = 0;
            st.sighted.clear();
            for (int i = 0; i < PER_DAY; i++) st.list[i] = null;
            List<Mission> made = generate(p, List.of());
            for (int i = 0; i < PER_DAY && i < made.size(); i++) st.list[i] = made.get(i);
            st.fresh = true;
            store(p, st);
        }
        return st;
    }

    private State load(Player p) {
        State st = new State();
        String raw = p.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) return st;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 3) return st;
        st.day = parts[0];
        try {
            st.rerolls = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {
            st.rerolls = 0;
        }
        String[] ms = parts[2].split(";", -1);
        for (int i = 0; i < PER_DAY && i < ms.length; i++) st.list[i] = ms[i].isEmpty() ? null : Mission.decode(ms[i]);
        return st;
    }

    private void store(Player p, State st) {
        StringBuilder sb = new StringBuilder(st.day).append('|').append(st.rerolls).append('|');
        for (int i = 0; i < PER_DAY; i++) {
            if (i > 0) sb.append(';');
            if (st.list[i] != null) sb.append(st.list[i].encode());
        }
        p.getPersistentDataContainer().set(key, PersistentDataType.STRING, sb.toString());
    }

    private void announce(Player p, State st) {
        st.fresh = false;
        boolean any = false;
        for (Mission m : st.list) any |= m != null;
        if (any) Lang.get().chat(p, "misiones-nuevas");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            State st = state(p);
            if (st.fresh) announce(p, st);
        }, 80L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        State st = states.remove(event.getPlayer().getUniqueId());
        if (st != null && st.day != null) store(event.getPlayer(), st);
    }

    public void save() {
        if (!recentDirty) return;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Long> e : recent.entrySet()) y.set("especies-vistas." + e.getKey(), e.getValue());
        try {
            y.save(dataFile);
            recentDirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar misiones.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ armar misiones
    /** Especies que hay en el server: vistas por alguien hace poco o cargadas ahora. */
    private List<Species> present() {
        long now = System.currentTimeMillis();
        Set<Species> out = new HashSet<>();
        for (Map.Entry<String, Long> e : recent.entrySet()) {
            Species s = Species.byId(e.getKey());
            if (s != null && now - e.getValue() < recentMs) out.add(s);
        }
        for (Dino d : dinos.all()) out.add(d.species());
        List<Species> list = new ArrayList<>(out);
        list.sort((a, b) -> a.id.compareTo(b.id));
        return list;
    }

    private record Option(Mission mission, int weight) {
    }

    private List<Mission> generate(Player p, List<Mission> avoid) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Species> present = present();
        List<DinoRegistry.Entry> mine = registry.ownedBy(p.getUniqueId());
        List<Option> options = new ArrayList<>();
        for (Species s : present) {
            options.add(new Option(new Mission(Type.AVISTAR, s.id, null, s.group > 1 ? r.nextInt(3, 6) : r.nextInt(2, 4)), 3));
            List<String> unseen = new ArrayList<>();
            var seen = dinos.dex().seen(p, s);
            for (DinoRig.Variant v : s.rig.variants().values()) {
                if (!seen.contains(v.id()) && v.weight() > 5) unseen.add(v.id());
            }
            if (!unseen.isEmpty()) {
                options.add(new Option(new Mission(Type.COLOR, s.id, unseen.get(r.nextInt(unseen.size())), 1), 2));
            }
            if (s.tameable() && !s.eggTameOnly) options.add(new Option(new Mission(Type.DOMESTICAR, s.id, null, 1), 2));
            if (s.hostile()) options.add(new Option(new Mission(Type.CAZAR, s.id, null, r.nextInt(2, 5)), 2));
        }
        boolean breeds = false, rides = false, eats = false;
        for (DinoRegistry.Entry e : mine) {
            Species s = Species.byId(e.species());
            if (s == null) continue;
            breeds |= s.rig.hasEgg();
            rides |= s.rideable;
            eats |= s.getsHungry();
        }
        if (breeds) options.add(new Option(new Mission(Type.CRIAR, null, null, 1), 1));
        if (rides) options.add(new Option(new Mission(Type.MONTAR, null, null, 100 * r.nextInt(3, 9)), 2));
        if (eats) options.add(new Option(new Mission(Type.COMEDERO, null, null, 20 * r.nextInt(3, 8)), 1));

        List<Mission> out = new ArrayList<>();
        Set<Type> used = new HashSet<>();
        for (Mission a : avoid) if (a != null) used.add(a.type);
        while (out.size() < PER_DAY && !options.isEmpty()) {
            // primero tipos distintos; si no alcanza, se repite tipo con otra especie
            List<Option> pool = options.stream().filter(o -> !used.contains(o.mission().type)).toList();
            if (pool.isEmpty()) pool = options;
            int total = pool.stream().mapToInt(Option::weight).sum();
            int pick = r.nextInt(total);
            Option chosen = pool.get(pool.size() - 1);
            for (Option o : pool) {
                pick -= o.weight();
                if (pick < 0) {
                    chosen = o;
                    break;
                }
            }
            Option c = chosen;
            out.add(c.mission());
            used.add(c.mission().type);
            options.removeIf(o -> o == c || o.mission().type == c.mission().type
                    && java.util.Objects.equals(o.mission().species, c.mission().species));
        }
        return out;
    }

    /** Cambia una mision sin terminar por otra (rerolls-per-day por dia). */
    public void reroll(Player p, int i) {
        State st = state(p);
        Mission m = st.list[i];
        if (m == null || m.done) return;
        if (st.rerolls >= rerollsPerDay) {
            Lang.get().chat(p, "mision-sin-cambios");
            return;
        }
        List<Mission> avoid = new ArrayList<>();
        for (Mission x : st.list) avoid.add(x);
        List<Mission> made = generate(p, avoid).stream()
                .filter(n -> avoid.stream().noneMatch(a -> a != null && a.type == n.type
                        && java.util.Objects.equals(a.species, n.species))).toList();
        if (made.isEmpty()) {
            Lang.get().chat(p, "mision-sin-otra");
            return;
        }
        st.list[i] = made.get(0);
        st.rerolls++;
        store(p, st);
        Lang.get().chat(p, "mision-cambiada", "mision", st.list[i].text());
    }

    public boolean canReroll(Player p) {
        return state(p).rerolls < rerollsPerDay;
    }

    // ------------------------------------------------------------ progreso
    public void onSight(Player p, Dino d) {
        recent.put(d.species().id, System.currentTimeMillis());
        recentDirty = true;
        State st = state(p);
        for (Mission m : st.list) {
            if (m == null || m.done || !d.species().id.equals(m.species)) continue;
            if (m.type == Type.AVISTAR && st.sighted.add(d.wolf().getUniqueId())) advance(p, st, m, m.progress + 1);
            if (m.type == Type.COLOR && d.variant().equals(m.variant)) advance(p, st, m, 1);
        }
    }

    public void onTame(Player p, Species s) {
        bump(p, Type.DOMESTICAR, s, 1);
    }

    public void onHatch(Player p, Species s) {
        bump(p, Type.CRIAR, null, 1);
    }

    public void onKill(Player p, Species s) {
        bump(p, Type.CAZAR, s, 1);
    }

    public void onRide(Player p, double blocks) {
        State st = state(p);
        for (Mission m : st.list) {
            if (m == null || m.done || m.type != Type.MONTAR) continue;
            m.ridden += blocks;
            int whole = (int) m.ridden;
            if (whole > m.progress) advance(p, st, m, whole);
        }
    }

    public void onFeed(Player p, int points) {
        bump(p, Type.COMEDERO, null, points);
    }

    private void bump(Player p, Type type, Species s, int amount) {
        State st = state(p);
        for (Mission m : st.list) {
            if (m == null || m.done || m.type != type) continue;
            if (s != null && m.species != null && !m.species.equals(s.id)) continue;
            advance(p, st, m, m.progress + amount);
        }
    }

    private void advance(Player p, State st, Mission m, int value) {
        m.progress = Math.min(m.target, value);
        Lang lang = Lang.get();
        lang.bossbar(p, "mision", (float) m.progress / m.target, 60, "mision", m.text(), "progreso", m.progress,
                "total", m.target);
        if (m.progress >= m.target) {
            m.done = true;
            lang.chat(p, "mision-completa", "mision", m.text(), "xp", xpEach);
            p.playSound(p, "minecraft:entity.player.levelup", SoundCategory.PLAYERS, 0.6f, 1.2f);
            if (xpEach > 0) p.giveExp(xpEach);
            money(p, moneyEach);
            var stats = com.dinoearth.stats.PlayerStats.get();
            if (stats != null) stats.add(p, "misiones", 1);
            run(commandsEach, p, m);
            boolean all = true;
            for (Mission x : st.list) all &= x == null || x.done;
            if (all) {
                lang.chat(p, "misiones-todas", "xp", xpAll);
                p.playSound(p, "minecraft:ui.toast.challenge_complete", SoundCategory.PLAYERS, 0.6f, 1.1f);
                if (xpAll > 0) p.giveExp(xpAll);
                money(p, moneyAll);
                run(commandsAll, p, m);
            }
        }
        store(p, st);
    }

    private static void money(Player p, double amount) {
        String given = com.dinoearth.integration.Money.give(p, amount);
        if (given != null) Lang.get().chat(p, "dinero", "dinero", given);
    }

    /** Misiones de hoy completadas (para PlaceholderAPI). */
    public int doneToday(Player p) {
        int n = 0;
        for (Mission m : state(p).list) if (m != null && m.done) n++;
        return n;
    }

    private static void run(List<String> commands, Player p, Mission m) {
        for (String c : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", p.getName())
                    .replace("%mision%", m.type.name().toLowerCase()));
        }
    }
}

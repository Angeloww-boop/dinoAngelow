package com.dinoearth.race;

import com.dinoearth.Keys;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.dino.Genes;
import com.dinoearth.integration.Money;
import com.dinoearth.mission.MissionManager;
import com.dinoearth.species.Species;
import com.dinoearth.stats.PlayerStats;
import com.dinoearth.text.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carreras parejas: la pista presta a todos el mismo dino (misma especie, genetica neutra), asi gana el que maneja
 * mejor. El dueño arma la pista donde quiera y la marca con comandos: puestos de largada y puertas en orden (la
 * ultima puerta es la meta). Los jugadores se anotan con un NPC (FancyNpcs: comando "dino carrera unirse <pista>")
 * o con el comando; tambien hay carreras programadas con aviso en el chat.
 *
 * <p>Carrera: cuenta regresiva con el dino quieto, despues hay que pasar todas las puertas en orden (no se puede
 * cortar camino) las vueltas que tenga la pista. Correr gasta estamina. Si te bajas del dino volves a la ultima
 * puerta. Al terminar: podio, premios (config races.rewards), records por pista (de siempre y de la semana),
 * mision y sello de la Dinopedia. Los dinos prestados no se guardan con el mundo.
 */
public final class RaceManager implements Listener {

    // ------------------------------------------------------------ pistas
    /** Puerta de control: una caja; se cuenta si el dino la cruza entre un tick y el siguiente. */
    public record Gate(double x1, double y1, double z1, double x2, double y2, double z2, float yaw) {
        double cx() {
            return (x1 + x2) / 2;
        }

        double cz() {
            return (z1 + z2) / 2;
        }

        /** El segmento a-b toca la caja (agrandada un poco)? Metodo de las "losas". */
        boolean crossed(Vector a, Vector b) {
            double[] lo = {x1 - 0.3, y1, z1 - 0.3}, hi = {x2 + 0.3, y2, z2 + 0.3};
            double[] p = {a.getX(), a.getY(), a.getZ()}, d = {b.getX() - a.getX(), b.getY() - a.getY(), b.getZ() - a.getZ()};
            double t0 = 0, t1 = 1;
            for (int i = 0; i < 3; i++) {
                if (Math.abs(d[i]) < 1e-9) {
                    if (p[i] < lo[i] || p[i] > hi[i]) return false;
                } else {
                    double ta = (lo[i] - p[i]) / d[i], tb = (hi[i] - p[i]) / d[i];
                    if (ta > tb) {
                        double t = ta;
                        ta = tb;
                        tb = t;
                    }
                    t0 = Math.max(t0, ta);
                    t1 = Math.min(t1, tb);
                    if (t0 > t1) return false;
                }
            }
            return true;
        }
    }

    public record Spot(double x, double y, double z, float yaw) {
    }

    public static final class Track {
        final String id;
        UUID world;
        final List<Spot> grid = new ArrayList<>();
        final List<Gate> gates = new ArrayList<>();
        int laps;
        String species;
        double best;
        String bestName;
        double weekBest;
        String weekName;
        String week;

        Track(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public double best() {
            return best;
        }

        public String bestName() {
            return bestName;
        }

        boolean ready() {
            return world != null && !grid.isEmpty() && !gates.isEmpty() && Species.byId(species) != null;
        }
    }

    // ------------------------------------------------------------ carreras en curso
    private enum Phase { LOBBY, COUNTDOWN, RUNNING }

    private final class Racer {
        final UUID id;
        final String name;
        Dino dino;
        Location back;
        int lap, gate, place, respawn = -1, remount = -1;
        boolean finished, out;
        long finishTick;
        Vector last;

        Racer(Player p) {
            id = p.getUniqueId();
            name = p.getName();
        }

        Player player() {
            return Bukkit.getPlayer(id);
        }
    }

    private final class Race {
        final Track track;
        final boolean scheduled;
        final List<Racer> racers = new ArrayList<>();
        Phase phase = Phase.LOBBY;
        long startAt;          // ms: cuando larga (0 = todavia no hay minimo)
        final long deadline;   // ms: si no se junta el minimo, se cancela
        int countdown;
        long startTick;
        int grace = -1;
        int finishedCount;

        Race(Track track, boolean scheduled, long startAt, long deadline) {
            this.track = track;
            this.scheduled = scheduled;
            this.startAt = startAt;
            this.deadline = deadline;
        }
    }

    private static RaceManager instance;

    private final JavaPlugin plugin;
    private final DinoManager dinos;
    private final Keys keys;
    private final File file;
    private final Map<String, Track> tracks = new LinkedHashMap<>();
    private final Map<String, Race> races = new HashMap<>();
    private final Map<UUID, Race> playing = new HashMap<>();
    private boolean teleporting;
    private long tick;

    // config
    private int minPlayers, countdownSeconds, finishWaitSeconds, defaultLaps, announceMinutes, everyMinutes;
    private long lobbyMs, lobbyTimeoutMs;
    private double sprintSeconds, regenSeconds;
    private String defaultSpecies, scheduleTrack;
    private List<LocalTime> times = List.of();
    private ZoneId zone;
    private long nextScheduled = -1;
    private int rotation;

    public RaceManager(JavaPlugin plugin, DinoManager dinos, Keys keys) {
        instance = this;
        this.plugin = plugin;
        this.dinos = dinos;
        this.keys = keys;
        this.file = new File(plugin.getDataFolder(), "carreras.yml");
        loadConfig();
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public static RaceManager get() {
        return instance;
    }

    public void loadConfig() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("races");
        if (c == null) c = new YamlConfiguration();
        minPlayers = Math.max(1, c.getInt("min-players", 2));
        lobbyMs = (long) (Math.max(5, c.getDouble("lobby-seconds", 30)) * 1000);
        lobbyTimeoutMs = (long) (Math.max(1, c.getDouble("lobby-timeout-minutes", 3)) * 60_000);
        countdownSeconds = Math.max(3, c.getInt("countdown-seconds", 10));
        finishWaitSeconds = Math.max(5, c.getInt("finish-wait-seconds", 30));
        defaultLaps = Math.max(1, c.getInt("default-laps", 3));
        defaultSpecies = c.getString("default-species", "gallimimus");
        sprintSeconds = Math.max(1, c.getDouble("stamina.sprint-seconds", 6));
        regenSeconds = Math.max(1, c.getDouble("stamina.regen-seconds", 10));
        scheduleTrack = c.getString("schedule.track", "");
        announceMinutes = Math.max(1, c.getInt("schedule.announce-minutes", 5));
        everyMinutes = Math.max(0, c.getInt("schedule.every-minutes", 0));
        List<LocalTime> t = new ArrayList<>();
        for (String s : c.getStringList("schedule.times")) {
            try {
                t.add(LocalTime.parse(s.trim().length() == 4 ? "0" + s.trim() : s.trim()));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("races.schedule.times: hora invalida '" + s + "' (usá HH:mm).");
            }
        }
        times = t;
        try {
            zone = ZoneId.of(plugin.getConfig().getString("missions.timezone", "America/Argentina/Buenos_Aires"));
        } catch (RuntimeException e) {
            zone = ZoneId.systemDefault();
        }
        nextScheduled = -1;
    }

    // ------------------------------------------------------------ guardar
    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection all = y.getConfigurationSection("pistas");
        if (all == null) return;
        for (String id : all.getKeys(false)) {
            ConfigurationSection s = all.getConfigurationSection(id);
            Track t = new Track(id);
            try {
                String w = s.getString("mundo");
                t.world = w != null ? UUID.fromString(w) : null;
            } catch (IllegalArgumentException e) {
                t.world = null;
            }
            t.laps = s.getInt("vueltas", defaultLaps);
            t.species = s.getString("especie", defaultSpecies);
            for (String g : s.getStringList("largada")) {
                double[] v = nums(g);
                if (v.length == 4) t.grid.add(new Spot(v[0], v[1], v[2], (float) v[3]));
            }
            for (String g : s.getStringList("puertas")) {
                double[] v = nums(g);
                if (v.length == 7) t.gates.add(new Gate(v[0], v[1], v[2], v[3], v[4], v[5], (float) v[6]));
            }
            t.best = s.getDouble("record.tiempo", 0);
            t.bestName = s.getString("record.jugador");
            t.weekBest = s.getDouble("semana.tiempo", 0);
            t.weekName = s.getString("semana.jugador");
            t.week = s.getString("semana.id");
            tracks.put(id, t);
        }
    }

    private static double[] nums(String s) {
        String[] p = s.split(",");
        double[] out = new double[p.length];
        try {
            for (int i = 0; i < p.length; i++) out[i] = Double.parseDouble(p[i].trim());
        } catch (NumberFormatException e) {
            return new double[0];
        }
        return out;
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Track t : tracks.values()) {
            String k = "pistas." + t.id + ".";
            if (t.world != null) y.set(k + "mundo", t.world.toString());
            y.set(k + "vueltas", t.laps);
            y.set(k + "especie", t.species);
            y.set(k + "largada", t.grid.stream().map(s -> r(s.x()) + "," + r(s.y()) + "," + r(s.z()) + "," + r(s.yaw())).toList());
            y.set(k + "puertas", t.gates.stream().map(g -> r(g.x1()) + "," + r(g.y1()) + "," + r(g.z1()) + "," + r(g.x2()) + ","
                    + r(g.y2()) + "," + r(g.z2()) + "," + r(g.yaw())).toList());
            if (t.best > 0) {
                y.set(k + "record.tiempo", t.best);
                y.set(k + "record.jugador", t.bestName);
            }
            if (t.weekBest > 0) {
                y.set(k + "semana.tiempo", t.weekBest);
                y.set(k + "semana.jugador", t.weekName);
                y.set(k + "semana.id", t.week);
            }
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar carreras.yml: " + e.getMessage());
        }
    }

    private static String r(double v) {
        return String.valueOf(Math.round(v * 100) / 100.0);
    }

    // ------------------------------------------------------------ consultas
    public List<String> ids() {
        return List.copyOf(tracks.keySet());
    }

    public Track track(String id) {
        return id == null ? null : tracks.get(id.toLowerCase());
    }

    public boolean hasTracks() {
        return tracks.values().stream().anyMatch(Track::ready);
    }

    /** Especies que presta alguna pista (para el sello "Corredor" de la Dinopedia). */
    public boolean usesSpecies(Species s) {
        for (Track t : tracks.values()) {
            if (t.ready() && s.id.equals(t.species)) return true;
        }
        return false;
    }

    public boolean isRacing(Player p) {
        return playing.containsKey(p.getUniqueId());
    }

    // ------------------------------------------------------------ armar pistas (admin)
    public void create(Player p, String id) {
        Lang lang = Lang.get();
        id = id.toLowerCase();
        if (!id.matches("[a-z0-9_-]{1,24}")) {
            lang.chat(p, "carrera-nombre-invalido");
            return;
        }
        if (tracks.containsKey(id)) {
            lang.chat(p, "carrera-ya-existe", "pista", id);
            return;
        }
        Track t = new Track(id);
        t.world = p.getWorld().getUID();
        t.laps = defaultLaps;
        t.species = defaultSpecies;
        tracks.put(id, t);
        save();
        lang.chatLines(p, "carrera-creada", "pista", id);
    }

    public void addGrid(Player p, Track t) {
        if (!sameWorld(p, t)) return;
        Location l = p.getLocation();
        t.grid.add(new Spot(l.getBlockX() + 0.5, l.getY(), l.getBlockZ() + 0.5, l.getYaw()));
        save();
        Lang.get().chat(p, "carrera-largada-agregada", "pista", t.id, "numero", t.grid.size());
    }

    /** Puerta centrada donde esta parado el admin: angosta en la direccion en que mira, ancha de costado. */
    public void addGate(Player p, Track t, double width, double height) {
        if (!sameWorld(p, t)) return;
        Location l = p.getLocation();
        double yaw = Math.toRadians(l.getYaw());
        boolean alongX = Math.abs(Math.sin(yaw)) > Math.abs(Math.cos(yaw));   // mira hacia +-X
        double half = width / 2, depth = 1.5;
        double hx = alongX ? depth : half, hz = alongX ? half : depth;
        double y0 = Math.floor(l.getY()) - 1;
        t.gates.add(new Gate(l.getX() - hx, y0, l.getZ() - hz, l.getX() + hx, y0 + height + 1, l.getZ() + hz, l.getYaw()));
        save();
        Lang.get().chat(p, "carrera-puerta-agregada", "pista", t.id, "numero", t.gates.size());
        show(p, t, 100);
    }

    private boolean sameWorld(Player p, Track t) {
        if (t.world == null) t.world = p.getWorld().getUID();
        if (!p.getWorld().getUID().equals(t.world)) {
            Lang.get().chat(p, "carrera-otro-mundo", "pista", t.id);
            return false;
        }
        return true;
    }

    public void undoGate(Player p, Track t) {
        if (!t.gates.isEmpty()) t.gates.remove(t.gates.size() - 1);
        save();
        Lang.get().chat(p, "carrera-puerta-borrada", "pista", t.id, "cantidad", t.gates.size());
    }

    public void clear(Player p, Track t) {
        t.grid.clear();
        t.gates.clear();
        save();
        Lang.get().chat(p, "carrera-limpia", "pista", t.id);
    }

    public void setLaps(Player p, Track t, int laps) {
        t.laps = Math.max(1, Math.min(20, laps));
        save();
        Lang.get().chat(p, "carrera-vueltas", "pista", t.id, "vueltas", t.laps);
    }

    public void setSpecies(Player p, Track t, Species s) {
        if (!s.rideable || s.flies() || s.aquatic()) {
            Lang.get().chat(p, "carrera-especie-invalida", "especie", s.name);
            return;
        }
        t.species = s.id;
        save();
        Lang.get().chat(p, "carrera-especie", "pista", t.id, "especie", s.name);
    }

    public void delete(Player p, Track t) {
        Race race = races.get(t.id);
        if (race != null) cancel(race, "carrera-cancelada");
        tracks.remove(t.id);
        save();
        Lang.get().chat(p, "carrera-borrada", "pista", t.id);
    }

    public void info(Player p, Track t) {
        Species s = Species.byId(t.species);
        Lang.get().chatLines(p, "carrera-info", "pista", t.id, "largadas", t.grid.size(), "puertas", t.gates.size(),
                "vueltas", t.laps, "especie", s != null ? s.name : t.species,
                "estado", Lang.get().plain(t.ready() ? "carrera.lista" : "carrera.incompleta"),
                "record", t.best > 0 ? time(t.best) + " (" + t.bestName + ")" : Lang.get().plain("carrera.sin-record"));
    }

    public void list(Player p) {
        if (tracks.isEmpty()) {
            Lang.get().chat(p, "carrera-sin-pistas");
            return;
        }
        Lang.get().chat(p, "carrera-lista-titulo");
        for (Track t : tracks.values()) {
            Lang.get().chat(p, "carrera-lista-linea", "pista", t.id, "vueltas", t.laps,
                    "record", t.best > 0 ? time(t.best) + " (" + t.bestName + ")" : Lang.get().plain("carrera.sin-record"),
                    "semana", t.weekBest > 0 && week().equals(t.week) ? time(t.weekBest) + " (" + t.weekName + ")"
                            : Lang.get().plain("carrera.sin-record"));
        }
    }

    /** Muestra la pista con particulas unos segundos (largada en verde, puertas en rosa, la meta en dorado). */
    public void show(Player p, Track t, int ticks) {
        World w = Bukkit.getWorld(t.world);
        if (w == null || w != p.getWorld()) return;
        new org.bukkit.scheduler.BukkitRunnable() {
            int left = ticks;

            @Override
            public void run() {
                if (!p.isOnline() || (left -= 10) < 0) {
                    cancel();
                    return;
                }
                for (Spot s : t.grid) {
                    p.spawnParticle(Particle.HAPPY_VILLAGER, s.x(), s.y() + 0.3, s.z(), 6, 0.3, 0.2, 0.3, 0);
                }
                for (int i = 0; i < t.gates.size(); i++) {
                    Gate g = t.gates.get(i);
                    boolean finish = i == t.gates.size() - 1;
                    Particle.DustOptions dust = new Particle.DustOptions(finish ? org.bukkit.Color.fromRGB(240, 190, 40)
                            : org.bukkit.Color.fromRGB(255, 0, 217), 1.4f);
                    for (double x = g.x1(); x <= g.x2(); x += 1) {
                        for (double z = g.z1(); z <= g.z2(); z += 1) {
                            p.spawnParticle(Particle.DUST, x, g.y2(), z, 1, 0, 0, 0, 0, dust);
                            p.spawnParticle(Particle.DUST, x, g.y1() + 1, z, 1, 0, 0, 0, 0, dust);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // ------------------------------------------------------------ anotarse
    public void join(Player p, Track t) {
        Lang lang = Lang.get();
        if (t == null || !t.ready()) {
            lang.chat(p, "carrera-no-lista");
            return;
        }
        if (playing.containsKey(p.getUniqueId())) {
            lang.chat(p, "carrera-ya-anotado");
            return;
        }
        if (p.getVehicle() != null) {
            lang.chat(p, "carrera-bajate");
            return;
        }
        Race race = races.get(t.id);
        if (race != null && race.phase != Phase.LOBBY) {
            lang.chat(p, "carrera-en-curso", "pista", t.id);
            return;
        }
        long now = System.currentTimeMillis();
        if (race == null) {
            race = new Race(t, false, 0, now + lobbyTimeoutMs);
            races.put(t.id, race);
            broadcastOpen(race, p);
        }
        if (race.racers.size() >= t.grid.size()) {
            lang.chat(p, "carrera-llena", "pista", t.id);
            return;
        }
        race.racers.add(new Racer(p));
        playing.put(p.getUniqueId(), race);
        for (Racer r : race.racers) {
            Player o = r.player();
            if (o != null) lang.chat(o, "carrera-anotado", "player", p.getName(), "pista", t.id,
                    "cantidad", race.racers.size(), "max", t.grid.size());
        }
        if (!race.scheduled) {
            if (race.racers.size() >= t.grid.size()) race.startAt = now;
            else if (race.racers.size() >= minPlayers && race.startAt == 0) {
                race.startAt = now + lobbyMs;
                tellRace(race, "carrera-larga-en", "segundos", lobbyMs / 1000);
            }
        }
    }

    public void leave(Player p) {
        Race race = playing.get(p.getUniqueId());
        if (race == null) {
            Lang.get().chat(p, "carrera-no-anotado");
            return;
        }
        Racer r = racer(race, p.getUniqueId());
        if (race.phase == Phase.LOBBY) {
            race.racers.remove(r);
            playing.remove(p.getUniqueId());
            Lang.get().chat(p, "carrera-salio");
            if (race.racers.size() < minPlayers && !race.scheduled) race.startAt = 0;
            if (race.racers.isEmpty() && !race.scheduled) races.remove(race.track.id);
        } else {
            out(race, r, true);
            Lang.get().chat(p, "carrera-abandono");
        }
    }

    /** Admin: larga ya (aunque haya menos que el minimo, sirve para probar la pista solo). */
    public void forceStart(Player admin, Track t) {
        Race race = races.get(t.id);
        if (race == null || race.phase != Phase.LOBBY || race.racers.isEmpty()) {
            Lang.get().chat(admin, "carrera-nadie-anotado", "pista", t.id);
            return;
        }
        begin(race);
    }

    public void forceCancel(Player admin, Track t) {
        Race race = races.get(t.id);
        if (race == null) {
            Lang.get().chat(admin, "carrera-nadie-anotado", "pista", t.id);
            return;
        }
        cancel(race, "carrera-cancelada");
    }

    private void broadcastOpen(Race race, Player opener) {
        Component button = joinButton(race.track);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(opener)) continue;
            Lang.get().chat(o, "carrera-abierta", "boton", button, "player", opener.getName(), "pista", race.track.id);
        }
    }

    private Component joinButton(Track t) {
        Lang lang = Lang.get();
        return lang.chatMsg("carrera-boton").clickEvent(ClickEvent.runCommand("/dino carrera unirse " + t.id))
                .hoverEvent(HoverEvent.showText(lang.chatMsg("carrera-boton-hover")));
    }

    // ------------------------------------------------------------ tick
    private void tick() {
        tick++;
        long now = System.currentTimeMillis();
        if (tick % 20 == 0) schedule(now);
        for (Race race : new ArrayList<>(races.values())) {
            switch (race.phase) {
                case LOBBY -> tickLobby(race, now);
                case COUNTDOWN -> tickCountdown(race);
                case RUNNING -> tickRunning(race);
            }
        }
    }

    private void tickLobby(Race race, long now) {
        race.racers.removeIf(r -> {
            if (r.player() != null) return false;
            playing.remove(r.id);
            return true;
        });
        if (race.scheduled) {
            if (now >= race.startAt) {
                if (race.racers.size() >= minPlayers) begin(race);
                else cancel(race, "carrera-sin-corredores");
            }
        } else {
            if (race.racers.isEmpty()) {
                races.remove(race.track.id);
                return;
            }
            if (race.startAt > 0 && now >= race.startAt) {
                if (race.racers.size() >= minPlayers) begin(race);
                else race.startAt = 0;
            } else if (race.startAt == 0 && now > race.deadline) {
                cancel(race, "carrera-sin-corredores");
                return;
            }
        }
        if (tick % 20 == 0) {
            for (Racer r : race.racers) {
                Player p = r.player();
                if (p == null) continue;
                if (race.startAt > 0) {
                    Lang.get().action(p, "carrera-esperando-largada", "pista", race.track.id,
                            "segundos", Math.max(0, (race.startAt - now + 999) / 1000), "cantidad", race.racers.size(),
                            "max", race.track.grid.size());
                } else {
                    Lang.get().action(p, "carrera-esperando", "pista", race.track.id, "cantidad", race.racers.size(),
                            "min", minPlayers, "max", race.track.grid.size());
                }
            }
        }
    }

    /** Pasa a la grilla: cada uno a su puesto, montado en su dino prestado y quieto hasta la largada. */
    private void begin(Race race) {
        Track t = race.track;
        World w = Bukkit.getWorld(t.world);
        Species s = Species.byId(t.species);
        if (w == null || s == null) {
            cancel(race, "carrera-cancelada");
            return;
        }
        race.phase = Phase.COUNTDOWN;
        race.countdown = countdownSeconds * 20;
        int i = 0;
        for (Racer r : new ArrayList<>(race.racers)) {
            Player p = r.player();
            if (p == null) {
                race.racers.remove(r);
                playing.remove(r.id);
                continue;
            }
            Spot spot = t.grid.get(i++ % t.grid.size());
            Location at = new Location(w, spot.x(), spot.y(), spot.z(), spot.yaw(), 0);
            r.back = p.getLocation();
            p.getPersistentDataContainer().set(keys.raceReturn, PersistentDataType.STRING, encode(r.back));
            if (p.getVehicle() != null) p.leaveVehicle();
            teleport(p, at);
            r.dino = spawnRental(s, at, p);
            r.last = at.toVector();
        }
        if (race.racers.isEmpty()) cancel(race, "carrera-sin-corredores");
    }

    private Dino spawnRental(Species s, Location at, Player p) {
        Dino d = dinos.spawn(s, at, null, null, 1f, p, Genes.neutral());
        if (d == null) return null;
        var w = d.wolf();
        w.getPersistentDataContainer().set(keys.rental, PersistentDataType.BYTE, (byte) 1);
        // al aparecer se anoto como domesticado del jugador: es prestado, no cuenta (ni en /dino mis ni en el limite)
        if (dinos.registry() != null) dinos.registry().remove(w.getUniqueId());
        w.setPersistent(false);
        w.setInvulnerable(true);
        d.setSaddled(true);
        d.setHunger(1f);
        d.startRacing(sprintSeconds, regenSeconds);
        d.setFrozen(true);
        d.mount(p);
        return d;
    }

    private void tickCountdown(Race race) {
        race.countdown--;
        keepMounted(race);
        if (race.countdown % 20 == 0) {
            int sec = race.countdown / 20;
            for (Racer r : race.racers) {
                Player p = r.player();
                if (p == null) continue;
                if (sec > 0) {
                    Lang.get().bossbar(p, "carrera-cuenta", (float) sec / countdownSeconds, 25, "pista", race.track.id,
                            "segundos", sec);
                    if (sec <= 3) p.playSound(p, "minecraft:block.note_block.pling", SoundCategory.PLAYERS, 1f, 0.8f);
                } else {
                    Lang.get().bossbar(p, "carrera-largaron", 1f, 40, "pista", race.track.id);
                    p.playSound(p, "minecraft:block.note_block.pling", SoundCategory.PLAYERS, 1f, 1.6f);
                }
            }
        }
        if (race.countdown <= 0) {
            race.phase = Phase.RUNNING;
            race.startTick = tick;
            for (Racer r : race.racers) {
                if (r.dino != null) r.dino.setFrozen(false);
            }
        }
    }

    /** En la grilla: si alguien se bajo, de nuevo arriba. */
    private void keepMounted(Race race) {
        for (Racer r : race.racers) {
            Player p = r.player();
            if (p == null || r.dino == null || !r.dino.wolf().isValid()) continue;
            if (r.dino.rider() == null && p.getVehicle() == null && race.countdown % 10 == 0) r.dino.mount(p);
        }
    }

    private void tickRunning(Race race) {
        Track t = race.track;
        for (Racer r : new ArrayList<>(race.racers)) {
            if (r.finished || r.out) continue;
            Player p = r.player();
            if (p == null || r.dino == null || !r.dino.wolf().isValid()) {
                out(race, r, false);
                continue;
            }
            // se bajo: vuelve a la ultima puerta
            if (r.remount >= 0) {
                if (--r.remount < 0) {
                    r.dino.mount(p);
                    r.dino.setFrozen(false);
                }
                continue;
            }
            if (r.dino.rider() == null) {
                if (r.respawn < 0) {
                    r.respawn = 30;
                    r.dino.setFrozen(true);
                    Lang.get().action(p, "carrera-volves");
                } else if (--r.respawn < 0) {
                    Location at = lastCheckpoint(race, r);
                    r.dino.prepareTeleport();
                    r.dino.wolf().teleport(at);
                    teleport(p, at);
                    r.last = at.toVector();
                    r.remount = 2;
                }
                continue;
            }
            Vector now = r.dino.wolf().getLocation().toVector();
            Gate next = t.gates.get(r.gate);
            if (next.crossed(r.last, now)) {
                r.gate++;
                if (r.gate >= t.gates.size()) {
                    r.gate = 0;
                    r.lap++;
                    if (r.lap >= t.laps) {
                        finish(race, r);
                        r.last = now;
                        continue;
                    }
                    Lang.get().chat(p, "carrera-vuelta", "vuelta", r.lap + 1, "vueltas", t.laps);
                    p.playSound(p, "minecraft:entity.experience_orb.pickup", SoundCategory.PLAYERS, 0.8f, 1.4f);
                } else {
                    p.playSound(p, "minecraft:entity.experience_orb.pickup", SoundCategory.PLAYERS, 0.5f, 1.8f);
                }
            }
            r.last = now;
        }
        if (tick % 4 == 0) hud(race);
        if (race.grace >= 0 && --race.grace < 0) end(race);
        if (race.racers.stream().allMatch(r -> r.finished || r.out)) end(race);
    }

    private Location lastCheckpoint(Race race, Racer r) {
        Track t = race.track;
        World w = Bukkit.getWorld(t.world);
        int prev = r.gate - 1;
        if (prev < 0 && r.lap > 0) prev = t.gates.size() - 1;
        if (prev < 0) {
            Spot s = t.grid.get(Math.max(0, race.racers.indexOf(r)) % t.grid.size());
            return new Location(w, s.x(), s.y(), s.z(), s.yaw(), 0);
        }
        Gate g = t.gates.get(prev);
        return new Location(w, g.cx(), g.y1() + 1, g.cz(), g.yaw(), 0);
    }

    private void hud(Race race) {
        List<Racer> order = standings(race);
        long elapsed = tick - race.startTick;
        for (Racer r : race.racers) {
            Player p = r.player();
            if (p == null || r.out || r.finished || r.dino == null) continue;
            Lang.get().action(p, "carrera-estado", "puesto", order.indexOf(r) + 1, "total", race.racers.size(),
                    "vuelta", Math.min(r.lap + 1, race.track.laps), "vueltas", race.track.laps,
                    "tiempo", time(elapsed / 20.0), "estamina", staminaBar(r.dino));
        }
    }

    private Component staminaBar(Dino d) {
        Lang lang = Lang.get();
        int n = Math.round(d.stamina() * 10);
        String on = lang.text(d.isExhausted() ? "carrera.estamina-cansado" : "carrera.estamina-lleno");
        String off = lang.text("carrera.estamina-vacio");
        return Lang.parse(on.repeat(n) + off.repeat(10 - n));
    }

    /** Orden actual: los que llegaron por puesto, despues por puertas pasadas y cercania a la proxima. */
    private List<Racer> standings(Race race) {
        Track t = race.track;
        List<Racer> list = new ArrayList<>(race.racers);
        list.sort(Comparator.<Racer>comparingInt(r -> r.finished ? 0 : r.out ? 2 : 1)
                .thenComparingInt(r -> r.finished ? r.place : 0)
                .thenComparingInt(r -> -(r.lap * t.gates.size() + r.gate))
                .thenComparingDouble(r -> {
                    if (r.dino == null || r.finished || r.out) return 0;
                    Gate g = t.gates.get(r.gate);
                    Location l = r.dino.wolf().getLocation();
                    return Math.hypot(l.getX() - g.cx(), l.getZ() - g.cz());
                }));
        return list;
    }

    private void finish(Race race, Racer r) {
        r.finished = true;
        r.place = ++race.finishedCount;
        r.finishTick = tick - race.startTick;
        if (r.dino != null) r.dino.setFrozen(true);
        double secs = r.finishTick / 20.0;
        for (Racer o : race.racers) {
            Player op = o.player();
            if (op != null) Lang.get().chat(op, "carrera-llego", "player", r.name, "puesto", r.place, "tiempo", time(secs));
        }
        Player p = r.player();
        if (p != null) p.playSound(p, "minecraft:ui.toast.challenge_complete", SoundCategory.PLAYERS, 0.7f, 1.2f);
        if (r.place == 1) race.grace = finishWaitSeconds * 20;
    }

    /** Fin de la carrera: podio, premios, records, mision, sello; los dinos prestados se van y cada uno vuelve. */
    private void end(Race race) {
        if (!races.containsKey(race.track.id)) return;
        races.remove(race.track.id);
        Track t = race.track;
        Lang lang = Lang.get();
        List<Racer> order = standings(race);
        List<String> lines = new ArrayList<>();
        for (Racer r : order) {
            lines.add(r.finished
                    ? Lang.apply(lang.raw("messages.yml", "chat.carrera-podio-linea"), "puesto", r.place, "player", r.name,
                    "tiempo", time(r.finishTick / 20.0))
                    : Lang.apply(lang.raw("messages.yml", "chat.carrera-podio-afuera"), "player", r.name));
        }
        Racer winner = order.stream().filter(r -> r.finished && r.place == 1).findFirst().orElse(null);
        String week = week();
        boolean record = false, weekRecord = false;
        if (winner != null) {
            double secs = winner.finishTick / 20.0;
            if (t.best <= 0 || secs < t.best) {
                t.best = secs;
                t.bestName = winner.name;
                record = true;
            }
            if (!week.equals(t.week) || t.weekBest <= 0 || secs < t.weekBest) {
                t.week = week;
                t.weekBest = secs;
                t.weekName = winner.name;
                weekRecord = true;
            }
            save();
        }
        for (Racer r : race.racers) {
            Player p = r.player();
            if (p != null) {
                lang.chat(p, "carrera-podio-titulo", "pista", t.id);
                for (String l : lines) p.sendMessage(Lang.parse(l));
                if (r.finished) reward(p, r.place, t);
            }
        }
        if (winner != null) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                lang.chat(o, "carrera-ganador", "player", winner.name, "pista", t.id, "tiempo", time(winner.finishTick / 20.0));
                if (record) lang.chat(o, "carrera-record", "player", winner.name, "pista", t.id, "tiempo", time(t.best));
                else if (weekRecord) lang.chat(o, "carrera-record-semana", "player", winner.name, "pista", t.id,
                        "tiempo", time(t.weekBest));
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Racer r : race.racers) cleanup(r);
        }, 60L);
    }

    private void reward(Player p, int place, Track t) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("races.rewards");
        ConfigurationSection sec = c == null ? null : c.isConfigurationSection(String.valueOf(place))
                ? c.getConfigurationSection(String.valueOf(place)) : c.getConfigurationSection("finish");
        if (sec != null) {
            int xp = sec.getInt("xp", 0);
            if (xp > 0) p.giveExp(xp);
            double money = sec.getDouble("money", 0);
            if (money > 0 && Money.enabled()) Money.give(p, money);
            for (String cmd : sec.getStringList("commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName())
                        .replace("%pista%", t.id).replace("%puesto%", String.valueOf(place)));
            }
        }
        PlayerStats stats = PlayerStats.get();
        if (stats != null) {
            stats.add(p, "carreras-corridas", 1);
            if (place == 1) stats.add(p, "carreras", 1);
        }
        Species s = Species.byId(t.species);
        if (s != null) dinos.dex().onRace(p, s);
        MissionManager m = MissionManager.get();
        if (m != null) m.onRace(p, place);
    }

    /** Alguien quedo afuera (se fue, murio, se teletransporto o abandono). */
    private void out(Race race, Racer r, boolean leaving) {
        r.out = true;
        playing.remove(r.id);
        if (race.phase == Phase.LOBBY) {
            race.racers.remove(r);
            return;
        }
        for (Racer o : race.racers) {
            Player op = o.player();
            if (op != null && o != r) Lang.get().chat(op, "carrera-afuera", "player", r.name);
        }
        cleanup(r);
    }

    private void cleanup(Racer r) {
        playing.remove(r.id);
        if (r.dino != null) {
            Player rider = r.dino.rider();
            if (rider != null) r.dino.wolf().removePassenger(rider);
            r.dino.wolf().remove();
            r.dino = null;
        }
        Player p = r.player();
        if (p != null) {
            if (r.back != null) teleport(p, r.back);
            p.getPersistentDataContainer().remove(keys.raceReturn);
        }
    }

    private void cancel(Race race, String messageKey) {
        races.remove(race.track.id);
        for (Racer r : race.racers) {
            Player p = r.player();
            if (p != null) Lang.get().chat(p, messageKey, "pista", race.track.id);
            cleanup(r);
        }
    }

    private void tellRace(Race race, String key, Object... kv) {
        for (Racer r : race.racers) {
            Player p = r.player();
            if (p != null) Lang.get().chat(p, key, kv);
        }
    }

    private static Racer racer(Race race, UUID id) {
        for (Racer r : race.racers) if (r.id.equals(id)) return r;
        return null;
    }

    // ------------------------------------------------------------ programadas
    private void schedule(long now) {
        if (times.isEmpty() && everyMinutes <= 0) return;
        if (!hasTracks()) return;
        if (nextScheduled < 0) nextScheduled = nextTime(now);
        if (nextScheduled < 0) return;
        long announceAt = nextScheduled - announceMinutes * 60_000L;
        if (now < announceAt) return;
        Track t = pickTrack();
        long startAt = nextScheduled;
        nextScheduled = nextTime(startAt + 1000);
        if (t == null || races.containsKey(t.id)) {
            plugin.getLogger().info("Carrera programada salteada: la pista esta ocupada o no hay ninguna lista.");
            return;
        }
        Race race = new Race(t, true, startAt, startAt);
        races.put(t.id, race);
        long minutes = Math.max(1, (startAt - now + 59_999) / 60_000);
        Component button = joinButton(t);
        for (Player o : Bukkit.getOnlinePlayers()) {
            Lang.get().chat(o, "carrera-programada", "boton", button, "pista", t.id, "minutos", minutes);
        }
        // recordatorio un minuto antes
        long reminder = startAt - 60_000 - now;
        if (reminder > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (races.get(t.id) != race || race.phase != Phase.LOBBY) return;
                for (Player o : Bukkit.getOnlinePlayers()) {
                    Lang.get().chat(o, "carrera-programada", "boton", joinButton(t), "pista", t.id, "minutos", 1);
                }
            }, reminder / 50);
        }
    }

    private Track pickTrack() {
        if (scheduleTrack != null && !scheduleTrack.isBlank()) {
            Track t = tracks.get(scheduleTrack.toLowerCase());
            return t != null && t.ready() ? t : null;
        }
        List<Track> ready = tracks.values().stream().filter(Track::ready).toList();
        if (ready.isEmpty()) return null;
        return ready.get(rotation++ % ready.size());
    }

    /** Proxima carrera programada despues de "after" (ms), o -1. */
    private long nextTime(long after) {
        long best = -1;
        ZonedDateTime from = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(after), zone);
        for (LocalTime lt : times) {
            ZonedDateTime cand = from.toLocalDate().atTime(lt).atZone(zone);
            if (!cand.isAfter(from)) cand = cand.plusDays(1);
            long ms = cand.toInstant().toEpochMilli();
            if (best < 0 || ms < best) best = ms;
        }
        if (everyMinutes > 0) {
            long ms = after + everyMinutes * 60_000L;
            if (best < 0 || ms < best) best = ms;
        }
        return best;
    }

    // ------------------------------------------------------------ eventos
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Race race = playing.get(event.getPlayer().getUniqueId());
        if (race == null) return;
        Racer r = racer(race, event.getPlayer().getUniqueId());
        if (r == null) return;
        if (race.phase == Phase.LOBBY) {
            race.racers.remove(r);
            playing.remove(r.id);
        } else {
            // el dino se va; al volver a entrar lo llevamos adonde estaba antes de la carrera
            r.out = true;
            playing.remove(r.id);
            if (r.dino != null) {
                r.dino.wolf().eject();
                r.dino.wolf().remove();
                r.dino = null;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String raw = p.getPersistentDataContainer().get(keys.raceReturn, PersistentDataType.STRING);
        if (raw == null) return;
        p.getPersistentDataContainer().remove(keys.raceReturn);
        Location back = decode(raw);
        if (back != null) Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) teleport(p, back);
        }, 5L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        Race race = playing.get(p.getUniqueId());
        if (race == null || race.phase == Phase.LOBBY) return;
        Racer r = racer(race, p.getUniqueId());
        if (r == null) return;
        r.back = null;                 // reaparece donde le toque, no lo movemos despues
        out(race, r, false);
    }

    /** Teletransportarse en plena carrera (comandos, perlas...) lo deja afuera. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (teleporting) return;
        Player p = event.getPlayer();
        Race race = playing.get(p.getUniqueId());
        if (race == null || race.phase != Phase.RUNNING) return;
        Racer r = racer(race, p.getUniqueId());
        if (r == null || r.out || r.finished) return;
        if (event.getFrom().getWorld() == event.getTo().getWorld() && event.getFrom().distanceSquared(event.getTo()) < 9) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            out(race, r, false);
            Lang.get().chat(p, "carrera-descalificado");
        });
    }

    public void shutdown() {
        for (Race race : new ArrayList<>(races.values())) {
            for (Racer r : race.racers) cleanup(r);
        }
        races.clear();
        playing.clear();
    }

    // ------------------------------------------------------------ utilidades
    private void teleport(Player p, Location at) {
        teleporting = true;
        try {
            p.teleport(at);
        } finally {
            teleporting = false;
        }
    }

    private static String encode(Location l) {
        return l.getWorld().getUID() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    private static Location decode(String s) {
        try {
            String[] p = s.split(";");
            World w = Bukkit.getWorld(UUID.fromString(p[0]));
            if (w == null) return null;
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String week() {
        LocalDate d = LocalDate.now(zone);
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** 83.4 -> "1:23.40". */
    public static String time(double seconds) {
        int m = (int) (seconds / 60);
        double s = seconds - m * 60;
        return m > 0 ? String.format(java.util.Locale.ROOT, "%d:%05.2f", m, s) : String.format(java.util.Locale.ROOT, "%.2f", s);
    }
}

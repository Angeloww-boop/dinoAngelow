package com.dinoearth.bench;

import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.species.Species;
import com.dinoearth.text.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /dino bench: prueba de carga con los jugadores conectados (los bots cuentan como jugadores, y eso importa: las poses
 * solo se mandan a quien ve al dino). Por etapas, por ejemplo 2,5,10 dinos salvajes por jugador: en cada etapa los
 * pone alrededor de cada jugador (de a pocos por tick para no medir el pico del spawn), espera 10 s y despues anota
 * cada 10 s el tick de DinoEarth, el MSPT y TPS del server, los huesos mandados/ahorrados y la red estimada. Entre etapa
 * y etapa borra todo. Al final muestra el promedio de cada etapa y deja todas las muestras en bench/bench-fecha.csv.
 * Los dinos de la prueba no son persistentes (si se corta el server no quedan) y nunca son de nadie.
 */
public final class BenchManager {

    private static final int SAMPLE_TICKS = 200;     // una muestra cada 10 s
    private static final int WARMUP_TICKS = 200;     // 10 s sin medir al empezar cada etapa
    private static final int SPAWN_PER_TICK = 15;    // ~15 dinos (cada uno ~15 entidades) por tick
    private static final double BYTES_PER_BONE = 90; // como /dino stats: ~90 bytes por hueso mandado a un jugador

    private final JavaPlugin plugin;
    private final DinoManager dinos;

    private CommandSender owner;
    private List<Integer> levels;
    private int stage;
    private int stageTicks;
    private int stageLength;
    private double radius;
    private long startedAt;
    private BukkitTask task;
    private PrintWriter csv;
    private File csvFile;
    private final Set<UUID> spawned = new HashSet<>();
    private final ArrayDeque<Location> pending = new ArrayDeque<>();
    private final List<double[]> samples = new ArrayList<>();
    private final List<double[]> results = new ArrayList<>();   // promedio de cada etapa

    public BenchManager(JavaPlugin plugin, DinoManager dinos) {
        this.plugin = plugin;
        this.dinos = dinos;
    }

    public boolean running() {
        return task != null;
    }

    /** levels: dinos por jugador de cada etapa; minutes: lo que se mide cada etapa (sin contar los 10 s del arranque). */
    public void start(CommandSender sender, List<Integer> levels, double minutes, double radius) {
        Lang lang = Lang.get();
        if (running()) {
            lang.chat(sender, "bench-ya");
            return;
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            lang.chat(sender, "bench-sin-jugadores");
            return;
        }
        this.owner = sender;
        this.levels = levels;
        this.radius = radius;
        this.stageLength = Math.max(SAMPLE_TICKS, (int) Math.round(minutes * 60 * 20 / SAMPLE_TICKS) * SAMPLE_TICKS);
        this.stage = 0;
        this.startedAt = System.currentTimeMillis();
        results.clear();
        openCsv();
        beginStage();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.getLogger().info("Prueba de carga: etapas " + levels + " dinos por jugador, " + minutes + " min cada una.");
    }

    public void stop(CommandSender sender) {
        if (!running()) {
            Lang.get().chat(sender, "bench-nada");
            return;
        }
        if (!samples.isEmpty()) results.add(average(samples));
        int removed = clear();
        Lang.get().chat(sender, "bench-parado", "cantidad", removed);
        finish();
    }

    // ------------------------------------------------------------ etapas
    private void beginStage() {
        clear();
        samples.clear();
        stageTicks = 0;
        int perPlayer = levels.get(stage);
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player p : players) {
            for (int i = 0; i < perPlayer; i++) pending.add(p.getLocation());
        }
        say("bench-etapa", "etapa", stage + 1, "etapas", levels.size(), "por_jugador", perPlayer,
                "total", perPlayer * players.size(), "minutos", fmt(stageLength / 1200.0, 1));
    }

    private void tick() {
        if (!pending.isEmpty()) {
            for (int i = 0; i < SPAWN_PER_TICK && !pending.isEmpty(); i++) spawnNear(pending.poll());
            return;
        }
        stageTicks++;
        if (stageTicks > WARMUP_TICKS && (stageTicks - WARMUP_TICKS) % SAMPLE_TICKS == 0) sample();
        if (stageTicks >= WARMUP_TICKS + stageLength) {
            results.add(average(samples));
            stage++;
            if (stage < levels.size()) {
                beginStage();
            } else {
                clear();
                finish();
            }
        }
    }

    private static final List<Species> POOL = Species.ALL.stream()
            .filter(s -> !s.aquatic() && s.rig != null).toList();

    /** Un dino salvaje adulto en un lugar al azar a entre 6 y radius bloques, sobre suelo firme y en un chunk cargado. */
    private void spawnNear(Location center) {
        World w = center.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            double a = r.nextDouble(Math.PI * 2);
            double d = 6 + r.nextDouble() * Math.max(0, radius - 6);
            int x = (int) Math.floor(center.getX() + Math.cos(a) * d);
            int z = (int) Math.floor(center.getZ() + Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block ground = w.getHighestBlockAt(x, z);
            if (ground.isLiquid() || !ground.getType().isSolid()) continue;
            Species s = POOL.get(r.nextInt(POOL.size()));
            Dino dino = dinos.spawn(s, new Location(w, x + 0.5, ground.getY() + 1, z + 0.5), null, null, 1f, null);
            if (dino == null) return;
            dino.wolf().setPersistent(false);
            spawned.add(dino.wolf().getUniqueId());
            return;
        }
    }

    /** Borra los dinos de la prueba que siguen vivos y devuelve cuantos. */
    private int clear() {
        pending.clear();
        int n = 0;
        for (UUID id : spawned) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid()) {
                e.remove();
                n++;
            }
        }
        spawned.clear();
        return n;
    }

    // ------------------------------------------------------------ medicion
    private int alive() {
        int n = 0;
        for (UUID id : spawned) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid()) n++;
        }
        return n;
    }

    /**
     * Columnas: segundos, etapa, por_jugador, jugadores, dinos_prueba, dinos_total, displays, vistos, tick_ms, tick_max_ms,
     * mspt, tps, huesos_s, huesos_x_jugadores_s, ahorrados_s, kb_s_total, kb_s_jugador, entidades, memoria_mb.
     */
    private void sample() {
        List<Dino> all = dinos.all();
        int players = Math.max(1, Bukkit.getOnlinePlayers().size());
        double[] r = dinos.poseRates();
        double kb = r[1] * BYTES_PER_BONE / 1024.0;
        int entities = 0;
        for (World w : Bukkit.getWorlds()) entities += w.getEntities().size();
        Runtime rt = Runtime.getRuntime();
        double[] row = {
                (System.currentTimeMillis() - startedAt) / 1000.0, stage + 1, levels.get(stage), players, alive(),
                all.size(), all.stream().mapToLong(Dino::modelParts).sum(), all.stream().filter(Dino::isSeen).count(),
                dinos.avgTickMs(), dinos.maxTickMs(), Bukkit.getServer().getAverageTickTime(),
                Math.min(20, Bukkit.getServer().getTPS()[0]), r[0], r[1], r[2], kb, kb / players, entities,
                (rt.totalMemory() - rt.freeMemory()) / 1048576.0};
        samples.add(row);
        if (csv != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(fmt(row[i], 3));
            }
            csv.println(sb);
            csv.flush();
        }
        say("bench-muestra", "jugadores", (int) row[3], "dinos", (int) row[5], "tick", fmt(row[8], 2),
                "tick_max", fmt(row[9], 2), "mspt", fmt(row[10], 1), "tps", fmt(row[11], 1), "kb_jugador", fmt(row[16], 1));
    }

    private static double[] average(List<double[]> rows) {
        if (rows.isEmpty()) return null;
        double[] out = new double[rows.get(0).length];
        for (double[] row : rows) {
            for (int i = 0; i < row.length; i++) out[i] += row[i] / rows.size();
        }
        // el peor tick de la etapa es el maximo, no el promedio
        for (double[] row : rows) out[9] = Math.max(out[9], row[9]);
        return out;
    }

    private void finish() {
        if (task != null) task.cancel();
        task = null;
        say("bench-resumen");
        for (double[] row : results) {
            if (row == null) continue;
            say("bench-resumen-etapa", "por_jugador", (int) row[2], "jugadores", (int) Math.round(row[3]),
                    "dinos", (int) Math.round(row[5]), "tick", fmt(row[8], 2), "tick_max", fmt(row[9], 2),
                    "mspt", fmt(row[10], 1), "tps", fmt(row[11], 1), "kb_jugador", fmt(row[16], 1),
                    "kb_total", fmt(row[15], 0));
            plugin.getLogger().info(String.format(Locale.ROOT,
                    "Prueba de carga: %d por jugador, %d jugadores, %d dinos -> DinoEarth %.2f ms (peor %.2f), "
                            + "server %.1f ms, TPS %.1f, red %.1f KB/s por jugador (%.0f KB/s total)",
                    (int) row[2], Math.round(row[3]), Math.round(row[5]), row[8], row[9], row[10], row[11], row[16], row[15]));
        }
        if (csv != null) {
            csv.close();
            csv = null;
            say("bench-archivo", "archivo", plugin.getDataFolder().getName() + "/bench/" + csvFile.getName());
        }
        owner = null;
    }

    // ------------------------------------------------------------ salida
    private void say(String key, Object... kv) {
        Lang lang = Lang.get();
        if (owner instanceof Player p && !p.isOnline()) owner = Bukkit.getConsoleSender();
        if (owner != null) lang.chat(owner, key, kv);
        if (owner != Bukkit.getConsoleSender()) lang.chat(Bukkit.getConsoleSender(), key, kv);
    }

    private void openCsv() {
        File dir = new File(plugin.getDataFolder(), "bench");
        dir.mkdirs();
        csvFile = new File(dir, "bench-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".csv");
        try {
            csv = new PrintWriter(new FileWriter(csvFile));
            csv.println("segundos,etapa,por_jugador,jugadores,dinos_prueba,dinos_total,displays,vistos,tick_ms,tick_max_ms,"
                    + "mspt,tps,huesos_s,huesos_x_jugadores_s,ahorrados_s,kb_s_total,kb_s_jugador,entidades,memoria_mb");
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo crear " + csvFile + ": " + e.getMessage());
            csv = null;
        }
    }

    private static String fmt(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}

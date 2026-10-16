package com.dinoearth.dino;

import com.dinoearth.Keys;
import com.dinoearth.Settings;
import com.dinoearth.dex.DexManager;
import com.dinoearth.feeder.FeederManager;
import com.dinoearth.species.Species;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Lleva la cuenta de los dinosaurios cargados y los anima cada tick. */
public final class DinoManager {

    private final Keys keys;
    private final Settings settings;
    private final Map<UUID, Dino> active = new HashMap<>();
    private final List<Dino> dying = new ArrayList<>();
    private final FeederManager feeders;
    private final DexManager dex;

    public DinoManager(Keys keys, Settings settings, FeederManager feeders, DexManager dex) {
        this.keys = keys;
        this.settings = settings;
        this.feeders = feeders;
        this.dex = dex;
    }

    private com.dinoearth.registry.DinoRegistry registry;

    public void setRegistry(com.dinoearth.registry.DinoRegistry registry) {
        this.registry = registry;
    }

    /** Lista de domesticados con su ultima posicion (puede ser null si todavia no se creo). */
    public com.dinoearth.registry.DinoRegistry registry() {
        return registry;
    }

    public Settings settings() {
        return settings;
    }

    public FeederManager feeders() {
        return feeders;
    }

    public DexManager dex() {
        return dex;
    }

    /** Especie de un lobo-dino, o null si es un lobo comun (o cualquier otra cosa). */
    public Species speciesOf(Entity e) {
        if (!(e instanceof Wolf)) return null;
        return Species.byId(e.getPersistentDataContainer().get(keys.type, PersistentDataType.STRING));
    }

    public boolean isDino(Entity e) {
        return speciesOf(e) != null;
    }

    public Dino get(Entity e) {
        return e == null ? null : active.get(e.getUniqueId());
    }

    /**
     * @param variant id de color, o null para uno al azar
     * @param male    null para sexo al azar
     * @param growth  0 = recien nacido, 1 = adulto
     * @param owner   dueño (queda domesticado), o null para salvaje
     */
    public Dino spawn(Species species, Location loc, String variant, Boolean male, float growth, OfflinePlayer owner) {
        String v = variant != null ? species.rig.variant(variant).id() : species.rig.randomVariant().id();
        boolean m = male != null ? male : ThreadLocalRandom.current().nextBoolean();
        float g = species.hasBabies() ? growth : 1f;
        Wolf wolf = loc.getWorld().spawn(loc, Wolf.class, w -> {
            Dino.setup(w, keys, species, v, m, g);
            if (owner != null && species.tameable()) w.setOwner(owner);
        });
        Dino d = attach(wolf);
        if (d != null) d.healFully();
        return d;
    }

    /** Crea el modelo para un dino que existe en el mundo (spawn o carga de chunk). */
    public Dino attach(Wolf wolf) {
        if (wolf.isDead() || !wolf.isValid()) return null;
        Dino existing = active.get(wolf.getUniqueId());
        if (existing != null) return existing;
        Species species = speciesOf(wolf);
        if (species == null || species.rig == null) return null;
        Dino d = new Dino(wolf, species, keys, settings, this);
        active.put(wolf.getUniqueId(), d);
        if (registry != null && wolf.isTamed()) registry.update(d);
        return d;
    }

    public void detach(Entity e) {
        Dino d = active.remove(e.getUniqueId());
        if (d != null && !d.isDying()) d.remove();
    }

    public void onDeath(Wolf wolf) {
        Dino d = active.remove(wolf.getUniqueId());
        if (d != null) {
            d.onDeath();
            dying.add(d);
        }
    }

    public void attachLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (keys.isStalePart(e)) e.remove();
                else if (e instanceof Wolf w && isDino(w)) attach(w);
            }
        }
    }

    // ------------------------------------------------------------ limite de domesticados
    /** Maximo de domesticados de este jugador: el del config o el mayor dinoearth.limit.N; 0 = sin limite. */
    public int tamedLimit(org.bukkit.entity.Player p) {
        if (p.hasPermission("dinoearth.limit.unlimited")) return 0;
        int limit = settings.maxTamed;
        if (limit == 0) return 0;
        for (var perm : p.getEffectivePermissions()) {
            String name = perm.getPermission();
            if (!perm.getValue() || !name.startsWith("dinoearth.limit.")) continue;
            try {
                limit = Math.max(limit, Integer.parseInt(name.substring("dinoearth.limit.".length())));
            } catch (NumberFormatException ignored) {
                // dinoearth.limit.unlimited u otro texto
            }
        }
        return limit;
    }

    public int ownedCount(UUID owner) {
        return registry == null ? 0 : registry.ownedBy(owner).size();
    }

    /** ¿Puede quedarse con un dino mas? Desconectado se usa el limite del config (no se pueden ver sus permisos). */
    public boolean canOwnMore(UUID owner) {
        org.bukkit.entity.Player p = Bukkit.getPlayer(owner);
        int limit = p != null ? tamedLimit(p) : settings.maxTamed;
        return limit == 0 || ownedCount(owner) < limit;
    }

    public net.kyori.adventure.text.Component limitMessage(org.bukkit.entity.Player p) {
        return net.kyori.adventure.text.Component.text("Ya tenés " + ownedCount(p.getUniqueId()) + " dinos, el máximo ("
                + tamedLimit(p) + "). Guardá alguno en una jaula o regalalo.",
                net.kyori.adventure.text.format.NamedTextColor.RED);
    }

    // ------------------------------------------------------------ tick (y cuanto tarda, para /dino stats)
    private long tickNanos, tickMaxNanos;
    private int tickSamples;
    private double lastTickMs, lastTickMaxMs;
    private long boneMark, viewerMark, skippedMark;
    private double boneRate, viewerRate, skippedRate;

    /** Huesos mandados por segundo, huesos x jugadores que los reciben por segundo, y los que no hizo falta mandar. */
    public double[] poseRates() {
        return new double[]{boneRate, viewerRate, skippedRate};
    }

    public double avgTickMs() {
        return lastTickMs;
    }

    public double maxTickMs() {
        return lastTickMaxMs;
    }

    public void tick() {
        long start = System.nanoTime();
        tickDinos();
        long took = System.nanoTime() - start;
        tickNanos += took;
        tickMaxNanos = Math.max(tickMaxNanos, took);
        if (++tickSamples >= 100) {
            lastTickMs = tickNanos / 1e6 / tickSamples;
            lastTickMaxMs = tickMaxNanos / 1e6;
            double seconds = tickSamples / 20.0;
            boneRate = (com.dinoearth.model.DinoModel.boneUpdates - boneMark) / seconds;
            viewerRate = (com.dinoearth.model.DinoModel.viewerUpdates - viewerMark) / seconds;
            skippedRate = (com.dinoearth.model.DinoModel.skippedUpdates - skippedMark) / seconds;
            boneMark = com.dinoearth.model.DinoModel.boneUpdates;
            viewerMark = com.dinoearth.model.DinoModel.viewerUpdates;
            skippedMark = com.dinoearth.model.DinoModel.skippedUpdates;
            tickNanos = tickMaxNanos = 0;
            tickSamples = 0;
        }
    }

    private void tickDinos() {
        Iterator<Dino> it = active.values().iterator();
        while (it.hasNext()) {
            Dino d = it.next();
            if (!d.tick()) {
                d.remove();
                it.remove();
            }
        }
        dying.removeIf(d -> !d.tick());
    }

    /** El dino que vuela al lado de este jugador, o null. */
    public Dino escortOf(org.bukkit.entity.Player player) {
        for (Dino d : active.values()) {
            if (d.isEscorting(player)) return d;
        }
        return null;
    }

    public int count() {
        return active.size();
    }

    public List<Dino> all() {
        return new ArrayList<>(active.values());
    }

    public void shutdown() {
        active.values().forEach(Dino::remove);
        dying.forEach(Dino::remove);
        active.clear();
        dying.clear();
    }
}

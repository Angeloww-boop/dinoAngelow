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

    public void tick() {
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

    /** El dino que va en el hombro de este jugador, o null. */
    public Dino perchedOn(org.bukkit.entity.Player player) {
        for (Dino d : active.values()) {
            if (d.isPerchedOn(player)) return d;
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

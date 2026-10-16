package com.dinoearth;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** Valores generales de config.yml (los de cada especie estan en Species.applyConfig). */
public final class Settings {

    public final int growthTicks;
    public final int hatchTicks;
    public final int breedCooldownTicks;
    public final boolean hunger;
    public final int feederCapacity;
    public final int recallMax;
    public final long recallCooldownMs;
    public final long alertCooldownMs;
    /** Domesticados por jugador (0 = sin limite); los permisos dinoearth.limit.N lo suben. */
    public final int maxTamed;
    // Dinopedia
    public final int dexXpStamp;
    public final int dexXpSpecies;
    public final List<String> dexSpeciesCommands;
    public final List<String> dexAllCommands;
    public final double dexRange;
    public final double dexBookRange;
    public final double dexTracksRange;
    public final double dexTrackRange;

    public Settings(FileConfiguration c) {
        growthTicks = (int) (c.getDouble("growth-minutes", 40) * 1200);
        hatchTicks = (int) (c.getDouble("hatch-minutes", 5) * 1200);
        breedCooldownTicks = (int) (c.getDouble("breed-cooldown-minutes", 5) * 1200);
        hunger = c.getBoolean("hunger.enabled", true);
        feederCapacity = Math.max(1, c.getInt("hunger.feeder-capacity", 600));
        recallMax = Math.max(1, c.getInt("recall.max-dinos", 2));
        recallCooldownMs = (long) (Math.max(0, c.getDouble("recall.cooldown-minutes", 5)) * 60_000);
        alertCooldownMs = (long) (Math.max(0, c.getDouble("alerts.attack-cooldown-seconds", 30)) * 1000);
        maxTamed = Math.max(0, c.getInt("limits.max-tamed-per-player", 20));
        dexXpStamp = Math.max(0, c.getInt("dinopedia.xp-per-stamp", 30));
        dexXpSpecies = Math.max(0, c.getInt("dinopedia.xp-species-complete", 150));
        dexSpeciesCommands = c.getStringList("dinopedia.species-complete-commands");
        dexAllCommands = c.getStringList("dinopedia.all-complete-commands");
        dexRange = Math.max(4, c.getDouble("dinopedia.discover-range", 16));
        dexBookRange = Math.max(dexRange, c.getDouble("dinopedia.book-discover-range", 32));
        dexTracksRange = Math.max(0, c.getDouble("dinopedia.tracks-range", 64));
        dexTrackRange = Math.max(16, c.getDouble("dinopedia.track-range", 160));
    }
}

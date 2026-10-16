package com.dinoearth;

import org.bukkit.configuration.file.FileConfiguration;

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

    public Settings(FileConfiguration c) {
        growthTicks = (int) (c.getDouble("growth-minutes", 40) * 1200);
        hatchTicks = (int) (c.getDouble("hatch-minutes", 5) * 1200);
        breedCooldownTicks = (int) (c.getDouble("breed-cooldown-minutes", 5) * 1200);
        hunger = c.getBoolean("hunger.enabled", true);
        feederCapacity = Math.max(1, c.getInt("hunger.feeder-capacity", 600));
        recallMax = Math.max(1, c.getInt("recall.max-dinos", 2));
        recallCooldownMs = (long) (Math.max(0, c.getDouble("recall.cooldown-minutes", 5)) * 60_000);
        alertCooldownMs = (long) (Math.max(0, c.getDouble("alerts.attack-cooldown-seconds", 30)) * 1000);
    }
}

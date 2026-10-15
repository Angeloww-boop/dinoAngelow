package com.dinoearth;

import org.bukkit.configuration.file.FileConfiguration;

/** Valores generales de config.yml (los de cada especie estan en Species.applyConfig). */
public final class Settings {

    public final int growthTicks;
    public final int hatchTicks;
    public final int breedCooldownTicks;
    public final boolean hunger;
    public final int feederCapacity;

    public Settings(FileConfiguration c) {
        growthTicks = (int) (c.getDouble("growth-minutes", 40) * 1200);
        hatchTicks = (int) (c.getDouble("hatch-minutes", 5) * 1200);
        breedCooldownTicks = (int) (c.getDouble("breed-cooldown-minutes", 5) * 1200);
        hunger = c.getBoolean("hunger.enabled", true);
        feederCapacity = Math.max(1, c.getInt("hunger.feeder-capacity", 600));
    }
}

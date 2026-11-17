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
    // genetica
    public final boolean genetics;
    public final double wildVariation;
    public final double geneVariation;
    public final double geneJumpChance;
    public final double geneMin;
    public final double geneMax;
    public final double mutationChance;
    public final double randomColorChance;
    // nidos
    public final boolean nests;
    public final double nestChance;
    public final int nestMaxEggs;
    public final double nestSpacing;
    public final int nestMaxLoaded;
    public final long nestLifetimeMs;
    public final long nestCooldownMs;
    public final double nestGuardRange;
    public final boolean nestOnlyWild;
    public final List<String> nestSpecies;
    /** Item para sacar huevos de un nido (click derecho); null = cualquiera, con click izquierdo como antes. */
    public final org.bukkit.Material nestTool;
    public final int nestToolDamage;
    // dinero (Vault)
    public final double moneyStamp;
    public final double moneySpecies;

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
        genetics = c.getBoolean("genetics.enabled", true);
        wildVariation = Math.max(0, c.getDouble("genetics.wild-variation", 0.05));
        geneVariation = Math.max(0, c.getDouble("genetics.variation", 0.04));
        geneJumpChance = clamp01(c.getDouble("genetics.jump-chance", 0.1));
        double min = c.getDouble("genetics.min", 0.8), max = c.getDouble("genetics.max", 1.3);
        geneMin = Math.max(0.3, Math.min(min, max));
        geneMax = Math.min(3, Math.max(min, max));
        mutationChance = clamp01(c.getDouble("genetics.mutation-chance", 0.03));
        randomColorChance = clamp01(c.getDouble("genetics.random-color-chance", 0.08));
        nests = c.getBoolean("nests.enabled", true);
        nestChance = clamp01(c.getDouble("nests.chance", 0.15));
        nestMaxEggs = Math.max(1, Math.min(3, c.getInt("nests.max-eggs", 3)));
        nestSpacing = Math.max(8, c.getDouble("nests.spacing", 48));
        nestMaxLoaded = Math.max(0, c.getInt("nests.max-loaded", 12));
        nestLifetimeMs = (long) (Math.max(1, c.getDouble("nests.lifetime-minutes", 90)) * 60_000);
        nestCooldownMs = (long) (Math.max(0, c.getDouble("nests.cooldown-minutes", 60)) * 60_000);
        nestGuardRange = Math.max(4, c.getDouble("nests.guard-range", 10));
        nestOnlyWild = c.getBoolean("nests.only-wilderness", true);
        nestSpecies = c.getStringList("nests.species");
        String tool = c.getString("nests.tool", "BRUSH");
        org.bukkit.Material toolMat = tool == null ? null : org.bukkit.Material.matchMaterial(tool.trim());
        nestTool = tool == null || tool.isBlank() || tool.equalsIgnoreCase("ANY") ? null
                : toolMat != null ? toolMat : org.bukkit.Material.BRUSH;
        nestToolDamage = Math.max(0, c.getInt("nests.tool-damage", 1));
        moneyStamp = Math.max(0, c.getDouble("dinopedia.money-per-stamp", 0));
        moneySpecies = Math.max(0, c.getDouble("dinopedia.money-species-complete", 0));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}

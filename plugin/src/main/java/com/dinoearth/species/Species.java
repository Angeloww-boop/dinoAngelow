package com.dinoearth.species;

import com.dinoearth.model.DinoRig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Lo que hace distinta a cada especie: tamaño, stats, temperamento, comida y habilidades.
 * Todas usan un lobo invisible como base; el temperamento decide que IA le dejamos.
 */
public final class Species {

    public enum Body { BIPED, QUADRUPED }

    public enum Temper {
        /** Neutral (como un lobo), caza animales chicos, se domestica con carne. */
        CARNIVORE,
        /** Neutral, no caza nada, se defiende si lo atacan, se domestica con plantas. */
        HERBIVORE,
        /** Ataca a los jugadores que ve. No se domestica. */
        HOSTILE,
        /** Anda en grupo y ataca a jugadores heridos o cuando son muchos. No se domestica. */
        PACK
    }

    public record Drop(Material item, int min, int max) {
    }

    public static final Set<Material> MEAT = EnumSet.of(
            Material.BEEF, Material.CHICKEN, Material.PORKCHOP, Material.MUTTON, Material.RABBIT);
    public static final Set<Material> PLANTS = EnumSet.of(
            Material.WHEAT, Material.CARROT, Material.APPLE, Material.BEETROOT, Material.SWEET_BERRIES,
            Material.MELON_SLICE, Material.HAY_BLOCK);

    public final String id;
    public final String name;
    public final List<String> aliases;
    public final Body body;
    public final Temper temper;
    public double health;
    public double damage;
    public double speed;
    /** Escala del lobo base en un adulto (lobo = 0.6 x 0.85 bloques). */
    public final double hitbox;
    public final double knockback;
    public final float maleScale;
    /** Radianes de ciclo de pasos por bloque recorrido (patas largas = menos). */
    public final float stride;
    public double tameChance;
    public final boolean rideable;
    public double rideWalk;
    public double rideSprint;
    public double rideJump;
    public final boolean spits;
    public final boolean charges;
    /** Distancia de dibujado de un adulto (1 = 64 bloques, segun la "distancia de entidades" del jugador). */
    public final double viewRange;
    /** Cuantos aparecen juntos con /dino spawn. */
    public final int group;
    public final List<Drop> drops;
    public DinoRig rig;

    private Species(Builder b) {
        id = b.id;
        name = b.name;
        aliases = b.aliases;
        body = b.body;
        temper = b.temper;
        health = b.health;
        damage = b.damage;
        speed = b.speed;
        hitbox = b.hitbox;
        knockback = b.knockback;
        maleScale = b.maleScale;
        stride = b.stride;
        tameChance = b.tameChance;
        rideable = b.rideable;
        rideWalk = b.rideWalk;
        rideSprint = b.rideSprint;
        rideJump = b.rideJump;
        spits = b.spits;
        charges = b.charges;
        group = b.group;
        viewRange = b.viewRange;
        drops = b.drops;
    }

    public boolean tameable() {
        return temper == Temper.CARNIVORE || temper == Temper.HERBIVORE;
    }

    public boolean hostile() {
        return temper == Temper.HOSTILE || temper == Temper.PACK;
    }

    /** Comida para domesticar, curar, criar y hacer crecer a las crias. */
    public Set<Material> food() {
        return temper == Temper.HERBIVORE ? PLANTS : MEAT;
    }

    public boolean hasBabies() {
        return rig.baby() != null;
    }

    /** Valores que el dueño del server puede cambiar en config.yml (species.<id>.*). */
    public void applyConfig(ConfigurationSection c) {
        if (c == null) return;
        health = c.getDouble("health", health);
        damage = c.getDouble("damage", damage);
        speed = c.getDouble("speed", speed);
        tameChance = c.getDouble("tame-chance", tameChance);
        rideWalk = c.getDouble("ride.walk-speed", rideWalk);
        rideSprint = c.getDouble("ride.sprint-speed", rideSprint);
        rideJump = c.getDouble("ride.jump", rideJump);
    }

    // ------------------------------------------------------------ especies
    public static final Species RAPTOR = new Builder("raptor", "Velociraptor", Body.BIPED, Temper.CARNIVORE)
            .aliases("velociraptor").stats(30, 6, 0.36).hitbox(1.7).stride(2.4)
            .tame(0.25).ride(0.32, 0.5, 0.58)
            .drops(new Drop(Material.CHICKEN, 1, 2), new Drop(Material.BONE, 0, 2)).build();

    public static final Species TRICERATOPS = new Builder("triceratops", "Triceratops", Body.QUADRUPED, Temper.HERBIVORE)
            .aliases("trike", "tricera").stats(80, 9, 0.27).hitbox(2.4).knockback(1.5).stride(1.7).maleScale(1.08f)
            .tame(0.2).ride(0.24, 0.36, 0.42).charges().view(1.5)
            .drops(new Drop(Material.LEATHER, 2, 5), new Drop(Material.BEEF, 2, 4)).build();

    public static final Species DILOPHOSAURUS = new Builder("dilophosaurus", "Dilophosaurus", Body.BIPED, Temper.HOSTILE)
            .aliases("dilo").stats(24, 5, 0.33).hitbox(1.45).stride(2.6).spits()
            .drops(new Drop(Material.SPIDER_EYE, 1, 2), new Drop(Material.BONE, 0, 2)).build();

    public static final Species COMPSOGNATHUS = new Builder("compsognathus", "Compsognathus", Body.BIPED, Temper.PACK)
            .aliases("compy", "compi").stats(6, 2, 0.4).hitbox(0.55).stride(5.5).group(5).view(0.5)
            .drops(new Drop(Material.CHICKEN, 0, 1)).build();

    public static final List<Species> ALL = List.of(RAPTOR, TRICERATOPS, DILOPHOSAURUS, COMPSOGNATHUS);

    public static Species byId(String id) {
        if (id == null) return null;
        String k = id.toLowerCase();
        for (Species s : ALL) {
            if (s.id.equals(k) || s.aliases.contains(k)) return s;
        }
        return null;
    }

    private static final class Builder {
        final String id, name;
        final Body body;
        final Temper temper;
        List<String> aliases = List.of();
        double health = 20, damage = 4, speed = 0.3, hitbox = 1, knockback = 0, tameChance = 0, viewRange = 1;
        double rideWalk, rideSprint, rideJump;
        float maleScale = 1.06f, stride = 2.4f;
        boolean rideable, spits, charges;
        int group = 1;
        List<Drop> drops = List.of();

        Builder(String id, String name, Body body, Temper temper) {
            this.id = id;
            this.name = name;
            this.body = body;
            this.temper = temper;
        }

        Builder aliases(String... a) {
            aliases = List.of(a);
            return this;
        }

        Builder stats(double health, double damage, double speed) {
            this.health = health;
            this.damage = damage;
            this.speed = speed;
            return this;
        }

        Builder hitbox(double v) {
            hitbox = v;
            return this;
        }

        Builder knockback(double v) {
            knockback = v;
            return this;
        }

        Builder stride(double v) {
            stride = (float) v;
            return this;
        }

        Builder maleScale(float v) {
            maleScale = v;
            return this;
        }

        Builder tame(double chance) {
            tameChance = chance;
            return this;
        }

        Builder ride(double walk, double sprint, double jump) {
            rideable = true;
            rideWalk = walk;
            rideSprint = sprint;
            rideJump = jump;
            return this;
        }

        Builder spits() {
            spits = true;
            return this;
        }

        Builder charges() {
            charges = true;
            return this;
        }

        Builder view(double range) {
            viewRange = range;
            return this;
        }

        Builder group(int n) {
            group = n;
            return this;
        }

        Builder drops(Drop... d) {
            drops = List.of(d);
            return this;
        }

        Species build() {
            return new Species(this);
        }
    }
}

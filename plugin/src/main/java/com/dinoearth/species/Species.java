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

    /** FLYER: camina en cuatro patas (las alas plegadas) y vuela. */
    public enum Body { BIPED, QUADRUPED, FLYER }

    public enum Temper {
        /** Neutral (como un lobo), caza animales chicos, se domestica con carne. */
        CARNIVORE,
        /** Neutral, no caza nada, se defiende si lo atacan, se domestica con plantas. */
        HERBIVORE,
        /** Ataca a los jugadores que ve. No se domestica. */
        HOSTILE,
        /** Anda en grupo y ataca a jugadores heridos o cuando son muchos. No se domestica. */
        PACK,
        /** Presa: herbivoro en manada que huye de los jugadores (agachado te podes acercar). Se domestica con plantas. */
        PREY
    }

    /** Arma de la cola contra el que le pega por atras. */
    public enum TailAttack { NONE, SPIKES, CLUB }

    public record Drop(Material item, int min, int max) {
    }

    /** Que come: decide con que comida se domestica, crece y se alimenta, y de que comedero come. */
    public enum Diet {
        MEAT("carne"), PLANTS("plantas"), FISH("pescado");

        public final String label;

        Diet(String label) {
            this.label = label;
        }

        public static Diet byId(String id) {
            for (Diet d : values()) {
                if (d.name().equalsIgnoreCase(id) || d.label.equalsIgnoreCase(id)) return d;
            }
            return null;
        }
    }

    public static final Set<Material> MEAT = EnumSet.of(
            Material.BEEF, Material.CHICKEN, Material.PORKCHOP, Material.MUTTON, Material.RABBIT);
    public static final Set<Material> FISH = EnumSet.of(
            Material.COD, Material.SALMON, Material.TROPICAL_FISH);
    public static final Set<Material> PLANTS = EnumSet.of(
            Material.WHEAT, Material.CARROT, Material.APPLE, Material.BEETROOT, Material.SWEET_BERRIES,
            Material.MELON_SLICE, Material.HAY_BLOCK);

    /** Puntos de comida que da cada cosa (para el hambre y para cargar comederos). */
    public static int foodPoints(Material m) {
        return switch (m) {
            case BEEF, PORKCHOP -> 10;
            case MUTTON, SALMON -> 8;
            case CHICKEN, RABBIT, COD, APPLE -> 6;
            case WHEAT, CARROT -> 5;
            case BEETROOT, TROPICAL_FISH -> 4;
            case SWEET_BERRIES, MELON_SLICE -> 3;
            case HAY_BLOCK -> 45;
            default -> 0;
        };
    }

    public static Set<Material> foodOf(Diet diet) {
        return switch (diet) {
            case MEAT -> MEAT;
            case PLANTS -> PLANTS;
            case FISH -> FISH;
        };
    }

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
    /** Ruge y asusta (lentitud + oscuridad). Montado ruge con la barra espaciadora en vez de saltar. */
    public final boolean roars;
    /** Salvaje persigue jugadores aunque su temperamento sea domesticable. */
    public final boolean territorial;
    /** No se domestica dandole comida: solo naciendo de un huevo. */
    public final boolean eggTameOnly;
    /** Grados que se balancean las patas al caminar (las patas largas, menos). */
    public final float swing;
    public double flySpeed;
    public double flySprint;
    private final Set<Material> food;
    public final TailAttack tail;
    /** Las flechas y otros proyectiles le hacen mucho menos daño. */
    public final boolean armored;
    /** Domesticado acompaña volando al dueño (Dino.tickEscort); agachado + click derecho con la mano vacia: vuela o camina. */
    public final boolean perches;
    /** Minutos que tarda en pasar de lleno a muerto de hambre (0 = no tiene hambre). */
    public double hungerMinutes;
    /** Puntos de comida que le entran de vacio a lleno. */
    public final int stomach;
    /** Para la Dinopedia: tamaño de un adulto y una frase sobre la especie. */
    public final String size;
    public final String about;
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
        roars = b.roars;
        territorial = b.territorial;
        eggTameOnly = b.eggTameOnly;
        swing = b.swing;
        flySpeed = b.flySpeed;
        flySprint = b.flySprint;
        food = b.food;
        tail = b.tail;
        armored = b.armored;
        perches = b.perches;
        hungerMinutes = b.hungerMinutes;
        stomach = b.stomach;
        size = b.size;
        about = b.about;
        group = b.group;
        viewRange = b.viewRange;
        drops = b.drops;
    }

    public boolean tameable() {
        return temper == Temper.CARNIVORE || temper == Temper.HERBIVORE || temper == Temper.PREY;
    }

    /** Come plantas (herbivoro o presa). */
    public boolean herbivore() {
        return temper == Temper.HERBIVORE || temper == Temper.PREY;
    }

    public boolean hostile() {
        return temper == Temper.HOSTILE || temper == Temper.PACK;
    }

    /** Comida para domesticar, curar, criar y hacer crecer a las crias. */
    public Set<Material> food() {
        if (food != null) return food;
        return herbivore() ? PLANTS : MEAT;
    }

    public boolean flies() {
        return body == Body.FLYER;
    }

    public Diet diet() {
        Set<Material> f = food();
        return f == FISH ? Diet.FISH : f == PLANTS ? Diet.PLANTS : Diet.MEAT;
    }

    public boolean getsHungry() {
        return hungerMinutes > 0 && tameable();
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
        flySpeed = c.getDouble("ride.fly-speed", flySpeed);
        flySprint = c.getDouble("ride.fly-sprint-speed", flySprint);
        hungerMinutes = c.getDouble("hunger-minutes", hungerMinutes);
    }

    // ------------------------------------------------------------ especies
    public static final Species RAPTOR = new Builder("raptor", "Velociraptor", Body.BIPED, Temper.CARNIVORE)
            .aliases("velociraptor").stats(30, 6, 0.36).hitbox(1.7).stride(2.4)
            .tame(0.25).ride(0.32, 0.5, 0.58).hunger(80, 40)
            .about("2 × 3,6 bloques", "Cazador ágil y astuto. Domesticado es una montura rápida y saltarina.")
            .drops(new Drop(Material.CHICKEN, 1, 2)).build();

    public static final Species TRICERATOPS = new Builder("triceratops", "Triceratops", Body.QUADRUPED, Temper.HERBIVORE)
            .aliases("trike", "tricera").stats(80, 9, 0.27).hitbox(2.4).knockback(1.5).stride(1.7).maleScale(1.08f)
            .tame(0.2).ride(0.24, 0.36, 0.42).charges().view(1.5).hunger(100, 90)
            .about("2,2 × 5,3 bloques", "Tranquilo hasta que lo molestan. Montado y corriendo arrolla lo que tenga adelante.")
            .drops(new Drop(Material.BEEF, 2, 4)).build();

    public static final Species DILOPHOSAURUS = new Builder("dilophosaurus", "Dilophosaurus", Body.BIPED, Temper.HOSTILE)
            .aliases("dilo").stats(24, 5, 0.33).hitbox(1.45).stride(2.6).spits()
            .about("2 × 3,3 bloques", "Abre la gola del cuello y escupe veneno que ciega. Mejor verlo de lejos.")
            .drops().build();

    public static final Species COMPSOGNATHUS = new Builder("compsognathus", "Compsognathus", Body.BIPED, Temper.PACK)
            .aliases("compy", "compi").stats(6, 2, 0.4).hitbox(0.55).stride(5.5).group(5).view(0.5)
            .about("0,6 × 1,4 bloques", "Chiquito y cobarde solo, peligroso en banda: ataca a los heridos.")
            .drops(new Drop(Material.CHICKEN, 0, 1)).build();

    public static final Species TREX = new Builder("trex", "Tyrannosaurus rex", Body.BIPED, Temper.CARNIVORE)
            .aliases("t-rex", "rex", "tiranosaurio", "tyrannosaurus").stats(160, 14, 0.3).hitbox(3.2).knockback(1.2)
            .stride(1.3).swing(26).territorial().eggTameOnly().roars().ride(0.26, 0.42, 0).view(2).hunger(70, 120)
            .about("3,4 × 7,3 bloques", "El rey. Su rugido paraliza de miedo. Solo se domestica criándolo desde el huevo.")
            .drops(new Drop(Material.BEEF, 4, 8))
            .build();

    public static final Species PTERANODON = new Builder("pteranodon", "Pteranodon", Body.FLYER, Temper.CARNIVORE)
            .aliases("ptera", "ptero", "pterodactilo", "pterodactyl").stats(28, 4, 0.3).hitbox(1.6).stride(3.2)
            .food(FISH).tame(0.2).ride(0.16, 0.22, 0.55).fly(0.55, 0.9).view(1.5).hunger(80, 36)
            .about("6,5 bloques de ala a ala", "Reptil volador que pesca en las costas. Domesticado te lleva por el aire.")
            .drops(new Drop(Material.CHICKEN, 1, 2))
            .build();

    public static final Species PARASAUROLOPHUS = new Builder("parasaurolophus", "Parasaurolophus", Body.QUADRUPED, Temper.PREY)
            .aliases("para", "parasaurio").stats(60, 6, 0.3).hitbox(2.2).stride(1.8).group(4).view(1.5)
            .tame(0.15).ride(0.27, 0.42, 0.5).hunger(100, 80)
            .about("2,4 × 5,2 bloques", "Anda en manada y huye. Cuando uno se asusta trompetea con la cresta y salen todos corriendo.")
            .drops(new Drop(Material.BEEF, 2, 3)).build();

    public static final Species GALLIMIMUS = new Builder("gallimimus", "Gallimimus", Body.BIPED, Temper.PREY)
            .aliases("galli", "gallimimo").stats(24, 3, 0.42).hitbox(1.95).stride(2.0).swing(46).group(5)
            .tame(0.15).ride(0.36, 0.64, 0.62).hunger(70, 40)
            .about("2,2 × 3,7 bloques", "El \"avestruz\" de los dinosaurios: huye en bandada y domesticado es la montura más rápida.")
            .drops(new Drop(Material.CHICKEN, 1, 2)).build();

    public static final Species STEGOSAURUS = new Builder("stegosaurus", "Stegosaurus", Body.QUADRUPED, Temper.HERBIVORE)
            .aliases("stego", "estegosaurio").stats(90, 10, 0.25).hitbox(2.2).knockback(0.8).stride(1.7).view(1.5)
            .tame(0.15).ride(0.22, 0.34, 0.4).tail(TailAttack.SPIKES).hunger(110, 90)
            .about("2,4 × 4,5 bloques", "Placas en el lomo y púas en la cola: al que le pega por atrás le da un coletazo.")
            .drops(new Drop(Material.BEEF, 2, 4)).build();

    public static final Species ANKYLOSAURUS = new Builder("ankylosaurus", "Ankylosaurus", Body.QUADRUPED, Temper.HERBIVORE)
            .aliases("anky", "anquilosaurio").stats(110, 8, 0.22).hitbox(2.0).knockback(1.5).stride(2.2).view(1.5)
            .tame(0.15).ride(0.2, 0.3, 0.35).tail(TailAttack.CLUB).armored().hunger(110, 90)
            .about("1,6 × 4,3 bloques", "Un tanque acorazado: las flechas casi no le hacen nada y la maza de la cola tira lejos.")
            .drops(new Drop(Material.BEEF, 2, 4)).build();

    public static final Species GIGANOTOSAURUS = new Builder("giganotosaurus", "Giganotosaurus", Body.BIPED, Temper.CARNIVORE)
            .aliases("giga", "giganotosaurio").stats(150, 15, 0.33).hitbox(3.35).knockback(1.2).stride(1.3).swing(28)
            .territorial().eggTameOnly().roars().ride(0.28, 0.46, 0).view(2).hunger(65, 120)
            .about("3,3 × 7,6 bloques", "El gigante argentino, rival del T-Rex: un poco más rápido y más largo. Solo se cría desde el huevo.")
            .drops(new Drop(Material.BEEF, 4, 8))
            .build();

    public static final Species CARNOTAURUS = new Builder("carnotaurus", "Carnotaurus", Body.BIPED, Temper.CARNIVORE)
            .aliases("carno", "carnotauro").stats(45, 8, 0.4).hitbox(2.25).knockback(0.8).stride(2.0).swing(40)
            .tame(0.12).ride(0.34, 0.56, 0.55).charges().view(1.5).hunger(80, 50)
            .about("2,4 × 4,4 bloques", "El \"toro carnívoro\" argentino: cuernos sobre los ojos y patas largas. Montado y corriendo embiste.")
            .drops(new Drop(Material.BEEF, 2, 4)).build();

    public static final Species DIMORPHODON = new Builder("dimorphodon", "Dimorphodon", Body.FLYER, Temper.CARNIVORE)
            .aliases("dimo", "dimorfodonte").stats(10, 2, 0.32).hitbox(0.75).stride(5).group(2).view(0.8)
            .food(FISH).tame(0.33).perches().hunger(90, 16)
            .about("0,7 bloques, 2 de ala a ala", "Pterosaurio chiquito y cabezón. Domesticado vuela a tu lado y chilla si hay monstruos cerca.")
            .drops().build();

    public static final List<Species> ALL = List.of(RAPTOR, TRICERATOPS, DILOPHOSAURUS, COMPSOGNATHUS, TREX, PTERANODON,
            PARASAUROLOPHUS, GALLIMIMUS, STEGOSAURUS, ANKYLOSAURUS, GIGANOTOSAURUS, CARNOTAURUS, DIMORPHODON);

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
        double rideWalk, rideSprint, rideJump, flySpeed, flySprint;
        float maleScale = 1.06f, stride = 2.4f, swing = 38;
        boolean rideable, spits, charges, roars, territorial, eggTameOnly, armored, perches;
        TailAttack tail = TailAttack.NONE;
        Set<Material> food;
        double hungerMinutes;
        int stomach = 40;
        String size = "?", about = "";
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

        Builder roars() {
            roars = true;
            return this;
        }

        Builder territorial() {
            territorial = true;
            return this;
        }

        Builder eggTameOnly() {
            eggTameOnly = true;
            return this;
        }

        Builder swing(double degrees) {
            swing = (float) degrees;
            return this;
        }

        Builder food(Set<Material> items) {
            food = items;
            return this;
        }

        Builder tail(TailAttack attack) {
            tail = attack;
            return this;
        }

        Builder armored() {
            armored = true;
            return this;
        }

        Builder perches() {
            perches = true;
            return this;
        }

        Builder hunger(double minutes, int stomachPoints) {
            hungerMinutes = minutes;
            stomach = stomachPoints;
            return this;
        }

        Builder about(String adultSize, String text) {
            size = adultSize;
            about = text;
            return this;
        }

        Builder fly(double speed, double sprint) {
            flySpeed = speed;
            flySprint = sprint;
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

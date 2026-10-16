package com.dinoearth.dino;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import com.dinoearth.Keys;
import com.dinoearth.Settings;
import com.dinoearth.feeder.FeederManager;
import com.dinoearth.model.DinoModel;
import com.dinoearth.model.DinoRig;
import com.dinoearth.species.Species;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Un dinosaurio: un lobo invisible (IA, hitbox, vida, domesticacion) + el modelo de
 * item_displays. Lo que cambia entre especies (stats, temperamento, habilidades)
 * viene de {@link Species}; aca esta la logica comun, las animaciones y la montura.
 */
public final class Dino {

    private static final int ATTACK_TICKS = 9;
    private static final int ROAR_TICKS = 24;
    // el lobo muerto desaparece a los 20 ticks: la caida tiene que terminar antes
    private static final int DEATH_TICKS = 18;
    private static final int SPIT_TICKS = 16;
    private static final int ROAR_COOLDOWN = 140;
    private static final int EAT_TICKS = 40;
    private static final int TAIL_TICKS = 10;

    private final Wolf wolf;
    private final Species species;
    private final DinoRig rig;
    private final Keys keys;
    private final Settings settings;
    private final DinoManager manager;

    private final String variant;
    private final boolean male;
    private Genes genes;
    private static final Genes NEUTRAL = Genes.neutral();
    private float growth;
    private boolean saddled;

    private DinoRig.Form form;
    private DinoModel model;
    private int body, head, jaw, tail1, tail2, legR, legL, legFR, legFL, legBR, legBL, frillR, frillL, shinR, shinL;
    private int wingR, wingL, wingR2, wingL2, memR, memL, memR2, memL2;

    private Location last;
    private float speed;      // 0..1 suavizado
    private float phase;      // ciclo de pasos
    private float sit;        // 0..1 suavizado
    private float frill;      // 0 plegada .. 1 abierta (dilophosaurus)
    private float charge;     // 0..1 cabeza baja embistiendo (triceratops)
    private float rideYaw;
    private int jumpCooldown;
    // carrera pareja: dino de la pista (no es de nadie), quieto hasta la largada y con estamina para correr
    private boolean racing;
    private boolean frozen;
    private float stamina = 1f;
    private boolean exhausted;
    private double staminaDrain, staminaRegen;
    private int roarCooldown;

    // vuelo (pteranodon)
    private boolean flying;
    private int flyTicks;
    private int wildFlight;      // ticks que le quedan volando solo (0 = aterrizando)
    private Location flightCenter;
    private double flightAngle, flightRadius, flightAltitude;
    private double airSpeed;
    private final Vector flyVel = new Vector();   // velocidad que queremos (el roce del aire la iria frenando)
    private int stuck;
    private float fold = 1;      // 0 alas abiertas .. 1 plegadas
    private float flap;          // 0..1 cuanto aletea
    private float flapPhase;
    private float flightPitch, flightRoll, lastYaw;
    private Player lastRider;

    // nado (spinosaurus, mosasaurus): en el agua no tiene gravedad y se mueve con flyVel, como el vuelo
    private boolean swimming;
    private int dryTicks;
    private Location swimTarget;
    private int swimRetarget;
    private int swimStuck;
    private int biteCooldown;
    private float swimPhase;

    // hambre (solo domesticados)
    private float hunger = 1;    // 1 lleno .. 0 muerto de hambre
    private boolean starvingSit; // lo sentamos nosotros por hambre (no el dueño)
    private float sick;          // 0..1 cabeza y cola caidas
    private FeederManager.Feeder feeder;  // comedero al que va a comer
    private int seekTicks;
    private int eating = -1;

    // presas, cola, hombro
    private int fleeTicks;
    private Location fleeFrom;
    private Location lastAttacker;   // de donde vino el ultimo golpe
    private int tailSwing = -1;
    private int tailCooldown;
    private float tailSide = 1;
    private Player escort;           // dueño al que acompaña volando (dimorphodon)
    private Location escortLast;
    private final Vector escortVel = new Vector();
    private double escortAngle;
    private int escortStuck;
    private boolean walkOnly;        // el dueño lo puso a caminar: no despega solo para seguirlo
    private int alertCooldown;
    private int age;
    private int attack = -1;
    private int roar = -1;
    private int hurt;
    private int dying = -1;
    private int spit = -1;
    private int spitCooldown = 40;
    private float drawnYaw;      // ultimo giro con el que se dibujo (la muerte sigue desde ahi)
    private float deathYaw;
    private int fallSide = 1;
    private float fallLift;
    // ordenes del silbato
    public enum Order { FOLLOW, STAY, PATROL }

    public enum Combat { PASSIVE, DEFENSIVE, AGGRESSIVE }

    private Order order = Order.FOLLOW;
    private Combat combat = Combat.DEFENSIVE;
    private UUID patrolWorld;
    private int patrolCx, patrolCz;
    @SuppressWarnings("rawtypes")
    private Goal followGoal;          // el "seguir al dueño" del lobo, sacado mientras patrulla
    private Component nameComp;       // nombre puesto con una etiqueta
    private TextDisplay plate;        // cartel con el nombre y el dueño
    private int poseEvery = 2;   // ticks entre poses (0 = nadie lo ve, no se manda nada)
    private int lastPose;
    private boolean wasStill, quiet;
    private double rideDist;         // bloques montado desde la ultima vez que se conto para las misiones
    private final Map<UUID, Integer> rammed = new HashMap<>();

    public Dino(Wolf wolf, Species species, Keys keys, Settings settings, DinoManager manager) {
        this.wolf = wolf;
        this.species = species;
        this.rig = species.rig;
        this.keys = keys;
        this.settings = settings;
        this.manager = manager;
        PersistentDataContainer pdc = wolf.getPersistentDataContainer();
        this.hunger = pdc.getOrDefault(keys.hunger, PersistentDataType.FLOAT, 1f);
        this.starvingSit = pdc.getOrDefault(keys.starving, PersistentDataType.BYTE, (byte) 0) == 1;
        this.variant = rig.variant(pdc.getOrDefault(keys.variant, PersistentDataType.STRING, "")).id();
        this.male = "male".equals(pdc.get(keys.sex, PersistentDataType.STRING));
        float g = pdc.getOrDefault(keys.growth, PersistentDataType.FLOAT, 1f);
        this.growth = species.hasBabies() ? g : 1f;
        this.saddled = pdc.getOrDefault(keys.saddled, PersistentDataType.BYTE, (byte) 0) == 1;
        this.genes = Genes.read(pdc, keys);
        if (genes == null) {
            // dinos de antes de la genetica: los domesticados quedan normales, los salvajes sacan rasgos al azar
            genes = wolf.isTamed() ? Genes.neutral() : Genes.wild(settings);
            genes.write(pdc, keys);
        }
        this.last = wolf.getLocation();
        this.sit = wolf.isSitting() ? 1 : 0;
        this.rideYaw = wolf.getBodyYaw();
        this.age = ThreadLocalRandom.current().nextInt(1000);
        if (rider() == null) wolf.setAware(true);
        if (species.flies() || species.swims) wolf.setGravity(true);   // por si se guardo volando o nadando
        adjustGoals();
        loadOrders(pdc);
        form = isBabyForm() ? rig.baby() : rig.adult();
        updateStats();
        buildModel();
    }

    /** Marca un lobo recien spawneado como dinosaurio de una especie. */
    public static void setup(Wolf wolf, Keys keys, Species species, String variant, boolean male, float growth) {
        PersistentDataContainer pdc = wolf.getPersistentDataContainer();
        pdc.set(keys.type, PersistentDataType.STRING, species.id);
        pdc.set(keys.variant, PersistentDataType.STRING, variant);
        pdc.set(keys.sex, PersistentDataType.STRING, male ? "male" : "female");
        pdc.set(keys.growth, PersistentDataType.FLOAT, growth);
        wolf.setInvisible(true);
        wolf.setSilent(true);
        wolf.setAdult();
        wolf.customName(Component.text(species.name));
        wolf.setCustomNameVisible(false);
        wolf.setRemoveWhenFarAway(false);
    }

    /** Saca la IA de lobo que no corresponde a la especie (no se guarda, se hace en cada carga). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void adjustGoals() {
        MobGoals goals = Bukkit.getMobGoals();
        GoalKey[] remove = switch (species.temper) {
            case HERBIVORE, PREY -> new GoalKey[]{VanillaGoal.NON_TAME_RANDOM, VanillaGoal.NEAREST_ATTACKABLE,
                    VanillaGoal.LEAP_AT, VanillaGoal.WOLF_BEG, VanillaGoal.WOLF_AVOID_ENTITY};
            case HOSTILE, PACK -> new GoalKey[]{VanillaGoal.WOLF_BEG, VanillaGoal.WOLF_AVOID_ENTITY};
            case CARNIVORE -> new GoalKey[]{VanillaGoal.WOLF_AVOID_ENTITY};
        };
        for (GoalKey key : remove) {
            try {
                ((MobGoals) goals).removeGoal((Mob) wolf, key);
            } catch (RuntimeException ignored) {
                // la clave puede no existir en otra version del servidor
            }
        }
    }

    // ------------------------------------------------------------ modelo y tamaño
    private void buildModel() {
        if (model != null) model.remove();
        form = isBabyForm() ? rig.baby() : rig.adult();
        String modelName = isBabyForm() ? "baby" : (male ? "male" : "female");
        NamespacedKey[] items = rig.items(form, variant, modelName);
        if (!isBabyForm() && saddled && rig.hasSaddle()) items[rig.saddleBone()] = rig.saddleItem();
        // los pasajeros de un lobo van ~0.96 de su altura mas arriba
        Vector mount = new Vector(0, wolf.getHeight() * 0.963, 0);
        // caja de culling segun el tamaño (si es chica, las partes desaparecen al mirarlas de costado)
        float k = rig.scale() * species.maleScale / 16f;
        float cullWidth = Math.max(4f, form.radius() * k * 2 + 1);
        float cullHeight = Math.max(3f, form.height() * k + 1);
        model = new DinoModel(form.bones(), items, wolf.getLocation(), wolf, mount, cullWidth, cullHeight, keys::tagPart);
        body = form.index("body");
        head = form.index("head");
        jaw = form.index("jaw");
        tail1 = form.index("tail1");
        tail2 = form.index("tail2");
        legR = form.index("leg_r");
        legL = form.index("leg_l");
        legFR = form.index("leg_fr");
        legFL = form.index("leg_fl");
        legBR = form.index("leg_br");
        legBL = form.index("leg_bl");
        frillR = form.index("frill_r");
        frillL = form.index("frill_l");
        shinR = form.index("shin_r");
        shinL = form.index("shin_l");
        wingR = form.index("wing_r");
        wingL = form.index("wing_l");
        wingR2 = form.index("wing_r2");
        wingL2 = form.index("wing_l2");
        memR = form.index("mem_r");
        memL = form.index("mem_l");
        memR2 = form.index("mem_r2");
        memL2 = form.index("mem_l2");
        updateViewRange();
        animate();
        drawnYaw = wolf.getBodyYaw();
        model.applyPose(wolf.getLocation(), drawnYaw, visualScale(), 0);
        lastPose = age;
        refreshPlate();
    }

    private boolean isBabyForm() {
        return species.hasBabies() && growth < 0.5f;
    }

    /** Escala del modelo actual: la cria crece de 0.65 a 1, despues pasa al modelo adulto de 0.6 a 1. */
    private float modelScale() {
        float s = !species.hasBabies() ? 1f
                : growth < 0.5f ? lerp(0.65f, 1f, growth / 0.5f) : lerp(0.6f, 1f, (growth - 0.5f) / 0.5f);
        return s * (male ? species.maleScale : 1f) * g().size;
    }

    /** Los genes que cuentan para los stats (si la genetica esta apagada en el config, todos normales). */
    private Genes g() {
        return settings.genetics ? genes : NEUTRAL;
    }

    public Genes genes() {
        return genes;
    }

    /** Este dino como padre de una cria (color, sexo y nombre sin colores). */
    public Genes.Parent asParent() {
        String n = nameComp != null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(nameComp) : null;
        return new Genes.Parent(variant, male, n);
    }

    private float visualScale() {
        return rig.scale() * modelScale();
    }

    /** Tamaño comparado con un adulto (1 = adulto hembra). */
    private float relativeSize() {
        float ratio = isBabyForm() ? rig.baby().height() / rig.adult().height() : 1f;
        return ratio * modelScale();
    }

    private void updateStats() {
        float size = relativeSize();
        set(Attribute.SCALE, species.hitbox * size);
        set(Attribute.MAX_HEALTH, Math.round(species.health * g().health * (0.25 + 0.75 * Math.min(1f, size))));
        set(Attribute.ATTACK_DAMAGE, Math.max(1, species.damage * Math.min(1.1f, size)));
        double walk = species.speed * g().speed;
        set(Attribute.MOVEMENT_SPEED, growth >= 1 ? walk : walk * (0.85 + 0.15 * growth));
        set(Attribute.STEP_HEIGHT, growth >= 0.5f && species.hitbox > 1 ? 1.0 : 0.6);
        set(Attribute.ATTACK_KNOCKBACK, species.knockback * Math.min(1f, size));
        set(Attribute.FOLLOW_RANGE, species.hostile() ? 24 : 16);
        var max = wolf.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && wolf.getHealth() > max.getValue()) wolf.setHealth(max.getValue());
    }

    private void set(Attribute attribute, double value) {
        var inst = wolf.getAttribute(attribute);
        if (inst != null) inst.setBaseValue(value);
    }

    public void healFully() {
        var max = wolf.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) wolf.setHealth(max.getValue());
    }

    public void heal(double amount) {
        var max = wolf.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) wolf.setHealth(Math.min(max.getValue(), wolf.getHealth() + amount));
    }

    public boolean isHurt() {
        var max = wolf.getAttribute(Attribute.MAX_HEALTH);
        return max != null && wolf.getHealth() < max.getValue() - 0.01;
    }

    // ------------------------------------------------------------ datos
    public Wolf wolf() {
        return wolf;
    }

    public Species species() {
        return species;
    }

    public String variant() {
        return variant;
    }

    public boolean isMale() {
        return male;
    }

    public float growth() {
        return growth;
    }

    public boolean isAdult() {
        return growth >= 1f;
    }

    public boolean isSaddled() {
        return saddled;
    }

    public boolean isDying() {
        return dying >= 0;
    }

    public boolean isFlying() {
        return flying;
    }

    // ------------------------------------------------------------ carreras
    /** Dino que presta una pista de carreras: no cuenta como de nadie, no se guarda y no se puede tocar. */
    public boolean isRental() {
        return wolf.getPersistentDataContainer().has(keys.rental);
    }

    /**
     * Modo carrera: sin habilidades (rugido, embestida) y con estamina. Correr (Ctrl) gasta la barra en drainSeconds;
     * vacia, no deja correr hasta recuperar el 30 %. Se recupera en regenSeconds.
     */
    public void startRacing(double drainSeconds, double regenSeconds) {
        racing = true;
        stamina = 1f;
        exhausted = false;
        staminaDrain = 1.0 / Math.max(1, drainSeconds * 20);
        staminaRegen = 1.0 / Math.max(1, regenSeconds * 20);
    }

    public void setFrozen(boolean value) {
        frozen = value;
    }

    public float stamina() {
        return stamina;
    }

    public boolean isExhausted() {
        return exhausted;
    }

    public boolean canRide() {
        return species.rideable && rig.hasSaddle() && isAdult() && saddled && wolf.isTamed() && !isStarving();
    }

    // ------------------------------------------------------------ hambre
    /** Si esta especie (domesticada) tiene hambre con esta configuracion. */
    public boolean tracksHunger() {
        return settings.hunger && species.getsHungry() && wolf.isTamed() && !isRental();
    }

    public float hunger() {
        return hunger;
    }

    public boolean isStarving() {
        return tracksHunger() && hunger <= 0;
    }

    /** Tiene lugar para comer (los salvajes no: a esos la comida los domestica). */
    public boolean wantsFood() {
        return tracksHunger() && hunger < 0.97f;
    }

    /** Come puntos de comida (de la mano o de un comedero). */
    public void feed(int points) {
        hunger = Math.min(1f, hunger + points / (float) Math.max(1, species.stomach));
        if (starvingSit && hunger > 0.12f) standUp();
        save();
    }

    public void setHunger(float value) {
        hunger = Math.max(0f, Math.min(1f, value));
        if (starvingSit && hunger > 0.12f) standUp();
        save();
    }

    private void standUp() {
        starvingSit = false;
        if (rider() == null) wolf.setSitting(false);
    }

    private void tickHunger(Player rider) {
        if (!tracksHunger()) {
            sick += (0 - sick) * 0.1f;
            if (starvingSit) standUp();
            return;
        }
        if (hunger > 0) hunger = Math.max(0f, hunger - 1f / (float) (species.hungerMinutes * 1200));
        if (age % 200 == 0) save();
        boolean starving = hunger <= 0;
        sick += ((starving ? 1f : 0f) - sick) * 0.05f;
        if (starving) {
            if (rider != null) {
                wolf.removePassenger(rider);
                com.dinoearth.text.Lang.get().action(rider, "hambre-no-lleva", "especie", species.name);
            }
            if (flying) wildFlight = 0;
            // se echa, como si estuviera mal (salvo que este yendo a comer)
            if (!flying && !swimming && feeder == null && !wolf.isSitting() && age % 20 == 0) {
                wolf.setSitting(true);
                starvingSit = true;
            }
            if (age % 160 == 0) {
                play("hurt", 0.35f, 0.7f);
                wolf.getWorld().spawnParticle(Particle.SMOKE, wolf.getLocation().add(0, wolf.getHeight() * 0.8, 0),
                        4, 0.3, 0.2, 0.3, 0.01);
            }
        } else if (hunger < 0.25f && age % 600 == 0) {
            play("ambient", 0.5f, 0.8f);   // pide comida
        }
        if (starvingSit && hunger > 0.12f) standUp();
        tickFeeder(rider);
    }

    /** Con hambre busca un comedero de su dieta cerca, camina hasta el y come. */
    private void tickFeeder(Player rider) {
        if (eating >= 0) {
            if (++eating == EAT_TICKS / 2 && feeder != null && feeder.isValid()) {
                int wanted = (int) Math.ceil((1 - hunger) * species.stomach);
                int got = manager.feeders().take(feeder, wanted);
                if (got > 0) {
                    feed(got);
                    heal(got / (float) species.stomach * 0.3 * species.health);
                    wolf.getWorld().playSound(wolf.getLocation(), "minecraft:entity.generic.eat", SoundCategory.NEUTRAL, 1f, 0.8f);
                    Material bite = Species.foodOf(species.diet()).iterator().next();
                    wolf.getWorld().spawnParticle(Particle.ITEM, feeder.location().add(0, 0.5, 0), 10, 0.25, 0.1, 0.25,
                            0.05, new ItemStack(bite));
                }
            }
            if (eating > EAT_TICKS) {
                eating = -1;
                feeder = null;
            }
            return;
        }
        if (feeder != null) {
            if (!feeder.isValid() || feeder.points() <= 0 || rider != null || flying || swimming
                    || feeder.location().getWorld() != wolf.getWorld()) {
                feeder = null;
                return;
            }
            Location at = feeder.location();
            Location me = wolf.getLocation();
            double reach = 1.1 + wolf.getWidth() / 2;
            if (Math.hypot(at.getX() - me.getX(), at.getZ() - me.getZ()) <= reach && Math.abs(at.getY() - me.getY()) < 2) {
                wolf.getPathfinder().stopPathfinding();
                float yaw = (float) Math.toDegrees(Math.atan2(-(at.getX() - me.getX()), at.getZ() - me.getZ()));
                wolf.setRotation(yaw, 30);
                wolf.setBodyYaw(yaw);
                eating = 0;
            } else if (seekTicks++ % 20 == 0) {
                if (wolf.isSitting()) wolf.setSitting(false);
                wolf.getPathfinder().moveTo(at, 1.0);
            }
            if (seekTicks > 400) feeder = null;   // no llega: se rinde (si no hay hambre extrema, vuelve a probar)
            return;
        }
        if (age % 40 != 0 || hunger >= 0.6f || rider != null || flying || swimming || escort != null || wolf.isLeashed()) return;
        if (wolf.isSitting() && !starvingSit) return;   // el dueño lo dejo sentado: espera
        FeederManager.Feeder near = manager.feeders().nearest(wolf.getLocation(), species.diet(), 16);
        if (near == null) return;
        feeder = near;
        seekTicks = 0;
    }

    public void addGrowth(float amount) {
        if (!species.hasBabies()) return;
        boolean wasBaby = isBabyForm();
        growth = Math.min(1f, growth + amount);
        if (wasBaby && !isBabyForm()) buildModel();
        updateStats();
        save();
    }

    public void setSaddled(boolean value) {
        saddled = value;
        if (!isBabyForm() && rig.hasSaddle()) model.setItem(rig.saddleBone(), value ? rig.saddleItem() : null);
        save();
    }

    public void save() {
        if (!wolf.isValid()) return;
        PersistentDataContainer pdc = wolf.getPersistentDataContainer();
        pdc.set(keys.growth, PersistentDataType.FLOAT, growth);
        pdc.set(keys.saddled, PersistentDataType.BYTE, (byte) (saddled ? 1 : 0));
        pdc.set(keys.hunger, PersistentDataType.FLOAT, hunger);
        pdc.set(keys.starving, PersistentDataType.BYTE, (byte) (starvingSit ? 1 : 0));
    }

    public Player rider() {
        for (Entity e : wolf.getPassengers()) {
            if (e instanceof Player p) return p;
        }
        return null;
    }

    public void mount(Player player) {
        wolf.setSitting(false);
        wolf.setTarget(null);
        rideYaw = wolf.getBodyYaw();
        wolf.addPassenger(player);
        roar = 0;
        play("roar", 0.5f, 1.15f);
    }

    // ------------------------------------------------------------ ordenes (silbato)
    public Order order() {
        return order;
    }

    public Combat combat() {
        return combat;
    }

    /** "chunk x, z" si esta patrullando. */
    public String patrolLabel() {
        return order == Order.PATROL ? "chunk " + patrolCx + ", " + patrolCz : null;
    }

    private void loadOrders(PersistentDataContainer pdc) {
        try {
            order = Order.valueOf(pdc.getOrDefault(keys.order, PersistentDataType.STRING, "FOLLOW"));
            combat = Combat.valueOf(pdc.getOrDefault(keys.combat, PersistentDataType.STRING, "DEFENSIVE"));
        } catch (IllegalArgumentException e) {
            order = Order.FOLLOW;
            combat = Combat.DEFENSIVE;
        }
        String world = pdc.get(keys.patrolWorld, PersistentDataType.STRING);
        if (world != null) {
            patrolWorld = UUID.fromString(world);
            patrolCx = pdc.getOrDefault(keys.patrolX, PersistentDataType.INTEGER, 0);
            patrolCz = pdc.getOrDefault(keys.patrolZ, PersistentDataType.INTEGER, 0);
        }
        if (order == Order.PATROL && patrolWorld == null) order = Order.FOLLOW;
        if (order == Order.PATROL && wolf.isTamed()) removeFollowGoal();
        String name = pdc.get(keys.name, PersistentDataType.STRING);
        if (name != null) {
            try {
                nameComp = GsonComponentSerializer.gson().deserialize(name);
            } catch (RuntimeException ignored) {
                nameComp = null;
            }
        }
    }

    private void saveOrders() {
        if (!wolf.isValid()) return;
        PersistentDataContainer pdc = wolf.getPersistentDataContainer();
        pdc.set(keys.order, PersistentDataType.STRING, order.name());
        pdc.set(keys.combat, PersistentDataType.STRING, combat.name());
        if (patrolWorld != null) {
            pdc.set(keys.patrolWorld, PersistentDataType.STRING, patrolWorld.toString());
            pdc.set(keys.patrolX, PersistentDataType.INTEGER, patrolCx);
            pdc.set(keys.patrolZ, PersistentDataType.INTEGER, patrolCz);
        }
    }

    /** Orden del silbato. Patrullar usa el chunk donde esta parado el que da la orden. */
    public void setOrder(Order o, Player issuer) {
        if (!wolf.isTamed()) return;
        if (o != Order.FOLLOW) stopEscort();
        else walkOnly = false;
        order = o;
        switch (o) {
            case FOLLOW -> {
                restoreFollowGoal();
                if (!starvingSit) wolf.setSitting(false);
            }
            case STAY -> {
                restoreFollowGoal();
                wolf.getPathfinder().stopPathfinding();
                wolf.setSitting(true);
            }
            case PATROL -> {
                Location at = issuer.getLocation();
                patrolWorld = at.getWorld().getUID();
                patrolCx = at.getBlockX() >> 4;
                patrolCz = at.getBlockZ() >> 4;
                removeFollowGoal();
                if (!starvingSit) wolf.setSitting(false);
                patrol(true);
            }
        }
        saveOrders();
    }

    public void setCombat(Combat c) {
        combat = c;
        if (c == Combat.PASSIVE) {
            wolf.setTarget(null);
            wolf.setAngry(false);
        }
        saveOrders();
    }

    private void tickOrders(Player rider, Location loc) {
        if (!wolf.isTamed() || rider != null || escort != null || flying || swimming) return;
        // si el dueño lo sento o lo paro con click derecho, la orden pasa a ser eso
        if (age % 10 == 0 && !starvingSit) {
            boolean sitting = wolf.isSitting();
            if (sitting && order != Order.STAY) {
                order = Order.STAY;
                restoreFollowGoal();
                saveOrders();
            } else if (!sitting && order == Order.STAY) {
                order = Order.FOLLOW;
                saveOrders();
            }
        }
        LivingEntity target = wolf.getTarget();
        if (target != null && (!target.isValid() || target.isDead())) target = null;
        if (combat == Combat.PASSIVE && target != null) {
            wolf.setTarget(null);
            wolf.setAngry(false);
            target = null;
        }
        if (combat == Combat.AGGRESSIVE && target == null && age % 20 == 0 && !wolf.isSitting() && !isStarving()) {
            boolean here = order == Order.PATROL && patrolWorld != null && patrolWorld.equals(loc.getWorld().getUID());
            Location center = here ? new Location(loc.getWorld(), (patrolCx << 4) + 8, loc.getY(), (patrolCz << 4) + 8) : loc;
            Monster best = null;
            double bestD = Double.MAX_VALUE;
            for (Monster m : center.getNearbyEntitiesByType(Monster.class, 12)) {
                if (m instanceof Creeper || m.isDead() || !wolf.hasLineOfSight(m)) continue;
                double d = m.getLocation().distanceSquared(loc);
                if (d < bestD) {
                    best = m;
                    bestD = d;
                }
            }
            if (best != null) wolf.setTarget(best);
        }
        if (order == Order.PATROL && age % 20 == 0 && target == null && feeder == null && eating < 0 && !wolf.isSitting()) {
            patrol(false);
        }
    }

    /** Camina a un punto al azar del chunk de patrulla (o vuelve si se fue). */
    private void patrol(boolean now) {
        World w = wolf.getWorld();
        if (patrolWorld == null || !w.getUID().equals(patrolWorld) || !w.isChunkLoaded(patrolCx, patrolCz)) return;
        Location at = wolf.getLocation();
        int minX = patrolCx << 4, minZ = patrolCz << 4;
        boolean inside = at.getX() >= minX && at.getX() < minX + 16 && at.getZ() >= minZ && at.getZ() < minZ + 16;
        var path = wolf.getPathfinder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (inside && !now && (path.hasPath() || r.nextInt(3) != 0)) return;
        for (int tries = 0; tries < 6; tries++) {
            Location spot = standable(w, minX + 2 + r.nextInt(12), minZ + 2 + r.nextInt(12), at.getBlockY());
            if (spot != null) {
                path.moveTo(spot, inside ? 0.8 : 1.1);
                return;
            }
        }
    }

    /** Un lugar donde se pueda parar (piso solido y dos bloques libres) cerca de la altura dada. */
    private static Location standable(World w, int x, int z, int nearY) {
        for (int dy = 0; dy <= 8; dy++) {
            for (int y : dy == 0 ? new int[]{nearY} : new int[]{nearY + dy, nearY - dy}) {
                if (y <= w.getMinHeight() || y >= w.getMaxHeight() - 2) continue;
                Block b = w.getBlockAt(x, y, z);
                if (b.isPassable() && !b.isLiquid() && b.getRelative(BlockFace.UP).isPassable()
                        && b.getRelative(BlockFace.DOWN).getType().isSolid()) {
                    return b.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void removeFollowGoal() {
        if (followGoal != null) return;
        MobGoals goals = Bukkit.getMobGoals();
        try {
            Goal g = ((MobGoals) goals).getGoal((Mob) wolf, (GoalKey) VanillaGoal.FOLLOW_OWNER);
            if (g == null) return;
            followGoal = g;
            ((MobGoals) goals).removeGoal((Mob) wolf, (GoalKey) VanillaGoal.FOLLOW_OWNER);
        } catch (RuntimeException ignored) {
            // sin la API de goals no se puede patrullar: se queda siguiendo al dueño
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void restoreFollowGoal() {
        if (followGoal == null) return;
        try {
            ((MobGoals) Bukkit.getMobGoals()).addGoal((Mob) wolf, 6, followGoal);   // 6 = prioridad del lobo
        } catch (RuntimeException ignored) {
            // vuelve a seguir al dueño la proxima vez que se cargue el chunk
        }
        followGoal = null;
    }

    // ------------------------------------------------------------ nombre y dueño
    public Component name() {
        return nameComp;
    }

    public void setName(Component name) {
        nameComp = name;
        if (wolf.isValid()) {
            wolf.getPersistentDataContainer().set(keys.name, PersistentDataType.STRING,
                    GsonComponentSerializer.gson().serialize(name));
            wolf.customName(name);   // asi sale en los mensajes de muerte
        }
        refreshPlate();
    }

    public String ownerName() {
        AnimalTamer o = wolf.getOwner();
        return o != null && o.getName() != null ? o.getName() : null;
    }

    /** Cartel arriba del modelo con el nombre y el dueño (solo si tiene nombre). */
    public void refreshPlate() {
        if (nameComp == null || dying >= 0 || !wolf.isValid()) {
            removePlate();
            return;
        }
        Component text = nameComp.colorIfAbsent(NamedTextColor.WHITE);
        String owner = wolf.isTamed() ? ownerName() : null;
        if (owner != null) {
            text = text.appendNewline().append(com.dinoearth.text.Lang.parse(
                    com.dinoearth.text.Lang.get().text("cartel-dueno", "player", owner)));
        }
        if (plate == null || !plate.isValid()) {
            plate = wolf.getWorld().spawn(wolf.getLocation(), TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setBillboard(Display.Billboard.CENTER);
                t.setShadowed(true);
                t.setBackgroundColor(Color.fromARGB(70, 0, 0, 0));
                t.setViewRange(0.5f);
                keys.tagPart(t);
            });
            wolf.addPassenger(plate);
        }
        plate.text(text);
        // los pasajeros del lobo van a ~0.96 de su altura: el cartel sube hasta arriba del modelo
        float top = form.height() * visualScale() / 16f + 0.35f;
        float mount = (float) (wolf.getHeight() * 0.963);
        plate.setTransformation(new Transformation(new Vector3f(0, top - mount, 0), new Quaternionf(),
                new Vector3f(1, 1, 1), new Quaternionf()));
    }

    /** Antes de teletransportarlo: un lobo con pasajeros no se puede mover, asi que se saca el modelo
     *  (se vuelve a armar solo en el proximo tick, ya en el destino). */
    public void prepareTeleport() {
        stopEscort();
        if (flying) land();
        model.remove();
        removePlate();
        wolf.eject();
        wolf.getPathfinder().stopPathfinding();
    }

    private void removePlate() {
        if (plate != null) plate.remove();
        plate = null;
    }

    // ------------------------------------------------------------ eventos
    public void onAttack() {
        attack = 0;
        play("attack", 0.8f, 1f);
    }

    public void onHurt(EntityDamageEvent event) {
        hurt = 7;
        play("hurt", 0.8f, 1f);
        if (event instanceof EntityDamageByEntityEvent by) {
            Entity src = by.getDamager();
            if (src instanceof Projectile p && p.getShooter() instanceof Entity shooter) src = shooter;
            lastAttacker = src.getLocation();
            // nadando no tiene la IA del lobo: contesta el ataque a mano (el domesticado pasivo no)
            if (swimming && src instanceof LivingEntity le && src != rider() && !(wolf.isTamed()
                    && (combat == Combat.PASSIVE || src.getUniqueId().equals(wolf.getOwnerUniqueId())))) {
                if (!(le instanceof Player p) || huntable(p)) wolf.setTarget(le);
            }
        } else {
            lastAttacker = null;
        }
        // presa salvaje: avisa a la manada y salen corriendo
        if (species.temper == Species.Temper.PREY && !wolf.isTamed()) {
            alarm(lastAttacker != null ? lastAttacker : wolf.getLocation().add(
                    ThreadLocalRandom.current().nextGaussian(), 0, ThreadLocalRandom.current().nextGaussian()));
        }
    }

    /**
     * Le pegaron: la armadura frena proyectiles; con cola de puas o maza contesta con un coletazo
     * al que le pego por atras (la maza tambien a los costados).
     */
    public void onStruck(EntityDamageByEntityEvent event) {
        Entity src = event.getDamager();
        if (escort != null && src == escort) {
            event.setCancelled(true);   // el dueño no le pega sin querer al que vuela a su lado
            return;
        }
        if (species.armored && src instanceof Projectile) event.setDamage(event.getDamage() * 0.35);
        if (species.tail == Species.TailAttack.NONE || tailCooldown > 0 || isBabyForm() || dying >= 0) return;
        Entity attacker = src instanceof Projectile p && p.getShooter() instanceof Entity shooter ? shooter : src;
        if (!(attacker instanceof LivingEntity le) || attacker == rider()) return;
        if (wolf.isTamed() && attacker.getUniqueId().equals(wolf.getOwnerUniqueId())) return;
        Location me = wolf.getLocation();
        if (le.getWorld() != me.getWorld()) return;
        Vector to = le.getLocation().toVector().subtract(me.toVector()).setY(0);
        double dist = to.length();
        if (dist > 2.5 + 3.5 * visualScale() || dist < 1e-3) return;
        double yaw = Math.toRadians(rider() != null ? rideYaw : wolf.getBodyYaw());
        Vector fwd = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        to.multiply(1 / dist);
        boolean club = species.tail == Species.TailAttack.CLUB;
        if (to.dot(fwd) > (club ? 0.3 : -0.2)) return;   // tiene que estar atras
        // de que lado gira la cola (signo del producto vectorial)
        tailSide = fwd.getX() * to.getZ() - fwd.getZ() * to.getX() > 0 ? 1 : -1;
        tailSwing = 0;
        tailCooldown = 30;
        le.damage(species.damage * (club ? 0.6 : 0.9), wolf);
        le.setVelocity(to.multiply(club ? 1.6 : 0.7).setY(club ? 0.55 : 0.3));
        play("attack", 1f, 1f);
    }

    // ------------------------------------------------------------ presas
    /** Salvaje: si un jugador se acerca sin agacharse, se asusta. Mientras huye no pelea. */
    private void tickPrey(Location loc) {
        if (fleeTicks > 0) {
            fleeTicks--;
            if (wolf.getTarget() != null) wolf.setTarget(null);
            if (wolf.isAngry()) wolf.setAngry(false);
            if (fleeTicks % 15 == 0) runFrom(loc);
            return;
        }
        if (age % 10 != 0 || wolf.isLeashed()) return;
        for (Player p : loc.getNearbyPlayers(10)) {
            if (!huntable(p)) continue;
            if (p.isSneaking() && p.getLocation().distanceSquared(loc) > 2.5 * 2.5) continue;   // agachado te acercas
            if (!wolf.hasLineOfSight(p)) continue;
            alarm(p.getLocation());
            return;
        }
    }

    /** Madre cuidando el nido (o al que le robaron un huevo): se le tira encima al jugador y ruge. */
    public void provoke(Player p) {
        if (wolf.isTamed() || dying >= 0 || !huntable(p)) return;
        if (wolf.isSitting()) wolf.setSitting(false);
        wolf.setTarget(p);
        wolf.setAngry(true);
        if (roar < 0) {
            roar = 0;
            play("roar", 1f, 1f);
        }
        if (species.roars && roarCooldown == 0) scare(null);
    }

    /** Se asusta, avisa (trompetazo/grito) y sale corriendo con toda la manada. */
    public void alarm(Location from) {
        if (fleeTicks <= 0) {
            roar = 0;
            play("roar", 1f, 1f);
        }
        for (Wolf w : wolf.getLocation().getNearbyEntitiesByType(Wolf.class, 14, 6, 14)) {
            Dino other = manager.get(w);
            if (other != null && other != this && other.species == species && !w.isTamed()) other.flee(from);
        }
        flee(from);
    }

    private void flee(Location from) {
        fleeFrom = from.clone();
        fleeTicks = 100;
        if (wolf.isSitting()) wolf.setSitting(false);
        runFrom(wolf.getLocation());
    }

    private void runFrom(Location loc) {
        if (fleeFrom == null || fleeFrom.getWorld() != loc.getWorld()) return;
        Vector away = loc.toVector().subtract(fleeFrom.toVector()).setY(0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (away.lengthSquared() < 1e-3) away = new Vector(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5);
        away.normalize().multiply(14).add(new Vector(r.nextDouble(-3, 3), 0, r.nextDouble(-3, 3)));
        wolf.getPathfinder().moveTo(loc.clone().add(away), 1.8);
    }

    // ------------------------------------------------------------ compañero volador (dimorphodon)
    public boolean isEscorting(Player p) {
        return escort != null && escort.getUniqueId().equals(p.getUniqueId());
    }

    /**
     * Vuela al lado del dueño. No es pasajero (eso trabaria los teleports): se mueve con su propia velocidad, apuntando
     * a donde VA a estar el jugador (su velocidad medida aca, la del server no sirve para jugadores), asi no queda atras.
     */
    public void escort(Player p) {
        escort = p;
        escortLast = p.getLocation();
        escortVel.zero();
        escortStuck = 0;
        walkOnly = false;
        wolf.setSitting(false);
        wolf.setTarget(null);
        wolf.setCollidable(false);
        if (!flying) {
            takeOff();
            flyVel.setY(0.3);
        }
        play("ambient", 0.6f, 1.2f);
    }

    /** Deja de acompañar: baja planeando y aterriza solo. */
    public void stopEscort() {
        if (escort == null) return;
        escort = null;
        wolf.setCollidable(true);
        wildFlight = 0;
    }

    /** Agachado + click derecho con la mano vacia: vuela con vos o camina (sin despegar solo). */
    public void toggleEscort(Player p) {
        if (isEscorting(p)) {
            stopEscort();
            walkOnly = true;
        } else {
            escort(p);
        }
    }

    private void tickEscort() {
        Player p = escort;
        if (!p.isOnline() || p.isDead() || isStarving() || wolf.isSitting() || order != Order.FOLLOW || hurt > 0
                && wolf.getTarget() != null) {
            stopEscort();
            return;
        }
        Location me = wolf.getLocation();
        Location at = p.getLocation();
        if (p.getWorld() != me.getWorld() || at.distanceSquared(me) > 40 * 40 || escortStuck > 60) {
            // se quedo muy atras (teleport, elytra, portal, trabado): aparece al lado del dueño
            Location spot = at.clone().add(0, Math.min(2.5, p.getHeight() + 0.6), 0);
            prepareTeleport();
            wolf.teleportAsync(spot).thenAccept(ok -> {
                if (ok && wolf.isValid() && p.isOnline()) escort(p);
            });
            return;
        }
        // velocidad del jugador, suavizada (bloques por tick)
        Vector step = at.toVector().subtract(escortLast.toVector());
        escortLast = at;
        if (step.lengthSquared() > 9) step.zero();          // teleport corto
        escortVel.multiply(0.6).add(step.multiply(0.4));
        double moving = Math.min(1, Math.hypot(escortVel.getX(), escortVel.getZ()) / 0.15);

        // quieto: da vueltas despacio alrededor; caminando: atras a la derecha
        double yaw = Math.toRadians(p.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);      // adelante
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);     // derecha
        escortAngle += 0.04 * (1 - moving);
        double ox = Math.cos(escortAngle) * 2.2 * (1 - moving) + (rx * 1.5 - fx * 0.9) * moving;
        double oz = Math.sin(escortAngle) * 2.2 * (1 - moving) + (rz * 1.5 - fz * 0.9) * moving;
        double oy = p.getHeight() + 0.4 + Math.sin(age * 0.12) * 0.25;
        // apunta a donde va a estar en 5 ticks
        double tx = at.getX() + ox + escortVel.getX() * 5;
        double ty = at.getY() + oy + escortVel.getY() * 5;
        double tz = at.getZ() + oz + escortVel.getZ() * 5;
        for (int i = 0; i < 3 && !me.getWorld().getBlockAt((int) Math.floor(tx), (int) Math.floor(ty),
                (int) Math.floor(tz)).isPassable(); i++) {
            ty += 1;     // el punto quedo adentro de un bloque: un poco mas arriba
        }
        Vector want = escortVel.clone().add(new Vector(tx - me.getX(), ty - me.getY(), tz - me.getZ()).multiply(0.25));
        if (want.lengthSquared() > 1.8 * 1.8) want.normalize().multiply(1.8);
        steer(want.getX(), want.getY(), want.getZ(), 0.5);
        wolf.setFallDistance(0);
        flapTarget(want.getY() > 0.05 || moving > 0.5 ? 1f : 0.45f);

        Vector v = flyVel;
        float heading = Math.hypot(v.getX(), v.getZ()) > 0.06 ? (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()))
                : p.getYaw();
        rideYaw += clamp(wrap(heading - rideYaw), -14, 14);
        wolf.setRotation(rideYaw, 0);
        wolf.setBodyYaw(rideYaw);

        double far = me.distanceSquared(new Location(me.getWorld(), tx, ty, tz));
        if (far > 36 && Math.hypot(wolf.getVelocity().getX(), wolf.getVelocity().getZ()) < 0.05) escortStuck++;
        else if (escortStuck > 0) escortStuck--;

        // avisa si hay monstruos cerca
        if (alertCooldown > 0) alertCooldown--;
        if (age % 40 == 0 && alertCooldown == 0
                && !at.getNearbyEntitiesByType(Monster.class, 12).isEmpty()) {
            alertCooldown = 400;
            roar = 0;
            play("roar", 0.7f, 1.2f);
            com.dinoearth.text.Lang.get().action(p, "monstruos", "especie", species.name);
        }
    }

    /** Siguiendo al dueño y este se aleja: despega y lo acompaña volando (salvo que lo hayan puesto a caminar). */
    private void maybeEscort(Player rider) {
        if (!species.perches || escort != null || walkOnly || age % 20 != 0 || rider != null || order != Order.FOLLOW
                || !wolf.isTamed() || wolf.isSitting() || isStarving() || isBabyForm() || wolf.getTarget() != null) {
            return;
        }
        if (!(wolf.getOwner() instanceof Player owner) || !owner.isOnline() || owner.getWorld() != wolf.getWorld()) return;
        if (owner.getLocation().distanceSquared(wolf.getLocation()) > 10 * 10) escort(owner);
    }

    public void onDeath() {
        if (flying) wolf.setGravity(true);
        dying = 0;
        removePlate();
        // sigue montado en el lobo muerto (se mueve igual que el) y conserva hacia donde miraba
        deathYaw = drawnYaw;
        fallSide = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        // mitad del ancho, en bloques del modelo sin escalar: lo que hay que levantarlo para que caiga
        // apoyado sobre el costado y no hundido en el piso
        fallLift = (float) (wolf.getWidth() / 2 / Math.max(0.05f, visualScale()));
        play(isBabyForm() ? "hurt" : "death", 0.9f, isBabyForm() ? 0.8f : 1f);
    }

    public void onTamed() {
        roar = 0;
        play("roar", 0.8f, 1.1f);
    }

    public void remove() {
        // que no quede flotando si se descarga el chunk en pleno vuelo (o yendo en un hombro)
        if ((flying || swimming || escort != null) && wolf.isValid()) {
            wolf.setGravity(true);
            wolf.setCollidable(true);
        }
        save();
        model.remove();
        removePlate();
    }

    // ------------------------------------------------------------ tick
    /** @return false cuando el dino ya no existe y hay que soltarlo. */
    public boolean tick() {
        if (dying >= 0) return tickDeath();
        if (!wolf.isValid()) return false;
        if (!model.isValid()) buildModel();   // alguien borro las displays (p. ej. /kill)

        age++;
        if (age % 200 == 0 && wolf.isTamed() && manager.registry() != null) manager.registry().update(this);
        if (age % 20 == 0) {
            model.remount();
            if (plate != null && plate.isValid() && plate.getVehicle() != wolf) wolf.addPassenger(plate);
        }

        if (growth < 1f && !isStarving()) {   // muerto de hambre no crece
            boolean wasBaby = isBabyForm();
            growth = Math.min(1f, growth + 1f / settings.growthTicks);
            if (wasBaby && !isBabyForm()) buildModel();
            if (age % 20 == 0) updateStats();
            if (age % 200 == 0) {
                save();
                updateViewRange();
                refreshPlate();
            }
        }

        if (roarCooldown > 0) roarCooldown--;
        Player rider = rider();
        if (rider == null && lastRider != null && lastRider.isOnline() && flying) {
            // se bajo en el aire: que no se mate, y el pteranodon baja solo
            lastRider.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false, true));
            wildFlight = 0;
        }
        lastRider = rider;
        tickHunger(rider);
        if (rider != null && rider() == null) rider = null;   // lo bajo el hambre
        if (tailSwing >= 0 && ++tailSwing > TAIL_TICKS) tailSwing = -1;
        if (tailCooldown > 0) tailCooldown--;
        maybeEscort(rider);
        if (species.swims) updateSwim(rider);
        if (biteCooldown > 0) biteCooldown--;
        if (escort != null) tickEscort();
        else if (rider != null) ride(rider);
        else if (flying) flyAlone();
        else if (swimming) swimAlone();
        else {
            if (!wolf.isAware()) wolf.setAware(true);
            if (species.flies()) maybeTakeOff();
            if (species.aquatic()) beached();
        }

        Location loc = wolf.getLocation();
        float bodyYaw = rider != null || flying || swimming || escort != null ? rideYaw : wolf.getBodyYaw();

        // velocidad horizontal real -> ciclo de pasos
        Vector delta = loc.toVector().subtract(last.toVector()).setY(0);
        float moved = last.getWorld() == loc.getWorld() ? (float) delta.length() : 0f;
        if (moved > 2) moved = 0; // teleport
        last = loc;
        if (rider != null && moved > 0) {
            rideDist += moved;
            if (age % 20 == 0) {
                var missions = com.dinoearth.mission.MissionManager.get();
                if (missions != null) missions.onRide(rider, rideDist);
                rideDist = 0;
            }
        }
        float size = Math.max(0.2f, visualScale() * (isBabyForm() ? rig.baby().height() / rig.adult().height() : 1f));
        float target = Math.min(1f, moved / (0.3f * Math.max(0.5f, size)));
        speed += (target - speed) * 0.25f;
        float prevPhase = phase;
        phase += moved * species.stride / size;
        if (Math.floor(prevPhase / Math.PI) != Math.floor(phase / Math.PI) && speed > 0.15f && !isBabyForm() && !flying
                && !swimming) {
            play("step", species.body == Species.Body.QUADRUPED || species.roars ? 0.6f : 0.35f, 1f);
        }
        if (species.flies()) tickWings();

        sit += ((wolf.isSitting() ? 1f : 0f) - sit) * 0.15f;
        behave(rider, loc);
        if (age % 40 == 0) discover(loc);

        if (hurt > 0) hurt--;
        if (attack >= 0 && ++attack > ATTACK_TICKS) attack = -1;
        if (roar >= 0 && ++roar > ROAR_TICKS) roar = -1;
        if (roar < 0 && attack < 0 && spit < 0 && ThreadLocalRandom.current().nextInt(isBabyForm() ? 260 : 400) == 0) {
            roar = 0;
            play("ambient", 0.6f, 1f);
        }

        drawnYaw = bodyYaw;
        // la pose solo se calcula y se manda cuando alguien la puede ver, menos seguido de lejos y mucho menos
        // si esta quieto (parado o sentado sin hacer nada): ahi solo respira y mueve la cabeza
        boolean still = isStill(rider);
        if (rider != null || escort != null) {
            poseEvery = 1;
        } else if (still != wasStill) {
            poseEvery = poseRate(loc, still);
            if (!still) lastPose = age - poseEvery;     // arranco a moverse: pose ya, sin esperar
        } else if (age % 10 == 0 || poseEvery == 1) {
            poseEvery = poseRate(loc, still);
        }
        wasStill = still;
        quiet = still;
        if (poseEvery > 0 && age - lastPose >= poseEvery) {
            lastPose = age;
            animate();
            boolean free = rider == null && !flying && !swimming && escort == null;   // mira para donde mira el lobo
            float headYaw = free ? wrap(loc.getYaw() - bodyYaw) : 0;
            model.pose(head).ry += -clamp(headYaw, -50, 50) * 0.8f;
            model.pose(head).rx += clamp(free ? loc.getPitch() : 0, -35, 35) * 0.7f;
            model.applyPose(loc, bodyYaw, visualScale(), poseEvery + 1);
        }
        return true;
    }

    /**
     * Cada cuantos ticks mandar la pose segun el jugador mas cercano que lo ve: cerca fluido, lejos menos seguido,
     * y nunca si nadie lo tiene cargado (0). Con distancia de vista 8 nadie lo ve a mas de 128 bloques.
     * Quieto: el cliente interpola entre poses, asi que respirar y girar la cabeza se ven suaves igual.
     */
    private int poseRate(Location loc, boolean still) {
        double best = Double.MAX_VALUE;
        for (Player p : model.viewers()) {
            if (p.getWorld() == loc.getWorld()) best = Math.min(best, p.getLocation().distanceSquared(loc));
        }
        if (best == Double.MAX_VALUE) return 0;
        if (best < 16 * 16) return still ? 6 : 2;
        if (best < 32 * 32) return still ? 8 : 3;
        if (best < 64 * 64) return still ? 12 : 5;
        return still ? 20 : 8;
    }

    /** Sin jinete, sin caminar y sin ninguna animacion en curso (ataque, rugido, comer, sentarse...). */
    private boolean isStill(Player rider) {
        if (rider != null || escort != null || flying || swimming || isDying()) return false;
        if (speed > 0.04f || attack >= 0 || roar >= 0 || spit >= 0 || eating >= 0 || hurt > 0 || tailSwing >= 0) {
            return false;
        }
        float sitTarget = wolf.isSitting() ? 1f : 0f;
        return Math.abs(sit - sitTarget) < 0.03f;
    }

    /** Para /dino stats: si en el ultimo tick estaba quieto (pose espaciada). */
    public boolean isQuiet() {
        return quiet;
    }

    public int modelParts() {
        return model.size() + (plate != null && plate.isValid() ? 1 : 0);
    }

    public boolean isSeen() {
        return poseEvery > 0;
    }

    /** Distancia de dibujado en el cliente: los chicos desaparecen antes que los grandes. */
    private void updateViewRange() {
        float size = isBabyForm() ? rig.baby().height() / rig.adult().height() * modelScale() : modelScale();
        model.setViewRange(Math.max(0.4f, (float) species.viewRange * Math.min(1f, size)));
    }

    // ------------------------------------------------------------ comportamiento
    private void behave(Player rider, Location loc) {
        LivingEntity target = wolf.getTarget();
        if (target != null && (!target.isValid() || target.isDead())) target = null;

        tickOrders(rider, loc);
        if (species.temper == Species.Temper.PREY && !wolf.isTamed() && rider == null && !flying) tickPrey(loc);
        boolean hunts = species.temper == Species.Temper.HOSTILE || species.territorial && !wolf.isTamed();
        if (species.aquatic() && !swimming) hunts = false;   // varado no caza
        if (hunts && age % 10 == 0 && rider == null && !flying) hunt(loc);
        if (species.temper == Species.Temper.PACK && age % 20 == 0 && rider == null) packHunt(loc);
        if (species.spits) tickSpit(target);
        if (species.charges) tickCharge(rider, target);
        if (frillR >= 0) {
            float open = target != null || spit >= 0 || roar >= 0 ? 1f : 0f;
            frill += (open - frill) * 0.15f;
        }
    }

    private static boolean huntable(Player p) {
        return p.isValid() && !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    /** Hostil: persigue al jugador mas cercano que vea. */
    private void hunt(Location loc) {
        if (wolf.getTarget() instanceof Player p && huntable(p) && p.getWorld() == loc.getWorld()
                && p.getLocation().distanceSquared(loc) < 24 * 24) {
            wolf.setAngry(true);
            return;
        }
        Player best = null;
        double bestD = 16 * 16;
        for (Player p : loc.getWorld().getPlayers()) {
            if (!huntable(p)) continue;
            if (species.aquatic() && (!p.isInWater() || p.getVehicle() != null)) continue;   // solo a los que nadan
            double d = p.getLocation().distanceSquared(loc);
            if (d < bestD && wolf.hasLineOfSight(p)) {
                best = p;
                bestD = d;
            }
        }
        if (best != null) {
            wolf.setTarget(best);
            wolf.setAngry(true);
            roar = 0;
            play("roar", 0.9f, 1f);
            if (species.roars && roarCooldown == 0) scare(null);
        }
    }

    /**
     * Rugido que asusta (T-Rex): lentitud y oscuridad a los jugadores cerca, lentitud a los mobs.
     * El dueño, el jinete y las mascotas del dueño no se asustan.
     */
    private void scare(Player rider) {
        roarCooldown = ROAR_COOLDOWN;
        UUID owner = wolf.getOwnerUniqueId();
        for (LivingEntity e : wolf.getLocation().getNearbyLivingEntities(12)) {
            if (e == wolf || e == rider || e.isDead()) continue;
            if (e instanceof Player p) {
                if (!huntable(p) || p.getUniqueId().equals(owner)) continue;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0));
            } else if (e instanceof Mob) {
                if (owner != null && e instanceof Tameable t && owner.equals(t.getOwnerUniqueId())) continue;
                e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            }
        }
    }

    /** Manada: atacan a un jugador herido, o a cualquiera si son 4 o mas. */
    private void packHunt(Location loc) {
        if (wolf.getTarget() instanceof Player p && huntable(p)) {
            wolf.setAngry(true);
            return;
        }
        var pack = loc.getNearbyEntitiesByType(Wolf.class, 10, 4, 10).stream()
                .filter(w -> species.id.equals(w.getPersistentDataContainer().get(keys.type, PersistentDataType.STRING)))
                .toList();
        for (Player p : loc.getNearbyPlayers(12)) {
            if (!huntable(p)) continue;
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            boolean weak = max != null && p.getHealth() <= max.getValue() * 0.6;
            if (!weak && pack.size() < 4) continue;
            for (Wolf member : pack) {
                if (member.getTarget() == null) {
                    member.setTarget(p);
                    member.setAngry(true);
                }
            }
            roar = 0;
            play("roar", 0.7f, 1f);
            return;
        }
    }

    /** Dilophosaurus: abre la gola, echa la cabeza atras y escupe veneno. */
    private void tickSpit(LivingEntity target) {
        if (spitCooldown > 0) spitCooldown--;
        if (spit >= 0) {
            spit++;
            if (spit == 9 && target != null) launchSpit(target);
            if (spit > SPIT_TICKS) spit = -1;
            return;
        }
        if (target == null || spitCooldown > 0 || wolf.isSitting()) return;
        double d = target.getLocation().distance(wolf.getLocation());
        if (d > 3.5 && d < 13 && wolf.hasLineOfSight(target)) {
            spit = 0;
            spitCooldown = 70 + ThreadLocalRandom.current().nextInt(50);
            play("roar", 0.7f, 1.1f);
        }
    }

    private void launchSpit(LivingEntity target) {
        Location from = wolf.getEyeLocation();
        Vector to = target.getEyeLocation().toVector().subtract(from.toVector());
        double dist = to.length();
        Vector dir = to.normalize();
        dir.setY(dir.getY() + dist * 0.012);   // compensa la caida
        from.add(dir.clone().multiply(0.7));
        LlamaSpit s = wolf.getWorld().spawn(from, LlamaSpit.class, e -> {
            e.setShooter(wolf);
            e.getPersistentDataContainer().set(keys.venom, PersistentDataType.BYTE, (byte) 1);
        });
        s.setVelocity(dir.multiply(1.25));
        play("spit", 1f, 1f);
    }

    /** Triceratops: baja la cabeza cuando embiste; montado y corriendo arrolla lo que tenga adelante. */
    private void tickCharge(Player rider, LivingEntity target) {
        boolean charging = rider != null
                ? rider.getCurrentInput().isSprint() && rider.getCurrentInput().isForward() && speed > 0.35f
                : target != null && speed > 0.3f;
        charge += ((charging ? 1f : 0f) - charge) * 0.2f;
        if (rider != null && charging && age % 2 == 0 && !racing) ram(rider);
        if (age % 100 == 0) rammed.values().removeIf(t -> age - t > 40);
    }

    private void ram(Player rider) {
        double rad = Math.toRadians(rideYaw);
        Vector fwd = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        double reach = 1.7 * visualScale();
        Location center = wolf.getLocation().add(fwd.clone().multiply(reach)).add(0, 0.8, 0);
        for (Entity e : wolf.getWorld().getNearbyEntities(center, 1.4, 1.2, 1.4)) {
            if (!(e instanceof LivingEntity le) || e == wolf || e == rider || le.isDead() || !le.isValid()) continue;
            if (e instanceof Player p && !huntable(p)) continue;
            if (e instanceof Tameable t && rider.getUniqueId().equals(t.getOwnerUniqueId())) continue;
            Integer lastHit = rammed.get(e.getUniqueId());
            if (lastHit != null && age - lastHit < 20) continue;
            rammed.put(e.getUniqueId(), age);
            le.damage(species.damage, rider);
            le.setVelocity(fwd.clone().multiply(1.2).setY(0.45));
            onAttack();
        }
    }

    /** Control del jugador montado: la IA se apaga y movemos al lobo segun las teclas. */
    private void ride(Player rider) {
        if (wolf.isAware()) wolf.setAware(false);
        if (wolf.isSitting()) wolf.setSitting(false);
        Input in = rider.getCurrentInput();

        float turn = flying || swimming ? 9 : 16;
        rideYaw += clamp(wrap(rider.getYaw() - rideYaw), -turn, turn);
        wolf.setRotation(rideYaw, 0);
        wolf.setBodyYaw(rideYaw);
        if (flying) {
            rideFlight(rider, in);
            return;
        }
        if (swimming) {
            rideSwim(rider, in);
            return;
        }

        if (frozen) {
            // largada: quieto hasta que larguen (puede mirar para los costados)
            Vector still = wolf.getVelocity();
            wolf.setVelocity(still.setX(0).setZ(0));
            return;
        }
        double f = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
        double s = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        boolean sprint = f > 0 && in.isSprint();
        if (racing) {
            if (sprint && !exhausted) {
                stamina = (float) Math.max(0, stamina - staminaDrain);
                if (stamina <= 0) exhausted = true;
            } else {
                stamina = (float) Math.min(1, stamina + staminaRegen);
                if (exhausted && stamina >= 0.3f) exhausted = false;
            }
            if (exhausted) sprint = false;
        }
        double max = (sprint ? species.rideSprint : species.rideWalk) * g().speed;
        if (f < 0) max *= 0.45;
        double rad = Math.toRadians(rideYaw);
        double vx = -Math.sin(rad) * f + Math.cos(rad) * s * 0.6;
        double vz = Math.cos(rad) * f + Math.sin(rad) * s * 0.6;
        double len = Math.hypot(vx, vz);
        if (len > 1e-4) {
            vx = vx / Math.max(1, len) * max;
            vz = vz / Math.max(1, len) * max;
        }

        Vector v = wolf.getVelocity();
        boolean ground = wolf.isOnGround();
        double k = ground ? 1.0 : 0.3;  // en el aire se controla menos
        v.setX(v.getX() + (vx - v.getX()) * k);
        v.setZ(v.getZ() + (vz - v.getZ()) * k);
        if (jumpCooldown > 0) jumpCooldown--;
        if (species.roars) {
            // el T-Rex no salta: la barra espaciadora es para rugir (en carrera no)
            if (in.isJump() && roarCooldown == 0 && roar < 0 && !racing) {
                roar = 0;
                play("roar", 1f, 1f);
                scare(rider);
            }
        } else if (in.isJump() && ground && jumpCooldown == 0) {
            if (species.flies()) {
                takeOff();
                return;
            }
            v.setY(species.rideJump);
            jumpCooldown = 8;
        }
        wolf.setVelocity(v);
    }

    // ------------------------------------------------------------ vuelo
    private void takeOff() {
        flying = true;
        flyTicks = 0;
        stuck = 0;
        airSpeed = 0.25;
        flap = 1;
        rideYaw = wolf.getBodyYaw();
        wolf.setSitting(false);
        wolf.setAware(false);
        wolf.setGravity(false);
        flyVel.copy(wolf.getVelocity()).setY(species.rideJump);   // impulso del despegue
        wolf.setVelocity(flyVel);
        play("flap", 0.9f, 1f);
    }

    private void land() {
        flying = false;
        wildFlight = 0;
        wolf.setGravity(true);
        wolf.setFallDistance(0);
        wolf.setRotation(rideYaw, 0);
        wolf.setBodyYaw(rideYaw);
        if (rider() == null) wolf.setAware(true);
    }

    /**
     * Vuelo montado: W vuela hacia donde mirás (mirar abajo = picada, arriba = trepar),
     * Ctrl va mas rapido, espacio aletea para subir, sin W planea bajando despacio.
     * Al tocar el suelo aterriza. Shift baja al jinete (con caida lenta).
     */
    private void rideFlight(Player rider, Input in) {
        flyTicks++;
        wolf.setFallDistance(0);
        rider.setFallDistance(0);
        double pitch = Math.toRadians(clamp(rider.getPitch(), -55, 65));
        double target = in.isForward() ? (in.isSprint() ? species.flySprint : species.flySpeed) * g().speed
                : in.isBackward() ? 0.08 : 0.28;
        airSpeed += (target - airSpeed) * (target > airSpeed ? 0.05 : 0.08);
        double yaw = Math.toRadians(rideYaw);
        double horizontal, vy;
        if (in.isForward()) {
            horizontal = Math.cos(pitch) * airSpeed;
            vy = -Math.sin(pitch) * airSpeed;
        } else {
            horizontal = airSpeed;
            vy = -0.07;   // planea
        }
        if (in.isJump()) vy = Math.max(vy, 0) + 0.32;
        flapTarget(in.isJump() || in.isForward() && (in.isSprint() || pitch < -0.2) ? 1f : in.isForward() ? 0.35f : 0.1f);
        steer(-Math.sin(yaw) * horizontal, vy, Math.cos(yaw) * horizontal, 0.2);
        if (flyTicks > 10 && vy <= 0 && (wolf.isOnGround() || wolf.isInWater())) land();
    }

    /** Lleva la velocidad de vuelo hacia (x, y, z) suavemente y se la aplica al lobo. */
    private void steer(double x, double y, double z, double k) {
        Vector v = flyVel;
        v.setX(v.getX() + (x - v.getX()) * k);
        v.setY(v.getY() + (y - v.getY()) * k);
        v.setZ(v.getZ() + (z - v.getZ()) * k);
        wolf.setVelocity(v);
        double horizontal = Math.hypot(v.getX(), v.getZ());
        float pitch = (float) Math.toDegrees(Math.atan2(-v.getY(), Math.max(0.05, horizontal)));
        flightPitch += (clamp(pitch, -30, 35) - flightPitch) * 0.2f;
    }

    private void flapTarget(float target) {
        flap += (target - flap) * 0.1f;
    }

    /** Salvaje (o suelto) a veces sale a dar vueltas; el salvaje herido se escapa volando. */
    private void maybeTakeOff() {
        if (isBabyForm() || wolf.isSitting() || wolf.isLeashed() || wolf.isInWater() || !wolf.isOnGround()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean scared = !wolf.isTamed() && hurt > 0;
        if (!scared && (wolf.getTarget() != null || r.nextInt(wolf.isTamed() ? 3600 : 1400) != 0)) return;
        Location at = wolf.getLocation();
        flightCenter = at.clone();
        if (scared && lastAttacker != null && lastAttacker.getWorld() == at.getWorld()) {
            // el centro de las vueltas queda lejos del que lo ataco
            Vector away = at.toVector().subtract(lastAttacker.toVector()).setY(0);
            if (away.lengthSquared() > 1e-4) flightCenter.add(away.normalize().multiply(24));
        }
        flightAngle = r.nextDouble(Math.PI * 2);
        flightRadius = 8 + r.nextDouble(8);
        flightAltitude = 10 + r.nextDouble(8);
        wildFlight = 300 + r.nextInt(400);
        wolf.setTarget(null);
        takeOff();
    }

    /** Vuelo sin jinete: da vueltas alrededor de un punto a cierta altura y despues baja a aterrizar. */
    private void flyAlone() {
        flyTicks++;
        wolf.setFallDistance(0);
        if (wolf.isAware()) wolf.setAware(false);
        if (wildFlight > 0) wildFlight--;
        if (wolf.isTamed() && hurt > 0) wildFlight = 0;   // domesticado: baja a pelear
        Location at = wolf.getLocation();
        double tx, ty, tz;
        if (wildFlight > 0 && flightCenter != null && flightCenter.getWorld() == at.getWorld()) {
            flightAngle += 0.42 / flightRadius;
            double cx = flightCenter.getX() + Math.cos(flightAngle) * flightRadius;
            double cz = flightCenter.getZ() + Math.sin(flightAngle) * flightRadius;
            if (!at.getWorld().isChunkLoaded((int) Math.floor(cx) >> 4, (int) Math.floor(cz) >> 4)) {
                wildFlight = 0;
                return;
            }
            double groundY = at.getWorld().getHighestBlockYAt(at.getBlockX(), at.getBlockZ());
            double wantY = Math.max(groundY, flightCenter.getY()) + flightAltitude;
            Vector to = new Vector(cx - at.getX(), 0, cz - at.getZ());
            if (to.lengthSquared() > 1e-4) to.normalize().multiply(0.42);
            tx = to.getX();
            tz = to.getZ();
            ty = clamp((float) ((wantY - at.getY()) * 0.08), -0.25f, 0.3f);
            // trabado contra algo: sube
            if (Math.hypot(wolf.getVelocity().getX(), wolf.getVelocity().getZ()) < 0.08 && ++stuck > 20) ty = 0.3;
            else if (stuck > 0) stuck--;
            if (stuck > 100) wildFlight = 0;
            flapTarget(ty > 0.05 ? 1f : 0.2f);
        } else {
            // aterrizando: sigue para adelante cada vez mas despacio y baja
            double yaw = Math.toRadians(rideYaw);
            airSpeed += (0.18 - airSpeed) * 0.05;
            tx = -Math.sin(yaw) * airSpeed;
            tz = Math.cos(yaw) * airSpeed;
            ty = -0.22;
            flapTarget(0.15f);
        }
        steer(tx, ty, tz, 0.12);
        Vector v = flyVel;
        if (Math.abs(v.getX()) + Math.abs(v.getZ()) > 0.02) {
            float heading = (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
            rideYaw += clamp(wrap(heading - rideYaw), -8, 8);
            wolf.setRotation(rideYaw, 0);
            wolf.setBodyYaw(rideYaw);
        }
        if (flyTicks > 10 && wildFlight == 0 && (wolf.isOnGround() || wolf.isInWater())) land();
    }

    /** Alas: se pliegan en el suelo, aletean en el aire (y suenan). */
    private void tickWings() {
        fold += ((flying ? 0f : 1f) - fold) * 0.12f;
        if (!flying) {
            flap += (0 - flap) * 0.2f;
            flightPitch += (0 - flightPitch) * 0.2f;
        }
        // se inclina para el lado en que dobla
        float turn = flying ? wrap(rideYaw - lastYaw) : 0;
        lastYaw = rideYaw;
        flightRoll += (clamp(turn * 4, -35, 35) - flightRoll) * 0.15f;
        float prev = flapPhase;
        flapPhase += 0.2f + 0.35f * flap;
        if (flying && flap > 0.4f && Math.floor(prev / (2 * Math.PI)) != Math.floor(flapPhase / (2 * Math.PI))) {
            play("flap", 0.4f + 0.4f * flap, 1f);
        }
    }

    // ------------------------------------------------------------ nado (spinosaurus, mosasaurus)
    public boolean isSwimming() {
        return swimming;
    }

    /** Entra o sale del agua. El anfibio necesita agua hasta la mitad del cuerpo; el acuatico, apenas mojarse. */
    private void updateSwim(Player rider) {
        boolean wet = wolf.isInWater();
        if (wet) wolf.setRemainingAir(wolf.getMaximumAir());   // nunca se ahoga
        if (!swimming) {
            flightPitch *= 0.8f;
            if (wet && deepEnough() && !wolf.isLeashed() && escort == null) startSwim();
        } else if (!wet) {
            if (++dryTicks > 6) stopSwim();
        } else {
            dryTicks = 0;
        }
    }

    private boolean deepEnough() {
        double k = species.aquatic() ? 0.15 : 0.5;
        return wolf.getLocation().add(0, wolf.getHeight() * k, 0).getBlock().isLiquid();
    }

    private void startSwim() {
        swimming = true;
        dryTicks = 0;
        swimTarget = null;
        swimStuck = 0;
        wolf.setAware(false);
        wolf.setGravity(false);
        flyVel.copy(wolf.getVelocity());
        rideYaw = wolf.getBodyYaw();
        play("swim", 0.8f, 1f);
    }

    private void stopSwim() {
        swimming = false;
        wolf.setGravity(true);
        wolf.setFallDistance(0);
        wolf.setRotation(rideYaw, 0);
        wolf.setBodyYaw(rideYaw);
        if (rider() == null) wolf.setAware(true);
    }

    /** Sin jinete en el agua: persigue a su presa, sigue al dueño o da vueltas por el agua. */
    private void swimAlone() {
        if (wolf.isAware()) wolf.setAware(false);
        Location at = wolf.getLocation();
        Location body = at.clone().add(0, wolf.getHeight() * 0.5, 0);
        LivingEntity target = wolf.getTarget();
        if (target != null && (!target.isValid() || target.isDead() || target.getWorld() != at.getWorld()
                || target.getLocation().distanceSquared(at) > 32 * 32 || target instanceof Player p && !huntable(p))) {
            wolf.setTarget(null);
            target = null;
        }
        if (target != null && wolf.isTamed() && combat == Combat.PASSIVE) target = null;
        double speed = 0;
        Vector to = new Vector();
        boolean wander = false;
        if (target != null) {
            Location aim = target.getLocation().add(0, target.getHeight() * 0.5, 0);
            to = aim.toVector().subtract(body.toVector());
            speed = species.swimSprint * 0.75 * g().speed;
            double reach = wolf.getWidth() * 0.5 + 1.2 + 1.2 * visualScale();
            if (to.length() < reach && biteCooldown == 0) bite(target);
        } else if (wolf.isTamed() && (order == Order.STAY || wolf.isSitting())) {
            speed = 0;
        } else if (wolf.isTamed() && order == Order.FOLLOW && wolf.getOwner() instanceof Player owner && owner.isOnline()
                && owner.getWorld() == at.getWorld() && owner.getLocation().distanceSquared(at) < 48 * 48) {
            to = owner.getLocation().add(0, 0.5, 0).toVector().subtract(body.toVector());
            double d = to.length();
            speed = d < 4 ? 0 : Math.min(species.swimSprint * g().speed, 0.08 + d * 0.03);
        } else {
            wander = true;
            if (swimTarget == null || swimTarget.getWorld() != at.getWorld() || --swimRetarget <= 0
                    || swimTarget.distanceSquared(body) < 2.5 * 2.5) {
                swimTarget = waterSpot(at, 10);
                swimRetarget = 100 + ThreadLocalRandom.current().nextInt(120);
            }
            if (swimTarget != null) {
                to = swimTarget.toVector().subtract(body.toVector());
                speed = species.swimSpeed * 0.45 * g().speed;
            }
        }
        double len = to.length();
        Vector v = len > 1e-3 ? to.multiply(speed / len) : new Vector();
        // el acuatico no se asoma solo: si arriba hay aire, baja (salvo que persiga a alguien)
        if (species.aquatic() && target == null) {
            boolean airAbove = !at.clone().add(0, wolf.getHeight() + 0.3, 0).getBlock().isLiquid();
            if (airAbove) v.setY(Math.min(v.getY(), -0.03));
            Vector flat = v.clone().setY(0);
            if (flat.lengthSquared() > 1e-4) {
                Location ahead = body.clone().add(flat.normalize().multiply(1.5 + wolf.getWidth() / 2));
                if (!ahead.getBlock().isLiquid()) {
                    swimTarget = null;   // adelante no hay agua: busca otro lado
                    v.multiply(0.2);
                }
            }
        } else if (!species.aquatic() && wander) {
            v.setY(v.getY() + 0.02);   // el anfibio tiende a flotar
        }
        // trabado (contra la orilla): el anfibio sube para salir, el acuatico busca otro lado
        if (speed > 0.05 && Math.hypot(wolf.getVelocity().getX(), wolf.getVelocity().getZ()) < 0.02) {
            if (++swimStuck > 20) {
                if (species.aquatic()) swimTarget = null;
                else v.setY(0.3);
            }
        } else if (swimStuck > 0) {
            swimStuck--;
        }
        steer(v.getX(), v.getY(), v.getZ(), 0.12);
        Vector fv = flyVel;
        if (Math.abs(fv.getX()) + Math.abs(fv.getZ()) > 0.02) {
            float heading = (float) Math.toDegrees(Math.atan2(-fv.getX(), fv.getZ()));
            rideYaw += clamp(wrap(heading - rideYaw), -7, 7);
        } else if (target != null) {
            float heading = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
            rideYaw += clamp(wrap(heading - rideYaw), -7, 7);
        }
        wolf.setRotation(rideYaw, 0);
        wolf.setBodyYaw(rideYaw);
        if (age % 70 == 0 && speed > 0.15) play("swim", 0.5f, 1f);
    }

    /** Un punto con agua cerca (para nadar sin rumbo); el anfibio lo busca cerca de la superficie. */
    private Location waterSpot(Location around, int radius) {
        World w = around.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int tries = 0; tries < 10; tries++) {
            int x = around.getBlockX() + r.nextInt(-radius, radius + 1);
            int z = around.getBlockZ() + r.nextInt(-radius, radius + 1);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = around.getBlockY() + r.nextInt(-3, 3);
            Block b = w.getBlockAt(x, y, z);
            if (!b.isLiquid()) continue;
            if (!species.aquatic()) {
                while (b.getRelative(BlockFace.UP).isLiquid() && b.getY() < around.getBlockY() + 6) b = b.getRelative(BlockFace.UP);
                return b.getLocation().add(0.5, -0.3, 0.5);
            }
            if (!b.getRelative(BlockFace.UP).isLiquid()) b = b.getRelative(BlockFace.DOWN);   // no en la superficie
            if (b.isLiquid()) return b.getLocation().add(0.5, 0.5, 0.5);
        }
        return null;
    }

    /** Mordida en el agua (sin la IA del lobo hay que hacerla a mano). */
    private void bite(LivingEntity target) {
        biteCooldown = 20;
        onAttack();
        double damage = species.damage * Math.min(1.1f, relativeSize());
        target.damage(damage, wolf);
        Vector push = target.getLocation().toVector().subtract(wolf.getLocation().toVector()).setY(0);
        if (push.lengthSquared() > 1e-4) target.setVelocity(push.normalize().multiply(0.5).setY(0.15));
    }

    /** Acuatico en tierra: se arrastra despacio hacia el agua mas cercana. */
    private void beached() {
        if (age % 40 != 0 || wolf.isTamed() && (wolf.isSitting() || order != Order.FOLLOW) || wolf.isLeashed()) return;
        Location at = wolf.getLocation();
        Location water = null;
        double best = Double.MAX_VALUE;
        World w = at.getWorld();
        for (int dx = -12; dx <= 12; dx += 3) {
            for (int dz = -12; dz <= 12; dz += 3) {
                for (int dy = -3; dy <= 2; dy++) {
                    Block b = w.getBlockAt(at.getBlockX() + dx, at.getBlockY() + dy, at.getBlockZ() + dz);
                    if (!b.isLiquid()) continue;
                    double d = dx * dx + dz * dz + dy * dy;
                    if (d < best) {
                        best = d;
                        water = b.getLocation().add(0.5, 0, 0.5);
                    }
                }
            }
        }
        if (water != null) wolf.getPathfinder().moveTo(water, 1.0);
    }

    /**
     * Nadando con jinete: W nada hacia donde mirás (mirar abajo = bucear), Ctrl mas rapido, espacio sube.
     * Al jinete le da respiracion bajo el agua mientras va montado.
     */
    private void rideSwim(Player rider, Input in) {
        if (age % 40 == 0) {
            rider.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 100, 0, false, false, true));
        }
        wolf.setFallDistance(0);
        rider.setFallDistance(0);
        double pitch = Math.toRadians(clamp(rider.getPitch(), -70, 70));
        double max = (in.isSprint() ? species.swimSprint : species.swimSpeed) * g().speed;
        double f = (in.isForward() ? 1 : 0) - (in.isBackward() ? 0.5 : 0);
        double s = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        double yaw = Math.toRadians(rideYaw);
        double h = Math.cos(pitch) * max * f;
        double vy = -Math.sin(pitch) * max * f;
        if (in.isJump()) vy = Math.max(vy, 0.25);
        else if (f == 0) vy = species.aquatic() ? 0 : 0.04;   // quieto: el anfibio flota
        double vx = -Math.sin(yaw) * h + Math.cos(yaw) * s * max * 0.4;
        double vz = Math.cos(yaw) * h + Math.sin(yaw) * s * max * 0.4;
        steer(vx, vy, vz, 0.18);
        if (age % 60 == 0 && f > 0) play("swim", 0.6f, 1f);
    }

    /** Mosasaurus: ondula la cola, rema con las aletas y se inclina con la subida y bajada. Varado se arrastra. */
    private void animateSwimmer() {
        float t = age;
        float sw = swimming ? 1f : 0f;
        float s = swimming ? Math.max(0.3f, Math.min(1f, speed + (float) flyVel.length() * 2)) : speed;
        float k = form.height() / 18f;
        swimPhase += swimming ? 0.12f + 0.22f * s : 0.25f * s;
        float w = (float) Math.sin(swimPhase);
        float w2 = (float) Math.sin(swimPhase - 1.1f);

        DinoModel.BonePose pBody = model.pose(body);
        DinoModel.BonePose pHead = model.pose(head);
        DinoModel.BonePose pJaw = model.pose(jaw);
        DinoModel.BonePose pT1 = model.pose(tail1);
        DinoModel.BonePose pT2 = model.pose(tail2);
        DinoModel.BonePose fr = model.pose(legFR), fl = model.pose(legFL), br = model.pose(legBR), bl = model.pose(legBL);

        pBody.dy = (float) Math.sin(t * 0.05f) * 0.3f * k;
        float amp = 0.35f + s;
        pHead.ry = -w * 4 * amp;
        pT1.ry = w * 10 * amp;
        pT2.ry = w2 * 18 * amp;
        pBody.rz = w * 2 * sw;
        // aletas: reman en el agua; en tierra quedan apoyadas y empujan al arrastrarse
        float paddle = (float) Math.sin(swimPhase * 0.5f);
        fr.rz = (6 + 16 * paddle) * sw - 8 * (1 - sw);
        fl.rz = -(6 + 16 * paddle) * sw + 8 * (1 - sw);
        br.rz = 10 * (float) Math.sin(swimPhase * 0.5f + 1.2f) * sw - 6 * (1 - sw);
        bl.rz = -10 * (float) Math.sin(swimPhase * 0.5f + 1.2f) * sw + 6 * (1 - sw);
        fr.ry = -12 * paddle * sw + 20 * w * s * (1 - sw);
        fl.ry = 12 * paddle * sw + 20 * w * s * (1 - sw);

        if (attack >= 0) {
            float a = (float) Math.sin(Math.PI * attack / ATTACK_TICKS);
            pHead.rx -= 12 * a;
            pJaw.rx += 45 * a;
        }
        if (roar >= 0) {
            float r = (float) Math.sin(Math.PI * roar / ROAR_TICKS);
            pHead.rx -= 18 * r;
            pJaw.rx += 35 * r;
        }
        if (hurt > 0) {
            float h = hurt / 7f;
            pBody.rz += (float) Math.sin(hurt * 2.2f) * 6 * h;
            pT1.ry += (float) Math.sin(hurt * 2.0f) * 12 * h;
        }
        if (swimming) tilt(1f);
    }

    /** Spinosaurus nadando: patea con las patas, la cola-remo ondula y se inclina como el mosasaurus. */
    private void swimPose() {
        swimPhase += 0.15f + 0.2f * speed;
        float w = (float) Math.sin(swimPhase);
        model.pose(tail1).ry += w * 12;
        model.pose(tail2).ry += (float) Math.sin(swimPhase - 1.0f) * 22;
        if (legR >= 0) {
            model.pose(legR).rx += w * 30 + 20;
            model.pose(legL).rx += -w * 30 + 20;
        }
        model.pose(head).rx -= 8;
        tilt(0.6f);
    }

    /** Inclinacion de todo el modelo alrededor del cuerpo segun suba o baje (flightPitch lo calcula steer). */
    private void tilt(float amount) {
        float y = form.bones().get(body).jy() / 16f;
        model.extra().translate(0, y, 0).rotateX((float) Math.toRadians(flightPitch * amount)).translate(0, -y, 0);
    }

    // ------------------------------------------------------------ animacion
    private void animate() {
        model.resetPose();
        switch (species.body) {
            case QUADRUPED -> animateQuadruped();
            case FLYER -> animateFlyer();
            case SWIMMER -> animateSwimmer();
            default -> animateBiped();
        }
        if (swimming && species.body != Species.Body.SWIMMER) swimPose();
        if (sick > 0.01f) {
            // muerto de hambre: cabeza y cola caidas
            model.pose(head).rx += 22 * sick;
            if (tail1 >= 0) model.pose(tail1).rx -= 10 * sick;
        }
        if (eating >= 0) {
            // baja la cabeza al comedero y mastica
            float e = Math.max(0f, Math.min(1f, Math.min(eating / 6f, (EAT_TICKS - eating) / 6f)));
            model.pose(head).rx += 38 * e;
            if (jaw >= 0) model.pose(jaw).rx += Math.abs((float) Math.sin(eating * 0.8f)) * 22 * e;
        }
    }

    /** Anota la especie (y el color) en la Dinopedia de los jugadores que lo ven de cerca. */
    private void discover(Location loc) {
        for (Player p : model.viewers()) {
            double r = manager.dex().discoverRange(p);
            if (p.getWorld() == loc.getWorld() && p.getLocation().distanceSquared(loc) < r * r && p.hasLineOfSight(wolf)) {
                manager.dex().discover(p, species, variant);
                var missions = com.dinoearth.mission.MissionManager.get();
                if (missions != null) missions.onSight(p, this);
            }
        }
    }

    private void animateBiped() {
        float t = age;
        float s = speed * (1 - sit);
        float sinP = (float) Math.sin(phase);
        float cosP = (float) Math.cos(phase);
        float k = form.height() / 32f;   // los desplazamientos escalan con el tamaño del modelo

        DinoModel.BonePose pBody = model.pose(body);
        DinoModel.BonePose pHead = model.pose(head);
        DinoModel.BonePose pJaw = model.pose(jaw);
        DinoModel.BonePose pT1 = model.pose(tail1);
        DinoModel.BonePose pT2 = model.pose(tail2);
        DinoModel.BonePose pLR = model.pose(legR);
        DinoModel.BonePose pLL = model.pose(legL);

        float breath = (float) Math.sin(t * 0.08f);
        pBody.dy = breath * 0.25f * k;

        // caminar / correr
        pLR.rx = sinP * species.swing * s;
        pLL.rx = -sinP * species.swing * s;
        if (shinR >= 0) {
            // rodilla: la pata que va para adelante se dobla (rx positivo = para atras)
            model.pose(shinR).rx = 30 * s * Math.max(0, -cosP) + 80 * sit;
            model.pose(shinL).rx = 30 * s * Math.max(0, cosP) + 80 * sit;
        }
        pBody.dy += -Math.abs(cosP) * 1.2f * s * k;
        pBody.rx = 4 * s;
        pBody.rz = sinP * 2.5f * s;
        pHead.rx = -4 * s + (float) Math.sin(phase * 2) * 3 * s + 18 * charge;   // embestida: cabeza baja

        // cola: balanceo lento en reposo, mas rapido caminando
        pT1.ry = (float) Math.sin(t * 0.06f) * 7 * (1 - s) + sinP * 6 * s;
        pT2.ry = (float) Math.sin(t * 0.06f - 0.9f) * 11 * (1 - s) + (float) Math.sin(phase - 0.9f) * 10 * s;
        pT1.rx = 3 * s + breath * 1.5f;
        pT2.rx = 2 * s - breath * 1.5f;

        if (attack >= 0) {
            float a = (float) Math.sin(Math.PI * attack / ATTACK_TICKS);
            pHead.rx += 28 * a;
            pJaw.rx += 40 * a;
            pBody.rx += 6 * a;
            pT1.rx -= 8 * a;
        }
        if (hurt > 0) {
            float h = hurt / 7f;
            pBody.rz += (float) Math.sin(hurt * 2.2f) * 7 * h;
            pHead.rx -= 12 * h;
            pHead.ry += (float) Math.sin(hurt * 1.7f) * 10 * h;
            pT1.ry += (float) Math.sin(hurt * 2.0f) * 12 * h;
        }
        if (roar >= 0) {
            float r = (float) Math.sin(Math.PI * roar / ROAR_TICKS);
            pHead.rx -= 30 * r;
            pJaw.rx += 32 * r + (float) Math.sin(roar * 1.7f) * 3 * r;
        }
        if (spit >= 0) {
            // se echa atras y despues lanza la cabeza hacia adelante con la boca abierta
            float p = spit / (float) SPIT_TICKS;
            pHead.rx += p < 0.55f ? -25 * (p / 0.55f) : -25 + 45 * ((p - 0.55f) / 0.45f);
            pJaw.rx += 35 * (float) Math.sin(Math.PI * p);
        }
        if (frillR >= 0) {
            // gola: plegada y chiquita contra el cuello, se despliega y vibra al abrirse
            float quiver = (float) Math.sin(t * 1.4f) * 4 * frill;
            model.pose(frillR).ry = 80 * (1 - frill) + quiver;
            model.pose(frillL).ry = -80 * (1 - frill) - quiver;
            model.pose(frillR).scale = model.pose(frillL).scale = 0.3f + 0.7f * frill;
            // cerrada queda pegada al cuello (y titila contra el): solo se dibuja mientras se abre
            model.pose(frillR).hidden = model.pose(frillL).hidden = frill < 0.05f;
        }
        if (sit > 0.01f) {
            float d = 7 * k * sit;
            pBody.dy -= d;
            pBody.rx -= 6 * sit;
            pLR.dy -= d;
            pLL.dy -= d;
            pLR.rx -= 70 * sit;
            pLL.rx -= 70 * sit;
            pT1.rx -= 6 * sit;
            pT2.rx -= 4 * sit;
        }
    }

    private void animateQuadruped() {
        float t = age;
        float s = speed * (1 - sit);
        float sinP = (float) Math.sin(phase);
        float cosP = (float) Math.cos(phase);
        float k = form.height() / 44f;

        DinoModel.BonePose pBody = model.pose(body);
        DinoModel.BonePose pHead = model.pose(head);
        DinoModel.BonePose pJaw = model.pose(jaw);
        DinoModel.BonePose pT1 = model.pose(tail1);
        DinoModel.BonePose pT2 = model.pose(tail2);
        DinoModel.BonePose fr = model.pose(legFR), fl = model.pose(legFL), br = model.pose(legBR), bl = model.pose(legBL);

        float breath = (float) Math.sin(t * 0.06f);
        pBody.dy = breath * 0.3f * k;

        // paso diagonal: delantera derecha con trasera izquierda
        fr.rx = sinP * 26 * s;
        bl.rx = sinP * 26 * s;
        fl.rx = -sinP * 26 * s;
        br.rx = -sinP * 26 * s;
        pBody.dy += -Math.abs(cosP) * 1.0f * s * k;
        pBody.rz = sinP * 1.5f * s;
        pHead.rx = (float) Math.sin(phase * 2) * 2.5f * s + (float) Math.sin(t * 0.05f) * 1.5f * (1 - s);

        pT1.ry = (float) Math.sin(t * 0.05f) * 5 * (1 - s) + sinP * 5 * s;
        pT2.ry = (float) Math.sin(t * 0.05f - 0.8f) * 8 * (1 - s) + (float) Math.sin(phase - 0.8f) * 8 * s;
        pT1.rx = breath * 1.2f;

        pHead.rx += 18 * charge;   // embestida: cabeza baja, cuernos al frente
        if (tailSwing >= 0) {
            // coletazo: la cola barre para un lado y vuelve
            float p = tailSwing / (float) TAIL_TICKS;
            float sw = (float) Math.sin(p * Math.PI * 2) * (1 - p * 0.5f);
            pT1.ry += 35 * sw * tailSide;
            pT2.ry += 45 * sw * tailSide;
        }

        if (attack >= 0) {
            // cabezazo: baja y levanta la cabeza
            float p = attack / (float) ATTACK_TICKS;
            pHead.rx += 22 * (float) Math.sin(Math.PI * p) - 14 * (float) Math.sin(2 * Math.PI * p) * (p > 0.5f ? 1 : 0);
            pBody.rx += 3 * (float) Math.sin(Math.PI * p);
        }
        if (hurt > 0) {
            float h = hurt / 7f;
            pBody.rz += (float) Math.sin(hurt * 2.2f) * 4 * h;
            pHead.ry += (float) Math.sin(hurt * 1.7f) * 8 * h;
            pHead.rx -= 8 * h;
            pT1.ry += (float) Math.sin(hurt * 2.0f) * 10 * h;
        }
        if (roar >= 0) {
            float r = (float) Math.sin(Math.PI * roar / ROAR_TICKS);
            pHead.rx -= 18 * r;
            pJaw.rx += 22 * r;
        }
        if (sit > 0.01f) {
            // echado: patas delanteras dobladas hacia atras, traseras hacia adelante
            float d = 10 * k * sit;
            pBody.dy -= d;
            for (DinoModel.BonePose leg : new DinoModel.BonePose[]{fr, fl, br, bl}) leg.dy -= d;
            fr.rx += 75 * sit;
            fl.rx += 75 * sit;
            br.rx -= 75 * sit;
            bl.rx -= 75 * sit;
            pHead.rx += 8 * sit;
            pT1.rx -= 8 * sit;
        }
    }

    /**
     * Pteranodon. En el suelo camina en cuatro patas: el brazo del ala baja y hace de pata,
     * el dedo largo se dobla para atras y las membranas se achican. En el aire abre las alas,
     * aletea, estira las patas para atras y todo el cuerpo se inclina con la subida/bajada y los giros.
     */
    private void animateFlyer() {
        float t = age;
        float s = speed * (1 - sit);
        float sinP = (float) Math.sin(phase);
        float k = form.height() / 28f;
        float g = fold;          // 1 = en el suelo
        float a = 1 - fold;      // 1 = volando

        DinoModel.BonePose pBody = model.pose(body);
        DinoModel.BonePose pHead = model.pose(head);
        DinoModel.BonePose pJaw = model.pose(jaw);
        DinoModel.BonePose wr = model.pose(wingR), wl = model.pose(wingL);
        DinoModel.BonePose wr2 = model.pose(wingR2), wl2 = model.pose(wingL2);
        DinoModel.BonePose lr = model.pose(legR), ll = model.pose(legL);

        float breath = (float) Math.sin(t * 0.08f);
        pBody.dy = breath * 0.2f * k * g;

        // --- suelo: paso diagonal (ala derecha con pata izquierda)
        wr.rz = -68 * g;
        wl.rz = 68 * g;
        wr.rx = (-10 - sinP * 22 * s) * g;
        wl.rx = (-10 + sinP * 22 * s) * g;
        wr2.ry = 145 * g;
        wr2.rz = 30 * g;
        wl2.ry = -145 * g;
        wl2.rz = -30 * g;
        lr.rx = -sinP * 26 * s * g;
        ll.rx = sinP * 26 * s * g;
        pBody.dy += -Math.abs((float) Math.cos(phase)) * 0.8f * s * k * g;
        pHead.rx = (float) Math.sin(phase * 2) * 3 * s * g;
        float membrane = 1 - 0.88f * g;
        for (int m : new int[]{memR, memL, memR2, memL2}) {
            model.pose(m).scale = membrane;
            // plegada del todo queda apretada adentro del ala y sus caras titilan: mejor no dibujarla
            model.pose(m).hidden = membrane < 0.2f;
        }

        // --- aire: aleteo (o planeo con las alas apenas en V)
        float beat = (float) Math.sin(flapPhase) * flap;
        float tipBeat = (float) Math.sin(flapPhase - 0.7f) * flap;
        wr.rz += a * (4 + 34 * beat);
        wl.rz -= a * (4 + 34 * beat);
        wr2.rz += a * (-3 + 18 * tipBeat);
        wl2.rz -= a * (-3 + 18 * tipBeat);
        lr.rx += a * 75;
        ll.rx += a * 75;
        pHead.rx += a * 12;
        pBody.dy += a * -beat * 0.6f * k;
        if (a > 0.01f) {
            // inclinacion de todo el modelo alrededor del cuerpo
            float y = form.bones().get(body).jy() / 16f;
            model.extra().translate(0, y, 0)
                    .rotateZ((float) Math.toRadians(flightRoll * a))
                    .rotateX((float) Math.toRadians(flightPitch * a))
                    .translate(0, -y, 0);
        }

        if (attack >= 0) {
            // picotazo
            float p = (float) Math.sin(Math.PI * attack / ATTACK_TICKS);
            pHead.rx += 30 * p;
            pJaw.rx += 30 * p;
        }
        if (hurt > 0) {
            float h = hurt / 7f;
            pBody.rz += (float) Math.sin(hurt * 2.2f) * 6 * h;
            pHead.rx -= 10 * h;
            pHead.ry += (float) Math.sin(hurt * 1.7f) * 12 * h;
        }
        if (roar >= 0) {
            float r = (float) Math.sin(Math.PI * roar / ROAR_TICKS);
            pHead.rx -= 35 * r;
            pJaw.rx += 30 * r;
            // abre un poco las alas para parecer mas grande
            wr.rz += 25 * r * g;
            wl.rz -= 25 * r * g;
        }
        if (sit > 0.01f) {
            float d = 6 * k * sit;
            pBody.dy -= d;   // las alas cuelgan del cuerpo, bajan con el
            lr.dy -= d;
            ll.dy -= d;
            lr.rx -= 60 * sit;
            ll.rx -= 60 * sit;
            pHead.rx += 10 * sit;
        }
    }

    private boolean tickDeath() {
        dying++;
        float k = Math.min(1f, dying / 9f);
        float e = k * k;    // cae cada vez mas rapido
        model.resetPose();
        // cae de costado: gira 90 grados y sube lo justo para que el costado quede sobre el piso
        model.extra().translate(0, fallLift * e, 0).rotateZ((float) Math.toRadians(90 * e * fallSide));
        model.pose(jaw).rx = 25 * k;
        model.pose(head).rx = 15 * k;
        if (legR >= 0) {
            model.pose(legR).rx = -20 * k;
            model.pose(legL).rx = 10 * k;
        }
        if (legFR >= 0) {
            model.pose(legFR).rx = -15 * k;
            model.pose(legBL).rx = 15 * k;
        }
        if (frillR >= 0) {
            model.pose(frillR).ry = 80;
            model.pose(frillL).ry = -80;
            model.pose(frillR).scale = model.pose(frillL).scale = 0.3f;
            model.pose(frillR).hidden = model.pose(frillL).hidden = true;
        }
        if (wingR >= 0) {
            // alas plegadas, medio abiertas al caer
            model.pose(wingR).rz = -40;
            model.pose(wingL).rz = 40;
            model.pose(wingR2).ry = 145;
            model.pose(wingL2).ry = -145;
            for (int m : new int[]{memR, memL, memR2, memL2}) model.pose(m).scale = 0.4f;
        }
        model.applyPose(wolf.getLocation(), deathYaw, visualScale(), 2);
        if (dying > DEATH_TICKS) {
            model.remove();
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------ sonidos
    /** Sonidos del resource pack: dinoearth:<especie>.<nombre>. Las crias usan los suyos. */
    private void play(String name, float volume, float pitch) {
        String key = name;
        if (isBabyForm()) {
            key = switch (name) {
                case "hurt", "death" -> "baby_hurt";
                case "step" -> null;
                case "swim" -> "swim";
                default -> "baby_ambient";
            };
            if (key == null) return;
            volume *= 0.7f;
            pitch *= 1.15f - 0.3f * growth;
        }
        if (species.roars && !isBabyForm()) volume *= 2f;   // el T-Rex se oye el doble de lejos
        float jitter = 1f + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.12f;
        wolf.getWorld().playSound(wolf.getLocation(), "dinoearth:" + species.id + "." + key,
                species.hostile() ? SoundCategory.HOSTILE : SoundCategory.NEUTRAL, volume, pitch * jitter);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0, Math.min(1, t));
    }

    private static float wrap(float deg) {
        deg %= 360;
        if (deg >= 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

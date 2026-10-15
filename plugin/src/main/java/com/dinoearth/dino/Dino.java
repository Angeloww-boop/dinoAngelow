package com.dinoearth.dino;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import com.dinoearth.Keys;
import com.dinoearth.Settings;
import com.dinoearth.model.DinoModel;
import com.dinoearth.model.DinoRig;
import com.dinoearth.species.Species;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
    private static final int DEATH_TICKS = 24;
    private static final int SPIT_TICKS = 16;
    private static final int ROAR_COOLDOWN = 140;

    private final Wolf wolf;
    private final Species species;
    private final DinoRig rig;
    private final Keys keys;
    private final Settings settings;

    private final String variant;
    private final boolean male;
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
    private int age;
    private int attack = -1;
    private int roar = -1;
    private int hurt;
    private int dying = -1;
    private int spit = -1;
    private int spitCooldown = 40;
    private int poseEvery = 2;   // ticks entre poses (0 = nadie lo ve, no se manda nada)
    private int lastPose;
    private final Map<UUID, Integer> rammed = new HashMap<>();

    public Dino(Wolf wolf, Species species, Keys keys, Settings settings) {
        this.wolf = wolf;
        this.species = species;
        this.rig = species.rig;
        this.keys = keys;
        this.settings = settings;
        PersistentDataContainer pdc = wolf.getPersistentDataContainer();
        this.variant = rig.variant(pdc.getOrDefault(keys.variant, PersistentDataType.STRING, "")).id();
        this.male = "male".equals(pdc.get(keys.sex, PersistentDataType.STRING));
        float g = pdc.getOrDefault(keys.growth, PersistentDataType.FLOAT, 1f);
        this.growth = species.hasBabies() ? g : 1f;
        this.saddled = pdc.getOrDefault(keys.saddled, PersistentDataType.BYTE, (byte) 0) == 1;
        this.last = wolf.getLocation();
        this.sit = wolf.isSitting() ? 1 : 0;
        this.rideYaw = wolf.getBodyYaw();
        this.age = ThreadLocalRandom.current().nextInt(1000);
        if (rider() == null) wolf.setAware(true);
        if (species.flies()) wolf.setGravity(true);   // por si se guardo volando
        adjustGoals();
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
            case HERBIVORE -> new GoalKey[]{VanillaGoal.NON_TAME_RANDOM, VanillaGoal.NEAREST_ATTACKABLE,
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
        model.applyPose(wolf.getLocation(), wolf.getBodyYaw(), visualScale(), 0);
        lastPose = age;
    }

    private boolean isBabyForm() {
        return species.hasBabies() && growth < 0.5f;
    }

    /** Escala del modelo actual: la cria crece de 0.65 a 1, despues pasa al modelo adulto de 0.6 a 1. */
    private float modelScale() {
        float s = !species.hasBabies() ? 1f
                : growth < 0.5f ? lerp(0.65f, 1f, growth / 0.5f) : lerp(0.6f, 1f, (growth - 0.5f) / 0.5f);
        return s * (male ? species.maleScale : 1f);
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
        set(Attribute.MAX_HEALTH, Math.round(species.health * (0.25 + 0.75 * Math.min(1f, size))));
        set(Attribute.ATTACK_DAMAGE, Math.max(1, species.damage * Math.min(1.1f, size)));
        set(Attribute.MOVEMENT_SPEED, growth >= 1 ? species.speed : species.speed * (0.85 + 0.15 * growth));
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

    public boolean canRide() {
        return species.rideable && rig.hasSaddle() && isAdult() && saddled && wolf.isTamed();
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

    // ------------------------------------------------------------ eventos
    public void onAttack() {
        attack = 0;
        play("attack", 0.8f, 1f);
    }

    public void onHurt() {
        hurt = 7;
        play("hurt", 0.8f, 1f);
    }

    public void onDeath() {
        if (flying) wolf.setGravity(true);
        dying = 0;
        model.dismount();
        play(isBabyForm() ? "hurt" : "death", 0.9f, isBabyForm() ? 0.8f : 1f);
    }

    public void onTamed() {
        roar = 0;
        play("roar", 0.8f, 1.1f);
    }

    public void remove() {
        // que no quede flotando si se descarga el chunk en pleno vuelo
        if (flying && wolf.isValid()) wolf.setGravity(true);
        save();
        model.remove();
    }

    // ------------------------------------------------------------ tick
    /** @return false cuando el dino ya no existe y hay que soltarlo. */
    public boolean tick() {
        if (dying >= 0) return tickDeath();
        if (!wolf.isValid()) return false;
        if (!model.isValid()) buildModel();   // alguien borro las displays (p. ej. /kill)

        age++;
        if (age % 20 == 0) model.remount();

        if (growth < 1f) {
            boolean wasBaby = isBabyForm();
            growth = Math.min(1f, growth + 1f / settings.growthTicks);
            if (wasBaby && !isBabyForm()) buildModel();
            if (age % 20 == 0) updateStats();
            if (age % 200 == 0) {
                save();
                updateViewRange();
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
        if (rider != null) ride(rider);
        else if (flying) flyAlone();
        else {
            if (!wolf.isAware()) wolf.setAware(true);
            if (species.flies()) maybeTakeOff();
        }

        Location loc = wolf.getLocation();
        float bodyYaw = rider != null || flying ? rideYaw : wolf.getBodyYaw();

        // velocidad horizontal real -> ciclo de pasos
        Vector delta = loc.toVector().subtract(last.toVector()).setY(0);
        float moved = last.getWorld() == loc.getWorld() ? (float) delta.length() : 0f;
        if (moved > 2) moved = 0; // teleport
        last = loc;
        float size = Math.max(0.2f, visualScale() * (isBabyForm() ? rig.baby().height() / rig.adult().height() : 1f));
        float target = Math.min(1f, moved / (0.3f * Math.max(0.5f, size)));
        speed += (target - speed) * 0.25f;
        float prevPhase = phase;
        phase += moved * species.stride / size;
        if (Math.floor(prevPhase / Math.PI) != Math.floor(phase / Math.PI) && speed > 0.15f && !isBabyForm() && !flying) {
            play("step", species.body == Species.Body.QUADRUPED || species.roars ? 0.6f : 0.35f, 1f);
        }
        if (species.flies()) tickWings();

        sit += ((wolf.isSitting() ? 1f : 0f) - sit) * 0.15f;
        behave(rider, loc);

        if (hurt > 0) hurt--;
        if (attack >= 0 && ++attack > ATTACK_TICKS) attack = -1;
        if (roar >= 0 && ++roar > ROAR_TICKS) roar = -1;
        if (roar < 0 && attack < 0 && spit < 0 && ThreadLocalRandom.current().nextInt(isBabyForm() ? 260 : 400) == 0) {
            roar = 0;
            play("ambient", 0.6f, 1f);
        }

        // la pose solo se calcula y se manda cuando alguien la puede ver, y menos seguido de lejos
        if (rider != null) poseEvery = 1;
        else if (age % 10 == 0 || poseEvery == 1) poseEvery = poseRate(loc);
        if (poseEvery > 0 && age - lastPose >= poseEvery) {
            lastPose = age;
            animate();
            boolean free = rider == null && !flying;   // mira para donde mira el lobo
            float headYaw = free ? wrap(loc.getYaw() - bodyYaw) : 0;
            model.pose(head).ry += -clamp(headYaw, -50, 50) * 0.8f;
            model.pose(head).rx += clamp(free ? loc.getPitch() : 0, -35, 35) * 0.7f;
            model.applyPose(loc, bodyYaw, visualScale(), poseEvery + 1);
        }
        return true;
    }

    /**
     * Cada cuantos ticks mandar la pose segun el jugador mas cercano que lo ve:
     * cerca fluido, lejos menos seguido, y nunca si nadie lo tiene cargado (0).
     */
    private int poseRate(Location loc) {
        double best = Double.MAX_VALUE;
        for (Player p : model.viewers()) {
            if (p.getWorld() == loc.getWorld()) best = Math.min(best, p.getLocation().distanceSquared(loc));
        }
        if (best == Double.MAX_VALUE) return 0;
        if (best < 24 * 24) return 2;
        if (best < 48 * 48) return 4;
        if (best < 96 * 96) return 8;
        return 16;
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

        boolean hunts = species.temper == Species.Temper.HOSTILE || species.territorial && !wolf.isTamed();
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
        if (rider != null && charging && age % 2 == 0) ram(rider);
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

        rideYaw += clamp(wrap(rider.getYaw() - rideYaw), flying ? -9 : -16, flying ? 9 : 16);
        wolf.setRotation(rideYaw, 0);
        wolf.setBodyYaw(rideYaw);
        if (flying) {
            rideFlight(rider, in);
            return;
        }

        double f = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
        double s = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        double max = f > 0 && in.isSprint() ? species.rideSprint : species.rideWalk;
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
            // el T-Rex no salta: la barra espaciadora es para rugir
            if (in.isJump() && roarCooldown == 0 && roar < 0) {
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
        double target = in.isForward() ? (in.isSprint() ? species.flySprint : species.flySpeed)
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
        if (scared && wolf.getLastDamageCause() instanceof EntityDamageByEntityEvent ev) {
            // el centro de las vueltas queda lejos del que lo ataco
            Vector away = at.toVector().subtract(ev.getDamager().getLocation().toVector()).setY(0);
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

    // ------------------------------------------------------------ animacion
    private void animate() {
        model.resetPose();
        switch (species.body) {
            case QUADRUPED -> animateQuadruped();
            case FLYER -> animateFlyer();
            default -> animateBiped();
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
        pHead.rx = -4 * s + (float) Math.sin(phase * 2) * 3 * s;

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
        for (int m : new int[]{memR, memL, memR2, memL2}) model.pose(m).scale = membrane;

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
        float k = Math.min(1f, dying / 10f);
        model.resetPose();
        model.extra().rotateZ((float) Math.toRadians(90 * k * k));   // cae de costado
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
        }
        if (wingR >= 0) {
            // alas plegadas, medio abiertas al caer
            model.pose(wingR).rz = -40;
            model.pose(wingL).rz = 40;
            model.pose(wingR2).ry = 145;
            model.pose(wingL2).ry = -145;
            for (int m : new int[]{memR, memL, memR2, memL2}) model.pose(m).scale = 0.4f;
        }
        model.applyPose(wolf.getLocation(), rideYaw, visualScale(), 2);
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

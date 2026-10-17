package com.dinoearth.nest;

import com.dinoearth.Keys;
import com.dinoearth.Settings;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.dino.Genes;
import com.dinoearth.egg.EggManager;
import com.dinoearth.integration.Wilderness;
import com.dinoearth.model.DinoModel;
import com.dinoearth.model.DinoRig;
import com.dinoearth.species.Species;
import com.dinoearth.text.Lang;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nidos salvajes. Cada tanto una hembra adulta salvaje (de una especie que pone huevos) arma un nido donde esta
 * parada, con 1 a 3 huevos, y se queda cerca cuidandolo. Con click izquierdo se le saca un huevo (que nace
 * domesticado por quien lo pone, con los genes de la madre y un padre salvaje), pero la madre ataca a quien se
 * acerca o le roba. Las presas no pelean: se asustan y salen corriendo, asi sus nidos son faciles.
 *
 * Igual que los huevos y los comederos: una Interaction (guarda los datos, se guarda con el mundo) + displays con el
 * modelo (el nido y un huevo por hueso). Con el tiempo, o vacio, el nido se deshace.
 */
public final class NestManager implements Listener {

    private static final int MAX_EGGS = 3;
    // huevos acomodados adentro del nido (px desde el centro)
    private static final float[][] EGG_AT = {{-4.5f, 1f, -3f}, {4.5f, 1f, -2f}, {0f, 1f, 4.5f}};

    private final Keys keys;
    private final Settings settings;
    private final DinoManager dinos;
    private final EggManager eggs;
    private final Map<UUID, Nest> nests = new HashMap<>();
    private int ticks;

    private final class Nest {
        final Interaction box;
        final Species species;
        DinoModel model;
        int count;

        Nest(Interaction box, Species species) {
            this.box = box;
            this.species = species;
            this.count = box.getPersistentDataContainer().getOrDefault(keys.nestEggs, PersistentDataType.INTEGER, 0);
            build();
        }

        void build() {
            if (model != null) model.remove();
            List<DinoRig.BoneDef> bones = new ArrayList<>();
            bones.add(new DinoRig.BoneDef(0, "nest", -1, 0, 0, 0));
            NamespacedKey[] items = new NamespacedKey[1 + MAX_EGGS];
            items[0] = NamespacedKey.fromString("dinoearth:nest/nest");
            for (int i = 0; i < MAX_EGGS; i++) {
                bones.add(new DinoRig.BoneDef(i + 1, "egg" + i, -1, EGG_AT[i][0], EGG_AT[i][1], EGG_AT[i][2]));
                items[i + 1] = species.rig.eggModel();
            }
            model = new DinoModel(bones, items, box.getLocation(), null, new Vector(), 2f, 1f, keys::tagPart);
            model.setViewRange(0.75f);
            pose();
        }

        void pose() {
            model.resetPose();
            for (int i = 0; i < MAX_EGGS; i++) {
                model.pose(i + 1).scale = Math.min(0.85f, 0.85f / species.rig.eggSize());
                model.pose(i + 1).hidden = i >= count;
            }
            model.applyPose(box.getLocation(), box.getLocation().getYaw(), 1f, 0);
        }

        UUID mother() {
            String id = box.getPersistentDataContainer().get(keys.nestMother, PersistentDataType.STRING);
            try {
                return id != null ? UUID.fromString(id) : null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        Dino motherDino() {
            UUID id = mother();
            Entity e = id != null ? Bukkit.getEntity(id) : null;
            Dino d = e != null ? dinos.get(e) : null;
            return d != null && !d.isDying() && !d.wolf().isTamed() ? d : null;
        }
    }

    public NestManager(Plugin plugin, Keys keys, Settings settings, DinoManager dinos, EggManager eggs) {
        this.keys = keys;
        this.settings = settings;
        this.dinos = dinos;
        this.eggs = eggs;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ------------------------------------------------------------ mundo
    public boolean isNest(Entity e) {
        return e instanceof Interaction && e.getPersistentDataContainer().has(keys.nest);
    }

    public void attach(Interaction box) {
        if (!box.isValid() || nests.containsKey(box.getUniqueId())) return;
        Species s = Species.byId(box.getPersistentDataContainer().get(keys.nestSpecies, PersistentDataType.STRING));
        if (s == null || s.rig == null || !s.rig.hasEgg()) return;
        nests.put(box.getUniqueId(), new Nest(box, s));
    }

    public void detach(Entity e) {
        Nest n = nests.remove(e.getUniqueId());
        if (n != null) n.model.remove();
    }

    public void attachLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (Interaction i : world.getEntitiesByClass(Interaction.class)) {
                if (isNest(i)) attach(i);
            }
        }
    }

    public void shutdown() {
        nests.values().forEach(n -> n.model.remove());
        nests.clear();
    }

    private boolean allowed(Species s) {
        if (!s.rig.hasEgg() || s.isPlus()) return false;
        if (settings.nestSpecies.isEmpty()) return true;
        for (String id : settings.nestSpecies) {
            if (Species.byId(id) == s) return true;
        }
        return false;
    }

    /** Arma un nido debajo de esta hembra salvaje. false si no se puede ahi. */
    private boolean makeNest(Dino mother, int count) {
        Wolf w = mother.wolf();
        Block at = w.getLocation().getBlock();
        if (!at.isPassable() || at.isLiquid() || !at.getRelative(BlockFace.DOWN).getType().isSolid()) return false;
        Location loc = at.getLocation().add(0.5, 0, 0.5);
        if (settings.nestOnlyWild && !Wilderness.isWild(loc)) return false;
        for (Nest n : nests.values()) {
            if (n.box.getWorld() == loc.getWorld() && n.box.getLocation().distanceSquared(loc) < settings.nestSpacing * settings.nestSpacing) {
                return false;
            }
        }
        loc.setYaw(ThreadLocalRandom.current().nextFloat() * 360f);
        Species s = mother.species();
        Interaction box = loc.getWorld().spawn(loc, Interaction.class, i -> {
            i.setInteractionWidth(1.4f);
            i.setInteractionHeight(0.6f);
            i.setResponsive(true);
            PersistentDataContainer pdc = i.getPersistentDataContainer();
            pdc.set(keys.nest, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keys.nestSpecies, PersistentDataType.STRING, s.id);
            pdc.set(keys.nestMother, PersistentDataType.STRING, w.getUniqueId().toString());
            pdc.set(keys.nestEggs, PersistentDataType.INTEGER, Math.max(1, Math.min(MAX_EGGS, count)));
            pdc.set(keys.nestBorn, PersistentDataType.LONG, System.currentTimeMillis());
            // los huevos llevan los genes de la madre (el padre es un salvaje cualquiera)
            mother.genes().write(pdc, keys);
            pdc.set(keys.eggVariant, PersistentDataType.STRING, mother.variant());
        });
        PersistentDataContainer wp = w.getPersistentDataContainer();
        wp.set(keys.guarding, PersistentDataType.STRING, box.getUniqueId().toString());
        wp.set(keys.nestCooldown, PersistentDataType.LONG, System.currentTimeMillis() + settings.nestCooldownMs);
        attach(box);
        loc.getWorld().playSound(loc, "minecraft:entity.turtle.lay_egg", SoundCategory.NEUTRAL, 1f, 0.8f);
        return true;
    }

    /** Admin (/dino nido): el dino que miras arma un nido ya, sin esperar ni sortear. */
    public boolean forceNest(Dino d) {
        if (d.wolf().isTamed() || d.isMale() || !d.isAdult() || !d.species().rig.hasEgg()) return false;
        return makeNest(d, 1 + ThreadLocalRandom.current().nextInt(settings.nestMaxEggs));
    }

    // ------------------------------------------------------------ jugadores
    /** Click derecho: cuantos huevos tiene y si la madre anda cerca. */
    public void inspect(Player p, Interaction box) {
        Nest n = nests.get(box.getUniqueId());
        if (n == null) return;
        Dino mother = n.motherDino();
        boolean near = mother != null && mother.wolf().getWorld() == box.getWorld()
                && mother.wolf().getLocation().distanceSquared(box.getLocation()) < 24 * 24;
        Lang.get().action(p, near ? "nido-madre-cerca" : "nido", "especie", n.species.name, "huevos", n.count);
    }

    /** Click izquierdo: saca un huevo. La madre (si esta cargada y no muy lejos) se le tira encima al ladron. */
    public void take(Player p, Interaction box) {
        Nest n = nests.get(box.getUniqueId());
        if (n == null || p.getGameMode() == GameMode.ADVENTURE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (n.count <= 0) return;
        n.count--;
        PersistentDataContainer pdc = box.getPersistentDataContainer();
        pdc.set(keys.nestEggs, PersistentDataType.INTEGER, n.count);
        Genes mom = Genes.read(pdc, keys);
        String momVariant = pdc.get(keys.eggVariant, PersistentDataType.STRING);
        String father = n.species.rig.randomVariant().id();
        String[] color = Genes.childColor(n.species, momVariant, father, settings);
        Genes.Parent motherParent = momVariant != null ? new Genes.Parent(momVariant, false, null) : null;
        Genes child = Genes.child(mom != null ? mom : Genes.wild(settings), null, motherParent, null,
                "1".equals(color[1]), settings);
        ItemStack egg = eggs.createItem(n.species, 1, child, color[0]);
        Location at = box.getLocation();
        at.getWorld().dropItemNaturally(at.clone().add(0, 0.4, 0), egg);
        at.getWorld().playSound(at, "minecraft:entity.turtle.egg_crack", SoundCategory.NEUTRAL, 1f, 0.9f);
        at.getWorld().spawnParticle(Particle.ITEM, at.clone().add(0, 0.3, 0), 10, 0.3, 0.1, 0.3, 0.05,
                new ItemStack(Material.TURTLE_EGG));
        var stats = com.dinoearth.stats.PlayerStats.get();
        if (stats != null) stats.add(p, "nidos", 1);

        Dino mother = n.motherDino();
        boolean angry = false;
        if (mother != null && mother.wolf().getWorld() == at.getWorld()
                && mother.wolf().getLocation().distanceSquared(at) < 48 * 48) {
            if (mother.species().temper == Species.Temper.PREY) {
                mother.alarm(p.getLocation());
            } else {
                mother.provoke(p);
                angry = true;
            }
        }
        Lang.get().action(p, angry ? "nido-robado-madre" : "nido-robado", "especie", n.species.name, "huevos", n.count);
        if (n.count <= 0) {
            n.model.remove();
            nests.remove(box.getUniqueId());
            box.remove();
        } else {
            n.pose();
        }
    }

    // ------------------------------------------------------------ cada segundo
    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        Iterator<Nest> it = nests.values().iterator();
        while (it.hasNext()) {
            Nest n = it.next();
            if (!n.box.isValid()) {
                n.model.remove();
                it.remove();
                continue;
            }
            if (!n.model.isValid()) n.build();
            long born = n.box.getPersistentDataContainer().getOrDefault(keys.nestBorn, PersistentDataType.LONG, now);
            Location at = n.box.getLocation();
            if (now - born > settings.nestLifetimeMs && at.getNearbyPlayers(32).isEmpty()) {
                n.model.remove();
                it.remove();
                n.box.remove();
                continue;
            }
            guard(n, at);
        }
        if (settings.nests && ticks % 60 == 0) spawnNests(now);
    }

    /** La madre no se aleja del nido y ataca al que se acerca (agachado tenes que estar mas cerca). */
    private void guard(Nest n, Location at) {
        Dino mother = n.motherDino();
        if (mother == null || mother.wolf().getWorld() != at.getWorld()) return;
        Wolf w = mother.wolf();
        if (mother.species().temper == Species.Temper.PREY) {
            if (w.getTarget() == null && ticks % 5 == 0 && w.getLocation().distanceSquared(at) > 12 * 12) {
                w.getPathfinder().moveTo(at, 1.0);
            }
            return;
        }
        if (w.getTarget() instanceof Player) return;
        double r = settings.nestGuardRange;
        for (Player p : at.getNearbyPlayers(r)) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            double reach = p.isSneaking() ? r / 2 : r;
            if (p.getLocation().distanceSquared(at) > reach * reach) continue;
            mother.provoke(p);
            Lang.get().action(p, "nido-madre", "especie", n.species.name);
            return;
        }
        if (ticks % 5 == 0 && w.getLocation().distanceSquared(at) > 12 * 12) w.getPathfinder().moveTo(at, 1.1);
    }

    /** Cada minuto: algunas hembras salvajes sin nido arman uno. */
    private void spawnNests(long now) {
        if (nests.size() >= settings.nestMaxLoaded) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Dino d : dinos.all()) {
            if (nests.size() >= settings.nestMaxLoaded) return;
            Wolf w = d.wolf();
            if (w.isTamed() || d.isMale() || !d.isAdult() || d.isDying() || d.isFlying() || !allowed(d.species())) continue;
            if (!w.isOnGround() || w.isInWater() || w.getTarget() != null || w.isLeashed()) continue;
            PersistentDataContainer pdc = w.getPersistentDataContainer();
            if (pdc.getOrDefault(keys.nestCooldown, PersistentDataType.LONG, 0L) > now) continue;
            String guarding = pdc.get(keys.guarding, PersistentDataType.STRING);
            if (guarding != null) {
                try {
                    Entity e = Bukkit.getEntity(UUID.fromString(guarding));
                    if (e != null && e.isValid()) continue;
                } catch (IllegalArgumentException ignored) {
                    // uuid roto: arma otro
                }
            }
            if (r.nextDouble() >= settings.nestChance) continue;
            makeNest(d, 1 + r.nextInt(settings.nestMaxEggs));
        }
    }
}

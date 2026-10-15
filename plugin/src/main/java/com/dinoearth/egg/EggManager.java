package com.dinoearth.egg;

import com.dinoearth.Keys;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.model.DinoModel;
import com.dinoearth.model.DinoRig;
import com.dinoearth.species.Species;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Huevos puestos en el mundo. Cada huevo es una entidad Interaction (hitbox para
 * clickearlo, guarda los datos) + una item_display con el modelo. Cuando eclosiona
 * sale una cria de color y sexo al azar, domesticada por quien puso el huevo.
 */
public final class EggManager {

    private static final List<DinoRig.BoneDef> EGG_BONES = List.of(new DinoRig.BoneDef(0, "egg", -1, 0, 0, 0));

    private final Keys keys;
    private final DinoManager dinos;
    private final int hatchTicks;
    private final Map<UUID, Egg> eggs = new HashMap<>();

    private final class Egg {
        final Interaction box;
        final Species species;
        DinoModel model;
        int progress;
        int wobble = -1;
        boolean cracked;

        Egg(Interaction box, Species species) {
            this.box = box;
            this.species = species;
            this.progress = box.getPersistentDataContainer().getOrDefault(keys.eggProgress, PersistentDataType.INTEGER, 0);
            this.cracked = progress > hatchTicks * 0.75;
            build();
        }

        void build() {
            if (model != null) model.remove();
            DinoRig rig = species.rig;
            model = new DinoModel(EGG_BONES, new NamespacedKey[]{cracked ? rig.eggCracked() : rig.eggModel()},
                    box.getLocation(), null, new Vector(), 1f, 1f, keys::tagPart);
            pose();
        }

        void pose() {
            model.resetPose();
            if (wobble >= 0) {
                float w = (float) Math.sin(wobble * 0.9) * (1 - wobble / 14f);
                model.pose(0).rz = 14 * w;
                model.pose(0).rx = 6 * w;
            }
            model.applyPose(box.getLocation(), box.getLocation().getYaw(), 1f, 2);
        }
    }

    public EggManager(Keys keys, DinoManager dinos, int hatchTicks) {
        this.keys = keys;
        this.dinos = dinos;
        this.hatchTicks = Math.max(20, hatchTicks);
    }

    // ------------------------------------------------------------ item
    public ItemStack createItem(Species species, int amount) {
        ItemStack item = new ItemStack(Material.TURTLE_EGG, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(species.rig.eggIcon());
        meta.itemName(Component.text("Huevo de " + species.name, NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Ponelo en el suelo y esperá a que eclosione.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("La cría saldrá de un color al azar y será tuya.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setMaxStackSize(16);
        meta.getPersistentDataContainer().set(keys.eggItem, PersistentDataType.STRING, species.id);
        item.setItemMeta(meta);
        return item;
    }

    /** Especie de un item de huevo, o null si no es un huevo nuestro. */
    public Species eggSpecies(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(keys.eggItem, PersistentDataType.STRING)) {
            return Species.byId(pdc.get(keys.eggItem, PersistentDataType.STRING));
        }
        // huevos de la version 0.2 (solo existia el raptor)
        return pdc.has(keys.eggItem, PersistentDataType.BYTE) ? Species.RAPTOR : null;
    }

    // ------------------------------------------------------------ mundo
    public boolean isEgg(Entity e) {
        return e instanceof Interaction && e.getPersistentDataContainer().has(keys.egg);
    }

    private Species speciesOf(Interaction box) {
        Species s = Species.byId(box.getPersistentDataContainer().get(keys.eggSpecies, PersistentDataType.STRING));
        return s != null ? s : Species.RAPTOR;
    }

    /** Pone un huevo con la base en loc (centro del bloque). */
    public void place(Species species, Location loc, Player owner) {
        Location at = loc.clone();
        at.setYaw(ThreadLocalRandom.current().nextFloat() * 360f);
        at.setPitch(0);
        Interaction box = at.getWorld().spawn(at, Interaction.class, i -> {
            i.setInteractionWidth(0.55f);
            i.setInteractionHeight(0.7f);
            i.setResponsive(true);
            PersistentDataContainer pdc = i.getPersistentDataContainer();
            pdc.set(keys.egg, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keys.eggSpecies, PersistentDataType.STRING, species.id);
            pdc.set(keys.eggProgress, PersistentDataType.INTEGER, 0);
            if (owner != null) pdc.set(keys.eggOwner, PersistentDataType.STRING, owner.getUniqueId().toString());
        });
        attach(box);
        at.getWorld().playSound(at, "minecraft:entity.turtle.lay_egg", SoundCategory.BLOCKS, 1f, 0.9f);
    }

    public void attach(Interaction box) {
        if (!box.isValid() || eggs.containsKey(box.getUniqueId())) return;
        Species species = speciesOf(box);
        if (species.rig == null || !species.rig.hasEgg()) return;
        eggs.put(box.getUniqueId(), new Egg(box, species));
    }

    public void detach(Entity e) {
        Egg egg = eggs.remove(e.getUniqueId());
        if (egg != null) {
            save(egg);
            egg.model.remove();
        }
    }

    /** Un jugador rompe el huevo: lo devuelve como item. */
    public void breakEgg(Interaction box) {
        Egg egg = eggs.remove(box.getUniqueId());
        if (egg != null) egg.model.remove();
        Location at = box.getLocation();
        at.getWorld().dropItemNaturally(at.clone().add(0, 0.3, 0), createItem(speciesOf(box), 1));
        at.getWorld().playSound(at, "minecraft:entity.item.pickup", SoundCategory.BLOCKS, 0.8f, 0.8f);
        box.remove();
    }

    public int percent(Interaction box) {
        Egg egg = eggs.get(box.getUniqueId());
        int p = egg != null ? egg.progress : box.getPersistentDataContainer()
                .getOrDefault(keys.eggProgress, PersistentDataType.INTEGER, 0);
        return Math.min(100, p * 100 / hatchTicks);
    }

    public String speciesName(Interaction box) {
        return speciesOf(box).name;
    }

    public void attachLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (Interaction i : world.getEntitiesByClass(Interaction.class)) {
                if (isEgg(i)) attach(i);
            }
        }
    }

    public void tick() {
        Iterator<Egg> it = eggs.values().iterator();
        List<Egg> hatched = new ArrayList<>();
        while (it.hasNext()) {
            Egg egg = it.next();
            if (!egg.box.isValid()) {
                egg.model.remove();
                it.remove();
                continue;
            }
            if (!egg.model.isValid()) egg.build();
            egg.progress++;
            float f = egg.progress / (float) hatchTicks;

            if (!egg.cracked && f > 0.75f) {
                egg.cracked = true;
                egg.build();
                crackSound(egg);
            }
            // se mueve cada tanto, mas seguido cuanto mas cerca esta de eclosionar
            if (egg.wobble < 0 && f > 0.4f && ThreadLocalRandom.current().nextFloat() < (f - 0.35f) * 0.03f) {
                egg.wobble = 0;
                crackSound(egg);
            }
            if (egg.wobble >= 0) {
                egg.pose();
                if (++egg.wobble > 14) {
                    egg.wobble = -1;
                    egg.pose();
                }
            }
            if (egg.progress % 100 == 0) save(egg);
            if (egg.progress >= hatchTicks) {
                hatched.add(egg);
                it.remove();
            }
        }
        hatched.forEach(this::hatch);
    }

    private void crackSound(Egg egg) {
        egg.box.getWorld().playSound(egg.box.getLocation(), "dinoearth:egg.crack", SoundCategory.NEUTRAL, 0.8f,
                0.9f + ThreadLocalRandom.current().nextFloat() * 0.2f);
    }

    private void hatch(Egg egg) {
        Location at = egg.box.getLocation();
        String ownerId = egg.box.getPersistentDataContainer().get(keys.eggOwner, PersistentDataType.STRING);
        egg.model.remove();
        egg.box.remove();

        World world = at.getWorld();
        world.playSound(at, "dinoearth:egg.hatch", SoundCategory.NEUTRAL, 1f, 1f);
        world.spawnParticle(Particle.ITEM, at.clone().add(0, 0.35, 0), 24, 0.18, 0.2, 0.18, 0.08,
                new ItemStack(Material.TURTLE_EGG));
        world.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.5, 0), 8, 0.3, 0.3, 0.3);

        var owner = ownerId != null ? Bukkit.getOfflinePlayer(UUID.fromString(ownerId)) : null;
        Dino baby = dinos.spawn(egg.species, at, null, null, 0f, owner);
        Player online = owner != null ? owner.getPlayer() : null;
        if (baby != null && online != null) dinos.dex().addTamed(online, egg.species, baby.variant());
        if (baby != null && online != null && online.getWorld() == world && online.getLocation().distanceSquared(at) < 48 * 48) {
            String sexo = baby.isMale() ? "macho" : "hembra";
            online.sendActionBar(Component.text("¡Nació una cría de " + egg.species.name + " "
                    + egg.species.rig.variant(baby.variant()).name() + " (" + sexo + ")!", NamedTextColor.GREEN));
        }
    }

    private void save(Egg egg) {
        if (egg.box.isValid()) {
            egg.box.getPersistentDataContainer().set(keys.eggProgress, PersistentDataType.INTEGER, egg.progress);
        }
    }

    public void shutdown() {
        for (Egg egg : eggs.values()) {
            save(egg);
            egg.model.remove();
        }
        eggs.clear();
    }
}

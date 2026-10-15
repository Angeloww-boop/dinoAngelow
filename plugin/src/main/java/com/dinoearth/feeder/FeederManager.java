package com.dinoearth.feeder;

import com.dinoearth.Keys;
import com.dinoearth.Settings;
import com.dinoearth.model.DinoModel;
import com.dinoearth.model.DinoRig;
import com.dinoearth.species.Species;
import com.dinoearth.species.Species.Diet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comederos: una entidad Interaction (hitbox, guarda los datos) + una item_display con el modelo.
 * No tienen inventario: la comida que se les pone se consume en el momento y se convierte en
 * puntos (un numero en la entidad). Los dinos domesticados con hambre vienen a comer solos.
 * Asi no hay items guardados que se puedan duplicar.
 */
public final class FeederManager {

    private static final List<DinoRig.BoneDef> BONES = List.of(new DinoRig.BoneDef(0, "feeder", -1, 0, 0, 0));
    private static final String ADMIN = "dinoearth.admin";

    private final Keys keys;
    private final int capacity;
    private final Map<UUID, Feeder> feeders = new HashMap<>();

    /** Un comedero cargado en el mundo. */
    public final class Feeder {
        private final Interaction box;
        private final Diet diet;
        private int points;
        private int level = -1;
        private DinoModel model;

        private Feeder(Interaction box, Diet diet) {
            this.box = box;
            this.diet = diet;
            this.points = box.getPersistentDataContainer().getOrDefault(keys.feederPoints, PersistentDataType.INTEGER, 0);
            build();
        }

        private void build() {
            if (model != null) model.remove();
            level = level();
            model = new DinoModel(BONES, new NamespacedKey[]{modelKey(diet, level)}, box.getLocation(), null,
                    new Vector(), 1.5f, 1f, keys::tagPart);
            model.applyPose(box.getLocation(), box.getLocation().getYaw(), 1f, 0);
        }

        /** 0 = vacio, 1..3 = un tercio, dos tercios, lleno. */
        private int level() {
            if (points <= 0) return 0;
            float f = points / (float) capacity;
            return f < 1 / 3f ? 1 : f < 2 / 3f ? 2 : 3;
        }

        private void changed() {
            box.getPersistentDataContainer().set(keys.feederPoints, PersistentDataType.INTEGER, points);
            int l = level();
            if (l != level) {
                level = l;
                model.setItem(0, modelKey(diet, l));
            }
        }

        public Diet diet() {
            return diet;
        }

        public int points() {
            return points;
        }

        public Location location() {
            return box.getLocation();
        }

        public boolean isValid() {
            return box.isValid();
        }
    }

    public FeederManager(Keys keys, Settings settings) {
        this.keys = keys;
        this.capacity = settings.feederCapacity;
    }

    private static NamespacedKey modelKey(Diet diet, int level) {
        return NamespacedKey.fromString("dinoearth:feeder/" + diet.name().toLowerCase() + "_" + level + "/feeder");
    }

    // ------------------------------------------------------------ item
    public ItemStack createItem(Diet diet, int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(modelKey(diet, 3));
        meta.itemName(Component.text("Comedero de " + diet.label, NamedTextColor.GOLD));
        meta.lore(List.of(
                line("Ponelo en el suelo y llenalo con " + foodText(diet) + "."),
                line("Los dinos domesticados con hambre vienen a comer solos."),
                line("Agachado carga la pila entera. No se puede vaciar.")));
        meta.setMaxStackSize(16);
        meta.getPersistentDataContainer().set(keys.feederItem, PersistentDataType.STRING, diet.name());
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    public static String foodText(Diet diet) {
        return switch (diet) {
            case MEAT -> "carne cruda";
            case PLANTS -> "trigo, zanahoria, manzana, remolacha, bayas, sandía o fardos";
            case FISH -> "bacalao, salmón o pez tropical crudo";
        };
    }

    /** Dieta de un item de comedero, o null si no es uno nuestro. */
    public Diet itemDiet(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(keys.feederItem, PersistentDataType.STRING);
        return id != null ? Diet.byId(id) : null;
    }

    // ------------------------------------------------------------ mundo
    public boolean isFeeder(Entity e) {
        return e instanceof Interaction && e.getPersistentDataContainer().has(keys.feeder);
    }

    /** Pone un comedero con la base en loc (centro del bloque), alineado con donde mira el jugador. */
    public void place(Diet diet, Location loc, Player owner) {
        Location at = loc.clone();
        at.setYaw(Math.round(owner.getYaw() / 90f) * 90f);
        at.setPitch(0);
        Interaction box = at.getWorld().spawn(at, Interaction.class, i -> {
            i.setInteractionWidth(0.95f);
            i.setInteractionHeight(0.55f);
            i.setResponsive(true);
            PersistentDataContainer pdc = i.getPersistentDataContainer();
            pdc.set(keys.feeder, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keys.feederDiet, PersistentDataType.STRING, diet.name());
            pdc.set(keys.feederPoints, PersistentDataType.INTEGER, 0);
            pdc.set(keys.feederOwner, PersistentDataType.STRING, owner.getUniqueId().toString());
        });
        attach(box);
        at.getWorld().playSound(at, "minecraft:block.wood.place", SoundCategory.BLOCKS, 1f, 0.9f);
    }

    public void attach(Interaction box) {
        if (!box.isValid() || feeders.containsKey(box.getUniqueId())) return;
        Diet diet = Diet.byId(box.getPersistentDataContainer().getOrDefault(keys.feederDiet, PersistentDataType.STRING, ""));
        if (diet == null) return;
        feeders.put(box.getUniqueId(), new Feeder(box, diet));
    }

    public void detach(Entity e) {
        Feeder f = feeders.remove(e.getUniqueId());
        if (f != null) f.model.remove();
    }

    public void attachLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (Interaction i : world.getEntitiesByClass(Interaction.class)) {
                if (isFeeder(i)) attach(i);
            }
        }
    }

    /** Cada segundo: rearma el modelo si alguien borro las displays, suelta los que ya no existen. */
    public void tick() {
        Iterator<Feeder> it = feeders.values().iterator();
        while (it.hasNext()) {
            Feeder f = it.next();
            if (!f.box.isValid()) {
                f.model.remove();
                it.remove();
            } else if (!f.model.isValid()) {
                f.build();
            }
        }
    }

    public void shutdown() {
        feeders.values().forEach(f -> f.model.remove());
        feeders.clear();
    }

    // ------------------------------------------------------------ uso
    /** Click derecho: con su comida lo carga (agachado, la pila entera); con otra cosa muestra cuanto tiene. */
    public void interact(Player player, Interaction box, ItemStack hand) {
        Feeder f = feeders.get(box.getUniqueId());
        if (f == null) return;
        Material type = hand == null ? Material.AIR : hand.getType();
        int each = Species.foodPoints(type);
        if (Species.foodOf(f.diet).contains(type) && each > 0) {
            int room = (capacity - f.points) / each;
            int n = Math.min(room, player.isSneaking() ? hand.getAmount() : 1);
            if (n <= 0) {
                player.sendActionBar(Component.text("El comedero está lleno.", NamedTextColor.YELLOW));
                return;
            }
            f.points += n * each;
            f.changed();
            if (player.getGameMode() != GameMode.CREATIVE) hand.subtract(n);
            box.getWorld().playSound(box.getLocation(), "minecraft:block.composter.fill", SoundCategory.BLOCKS, 1f, 0.9f);
        } else if (each > 0) {
            player.sendActionBar(Component.text("Este comedero es de " + f.diet.label + ".", NamedTextColor.RED));
            return;
        }
        player.sendActionBar(Component.text("Comedero de " + f.diet.label + ": " + f.points + " / " + capacity,
                NamedTextColor.GOLD));
    }

    /** Lo rompe el dueño, un admin o alguien que puede construir ahi. Con comida adentro hay que estar agachado. */
    public void breakFeeder(Player player, Interaction box) {
        Feeder f = feeders.get(box.getUniqueId());
        String owner = box.getPersistentDataContainer().get(keys.feederOwner, PersistentDataType.STRING);
        boolean mine = player.getUniqueId().toString().equals(owner);
        if (!mine && !player.hasPermission(ADMIN) && !Protection.canBuild(player, box.getLocation().getBlock())) {
            player.sendActionBar(Component.text("Este comedero no es tuyo.", NamedTextColor.RED));
            return;
        }
        int points = f != null ? f.points : 0;
        if (points > 0 && !player.isSneaking()) {
            player.sendActionBar(Component.text("Tiene comida: agachate para romperlo (se pierde lo que tiene).",
                    NamedTextColor.YELLOW));
            return;
        }
        feeders.remove(box.getUniqueId());
        if (f != null) f.model.remove();
        Diet diet = f != null ? f.diet : Diet.byId(box.getPersistentDataContainer()
                .getOrDefault(keys.feederDiet, PersistentDataType.STRING, ""));
        Location at = box.getLocation();
        box.remove();
        if (diet != null) at.getWorld().dropItemNaturally(at.clone().add(0, 0.3, 0), createItem(diet, 1));
        at.getWorld().playSound(at, "minecraft:block.wood.break", SoundCategory.BLOCKS, 1f, 0.9f);
    }

    /** El comedero con comida de esa dieta mas cercano, o null. */
    public Feeder nearest(Location from, Diet diet, double radius) {
        Feeder best = null;
        double bestD = radius * radius;
        for (Feeder f : feeders.values()) {
            if (f.diet != diet || f.points <= 0 || !f.box.isValid()) continue;
            Location at = f.box.getLocation();
            if (at.getWorld() != from.getWorld()) continue;
            double d = at.distanceSquared(from);
            if (d < bestD) {
                best = f;
                bestD = d;
            }
        }
        return best;
    }

    /** Saca hasta wanted puntos. Devuelve cuantos saco. */
    public int take(Feeder f, int wanted) {
        int t = Math.max(0, Math.min(f.points, wanted));
        if (t > 0) {
            f.points -= t;
            f.changed();
        }
        return t;
    }
}

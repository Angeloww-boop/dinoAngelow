package com.dinoearth.order;

import com.dinoearth.Keys;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.item.DinoItems;
import com.dinoearth.registry.RecallManager;
import com.dinoearth.text.Fmt;
import com.dinoearth.text.Lang;
import com.dinoearth.text.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Silbato de ordenes. Click derecho a tu dino: menu para ese dino. Agachado y click derecho al aire:
 * el mismo menu para todos tus dinos a 16 bloques. Ordenes: seguirte, quedarse, patrullar el chunk
 * donde estas parado; y como pelea: pasivo, defensivo o agresivo. El menu sale de menus/silbato.yml.
 */
public final class OrderMenu implements Listener {

    private static final double GROUP_RADIUS = 16;
    /** id del item en el yml -> orden / modo que pone. */
    private static final Map<String, Dino.Order> ORDERS = Map.of(
            "seguir", Dino.Order.FOLLOW, "quedarse", Dino.Order.STAY, "patrullar", Dino.Order.PATROL);
    private static final Map<String, Dino.Combat> COMBATS = Map.of(
            "pasivo", Dino.Combat.PASSIVE, "defensivo", Dino.Combat.DEFENSIVE, "agresivo", Dino.Combat.AGGRESSIVE);

    private final DinoManager dinos;
    private final DinoItems items;
    private final Keys keys;
    private final RecallManager recall;

    /** Identifica nuestro menu (nunca por el titulo) y a que dinos se le dan las ordenes. */
    public static final class Holder implements InventoryHolder {
        private final List<UUID> targets;
        private final boolean group;
        private Inventory inventory;

        private Holder(List<UUID> targets, boolean group) {
            this.targets = targets;
            this.group = group;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public OrderMenu(DinoManager dinos, DinoItems items, Keys keys, RecallManager recall) {
        this.recall = recall;
        this.dinos = dinos;
        this.items = items;
        this.keys = keys;
        recall.setWhistleBack(this::reopen);
    }

    // ------------------------------------------------------------ usar el silbato
    @EventHandler(priority = EventPriority.LOW)
    public void onWhistleDino(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!items.is(player.getInventory().getItemInMainHand(), DinoItems.Kind.WHISTLE)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Dino d = event.getRightClicked() instanceof Wolf w ? dinos.get(w) : null;
        if (d == null) return;
        if (!d.wolf().isTamed() || !player.getUniqueId().equals(d.wolf().getOwnerUniqueId())) {
            Lang.get().action(player, "silbato-no-tuyo");
            return;
        }
        whistle(player);
        open(player, List.of(d.wolf().getUniqueId()), false);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWhistleAir(PlayerInteractEvent event) {
        if (!items.is(event.getItem(), DinoItems.Kind.WHISTLE)) return;
        // montado en un Plus, el click derecho es su habilidad (PlusAbilities)
        Entity vehicle = event.getPlayer().getVehicle();
        Dino riding = vehicle != null ? dinos.get(vehicle) : null;
        if (riding != null && riding.species().isPlus()) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            Lang.get().action(player, "silbato-uso");
            return;
        }
        List<UUID> mine = near(player);
        whistle(player);
        if (mine.isEmpty()) {
            // ninguno cerca: directo al menu de llamarlos de regreso
            recall.openMenu(player, false);
            return;
        }
        open(player, mine, true);
    }

    private List<UUID> near(Player player) {
        List<UUID> mine = new ArrayList<>();
        for (Dino d : dinos.all()) {
            if (owned(player, d) && d.wolf().getLocation().distanceSquared(player.getLocation()) <= GROUP_RADIUS * GROUP_RADIUS) {
                mine.add(d.wolf().getUniqueId());
            }
        }
        return mine;
    }

    /** "Volver" desde el menu de llamar: el menu de grupo con los que tenga cerca (o nada). */
    private void reopen(Player p) {
        List<UUID> mine = near(p);
        if (!mine.isEmpty()) open(p, mine, true);
    }

    private static void whistle(Player p) {
        p.getWorld().playSound(p.getLocation(), "minecraft:entity.breeze.idle_ground", SoundCategory.PLAYERS, 0.6f, 1.8f);
    }

    private boolean owned(Player p, Dino d) {
        return !d.isDying() && !d.isRental() && d.wolf().isValid() && d.wolf().isTamed() && d.wolf().getWorld() == p.getWorld()
                && p.getUniqueId().equals(d.wolf().getOwnerUniqueId());
    }

    private List<Dino> resolve(Player p, Holder h) {
        List<Dino> out = new ArrayList<>();
        for (UUID id : h.targets) {
            Entity e = Bukkit.getEntity(id);
            Dino d = e == null ? null : dinos.get(e);
            if (d != null && owned(p, d)) out.add(d);
        }
        return out;
    }

    // ------------------------------------------------------------ menu
    private void open(Player p, List<UUID> targets, boolean group) {
        Holder h = new Holder(targets, group);
        List<Dino> list = resolve(p, h);
        if (list.isEmpty()) return;
        Menu menu = Lang.get().menu("silbato");
        Inventory inv = Bukkit.createInventory(h, menu.size(), menu.title());
        h.inventory = inv;
        fill(p, menu, inv, list, group);
        p.openInventory(inv);
    }

    private void fill(Player p, Menu menu, Inventory inv, List<Dino> list, boolean group) {
        inv.clear();
        Set<String> dynamic = Set.of("dino", "grupo", "seguir", "quedarse", "patrullar", "pasivo", "defensivo",
                "agresivo", "llamar");
        menu.fill(inv, dynamic, this::tag);
        Dino first = list.get(0);
        Dino.Order order = all(list, d -> d.order() == first.order()) ? first.order() : null;
        Dino.Combat combat = all(list, d -> d.combat() == first.combat()) ? first.combat() : null;

        if (group) {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < Math.min(8, list.size()); i++) {
                lines.add(Lang.apply(menu.text("linea-grupo"), "dino", label(list.get(i))));
            }
            if (list.size() > 8) lines.add(menu.text("linea-grupo-mas", "cantidad", list.size() - 8));
            put(inv, menu, "grupo", menu.item("grupo", "cantidad", list.size(), "lista", lines));
        } else {
            var max = first.wolf().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            String owner = first.ownerName();
            List<String> hunger = first.tracksHunger()
                    ? List.of(menu.text("linea-hambre", "hambre", Math.round(first.hunger() * 100))) : List.of();
            put(inv, menu, "dino", menu.item("dino", "dino", label(first), "especie", first.species().name,
                    "color", first.species().colorName(first.variant()), "sexo", Fmt.sex(first.isMale()),
                    "dueno", owner != null ? owner : "?", "vida", Math.round(first.wolf().getHealth()),
                    "vida_max", max != null ? Math.round(max.getValue()) : "?", "hambre", hunger));
        }

        String chunk = (p.getLocation().getBlockX() >> 4) + ", " + (p.getLocation().getBlockZ() >> 4);
        List<String> patrolNow = !group && first.patrolLabel() != null
                ? List.of(menu.text("linea-patrulla", "chunk", first.patrolLabel())) : List.of();
        for (Map.Entry<String, Dino.Order> e : ORDERS.entrySet()) {
            boolean on = order == e.getValue();
            put(inv, menu, e.getKey(), state(menu.item(e.getKey(), "chunk", chunk, "patrulla_actual", patrolNow,
                    "estado", menu.text(on ? "activo" : "elegir")), on));
        }
        for (Map.Entry<String, Dino.Combat> e : COMBATS.entrySet()) {
            boolean on = combat == e.getValue();
            put(inv, menu, e.getKey(), state(menu.item(e.getKey(), "estado", menu.text(on ? "activo" : "elegir")), on));
        }
        put(inv, menu, "llamar", menu.item("llamar", "max", dinos.settings().recallMax));
    }

    private static ItemStack state(ItemStack item, boolean on) {
        if (on) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void put(Inventory inv, Menu menu, String id, ItemStack item) {
        tag(item);
        int slot = menu.slot(id);
        if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item);
    }

    private void tag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keys.menuIcon, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getClickedInventory() != event.getInventory()) return;
        int slot = event.getSlot();
        Menu menu = Lang.get().menu("silbato");
        if (slot == menu.slot("cerrar")) {
            p.closeInventory();
            return;
        }
        if (slot == menu.slot("llamar")) {
            p.closeInventory();
            recall.openMenu(p, true);
            return;
        }
        List<Dino> list = resolve(p, h);
        if (list.isEmpty()) {
            p.closeInventory();
            return;
        }
        String key = null;
        for (var e : ORDERS.entrySet()) {
            if (slot == menu.slot(e.getKey())) {
                list.forEach(d -> d.setOrder(e.getValue(), p));
                key = e.getValue().name();
            }
        }
        for (var e : COMBATS.entrySet()) {
            if (slot == menu.slot(e.getKey())) {
                list.forEach(d -> d.setCombat(e.getValue()));
                key = e.getValue().name();
            }
        }
        if (key == null) return;
        Lang lang = Lang.get();
        List<String> said = lang.list("textos.yml", "orden-hecha." + key);
        String action = said.size() > 1 && list.size() > 1 ? said.get(1) : said.isEmpty() ? "" : said.get(0);
        Component who = list.size() == 1 ? label(list.get(0)) : Component.text(lang.plain("varios-dinos", "cantidad", list.size()));
        lang.action(p, "orden", "dinos", who, "accion", action);
        p.playSound(p.getLocation(), "minecraft:ui.button.click", SoundCategory.PLAYERS, 0.5f, 1.4f);
        fill(p, menu, event.getInventory(), list, h.group);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private static boolean all(List<Dino> list, Predicate<Dino> test) {
        return list.stream().allMatch(test);
    }

    /** Nombre del dino: el de la etiqueta o el de su especie. */
    public static Component label(Dino d) {
        return d.name() != null ? d.name() : Component.text(d.species().name);
    }
}

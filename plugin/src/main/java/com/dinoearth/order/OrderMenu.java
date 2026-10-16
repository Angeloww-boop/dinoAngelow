package com.dinoearth.order;

import com.dinoearth.Keys;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.item.DinoItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Silbato de ordenes. Click derecho a tu dino: menu para ese dino. Agachado y click derecho al aire:
 * el mismo menu para todos tus dinos a 16 bloques. Ordenes: seguirte, quedarse, patrullar el chunk
 * donde estas parado; y como pelea: pasivo, defensivo o agresivo.
 */
public final class OrderMenu implements Listener {

    private static final int HEADER = 4, FOLLOW = 10, STAY = 11, PATROL = 12, PASSIVE = 14, DEFENSIVE = 15,
            AGGRESSIVE = 16, CLOSE = 22;
    private static final double GROUP_RADIUS = 16;

    private final DinoManager dinos;
    private final DinoItems items;
    private final Keys keys;

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

    public OrderMenu(DinoManager dinos, DinoItems items, Keys keys) {
        this.dinos = dinos;
        this.items = items;
        this.keys = keys;
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
            player.sendActionBar(Component.text("Ese dino no es tuyo.", NamedTextColor.RED));
            return;
        }
        whistle(player);
        open(player, List.of(d.wolf().getUniqueId()), false);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWhistleAir(PlayerInteractEvent event) {
        if (!items.is(event.getItem(), DinoItems.Kind.WHISTLE)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            player.sendActionBar(Component.text("Click derecho a tu dino, o agachado para darle órdenes a todos los cercanos.",
                    NamedTextColor.GRAY));
            return;
        }
        List<UUID> mine = new ArrayList<>();
        for (Dino d : dinos.all()) {
            if (owned(player, d) && d.wolf().getLocation().distanceSquared(player.getLocation()) <= GROUP_RADIUS * GROUP_RADIUS) {
                mine.add(d.wolf().getUniqueId());
            }
        }
        if (mine.isEmpty()) {
            player.sendActionBar(Component.text("No tenés dinos a menos de 16 bloques.", NamedTextColor.RED));
            return;
        }
        whistle(player);
        open(player, mine, true);
    }

    private static void whistle(Player p) {
        p.getWorld().playSound(p.getLocation(), "minecraft:entity.breeze.idle_ground", SoundCategory.PLAYERS, 0.6f, 1.8f);
    }

    private boolean owned(Player p, Dino d) {
        return !d.isDying() && d.wolf().isValid() && d.wolf().isTamed() && d.wolf().getWorld() == p.getWorld()
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
        Component title = group ? Component.text("Órdenes · " + list.size() + " dinos")
                : Component.text("Órdenes · ").append(label(list.get(0)));
        Inventory inv = Bukkit.createInventory(h, 27, title);
        h.inventory = inv;
        fill(p, inv, list, group);
        p.openInventory(inv);
    }

    private void fill(Player p, Inventory inv, List<Dino> list, boolean group) {
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        Dino first = list.get(0);
        Dino.Order order = all(list, d -> d.order() == first.order()) ? first.order() : null;
        Dino.Combat combat = all(list, d -> d.combat() == first.combat()) ? first.combat() : null;

        List<Component> info = new ArrayList<>();
        if (group) {
            for (int i = 0; i < Math.min(8, list.size()); i++) info.add(line(Component.text("· ").append(label(list.get(i)))));
            if (list.size() > 8) info.add(line(Component.text("y " + (list.size() - 8) + " más")));
        } else {
            info.add(line(Component.text(first.species().name + " " + first.species().rig.variant(first.variant()).name()
                    + " · " + (first.isMale() ? "macho" : "hembra") + (first.isAdult() ? "" : " · cría"))));
            String owner = first.ownerName();
            if (owner != null) info.add(line(Component.text("Dueño: " + owner)));
            var max = first.wolf().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            info.add(line(Component.text("Vida: " + Math.round(first.wolf().getHealth()) + " / "
                    + (max != null ? Math.round(max.getValue()) : "?"))));
            if (first.tracksHunger()) info.add(line(Component.text("Hambre: " + Math.round(first.hunger() * 100) + "%")));
        }
        inv.setItem(HEADER, icon(Material.NAME_TAG, group ? Component.text("Tus dinos cerca: " + list.size(), NamedTextColor.GOLD)
                : label(first).color(NamedTextColor.GOLD), info, false));

        String chunk = (p.getLocation().getBlockX() >> 4) + ", " + (p.getLocation().getBlockZ() >> 4);
        String patrolNow = !group && first.patrolLabel() != null ? "Ahora: " + first.patrolLabel() : null;
        inv.setItem(FOLLOW, icon(Material.LEAD, title("Seguirme", order == Dino.Order.FOLLOW),
                lines("Te sigue a donde vayas."), order == Dino.Order.FOLLOW));
        inv.setItem(STAY, icon(Material.RED_BED, title("Quedarse acá", order == Dino.Order.STAY),
                lines("Se sienta y espera."), order == Dino.Order.STAY));
        List<Component> patrol = new ArrayList<>(lines("Patrulla el chunk donde estás parado",
                "(chunk " + chunk + ") y no se va de ahí."));
        if (patrolNow != null) patrol.add(line(Component.text(patrolNow, NamedTextColor.YELLOW)));
        inv.setItem(PATROL, icon(Material.FILLED_MAP, title("Patrullar este chunk", order == Dino.Order.PATROL),
                patrol, order == Dino.Order.PATROL));
        inv.setItem(PASSIVE, icon(Material.POPPY, title("Pasivo", combat == Dino.Combat.PASSIVE),
                lines("No ataca a nadie, ni aunque lo ataquen."), combat == Dino.Combat.PASSIVE));
        inv.setItem(DEFENSIVE, icon(Material.SHIELD, title("Defensivo", combat == Dino.Combat.DEFENSIVE),
                lines("Te defiende a vos y a sí mismo.", "(lo normal)"), combat == Dino.Combat.DEFENSIVE));
        inv.setItem(AGGRESSIVE, icon(Material.IRON_SWORD, title("Agresivo", combat == Dino.Combat.AGGRESSIVE),
                lines("Además ataca a los monstruos", "que vea cerca (menos creepers)."), combat == Dino.Combat.AGGRESSIVE));
        inv.setItem(CLOSE, icon(Material.BARRIER, Component.text("Cerrar", NamedTextColor.RED), List.of(), false));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getClickedInventory() != event.getInventory()) return;
        int slot = event.getSlot();
        if (slot == CLOSE) {
            p.closeInventory();
            return;
        }
        List<Dino> list = resolve(p, h);
        if (list.isEmpty()) {
            p.closeInventory();
            return;
        }
        String one, many;
        switch (slot) {
            case FOLLOW -> {
                list.forEach(d -> d.setOrder(Dino.Order.FOLLOW, p));
                one = "te sigue";
                many = "te siguen";
            }
            case STAY -> {
                list.forEach(d -> d.setOrder(Dino.Order.STAY, p));
                one = "se queda acá";
                many = "se quedan acá";
            }
            case PATROL -> {
                list.forEach(d -> d.setOrder(Dino.Order.PATROL, p));
                one = "patrulla este chunk";
                many = "patrullan este chunk";
            }
            case PASSIVE -> {
                list.forEach(d -> d.setCombat(Dino.Combat.PASSIVE));
                one = "está en modo pasivo";
                many = "están en modo pasivo";
            }
            case DEFENSIVE -> {
                list.forEach(d -> d.setCombat(Dino.Combat.DEFENSIVE));
                one = "está en modo defensivo";
                many = "están en modo defensivo";
            }
            case AGGRESSIVE -> {
                list.forEach(d -> d.setCombat(Dino.Combat.AGGRESSIVE));
                one = "está en modo agresivo";
                many = "están en modo agresivo";
            }
            default -> {
                return;
            }
        }
        Component who = list.size() == 1 ? label(list.get(0)) : Component.text(list.size() + " dinos");
        p.sendActionBar(who.append(Component.text(" " + (list.size() == 1 ? one : many), NamedTextColor.GREEN)));
        p.playSound(p.getLocation(), "minecraft:ui.button.click", SoundCategory.PLAYERS, 0.5f, 1.4f);
        fill(p, event.getInventory(), list, h.group);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private static boolean all(List<Dino> list, Predicate<Dino> test) {
        return list.stream().allMatch(test);
    }

    // ------------------------------------------------------------ iconos
    public static Component label(Dino d) {
        return d.name() != null ? d.name() : Component.text(d.species().name);
    }

    private static Component title(String text, boolean on) {
        return Component.text((on ? "» " : "") + text, on ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> lines(String... text) {
        List<Component> out = new ArrayList<>();
        for (String t : text) out.add(line(Component.text(t)));
        return out;
    }

    private static Component line(Component c) {
        return c.colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack icon(Material material, Component name, List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(glow);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(keys.menuIcon, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}

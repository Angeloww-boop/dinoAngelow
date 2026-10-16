package com.dinoearth.item;

import com.dinoearth.text.Lang;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import com.dinoearth.dino.DinoManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Que los items propios no se usen como el item de vanilla en el que estan basados (papel, palo,
 * montura): no entran en crafteos, yunques, hornos ni dispensers, y la montura de un dino no se le
 * pone a un caballo. Los crafteos de verdad los arma el dueño del server con Shopkeepers.
 * Tambien suelta la jaula llena al usarla contra el piso.
 */
public final class ItemListener implements Listener {

    private final DinoItems items;
    private final CageManager cages;
    private final DinoManager dinos;

    public ItemListener(DinoItems items, CageManager cages, DinoManager dinos) {
        this.items = items;
        this.cages = cages;
        this.dinos = dinos;
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack i : event.getInventory().getMatrix()) {
            if (items.isOurs(i)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onPrepare(PrepareResultEvent event) {
        for (ItemStack i : event.getInventory().getContents()) {
            if (items.isOurs(i)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFuel(FurnaceBurnEvent event) {
        if (items.isOurs(event.getFuel())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (items.isOurs(event.getItem())) event.setCancelled(true);
    }

    /** La montura de un dino no sirve para caballos, cerdos, etc. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onUseOnMob(PlayerInteractEntityEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (items.is(hand, DinoItems.Kind.SADDLE) && !dinos.isDino(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!items.is(item, DinoItems.Kind.CAGE)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) return;
        if (cages.isFull(item)) {
            cages.release(event.getPlayer(), item, event.getClickedBlock(), event.getBlockFace());
        } else {
            Lang.get().action(event.getPlayer(), "jaula-uso");
        }
    }
}

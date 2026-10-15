package com.dinoearth.feeder;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Pregunta a los plugins de proteccion (Towny, WorldGuard, ...) si el jugador puede construir en un bloque,
 * sin depender de ninguno: se dispara un BlockPlaceEvent de prueba y se mira si alguien lo cancelo.
 * No se coloca nada.
 */
public final class Protection {

    private Protection() {
    }

    public static boolean canBuild(Player player, Block block) {
        BlockPlaceEvent probe = new BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN),
                new ItemStack(Material.BARREL), player, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(probe);
        return !probe.isCancelled() && probe.canBuild();
    }
}

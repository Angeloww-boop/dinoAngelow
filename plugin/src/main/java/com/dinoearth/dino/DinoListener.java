package com.dinoearth.dino;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.dinoearth.Keys;
import com.dinoearth.egg.EggManager;
import com.dinoearth.species.Species;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DinoListener implements Listener {

    private static final float FEED_GROWTH = 0.05f;

    private final Plugin plugin;
    private final DinoManager dinos;
    private final EggManager eggs;
    private final Keys keys;
    /** Ultimo tick en que manejamos un click con la mano principal (para ignorar el de la mano secundaria). */
    private final Map<UUID, Integer> handled = new HashMap<>();

    public DinoListener(Plugin plugin, DinoManager dinos, EggManager eggs, Keys keys) {
        this.plugin = plugin;
        this.dinos = dinos;
        this.eggs = eggs;
        this.keys = keys;
    }

    // ------------------------------------------------------------ carga / descarga
    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        Entity e = event.getEntity();
        if (keys.isStalePart(e)) {
            Bukkit.getScheduler().runTask(plugin, e::remove);
        } else if (dinos.isDino(e)) {
            Bukkit.getScheduler().runTask(plugin, () -> dinos.attach((Wolf) e));
        } else if (eggs.isEgg(e)) {
            Bukkit.getScheduler().runTask(plugin, () -> eggs.attach((Interaction) e));
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        Entity e = event.getEntity();
        if (dinos.isDino(e)) dinos.detach(e);
        else if (eggs.isEgg(e)) eggs.detach(e);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // si no, el dino se guardaria "adentro" del jugador al desconectarse
        Player p = event.getPlayer();
        if (p.getVehicle() != null && dinos.isDino(p.getVehicle())) p.leaveVehicle();
    }

    // ------------------------------------------------------------ combate
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Dino d = dinos.get(event.getEntity());
        if (d != null) d.onHurt();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Dino d = dinos.get(event.getDamager());
        if (d != null) {
            d.onAttack();
            return;
        }
        // el jinete pega: el dino muerde/embiste tambien
        if (event.getDamager() instanceof Player p && p.getVehicle() != null) {
            Dino mount = dinos.get(p.getVehicle());
            if (mount != null && event.getEntity() != mount.wolf()) {
                mount.onAttack();
                event.setDamage(event.getDamage() + 3);
            }
        }
    }

    /** Escupitajo del dilophosaurus: ciega y envenena. */
    @EventHandler
    public void onSpitHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        if (!proj.getPersistentDataContainer().has(keys.venom)) return;
        if (event.getHitEntity() instanceof LivingEntity le && le != proj.getShooter()) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 70, 0));
            le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
            le.getWorld().spawnParticle(Particle.ITEM_SLIME, le.getEyeLocation(), 10, 0.25, 0.2, 0.25, 0.05);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf)) return;
        Species species = dinos.speciesOf(wolf);
        if (species == null) return;
        Dino d = dinos.get(wolf);
        event.getDrops().clear();
        if (d != null && d.isSaddled()) event.getDrops().add(new ItemStack(Material.SADDLE));
        float size = d != null ? Math.max(0.3f, d.growth()) : 1f;
        for (Species.Drop drop : species.drops) {
            int n = ThreadLocalRandom.current().nextInt(drop.min(), drop.max() + 1);
            n = Math.round(n * size);
            if (n > 0) event.getDrops().add(new ItemStack(drop.item(), n));
        }
        event.setDroppedExp((int) Math.round(species.health / 5 * size));
        dinos.onDeath(wolf);
    }

    // ------------------------------------------------------------ interaccion con el dino
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (eggs.isEgg(event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                Interaction box = (Interaction) event.getRightClicked();
                player.sendActionBar(Component.text("Huevo de " + eggs.speciesName(box) + ": "
                        + eggs.percent(box) + "% listo", NamedTextColor.GOLD));
            }
            return;
        }
        if (!(event.getRightClicked() instanceof Wolf wolf)) return;
        Dino d = dinos.get(wolf);
        if (d == null) return;
        Species species = d.species();

        int now = Bukkit.getCurrentTick();
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            if (!wolf.isTamed() || handled.getOrDefault(player.getUniqueId(), -1) == now) event.setCancelled(true);
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Material type = hand.getType();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        boolean food = species.food().contains(type);
        Location fx = wolf.getLocation().add(0, wolf.getHeight() + 0.3, 0);

        if (!species.tameable()) {
            event.setCancelled(true);   // los hostiles no se domestican (ni con hueso)
            return;
        }

        if (!wolf.isTamed()) {
            // salvaje: el hueso no sirve, se doma con su comida
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            if (species.eggTameOnly) {
                player.sendActionBar(Component.text("Un " + species.name + " salvaje no se domestica: criá uno desde el huevo.",
                        NamedTextColor.RED));
                return;
            }
            if (!food || wolf.isAngry()) return;
            if (!creative) hand.subtract();
            if (ThreadLocalRandom.current().nextDouble() < species.tameChance) {
                wolf.setOwner(player);
                wolf.setTarget(null);
                wolf.setSitting(true);
                d.healFully();
                wolf.getWorld().spawnParticle(Particle.HEART, fx, 7, 0.5, 0.4, 0.5);
                d.onTamed();
            } else {
                wolf.getWorld().spawnParticle(Particle.SMOKE, fx, 7, 0.4, 0.3, 0.4, 0.02);
            }
            return;
        }

        boolean owner = player.getUniqueId().equals(wolf.getOwnerUniqueId());
        if (type == Material.WOLF_ARMOR || type.name().endsWith("_DYE") || type == Material.BONE) {
            event.setCancelled(true);
            return;
        }
        if (!owner) return;

        if (type == Material.SADDLE && species.rideable) {
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            if (!d.isAdult()) {
                player.sendActionBar(Component.text("Todavía es muy chico para montarlo.", NamedTextColor.RED));
            } else if (!d.isSaddled()) {
                d.setSaddled(true);
                if (!creative) hand.subtract();
                wolf.getWorld().playSound(wolf.getLocation(), "minecraft:entity.horse.saddle", SoundCategory.NEUTRAL, 1f, 1f);
            }
            return;
        }
        if (type == Material.SHEARS && d.isSaddled()) {
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            d.setSaddled(false);
            wolf.getWorld().dropItemNaturally(wolf.getLocation().add(0, 1, 0), new ItemStack(Material.SADDLE));
            wolf.getWorld().playSound(wolf.getLocation(), "minecraft:entity.sheep.shear", SoundCategory.NEUTRAL, 1f, 1f);
            return;
        }
        if (food && !d.isAdult()) {
            // alimentar a la cria la hace crecer
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            if (!creative) hand.subtract();
            d.addGrowth(FEED_GROWTH);
            wolf.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, fx, 8, 0.4, 0.3, 0.4);
            wolf.getWorld().playSound(wolf.getLocation(), "minecraft:entity.generic.eat", SoundCategory.NEUTRAL, 0.8f, 1.3f);
            player.sendActionBar(Component.text("Crecimiento: " + Math.round(d.growth() * 100) + "%", NamedTextColor.GREEN));
            return;
        }
        if (species.food() != Species.MEAT) {
            // el lobo solo entiende la carne: a los que comen otra cosa los curamos y ponemos en celo nosotros
            if (species.temper == Species.Temper.HERBIVORE && Species.MEAT.contains(type)) {
                event.setCancelled(true);
                return;
            }
            if (food) {
                event.setCancelled(true);
                handled.put(player.getUniqueId(), now);
                if (d.isHurt()) {
                    d.heal(6);
                } else if (wolf.getAge() == 0 && !wolf.isLoveMode()) {
                    wolf.setLoveModeTicks(600);
                    wolf.getWorld().spawnParticle(Particle.HEART, fx, 5, 0.5, 0.3, 0.5);
                } else {
                    return;
                }
                if (!creative) hand.subtract();
                wolf.getWorld().playSound(wolf.getLocation(), "minecraft:entity.generic.eat", SoundCategory.NEUTRAL, 0.9f, 0.8f);
                return;
            }
        }
        if (d.canRide() && !player.isSneaking() && !food && d.rider() == null) {
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            d.mount(player);
        }
        // si no, comportamiento de lobo: sentarse/pararse, curarse con carne, entrar en celo
    }

    // ------------------------------------------------------------ cria
    @EventHandler
    public void onLove(EntityEnterLoveModeEvent event) {
        Dino d = dinos.get(event.getEntity());
        if (d != null && (!d.isAdult() || !d.species().rig.hasEgg())) event.setCancelled(true);
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        Species ma = dinos.speciesOf(event.getMother());
        Species pa = dinos.speciesOf(event.getFather());
        if (ma == null && pa == null) return;
        // nunca nace un lobo comun: en su lugar la hembra pone un huevo
        event.setCancelled(true);
        int cooldown = dinos.settings().breedCooldownTicks;
        for (Entity e : new Entity[]{event.getMother(), event.getFather()}) {
            if (e instanceof Wolf w) {
                w.setLoveModeTicks(0);
                w.setAge(cooldown);
            }
        }
        Dino a = dinos.get(event.getMother());
        Dino b = dinos.get(event.getFather());
        if (a == null || b == null || ma != pa || a.isMale() == b.isMale() || !ma.rig.hasEgg()) return;
        Dino female = a.isMale() ? b : a;
        Wolf w = female.wolf();
        w.getWorld().spawnParticle(Particle.HEART, w.getLocation().add(0, w.getHeight(), 0), 6, 0.6, 0.3, 0.6);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!w.isValid()) return;
            Location at = w.getLocation();
            w.getWorld().playSound(at, "minecraft:entity.turtle.lay_egg", SoundCategory.NEUTRAL, 1f, 0.8f);
            w.getWorld().dropItemNaturally(at.add(0, 0.4, 0), eggs.createItem(ma, 1));
        }, 60L);
    }

    // ------------------------------------------------------------ huevos
    @EventHandler(priority = EventPriority.HIGH)
    public void onUseEgg(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        Species species = eggs.eggSpecies(item);
        if (species == null) return;
        event.setCancelled(true);   // que no se coloque como huevo de tortuga
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        Block below = target.getRelative(BlockFace.DOWN);
        if (!target.isPassable() || target.isLiquid() || !below.getType().isSolid()) return;
        Location at = target.getLocation().add(0.5, 0, 0.5);
        if (!at.getNearbyEntitiesByType(Interaction.class, 0.45).stream().filter(eggs::isEgg).toList().isEmpty()) return;
        eggs.place(species, at, event.getPlayer());
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) item.subtract();
        event.getPlayer().swingHand(event.getHand() != null ? event.getHand() : EquipmentSlot.HAND);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceEgg(BlockPlaceEvent event) {
        if (eggs.eggSpecies(event.getItemInHand()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreakEgg(PrePlayerAttackEntityEvent event) {
        if (eggs.isEgg(event.getAttacked())) {
            event.setCancelled(true);
            if (event.getPlayer().getGameMode() != GameMode.ADVENTURE) eggs.breakEgg((Interaction) event.getAttacked());
        }
    }
}

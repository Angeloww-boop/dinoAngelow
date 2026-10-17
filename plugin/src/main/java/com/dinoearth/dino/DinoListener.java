package com.dinoearth.dino;

import com.dinoearth.text.Fmt;
import com.dinoearth.text.Lang;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.dinoearth.Keys;
import com.dinoearth.dex.DexManager;
import com.dinoearth.egg.EggManager;
import com.dinoearth.feeder.FeederManager;
import com.dinoearth.feeder.Protection;
import com.dinoearth.item.CageManager;
import com.dinoearth.item.DinoItems;
import com.dinoearth.species.Species;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
    private final FeederManager feeders;
    private final DexManager dex;
    private final Keys keys;
    private final DinoItems items;
    private final CageManager cages;
    private final com.dinoearth.nest.NestManager nests;
    /** Ultimo tick en que manejamos un click con la mano principal (para ignorar el de la mano secundaria). */
    private final Map<UUID, Integer> handled = new HashMap<>();

    public DinoListener(Plugin plugin, DinoManager dinos, EggManager eggs, Keys keys, DinoItems items, CageManager cages,
                        com.dinoearth.nest.NestManager nests) {
        this.plugin = plugin;
        this.nests = nests;
        this.items = items;
        this.cages = cages;
        this.dinos = dinos;
        this.eggs = eggs;
        this.feeders = dinos.feeders();
        this.dex = dinos.dex();
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
        } else if (feeders.isFeeder(e)) {
            Bukkit.getScheduler().runTask(plugin, () -> feeders.attach((Interaction) e));
        } else if (nests.isNest(e)) {
            Bukkit.getScheduler().runTask(plugin, () -> nests.attach((Interaction) e));
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        Entity e = event.getEntity();
        if (dinos.isDino(e)) dinos.detach(e);
        else if (eggs.isEgg(e)) eggs.detach(e);
        else if (feeders.isFeeder(e)) feeders.detach(e);
        else if (nests.isNest(e)) nests.detach(e);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // si no, el dino se guardaria "adentro" del jugador al desconectarse
        Player p = event.getPlayer();
        if (p.getVehicle() != null && dinos.isDino(p.getVehicle())) p.leaveVehicle();
        Dino escort = dinos.escortOf(p);
        if (escort != null) escort.stopEscort();
    }

    // ------------------------------------------------------------ combate
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Dino d = dinos.get(event.getEntity());
        if (d != null) d.onHurt(event);
    }

    /** Los que nadan no se ahogan (el lobo de base si se ahogaria). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrown(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.DROWNING) return;
        Species s = dinos.speciesOf(event.getEntity());
        if (s != null && s.swims) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Dino victim = dinos.get(event.getEntity());
        if (victim != null) {
            victim.onStruck(event);
            if (event.isCancelled()) return;
        }
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
        if (d != null && d.isSaddled()) event.getDrops().add(items.saddleFor(species));
        float size = d != null ? Math.max(0.3f, d.growth()) : 1f;
        event.getDrops().addAll(items.rollDrops(species, size));
        for (Species.Drop drop : species.drops) {
            int n = ThreadLocalRandom.current().nextInt(drop.min(), drop.max() + 1);
            n = Math.round(n * size);
            if (n > 0) event.getDrops().add(new ItemStack(drop.item(), n));
        }
        event.setDroppedExp((int) Math.round(species.health / 5 * size));
        Player killer = wolf.getKiller();
        if (killer != null && !killer.getUniqueId().equals(wolf.getOwnerUniqueId())) {
            dex.onKill(killer, species);
            com.dinoearth.stats.PlayerStats.get().add(killer, "cazados", 1);
            var missions = com.dinoearth.mission.MissionManager.get();
            if (missions != null) missions.onKill(killer, species);
        }
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
                Lang.get().action(player, "huevo", "especie", eggs.speciesName(box), "porcentaje", eggs.percent(box));
            }
            return;
        }
        if (nests.isNest(event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) nests.inspect(player, (Interaction) event.getRightClicked());
            return;
        }
        if (feeders.isFeeder(event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                feeders.interact(player, (Interaction) event.getRightClicked(), player.getInventory().getItemInMainHand());
            }
            return;
        }
        if (!(event.getRightClicked() instanceof Wolf wolf)) return;
        Dino d = dinos.get(wolf);
        if (d == null) return;
        if (d.isRental()) {
            // dino de una pista de carreras: no se toca (ni jaula, ni nombre, ni montura, ni comida)
            event.setCancelled(true);
            return;
        }
        Species species = d.species();

        // con la Dinopedia en la mano: ficha del dino
        if (dex.isBook(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                handled.put(player.getUniqueId(), Bukkit.getCurrentTick());
                dex.showCard(player, d);
            }
            return;
        }

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
                Lang.get().action(player, "salvaje-solo-huevo", "especie", species.name);
                return;
            }
            if (!food || wolf.isAngry()) return;
            if (!dinos.canOwnMore(player.getUniqueId())) {
                dinos.limitMessage(player);
                return;
            }
            if (!creative) hand.subtract();
            if (ThreadLocalRandom.current().nextDouble() < species.tameChance) {
                wolf.setOwner(player);
                wolf.setTarget(null);
                wolf.setSitting(true);
                d.healFully();
                d.setHunger(1f);
                wolf.getWorld().spawnParticle(Particle.HEART, fx, 7, 0.5, 0.4, 0.5);
                d.onTamed();
                if (dinos.registry() != null) dinos.registry().update(d);
                dex.addTamed(player, species, d.variant());
                com.dinoearth.stats.PlayerStats.get().add(player, "domesticados", 1);
                var missions = com.dinoearth.mission.MissionManager.get();
                if (missions != null) missions.onTame(player, species);
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
        if (type == Material.NAME_TAG) {
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            if (!owner) {
                Lang.get().action(player, "nombre-solo-dueno");
                return;
            }
            var meta = hand.getItemMeta();
            if (meta == null || !meta.hasDisplayName() || meta.displayName() == null) {
                Lang.get().action(player, "nombre-etiqueta");
                return;
            }
            d.setName(meta.displayName());
            if (!creative) hand.subtract();
            wolf.getWorld().playSound(wolf.getLocation(), "minecraft:entity.villager.yes", SoundCategory.NEUTRAL, 0.6f, 1.3f);
            Lang.get().action(player, "nombre-hecho", "dino", d.name());
            return;
        }
        if (items.is(hand, DinoItems.Kind.CAGE)) {
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            if (!owner) {
                Lang.get().action(player, "jaula-solo-propios");
                return;
            }
            cages.capture(player, d, hand);
            return;
        }
        if (food && d.wantsFood()) {
            // con hambre: comer primero (cualquiera le puede dar de comer)
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            if (!creative) hand.subtract();
            d.feed(Species.foodPoints(type));
            d.heal(2);
            if (!d.isAdult()) d.addGrowth(FEED_GROWTH);
            wolf.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, fx, 6, 0.4, 0.3, 0.4);
            wolf.getWorld().playSound(wolf.getLocation(), "minecraft:entity.generic.eat", SoundCategory.NEUTRAL, 0.9f, 0.9f);
            Lang.get().action(player, "hambre", "porcentaje", Math.round(d.hunger() * 100));
            return;
        }
        if (!owner) return;
        if (d.isStarving() && type != Material.SHEARS) {
            // muerto de hambre no se para ni se deja montar
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            Lang.get().action(player, "muerto-de-hambre", "especie", species.name, "dieta", Fmt.diet(species.diet()));
            return;
        }

        DinoItems.Def held = items.defOf(hand);
        boolean saddleItem = held != null && held.kind() == DinoItems.Kind.SADDLE;
        if ((saddleItem || type == Material.SADDLE) && species.rideable) {
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            boolean fits = saddleItem && (species.id.equals(held.species())
                    || species.plusOf != null && species.plusOf.id.equals(held.species()));
            if (!fits) {
                Lang.get().action(player, "montura-necesita", "montura", items.plainName(items.saddleId(species)));
                return;
            }
            if (!d.isAdult()) {
                Lang.get().action(player, "montura-chico");
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
            wolf.getWorld().dropItemNaturally(wolf.getLocation().add(0, 1, 0), items.saddleFor(species));
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
            Lang.get().action(player, "crecimiento", "porcentaje", Math.round(d.growth() * 100));
            return;
        }
        if (species.food() != Species.MEAT) {
            // el lobo solo entiende la carne: a los que comen otra cosa los curamos y ponemos en celo nosotros
            if (species.herbivore() && Species.MEAT.contains(type)) {
                event.setCancelled(true);
                return;
            }
            if (food) {
                event.setCancelled(true);
                handled.put(player.getUniqueId(), now);
                if (d.isHurt()) {
                    d.heal(6);
                } else if (wolf.getAge() == 0 && !wolf.isLoveMode() && !(d.tracksHunger() && d.hunger() < 0.3f)) {
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
        if (species.perches && player.isSneaking() && type == Material.AIR && d.isAdult()
                && player.getUniqueId().equals(wolf.getOwnerUniqueId())) {
            // agachado y con la mano vacia: vuela a tu lado (o baja y camina)
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            if (!d.isEscorting(player)) {
                Dino other = dinos.escortOf(player);
                if (other != null) other.toggleEscort(player);
                if (d.order() != Dino.Order.FOLLOW) d.setOrder(Dino.Order.FOLLOW, player);
                dex.onEscort(player, species);
            }
            d.toggleEscort(player);
            Lang.get().action(player, d.isEscorting(player) ? "escolta-vuela" : "escolta-camina", "especie", species.name);
            return;
        }
        if (d.canRide() && !player.isSneaking() && !food && d.rider() == null) {
            event.setCancelled(true);
            handled.put(player.getUniqueId(), now);
            d.mount(player);
            dex.onRide(player, species);
        }
        // si no, comportamiento de lobo: sentarse/pararse, curarse con carne, entrar en celo
    }

    // ------------------------------------------------------------ cria
    @EventHandler
    public void onLove(EntityEnterLoveModeEvent event) {
        Dino d = dinos.get(event.getEntity());
        if (d != null && (!d.isAdult() || !d.species().rig.hasEgg() || d.tracksHunger() && d.hunger() < 0.3f)) {
            event.setCancelled(true);
        }
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
        Dino male = a.isMale() ? a : b;
        // la cria se decide ahora: color de uno de los padres (o mutado) y rasgos mezclados
        String[] color = Genes.childColor(ma, female.variant(), male.variant(), dinos.settings());
        Genes genes = Genes.child(female.genes(), male.genes(), female.asParent(), male.asParent(),
                "1".equals(color[1]), dinos.settings());
        Wolf w = female.wolf();
        w.getWorld().spawnParticle(Particle.HEART, w.getLocation().add(0, w.getHeight(), 0), 6, 0.6, 0.3, 0.6);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!w.isValid()) return;
            Location at = w.getLocation();
            w.getWorld().playSound(at, "minecraft:entity.turtle.lay_egg", SoundCategory.NEUTRAL, 1f, 0.8f);
            w.getWorld().dropItemNaturally(at.add(0, 0.4, 0), eggs.createItem(ma, 1, genes, color[0]));
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
        eggs.place(species, at, event.getPlayer(), item);
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
        } else if (nests.isNest(event.getAttacked())) {
            event.setCancelled(true);
            nests.take(event.getPlayer(), (Interaction) event.getAttacked());
        } else if (feeders.isFeeder(event.getAttacked())) {
            event.setCancelled(true);
            if (event.getPlayer().getGameMode() != GameMode.ADVENTURE) {
                feeders.breakFeeder(event.getPlayer(), (Interaction) event.getAttacked());
            }
        }
    }

    // ------------------------------------------------------------ comederos y dinopedia
    /** Agachado + click izquierdo: el que vuela a tu lado baja y camina. */
    @EventHandler
    public void onPunchAir(PlayerInteractEvent event) {
        Action a = event.getAction();
        if ((a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) || !event.getPlayer().isSneaking()) return;
        Dino escort = dinos.escortOf(event.getPlayer());
        if (escort != null) escort.toggleEscort(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseItem(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        // el click a un dino ya se manejo en este tick (el cliente igual manda "usar item")
        if (handled.getOrDefault(player.getUniqueId(), -1) == Bukkit.getCurrentTick()) return;
        if (dex.isBook(item)) {
            event.setCancelled(true);
            dex.openIndex(player);
            return;
        }
        Species.Diet diet = feeders.itemDiet(item);
        if (diet == null) return;
        event.setCancelled(true);
        if (action != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        Block below = target.getRelative(BlockFace.DOWN);
        if (!target.isPassable() || target.isLiquid() || !below.getType().isSolid()) return;
        Location at = target.getLocation().add(0.5, 0, 0.5);
        boolean taken = at.getNearbyEntitiesByType(Interaction.class, 0.6).stream()
                .anyMatch(i -> feeders.isFeeder(i) || eggs.isEgg(i) || nests.isNest(i));
        if (taken) return;
        if (!Protection.canBuild(player, target)) {
            Lang.get().action(player, "comedero-aca");
            return;
        }
        feeders.place(diet, at, player);
        if (player.getGameMode() != GameMode.CREATIVE) item.subtract();
        player.swingHand(EquipmentSlot.HAND);
    }

    /** Los menus de la Dinopedia son de solo lectura: se cancela todo click (arriba y abajo). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DexManager.Menu menu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p && event.getClickedInventory() == event.getView().getTopInventory()) {
                Runnable open = dex.click(p, menu, event.getRawSlot());
                if (open != null) Bukkit.getScheduler().runTask(plugin, open);
            }
            return;
        }
        // por las dudas: un icono de la Dinopedia fuera del menu se borra
        if (dex.isIcon(event.getCurrentItem())) {
            event.setCancelled(true);
            event.setCurrentItem(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DexManager.Menu) event.setCancelled(true);
    }
}

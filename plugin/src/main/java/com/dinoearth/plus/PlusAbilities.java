package com.dinoearth.plus;

import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.feeder.Protection;
import com.dinoearth.species.Species;
import com.dinoearth.text.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Habilidades de la linea Plus.
 * <ul>
 *   <li>Activas, montado: click derecho con cualquier item en la mano (el click derecho al aire con la mano vacia no
 *       le llega al server) o tecla de cambiar de mano (F). Raptor Sombra = dash; Tricera = cosecha alrededor;
 *       Spino = pesca ya; Para = canto (velocidad y regeneracion); Ptera = turbo de vuelo / despegue alto;
 *       Galli = arranque; Anky = tala el arbol de adelante.</li>
 *   <li>Pasivas (dueño conectado): Tricera quieto o patrullando cosecha y replanta (a un cofre); montado o
 *       siguiendo al dueño es un tractor: cosecha y replanta en 3x3 por donde pasa y lo manda al inventario del
 *       jinete (o del dueño); Spino cerca del agua pesca; Dimorphodon junta los items del suelo alrededor del dueño.</li>
 * </ul>
 * Todo lo que rompe o cosecha respeta Towny/WorldGuard (Protection.canBuild con el dueño). Tiempos en config plus.*.
 */
public final class PlusAbilities implements Listener {

    private static final Set<Material> CROPS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.SWEET_BERRY_BUSH);

    private final JavaPlugin plugin;
    private final DinoManager dinos;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<UUID, Long> nextPassive = new HashMap<>();
    // tractor: cuanto lleva cosechado cada triceratops con el jinete (o dueño) actual
    private final Map<UUID, UUID> tractorWho = new HashMap<>();
    private final Map<UUID, Integer> tractorCount = new HashMap<>();
    private long tick, tractorTick;

    // config
    private final Map<Species.Ability, Integer> cooldownSeconds = new HashMap<>();
    private int harvestRadius, harvestActiveRadius, harvestEvery, fishEvery, magnetRadius, maxLogs;
    private int tractorRadius, tractorEvery, tractorFollowDistance;
    private double dashSpeed, sprintBoost, flyBoost;

    public PlusAbilities(JavaPlugin plugin, DinoManager dinos) {
        this.plugin = plugin;
        this.dinos = dinos;
        loadConfig();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tractor, 4L, 1L);
    }

    public void loadConfig() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("plus");
        if (c == null) c = new YamlConfiguration();
        for (Species.Ability a : Species.Ability.values()) {
            cooldownSeconds.put(a, Math.max(1, c.getInt("cooldowns." + a.name().toLowerCase(), switch (a) {
                case DASH -> 8;
                case COSECHA -> 20;
                case PESCA -> 60;
                case CANTO -> 60;
                case TURBO -> 10;
                case ARRANQUE -> 30;
                case TALA -> 12;
                case BUSCADOR -> 1;
            })));
        }
        dashSpeed = c.getDouble("dash.speed", 1.3);
        sprintBoost = c.getDouble("arranque.multiplier", 1.6);
        flyBoost = c.getDouble("turbo.multiplier", 1.8);
        harvestRadius = Math.max(1, Math.min(8, c.getInt("cosecha.radius", 3)));
        harvestActiveRadius = Math.max(1, Math.min(10, c.getInt("cosecha.active-radius", 5)));
        harvestEvery = Math.max(1, c.getInt("cosecha.every-seconds", 3));
        fishEvery = Math.max(5, c.getInt("pesca.every-seconds", 45));
        magnetRadius = Math.max(1, Math.min(12, c.getInt("buscador.radius", 6)));
        maxLogs = Math.max(8, Math.min(256, c.getInt("tala.max-blocks", 96)));
        tractorRadius = Math.max(0, Math.min(3, c.getInt("cosecha.tractor-radius", 1)));
        tractorEvery = Math.max(1, Math.min(40, c.getInt("cosecha.tractor-every-ticks", 4)));
        tractorFollowDistance = Math.max(0, c.getInt("cosecha.tractor-follow-distance", 24));
    }

    // ------------------------------------------------------------ activar montado
    private Dino ridden(Player p) {
        Entity v = p.getVehicle();
        Dino d = v == null ? null : dinos.get(v);
        return d != null && d.species().isPlus() && !d.isRental() ? d : null;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onRightClick(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Dino d = ridden(event.getPlayer());
        if (d == null) return;
        // la Dinopedia se sigue abriendo normal
        ItemStack item = event.getItem();
        if (item != null && dinos.dex().isBook(item)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        use(event.getPlayer(), d);
    }

    /** El tractor no pisotea la tierra arada (ni montado ni caminando). */
    @EventHandler(ignoreCancelled = true)
    public void onTrample(EntityInteractEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;
        Dino d = dinos.get(event.getEntity());
        if (d != null && d.species().ability == Species.Ability.COSECHA) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Dino d = ridden(event.getPlayer());
        if (d == null) return;
        event.setCancelled(true);
        use(event.getPlayer(), d);
    }

    private void use(Player p, Dino d) {
        Species.Ability ability = d.species().ability;
        if (ability == null || ability == Species.Ability.BUSCADOR) return;
        Lang lang = Lang.get();
        long now = System.currentTimeMillis();
        long ready = cooldown.getOrDefault(d.wolf().getUniqueId(), 0L);
        if (now < ready) {
            lang.action(p, "plus-espera", "segundos", (ready - now + 999) / 1000);
            return;
        }
        boolean done = switch (ability) {
            case DASH -> {
                d.dash(dashSpeed, 6);
                effect(d, Particle.CLOUD, "minecraft:entity.breeze.wind_burst", 1.3f);
                yield true;
            }
            case ARRANQUE -> {
                d.boost(sprintBoost, 100);
                effect(d, Particle.CLOUD, "minecraft:entity.horse.gallop", 1.2f);
                yield true;
            }
            case TURBO -> {
                if (d.isFlying()) d.boost(flyBoost, 60);
                else d.launch();
                effect(d, Particle.CLOUD, "minecraft:entity.phantom.flap", 0.8f);
                yield true;
            }
            case CANTO -> sing(p, d);
            case COSECHA -> {
                int n = harvest(p, d, harvestActiveRadius, p);
                lang.action(p, n > 0 ? "plus-cosecha" : "plus-sin-cultivos", "cantidad", n);
                yield n > 0;
            }
            case PESCA -> {
                if (!nearWater(d.wolf().getLocation())) {
                    lang.action(p, "plus-sin-agua");
                    yield false;
                }
                fish(p, d);
                yield true;
            }
            case TALA -> chop(p, d);
            case BUSCADOR -> false;
        };
        if (done) {
            cooldown.put(d.wolf().getUniqueId(), now + cooldownSeconds.get(ability) * 1000L);
            lang.action(p, "plus-usada", "habilidad", lang.plain("plus.habilidades." + ability.name()));
        }
    }

    private static void effect(Dino d, Particle particle, String sound, float pitch) {
        Location at = d.wolf().getLocation().add(0, 0.6, 0);
        at.getWorld().spawnParticle(particle, at, 14, 0.6, 0.3, 0.6, 0.05);
        at.getWorld().playSound(at, sound, SoundCategory.NEUTRAL, 0.8f, pitch);
    }

    // ------------------------------------------------------------ pasivas
    private void tick() {
        tick++;
        long now = System.currentTimeMillis();
        for (Dino d : dinos.all()) {
            Species s = d.species();
            if (!s.isPlus() || d.isDying() || d.isRental() || !d.wolf().isTamed()) continue;
            Player owner = d.wolf().getOwner() instanceof Player o && o.isOnline() ? o : null;
            if (owner == null || owner.getWorld() != d.wolf().getWorld()) continue;
            UUID id = d.wolf().getUniqueId();
            switch (s.ability) {
                case COSECHA -> {
                    // trabajando: quieto (sentado) o patrullando, sin jinete
                    if (d.rider() != null || d.order() == Dino.Order.FOLLOW) continue;
                    if (now < nextPassive.getOrDefault(id, 0L)) continue;
                    nextPassive.put(id, now + harvestEvery * 1000L);
                    harvest(owner, d, harvestRadius, null);
                }
                case PESCA -> {
                    if (d.rider() != null || !nearWater(d.wolf().getLocation())) continue;
                    if (owner.getLocation().distanceSquared(d.wolf().getLocation()) > 48 * 48) continue;
                    if (!nextPassive.containsKey(id)) nextPassive.put(id, now + fishEvery * 1000L);
                    if (now < nextPassive.get(id)) continue;
                    nextPassive.put(id, now + fishEvery * 1000L);
                    fish(owner, d);
                }
                case BUSCADOR -> magnet(owner, d);
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------ cosecha (triceratops)
    // ------------------------------------------------------------ tractor (triceratops montado o siguiendo)
    /**
     * Cada tractor-every-ticks: el triceratops Plus montado (o siguiendo a su dueño cerca) cosecha y replanta en un
     * cuadrado de (2 x tractor-radius + 1) alrededor de sus patas, y lo cosechado va al inventario del jinete (lo que
     * no entra cae al piso). En la barra de accion cuenta lo cosechado desde que se subio.
     */
    private void tractor() {
        if (++tractorTick % tractorEvery != 0) return;
        for (Dino d : dinos.all()) {
            Species s = d.species();
            if (!s.isPlus() || s.ability != Species.Ability.COSECHA) continue;
            if (d.isDying() || d.isRental() || !d.wolf().isTamed() || !d.isAdult()) continue;
            Player to = d.rider();
            if (to == null) {
                if (d.order() != Dino.Order.FOLLOW) continue;   // sentado o patrullando: lo hace tick() (al cofre)
                Player owner = d.wolf().getOwner() instanceof Player o && o.isOnline() ? o : null;
                if (owner == null || owner.getWorld() != d.wolf().getWorld()) continue;
                if (owner.getLocation().distanceSquared(d.wolf().getLocation())
                        > (double) tractorFollowDistance * tractorFollowDistance) continue;
                to = owner;
            }
            UUID id = d.wolf().getUniqueId();
            if (!to.getUniqueId().equals(tractorWho.get(id))) {
                tractorWho.put(id, to.getUniqueId());
                tractorCount.put(id, 0);
            }
            int n = harvest(to, d, tractorRadius, to);
            if (n > 0) {
                int total = tractorCount.merge(id, n, Integer::sum);
                if (d.rider() != null) Lang.get().action(to, "plus-cosecha", "cantidad", total);
            }
        }
    }

    /**
     * Cosecha los cultivos maduros alrededor y los replanta. Lo cosechado va al inventario de {@code into} (lo que no
     * entra cae al piso) o, si es null, al cofre o barril mas cerca.
     */
    private int harvest(Player owner, Dino d, int radius, Player into) {
        Location at = d.wolf().getLocation();
        World w = at.getWorld();
        List<ItemStack> loot = new ArrayList<>();
        int n = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block b = w.getBlockAt(at.getBlockX() + dx, at.getBlockY() + dy, at.getBlockZ() + dz);
                    if (!CROPS.contains(b.getType()) || !(b.getBlockData() instanceof Ageable age)) continue;
                    if (age.getAge() < age.getMaximumAge()) continue;
                    if (!Protection.canBuild(owner, b)) continue;
                    Collection<ItemStack> drops = b.getDrops();
                    // los arbustos de bayas quedan como al cosecharlos a mano; lo demas se replanta
                    age.setAge(b.getType() == Material.SWEET_BERRY_BUSH ? 1 : 0);
                    b.setBlockData(age);
                    removeSeed(drops, b.getType());
                    loot.addAll(drops);
                    w.spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.3, 0.2, 0.3);
                    n++;
                }
            }
        }
        if (n > 0) {
            if (into != null) give(into, at, loot);
            else deliver(owner, at, loot);
            w.playSound(at, "minecraft:item.crop.plant", SoundCategory.BLOCKS, 0.8f, 1f);
        }
        return n;
    }

    /** Lo que se replanta sale de la cosecha (una semilla menos). */
    private static void removeSeed(Collection<ItemStack> drops, Material crop) {
        Material seed = switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
        if (seed == null) return;
        for (ItemStack it : drops) {
            if (it.getType() == seed && it.getAmount() > 0) {
                it.setAmount(it.getAmount() - 1);
                return;
            }
        }
    }

    /** Al inventario del jugador; lo que no entra, al piso. */
    private static void give(Player p, Location at, List<ItemStack> items) {
        for (ItemStack it : items) {
            if (it == null || it.getAmount() <= 0) continue;
            for (ItemStack rest : p.getInventory().addItem(it).values()) at.getWorld().dropItemNaturally(at, rest);
        }
    }

    /** A un cofre o barril a 6 bloques (donde el dueño puede construir); si no entra, al piso. */
    private void deliver(Player owner, Location at, List<ItemStack> items) {
        items.removeIf(i -> i == null || i.getAmount() <= 0);
        if (items.isEmpty()) return;
        Container box = nearestContainer(owner, at);
        List<ItemStack> rest = new ArrayList<>();
        if (box != null) {
            for (ItemStack it : items) rest.addAll(box.getInventory().addItem(it).values());
        } else {
            rest = items;
        }
        for (ItemStack it : rest) at.getWorld().dropItemNaturally(at, it);
    }

    private static Container nearestContainer(Player owner, Location at) {
        Container best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    Block b = at.getWorld().getBlockAt(at.getBlockX() + dx, at.getBlockY() + dy, at.getBlockZ() + dz);
                    Material m = b.getType();
                    if (m != Material.CHEST && m != Material.BARREL && m != Material.TRAPPED_CHEST) continue;
                    if (!(b.getState() instanceof Container c) || !Protection.canBuild(owner, b)) continue;
                    double dist = dx * dx + dy * dy + dz * dz;
                    if (dist < bd) {
                        bd = dist;
                        best = c;
                    }
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------ pesca (spinosaurus)
    private static boolean nearWater(Location at) {
        World w = at.getWorld();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 0; dy++) {
                    if (w.getBlockAt(at.getBlockX() + dx, at.getBlockY() + dy, at.getBlockZ() + dz).getType() == Material.WATER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void fish(Player owner, Dino d) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int roll = r.nextInt(100);
        ItemStack catchItem;
        if (roll < 50) catchItem = new ItemStack(Material.COD);
        else if (roll < 75) catchItem = new ItemStack(Material.SALMON);
        else if (roll < 81) catchItem = new ItemStack(Material.TROPICAL_FISH);
        else if (roll < 87) catchItem = new ItemStack(Material.PUFFERFISH);
        else if (roll < 97) {
            Material[] junk = {Material.BONE, Material.STRING, Material.LEATHER, Material.STICK, Material.INK_SAC,
                    Material.LILY_PAD, Material.BOWL};
            catchItem = new ItemStack(junk[r.nextInt(junk.length)]);
        } else {
            Material[] treasure = {Material.NAUTILUS_SHELL, Material.NAME_TAG, Material.SADDLE, Material.BOW,
                    Material.FISHING_ROD};
            catchItem = new ItemStack(treasure[r.nextInt(treasure.length)]);
        }
        Location at = d.wolf().getLocation();
        at.getWorld().spawnParticle(Particle.SPLASH, at.clone().add(0, 0.5, 0), 20, 0.6, 0.2, 0.6);
        at.getWorld().playSound(at, "minecraft:entity.fishing_bobber.splash", SoundCategory.NEUTRAL, 0.8f, 1f);
        for (ItemStack rest : owner.getInventory().addItem(catchItem).values()) at.getWorld().dropItemNaturally(at, rest);
        Lang.get().action(owner, "plus-pesca", "especie", d.species().name,
                "item", catchItem.getType().name().toLowerCase().replace('_', ' '));
    }

    // ------------------------------------------------------------ iman (dimorphodon)
    private void magnet(Player owner, Dino d) {
        if (owner.getLocation().distanceSquared(d.wolf().getLocation()) > 20 * 20) return;
        for (Item item : owner.getLocation().getNearbyEntitiesByType(Item.class, magnetRadius)) {
            if (!item.isValid() || item.getPickupDelay() > 0) continue;
            UUID thrower = item.getThrower();
            if (thrower != null && !thrower.equals(owner.getUniqueId())) continue;   // lo tiro otro: no se lo robamos
            UUID only = item.getOwner();
            if (only != null && !only.equals(owner.getUniqueId())) continue;
            ItemStack stack = item.getItemStack();
            var rest = owner.getInventory().addItem(stack.clone());
            if (rest.isEmpty()) {
                owner.getWorld().spawnParticle(Particle.END_ROD, item.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
                item.remove();
            } else {
                item.setItemStack(rest.values().iterator().next());
            }
        }
    }

    // ------------------------------------------------------------ canto (parasaurolophus)
    private boolean sing(Player p, Dino d) {
        Location at = d.wolf().getLocation();
        at.getWorld().playSound(at, "dinoearth:" + d.species().soundId + ".roar", SoundCategory.NEUTRAL, 1.2f, 1.1f);
        at.getWorld().spawnParticle(Particle.NOTE, at.clone().add(0, 2.5, 0), 12, 1.2, 0.6, 1.2, 1);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, true, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0, true, true, true));
        for (Dino o : dinos.all()) {
            if (!o.wolf().isTamed() || !p.getUniqueId().equals(o.wolf().getOwnerUniqueId())) continue;
            if (o.wolf().getWorld() != at.getWorld() || o.wolf().getLocation().distanceSquared(at) > 16 * 16) continue;
            o.heal(6);
        }
        return true;
    }

    // ------------------------------------------------------------ tala (ankylosaurus)
    /**
     * Busca un tronco adelante; si hay, arranca la animacion (se para de manos y embiste) y el arbol cae justo en el
     * golpe (Dino.CHOP_IMPACT ticks despues).
     */
    private boolean chop(Player p, Dino d) {
        Block start = treeAhead(d);
        if (start == null) {
            Lang.get().action(p, "plus-sin-arbol");
            return false;
        }
        if (!Protection.canBuild(p, start)) {
            Lang.get().action(p, "plus-sin-permiso");
            return false;
        }
        d.chop();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.isDying() || !Tag.LOGS.isTagged(start.getType())) return;
            fell(p, d, start);
        }, Dino.CHOP_IMPACT);
        return true;
    }

    private static Block treeAhead(Dino d) {
        Location at = d.wolf().getLocation();
        double yaw = Math.toRadians(d.wolf().getBodyYaw());
        Block start = null;
        for (double dist = 1; dist <= 3.5 && start == null; dist += 0.5) {
            for (int dy = 0; dy <= 3 && start == null; dy++) {
                Block b = at.getWorld().getBlockAt((int) Math.floor(at.getX() - Math.sin(yaw) * dist),
                        at.getBlockY() + dy, (int) Math.floor(at.getZ() + Math.cos(yaw) * dist));
                if (Tag.LOGS.isTagged(b.getType())) start = b;
            }
        }
        return start;
    }

    /** Tira el arbol desde start (troncos conectados, hasta max-blocks) donde el jinete puede construir. */
    private boolean fell(Player p, Dino d, Block start) {
        Location at = d.wolf().getLocation();
        // el golpe: astillas del tronco y un ruido seco antes de que caiga
        at.getWorld().spawnParticle(Particle.BLOCK, start.getLocation().add(0.5, 0.8, 0.5), 40, 0.4, 0.6, 0.4,
                start.getBlockData());
        at.getWorld().playSound(start.getLocation(), "minecraft:entity.zombie.attack_wooden_door", SoundCategory.BLOCKS,
                1f, 0.8f);
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        int broken = 0;
        while (!queue.isEmpty() && broken < maxLogs) {
            Block b = queue.poll();
            if (!Protection.canBuild(p, b)) continue;
            b.breakNaturally();
            broken++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = b.getRelative(dx, dy, dz);
                        if (Tag.LOGS.isTagged(n.getType()) && seen.add(n)) queue.add(n);
                    }
                }
            }
        }
        if (broken == 0) {
            Lang.get().action(p, "plus-sin-permiso");
            return false;
        }
        at.getWorld().playSound(start.getLocation(), "minecraft:block.wood.break", SoundCategory.BLOCKS, 1f, 0.7f);
        start.getRelative(BlockFace.UP).getWorld().spawnParticle(Particle.CLOUD, start.getLocation().add(0.5, 1, 0.5), 10,
                0.5, 0.5, 0.5, 0.02);
        Lang.get().action(p, "plus-tala", "cantidad", broken);
        return true;
    }
}

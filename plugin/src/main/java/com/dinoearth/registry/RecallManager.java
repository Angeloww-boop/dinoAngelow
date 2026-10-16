package com.dinoearth.registry;

import com.dinoearth.Keys;
import com.dinoearth.Settings;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.order.OrderMenu;
import com.dinoearth.species.Species;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "No perder a tus dinos": /dino mis (lista con coordenadas), el silbato de regreso y los avisos
 * cuando atacan o matan a un dino tuyo (si no estabas conectado, te llegan al entrar).
 *
 * <p>Para traer un dino lejano se carga SOLO el chunk donde quedo (con un ticket del plugin), se
 * espera a que aparezca, se lo trae a tu lado y se suelta el ticket: como mucho un chunk por dino,
 * un momento. Hay un maximo de dinos por llamada y una espera entre llamadas (config.yml).
 */
public final class RecallManager implements Listener {

    private static final double HERE = 16;          // a esta distancia ya "esta con vos"
    private static final int FIND_TICKS = 200;      // cuanto se espera a que aparezca al cargar su chunk
    private static final int CONFIRM = 49, INFO = 45, CLOSE = 53;

    private final Plugin plugin;
    private final DinoManager dinos;
    private final DinoRegistry registry;
    private final Keys keys;
    private final Settings settings;
    private final Map<UUID, Long> lastCall = new HashMap<>();
    private final Map<String, Integer> tickets = new HashMap<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();

    /** Menu de elegir a quien llamar (se reconoce por el holder, nunca por el titulo). */
    public static final class Holder implements InventoryHolder {
        private final List<UUID> ids = new ArrayList<>();
        private final Set<UUID> chosen = new LinkedHashSet<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public RecallManager(Plugin plugin, DinoManager dinos, DinoRegistry registry, Keys keys, Settings settings) {
        this.plugin = plugin;
        this.dinos = dinos;
        this.registry = registry;
        this.keys = keys;
        this.settings = settings;
    }

    // ------------------------------------------------------------ /dino mis
    public void list(Player p) {
        List<DinoRegistry.Entry> mine = sorted(p);
        if (mine.isEmpty()) {
            p.sendMessage(Component.text("No tenés dinos domesticados.", NamedTextColor.GRAY));
            return;
        }
        p.sendMessage(Component.text("── Tus dinos (" + mine.size() + ") ──", NamedTextColor.GOLD));
        for (DinoRegistry.Entry e : mine) {
            Component line = Component.text("• ", NamedTextColor.DARK_GRAY).append(title(e).color(NamedTextColor.WHITE))
                    .append(Component.text(" — " + where(p, e) + " · " + orderLabel(e.order()), NamedTextColor.GRAY));
            if (!isHere(p, e)) {
                line = line.append(Component.text(" [Llamar]", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("Traerlo a tu lado")))
                        .clickEvent(ClickEvent.runCommand("/dino llamar " + shortId(e.id()))));
            }
            p.sendMessage(line);
        }
        long wait = cooldownLeft(p);
        p.sendMessage(Component.text(wait > 0 ? "Podés volver a llamar en " + time(wait) + "."
                : "Podés llamar hasta " + settings.recallMax + " dinos a la vez (cada " + time(settings.recallCooldownMs)
                + ").", NamedTextColor.DARK_GRAY));
    }

    /** /dino llamar <id> [id]: ids cortos que muestra /dino mis. */
    public void recallById(Player p, List<String> shortIds) {
        List<UUID> ids = new ArrayList<>();
        for (DinoRegistry.Entry e : registry.ownedBy(p.getUniqueId())) {
            for (String s : shortIds) {
                if (shortId(e.id()).equalsIgnoreCase(s)) ids.add(e.id());
            }
        }
        if (ids.isEmpty()) {
            p.sendMessage(Component.text("No encontré ese dino. Mirá la lista con /dino mis.", NamedTextColor.RED));
            return;
        }
        recall(p, ids);
    }

    // ------------------------------------------------------------ silbato de regreso
    public void openMenu(Player p) {
        List<DinoRegistry.Entry> far = sorted(p).stream().filter(e -> !isHere(p, e)).toList();
        if (far.isEmpty()) {
            p.sendActionBar(Component.text("Todos tus dinos están con vos.", NamedTextColor.GRAY));
            return;
        }
        Holder h = new Holder();
        for (int i = 0; i < Math.min(45, far.size()); i++) h.ids.add(far.get(i).id());
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Llamar de regreso"));
        h.inventory = inv;
        draw(p, h);
        p.openInventory(inv);
    }

    private void draw(Player p, Holder h) {
        Inventory inv = h.inventory;
        inv.clear();
        for (int i = 0; i < h.ids.size(); i++) {
            DinoRegistry.Entry e = registry.get(h.ids.get(i));
            if (e == null) continue;
            boolean on = h.chosen.contains(e.id());
            List<Component> lore = new ArrayList<>();
            lore.add(line(where(p, e)));
            lore.add(line(orderLabel(e.order())));
            lore.add(line(Bukkit.getEntity(e.id()) != null ? "Cargado: llega al toque."
                    : "Lejos: se carga su chunk un momento."));
            lore.add(line(on ? "Click: sacar de la llamada" : "Click: elegir"));
            ItemStack icon = icon(Material.PAPER, title(e).color(on ? NamedTextColor.GREEN : NamedTextColor.WHITE), lore, on);
            ItemMeta meta = icon.getItemMeta();
            meta.setItemModel(NamespacedKey.fromString("dinoearth:icon/" + e.species() + "/" + e.variant()));
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }
        long wait = cooldownLeft(p);
        inv.setItem(INFO, icon(Material.CLOCK, Component.text(wait > 0 ? "Esperá " + time(wait) : "Listo para llamar",
                wait > 0 ? NamedTextColor.RED : NamedTextColor.GREEN), List.of(line("Hasta " + settings.recallMax
                + " dinos por llamada, una vez cada " + time(settings.recallCooldownMs) + ".")), false));
        inv.setItem(CONFIRM, icon(Material.GOAT_HORN, Component.text("Llamar (" + h.chosen.size() + "/" + settings.recallMax
                + ")", NamedTextColor.GOLD), List.of(line("Trae a los elegidos a tu lado."),
                line("Después te siguen.")), !h.chosen.isEmpty()));
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
        } else if (slot == CONFIRM) {
            if (h.chosen.isEmpty()) return;
            p.closeInventory();
            recall(p, new ArrayList<>(h.chosen));
        } else if (slot < h.ids.size()) {
            UUID id = h.ids.get(slot);
            if (!h.chosen.remove(id)) {
                if (h.chosen.size() >= settings.recallMax) {
                    p.sendActionBar(Component.text("Máximo " + settings.recallMax + " dinos por llamada.", NamedTextColor.RED));
                    return;
                }
                h.chosen.add(id);
            }
            draw(p, h);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    /** Trae a tu lado a estos dinos (como mucho recallMax), si ya paso la espera. */
    public void recall(Player p, List<UUID> ids) {
        long wait = cooldownLeft(p);
        if (wait > 0) {
            p.sendMessage(Component.text("El silbato todavía no se recuperó: esperá " + time(wait) + ".", NamedTextColor.RED));
            return;
        }
        List<DinoRegistry.Entry> list = new ArrayList<>();
        for (UUID id : ids) {
            DinoRegistry.Entry e = registry.get(id);
            if (e != null && e.owner().equals(p.getUniqueId()) && !isHere(p, e) && list.size() < settings.recallMax) list.add(e);
        }
        if (list.isEmpty()) {
            p.sendMessage(Component.text("No hay nadie para llamar.", NamedTextColor.GRAY));
            return;
        }
        lastCall.put(p.getUniqueId(), System.currentTimeMillis());
        p.getWorld().playSound(p.getLocation(), "minecraft:item.goat_horn.sound.1", SoundCategory.PLAYERS, 0.9f, 1.3f);
        for (DinoRegistry.Entry e : list) bring(p, e);
    }

    private void bring(Player p, DinoRegistry.Entry e) {
        Entity ent = Bukkit.getEntity(e.id());
        if (ent instanceof Wolf w && dinos.isDino(w)) {
            arrive(p, e, w, () -> {
            });
            return;
        }
        World world = Bukkit.getWorld(e.world());
        if (world == null) {
            p.sendMessage(Component.text("El mundo de ", NamedTextColor.RED).append(title(e))
                    .append(Component.text(" no está cargado.", NamedTextColor.RED)));
            return;
        }
        int cx = e.chunkX(), cz = e.chunkZ();
        world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
            hold(world, cx, cz);
            new BukkitRunnable() {
                int waited;

                @Override
                public void run() {
                    Entity found = Bukkit.getEntity(e.id());
                    if (found instanceof Wolf w && dinos.isDino(w)) {
                        cancel();
                        arrive(p, e, w, () -> release(world, cx, cz));
                    } else if (++waited > FIND_TICKS || !p.isOnline()) {
                        cancel();
                        release(world, cx, cz);
                        if (p.isOnline()) {
                            p.sendMessage(Component.text("No encontré a ", NamedTextColor.RED).append(title(e))
                                    .append(Component.text(" donde estaba la última vez.", NamedTextColor.RED)));
                        }
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        });
    }

    private void arrive(Player p, DinoRegistry.Entry e, Wolf wolf, Runnable done) {
        Dino d = dinos.get(wolf);
        if (d == null) d = dinos.attach(wolf);
        if (d == null || !p.isOnline() || d.rider() != null || !p.getUniqueId().equals(wolf.getOwnerUniqueId())) {
            done.run();
            return;
        }
        Location dest = spotNear(p.getLocation());
        UUID id = wolf.getUniqueId();
        d.prepareTeleport();
        wolf.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            done.run();
            // si cambio de mundo es otra entidad: se busca de nuevo
            Entity now = Bukkit.getEntity(id);
            Dino moved = now instanceof Wolf w ? (dinos.get(w) != null ? dinos.get(w) : dinos.attach(w)) : null;
            if (!ok || moved == null) {
                if (p.isOnline()) p.sendMessage(Component.text("No se pudo traer a ", NamedTextColor.RED).append(title(e)));
                return;
            }
            moved.setOrder(Dino.Order.FOLLOW, p);
            registry.update(moved);
            Location at = moved.wolf().getLocation();
            at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.6, 0), 16, 0.5, 0.4, 0.5, 0.03);
            at.getWorld().playSound(at, "minecraft:entity.enderman.teleport", SoundCategory.NEUTRAL, 0.6f, 1.3f);
            p.sendMessage(OrderMenu.label(moved).colorIfAbsent(NamedTextColor.GREEN)
                    .append(Component.text(" volvió con vos.", NamedTextColor.GREEN)));
        });
    }

    /** Un lugar libre a 2-3 bloques del jugador (o el mismo lugar del jugador). */
    private static Location spotNear(Location base) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World w = base.getWorld();
        for (int tries = 0; tries < 12; tries++) {
            int x = base.getBlockX() + r.nextInt(-3, 4), z = base.getBlockZ() + r.nextInt(-3, 4);
            if (Math.abs(x - base.getBlockX()) < 2 && Math.abs(z - base.getBlockZ()) < 2) continue;
            for (int dy = 2; dy >= -2; dy--) {
                Block b = w.getBlockAt(x, base.getBlockY() + dy, z);
                if (b.isPassable() && !b.isLiquid() && b.getRelative(BlockFace.UP).isPassable()
                        && b.getRelative(BlockFace.DOWN).getType().isSolid()) {
                    Location l = b.getLocation().add(0.5, 0, 0.5);
                    l.setYaw(base.getYaw() + 180);
                    return l;
                }
            }
        }
        return base.clone();
    }

    // un ticket por chunk aunque se traigan 2 dinos del mismo
    private void hold(World w, int cx, int cz) {
        String key = w.getUID() + ":" + cx + ":" + cz;
        if (tickets.merge(key, 1, Integer::sum) == 1) w.getChunkAt(cx, cz).addPluginChunkTicket(plugin);
    }

    private void release(World w, int cx, int cz) {
        String key = w.getUID() + ":" + cx + ":" + cz;
        Integer n = tickets.get(key);
        if (n == null) return;
        if (n <= 1) {
            tickets.remove(key);
            w.removePluginChunkTicket(cx, cz, plugin);
        } else {
            tickets.put(key, n - 1);
        }
    }

    public void shutdown() {
        for (String key : tickets.keySet()) {
            String[] k = key.split(":");
            World w = Bukkit.getWorld(UUID.fromString(k[0]));
            if (w != null) w.removePluginChunkTicket(Integer.parseInt(k[1]), Integer.parseInt(k[2]), plugin);
        }
        tickets.clear();
    }

    // ------------------------------------------------------------ registro y avisos
    /** Donde quedo al descargarse; si se fue por otra cosa (jaula, /dino remove...) sale de la lista. */
    @EventHandler
    public void onRemoved(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Wolf w) || !dinos.isDino(w)) return;
        switch (event.getCause()) {
            case UNLOAD, PLAYER_QUIT -> {
                Dino d = dinos.get(w);
                if (d != null && w.isTamed()) registry.update(d);
                else registry.move(w.getUniqueId(), w.getLocation());
            }
            case DEATH -> {
                // lo maneja onDeath
            }
            default -> registry.remove(w.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Wolf w) || !w.isTamed()) return;
        Dino d = dinos.get(w);
        UUID ownerId = w.getOwnerUniqueId();
        Player owner = ownerId != null ? Bukkit.getPlayer(ownerId) : null;
        if (d == null || owner == null) return;
        Entity attacker = event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity s ? s : event.getDamager();
        if (attacker.getUniqueId().equals(ownerId)) return;
        if (owner.getWorld() == w.getWorld() && owner.getLocation().distanceSquared(w.getLocation()) < 24 * 24) return;
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(w.getUniqueId());
        if (last != null && now - last < settings.alertCooldownMs) return;
        lastAlert.put(w.getUniqueId(), now);
        Location at = w.getLocation();
        owner.sendMessage(Component.text("⚠ Están atacando a ", NamedTextColor.RED).append(OrderMenu.label(d))
                .append(Component.text(" en " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ()
                        + " — " + who(attacker) + ".", NamedTextColor.RED)));
        owner.playSound(owner.getLocation(), "minecraft:block.note_block.bell", SoundCategory.PLAYERS, 0.7f, 0.6f);
    }

    /** Corre antes que el listener que saca al dino de la lista de activos. */
    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wolf w) || !w.isTamed() || !dinos.isDino(w)) return;
        UUID ownerId = w.getOwnerUniqueId();
        Dino d = dinos.get(w);
        registry.remove(w.getUniqueId());
        lastAlert.remove(w.getUniqueId());
        if (ownerId == null) return;
        Location at = w.getLocation();
        Species s = dinos.speciesOf(w);
        String name = d != null && d.name() != null
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(d.name())
                + " (" + s.name + ")" : s.name;
        String text = "Tu " + name + " murió en " + at.getWorld().getName() + " " + at.getBlockX() + " " + at.getBlockY()
                + " " + at.getBlockZ() + " — " + cause(w.getLastDamageCause()) + ".";
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            owner.sendMessage(Component.text("✝ " + text, NamedTextColor.DARK_RED));
        } else {
            registry.addNotice(ownerId, text);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            List<String> list = registry.takeNotices(p.getUniqueId());
            if (list.isEmpty()) return;
            p.sendMessage(Component.text("Mientras no estabas:", NamedTextColor.GOLD));
            for (String t : list) p.sendMessage(Component.text("✝ " + t, NamedTextColor.DARK_RED));
        }, 60L);
    }

    // ------------------------------------------------------------ textos
    private List<DinoRegistry.Entry> sorted(Player p) {
        List<DinoRegistry.Entry> mine = new ArrayList<>(registry.ownedBy(p.getUniqueId()));
        mine.sort(Comparator.comparingDouble(e -> distance(p, e)));
        return mine;
    }

    private static double distance(Player p, DinoRegistry.Entry e) {
        if (!p.getWorld().getUID().equals(e.world())) return Double.MAX_VALUE;
        Location l = p.getLocation();
        return Math.hypot(l.getX() - e.x(), l.getZ() - e.z());
    }

    private static boolean isHere(Player p, DinoRegistry.Entry e) {
        return distance(p, e) <= HERE;
    }

    private static String where(Player p, DinoRegistry.Entry e) {
        World w = Bukkit.getWorld(e.world());
        String coords = (int) Math.floor(e.x()) + " " + (int) Math.floor(e.y()) + " " + (int) Math.floor(e.z());
        double d = distance(p, e);
        return (w != null ? w.getName() + " " : "") + coords
                + (d == Double.MAX_VALUE ? " (otro mundo)" : " (" + Math.round(d) + " bloques)");
    }

    private static Component title(DinoRegistry.Entry e) {
        Species s = Species.byId(e.species());
        String species = s != null ? s.name : e.species();
        String color = s != null && s.rig != null ? s.rig.variant(e.variant()).name() : e.variant();
        String sex = e.male() ? "♂" : "♀";
        return e.name() != null ? Component.text(e.name() + " (" + species + " " + color + " " + sex + ")")
                : Component.text(species + " " + color + " " + sex);
    }

    private static String orderLabel(String order) {
        return switch (order) {
            case "STAY" -> "sentado";
            case "PATROL" -> "patrullando";
            default -> "siguiéndote";
        };
    }

    private static String who(Entity e) {
        if (e instanceof Player p) return p.getName();
        Component custom = e.customName();
        if (custom != null) return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(custom);
        String type = e.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }

    private static String cause(EntityDamageEvent last) {
        if (last == null) return "causa desconocida";
        if (last instanceof EntityDamageByEntityEvent by) {
            Entity k = by.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity s ? s : by.getDamager();
            return "lo mató " + who(k);
        }
        return switch (last.getCause()) {
            case FALL -> "una caída";
            case LAVA -> "la lava";
            case FIRE, FIRE_TICK -> "el fuego";
            case DROWNING -> "se ahogó";
            case VOID -> "cayó al vacío";
            case SUFFOCATION -> "se asfixió";
            case STARVATION -> "el hambre";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "una explosión";
            case LIGHTNING -> "un rayo";
            case FREEZE -> "el frío";
            default -> last.getCause().name().toLowerCase().replace('_', ' ');
        };
    }

    private long cooldownLeft(Player p) {
        Long last = lastCall.get(p.getUniqueId());
        return last == null ? 0 : Math.max(0, settings.recallCooldownMs - (System.currentTimeMillis() - last));
    }

    private static String time(long ms) {
        long s = (ms + 999) / 1000;
        return s >= 60 ? (s / 60) + " min" + (s % 60 > 0 ? " " + s % 60 + " s" : "") : s + " s";
    }

    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
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

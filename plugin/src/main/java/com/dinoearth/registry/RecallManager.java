package com.dinoearth.registry;

import com.dinoearth.Keys;
import com.dinoearth.Settings;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.order.OrderMenu;
import com.dinoearth.species.Species;
import com.dinoearth.text.Fmt;
import com.dinoearth.text.Lang;
import com.dinoearth.text.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
        private final boolean fromWhistle;
        private int page;
        private Inventory inventory;

        private Holder(boolean fromWhistle) {
            this.fromWhistle = fromWhistle;
        }

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
        Lang lang = Lang.get();
        List<DinoRegistry.Entry> mine = sorted(p);
        if (mine.isEmpty()) {
            lang.chat(p, "mis-vacio");
            return;
        }
        lang.chat(p, "mis-titulo", "cantidad", mine.size());
        for (DinoRegistry.Entry e : mine) {
            Component call = isHere(p, e) ? Component.empty() : lang.chatMsg("mis-boton")
                    .hoverEvent(HoverEvent.showText(lang.chatMsg("mis-boton-hover")))
                    .clickEvent(ClickEvent.runCommand("/dino llamar " + shortId(e.id())));
            lang.chat(p, "mis-linea", "dino", title(e), "donde", where(p, e), "orden", orderLabel(e.order()), "llamar", call);
        }
        long wait = cooldownLeft(p);
        if (wait > 0) lang.chat(p, "mis-pie-espera", "tiempo", Fmt.time(wait));
        else lang.chat(p, "mis-pie-listo", "max", settings.recallMax, "tiempo", Fmt.time(settings.recallCooldownMs));
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
            Lang.get().chat(p, "llamar-no-encontrado");
            return;
        }
        recall(p, ids);
    }

    // ------------------------------------------------------------ silbato de regreso
    /** @param fromWhistle se abrio desde el menu del silbato: el boton del 49 dice Volver en vez de Cerrar. */
    public void openMenu(Player p, boolean fromWhistle) {
        List<DinoRegistry.Entry> far = sorted(p).stream().filter(e -> !isHere(p, e)).toList();
        if (far.isEmpty()) {
            Lang.get().action(p, "llamar-todos-aca");
            return;
        }
        Menu menu = Lang.get().menu("llamar");
        Holder h = new Holder(fromWhistle);
        for (DinoRegistry.Entry e : far) h.ids.add(e.id());
        Inventory inv = Bukkit.createInventory(h, menu.size(), menu.title());
        h.inventory = inv;
        draw(p, h);
        p.openInventory(inv);
    }

    private void draw(Player p, Holder h) {
        Menu menu = Lang.get().menu("llamar");
        Inventory inv = h.inventory;
        inv.clear();
        menu.fill(inv, Set.of("dino", "info", "llamar", "anterior", "siguiente", "volver", "cerrar"), this::tag);
        List<Integer> slots = menu.slots("dino");
        int per = Math.max(1, slots.size());
        int pages = (h.ids.size() + per - 1) / per;
        h.page = Math.max(0, Math.min(pages - 1, h.page));
        for (int i = 0; i < per; i++) {
            int idx = h.page * per + i;
            if (idx >= h.ids.size()) break;
            DinoRegistry.Entry e = registry.get(h.ids.get(idx));
            if (e == null) continue;
            boolean on = h.chosen.contains(e.id());
            Species s = Species.byId(e.species());
            ItemStack icon = menu.item("dino", Material.PAPER, "dinoearth:icon/" + e.species() + "/" + e.variant(),
                    "dino", title(e), "especie", s != null ? s.name : e.species(),
                    "color", s != null && s.rig != null ? s.colorName(e.variant()) : e.variant(),
                    "sexo", Fmt.sex(e.male()), "donde", where(p, e), "orden", orderLabel(e.order()),
                    "carga", Lang.get().plain(Bukkit.getEntity(e.id()) != null ? "llamar.cargado" : "llamar.lejos"),
                    "click", menu.text(on ? "sacar" : "elegir"));
            if (on) glow(icon);
            tag(icon);
            inv.setItem(slots.get(i), icon);
        }
        long wait = cooldownLeft(p);
        String time = Fmt.time(settings.recallCooldownMs);
        put(inv, menu, "info", menu.item("info", "estado", wait > 0 ? menu.text("espera", "tiempo", Fmt.time(wait))
                : menu.text("listo"), "max", settings.recallMax, "tiempo", time));
        ItemStack call = menu.item("llamar", "elegidos", h.chosen.size(), "max", settings.recallMax);
        if (!h.chosen.isEmpty()) glow(call);
        put(inv, menu, "llamar", call);
        if (h.page > 0) put(inv, menu, "anterior", menu.item("anterior"));
        if (h.page < pages - 1) put(inv, menu, "siguiente", menu.item("siguiente"));
        String back = h.fromWhistle ? "volver" : "cerrar";
        put(inv, menu, back, menu.item(back));
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

    private static void glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getClickedInventory() != event.getInventory()) return;
        int slot = event.getSlot();
        Menu menu = Lang.get().menu("llamar");
        if (slot == menu.slot("volver") || slot == menu.slot("cerrar")) {
            p.closeInventory();
            if (h.fromWhistle && whistleBack != null) whistleBack.accept(p);
        } else if (slot == menu.slot("llamar")) {
            if (h.chosen.isEmpty()) return;
            p.closeInventory();
            recall(p, new ArrayList<>(h.chosen));
        } else if (slot == menu.slot("anterior")) {
            h.page--;
            draw(p, h);
        } else if (slot == menu.slot("siguiente")) {
            h.page++;
            draw(p, h);
        } else {
            List<Integer> slots = menu.slots("dino");
            int i = slots.indexOf(slot);
            int idx = h.page * Math.max(1, slots.size()) + i;
            if (i < 0 || idx >= h.ids.size()) return;
            UUID id = h.ids.get(idx);
            if (!h.chosen.remove(id)) {
                if (h.chosen.size() >= settings.recallMax) {
                    Lang.get().action(p, "llamar-maximo", "max", settings.recallMax);
                    return;
                }
                h.chosen.add(id);
            }
            draw(p, h);
        }
    }

    private java.util.function.Consumer<Player> whistleBack;

    /** Que hace "Volver" (abrir de nuevo el menu del silbato). */
    public void setWhistleBack(java.util.function.Consumer<Player> back) {
        this.whistleBack = back;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    /** Trae a tu lado a estos dinos (como mucho recallMax), si ya paso la espera. */
    public void recall(Player p, List<UUID> ids) {
        long wait = cooldownLeft(p);
        if (wait > 0) {
            Lang.get().chat(p, "llamar-espera", "tiempo", Fmt.time(wait));
            return;
        }
        List<DinoRegistry.Entry> list = new ArrayList<>();
        for (UUID id : ids) {
            DinoRegistry.Entry e = registry.get(id);
            if (e != null && e.owner().equals(p.getUniqueId()) && !isHere(p, e) && list.size() < settings.recallMax) list.add(e);
        }
        if (list.isEmpty()) {
            Lang.get().chat(p, "llamar-nadie");
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
            Lang.get().chat(p, "llamar-mundo", "dino", title(e));
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
                            Lang.get().chat(p, "llamar-no-esta", "dino", title(e));
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
                if (p.isOnline()) Lang.get().chat(p, "llamar-fallo", "dino", title(e));
                return;
            }
            moved.setOrder(Dino.Order.FOLLOW, p);
            registry.update(moved);
            Location at = moved.wolf().getLocation();
            at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.6, 0), 16, 0.5, 0.4, 0.5, 0.03);
            at.getWorld().playSound(at, "minecraft:entity.enderman.teleport", SoundCategory.NEUTRAL, 0.6f, 1.3f);
            Lang.get().chat(p, "llamar-volvio", "dino", OrderMenu.label(moved));
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
        Lang.get().chat(owner, "aviso-ataque", "dino", OrderMenu.label(d), "x", at.getBlockX(), "y", at.getBlockY(),
                "z", at.getBlockZ(), "atacante", who(attacker));
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
        // se guarda ya armado (con colores) para mandarlo tal cual si el dueño no esta conectado
        String text = Lang.apply(Lang.get().raw("messages.yml", "chat.aviso-muerte"), "dino", name,
                "mundo", at.getWorld().getName(), "x", at.getBlockX(), "y", at.getBlockY(), "z", at.getBlockZ(),
                "causa", cause(w.getLastDamageCause()));
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            owner.sendMessage(Lang.parse(text));
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
            Lang.get().chat(p, "avisos-titulo");
            for (String t : list) p.sendMessage(Lang.parse(t));
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
        double d = distance(p, e);
        return Lang.get().plain(d == Double.MAX_VALUE ? "lugar.otro-mundo" : "lugar.con-distancia",
                "mundo", w != null ? w.getName() : "?", "x", (int) Math.floor(e.x()), "y", (int) Math.floor(e.y()),
                "z", (int) Math.floor(e.z()), "bloques", Math.round(d));
    }

    private static Component title(DinoRegistry.Entry e) {
        Species s = Species.byId(e.species());
        String species = s != null ? s.name : e.species();
        String color = s != null && s.rig != null ? s.colorName(e.variant()) : e.variant();
        String sex = e.male() ? "♂" : "♀";
        Lang lang = Lang.get();
        return Component.text(e.name() != null
                ? lang.plain("dino-con-nombre", "nombre", e.name(), "especie", species, "color", color, "sexo", sex)
                : lang.plain("dino-sin-nombre", "especie", species, "color", color, "sexo", sex));
    }

    private static String orderLabel(String order) {
        Lang lang = Lang.get();
        return lang.has("textos.yml", "ordenes." + order) ? lang.plain("ordenes." + order) : lang.plain("ordenes.FOLLOW");
    }

    private static String who(Entity e) {
        if (e instanceof Player p) return p.getName();
        Component custom = e.customName();
        if (custom != null) return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(custom);
        String type = e.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }

    private static String cause(EntityDamageEvent last) {
        Lang lang = Lang.get();
        if (last == null) return lang.plain("causas.desconocida");
        if (last instanceof EntityDamageByEntityEvent by) {
            Entity k = by.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity s ? s : by.getDamager();
            return lang.plain("causas.mato", "quien", who(k));
        }
        String key = "causas." + last.getCause().name();
        return lang.has("textos.yml", key) ? lang.plain(key) : last.getCause().name().toLowerCase().replace('_', ' ');
    }

    private long cooldownLeft(Player p) {
        Long last = lastCall.get(p.getUniqueId());
        return last == null ? 0 : Math.max(0, settings.recallCooldownMs - (System.currentTimeMillis() - last));
    }


    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }


}

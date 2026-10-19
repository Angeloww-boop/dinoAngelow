package com.dinoearth.spawn;

import com.dinoearth.Keys;
import com.dinoearth.species.Species;
import com.dinoearth.text.Lang;
import com.dinoearth.text.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Mapa del Paleontologo: un mapa de Minecraft con estilo de la Dinopedia (papel y cuero, mapa/tierra.png, lo hace
 * generator/paleomap.py), una huella roja en el centro de cada region del config y una flecha donde esta cada jugador.
 *
 * Todos los items comparten un solo MapView (id guardado en mapa.yml), asi son iguales entre si (sirven para
 * Shopkeepers). El dibujo se arma una vez por jugador; despues solo se mueve la flecha, asi que casi no consume.
 * En la mano muestra en la barra de accion la latitud/longitud y la region (cada segundo, solo a quien lo tiene en la
 * mano) y con click derecho abre el menu de regiones (distancia y rumbo a cada una).
 */
public final class PaleoMap implements Listener {

    /** Donde va el mundo (360 x 180 grados) dentro del mapa: lo mismo que generator/paleomap.py (WX, WY, WW, WH). */
    private static final int SIZE = 128, WORLD_X = 6, WORLD_Y = 35, WORLD_W = 116, WORLD_H = 58;
    /** Huella de tres dedos de cada region, en tinta roja con sombra oscura (colores de la paleta de los mapas). */
    private static final Color TRACK = new Color(153, 51, 51), TRACK_EDGE = new Color(54, 40, 27);
    private static final String[] TRACK_SPRITE = {
            "#.#.#",
            ".###.",
            "..#.."};

    private final JavaPlugin plugin;
    private final Keys keys;
    private final NaturalSpawner natural;
    private final File dataFile;
    private BufferedImage earth;
    private MapView view;
    private int version;

    public PaleoMap(JavaPlugin plugin, Keys keys, NaturalSpawner natural) {
        this.plugin = plugin;
        this.keys = keys;
        this.natural = natural;
        this.dataFile = new File(plugin.getDataFolder(), "mapa.yml");
        try (InputStream in = plugin.getResource("mapa/tierra.png")) {
            earth = in != null ? ImageIO.read(in) : null;
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo leer mapa/tierra.png: " + e.getMessage());
        }
        setupView();
        Bukkit.getScheduler().runTaskTimer(plugin, this::actionTick, 20L, 20L);
    }

    /** /dino reload: vuelve a dibujar los mapas (por si cambiaron las regiones o los textos). */
    public void reload() {
        version++;
    }

    @SuppressWarnings("deprecation")
    private void setupView() {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        int id = data.getInt("map-id", -1);
        MapView v = id >= 0 ? Bukkit.getMap(id) : null;
        if (v == null) {
            v = Bukkit.createMap(natural.mapWorld());
            data.set("map-id", v.getId());
            try {
                data.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo guardar mapa.yml: " + e.getMessage());
            }
        }
        for (MapRenderer r : new ArrayList<>(v.getRenderers())) v.removeRenderer(r);
        v.setTrackingPosition(false);
        v.setUnlimitedTracking(false);
        v.setLocked(true);
        v.addRenderer(new Renderer());
        view = v;
    }

    // ------------------------------------------------------------ item
    public ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(Material.FILLED_MAP, Math.max(1, amount));
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        Lang.get().itemTexts(meta, "mapa");
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(keys.paleoMap, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMap(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keys.paleoMap, PersistentDataType.BYTE);
    }

    private boolean holds(Player p) {
        return isMap(p.getInventory().getItemInMainHand()) || isMap(p.getInventory().getItemInOffHand());
    }

    // ------------------------------------------------------------ dibujo
    static double fx(double lon) {
        return WORLD_X + (lon + 180) / 360 * WORLD_W;
    }

    static double fy(double lat) {
        return WORLD_Y + (90 - lat) / 180 * WORLD_H;
    }

    /** Algo que recibe pixeles: el MapCanvas en el juego, una imagen en MapPreview. */
    interface Pixels {
        void set(int x, int y, Color c);
    }

    /** Las huellas de las regiones (la misma cuenta en el juego y en la vista previa). */
    static void drawRegions(List<EarthMap.Region> regions, Pixels out) {
        for (EarthMap.Region r : regions) {
            int cx = (int) Math.round(fx((r.lonMin() + r.lonMax()) / 2)) - 2;
            int cy = (int) Math.round(fy((r.latMin() + r.latMax()) / 2)) - 1;
            for (int y = 0; y < TRACK_SPRITE.length; y++) {
                for (int x = 0; x < 5; x++) {
                    if (TRACK_SPRITE[y].charAt(x) == '#') out.set(cx + x, cy + y, TRACK);
                }
            }
            out.set(cx + 2, cy + TRACK_SPRITE.length, TRACK_EDGE);   // sombrita debajo del talon
        }
    }

    private final class Renderer extends MapRenderer {
        private final Map<Player, Integer> drawn = new WeakHashMap<>();

        Renderer() {
            super(true);   // una flecha distinta para cada jugador
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            Integer done = drawn.get(player);
            if (done == null || done != version) {
                drawBase(canvas);
                drawn.put(player, version);
            }
            MapCursorCollection cursors = new MapCursorCollection();
            if (natural.isMapWorld(player.getWorld())) {
                Location at = player.getLocation();
                EarthMap m = natural.map();
                double x = fx(m.lon(at.getX()));
                double y = fy(m.lat(at.getZ()));
                float yaw = at.getYaw();
                int rot = (int) ((yaw < 0 ? yaw - 8 : yaw + 8) * 16 / 360);   // igual que los mapas de vanilla
                cursors.addCursor(new MapCursor(cursorCoord(x), cursorCoord(y), (byte) (rot & 15),
                        MapCursor.Type.PLAYER, true));
            }
            canvas.setCursors(cursors);
        }
    }

    /** Pixel del mapa (0..128) a la coordenada de las flechas (-128..127, en medios pixeles). */
    private static byte cursorCoord(double pixel) {
        return (byte) Math.max(-128, Math.min(127, Math.round(pixel * 2 - SIZE)));
    }

    private void drawBase(MapCanvas canvas) {
        if (earth != null) canvas.drawImage(0, 0, earth);
        drawRegions(natural.map().regions(), (x, y, c) -> {
            if (x >= WORLD_X && x < WORLD_X + WORLD_W && y >= WORLD_Y && y < WORLD_Y + WORLD_H) canvas.setPixelColor(x, y, c);
        });
    }

    // ------------------------------------------------------------ barra de accion
    private void actionTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!holds(p)) continue;
            Lang lang = Lang.get();
            if (!natural.isMapWorld(p.getWorld())) {
                lang.action(p, "mapa-mano-otro-mundo");
                continue;
            }
            Location at = p.getLocation();
            EarthMap m = natural.map();
            String lat = NaturalSpawner.coord(m.lat(at.getZ()), "mapa.norte", "mapa.sur");
            String lon = NaturalSpawner.coord(m.lon(at.getX()), "mapa.este", "mapa.oeste");
            List<EarthMap.Region> here = m.regionsAt(at.getX(), at.getZ());
            if (!here.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (EarthMap.Region r : here) names.add(NaturalSpawner.regionName(r.id()));
                lang.action(p, "mapa-mano-region", "lat", lat, "lon", lon, "region", String.join(", ", names));
                continue;
            }
            EarthMap.Region best = null;
            double bestD = Double.MAX_VALUE;
            for (EarthMap.Region r : m.regions()) {
                double d = distance(m, r, at);
                if (d < bestD) {
                    bestD = d;
                    best = r;
                }
            }
            if (best == null) {
                lang.action(p, "mapa-mano-posicion", "lat", lat, "lon", lon);
            } else {
                lang.action(p, "mapa-mano-cerca", "lat", lat, "lon", lon, "region", NaturalSpawner.regionName(best.id()),
                        "distancia", String.format(java.util.Locale.forLanguageTag("es-AR"), "%,d", Math.round(bestD)),
                        "rumbo", heading(m, best, at));
            }
        }
    }

    /** El punto de la region mas cerca del jugador, en bloques. */
    private static double[] nearest(EarthMap m, EarthMap.Region r, Location at) {
        double xMin = m.x(r.lonMin()), xMax = m.x(r.lonMax());
        double zMin = Math.min(m.z(r.latMin()), m.z(r.latMax())), zMax = Math.max(m.z(r.latMin()), m.z(r.latMax()));
        return new double[]{Math.max(xMin, Math.min(xMax, at.getX())), Math.max(zMin, Math.min(zMax, at.getZ()))};
    }

    static double distance(EarthMap m, EarthMap.Region r, Location at) {
        double[] n = nearest(m, r, at);
        return Math.hypot(n[0] - at.getX(), n[1] - at.getZ());
    }

    /** Rumbo de 8 puntas hacia la region (en Minecraft el norte es -z). */
    static String heading(EarthMap m, EarthMap.Region r, Location at) {
        double[] n = nearest(m, r, at);
        double angle = Math.toDegrees(Math.atan2(-(n[1] - at.getZ()), n[0] - at.getX()));   // 0 = este, 90 = norte
        int i = (int) Math.floorMod(Math.round(angle / 45), 8);
        List<String> names = Lang.get().list("textos.yml", "mapa.rumbos");
        return names.size() == 8 ? names.get(i) : "";
    }

    // ------------------------------------------------------------ menu de regiones
    /** Menu de solo lectura (se reconoce por el holder, nunca por el titulo). */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private int page;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isMap(event.getItem())) return;
        Action a = event.getAction();
        if (a == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()) {
            return;   // abrir un cofre o una puerta con el mapa en la mano
        }
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        open(event.getPlayer());
    }

    public void open(Player p) {
        Menu menu = Lang.get().menu("regiones");
        Holder h = new Holder();
        h.inventory = Bukkit.createInventory(h, menu.size(), menu.title());
        draw(p, h);
        p.openInventory(h.inventory);
    }

    /** Arma la pagina actual: las regiones de la mas cerca a la mas lejos (en la que estas, primero). */
    private void draw(Player p, Holder h) {
        Menu menu = Lang.get().menu("regiones");
        Inventory inv = h.inventory;
        inv.clear();
        menu.fill(inv, Set.of("region", "posicion", "oceano", "resto", "cerrar", "anterior", "siguiente"), null);
        EarthMap m = natural.map();
        Location at = p.getLocation();
        boolean onMap = natural.isMapWorld(p.getWorld());
        java.util.Locale es = java.util.Locale.forLanguageTag("es-AR");

        List<Integer> slots = menu.slots("region");
        List<EarthMap.Region> regions = new ArrayList<>(m.regions());
        if (onMap) regions.sort(java.util.Comparator.comparingDouble(r -> distance(m, r, at)));
        int per = Math.max(1, slots.size());
        int pages = Math.max(1, (regions.size() + per - 1) / per);
        h.page = Math.max(0, Math.min(pages - 1, h.page));
        for (int i = 0; i < per; i++) {
            int idx = h.page * per + i;
            if (idx >= regions.size()) break;
            EarthMap.Region r = regions.get(idx);
            Species icon = r.species().isEmpty() ? null : Species.byId(r.species().keySet().iterator().next());
            String model = icon != null && icon.rig != null
                    ? "dinoearth:icon/" + icon.id + "/" + icon.rig.variants().keySet().iterator().next() : null;
            boolean inside = onMap && r.contains(m.lat(at.getZ()), m.lon(at.getX()));
            String estado = !onMap ? menu.text("estado-otro-mundo")
                    : inside ? menu.text("estado-aca")
                    : menu.text("estado-lejos", "distancia", String.format(es, "%,d", Math.round(distance(m, r, at))),
                    "rumbo", heading(m, r, at));
            ItemStack item = menu.item("region", model != null ? Material.PAPER : Material.FILLED_MAP, model,
                    "region", NaturalSpawner.regionName(r.id()), "lista", NaturalSpawner.names(r.species()),
                    "x", Math.round(m.x((r.lonMin() + r.lonMax()) / 2)), "z", Math.round(m.z((r.latMin() + r.latMax()) / 2)),
                    "estado", estado);
            if (inside) glow(item);
            inv.setItem(slots.get(i), item);
        }
        String here = onMap ? NaturalSpawner.coord(m.lat(at.getZ()), "mapa.norte", "mapa.sur") + ", "
                + NaturalSpawner.coord(m.lon(at.getX()), "mapa.este", "mapa.oeste") : menu.text("fuera");
        put(inv, menu, "posicion", menu.item("posicion", "donde", here));
        put(inv, menu, "oceano", menu.item("oceano", "lista", NaturalSpawner.names(natural.oceanSpecies())));
        put(inv, menu, "resto", menu.item("resto", "lista", NaturalSpawner.names(natural.elsewhereSpecies())));
        put(inv, menu, "cerrar", menu.item("cerrar"));
        if (h.page > 0) put(inv, menu, "anterior", menu.item("anterior", "pagina", h.page, "total", pages));
        if (h.page < pages - 1) put(inv, menu, "siguiente", menu.item("siguiente", "pagina", h.page + 2, "total", pages));
    }

    private static void put(Inventory inv, Menu menu, String id, ItemStack item) {
        int slot = menu.slot(id);
        if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item);
    }

    private static void glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getClickedInventory() != event.getInventory()) return;
        Holder h = (Holder) event.getInventory().getHolder();
        Menu menu = Lang.get().menu("regiones");
        int slot = event.getSlot();
        if (slot == menu.slot("cerrar")) {
            p.closeInventory();
        } else if (slot == menu.slot("anterior") && event.getCurrentItem() != null && h.page > 0) {
            h.page--;
            draw(p, h);
        } else if (slot == menu.slot("siguiente") && event.getCurrentItem() != null) {
            h.page++;
            draw(p, h);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    /** Para /dino item mapa: que el mapa tambien salga en la lista. */
    public static boolean isMapId(String id) {
        return "mapa".equalsIgnoreCase(id);
    }
}

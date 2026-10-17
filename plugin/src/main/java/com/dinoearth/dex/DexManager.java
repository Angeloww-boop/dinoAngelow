package com.dinoearth.dex;

import com.dinoearth.Keys;
import com.dinoearth.Settings;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.gui.GuiFont;
import com.dinoearth.mission.MissionManager;
import com.dinoearth.model.DinoRig;
import com.dinoearth.species.Species;
import com.dinoearth.text.Fmt;
import com.dinoearth.text.Lang;
import com.dinoearth.text.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dinopedia: libreta de paleontologo con menus dibujados (ver GuiFont y generator/gui.py).
 *
 * - Indice: las especies (silueta si todavia no la viste), tu rango y cuanto investigaste.
 * - Pagina de cada especie: foto grande del color que elijas, barras de vida/daño/velocidad/tamaño, colores vistos,
 *   sellos de investigacion y la lupa para rastrearla.
 * - Ficha de un dino (click derecho con el libro): su foto, sexo, edad, vida, hambre, dueño y ordenes.
 * - Misiones del dia (MissionManager): tres tarjetas con su progreso y el boton para cambiar una.
 *
 * Sellos: tareas por especie (verlo, todos los colores, el color raro, domesticarlo, criarlo, montarlo...). Cada
 * sello da experiencia; una especie completa da mas y puede correr comandos (config.yml). Con el libro en la mano
 * se anotan los dinos desde mas lejos, aparecen huellas de especies que no conocés y la lupa marca hacia donde ir.
 *
 * Todos los textos salen de menus/dinopedia.yml, textos.yml y messages.yml. Lo de cada jugador se guarda en el
 * propio jugador (PDC). Los menus son de solo lectura: todo click se cancela.
 */
public final class DexManager {

    // ------------------------------------------------------------ casilleros que dependen del dibujo
    private static final int[][] INDEX_ROWS = {{10, 11, 12, 13, 14, 15, 16}, {28, 29, 30, 31, 32, 33, 34}};
    private static final int[] COLOR_SLOTS = {36, 37, 38, 39, 46, 47, 48};
    private static final int[] STAMP_SLOTS = {41, 42, 43, 50, 51, 52, 40};   // el 7.o va sobre el lomo
    private static final int[] MISSION_ICON = {0, 18, 36};
    private static final int[] MISSION_REROLL = {8, 26, 44};

    // ------------------------------------------------------------ sellos y rangos
    public enum Stamp {
        VISTO, COLORES, RARO, DOMESTICADO, CRIADO, JINETE, HOMBRO, CAZADO, CORREDOR;

        String label() {
            return Lang.get().plain("sellos." + name() + ".nombre");
        }

        String task(Species s) {
            if (this == DOMESTICADO && s.eggTameOnly) return Lang.get().plain("sellos.DOMESTICADO.tarea-huevo");
            return Lang.get().plain("sellos." + name() + ".tarea");
        }

        String model() {
            return name().toLowerCase();
        }
    }

    /** Desde que fraccion de sellos se tiene cada rango y su color de tinta (los nombres estan en textos.yml). */
    private static final double[] RANK_FROM = {0, 0.10, 0.25, 0.45, 0.70, 1.0};
    private static final TextColor[] RANK_COLOR = {TextColor.color(0x7A5236), TextColor.color(0x8A5A24),
            TextColor.color(0x5E646C), TextColor.color(0x9A7414), TextColor.color(0x23786E), TextColor.color(0x7A3C96)};

    public static List<Stamp> stampsOf(Species s) {
        List<Stamp> out = new ArrayList<>();
        out.add(Stamp.VISTO);
        out.add(Stamp.COLORES);
        if (rare(s) != null) out.add(Stamp.RARO);
        if (s.tameable()) {
            out.add(Stamp.DOMESTICADO);
            if (s.rig.hasEgg() && !s.eggTameOnly) out.add(Stamp.CRIADO);
        } else {
            out.add(Stamp.CAZADO);
        }
        if (s.rideable) out.add(Stamp.JINETE);
        else if (s.perches) out.add(Stamp.HOMBRO);
        // solo en las especies que presta alguna pista de carreras
        var races = com.dinoearth.race.RaceManager.get();
        if (races != null && races.usesSpecies(s)) out.add(Stamp.CORREDOR);
        return out;
    }

    private static DinoRig.Variant rare(Species s) {
        for (DinoRig.Variant v : s.rig.variants().values()) {
            if (v.weight() <= 5) return v;
        }
        return null;
    }

    // ------------------------------------------------------------ estado
    private final Keys keys;
    private final Settings settings;
    private final GuiFont gui;
    private final Map<UUID, String> tracking = new HashMap<>();
    private DinoManager dinos;
    private MissionManager missions;
    private final double maxHealth, maxDamage, maxSpeed, maxSize;

    /** Identifica nuestros menus (nunca por el titulo). Cada casillero con accion tiene su Runnable. */
    public static final class Menu implements InventoryHolder {
        private final Map<Integer, Runnable> actions = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public DexManager(Keys keys, Settings settings, GuiFont gui) {
        this.keys = keys;
        this.settings = settings;
        this.gui = gui;
        maxHealth = Species.ALL.stream().mapToDouble(s -> s.health).max().orElse(1);
        maxDamage = Species.ALL.stream().mapToDouble(s -> s.damage).max().orElse(1);
        maxSpeed = Species.ALL.stream().mapToDouble(s -> s.speed).max().orElse(1);
        maxSize = Species.ALL.stream().mapToDouble(s -> s.hitbox).max().orElse(1);
    }

    /** Arranca las huellas y el rastreo (necesita la lista de dinos, que se crea despues). */
    public void start(Plugin plugin, DinoManager dinos) {
        this.dinos = dinos;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickHud, 40L, 10L);
    }

    public void setMissions(MissionManager missions) {
        this.missions = missions;
    }

    private static Lang lang() {
        return Lang.get();
    }

    private static com.dinoearth.text.Menu yml() {
        return Lang.get().menu("dinopedia");
    }

    // ------------------------------------------------------------ libro
    public ItemStack createBook() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString("dinoearth:dinopedia"));
        lang().itemTexts(meta, "dinopedia");
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(keys.dexBook, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBook(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(keys.dexBook);
    }

    private boolean holdsBook(Player p) {
        return isBook(p.getInventory().getItemInMainHand()) || isBook(p.getInventory().getItemInOffHand());
    }

    /** Un icono de nuestros menus que por algun motivo termino afuera (no deberia pasar nunca). */
    public boolean isIcon(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(keys.dexIcon);
    }

    /** Distancia a la que se anota un dino al verlo: con el libro en la mano, mas lejos. */
    public double discoverRange(Player p) {
        return holdsBook(p) ? settings.dexBookRange : settings.dexRange;
    }

    // ------------------------------------------------------------ progreso del jugador
    public Set<String> seen(Player p, Species s) {
        return set(p, keys.dexSeen(s.id));
    }

    private Set<String> set(Player p, NamespacedKey key) {
        String raw = p.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        Set<String> out = new LinkedHashSet<>();
        if (raw != null && !raw.isEmpty()) out.addAll(List.of(raw.split(",")));
        return out;
    }

    public boolean discovered(Player p, Species s) {
        return p.getPersistentDataContainer().has(keys.dexSeen(s.id));
    }

    public int tamed(Player p, Species s) {
        return p.getPersistentDataContainer().getOrDefault(keys.dexTamed(s.id), PersistentDataType.INTEGER, 0);
    }

    private Set<Stamp> stamps(Player p, Species s) {
        Set<Stamp> out = new LinkedHashSet<>();
        for (String id : set(p, keys.dexStamps(s.id))) {
            try {
                out.add(Stamp.valueOf(id));
            } catch (IllegalArgumentException ignored) {
                // sello de otra version
            }
        }
        out.retainAll(stampsOf(s));
        return out;
    }

    // ------------------------------------------------------------ para los tops y PlaceholderAPI
    public int speciesFound(Player p) {
        return (int) Species.ALL.stream().filter(s -> discovered(p, s)).count();
    }

    public int stampsDone(Player p) {
        return doneStamps(p);
    }

    public int stampsTotal() {
        return totalStamps();
    }

    public String rankOf(Player p) {
        return rankName(rank(doneStamps(p)));
    }

    /** Copia sellos y especies descubiertas a jugadores.yml (los tops comparan tambien a los desconectados). */
    public void syncStats(Player p) {
        var stats = com.dinoearth.stats.PlayerStats.get();
        if (stats == null) return;
        stats.set(p, "sellos", doneStamps(p));
        stats.set(p, "especies", speciesFound(p));
    }

    private int totalStamps() {
        return Species.ALL.stream().mapToInt(s -> stampsOf(s).size()).sum();
    }

    private int doneStamps(Player p) {
        return Species.ALL.stream().mapToInt(s -> stamps(p, s).size()).sum();
    }

    private int rank(int done) {
        double f = done / (double) totalStamps();
        int r = 0;
        for (int i = 0; i < RANK_FROM.length; i++) {
            if (f >= RANK_FROM[i] - 1e-9) r = i;
        }
        return r;
    }

    private static String rankName(int i) {
        List<String> names = lang().list("textos.yml", "rangos");
        return i < names.size() ? names.get(i) : String.valueOf(i);
    }

    /** El jugador vio un dino de esta especie y color. Avisa si es nuevo. */
    public void discover(Player p, Species s, String variant) {
        Set<String> seen = seen(p, s);
        if (!seen.add(variant)) return;
        boolean first = seen.size() == 1;
        p.getPersistentDataContainer().set(keys.dexSeen(s.id), PersistentDataType.STRING, String.join(",", seen));
        DinoRig.Variant v = s.rig.variant(variant);
        if (first) {
            syncStats(p);
            lang().action(p, "dex-especie-nueva", "especie", s.name);
            p.playSound(p, "minecraft:entity.player.levelup", SoundCategory.PLAYERS, 0.5f, 1.4f);
        } else if (v.weight() <= 5) {
            lang().action(p, "dex-color-raro", "especie", s.name, "color", s.colorName(variant));
            p.playSound(p, "minecraft:entity.player.levelup", SoundCategory.PLAYERS, 0.5f, 1.8f);
        } else {
            lang().action(p, "dex-color-nuevo", "especie", s.name, "color", s.colorName(variant));
        }
        refresh(p, s);
    }

    public void addTamed(Player p, Species s, String variant) {
        p.getPersistentDataContainer().set(keys.dexTamed(s.id), PersistentDataType.INTEGER, tamed(p, s) + 1);
        discover(p, s, variant);
        refresh(p, s);
    }

    public void onRide(Player p, Species s) {
        award(p, s, Stamp.JINETE);
    }

    public void onEscort(Player p, Species s) {
        award(p, s, Stamp.HOMBRO);
    }

    public void onHatch(Player p, Species s) {
        award(p, s, Stamp.CRIADO);
    }

    /** Termino una carrera montando un dino de esta especie (prestado por la pista). */
    public void onRace(Player p, Species s) {
        if (!discovered(p, s)) discover(p, s, s.rig.variants().keySet().iterator().next());
        award(p, s, Stamp.CORREDOR);
    }

    public void onKill(Player p, Species s) {
        award(p, s, Stamp.CAZADO);
    }

    /** Da los sellos que salen de lo que ya tiene anotado (vistos, colores, domesticados). */
    private void refresh(Player p, Species s) {
        if (!discovered(p, s)) return;
        Set<String> seen = seen(p, s);
        award(p, s, Stamp.VISTO);
        boolean common = s.rig.variants().values().stream().filter(v -> v.weight() > 5).allMatch(v -> seen.contains(v.id()));
        if (common) award(p, s, Stamp.COLORES);
        DinoRig.Variant rare = rare(s);
        if (rare != null && seen.contains(rare.id())) award(p, s, Stamp.RARO);
        if (tamed(p, s) > 0) award(p, s, Stamp.DOMESTICADO);
    }

    private void award(Player p, Species s, Stamp stamp) {
        List<Stamp> all = stampsOf(s);
        if (!all.contains(stamp)) return;
        Set<Stamp> have = stamps(p, s);
        if (have.contains(stamp)) return;
        int before = rank(doneStamps(p));
        have.add(stamp);
        List<String> ids = have.stream().map(Enum::name).toList();
        p.getPersistentDataContainer().set(keys.dexStamps(s.id), PersistentDataType.STRING, String.join(",", ids));

        int xp = settings.dexXpStamp;
        lang().chat(p, "sello", "sello", stamp.label(), "especie", s.name, "xp", xp);
        p.playSound(p, "minecraft:ui.cartography_table.take_result", SoundCategory.PLAYERS, 0.8f, 1.1f);
        if (xp > 0) p.giveExp(xp);
        money(p, settings.moneyStamp);
        syncStats(p);

        if (have.containsAll(all)) {
            int bonus = settings.dexXpSpecies;
            lang().chat(p, "pagina-completa", "especie", s.name, "xp", bonus);
            p.playSound(p, "minecraft:ui.toast.challenge_complete", SoundCategory.PLAYERS, 0.6f, 1.2f);
            if (bonus > 0) p.giveExp(bonus);
            money(p, settings.moneySpecies);
            run(settings.dexSpeciesCommands, p, s);
        }
        int done = doneStamps(p);
        int after = rank(done);
        if (after != before) {
            lang().chat(p, "rango", "rango", rankName(after));
            p.playSound(p, "minecraft:entity.player.levelup", SoundCategory.PLAYERS, 0.7f, 0.9f);
        }
        if (done == totalStamps()) run(settings.dexAllCommands, p, s);
    }

    private static void money(Player p, double amount) {
        String given = com.dinoearth.integration.Money.give(p, amount);
        if (given != null) lang().chat(p, "dinero", "dinero", given);
    }

    private static void run(List<String> commands, Player p, Species s) {
        for (String c : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", p.getName()).replace("%species%", s.id));
        }
    }

    // ------------------------------------------------------------ indice
    public void openIndex(Player p) {
        openIndex(p, 0);
    }

    public void openIndex(Player p, int page) {
        Species.ALL.forEach(s -> refresh(p, s));
        com.dinoearth.text.Menu menu = yml();
        Menu holder = new Menu();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.empty());
        int perPage = INDEX_ROWS[0].length * 2;
        int pages = (Species.ALL.size() + perPage - 1) / perPage;
        page = Math.max(0, Math.min(pages - 1, page));
        int found = (int) Species.ALL.stream().filter(s -> discovered(p, s)).count();
        int total = totalStamps(), done = doneStamps(p);
        int rank = rank(done);

        List<String> rankLines = new ArrayList<>();
        for (int i = 0; i < RANK_FROM.length; i++) {
            rankLines.add(menu.text(i <= rank ? "rango-tiene" : "rango-falta", "rango", rankName(i),
                    "porcentaje", Math.round(RANK_FROM[i] * 100)));
        }
        List<String> missing = rank + 1 < RANK_FROM.length ? List.of(menu.text("falta",
                "cantidad", (int) Math.ceil(RANK_FROM[rank + 1] * total) - done, "rango", rankName(rank + 1))) : List.of();
        Object[] counts = {"especies", found, "especies_total", Species.ALL.size(), "sellos", done, "sellos_total", total};
        put(inv, menu, "rango", menu.item("rango", null, "dinoearth:gui/rank" + rank,
                concat(counts, "rango", rankName(rank), "falta", missing, "rangos", rankLines)));
        ItemStack research = menu.item("investigacion", counts);
        tag(research);
        for (int slot : menu.slots("investigacion")) inv.setItem(slot, research);

        List<Integer> bars = new ArrayList<>();
        List<Boolean> completeBars = new ArrayList<>();
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= Species.ALL.size()) break;
            Species s = Species.ALL.get(idx);
            int slot = INDEX_ROWS[i / INDEX_ROWS[0].length][i % INDEX_ROWS[0].length];
            if (!discovered(p, s)) {
                ItemStack it = menu.item("especie-desconocida", null, "dinoearth:icon/" + s.id + "/unknown");
                tag(it);
                inv.setItem(slot, it);
                bars.add(-1);
                completeBars.add(false);
                continue;
            }
            Set<String> seen = seen(p, s);
            Set<Stamp> have = stamps(p, s);
            List<Stamp> all = stampsOf(s);
            boolean complete = have.size() == all.size();
            ItemStack it = menu.item("especie", null, iconOf(s, seen), "especie", s.name,
                    "descripcion", wrapLines(menu, "descripcion", s.about, 40),
                    "colores", seen.size(), "colores_total", s.rig.variants().size(),
                    "sellos", have.size(), "sellos_total", all.size(),
                    "completa", complete ? List.of(menu.text("completa")) : List.of());
            if (complete) glow(it);
            tag(it);
            inv.setItem(slot, it);
            bars.add((int) Math.round(8.0 * have.size() / all.size()));
            completeBars.add(complete);
            holder.actions.put(slot, () -> openSpecies(p, s, null));
        }

        int current = page;
        if (page > 0) action(holder, inv, menu, "anterior", menu.item("anterior"), () -> openIndex(p, current - 1));
        if (page < pages - 1) action(holder, inv, menu, "siguiente", menu.item("siguiente"), () -> openIndex(p, current + 1));
        put(inv, menu, "ayuda", menu.item("ayuda"));
        if (missions != null) {
            MissionManager.State st = missions.state(p);
            int doneMissions = 0, totalMissions = 0;
            for (int i = 0; i < MissionManager.PER_DAY; i++) {
                if (st.get(i) == null) continue;
                totalMissions++;
                if (st.get(i).done) doneMissions++;
            }
            action(holder, inv, menu, "misiones", menu.item("misiones", "hechas", doneMissions, "total", totalMissions,
                    "tiempo", Fmt.time(missions.msUntilReset())), () -> openMissions(p));
        }
        String tracked = tracking.get(p.getUniqueId());
        Species trackedSpecies = tracked != null ? Species.byId(tracked) : null;
        if (trackedSpecies != null) {
            action(holder, inv, menu, "rastreando", menu.item("rastreando", "especie", trackedSpecies.name), () -> {
                tracking.remove(p.getUniqueId());
                openIndex(p, current);
            });
        }
        action(holder, inv, menu, "cerrar", menu.item("cerrar"), p::closeInventory);
        open(p, holder, inv, DexLayout.index(gui, new DexLayout.IndexView(menu.text("pagina-dinopedia"), rankName(rank),
                RANK_COLOR[Math.min(rank, RANK_COLOR.length - 1)], done, total, bars, completeBars)));
    }

    // ------------------------------------------------------------ pagina de una especie
    public void openSpecies(Player p, Species s, String variant) {
        if (!discovered(p, s)) {
            openIndex(p);
            return;
        }
        com.dinoearth.text.Menu menu = yml();
        Set<String> seen = seen(p, s);
        if (variant == null || !seen.contains(variant)) variant = firstSeen(s, seen);
        String shown = variant;
        Menu holder = new Menu();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.empty());
        String dietIcon = switch (s.diet()) {
            case MEAT -> "meat";
            case PLANTS -> "leaf";
            case FISH -> "fish";
        };
        Component title = DexLayout.species(gui, new DexLayout.SpeciesView(s.id, shown, s.name, dietIcon,
                lang().plain("dieta-palabra." + s.diet().name()), List.of(
                new DexLayout.Stat("heart", s.health / maxHealth, TextColor.color(0xB43A32)),
                new DexLayout.Stat("sword", s.damage / maxDamage, TextColor.color(0x7C6A5A)),
                new DexLayout.Stat("speed", s.speed / maxSpeed, TextColor.color(0x3C82BE)),
                new DexLayout.Stat("size", s.hitbox / maxSize, TextColor.color(0x8A6A3A))),
                temperWord(s), menu.text("pagina-colores", "vistos", seen.size(), "total", s.rig.variants().size())));

        ItemStack photo = menu.item("foto", "especie", s.name, "color", s.colorName(shown),
                "descripcion", wrapLines(menu, "descripcion", s.about, 40), "tamano", s.size,
                "domesticados", s.tameable() ? List.of(menu.text("domesticados", "cantidad", tamed(p, s))) : List.of());
        tag(photo);
        for (int slot : menu.slots("foto")) inv.setItem(slot, photo);
        ItemStack sheet = technical(menu, s);
        tag(sheet);
        for (int slot : menu.slots("ficha")) inv.setItem(slot, sheet);

        int i = 0;
        for (DinoRig.Variant v : s.rig.variants().values()) {
            if (i >= COLOR_SLOTS.length) break;
            int slot = COLOR_SLOTS[i++];
            String rare = v.weight() <= 5 ? menu.text("raro") : "";
            if (seen.contains(v.id())) {
                boolean current = v.id().equals(shown);
                ItemStack it = menu.item("color", null, "dinoearth:icon/" + s.id + "/" + v.id(), "color", s.colorName(v.id()),
                        "raro", rare, "click", current ? menu.text("en-foto") : menu.text("ver-color"));
                if (current) {
                    glow(it);
                    tag(it);
                    inv.setItem(slot, it);
                } else {
                    action(holder, inv, slot, it, () -> openSpecies(p, s, v.id()));
                }
            } else {
                ItemStack it = menu.item("color-sin-ver", null, "dinoearth:icon/" + s.id + "/unknown", "raro", rare);
                tag(it);
                inv.setItem(slot, it);
            }
        }

        Set<Stamp> have = stamps(p, s);
        List<Stamp> all = stampsOf(s);
        for (int k = 0; k < all.size() && k < STAMP_SLOTS.length; k++) {
            Stamp st = all.get(k);
            boolean got = have.contains(st);
            ItemStack it = menu.item("sello", null, "dinoearth:gui/stamp/" + st.model() + (got ? "" : "_off"),
                    "sello", st.label(), "tarea", st.task(s), "estado", menu.text(got ? "sello-hecho" : "sello-pendiente"));
            if (got) glow(it);
            tag(it);
            inv.setItem(STAMP_SLOTS[k], it);
        }

        boolean tracked = s.id.equals(tracking.get(p.getUniqueId()));
        action(holder, inv, menu, "lupa", menu.item("lupa", null, tracked ? "dinoearth:gui/track" : "dinoearth:gui/track_off",
                "titulo", menu.text(tracked ? "lupa-on" : "lupa-off"), "especie", s.name,
                "bloques", Math.round(settings.dexTrackRange), "click", menu.text(tracked ? "lupa-desactivar" : "lupa-activar")),
                () -> {
                    if (tracked) tracking.remove(p.getUniqueId());
                    else tracking.put(p.getUniqueId(), s.id);
                    openSpecies(p, s, shown);
                });

        List<Species> known = Species.ALL.stream().filter(x -> discovered(p, x)).toList();
        int at = known.indexOf(s);
        if (known.size() > 1) {
            Species prev = known.get((at - 1 + known.size()) % known.size());
            Species next = known.get((at + 1) % known.size());
            action(holder, inv, menu, "especie-anterior", menu.item("especie-anterior", "especie", prev.name),
                    () -> openSpecies(p, prev, null));
            action(holder, inv, menu, "especie-siguiente", menu.item("especie-siguiente", "especie", next.name),
                    () -> openSpecies(p, next, null));
        }
        action(holder, inv, menu, "volver", menu.item("volver"),
                () -> openIndex(p, Species.ALL.indexOf(s) / (INDEX_ROWS[0].length * 2)));
        open(p, holder, inv, title);
    }

    private ItemStack technical(com.dinoearth.text.Menu menu, Species s) {
        Lang l = lang();
        String tame = s.eggTameOnly ? l.plain("domesticar.solo-huevo") : s.tameable()
                ? l.plain("domesticar.comida", "dieta", Fmt.diet(s.diet()), "chance", Math.round(s.tameChance * 100))
                : l.plain("domesticar.imposible");
        String ride = !s.rideable ? l.plain(s.perches ? "montura.hombro" : "montura.ninguna")
                : l.plain(s.aquatic() ? "montura.bucea" : s.swims ? "montura.nada" : s.flies() ? "montura.vuela"
                : s.roars ? "montura.ruge" : s.charges ? "montura.embiste" : "montura.normal");
        String ability = ability(s);
        List<String> abilityLines = new ArrayList<>();
        if (ability != null) {
            abilityLines.add(menu.text("habilidad"));
            abilityLines.addAll(wrapLines(menu, "habilidad-linea", ability, 40));
        }
        String temper = l.plain(s.territorial ? "caracter-largo.territorial" : "caracter-largo." + s.temper.name());
        return menu.item("ficha", "vida", Math.round(s.health), "dano", Math.round(s.damage),
                "velocidad", String.format("%.2f", s.speed).replace('.', ','),
                "caracter", wrapLines(menu, "caracter", temper, 40), "comida", Fmt.food(s.diet()),
                "domesticar", tame, "montura", ride, "crias", l.plain(s.rig.hasEgg() ? "crias.pone" : "crias.ninguna"),
                "habilidad", abilityLines);
    }

    // ------------------------------------------------------------ ficha de un dino
    /** Lo que se ve al hacerle click derecho a un dino con el libro. */
    public void showCard(Player p, Dino d) {
        Species s = d.species();
        discover(p, s, d.variant());
        com.dinoearth.text.Menu menu = yml();
        Lang l = lang();
        DinoRig.Variant v = s.rig.variant(d.variant());
        String name = d.name() != null ? PlainTextComponentSerializer.plainText().serialize(d.name()) : s.name;
        Menu holder = new Menu();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.empty());
        boolean male = d.isMale();
        String age = d.isAdult() ? l.plain(male ? "edad.Adulto" : "edad.Adulta")
                : l.plain("edad.cria-porcentaje", "porcentaje", Math.round(d.growth() * 100));
        var maxAttr = d.wolf().getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : s.health;
        double hp = d.wolf().getHealth();
        boolean tamed = d.wolf().isTamed();
        Double hunger = tamed && d.tracksHunger() ? (double) d.hunger() : null;
        TextColor hungerColor = hunger == null ? null : hunger <= 0 ? TextColor.color(0x6A1A1A)
                : hunger < 0.3 ? TextColor.color(0xC87A28) : TextColor.color(0x5A9A3A);
        List<DexLayout.Line> lines = new ArrayList<>();
        if (tamed) {
            String owner = d.ownerName();
            lines.add(new DexLayout.Line(menu.text("pagina-dueno", "player", owner != null ? owner : "?"), DexLayout.INK));
            lines.add(new DexLayout.Line(l.plain("ordenes-ficha." + d.order().name()), DexLayout.INK_SOFT));
            lines.add(new DexLayout.Line(l.plain("combate-ficha." + d.combat().name()), DexLayout.INK_SOFT));
        } else {
            lines.add(new DexLayout.Line(menu.text("pagina-salvaje"), DexLayout.INK_RED));
            lines.add(new DexLayout.Line(temperWord(s), DexLayout.INK_SOFT));
        }
        String color = s.colorName(v.id()) + (v.weight() <= 5 ? Lang.strip(menu.text("raro")) : "");
        Component title = DexLayout.card(gui, new DexLayout.CardView(s.id, v.id(), name,
                d.name() != null ? s.name : menu.text("pagina-ficha"), male, Fmt.sexTitle(male), age, color,
                v.weight() <= 5, hp / max, hunger, hungerColor, lines));

        ItemStack photo = menu.item("ficha-foto", "dino", name, "descripcion", wrapLines(menu, "descripcion", s.about, 40));
        tag(photo);
        for (int slot : menu.slots("ficha-foto")) inv.setItem(slot, photo);
        List<String> hungerLine = hunger != null ? List.of(menu.text("hambre", "hambre", Math.round(hunger * 100))) : List.of();
        List<String> patrol = tamed && d.order() == Dino.Order.PATROL && d.patrolLabel() != null
                ? List.of(menu.text("patrulla", "chunk", d.patrolLabel())) : List.of();
        ItemStack info = menu.item("ficha-datos", "dino", name, "vida", Math.round(hp), "vida_max", Math.round(max),
                "hambre", hungerLine, "patrulla", patrol, "sexo", Fmt.sex(male), "edad", age);
        tag(info);
        for (int slot : menu.slots("ficha-datos")) inv.setItem(slot, info);
        ItemStack genes = genesItem(menu, d);
        if (genes != null) put(inv, menu, "ficha-genes", genes);
        action(holder, inv, menu, "ver-especie", menu.item("ver-especie", "especie", s.name), () -> openSpecies(p, s, v.id()));
        action(holder, inv, menu, "cerrar", menu.item("cerrar"), p::closeInventory);
        open(p, holder, inv, title);
    }

    /** Genetica del dino: rasgos, calidad, generacion, padres y si su color es una mutacion. */
    private ItemStack genesItem(com.dinoearth.text.Menu menu, Dino d) {
        com.dinoearth.dino.Genes g = d.genes();
        if (g == null) return null;
        Species s = d.species();
        List<String> parents = new ArrayList<>();
        if (g.mother == null && g.father == null) {
            parents.add(menu.text("genes-salvaje"));
        } else {
            parents.add(menu.text("genes-madre", "madre", Fmt.parent(s, g.mother)));
            parents.add(menu.text("genes-padre", "padre", Fmt.parent(s, g.father)));
        }
        return menu.item("ficha-genes", null, s.rig.eggIcon() != null ? s.rig.eggIcon().toString() : null,
                "vida", com.dinoearth.dino.Genes.percent(g.health), "velocidad", com.dinoearth.dino.Genes.percent(g.speed),
                "tamano", com.dinoearth.dino.Genes.percent(g.size), "calidad", Fmt.quality(g.score()),
                "promedio", com.dinoearth.dino.Genes.percent(g.score()), "generacion", g.generation,
                "padres", parents, "mutacion", g.mutant ? List.of(menu.text("genes-mutacion")) : List.of(),
                "apagada", settings.genetics ? List.of() : List.of(menu.text("genes-apagada")));
    }

    // ------------------------------------------------------------ misiones
    public void openMissions(Player p) {
        if (missions == null) return;
        com.dinoearth.text.Menu menu = yml();
        MissionManager.State st = missions.state(p);
        Menu holder = new Menu();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.empty());
        List<DexLayout.MissionCard> cards = new ArrayList<>();
        boolean canReroll = missions.canReroll(p);
        for (int i = 0; i < MissionManager.PER_DAY; i++) {
            MissionManager.Mission m = st.get(i);
            if (m == null) {
                cards.add(null);
                ItemStack empty = menu.item("mision-vacia");
                tag(empty);
                inv.setItem(MISSION_ICON[i], empty);
                continue;
            }
            cards.add(new DexLayout.MissionCard(m.text(), m.progress, m.target, m.done, menu.text("pagina-completada")));
            ItemStack icon = menu.item("mision", null, missionIcon(m), "mision", m.text(), "progreso", m.progress,
                    "total", m.target, "xp", missions.xpEach(), "estado", menu.text(m.done ? "mision-hecha" : "mision-en-curso"));
            if (m.done) glow(icon);
            tag(icon);
            inv.setItem(MISSION_ICON[i], icon);
            if (!m.done && canReroll) {
                int index = i;
                action(holder, inv, MISSION_REROLL[i], menu.item("cambiar"), () -> {
                    missions.reroll(p, index);
                    openMissions(p);
                });
            }
        }
        action(holder, inv, menu, "volver", menu.item("volver"), () -> openIndex(p));
        open(p, holder, inv, DexLayout.missions(gui, new DexLayout.MissionsView(menu.text("pagina-misiones"),
                menu.text("pagina-cambian", "tiempo", Fmt.time(missions.msUntilReset())), cards)));
    }

    private static String missionIcon(MissionManager.Mission m) {
        Species s = m.speciesObj();
        return switch (m.type) {
            case AVISTAR, CAZAR, DOMESTICAR -> s != null ? "dinoearth:icon/" + s.id + "/" + s.rig.variants().keySet().iterator().next()
                    : "dinoearth:gui/missions";
            case COLOR -> s != null ? "dinoearth:icon/" + s.id + "/" + m.variant : "dinoearth:gui/missions";
            case CRIAR -> "dinoearth:gui/stamp/criado";
            case MONTAR -> "dinoearth:gui/stamp/jinete";
            case COMEDERO -> "dinoearth:gui/stamp/domesticado";
            case CARRERA -> "dinoearth:gui/stamp/corredor";
        };
    }

    // ------------------------------------------------------------ menus
    private void open(Player p, Menu holder, Inventory inv, Component title) {
        Inventory real = Bukkit.createInventory(holder, 54, title);
        real.setContents(inv.getContents());
        holder.inventory = real;
        p.openInventory(real);
    }

    private void put(Inventory inv, com.dinoearth.text.Menu menu, String id, ItemStack item) {
        tag(item);
        int slot = menu.slot(id);
        if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item);
    }

    private void action(Menu holder, Inventory inv, com.dinoearth.text.Menu menu, String id, ItemStack item, Runnable run) {
        int slot = menu.slot(id);
        if (slot < 0 || slot >= inv.getSize()) return;
        action(holder, inv, slot, item, run);
    }

    private void action(Menu holder, Inventory inv, int slot, ItemStack item, Runnable run) {
        tag(item);
        inv.setItem(slot, item);
        holder.actions.put(slot, run);
    }

    private void tag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keys.dexIcon, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private static void glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    /** Click en un menu nuestro (el evento ya viene cancelado). Abre lo que corresponda al tick siguiente. */
    public Runnable click(Player p, Menu menu, int slot) {
        Runnable action = menu.actions.get(slot);
        if (action == null) return null;
        return () -> {
            p.playSound(p, "minecraft:item.book.page_turn", SoundCategory.PLAYERS, 0.8f, 1.0f);
            action.run();
        };
    }

    // ------------------------------------------------------------ huellas y rastreo (barra de accion)
    private int hudTick;

    private void tickHud() {
        if (dinos == null) return;
        hudTick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!holdsBook(p)) continue;
            String sid = tracking.get(p.getUniqueId());
            Species tracked = sid != null ? Species.byId(sid) : null;
            if (tracked != null) {
                track(p, tracked);
            } else if (hudTick % 4 == 0) {
                tracks(p);
            }
        }
    }

    private void track(Player p, Species s) {
        Set<String> seen = seen(p, s);
        Location at = p.getLocation();
        double range = settings.dexTrackRange * settings.dexTrackRange;
        Dino best = null, bestNew = null;
        double bd = range, bn = range;
        for (Dino d : dinos.all()) {
            if (d.species() != s || !d.wolf().isValid() || d.wolf().getWorld() != at.getWorld()) continue;
            double dist = d.wolf().getLocation().distanceSquared(at);
            if (dist < 4) continue;
            if (dist < bd) {
                bd = dist;
                best = d;
            }
            if (!seen.contains(d.variant()) && dist < bn) {
                bn = dist;
                bestNew = d;
            }
        }
        Dino target = bestNew != null ? bestNew : best;
        if (target == null) {
            if (hudTick % 4 == 0) lang().action(p, "rastreo-nada", "especie", s.name);
            return;
        }
        double meters = Math.sqrt(target == bestNew ? bn : bd);
        lang().action(p, target == bestNew ? "rastreo-color-nuevo" : "rastreo",
                "flecha", gui.arrow(relativeAngle(at, target.wolf().getLocation()), NamedTextColor.GOLD),
                "especie", s.name, "metros", Math.round(meters));
    }

    /** Huellas de especies que el jugador no conoce, sin decir cual ni a que distancia. */
    private void tracks(Player p) {
        Location at = p.getLocation();
        double range = settings.dexTracksRange * settings.dexTracksRange;
        Dino near = null;
        double nd = range;
        for (Dino d : dinos.all()) {
            if (!d.wolf().isValid() || d.wolf().getWorld() != at.getWorld() || discovered(p, d.species())) continue;
            double dist = d.wolf().getLocation().distanceSquared(at);
            if (dist < nd) {
                nd = dist;
                near = d;
            }
        }
        if (near == null) return;
        double h = near.species().hitbox;
        String size = lang().plain(h >= 3 ? "huellas.enormes" : h >= 2 ? "huellas.grandes" : h >= 1.2 ? "huellas.medianas"
                : "huellas.chiquitas");
        lang().action(p, "huellas", "huella", gui.footprint(DexLayout.INK_SOFT), "tamano", size,
                "flecha", gui.arrow(relativeAngle(at, near.wolf().getLocation()), NamedTextColor.GOLD));
    }

    private static double relativeAngle(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));     // mismo sentido que el yaw de Minecraft
        double rel = bearing - from.getYaw();
        rel = ((rel % 360) + 540) % 360 - 180;
        return rel;
    }

    // ------------------------------------------------------------ utilidades
    private static Object[] concat(Object[] a, Object... b) {
        Object[] out = new Object[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static String firstSeen(Species s, Set<String> seen) {
        for (String v : s.rig.variants().keySet()) {
            if (seen.contains(v)) return v;
        }
        return s.rig.variants().keySet().iterator().next();
    }

    private static String iconOf(Species s, Set<String> seen) {
        // el primer color visto, siguiendo el orden de la especie
        for (String v : s.rig.variants().keySet()) {
            if (seen.contains(v)) return "dinoearth:icon/" + s.id + "/" + v;
        }
        return "dinoearth:icon/" + s.id + "/unknown";
    }

    private static String temperWord(Species s) {
        return lang().plain(s.territorial ? "caracter.territorial" : "caracter." + s.temper.name());
    }

    private static String ability(Species s) {
        String key;
        if (s.ability != null) key = "plus-" + s.ability.name();
        else if (s.aquatic()) key = "acuatico";
        else if (s.swims) key = "nada";
        else if (s.perches) key = "escolta";
        else if (s.tail == Species.TailAttack.SPIKES) key = "puas";
        else if (s.tail == Species.TailAttack.CLUB) key = "maza";
        else if (s.temper == Species.Temper.PREY) key = "presa";
        else if (s.spits) key = "escupe";
        else if (s.roars) key = "ruge";
        else if (s.charges) key = "embiste";
        else if (s.flies()) key = "vuela";
        else if (s.temper == Species.Temper.PACK) key = "manada";
        else return null;
        return lang().plain("habilidad." + key);
    }

    /** Parte un texto largo en renglones de lore con la plantilla del menu (que tiene %linea%). */
    private static List<String> wrapLines(com.dinoearth.text.Menu menu, String template, String text, int width) {
        List<String> out = new ArrayList<>();
        for (String l : wrap(text, width)) out.add(menu.text(template, "linea", l));
        return out;
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String w : text.split(" ")) {
            if (sb.length() > 0 && sb.length() + 1 + w.length() > width) {
                out.add(sb.toString());
                sb.setLength(0);
            }
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
        }
        if (sb.length() > 0) out.add(sb.toString());
        return out;
    }
}

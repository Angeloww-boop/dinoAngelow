package com.dinoearth.dex;

import com.dinoearth.Keys;
import com.dinoearth.Settings;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.feeder.FeederManager;
import com.dinoearth.gui.GuiFont;
import com.dinoearth.model.DinoRig;
import com.dinoearth.species.Species;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 *
 * Sellos: tareas por especie (verlo, todos los colores, el color raro, domesticarlo, criarlo, montarlo...). Cada
 * sello da experiencia; una especie completa da mas y puede correr comandos (config.yml). Con el libro en la mano
 * se anotan los dinos desde mas lejos, aparecen huellas de especies que no conocés y la lupa marca hacia donde ir.
 *
 * Lo de cada jugador se guarda en el propio jugador (PDC). Los menus son de solo lectura: todo click se cancela.
 */
public final class DexManager {

    // ------------------------------------------------------------ casilleros (el dibujo esta en DexLayout)
    private static final int[][] INDEX_ROWS = {{10, 11, 12, 13, 14, 15, 16}, {28, 29, 30, 31, 32, 33, 34}};
    private static final int[] COLOR_SLOTS = {36, 37, 38, 39, 46, 47, 48};
    private static final int[] STAMP_SLOTS = {41, 42, 43, 50, 51, 52};
    private static final int RANK_SLOT = 0, PREV = 45, BACK = 49, NEXT = 53, TRACK = 44, HELP = 49, TRACKING = 51;

    private static final TextColor INK_SOFT = DexLayout.INK_SOFT;
    private static final TextColor DIM = NamedTextColor.GRAY;

    // ------------------------------------------------------------ sellos y rangos
    public enum Stamp {
        VISTO("Avistado", "Ver uno de cerca."),
        COLORES("Coleccionista", "Ver todos sus colores comunes."),
        RARO("Rareza", "Ver su color raro."),
        DOMESTICADO("Domador", "Domesticar uno."),
        CRIADO("Criador", "Que nazca una cría tuya de un huevo."),
        JINETE("Jinete", "Montarlo."),
        HOMBRO("Compañero", "Llevarlo en el hombro."),
        CAZADO("Cazador", "Derrotar uno.");

        public final String label;
        public final String task;

        Stamp(String label, String task) {
            this.label = label;
            this.task = task;
        }

        String model() {
            return name().toLowerCase();
        }
    }

    private record Rank(String name, double from, TextColor color) {
    }

    private static final List<Rank> RANKS = List.of(
            new Rank("Aprendiz", 0, TextColor.color(0x7A5236)),
            new Rank("Explorador", 0.10, TextColor.color(0x8A5A24)),
            new Rank("Rastreador", 0.25, TextColor.color(0x5E646C)),
            new Rank("Naturalista", 0.45, TextColor.color(0x9A7414)),
            new Rank("Paleontólogo", 0.70, TextColor.color(0x23786E)),
            new Rank("Leyenda jurásica", 1.0, TextColor.color(0x7A3C96)));

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
    private final Map<UUID, Integer> hudWait = new HashMap<>();
    private DinoManager dinos;
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

    // ------------------------------------------------------------ libro
    public ItemStack createBook() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString("dinoearth:dinopedia"));
        meta.itemName(Component.text("Dinopedia", NamedTextColor.GOLD));
        meta.lore(List.of(
                line("Click derecho: abrir."),
                line("Click derecho a un dinosaurio: su ficha."),
                line("En la mano: anotás dinos desde más lejos,"),
                line("ves huellas de especies que no conocés"),
                line("y la lupa te marca hacia dónde ir.")));
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

    private int totalStamps() {
        return Species.ALL.stream().mapToInt(s -> stampsOf(s).size()).sum();
    }

    private int doneStamps(Player p) {
        return Species.ALL.stream().mapToInt(s -> stamps(p, s).size()).sum();
    }

    private Rank rank(int done) {
        double f = done / (double) totalStamps();
        Rank r = RANKS.get(0);
        for (Rank k : RANKS) {
            if (f >= k.from() - 1e-9) r = k;
        }
        return r;
    }

    /** El jugador vio un dino de esta especie y color. Avisa si es nuevo. */
    public void discover(Player p, Species s, String variant) {
        Set<String> seen = seen(p, s);
        if (!seen.add(variant)) return;
        boolean first = seen.size() == 1;
        p.getPersistentDataContainer().set(keys.dexSeen(s.id), PersistentDataType.STRING, String.join(",", seen));
        DinoRig.Variant v = s.rig.variant(variant);
        if (first) {
            p.sendActionBar(Component.text("¡Nueva especie en la Dinopedia: " + s.name + "!", NamedTextColor.GOLD));
            p.playSound(p, "minecraft:entity.player.levelup", SoundCategory.PLAYERS, 0.5f, 1.4f);
        } else if (v.weight() <= 5) {
            p.sendActionBar(Component.text("¡Un " + s.name + " " + v.name().toLowerCase() + "! Es muy raro.",
                    NamedTextColor.LIGHT_PURPLE));
            p.playSound(p, "minecraft:entity.player.levelup", SoundCategory.PLAYERS, 0.5f, 1.8f);
        } else {
            p.sendActionBar(Component.text("Dinopedia: nuevo color de " + s.name + " (" + v.name() + ")",
                    NamedTextColor.YELLOW));
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

    public void onPerch(Player p, Species s) {
        award(p, s, Stamp.HOMBRO);
    }

    public void onHatch(Player p, Species s) {
        award(p, s, Stamp.CRIADO);
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
        Rank before = rank(doneStamps(p));
        have.add(stamp);
        List<String> ids = have.stream().map(Enum::name).toList();
        p.getPersistentDataContainer().set(keys.dexStamps(s.id), PersistentDataType.STRING, String.join(",", ids));

        int xp = settings.dexXpStamp;
        p.sendMessage(Component.text("Dinopedia · ", INK_SOFT)
                .append(Component.text("sello «" + stamp.label + "» de " + s.name, NamedTextColor.GOLD))
                .append(Component.text(xp > 0 ? " (+" + xp + " de experiencia)" : "", NamedTextColor.GRAY)));
        p.playSound(p, "minecraft:ui.cartography_table.take_result", SoundCategory.PLAYERS, 0.8f, 1.1f);
        if (xp > 0) p.giveExp(xp);

        if (have.containsAll(all)) {
            int bonus = settings.dexXpSpecies;
            p.sendMessage(Component.text("¡Completaste la página de " + s.name + " en la Dinopedia!", NamedTextColor.GOLD,
                    TextDecoration.BOLD).append(Component.text(bonus > 0 ? " +" + bonus + " de experiencia" : "",
                    NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
            p.playSound(p, "minecraft:ui.toast.challenge_complete", SoundCategory.PLAYERS, 0.6f, 1.2f);
            if (bonus > 0) p.giveExp(bonus);
            run(settings.dexSpeciesCommands, p, s);
        }
        int done = doneStamps(p);
        Rank after = rank(done);
        if (after != before) {
            p.sendMessage(Component.text("Nuevo rango de paleontólogo: ", INK_SOFT)
                    .append(Component.text(after.name(), after.color(), TextDecoration.BOLD)));
            p.playSound(p, "minecraft:entity.player.levelup", SoundCategory.PLAYERS, 0.7f, 0.9f);
        }
        if (done == totalStamps()) run(settings.dexAllCommands, p, s);
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
        Menu menu = new Menu();
        int perPage = INDEX_ROWS[0].length * 2;
        int pages = (Species.ALL.size() + perPage - 1) / perPage;
        page = Math.max(0, Math.min(pages - 1, page));
        int found = (int) Species.ALL.stream().filter(s -> discovered(p, s)).count();
        int total = totalStamps(), done = doneStamps(p);
        Rank rank = rank(done);
        int rankIndex = RANKS.indexOf(rank);

        List<Integer> bars = new ArrayList<>();
        List<Boolean> completeBars = new ArrayList<>();

        Inventory inv = Bukkit.createInventory(menu, 54, Component.empty());
        List<Component> progress = new ArrayList<>(List.of(
                line("Especies descubiertas: " + found + " de " + Species.ALL.size()),
                line("Sellos de investigación: " + done + " de " + total)));
        Rank next = rankIndex + 1 < RANKS.size() ? RANKS.get(rankIndex + 1) : null;
        if (next != null) {
            int need = (int) Math.ceil(next.from() * total) - done;
            progress.add(line("Faltan " + need + " sellos para " + next.name() + "."));
        }
        List<Component> rankLore = new ArrayList<>(progress);
        rankLore.add(Component.empty());
        for (Rank r : RANKS) {
            boolean got = RANKS.indexOf(r) <= rankIndex;
            rankLore.add(Component.text((got ? "✔ " : "· ") + r.name() + " (" + Math.round(r.from() * 100) + "%)",
                    got ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        inv.setItem(RANK_SLOT, icon("dinoearth:gui/rank" + rankIndex,
                Component.text("Rango: " + rank.name(), NamedTextColor.GOLD), rankLore));
        for (int slot = 1; slot <= 8; slot++) {
            inv.setItem(slot, icon("dinoearth:gui/blank", Component.text("Investigación", NamedTextColor.GOLD), progress));
        }

        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= Species.ALL.size()) break;
            Species s = Species.ALL.get(idx);
            int row = i / INDEX_ROWS[0].length, col = i % INDEX_ROWS[0].length;
            int slot = INDEX_ROWS[row][col];
            if (!discovered(p, s)) {
                inv.setItem(slot, icon("dinoearth:icon/" + s.id + "/unknown", Component.text("???", NamedTextColor.DARK_GRAY),
                        List.of(line("Todavía no lo viste."), line("Mirá uno de cerca para anotarlo."))));
                bars.add(-1);
                completeBars.add(false);
                continue;
            }
            Set<String> seen = seen(p, s);
            Set<Stamp> have = stamps(p, s);
            List<Stamp> all = stampsOf(s);
            boolean complete = have.size() == all.size();
            List<Component> lore = new ArrayList<>();
            for (String l : wrap(s.about, 36)) lore.add(line(l));
            lore.add(Component.empty());
            lore.add(line("Colores vistos: " + seen.size() + "/" + s.rig.variants().size()));
            lore.add(Component.text("Sellos: " + have.size() + "/" + all.size() + (complete ? " ✔ completa" : ""),
                    complete ? NamedTextColor.GOLD : DIM).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Click para abrir su página", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            inv.setItem(slot, icon(iconOf(s, seen), Component.text(s.name, complete ? NamedTextColor.GOLD : NamedTextColor.GREEN), lore));
            bars.add((int) Math.round(8.0 * have.size() / all.size()));
            completeBars.add(complete);
            menu.actions.put(slot, () -> openSpecies(p, s, null));
        }

        if (page > 0) {
            int prev = page - 1;
            inv.setItem(PREV, icon("dinoearth:gui/prev", Component.text("Página anterior", NamedTextColor.YELLOW), List.of()));
            menu.actions.put(PREV, () -> openIndex(p, prev));
        }
        if (page < pages - 1) {
            int nextPage = page + 1;
            inv.setItem(NEXT, icon("dinoearth:gui/next", Component.text("Página siguiente", NamedTextColor.YELLOW), List.of()));
            menu.actions.put(NEXT, () -> openIndex(p, nextPage));
        }
        inv.setItem(HELP, icon("dinoearth:gui/help", Component.text("Cómo se usa", NamedTextColor.GOLD), List.of(
                line("Las especies se anotan solas al verlas de cerca"),
                line("(con la Dinopedia en la mano, desde más lejos)."),
                line("Click derecho a un dino con el libro: su ficha."),
                Component.empty(),
                line("Huellas: con el libro en la mano te avisa si hay"),
                line("una especie que no conocés cerca y hacia dónde."),
                line("Lupa: en la página de una especie, rastreala."),
                Component.empty(),
                line("Sellos: cada especie tiene tareas (verla, sus"),
                line("colores, domesticarla, montarla...). Cada sello"),
                line("da experiencia y te sube de rango."))));
        String tracked = tracking.get(p.getUniqueId());
        Species trackedSpecies = tracked != null ? Species.byId(tracked) : null;
        if (trackedSpecies != null) {
            inv.setItem(TRACKING, icon("dinoearth:gui/track", Component.text("Rastreando: " + trackedSpecies.name,
                    NamedTextColor.GOLD), List.of(line("Con la Dinopedia en la mano te marca"),
                    line("hacia dónde está el más cercano."), Component.empty(),
                    Component.text("Click para dejar de rastrear", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))));
            int current = page;
            menu.actions.put(TRACKING, () -> {
                tracking.remove(p.getUniqueId());
                openIndex(p, current);
            });
        }
        open(p, menu, inv, DexLayout.index(gui, new DexLayout.IndexView(rank.name(), rank.color(), done, total, bars,
                completeBars)));
    }

    // ------------------------------------------------------------ pagina de una especie
    public void openSpecies(Player p, Species s, String variant) {
        if (!discovered(p, s)) {
            openIndex(p);
            return;
        }
        Set<String> seen = seen(p, s);
        if (variant == null || !seen.contains(variant)) variant = firstSeen(s, seen);
        String shown = variant;
        Menu menu = new Menu();
        String diet = switch (s.diet()) {
            case MEAT -> "Carnívoro";
            case PLANTS -> "Herbívoro";
            case FISH -> "Piscívoro";
        };
        String dietIcon = switch (s.diet()) {
            case MEAT -> "meat";
            case PLANTS -> "leaf";
            case FISH -> "fish";
        };
        Component title = DexLayout.species(gui, new DexLayout.SpeciesView(s.id, shown, s.name, dietIcon, diet, List.of(
                new DexLayout.Stat("heart", s.health / maxHealth, TextColor.color(0xB43A32)),
                new DexLayout.Stat("sword", s.damage / maxDamage, TextColor.color(0x7C6A5A)),
                new DexLayout.Stat("speed", s.speed / maxSpeed, TextColor.color(0x3C82BE)),
                new DexLayout.Stat("size", s.hitbox / maxSize, TextColor.color(0x8A6A3A))),
                temperWord(s), "Colores: " + seen.size() + "/" + s.rig.variants().size()));

        Inventory inv = Bukkit.createInventory(menu, 54, Component.empty());
        List<Component> about = new ArrayList<>();
        for (String l : wrap(s.about, 36)) about.add(line(l));
        about.add(Component.empty());
        about.add(line("Tamaño de un adulto: " + s.size));
        if (s.tameable()) about.add(line("Domesticados por vos: " + tamed(p, s)));
        ItemStack photo = icon("dinoearth:gui/blank", Component.text(s.name + " · " + s.rig.variant(shown).name(),
                NamedTextColor.GREEN), about);
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) inv.setItem(r * 9 + c, photo);
        }
        ItemStack sheet = icon("dinoearth:gui/blank", Component.text("Ficha técnica", NamedTextColor.GOLD), technical(s));
        for (int r = 0; r < 4; r++) {
            for (int c = 5; c < 9; c++) inv.setItem(r * 9 + c, sheet);
        }

        int i = 0;
        for (DinoRig.Variant v : s.rig.variants().values()) {
            if (i >= COLOR_SLOTS.length) break;
            int slot = COLOR_SLOTS[i++];
            String rare = v.weight() <= 5 ? " · raro" : "";
            if (seen.contains(v.id())) {
                boolean current = v.id().equals(shown);
                ItemStack it = icon("dinoearth:icon/" + s.id + "/" + v.id(), Component.text(v.name(), NamedTextColor.AQUA),
                        List.of(line("Visto" + rare), Component.text(current ? "En la foto" : "Click para verlo en la foto",
                                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
                if (current) {
                    ItemMeta m = it.getItemMeta();
                    m.setEnchantmentGlintOverride(true);
                    it.setItemMeta(m);
                } else {
                    menu.actions.put(slot, () -> openSpecies(p, s, v.id()));
                }
                inv.setItem(slot, it);
            } else {
                inv.setItem(slot, icon("dinoearth:icon/" + s.id + "/unknown",
                        Component.text("???", NamedTextColor.DARK_GRAY), List.of(line("Color sin ver" + rare))));
            }
        }

        Set<Stamp> have = stamps(p, s);
        List<Stamp> all = stampsOf(s);
        for (int k = 0; k < all.size() && k < STAMP_SLOTS.length; k++) {
            Stamp st = all.get(k);
            boolean got = have.contains(st);
            String task = st == Stamp.DOMESTICADO && s.eggTameOnly ? "Criar uno desde el huevo." : st.task;
            inv.setItem(STAMP_SLOTS[k], icon("dinoearth:gui/stamp/" + st.model() + (got ? "" : "_off"),
                    Component.text("Sello: " + st.label, got ? NamedTextColor.GOLD : NamedTextColor.GRAY),
                    List.of(line(task), Component.text(got ? "✔ Conseguido" : "Pendiente",
                            got ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))));
        }

        boolean tracked = s.id.equals(tracking.get(p.getUniqueId()));
        inv.setItem(TRACK, icon(tracked ? "dinoearth:gui/track" : "dinoearth:gui/track_off",
                Component.text(tracked ? "Rastreando" : "Rastrear", NamedTextColor.GOLD), List.of(
                        line("Con la Dinopedia en la mano te marca hacia"),
                        line("dónde está el " + s.name + " más cercano"),
                        line("(hasta " + Math.round(settings.dexTrackRange) + " bloques; primero los colores"),
                        line("que todavía no viste)."), Component.empty(),
                        Component.text(tracked ? "Click para dejar de rastrear" : "Click para rastrear",
                                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))));
        menu.actions.put(TRACK, () -> {
            if (tracked) {
                tracking.remove(p.getUniqueId());
            } else {
                tracking.put(p.getUniqueId(), s.id);
                hudWait.remove(p.getUniqueId());
            }
            openSpecies(p, s, shown);
        });

        List<Species> known = Species.ALL.stream().filter(x -> discovered(p, x)).toList();
        int at = known.indexOf(s);
        if (known.size() > 1) {
            Species prev = known.get((at - 1 + known.size()) % known.size());
            Species next = known.get((at + 1) % known.size());
            inv.setItem(PREV, icon("dinoearth:gui/prev", Component.text("← " + prev.name, NamedTextColor.YELLOW), List.of()));
            inv.setItem(NEXT, icon("dinoearth:gui/next", Component.text(next.name + " →", NamedTextColor.YELLOW), List.of()));
            menu.actions.put(PREV, () -> openSpecies(p, prev, null));
            menu.actions.put(NEXT, () -> openSpecies(p, next, null));
        }
        inv.setItem(BACK, icon("dinoearth:dinopedia", Component.text("Volver al índice", NamedTextColor.YELLOW), List.of()));
        menu.actions.put(BACK, () -> openIndex(p, Species.ALL.indexOf(s) / (INDEX_ROWS[0].length * 2)));
        open(p, menu, inv, title);
    }

    private List<Component> technical(Species s) {
        List<Component> out = new ArrayList<>();
        out.add(line("Vida " + Math.round(s.health) + " · daño " + Math.round(s.damage) + " · velocidad "
                + String.format("%.2f", s.speed).replace('.', ',')));
        out.add(Component.empty());
        out.add(head("Carácter"));
        for (String l : wrap(temper(s), 40)) out.add(line(l));
        out.add(head("Dieta"));
        out.add(line("Come " + FeederManager.foodText(s.diet()) + "."));
        out.add(head("Domesticar"));
        out.add(line(s.eggTameOnly ? "Solo naciendo de un huevo." : s.tameable()
                ? "Dándole " + s.diet().label + " (" + Math.round(s.tameChance * 100) + "% por bocado)." : "No se domestica."));
        out.add(head("Montura"));
        out.add(line(!s.rideable ? (s.perches ? "No se monta: va en tu hombro." : "No se puede montar.")
                : s.flies() ? "Sí: espacio despega, W vuela hacia donde mirás."
                : s.roars ? "Sí: espacio ruge en vez de saltar."
                : s.charges ? "Sí: corriendo arrolla lo que tenga adelante." : "Sí: rápido y salta alto."));
        out.add(head("Crías"));
        out.add(line(s.rig.hasEgg() ? "Macho y hembra domesticados y bien alimentados ponen huevos." : "No pone huevos."));
        String ability = ability(s);
        if (ability != null) {
            out.add(head("Habilidad"));
            for (String l : wrap(ability, 40)) out.add(line(l));
        }
        return out;
    }

    // ------------------------------------------------------------ ficha de un dino
    /** Lo que se ve al hacerle click derecho a un dino con el libro. */
    public void showCard(Player p, Dino d) {
        Species s = d.species();
        discover(p, s, d.variant());
        DinoRig.Variant v = s.rig.variant(d.variant());
        String name = d.name() != null ? PlainTextComponentSerializer.plainText().serialize(d.name()) : s.name;
        Menu menu = new Menu();
        boolean male = d.isMale();
        String age = d.isAdult() ? (male ? "Adulto" : "Adulta") : "Cría (" + Math.round(d.growth() * 100) + "%)";
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
            lines.add(new DexLayout.Line("de " + (owner != null ? owner : "?"), DexLayout.INK));
            String order = switch (d.order()) {
                case FOLLOW -> "Te sigue";
                case STAY -> "Quieto";
                case PATROL -> "Patrulla";
            };
            String combat = switch (d.combat()) {
                case PASSIVE -> "Pasivo";
                case DEFENSIVE -> "Defensivo";
                case AGGRESSIVE -> "Agresivo";
            };
            lines.add(new DexLayout.Line(order, INK_SOFT));
            lines.add(new DexLayout.Line(combat, INK_SOFT));
        } else {
            lines.add(new DexLayout.Line("Salvaje", DexLayout.INK_RED));
            lines.add(new DexLayout.Line(temperWord(s), INK_SOFT));
        }
        Component title = DexLayout.card(gui, new DexLayout.CardView(s.id, v.id(), name, d.name() != null ? s.name : "Ficha",
                male, age, v.name(), v.weight() <= 5, hp / max, hunger, hungerColor, lines));

        Inventory inv = Bukkit.createInventory(menu, 54, Component.empty());
        List<Component> about = new ArrayList<>();
        for (String l : wrap(s.about, 36)) about.add(line(l));
        ItemStack photo = icon("dinoearth:gui/blank", Component.text(name, NamedTextColor.GREEN), about);
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) inv.setItem(r * 9 + c, photo);
        }
        List<Component> facts = new ArrayList<>();
        facts.add(line("Vida: " + Math.round(hp) + " / " + Math.round(max)));
        if (tamed && d.tracksHunger()) {
            int h = Math.round(d.hunger() * 100);
            facts.add(line("Hambre: " + h + "%" + (h <= 0 ? " — ¡muerto de hambre!" : "")));
        }
        if (tamed && d.order() == Dino.Order.PATROL && d.patrolLabel() != null) facts.add(line("Patrulla el " + d.patrolLabel()));
        facts.add(line(male ? "Macho" : "Hembra"));
        facts.add(line(d.isAdult() ? "Adulto" : "Cría: " + Math.round(d.growth() * 100) + "% crecida"));
        ItemStack info = icon("dinoearth:gui/blank", Component.text(name, NamedTextColor.GOLD), facts);
        for (int r = 0; r < 5; r++) {
            for (int c = 5; c < 9; c++) inv.setItem(r * 9 + c, info);
        }
        inv.setItem(BACK, icon("dinoearth:dinopedia", Component.text("Ver la página de " + s.name, NamedTextColor.YELLOW),
                List.of()));
        menu.actions.put(BACK, () -> openSpecies(p, s, v.id()));
        open(p, menu, inv, title);
    }

    // ------------------------------------------------------------ menus
    private void open(Player p, Menu menu, Inventory inv, Component title) {
        Inventory real = Bukkit.createInventory(menu, 54, title);
        real.setContents(inv.getContents());
        menu.inventory = real;
        p.openInventory(real);
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
            if (hudTick % 4 == 0) {
                p.sendActionBar(Component.text("No hay rastros de " + s.name + " cerca", INK_SOFT));
            }
            return;
        }
        Location to = target.wolf().getLocation();
        double meters = Math.sqrt(target == bestNew ? bn : bd);
        Component bar = gui.arrow(relativeAngle(at, to), NamedTextColor.GOLD)
                .append(Component.text(" " + s.name + " · " + Math.round(meters) + " m", NamedTextColor.YELLOW));
        if (target == bestNew) bar = bar.append(Component.text(" · color nuevo", NamedTextColor.LIGHT_PURPLE));
        p.sendActionBar(bar);
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
        String size = near.species().hitbox >= 3 ? "enormes" : near.species().hitbox >= 2 ? "grandes"
                : near.species().hitbox >= 1.2 ? "medianas" : "chiquitas";
        p.sendActionBar(gui.footprint(INK_SOFT)
                .append(Component.text(" Huellas " + size + " de algo que no conocés ", NamedTextColor.GRAY))
                .append(gui.arrow(relativeAngle(at, near.wolf().getLocation()), NamedTextColor.GOLD)));
    }

    private static double relativeAngle(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));     // mismo sentido que el yaw de Minecraft
        double rel = bearing - from.getYaw();
        rel = ((rel % 360) + 540) % 360 - 180;
        return rel;
    }

    // ------------------------------------------------------------ utilidades
    private ItemStack icon(String model, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(model));
        meta.itemName(name);
        meta.lore(lore);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(keys.dexIcon, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
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
        if (s.territorial) return "Territorial";
        return switch (s.temper) {
            case CARNIVORE -> "Neutral";
            case HERBIVORE -> "Tranquilo";
            case HOSTILE -> "Hostil";
            case PACK -> "En manada";
            case PREY -> "Asustadizo";
        };
    }

    private static String temper(Species s) {
        if (s.territorial) return "Salvaje te persigue. Domesticado es leal.";
        return switch (s.temper) {
            case CARNIVORE -> "Neutral: caza animales chicos y se defiende.";
            case HERBIVORE -> "Tranquilo, pero embiste si lo atacan.";
            case HOSTILE -> "Hostil: ataca a los jugadores que ve.";
            case PACK -> "Anda en manada y ataca a los heridos o si son muchos.";
            case PREY -> "Huye en manada. Acercate agachado para darle de comer.";
        };
    }

    private static String ability(Species s) {
        if (s.perches) return "Agachado + click derecho: se sube a tu hombro y chilla si hay monstruos cerca.";
        if (s.tail == Species.TailAttack.SPIKES) return "Coletazo con púas al que le pega por atrás.";
        if (s.tail == Species.TailAttack.CLUB) return "Maza en la cola que tira lejos; las flechas casi no le hacen daño.";
        if (s.temper == Species.Temper.PREY) return "Si uno se asusta, avisa y sale corriendo toda la manada.";
        if (s.spits) return "Escupe veneno: ceguera y veneno.";
        if (s.roars) return "Rugido: lentitud y oscuridad a los que estén cerca.";
        if (s.charges) return "Embestida que tira lejos.";
        if (s.flies()) return "Vuela. Si lo lastimás salvaje, se escapa por el aire.";
        if (s.temper == Species.Temper.PACK) return "Caza en grupo.";
        return null;
    }

    private static Component head(String text) {
        return Component.text(text, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
    }

    private static Component line(String text) {
        return Component.text(text, DIM).decoration(TextDecoration.ITALIC, false);
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

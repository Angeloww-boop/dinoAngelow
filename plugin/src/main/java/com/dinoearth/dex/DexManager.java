package com.dinoearth.dex;

import com.dinoearth.Keys;
import com.dinoearth.dino.Dino;
import com.dinoearth.feeder.FeederManager;
import com.dinoearth.model.DinoRig;
import com.dinoearth.species.Species;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dinopedia: un libro que abre un menu con las especies. Cada especie se descubre al verla de cerca
 * (y cada color por separado). Lo que vio cada jugador se guarda en el propio jugador (PDC).
 * Los menus son de solo lectura: todos los clicks se cancelan y no da ni recibe items.
 */
public final class DexManager {

    private static final int[] SPECIES_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int[] COLOR_SLOTS = {37, 38, 39, 40, 41, 42, 43};
    private static final int BACK = 49;
    private static final TextColor DIM = NamedTextColor.GRAY;

    private final Keys keys;

    /** Identifica nuestros menus (nunca por el titulo). species == null es el indice. */
    public static final class Menu implements InventoryHolder {
        private final Species species;
        private final Map<Integer, Species> links = new HashMap<>();
        private Inventory inventory;

        private Menu(Species species) {
            this.species = species;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public DexManager(Keys keys) {
        this.keys = keys;
    }

    // ------------------------------------------------------------ libro
    public ItemStack createBook() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString("dinoearth:dinopedia"));
        meta.itemName(Component.text("Dinopedia", NamedTextColor.GOLD));
        meta.lore(List.of(
                line("Click derecho: abrir."),
                line("Click derecho a un dinosaurio: ver su ficha."),
                line("Las especies se anotan solas al verlas de cerca.")));
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(keys.dexBook, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBook(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(keys.dexBook);
    }

    /** Un icono de nuestros menus que por algun motivo termino afuera (no deberia pasar nunca). */
    public boolean isIcon(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(keys.dexIcon);
    }

    // ------------------------------------------------------------ progreso del jugador
    public Set<String> seen(Player p, Species s) {
        String raw = p.getPersistentDataContainer().get(keys.dexSeen(s.id), PersistentDataType.STRING);
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
    }

    public void addTamed(Player p, Species s, String variant) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(keys.dexTamed(s.id), PersistentDataType.INTEGER, tamed(p, s) + 1);
        discover(p, s, variant);
    }

    // ------------------------------------------------------------ menus
    public void openIndex(Player p) {
        Menu menu = new Menu(null);
        int found = (int) Species.ALL.stream().filter(s -> discovered(p, s)).count();
        Inventory inv = Bukkit.createInventory(menu, 54,
                Component.text("Dinopedia · " + found + "/" + Species.ALL.size()));
        menu.inventory = inv;
        frame(inv);
        inv.setItem(4, icon("dinoearth:dinopedia", Component.text("Dinopedia", NamedTextColor.GOLD), List.of(
                line("Especies descubiertas: " + found + " de " + Species.ALL.size()),
                line("Mirá un dinosaurio de cerca para anotarlo."),
                line("Con el libro en la mano, click derecho"),
                line("a un dinosaurio para ver su ficha."))));
        for (int i = 0; i < Species.ALL.size() && i < SPECIES_SLOTS.length; i++) {
            Species s = Species.ALL.get(i);
            int slot = SPECIES_SLOTS[i];
            if (!discovered(p, s)) {
                inv.setItem(slot, icon("dinoearth:icon/" + s.id + "/unknown",
                        Component.text("???", NamedTextColor.DARK_GRAY), List.of(line("Todavía no lo viste."))));
                continue;
            }
            Set<String> seen = seen(p, s);
            List<Component> lore = new ArrayList<>();
            for (String l : wrap(s.about, 36)) lore.add(line(l));
            lore.add(Component.empty());
            lore.add(line("Dieta: " + s.diet().label));
            lore.add(line("Colores vistos: " + seen.size() + "/" + s.rig.variants().size()));
            if (s.tameable()) lore.add(line("Domesticados: " + tamed(p, s)));
            lore.add(Component.empty());
            lore.add(Component.text("Click para ver la ficha", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            inv.setItem(slot, icon(iconOf(s, seen), Component.text(s.name, NamedTextColor.GREEN), lore));
            menu.links.put(slot, s);
        }
        p.openInventory(inv);
    }

    public void openSpecies(Player p, Species s) {
        Menu menu = new Menu(s);
        Inventory inv = Bukkit.createInventory(menu, 54, Component.text("Dinopedia · " + s.name));
        menu.inventory = inv;
        frame(inv);
        Set<String> seen = seen(p, s);

        List<Component> head = new ArrayList<>();
        for (String l : wrap(s.about, 36)) head.add(line(l));
        head.add(Component.empty());
        head.add(line("Tamaño: " + s.size));
        if (s.tameable()) head.add(line("Domesticados: " + tamed(p, s)));
        inv.setItem(4, icon(iconOf(s, seen), Component.text(s.name, NamedTextColor.GREEN), head));

        inv.setItem(19, info("minecraft:" + switch (s.diet()) {
            case MEAT -> "beef";
            case PLANTS -> "wheat";
            case FISH -> "cod";
        }, "Dieta", "Come " + FeederManager.foodText(s.diet()) + "."));
        inv.setItem(20, info(s.hostile() || s.territorial ? "minecraft:iron_sword" : "minecraft:shield", "Carácter",
                temper(s)));
        inv.setItem(21, info("minecraft:lead", "Domesticar", s.eggTameOnly ? "Solo naciendo de un huevo."
                : s.tameable() ? "Dándole " + s.diet().label + " (" + Math.round(s.tameChance * 100) + "% por bocado)."
                : "No se domestica."));
        inv.setItem(22, info("minecraft:saddle", "Montura", !s.rideable ? "No se puede montar."
                : s.flies() ? "Sí: espacio despega, W vuela hacia donde mirás."
                : s.roars ? "Sí: espacio ruge en vez de saltar."
                : s.charges ? "Sí: corriendo arrolla lo que tenga adelante." : "Sí: rápido y salta alto."));
        inv.setItem(23, info("minecraft:turtle_egg", "Crías", s.rig.hasEgg()
                ? "Un macho y una hembra domesticados y bien alimentados ponen huevos." : "No pone huevos."));
        inv.setItem(24, info("minecraft:spyglass", "Tamaño", s.size + ". Vida " + Math.round(s.health)
                + ", daño " + Math.round(s.damage) + "."));
        String ability = ability(s);
        if (ability != null) inv.setItem(25, info("minecraft:blaze_powder", "Habilidad", ability));

        int i = 0;
        for (DinoRig.Variant v : s.rig.variants().values()) {
            if (i >= COLOR_SLOTS.length) break;
            int slot = COLOR_SLOTS[i++];
            String rare = v.weight() <= 5 ? " · raro" : "";
            if (seen.contains(v.id())) {
                inv.setItem(slot, icon("dinoearth:icon/" + s.id + "/" + v.id(),
                        Component.text(v.name(), NamedTextColor.AQUA), List.of(line("Visto" + rare))));
            } else {
                inv.setItem(slot, icon("dinoearth:icon/" + s.id + "/unknown",
                        Component.text("???", NamedTextColor.DARK_GRAY), List.of(line("Color sin ver" + rare))));
            }
        }
        inv.setItem(BACK, icon("minecraft:arrow", Component.text("Volver", NamedTextColor.YELLOW), List.of()));
        p.openInventory(inv);
    }

    /** Click en un menu nuestro (el evento ya viene cancelado). Abre lo que corresponda al tick siguiente. */
    public Runnable click(Player p, Menu menu, int slot) {
        if (menu.species == null) {
            Species s = menu.links.get(slot);
            return s != null ? () -> openSpecies(p, s) : null;
        }
        return slot == BACK ? () -> openIndex(p) : null;
    }

    // ------------------------------------------------------------ ficha de un dino
    /** Lo que se ve al hacerle click derecho a un dino con el libro. */
    public void showCard(Player p, Dino d) {
        Species s = d.species();
        discover(p, s, d.variant());
        var max = d.wolf().getAttribute(Attribute.MAX_HEALTH);
        String sex = d.isMale() ? "macho" : "hembra";
        String age = d.isAdult() ? "adulto" : "cría (" + Math.round(d.growth() * 100) + "% crecido)";
        p.sendMessage(Component.text("── " + s.name + " ──", NamedTextColor.GOLD));
        p.sendMessage(Component.text(s.rig.variant(d.variant()).name() + " · " + sex + " · " + age, NamedTextColor.WHITE));
        p.sendMessage(Component.text("Vida: " + Math.round(d.wolf().getHealth()) + " / "
                + (max != null ? Math.round(max.getValue()) : "?"), NamedTextColor.RED));
        if (d.wolf().isTamed()) {
            if (d.tracksHunger()) {
                int h = Math.round(d.hunger() * 100);
                NamedTextColor c = h <= 0 ? NamedTextColor.DARK_RED : h < 30 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
                p.sendMessage(Component.text("Hambre: " + bar(d.hunger()) + " " + h + "%"
                        + (h <= 0 ? " — ¡muerto de hambre!" : ""), c));
            }
            var owner = d.wolf().getOwner();
            p.sendMessage(Component.text("Dueño: " + (owner != null && owner.getName() != null ? owner.getName() : "?"),
                    NamedTextColor.GRAY));
        } else {
            p.sendMessage(Component.text("Salvaje", NamedTextColor.GRAY));
        }
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

    private ItemStack info(String model, String title, String text) {
        List<Component> lore = new ArrayList<>();
        for (String l : wrap(text, 36)) lore.add(line(l));
        return icon(model, Component.text(title, NamedTextColor.YELLOW), lore);
    }

    /** Bordes de vidrio gris (sin tooltip). */
    private void frame(Inventory inv) {
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9, col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                ItemStack pane = icon("minecraft:gray_stained_glass_pane", Component.empty(), List.of());
                ItemMeta meta = pane.getItemMeta();
                meta.setHideTooltip(true);
                pane.setItemMeta(meta);
                inv.setItem(slot, pane);
            }
        }
    }

    private static String iconOf(Species s, Set<String> seen) {
        // el primer color visto, siguiendo el orden de la especie
        for (String v : s.rig.variants().keySet()) {
            if (seen.contains(v)) return "dinoearth:icon/" + s.id + "/" + v;
        }
        return "dinoearth:icon/" + s.id + "/unknown";
    }

    private static String temper(Species s) {
        if (s.territorial) return "Salvaje te persigue. Domesticado es leal.";
        return switch (s.temper) {
            case CARNIVORE -> "Neutral: caza animales chicos y se defiende.";
            case HERBIVORE -> "Tranquilo, pero embiste si lo atacan.";
            case HOSTILE -> "Hostil: ataca a los jugadores que ve.";
            case PACK -> "Anda en manada y ataca a los heridos o si son muchos.";
        };
    }

    private static String ability(Species s) {
        if (s.spits) return "Escupe veneno: ceguera y veneno.";
        if (s.roars) return "Rugido: lentitud y oscuridad a los que estén cerca.";
        if (s.charges) return "Embestida que tira lejos.";
        if (s.flies()) return "Vuela. Si lo lastimás salvaje, se escapa por el aire.";
        if (s.temper == Species.Temper.PACK) return "Caza en grupo.";
        return null;
    }

    private static String bar(float f) {
        int n = Math.round(Math.max(0, Math.min(1, f)) * 10);
        return "█".repeat(n) + "░".repeat(10 - n);
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

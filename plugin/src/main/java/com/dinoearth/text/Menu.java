package com.dinoearth.text;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Un menu leido de menus/*.yml con el formato del server:
 * <pre>
 * menu_title: '&amp;8| &amp;9Nombre'
 * size: 54
 * items:
 *   'relleno': {material: black_stained_glass_pane, slots: [0-9, 17, ...], display_name: ' '}
 *   'volver':  {material: barrier, display_name: '&amp;#EE193BVolver', slot: 49}
 * </pre>
 * Ademas de material se puede poner model (item_model del pack). Los items que arma el codigo (uno por dino, por
 * especie...) usan su entrada como plantilla con placeholders; el resto se pone tal cual con {@link #fill}.
 */
public final class Menu {

    private final Lang lang;
    private final YamlConfiguration yml;

    Menu(Lang lang, YamlConfiguration yml) {
        this.lang = lang;
        this.yml = yml;
    }

    public Component title(Object... kv) {
        return Lang.parse(yml.getString("menu_title", "&8| &9Menu"), kv);
    }

    public int size() {
        return yml.getInt("size", 54);
    }

    public boolean has(String id) {
        return yml.isConfigurationSection("items." + id);
    }

    public int slot(String id) {
        return yml.getInt("items." + id + ".slot", -1);
    }

    /** slots: [0-9, 17, 45-53] (rangos con guion) o un solo slot. */
    public List<Integer> slots(String id) {
        List<Integer> out = new ArrayList<>();
        for (String s : yml.getStringList("items." + id + ".slots")) {
            String t = s.trim();
            int dash = t.indexOf('-');
            try {
                if (dash > 0) {
                    int a = Integer.parseInt(t.substring(0, dash).trim()), b = Integer.parseInt(t.substring(dash + 1).trim());
                    for (int i = a; i <= b; i++) out.add(i);
                } else {
                    out.add(Integer.parseInt(t));
                }
            } catch (NumberFormatException ignored) {
                // slot mal escrito: se saltea
            }
        }
        int single = slot(id);
        if (single >= 0 && out.isEmpty()) out.add(single);
        return out;
    }

    /** Texto suelto del menu (seccion "textos"), por ejemplo lineas de estado o textos dibujados. */
    public String text(String key, Object... kv) {
        return Lang.apply(yml.getString("textos." + key, key), kv);
    }

    public List<String> textList(String key) {
        return yml.getStringList("textos." + key);
    }

    /** El item de la entrada con sus placeholders (nombre, lore). model / material del codigo pisan los del yml. */
    public ItemStack item(String id, Object... kv) {
        return item(id, null, null, kv);
    }

    public ItemStack item(String id, Material material, String model, Object... kv) {
        ConfigurationSection c = yml.getConfigurationSection("items." + id);
        Material mat = material;
        if (mat == null && c != null) mat = Material.matchMaterial(c.getString("material", "paper"));
        if (mat == null) mat = Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String m = model != null ? model : c != null ? c.getString("model") : null;
        if (m != null) meta.setItemModel(NamespacedKey.fromString(m));
        if (c != null) {
            String name = c.getString("display_name", " ");
            meta.itemName(Lang.parse(name, kv));
            if (" ".equals(name)) meta.setHideTooltip(true);
            meta.lore(Lang.lore(c.getStringList("lore"), kv));
            if (c.getBoolean("glow", false)) meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    /** Pone los items fijos (relleno y los que no estan en skip), cada uno en sus slots. */
    public void fill(Inventory inv, Set<String> skip, java.util.function.Consumer<ItemStack> tag) {
        ConfigurationSection items = yml.getConfigurationSection("items");
        if (items == null) return;
        for (String id : items.getKeys(false)) {
            if (skip.contains(id)) continue;
            ItemStack it = item(id);
            if (tag != null) tag.accept(it);
            for (int slot : slots(id)) {
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, it);
            }
        }
    }

    public Lang lang() {
        return lang;
    }
}

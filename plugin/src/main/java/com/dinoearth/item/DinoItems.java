package com.dinoearth.item;

import com.dinoearth.Keys;
import com.dinoearth.species.Species;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Items propios: lo que suelta cada especie, las monturas (una por especie), el silbato y la jaula.
 * Todo sale de items.json, que escribe generator/items.py junto con los iconos del pack.
 * Se reconocen por una marca en el PDC, nunca por el material ni el nombre.
 */
public final class DinoItems {

    public enum Kind { DROP, SADDLE, WHISTLE, CAGE }

    public record Def(String id, Kind kind, String species, Material material, int maxStack, NamespacedKey model,
                      NamespacedKey modelFull) {
    }

    public record Drop(String item, int min, int max, double chance) {
    }

    public static final String WHISTLE = "silbato";
    public static final String CAGE = "jaula";

    private final Keys keys;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, List<Drop>> drops = new LinkedHashMap<>();

    public DinoItems(Keys keys, InputStream json) {
        this.keys = keys;
        JsonObject root = JsonParser.parseReader(new InputStreamReader(json, StandardCharsets.UTF_8)).getAsJsonObject();
        for (JsonElement e : root.getAsJsonArray("items")) {
            JsonObject o = e.getAsJsonObject();
            String id = o.get("id").getAsString();
            defs.put(id, new Def(id, Kind.valueOf(o.get("kind").getAsString().toUpperCase()),
                    o.has("species") ? o.get("species").getAsString() : null,
                    Material.valueOf(o.get("material").getAsString()), o.get("max_stack").getAsInt(),
                    NamespacedKey.fromString(o.get("model").getAsString()),
                    o.has("model_full") ? NamespacedKey.fromString(o.get("model_full").getAsString()) : null));
        }
        JsonObject d = root.getAsJsonObject("drops");
        for (String species : d.keySet()) {
            List<Drop> list = new ArrayList<>();
            for (JsonElement e : d.getAsJsonArray(species)) {
                JsonObject o = e.getAsJsonObject();
                list.add(new Drop(o.get("item").getAsString(), o.get("min").getAsInt(), o.get("max").getAsInt(),
                        o.get("chance").getAsDouble()));
            }
            drops.put(species, List.copyOf(list));
        }
    }

    public List<String> ids() {
        return List.copyOf(defs.keySet());
    }

    /** Nombre del item sin colores (items.yml). */
    public String plainName(String id) {
        return com.dinoearth.text.Lang.get().itemName(id);
    }

    public Def def(String id) {
        return defs.get(id);
    }

    public ItemStack create(String id, int amount) {
        Def def = defs.get(id);
        if (def == null) return null;
        ItemStack item = new ItemStack(def.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(def.model());
        com.dinoearth.text.Lang.get().itemTexts(meta, id);
        meta.setMaxStackSize(def.maxStack());
        meta.getPersistentDataContainer().set(keys.item, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    /** Id del item propio, o null si es un item comun. */
    public String idOf(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keys.item, PersistentDataType.STRING);
    }

    public boolean isOurs(ItemStack item) {
        return idOf(item) != null;
    }

    public Def defOf(ItemStack item) {
        String id = idOf(item);
        return id == null ? null : defs.get(id);
    }

    public boolean is(ItemStack item, Kind kind) {
        Def def = defOf(item);
        return def != null && def.kind() == kind;
    }

    public String saddleId(Species species) {
        return "montura_" + species.id;
    }

    /** La montura que lleva esta especie (null si no se monta). */
    public ItemStack saddleFor(Species species) {
        return create(saddleId(species), 1);
    }

    /** Lo que suelta una especie al morir; size achica la cantidad en las crias. */
    public List<ItemStack> rollDrops(Species species, float size) {
        List<ItemStack> out = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Drop d : drops.getOrDefault(species.id, List.of())) {
            if (r.nextDouble() >= d.chance()) continue;
            int n = Math.round(r.nextInt(d.min(), d.max() + 1) * size);
            if (n > 0) out.add(create(d.item(), n));
        }
        return out;
    }

}

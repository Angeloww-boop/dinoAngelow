package com.dinoearth.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.NamespacedKey;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Esqueletos, variantes de color e items de una especie. Lo genera generator/build.py
 * (rigs/&lt;especie&gt;.json adentro del jar). Cria, montura y huevo son opcionales.
 */
public final class DinoRig {

    public record BoneDef(int index, String name, int parent, float jx, float jy, float jz) {
    }

    /** Una forma del cuerpo (adulto o cria) con su lista de huesos. radius = alcance horizontal en px. */
    public record Form(String id, float height, float radius, List<BoneDef> bones) {
        public int index(String name) {
            for (BoneDef b : bones) {
                if (b.name().equals(name)) return b.index();
            }
            return -1;
        }
    }

    public record Variant(String id, String name, int weight) {
    }

    private final float scale;
    private final String itemPattern;
    private final Map<String, Variant> variants = new LinkedHashMap<>();
    private final Form adult;
    private final Form baby;
    private final int saddleBone;
    private final NamespacedKey saddleItem;
    private final NamespacedKey eggModel;
    private final NamespacedKey eggCracked;
    private final NamespacedKey eggIcon;
    private final float eggSize;

    private DinoRig(JsonObject root) {
        scale = root.has("scale") ? root.get("scale").getAsFloat() : 1f;
        itemPattern = root.get("item").getAsString();
        for (JsonElement e : root.getAsJsonArray("variants")) {
            JsonObject o = e.getAsJsonObject();
            variants.put(o.get("id").getAsString(),
                    new Variant(o.get("id").getAsString(), o.get("name").getAsString(), o.get("weight").getAsInt()));
        }
        JsonObject forms = root.getAsJsonObject("forms");
        JsonObject saddle = root.has("saddle") ? root.getAsJsonObject("saddle") : null;
        // la montura es un hueso extra del adulto, hijo del cuerpo
        adult = form("adult", forms.getAsJsonObject("adult"), saddle);
        baby = forms.has("baby") ? form("baby", forms.getAsJsonObject("baby"), null) : null;
        saddleBone = saddle != null ? adult.index(saddle.get("name").getAsString()) : -1;
        saddleItem = saddle != null ? NamespacedKey.fromString(saddle.get("item").getAsString()) : null;
        JsonObject egg = root.has("egg") ? root.getAsJsonObject("egg") : null;
        eggModel = egg != null ? NamespacedKey.fromString(egg.get("item").getAsString()) : null;
        eggCracked = egg != null ? NamespacedKey.fromString(egg.get("cracked").getAsString()) : null;
        eggIcon = egg != null ? NamespacedKey.fromString(egg.get("icon").getAsString()) : null;
        eggSize = egg != null && egg.has("size") ? egg.get("size").getAsFloat() : 1f;
    }

    private static Form form(String id, JsonObject o, JsonObject extra) {
        List<JsonObject> raw = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("bones")) raw.add(e.getAsJsonObject());
        if (extra != null) raw.add(extra);
        List<String> names = new ArrayList<>();
        for (JsonObject b : raw) names.add(b.get("name").getAsString());
        List<BoneDef> bones = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            JsonObject b = raw.get(i);
            JsonElement parent = b.get("parent");
            JsonArray j = b.getAsJsonArray("joint");
            bones.add(new BoneDef(i, names.get(i),
                    parent == null || parent.isJsonNull() ? -1 : names.indexOf(parent.getAsString()),
                    j.get(0).getAsFloat(), j.get(1).getAsFloat(), j.get(2).getAsFloat()));
        }
        float radius = o.has("radius") ? o.get("radius").getAsFloat() : 32f;
        return new Form(id, o.get("height").getAsFloat(), radius, List.copyOf(bones));
    }

    public static DinoRig load(InputStream in) {
        return new DinoRig(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
    }

    /** Escala con la que se muestra el modelo (algunas especies se modelan mas grandes). */
    public float scale() {
        return scale;
    }

    public Form adult() {
        return adult;
    }

    /** null si la especie no tiene modelo de cria. */
    public Form baby() {
        return baby;
    }

    public boolean hasSaddle() {
        return saddleBone >= 0;
    }

    public int saddleBone() {
        return saddleBone;
    }

    public boolean hasEgg() {
        return eggModel != null;
    }

    /** Items de cada hueso para una variante/modelo (female, male o baby). La montura arranca oculta. */
    public NamespacedKey[] items(Form form, String variant, String model) {
        NamespacedKey[] keys = new NamespacedKey[form.bones().size()];
        for (BoneDef b : form.bones()) {
            if (form == adult && b.index() == saddleBone) continue;
            keys[b.index()] = NamespacedKey.fromString(itemPattern
                    .replace("{variant}", variant).replace("{model}", model).replace("{bone}", b.name()));
        }
        return keys;
    }

    public NamespacedKey saddleItem() {
        return saddleItem;
    }

    public NamespacedKey eggModel() {
        return eggModel;
    }

    public NamespacedKey eggCracked() {
        return eggCracked;
    }

    public NamespacedKey eggIcon() {
        return eggIcon;
    }

    /** Tamaño del huevo comparado con el de un raptor (los nidos lo achican para que entren los tres). */
    public float eggSize() {
        return eggSize;
    }

    public Map<String, Variant> variants() {
        return variants;
    }

    public Variant variant(String id) {
        Variant v = variants.get(id);
        return v != null ? v : variants.values().iterator().next();
    }

    /** Color al azar segun el peso de cada variante (el albino es raro). */
    public Variant randomVariant() {
        int total = variants.values().stream().mapToInt(Variant::weight).sum();
        int r = ThreadLocalRandom.current().nextInt(total);
        for (Variant v : variants.values()) {
            r -= v.weight();
            if (r < 0) return v;
        }
        return variants.values().iterator().next();
    }
}

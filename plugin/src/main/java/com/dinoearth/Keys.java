package com.dinoearth;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Claves de datos persistentes (PDC) que usa el plugin. */
public final class Keys {

    public final NamespacedKey type;
    public final NamespacedKey variant;
    public final NamespacedKey sex;
    public final NamespacedKey growth;
    public final NamespacedKey saddled;
    public final NamespacedKey part;
    public final NamespacedKey egg;
    public final NamespacedKey eggOwner;
    public final NamespacedKey eggProgress;
    public final NamespacedKey eggItem;
    public final NamespacedKey eggSpecies;
    public final NamespacedKey venom;
    public final NamespacedKey hunger;
    public final NamespacedKey starving;
    public final NamespacedKey feeder;
    public final NamespacedKey feederDiet;
    public final NamespacedKey feederPoints;
    public final NamespacedKey feederOwner;
    public final NamespacedKey feederItem;
    public final NamespacedKey dexBook;
    public final NamespacedKey dexIcon;

    private final Plugin plugin;
    private final Map<String, NamespacedKey> dynamic = new HashMap<>();

    /** Cambia en cada arranque: las displays de una sesion anterior se reconocen y se borran. */
    private final String session = UUID.randomUUID().toString();

    public Keys(Plugin plugin) {
        type = new NamespacedKey(plugin, "type");
        variant = new NamespacedKey(plugin, "variant");
        sex = new NamespacedKey(plugin, "sex");
        growth = new NamespacedKey(plugin, "growth");
        saddled = new NamespacedKey(plugin, "saddled");
        part = new NamespacedKey(plugin, "part");
        egg = new NamespacedKey(plugin, "egg");
        eggOwner = new NamespacedKey(plugin, "egg_owner");
        eggProgress = new NamespacedKey(plugin, "egg_progress");
        eggItem = new NamespacedKey(plugin, "egg_item");
        eggSpecies = new NamespacedKey(plugin, "egg_species");
        venom = new NamespacedKey(plugin, "venom");
        hunger = new NamespacedKey(plugin, "hunger");
        starving = new NamespacedKey(plugin, "starving");
        feeder = new NamespacedKey(plugin, "feeder");
        feederDiet = new NamespacedKey(plugin, "feeder_diet");
        feederPoints = new NamespacedKey(plugin, "feeder_points");
        feederOwner = new NamespacedKey(plugin, "feeder_owner");
        feederItem = new NamespacedKey(plugin, "feeder_item");
        dexBook = new NamespacedKey(plugin, "dinopedia");
        dexIcon = new NamespacedKey(plugin, "dinopedia_icon");
        this.plugin = plugin;
    }

    /** Dinopedia en el jugador: colores vistos de una especie ("arena,gris,..."). */
    public NamespacedKey dexSeen(String species) {
        return dynamic.computeIfAbsent("dex_" + species, k -> new NamespacedKey(plugin, k));
    }

    /** Dinopedia en el jugador: cuantos de una especie domestico. */
    public NamespacedKey dexTamed(String species) {
        return dynamic.computeIfAbsent("dex_tamed_" + species, k -> new NamespacedKey(plugin, k));
    }

    public void tagPart(ItemDisplay display) {
        display.getPersistentDataContainer().set(part, PersistentDataType.STRING, session);
    }

    /** Display de un modelo nuestro que quedo de un arranque anterior. */
    public boolean isStalePart(Entity e) {
        if (!(e instanceof ItemDisplay)) return false;
        String s = e.getPersistentDataContainer().get(part, PersistentDataType.STRING);
        return s != null && !s.equals(session);
    }
}

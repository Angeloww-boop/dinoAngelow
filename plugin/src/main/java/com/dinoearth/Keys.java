package com.dinoearth;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

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

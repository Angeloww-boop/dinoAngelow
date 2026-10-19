package com.dinoearth;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
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
    // items propios (drops, monturas, silbato, jaula) y lo que guarda una jaula llena
    public final NamespacedKey item;
    public final NamespacedKey cage;
    public final NamespacedKey health;
    // dino domesticado: nombre, orden, modo de combate y chunk de patrulla
    public final NamespacedKey name;
    public final NamespacedKey order;
    public final NamespacedKey combat;
    public final NamespacedKey patrolWorld;
    public final NamespacedKey patrolX;
    public final NamespacedKey patrolZ;
    public final NamespacedKey menuIcon;
    // carreras: dino prestado por la pista, y adonde vuelve el jugador si se desconecta corriendo
    public final NamespacedKey rental;
    public final NamespacedKey raceReturn;
    // genetica (lobo, huevo, jaula) y color ya elegido de la cria que va a salir de un huevo
    public final NamespacedKey genes;
    public final NamespacedKey geneMother;
    public final NamespacedKey geneFather;
    public final NamespacedKey eggVariant;
    // nidos salvajes
    public final NamespacedKey nest;
    public final NamespacedKey nestSpecies;
    public final NamespacedKey nestMother;
    public final NamespacedKey nestEggs;
    public final NamespacedKey nestBorn;
    /** Lobo: madre salvaje que cuida un nido (uuid de la Interaction del nido). */
    public final NamespacedKey guarding;
    // spawn natural: aparecio solo, y la ultima vez que hubo un jugador cerca (para que desaparezca si queda solo)
    public final NamespacedKey natural;
    public final NamespacedKey naturalSeen;
    // Mapa del Paleontologo
    public final NamespacedKey paleoMap;
    public final NamespacedKey nestCooldown;

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
        item = new NamespacedKey(plugin, "item");
        cage = new NamespacedKey(plugin, "cage");
        health = new NamespacedKey(plugin, "health");
        name = new NamespacedKey(plugin, "name");
        order = new NamespacedKey(plugin, "order");
        combat = new NamespacedKey(plugin, "combat");
        patrolWorld = new NamespacedKey(plugin, "patrol_world");
        patrolX = new NamespacedKey(plugin, "patrol_x");
        patrolZ = new NamespacedKey(plugin, "patrol_z");
        menuIcon = new NamespacedKey(plugin, "menu_icon");
        rental = new NamespacedKey(plugin, "rental");
        raceReturn = new NamespacedKey(plugin, "race_return");
        genes = new NamespacedKey(plugin, "genes");
        geneMother = new NamespacedKey(plugin, "gene_mother");
        geneFather = new NamespacedKey(plugin, "gene_father");
        eggVariant = new NamespacedKey(plugin, "egg_variant");
        nest = new NamespacedKey(plugin, "nest");
        nestSpecies = new NamespacedKey(plugin, "nest_species");
        nestMother = new NamespacedKey(plugin, "nest_mother");
        nestEggs = new NamespacedKey(plugin, "nest_eggs");
        nestBorn = new NamespacedKey(plugin, "nest_born");
        guarding = new NamespacedKey(plugin, "guarding");
        natural = new NamespacedKey(plugin, "natural");
        naturalSeen = new NamespacedKey(plugin, "natural_seen");
        paleoMap = new NamespacedKey(plugin, "paleo_map");
        nestCooldown = new NamespacedKey(plugin, "nest_cooldown");
        this.plugin = plugin;
    }

    /** Dinopedia en el jugador: colores vistos de una especie ("arena,gris,..."). */
    public NamespacedKey dexSeen(String species) {
        return dynamic.computeIfAbsent("dex_" + species, k -> new NamespacedKey(plugin, k));
    }

    /** Dinopedia en el jugador: sellos de investigacion conseguidos de una especie ("VISTO,COLORES,..."). */
    public NamespacedKey dexStamps(String species) {
        return dynamic.computeIfAbsent("dex_stamps_" + species, k -> new NamespacedKey(plugin, k));
    }

    /** Dinopedia en el jugador: cuantos de una especie domestico. */
    public NamespacedKey dexTamed(String species) {
        return dynamic.computeIfAbsent("dex_tamed_" + species, k -> new NamespacedKey(plugin, k));
    }

    /** Contadores del jugador para los tops (crias nacidas, huevos robados, misiones hechas...). */
    public NamespacedKey stat(String name) {
        return dynamic.computeIfAbsent("stat_" + name, k -> new NamespacedKey(plugin, k));
    }

    public void tagPart(Display display) {
        display.getPersistentDataContainer().set(part, PersistentDataType.STRING, session);
    }

    /** Display de un modelo nuestro (de este arranque o de uno anterior). */
    public boolean isPart(Entity e) {
        return e instanceof Display && e.getPersistentDataContainer().has(part, PersistentDataType.STRING);
    }

    /** Display de un modelo nuestro que quedo de un arranque anterior. */
    public boolean isStalePart(Entity e) {
        if (!(e instanceof Display)) return false;
        String s = e.getPersistentDataContainer().get(part, PersistentDataType.STRING);
        return s != null && !s.equals(session);
    }
}

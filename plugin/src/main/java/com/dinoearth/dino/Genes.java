package com.dinoearth.dino;

import com.dinoearth.Keys;
import com.dinoearth.Settings;
import com.dinoearth.model.DinoRig;
import com.dinoearth.species.Species;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Genetica de un dino: tres rasgos que multiplican sus stats (vida, velocidad y tamaño, 1 = normal), la generacion
 * (0 = salvaje, 1 = hijo de salvajes...), si su color salio de una mutacion y quienes son sus padres.
 *
 * Los salvajes nacen con rasgos al azar cerca de 1. Las crias sacan el promedio de los padres con un poco de
 * variacion (y a veces un salto), asi criando a los mejores con los mejores se llega a dinos mas fuertes, rapidos
 * o grandes. El color sale de uno de los padres, a veces de cualquiera, y con poca probabilidad muta al raro.
 *
 * Se guarda en el PDC del lobo, del item de huevo, del huevo puesto y de la jaula (todos con las mismas claves).
 */
public final class Genes {

    /** Uno de los padres: su color y su nombre (puesto con etiqueta, sin colores) o null. */
    public record Parent(String variant, boolean male, String name) {
        String encode() {
            return variant + "|" + (male ? "m" : "f") + "|" + (name != null ? name : "");
        }

        static Parent decode(String raw) {
            if (raw == null || raw.isEmpty()) return null;
            String[] p = raw.split("\\|", 3);
            if (p.length < 2) return null;
            return new Parent(p[0], "m".equals(p[1]), p.length > 2 && !p[2].isEmpty() ? p[2] : null);
        }
    }

    public final float health;
    public final float speed;
    public final float size;
    public final int generation;
    public final boolean mutant;
    /** null = salvaje o padres desconocidos (huevo de nido: se sabe la madre, el padre no). */
    public final Parent mother;
    public final Parent father;

    public Genes(float health, float speed, float size, int generation, boolean mutant, Parent mother, Parent father) {
        this.health = health;
        this.speed = speed;
        this.size = size;
        this.generation = generation;
        this.mutant = mutant;
        this.mother = mother;
        this.father = father;
    }

    public static Genes neutral() {
        return new Genes(1f, 1f, 1f, 0, false, null, null);
    }

    /** Promedio de los tres rasgos (1 = normal): sirve para los tops y la calidad. */
    public float score() {
        return (health + speed + size) / 3f;
    }

    public Genes withParents(Parent mother, Parent father) {
        return new Genes(health, speed, size, generation, mutant, mother, father);
    }

    // ------------------------------------------------------------ azar
    /** Rasgos de un salvaje. */
    public static Genes wild(Settings s) {
        return new Genes(roll(1f, s.wildVariation, s), roll(1f, s.wildVariation, s), roll(1f, s.wildVariation, s),
                0, false, null, null);
    }

    /** Cria de dos padres (b puede ser null: huevo de nido, el padre es un salvaje cualquiera). */
    public static Genes child(Genes a, Genes b, Parent mother, Parent father, boolean mutant, Settings s) {
        if (b == null) b = wild(s);
        return new Genes(inherit(a.health, b.health, s), inherit(a.speed, b.speed, s), inherit(a.size, b.size, s),
                Math.max(a.generation, b.generation) + 1, mutant, mother, father);
    }

    private static float inherit(float a, float b, Settings s) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // la mayoria sale entre los dos padres; a veces se parece mucho mas a uno
        float t = r.nextFloat() < 0.25f ? (r.nextBoolean() ? 0.85f : 0.15f) : 0.5f;
        float base = a + (b - a) * t;
        double spread = r.nextFloat() < s.geneJumpChance ? s.geneVariation * 2.5 : s.geneVariation;
        return roll(base, spread, s);
    }

    private static float roll(float base, double spread, Settings s) {
        double v = base + ThreadLocalRandom.current().nextGaussian() * spread;
        return (float) Math.max(s.geneMin, Math.min(s.geneMax, Math.round(v * 100) / 100.0));
    }

    /**
     * Color de la cria: [variante, "1" si muto]. Casi siempre el de uno de los padres; a veces uno cualquiera
     * de la especie, y con mutation-chance el color raro (si los padres no lo eran ya).
     */
    public static String[] childColor(Species species, String motherVariant, String fatherVariant, Settings s) {
        DinoRig rig = species.rig;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        DinoRig.Variant rare = rareOf(rig);
        boolean parentsRare = rare != null && rare.id().equals(motherVariant) && rare.id().equals(fatherVariant);
        if (rare != null && !parentsRare && r.nextDouble() < s.mutationChance) return new String[]{rare.id(), "1"};
        double roll = r.nextDouble();
        if (roll < s.randomColorChance || motherVariant == null) return new String[]{rig.randomVariant().id(), "0"};
        String pick = fatherVariant == null || roll < (1 + s.randomColorChance) / 2 ? motherVariant : fatherVariant;
        return new String[]{rig.variant(pick).id(), "0"};
    }

    public static DinoRig.Variant rareOf(DinoRig rig) {
        for (DinoRig.Variant v : rig.variants().values()) {
            if (v.weight() <= 5) return v;
        }
        return null;
    }

    // ------------------------------------------------------------ PDC
    public void write(PersistentDataContainer pdc, Keys keys) {
        pdc.set(keys.genes, PersistentDataType.STRING, String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%d,%d",
                health, speed, size, generation, mutant ? 1 : 0));
        if (mother != null) pdc.set(keys.geneMother, PersistentDataType.STRING, mother.encode());
        else pdc.remove(keys.geneMother);
        if (father != null) pdc.set(keys.geneFather, PersistentDataType.STRING, father.encode());
        else pdc.remove(keys.geneFather);
    }

    /** null si no tiene genes guardados (dinos de antes de la 0.14, huevos dados por un admin). */
    public static Genes read(PersistentDataContainer pdc, Keys keys) {
        String raw = pdc.get(keys.genes, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            String[] p = raw.split(",");
            return new Genes(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]),
                    p.length > 3 ? Integer.parseInt(p[3]) : 0, p.length > 4 && "1".equals(p[4]),
                    Parent.decode(pdc.get(keys.geneMother, PersistentDataType.STRING)),
                    Parent.decode(pdc.get(keys.geneFather, PersistentDataType.STRING)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Copia los genes tal cual de un contenedor a otro (lobo -> jaula, item -> huevo puesto...). */
    public static void copy(PersistentDataContainer from, PersistentDataContainer to, Keys keys) {
        for (var key : new org.bukkit.NamespacedKey[]{keys.genes, keys.geneMother, keys.geneFather}) {
            String v = from.get(key, PersistentDataType.STRING);
            if (v != null) to.set(key, PersistentDataType.STRING, v);
        }
    }

    /** Porcentaje para mostrar (1.07 -> 107). */
    public static int percent(float v) {
        return Math.round(v * 100);
    }
}

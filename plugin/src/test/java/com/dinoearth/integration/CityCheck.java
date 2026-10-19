package com.dinoearth.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prueba sin servidor la distancia a las "ciudades" del spawn natural: terrenos de Towny (cuadrados de 16) y cajas de
 * regiones de WorldGuard. Uso: java -cp ... com.dinoearth.integration.CityCheck
 */
public final class CityCheck {

    private static int fails;

    public static void main(String[] args) {
        // un pueblo de 2 x 1 terrenos: x 160..192, z 320..336
        Set<Long> town = new HashSet<>();
        town.add(Wilderness.key(10, 20));
        town.add(Wilderness.key(11, 20));
        check("adentro del pueblo", Wilderness.nearTownBlocks(town, 16, 170, 325, 150));
        check("a 100 bloques al este", Wilderness.nearTownBlocks(town, 16, 292, 328, 150));
        check("a 149 bloques al norte", Wilderness.nearTownBlocks(town, 16, 170, 320 - 149, 150));
        check("a 151 bloques al norte no", !Wilderness.nearTownBlocks(town, 16, 170, 320 - 151, 150));
        check("a 160 bloques en diagonal no", !Wilderness.nearTownBlocks(town, 16, 192 + 113.2, 336 + 113.2, 150));
        check("a 140 en diagonal si", Wilderness.nearTownBlocks(town, 16, 192 + 98, 336 + 98, 150));
        check("lejisimo no", !Wilderness.nearTownBlocks(town, 16, 5000, 5000, 150));
        check("coordenadas negativas", !Wilderness.nearTownBlocks(town, 16, -170, -325, 150));
        Set<Long> neg = new HashSet<>();
        neg.add(Wilderness.key(-3, -2));   // x -48..-32, z -32..-16
        check("pueblo en negativo, cerca", Wilderness.nearTownBlocks(neg, 16, -40, 100, 150));
        check("pueblo en negativo, lejos", !Wilderness.nearTownBlocks(neg, 16, -40, 200, 150));
        check("sin pueblos", !Wilderness.nearTownBlocks(null, 16, 0, 0, 150));
        near("distancia exacta al pueblo", Wilderness.nearestTownBlock(town, 16, 292, 328, 1000), 100);
        check("terrenos de 32 bloques", Wilderness.nearTownBlocks(Set.of(Wilderness.key(1, 1)), 32, 64 + 140, 40, 150)
                && !Wilderness.nearTownBlocks(Set.of(Wilderness.key(1, 1)), 32, 64 + 160, 40, 150));

        // regiones de WorldGuard: el spawn de -100..100
        List<double[]> boxes = List.of(new double[]{-100, -100, 101, 101});
        check("adentro del spawn", Wilderness.nearBoxes(boxes, 0, 0, 150));
        check("a 149 del spawn", Wilderness.nearBoxes(boxes, 101 + 149, 0, 150));
        check("a 151 del spawn no", !Wilderness.nearBoxes(boxes, 101 + 151, 0, 150));
        near("distancia a la caja en diagonal", Wilderness.nearestBox(boxes, 104, 105), 5);
        check("sin regiones", !Wilderness.nearBoxes(null, 0, 0, 150));

        // claves: distintas para (x, z) y (z, x), y con negativos
        check("claves distintas", Wilderness.key(1, 2) != Wilderness.key(2, 1) && Wilderness.key(-1, 0) != Wilderness.key(0, -1));

        System.out.println(fails == 0 ? "todo ok" : fails + " fallas");
        if (fails > 0) System.exit(1);
    }

    private static void near(String name, double got, double want) {
        check(name + " (" + got + ")", Math.abs(got - want) < 1e-6);
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            fails++;
            System.out.println("FALLA: " + name);
        }
    }
}

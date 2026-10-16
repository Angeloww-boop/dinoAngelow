package com.dinoearth.race;

import org.bukkit.util.Vector;

/**
 * Prueba sin servidor la deteccion de puertas y el formato de tiempos de las carreras.
 * Uso: java -cp ... com.dinoearth.race.RaceCheck
 */
public final class RaceCheck {

    private static int fails;

    public static void main(String[] args) {
        // puerta de 8 de ancho (en Z) y 3 de profundidad (en X), como la que arma /dino carrera puerta mirando a +X
        RaceManager.Gate g = new RaceManager.Gate(-1.5, 63, -4, 1.5, 68, 4, -90f);
        check("todavia no llego", !g.crossed(new Vector(-2.5, 64, 0), new Vector(-2.0, 64, 0.1)));
        check("entra en la puerta", g.crossed(new Vector(-1.9, 64, 0), new Vector(-1.4, 64, 0)));
        check("rapido: la saltea entre dos ticks", g.crossed(new Vector(-3, 64, 1), new Vector(3, 64, 1.2)));
        check("rapidisimo en diagonal", g.crossed(new Vector(-6, 64, -6), new Vector(6, 64, 6)));
        check("pasa por al lado", !g.crossed(new Vector(-3, 64, 6), new Vector(3, 64, 6)));
        check("pasa por arriba", !g.crossed(new Vector(-3, 70, 0), new Vector(3, 70, 0)));
        check("parado adentro cuenta", g.crossed(new Vector(0, 64, 0), new Vector(0, 64, 0)));
        check("lejos no cuenta", !g.crossed(new Vector(10, 64, 0), new Vector(11, 64, 0)));
        // tiempos
        check("tiempo 83.4", RaceManager.time(83.4).equals("1:23.40"));
        check("tiempo 9.05", RaceManager.time(9.05).equals("9.05"));
        check("tiempo 125", RaceManager.time(125).equals("2:05.00"));
        System.out.println(fails == 0 ? "todo ok" : fails + " fallas");
        if (fails > 0) System.exit(1);
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            fails++;
            System.out.println("FALLA: " + name);
        }
    }
}

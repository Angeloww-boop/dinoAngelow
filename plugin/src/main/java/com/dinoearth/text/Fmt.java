package com.dinoearth.text;

import com.dinoearth.species.Species;

/** Palabras armadas con textos.yml que se usan en muchos lados (tiempos, sexo, dieta...). */
public final class Fmt {

    private Fmt() {
    }

    private static Lang l() {
        return Lang.get();
    }

    public static String time(long ms) {
        long s = (ms + 999) / 1000;
        if (s >= 3600) return l().plain("tiempo.horas", "h", s / 3600, "m", (s % 3600) / 60);
        if (s >= 60) {
            return s % 60 > 0 ? l().plain("tiempo.minutos-segundos", "m", s / 60, "s", s % 60)
                    : l().plain("tiempo.minutos", "m", s / 60);
        }
        return l().plain("tiempo.segundos", "s", s);
    }

    public static String sex(boolean male) {
        return l().plain(male ? "sexo.macho" : "sexo.hembra");
    }

    public static String sexTitle(boolean male) {
        return l().plain(male ? "sexo.Macho" : "sexo.Hembra");
    }

    public static String diet(Species.Diet d) {
        return l().plain("dietas." + d.name());
    }

    public static String food(Species.Diet d) {
        return l().plain("comida." + d.name());
    }
}

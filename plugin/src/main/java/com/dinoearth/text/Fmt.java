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

    /** Uno de los padres de un dino: "Luna (Rojizo)" o "Rojizo hembra"; sin datos, "salvaje". */
    public static String parent(Species s, com.dinoearth.dino.Genes.Parent p) {
        if (p == null) return l().plain("genes.desconocido");
        String color = s.colorName(p.variant());
        return p.name() != null ? l().plain("genes.padre-con-nombre", "nombre", p.name(), "color", color)
                : l().plain("genes.padre-sin-nombre", "color", color, "sexo", sex(p.male()));
    }

    /** Calidad de los genes en una palabra (segun el promedio de los rasgos). */
    public static String quality(float score) {
        String k = score >= 1.15f ? "legendaria" : score >= 1.08f ? "excelente" : score >= 1.02f ? "buena"
                : score >= 0.96f ? "normal" : "debil";
        return l().plain("genes.calidad." + k);
    }

    public static String food(Species.Diet d) {
        return l().plain("comida." + d.name());
    }
}

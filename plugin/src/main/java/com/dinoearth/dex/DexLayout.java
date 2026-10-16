package com.dinoearth.dex;

import com.dinoearth.gui.GuiFont;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;

/**
 * Dibujo de los menus de la Dinopedia (el titulo con fondo, foto, textos y barras). Solo arma el titulo a partir de
 * datos sueltos: no toca jugadores ni inventarios, asi se puede previsualizar sin servidor (GuiPreview en los tests).
 * Coordenadas del menu de cofre de 6 filas (ver generator/gui.py).
 */
public final class DexLayout {

    static final int T_TITLE = 6;
    static final int[] STAT_ROWS = {20, 31, 42, 53};
    static final int LEFT_CX = 46, RIGHT_CX = 130, RIGHT_X = 94, PIPS_X = 106, PAGE_W = 76;
    static final int RENDER_X = 16;
    static final int[] INDEX_BAR_ROW = {56, 92};
    static final int INDEX_COLS = 7;

    public static final TextColor INK = TextColor.color(0x3C2818);
    public static final TextColor INK_SOFT = TextColor.color(0x8C7452);
    public static final TextColor INK_RED = TextColor.color(0x8C3424);
    public static final TextColor GOLD = TextColor.color(0xB07A22);
    public static final TextColor DONE = TextColor.color(0x4E8A3A);

    private DexLayout() {
    }

    /** bars: por especie de la pagina, -1 = sin descubrir, 0..8 = octavos de sellos; complete = pagina completa. */
    public record IndexView(String rank, TextColor rankColor, int done, int total, List<Integer> bars,
                            List<Boolean> complete) {
    }

    public record Stat(String icon, double fraction, TextColor color) {
    }

    public record SpeciesView(String species, String variant, String name, String dietIcon, String diet,
                              List<Stat> stats, String temper, String colors) {
    }

    public record Line(String text, TextColor color) {
    }

    /** hunger == null: no se muestra (y los renglones de abajo suben). lines: hasta 3 (dueño, orden, modo...). */
    public record CardView(String species, String variant, String name, String header, boolean male, String age,
                           String color, boolean rare, double health, Double hunger, TextColor hungerColor,
                           List<Line> lines) {
    }

    public static Component index(GuiFont gui, IndexView v) {
        GuiFont.Title t = gui.title().image("bg/index", 0)
                .text(T_TITLE, 10, "Dinopedia", INK)
                .right(T_TITLE, 166, v.rank(), v.rankColor());
        int segs = 27, on = (int) Math.round(segs * v.done() / (double) Math.max(1, v.total()));
        int seg = t.advance("seg_on");
        t.repeat(20, 28, "seg_on", on, GOLD).repeat(20, 28 + seg * on, "seg_off", segs - on, INK_SOFT);
        for (int i = 0; i < v.bars().size(); i++) {
            int row = i / INDEX_COLS, col = i % INDEX_COLS;
            if (row >= INDEX_BAR_ROW.length) break;
            int x = 8 + 18 * (col + 1);
            int k = v.bars().get(i);
            if (k < 0) {
                t.icon(INDEX_BAR_ROW[row], x, "mbar0", INK_SOFT);
            } else {
                t.icon(INDEX_BAR_ROW[row], x, "mbar" + k, v.complete().get(i) ? GOLD : DONE);
            }
        }
        return t.build();
    }

    public static Component species(GuiFont gui, SpeciesView v) {
        GuiFont.Title t = gui.title().image("bg/species", 0).image("render/" + v.species() + "/" + v.variant(), RENDER_X)
                .center(T_TITLE, LEFT_CX, gui.fit(v.name(), PAGE_W), INK);
        int dw = t.advance(v.dietIcon()) + 2 + gui.width(v.diet());
        int dx = RIGHT_CX - dw / 2;
        t.icon(T_TITLE, dx, v.dietIcon(), NamedTextColor.WHITE).text(T_TITLE, dx + t.advance(v.dietIcon()) + 2, v.diet(), INK);
        for (int i = 0; i < v.stats().size() && i < STAT_ROWS.length; i++) {
            Stat s = v.stats().get(i);
            pips(t, STAT_ROWS[i], s.icon(), s.fraction(), s.color(), true);
        }
        t.center(64, RIGHT_CX, gui.fit(v.temper(), PAGE_W), INK_RED);
        t.center(75, RIGHT_CX, gui.fit(v.colors(), PAGE_W), INK_SOFT);
        return t.build();
    }

    public static Component card(GuiFont gui, CardView v) {
        GuiFont.Title t = gui.title().image("bg/card", 0).image("render/" + v.species() + "/" + v.variant(), RENDER_X)
                .center(T_TITLE, LEFT_CX, gui.fit(v.name(), PAGE_W), INK)
                .center(T_TITLE, RIGHT_CX, gui.fit(v.header(), PAGE_W), INK_SOFT);
        String sex = v.male() ? "♂" : "♀";
        int sx = RIGHT_X + t.advance(sex) + 2;
        t.icon(20, RIGHT_X, sex, v.male() ? TextColor.color(0x3C78C8) : TextColor.color(0xC8508C))
                .text(20, sx, v.male() ? "Macho" : "Hembra", INK)
                .text(31, RIGHT_X, gui.fit(v.age(), PAGE_W), INK)
                .text(42, RIGHT_X, gui.fit(v.color() + (v.rare() ? " (raro)" : ""), PAGE_W),
                        v.rare() ? TextColor.color(0x7A3C96) : INK);
        pips(t, 53, "heart", v.health(), TextColor.color(0xB43A32), false);
        int[] rows = v.hunger() != null ? new int[]{75, 86, 97} : new int[]{64, 75, 86};
        if (v.hunger() != null) pips(t, 64, "meat", v.hunger(), v.hungerColor(), false);
        for (int i = 0; i < v.lines().size() && i < rows.length; i++) {
            Line l = v.lines().get(i);
            t.text(rows[i], RIGHT_X, gui.fit(l.text(), PAGE_W), l.color());
        }
        return t.build();
    }

    /** Icono + 10 pips. atLeastOne: las estadisticas de especie nunca quedan en cero. */
    private static void pips(GuiFont.Title t, int row, String icon, double f, TextColor color, boolean atLeastOne) {
        int n = (int) Math.max(atLeastOne || f > 0 ? 1 : 0, Math.min(10, Math.round(f * 10)));
        t.icon(row, RIGHT_X, icon, NamedTextColor.WHITE)
                .repeat(row, PIPS_X, "pip_on", n, color)
                .repeat(row, PIPS_X + t.advance("pip_on") * n, "pip_off", 10 - n, INK_SOFT);
    }
}

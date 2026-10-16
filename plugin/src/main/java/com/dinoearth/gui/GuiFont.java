package com.dinoearth.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menus con diseño: el fondo y las fotos van en el titulo del menu como letras de una fuente del pack, y el texto
 * sobre las paginas usa copias de la fuente de vanilla corridas hacia abajo (un "renglon" por altura). Para ubicar
 * cada cosa se mueve el lapiz con espacios negativos/positivos. Todo sale de gui.json (lo escribe generator/gui.py):
 * que letra es cada imagen y cuanto avanza cada letra.
 *
 * Las coordenadas son las del menu de cofre de 6 filas (176 x 222, el titulo empieza en x = 8).
 */
public final class GuiFont {

    public record Glyph(String ch, int advance) {
    }

    private final int x0;
    private final Key spaceFont;
    private final Key imageFont;
    private final Key hudFont;
    private final Map<Integer, String> space = new HashMap<>();
    private final Map<String, Glyph> images = new HashMap<>();
    private final Map<String, Glyph> icons = new HashMap<>();
    private final Map<Integer, Key> rows = new HashMap<>();
    private final Map<Integer, Integer> widths = new HashMap<>();
    private final List<String> hud = new ArrayList<>();

    private GuiFont(JsonObject o) {
        x0 = o.get("x0").getAsInt();
        spaceFont = Key.key(o.get("space_font").getAsString());
        imageFont = Key.key(o.get("image_font").getAsString());
        hudFont = Key.key(o.get("hud_font").getAsString());
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("space").entrySet()) {
            space.put(Integer.parseInt(e.getKey()), e.getValue().getAsString());
        }
        glyphs(o.getAsJsonObject("images"), images);
        glyphs(o.getAsJsonObject("icons"), icons);
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rows").entrySet()) {
            rows.put(Integer.parseInt(e.getKey()), Key.key(e.getValue().getAsString()));
        }
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("widths").entrySet()) {
            widths.put(e.getKey().codePointAt(0), e.getValue().getAsInt());
        }
        for (JsonElement e : o.getAsJsonArray("hud")) hud.add(e.getAsString());
    }

    private static void glyphs(JsonObject from, Map<String, Glyph> into) {
        for (Map.Entry<String, JsonElement> e : from.entrySet()) {
            var a = e.getValue().getAsJsonArray();
            into.put(e.getKey(), new Glyph(a.get(0).getAsString(), a.get(1).getAsInt()));
        }
    }

    public static GuiFont load(InputStream in) throws IOException {
        if (in == null) throw new IOException("falta gui.json en el jar");
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new GuiFont(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    public boolean hasImage(String key) {
        return images.containsKey(key);
    }

    // ------------------------------------------------------------ texto
    /** Cuanto avanza un texto escrito en un renglon (las letras que no hay se cambian por "?"). */
    public int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += widths.getOrDefault(cp, widths.getOrDefault((int) '?', 6));
            i += Character.charCount(cp);
        }
        return w;
    }

    /** Recorta con "…" si no entra en el ancho. */
    public String fit(String text, int max) {
        if (width(text) <= max) return text;
        String dots = "…";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String next = sb + new String(Character.toChars(cp));
            if (width(next + dots) > max) break;
            sb.setLength(0);
            sb.append(next);
            i += Character.charCount(cp);
        }
        return sb.toString().stripTrailing() + dots;
    }

    private String clean(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            sb.appendCodePoint(widths.containsKey(cp) ? cp : '?');
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ barra de accion
    /** Flecha que apunta a un angulo relativo a donde mira el jugador (0 = adelante, en grados). */
    public Component arrow(double degrees, TextColor color) {
        int i = (int) Math.floorMod(Math.round(degrees / 45.0), 8L);
        return Component.text(hud.get(i)).font(hudFont).color(color);
    }

    public Component footprint(TextColor color) {
        return Component.text(hud.get(8)).font(hudFont).color(color);
    }

    // ------------------------------------------------------------ titulo
    public Title title() {
        return new Title();
    }

    /** Arma el titulo de un menu de a pedazos, cada uno en una posicion absoluta. */
    public final class Title {
        private final TextComponent.Builder out = Component.text();
        private int pen = x0;

        private void moveTo(int x) {
            int dx = x - pen;
            if (dx == 0) return;
            StringBuilder sb = new StringBuilder();
            int left = Math.abs(dx);
            for (int bit = 9; bit >= 0; bit--) {
                int step = 1 << bit;
                while (left >= step) {
                    sb.append(space.get(dx < 0 ? -step : step));
                    left -= step;
                }
            }
            out.append(Component.text(sb.toString()).font(spaceFont));
            pen = x;
        }

        /** Imagen (fondo o foto) con su borde izquierdo en x; la altura ya viene fija en el pack. */
        public Title image(String key, int x) {
            Glyph g = images.get(key);
            if (g == null) return this;
            moveTo(x);
            out.append(Component.text(g.ch()).font(imageFont).color(NamedTextColor.WHITE));
            pen += g.advance();
            return this;
        }

        public Title text(int row, int x, String text, TextColor color) {
            Key font = rows.get(row);
            if (font == null || text.isEmpty()) return this;
            String s = clean(text);
            moveTo(x);
            out.append(Component.text(s).font(font).color(color));
            pen += width(s);
            return this;
        }

        public Title center(int row, int cx, String text, TextColor color) {
            return text(row, cx - (width(clean(text)) - 1) / 2, text, color);
        }

        public Title right(int row, int right, String text, TextColor color) {
            return text(row, right - width(clean(text)) + 1, text, color);
        }

        /** Iconito del pack (corazon, pips, barritas...) en un renglon; los blancos se tiñen con el color. */
        public Title icon(int row, int x, String key, TextColor color) {
            Key font = rows.get(row);
            Glyph g = icons.get(key);
            if (font == null || g == null) return this;
            moveTo(x);
            out.append(Component.text(g.ch()).font(font).color(color));
            pen += g.advance();
            return this;
        }

        /** Fila de n iconos iguales pegados (pips de una barra). */
        public Title repeat(int row, int x, String key, int n, TextColor color) {
            Key font = rows.get(row);
            Glyph g = icons.get(key);
            if (font == null || g == null || n <= 0) return this;
            moveTo(x);
            out.append(Component.text(g.ch().repeat(n)).font(font).color(color));
            pen += g.advance() * n;
            return this;
        }

        public int advance(String iconKey) {
            Glyph g = icons.get(iconKey);
            return g != null ? g.advance() : 0;
        }

        public Component build() {
            return out.build();
        }
    }
}

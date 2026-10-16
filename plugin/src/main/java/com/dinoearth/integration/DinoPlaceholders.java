package com.dinoearth.integration;

import com.dinoearth.dex.DexManager;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.mission.MissionManager;
import com.dinoearth.species.Species;
import com.dinoearth.stats.PlayerStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

/**
 * Placeholders de PlaceholderAPI (%dinoearth_...%). Esta clase solo se carga si PlaceholderAPI esta instalado.
 *
 * <pre>
 * dinos, limite, especies, especies_total, sellos, sellos_total, rango, crias, domesticados, nidos, cazados,
 * misiones (completadas en total), misiones_hoy, mejor_genes (el mejor promedio de sus dinos, en %),
 * tiene_&lt;especie&gt; (cuantos tiene de esa especie), cargados (dinos cargados en el server),
 * puesto_&lt;categoria&gt; (su puesto en el top),
 * top_&lt;categoria&gt;_&lt;n&gt;_nombre / top_&lt;categoria&gt;_&lt;n&gt;_valor (n desde 1)
 * </pre>
 * Categorias: dinos, sellos, especies, crias, domesticados, nidos, cazados, misiones, genes.
 */
public final class DinoPlaceholders extends PlaceholderExpansion {

    private final Plugin plugin;
    private final DinoManager dinos;
    private final DexManager dex;
    private final MissionManager missions;
    private final PlayerStats stats;

    public DinoPlaceholders(Plugin plugin, DinoManager dinos, DexManager dex, MissionManager missions, PlayerStats stats) {
        this.plugin = plugin;
        this.dinos = dinos;
        this.dex = dex;
        this.missions = missions;
        this.stats = stats;
    }

    @Override
    public String getIdentifier() {
        return "dinoearth";
    }

    @Override
    public String getAuthor() {
        return "Angeloww";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;   // que no se borre con /papi reload
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        if (p.startsWith("top_")) return top(p.substring(4));
        if (p.equals("cargados")) return String.valueOf(dinos.count());
        // record_<pista> / recordista_<pista>: mejor tiempo de siempre de una pista de carreras
        var races = com.dinoearth.race.RaceManager.get();
        if (races != null && (p.startsWith("record_") || p.startsWith("recordista_"))) {
            var t = races.track(p.substring(p.indexOf('_') + 1));
            if (t == null || t.best() <= 0) return "-";
            return p.startsWith("record_") ? com.dinoearth.race.RaceManager.time(t.best()) : t.bestName();
        }
        if (player == null) return "";
        Player online = player.getPlayer();
        if (p.startsWith("puesto_")) return String.valueOf(stats.position(player.getUniqueId(), p.substring(7)));
        if (p.startsWith("tiene_")) {
            Species s = Species.byId(p.substring(6));
            if (s == null || dinos.registry() == null) return "0";
            return String.valueOf(dinos.registry().ownedBy(player.getUniqueId()).stream()
                    .filter(e -> s.id.equals(e.species())).count());
        }
        return switch (p) {
            case "dinos" -> num(stats.value(player.getUniqueId(), "dinos"));
            case "limite" -> {
                int limit = online != null ? dinos.tamedLimit(online) : dinos.settings().maxTamed;
                yield limit == 0 ? "∞" : String.valueOf(limit);
            }
            case "especies" -> online != null ? String.valueOf(dex.speciesFound(online))
                    : num(stats.value(player.getUniqueId(), "especies"));
            case "especies_total" -> String.valueOf(Species.ALL.size());
            case "sellos" -> online != null ? String.valueOf(dex.stampsDone(online))
                    : num(stats.value(player.getUniqueId(), "sellos"));
            case "sellos_total" -> String.valueOf(dex.stampsTotal());
            case "rango" -> online != null ? dex.rankOf(online) : "";
            case "misiones_hoy" -> online != null && missions != null ? String.valueOf(missions.doneToday(online)) : "0";
            case "mejor_genes" -> num(stats.value(player.getUniqueId(), "genes"));
            case "crias", "domesticados", "nidos", "cazados", "misiones", "carreras" -> num(stats.value(player.getUniqueId(), p));
            case "carreras_corridas" -> num(stats.value(player.getUniqueId(), "carreras-corridas"));
            case "montando" -> online != null && online.getVehicle() != null && dinos.get(online.getVehicle()) != null
                    ? dinos.get(online.getVehicle()).species().name : "";
            default -> null;
        };
    }

    /** top_&lt;categoria&gt;_&lt;n&gt;_nombre o _valor. */
    private String top(String rest) {
        int a = rest.lastIndexOf('_');
        if (a <= 0) return null;
        String what = rest.substring(a + 1);
        String head = rest.substring(0, a);
        int b = head.lastIndexOf('_');
        if (b <= 0) return null;
        String category = head.substring(0, b);
        int n;
        try {
            n = Integer.parseInt(head.substring(b + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (!PlayerStats.CATEGORIES.contains(category) || n < 1) return null;
        List<PlayerStats.Row> rows = stats.top(category);
        if (n > rows.size()) return what.equals("valor") ? "0" : "-";
        PlayerStats.Row row = rows.get(n - 1);
        return what.equals("valor") ? num(row.value()) : row.name();
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Para que no se cargue la clase si no hay PlaceholderAPI. */
    public static void register(Plugin plugin, DinoManager dinos, DexManager dex, MissionManager missions,
                                PlayerStats stats) {
        new DinoPlaceholders(plugin, dinos, dex, missions, stats).register();
        plugin.getLogger().info("PlaceholderAPI conectado: %dinoearth_...% listos para tops y scoreboards.");
    }
}

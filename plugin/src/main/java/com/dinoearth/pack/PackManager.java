package com.dinoearth.pack;

import com.dinoearth.text.Lang;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Manda el resource pack a cada jugador al entrar, con el hash calculado por el plugin
 * (se acabo actualizar resource-pack-sha1 a mano). Dos formas:
 * <ul>
 *   <li><b>url</b>: el zip esta subido en otro lado; el plugin lo descarga al arrancar para sacarle el hash
 *       y avisa si no es el mismo pack que trae esta version del plugin.</li>
 *   <li><b>host</b>: el plugin sirve el pack que trae adentro con un mini servidor web (necesita un puerto abierto).</li>
 * </ul>
 */
public final class PackManager implements Listener {

    private static final String BUNDLED = "pack/DinoEarth-ResourcePack.zip";
    private static final int MAX_BYTES = 100 * 1024 * 1024;

    private final JavaPlugin plugin;
    private String mode = "off";
    private boolean required;
    private Component prompt = Component.empty();
    private byte[] bundled;
    private String bundledHash;
    private volatile ResourcePackInfo pack;   // null hasta que este listo
    private volatile String problem;          // ultimo problema al preparar el pack (para /dino pack info)
    private HttpServer server;

    public PackManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ arranque
    public void start() {
        problem = null;
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("resource-pack");
        mode = c != null ? c.getString("mode", "url").toLowerCase() : "url";
        required = c != null && c.getBoolean("required", false);
        prompt = Lang.get().chatMsg("pack-pregunta");
        try (InputStream in = plugin.getResource(BUNDLED)) {
            bundled = in != null ? in.readAllBytes() : null;
            bundledHash = bundled != null ? sha1(bundled) : null;
        } catch (IOException e) {
            bundled = null;
        }
        switch (mode) {
            case "url" -> startUrl(c != null ? c.getString("url", "") : "");
            case "host" -> startHost(c);
            case "off" -> plugin.getLogger().info("Resource pack: desactivado (resource-pack.mode: off).");
            default -> problem("modo", "modo", mode);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        pack = null;
    }

    /** Vuelve a leer la config, recalcula el hash y se lo manda a todos (despues de subir un zip nuevo). */
    public void reload(CommandSender sender) {
        plugin.reloadConfig();
        stop();
        start();
        Lang.get().chat(sender, "pack-recargando");
        // en modo url el hash se calcula en segundo plano; al terminar se manda a todos
    }

    private void startUrl(String url) {
        if (url == null || url.isBlank()) {
            problem("falta-url");
            return;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            problem("url-invalida", "url", url);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .connectTimeout(Duration.ofSeconds(15)).build();
                HttpResponse<byte[]> res = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "Minecraft Java/1.21 (DinoEarth)").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200) {
                    problem("url-respuesta", "codigo", res.statusCode(), "url", uri);
                    return;
                }
                byte[] data = res.body();
                if (data.length > MAX_BYTES || data.length < 4 || data[0] != 'P' || data[1] != 'K') {
                    problem("no-es-zip", "url", uri);
                    return;
                }
                String hash = sha1(data);
                if (bundledHash != null && !bundledHash.equals(hash)) {
                    problem("otra-version");
                }
                Bukkit.getScheduler().runTask(plugin, () -> ready(uri, hash));
            } catch (Exception e) {
                problem("descarga", "url", uri, "error", e.getMessage());
            }
        });
    }

    private void startHost(ConfigurationSection c) {
        if (bundled == null) {
            problem("sin-pack");
            return;
        }
        int port = c != null ? c.getInt("host.port", 8163) : 8163;
        String address = c != null ? c.getString("host.public-address", "") : "";
        if (address == null || address.isBlank()) {
            address = Bukkit.getIp();
            if (address == null || address.isBlank()) {
                problem("falta-direccion");
                return;
            }
        }
        String path = "/DinoEarth-" + bundledHash.substring(0, 12) + ".zip";
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    if (!exchange.getRequestURI().getPath().equals(path)) {
                        byte[] msg = "DinoEarth".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(404, msg.length);
                        exchange.getResponseBody().write(msg);
                        return;
                    }
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, bundled.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bundled);
                    }
                }
            });
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "DinoEarth-pack");
                t.setDaemon(true);
                return t;
            }));
            server.start();
        } catch (IOException e) {
            problem("puerto", "puerto", port, "error", e.getMessage());
            server = null;
            return;
        }
        URI uri = URI.create("http://" + address.trim() + ":" + port + path);
        plugin.getLogger().info("Resource pack: sirviendo el pack en " + uri
                + " (el puerto " + port + " tiene que estar abierto hacia afuera).");
        ready(uri, bundledHash);
    }

    private void ready(URI uri, String hash) {
        // mismo UUID para la misma URL: si se reenvia, reemplaza al anterior en vez de apilarse
        UUID id = UUID.nameUUIDFromBytes(("dinoearth:" + uri).getBytes(StandardCharsets.UTF_8));
        pack = ResourcePackInfo.resourcePackInfo(id, uri, hash);
        plugin.getLogger().info("Resource pack listo (sha1 " + hash + ").");
        for (Player p : Bukkit.getOnlinePlayers()) send(p);
    }

    // ------------------------------------------------------------ jugadores
    public boolean send(Player player) {
        ResourcePackInfo info = pack;
        if (info == null) return false;
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info).replace(false).required(required).prompt(prompt).build());
        return true;
    }

    /** Anota un problema al preparar el pack: va a la consola y a /dino pack info. */
    /** Problema del pack: textos.yml pack.problemas.<clave>; va a la consola y a /dino pack info. */
    private void problem(String key, Object... kv) {
        problem(Lang.get().plain("pack.problemas." + key, kv));
    }

    private void problem(String message) {
        problem = message;
        plugin.getLogger().warning("Resource pack: " + message);
    }

    /** Estado para /dino pack info (con colores, textos.yml: pack.*). */
    public String status() {
        Lang lang = Lang.get();
        ResourcePackInfo info = pack;
        if ("off".equals(mode)) return lang.text("pack.desactivado");
        String note = problem;
        if (info == null) {
            return lang.text("pack.no-listo", "modo", mode, "motivo", note != null ? note : lang.text("pack.descargando"));
        }
        return lang.text("pack.listo", "modo", mode, "url", info.uri(), "hash", info.hash())
                + (note != null ? lang.text("pack.aviso", "aviso", note) : "");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) send(p);
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        ResourcePackInfo info = pack;
        if (info == null || !info.id().equals(event.getID())) return;
        switch (event.getStatus()) {
            case DECLINED -> {
                if (required) event.getPlayer().kick(Lang.get().chatMsg("pack-expulsado"));
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                plugin.getLogger().warning(event.getPlayer().getName() + " no pudo cargar el pack: " + event.getStatus());
                Lang.get().chat(event.getPlayer(), "pack-fallo", "estado", event.getStatus());
            }
            default -> {
            }
        }
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

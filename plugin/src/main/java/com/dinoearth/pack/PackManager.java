package com.dinoearth.pack;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        prompt = Component.text(c != null ? c.getString("prompt", "") : "", NamedTextColor.GOLD);
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
            default -> problem("resource-pack.mode desconocido: '" + mode + "' (usá url, host u off).");
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
        sender.sendMessage(Component.text("Recargando el resource pack...", NamedTextColor.YELLOW));
        // en modo url el hash se calcula en segundo plano; al terminar se manda a todos
    }

    private void startUrl(String url) {
        if (url == null || url.isBlank()) {
            problem("falta resource-pack.url en config.yml. Subí el "
                    + "DinoEarth-ResourcePack.zip (por ejemplo a mc-packs.net) y pegá el link directo, "
                    + "o usá resource-pack.mode: host.");
            return;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            problem("la URL no es válida: " + url);
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
                    problem("la URL respondió " + res.statusCode() + " (" + uri + ")");
                    return;
                }
                byte[] data = res.body();
                if (data.length > MAX_BYTES || data.length < 4 || data[0] != 'P' || data[1] != 'K') {
                    problem("lo que hay en la URL no es un .zip. "
                            + "¿Es un link de descarga directa? (" + uri + ")");
                    return;
                }
                String hash = sha1(data);
                if (bundledHash != null && !bundledHash.equals(hash)) {
                    problem("el zip de la URL NO es el de esta versión del plugin. "
                            + "Subí el DinoEarth-ResourcePack.zip nuevo (o usá /dino pack reload después de subirlo).");
                }
                Bukkit.getScheduler().runTask(plugin, () -> ready(uri, hash));
            } catch (Exception e) {
                problem("no se pudo descargar " + uri + ": " + e.getMessage());
            }
        });
    }

    private void startHost(ConfigurationSection c) {
        if (bundled == null) {
            problem("este jar no trae el pack adentro.");
            return;
        }
        int port = c != null ? c.getInt("host.port", 8163) : 8163;
        String address = c != null ? c.getString("host.public-address", "") : "";
        if (address == null || address.isBlank()) {
            address = Bukkit.getIp();
            if (address == null || address.isBlank()) {
                problem("poné resource-pack.host.public-address en config.yml "
                        + "(la IP o dominio con el que los jugadores entran al server).");
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
            problem("no se pudo abrir el puerto " + port + ": " + e.getMessage());
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
    private void problem(String message) {
        problem = message;
        plugin.getLogger().warning("Resource pack: " + message);
    }

    public String status() {
        ResourcePackInfo info = pack;
        if ("off".equals(mode)) return "desactivado";
        String note = problem;
        if (info == null) {
            return "modo " + mode + ", no está listo: " + (note != null ? note : "todavía descargando el pack, probá en unos segundos");
        }
        return "modo " + mode + ", " + info.uri() + " (sha1 " + info.hash() + ")" + (note != null ? " · AVISO: " + note : "");
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
                if (required) event.getPlayer().kick(Component.text(
                        "Este servidor necesita el pack de dinosaurios. Activá los paquetes de recursos del servidor "
                                + "(Multijugador → Editar servidor → Paquetes de recursos: Activados).", NamedTextColor.RED));
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                plugin.getLogger().warning(event.getPlayer().getName() + " no pudo cargar el pack: " + event.getStatus());
                event.getPlayer().sendMessage(Component.text(
                        "No se pudo cargar el pack de dinosaurios (" + event.getStatus() + "). Probá /dino pack.",
                        NamedTextColor.RED));
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

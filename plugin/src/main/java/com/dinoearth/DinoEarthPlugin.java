package com.dinoearth;

import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoListener;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.dex.DexManager;
import com.dinoearth.gui.GuiFont;
import com.dinoearth.feeder.FeederManager;
import com.dinoearth.egg.EggManager;
import com.dinoearth.item.CageManager;
import com.dinoearth.item.DinoItems;
import com.dinoearth.item.ItemListener;
import com.dinoearth.mission.MissionManager;
import com.dinoearth.order.OrderMenu;
import com.dinoearth.registry.DinoRegistry;
import com.dinoearth.registry.RecallManager;
import com.dinoearth.model.DinoRig;
import com.dinoearth.pack.PackManager;
import com.dinoearth.species.Species;
import com.dinoearth.integration.Money;
import com.dinoearth.integration.Wilderness;
import com.dinoearth.nest.NestManager;
import com.dinoearth.stats.PlayerStats;
import com.dinoearth.text.Fmt;
import com.dinoearth.text.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DinoEarthPlugin extends JavaPlugin {

    private static final String ADMIN = "dinoearth.admin";

    private Lang lang;
    private DinoManager dinos;
    private EggManager eggs;
    private PackManager pack;
    private FeederManager feeders;
    private DexManager dex;
    private DinoItems items;
    private CageManager cages;
    private DinoRegistry registry;
    private RecallManager recall;
    private com.dinoearth.race.RaceManager races;
    private com.dinoearth.plus.PlusAbilities plusAbilities;
    private com.dinoearth.spawn.NaturalSpawner natural;
    private com.dinoearth.spawn.PaleoMap paleoMap;
    private MissionManager missions;
    private PlayerStats stats;
    private NestManager nests;
    private com.dinoearth.bench.BenchManager bench;

    private static final int OFFER_TICKS = 60 * 20;

    /** Un dino ofrecido a otro jugador con /dino dar, esperando que lo acepte. */
    private record Offer(UUID wolf, UUID from, int expires) {
    }

    private final Map<UUID, Offer> offers = new HashMap<>();

    @Override
    public void onEnable() {
        ConfigUpdater.update(this);
        reloadConfig();
        lang = new Lang(this);
        for (Species s : Species.ALL) {
            try (InputStream in = getResource("rigs/" + s.id + ".json")) {
                s.rig = DinoRig.load(in);
            } catch (Exception e) {
                getLogger().severe("No se pudo cargar rigs/" + s.id + ".json: " + e);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            ConfigurationSection section = getConfig().getConfigurationSection("species." + s.id);
            // config de la version 0.2: tame-chance y ride.* sueltos eran del raptor
            if (section == null && s == Species.RAPTOR) section = getConfig();
            s.applyConfig(section);
            s.applyTexts(lang);
        }
        Keys keys = new Keys(this);
        Settings settings = new Settings(getConfig());
        feeders = new FeederManager(keys, settings);
        GuiFont gui;
        try (InputStream in = getResource("gui.json")) {
            gui = GuiFont.load(in);
        } catch (Exception e) {
            getLogger().severe("No se pudo cargar gui.json: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        dex = new DexManager(keys, settings, gui);
        dinos = new DinoManager(keys, settings, feeders, dex);
        dex.start(this, dinos);
        eggs = new EggManager(keys, dinos, settings.hatchTicks);
        try (InputStream in = getResource("items.json")) {
            items = new DinoItems(keys, in);
        } catch (Exception e) {
            getLogger().severe("No se pudo cargar items.json: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        cages = new CageManager(items, dinos, keys);
        registry = new DinoRegistry(this);
        dinos.setRegistry(registry);
        stats = new PlayerStats(this, registry);
        stats.setRefresher(dex::syncStats);
        getServer().getPluginManager().registerEvents(stats, this);
        getServer().getScheduler().runTaskTimer(this, stats::save, 1200L, 1200L);
        missions = new MissionManager(this, dinos, registry, settings);
        dex.setMissions(missions);
        getServer().getPluginManager().registerEvents(missions, this);
        races = new com.dinoearth.race.RaceManager(this, dinos, keys);
        plusAbilities = new com.dinoearth.plus.PlusAbilities(this, dinos);
        natural = new com.dinoearth.spawn.NaturalSpawner(this, dinos, keys);
        getServer().getPluginManager().registerEvents(natural, this);
        missions.setNatural(natural);
        paleoMap = new com.dinoearth.spawn.PaleoMap(this, keys, natural);
        getServer().getPluginManager().registerEvents(paleoMap, this);
        getServer().getPluginManager().registerEvents(plusAbilities, this);
        getServer().getPluginManager().registerEvents(races, this);
        bench = new com.dinoearth.bench.BenchManager(this, dinos);
        recall = new RecallManager(this, dinos, registry, keys, settings);
        getServer().getPluginManager().registerEvents(recall, this);
        getServer().getScheduler().runTaskTimer(this, registry::save, 600L, 600L);
        getServer().getPluginManager().registerEvents(new OrderMenu(dinos, items, keys, recall), this);
        getServer().getPluginManager().registerEvents(new ItemListener(items, cages, dinos), this);
        nests = new NestManager(this, keys, settings, dinos, eggs);
        getServer().getPluginManager().registerEvents(nests, this);
        getServer().getPluginManager().registerEvents(new DinoListener(this, dinos, eggs, keys, items, cages, nests), this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            dinos.tick();
            eggs.tick();
        }, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, feeders::tick, 20L, 20L);
        // los mundos ya estan cargados si es un /reload
        getServer().getScheduler().runTask(this, () -> {
            dinos.attachLoaded();
            eggs.attachLoaded();
            feeders.attachLoaded();
            nests.attachLoaded();
            // integraciones: despues de que arrancaron todos los plugins (la economia de Vault se registra tarde)
            Money.setup(getLogger());
            Wilderness.setup(getLogger());
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                try {
                    com.dinoearth.integration.DinoPlaceholders.register(this, dinos, dex, missions, stats);
                } catch (LinkageError e) {
                    getLogger().warning("No se pudo conectar con PlaceholderAPI: " + e);
                }
            }
            getServer().getOnlinePlayers().forEach(dex::syncStats);
        });
        pack = new PackManager(this);
        getServer().getPluginManager().registerEvents(pack, this);
        pack.start();
        getLogger().info("DinoEarth activado: " + Species.ALL.size() + " especies");
    }

    @Override
    public void onDisable() {
        if (bench != null && bench.running()) bench.stop(getServer().getConsoleSender());
        if (dinos != null) dinos.shutdown();
        if (eggs != null) eggs.shutdown();
        if (nests != null) nests.shutdown();
        if (stats != null) stats.save();
        if (feeders != null) feeders.shutdown();
        if (pack != null) pack.stop();
        if (races != null) races.shutdown();
        if (recall != null) recall.shutdown();
        if (registry != null) registry.save();
        if (missions != null) missions.save();
        if (lang != null) lang.shutdown();
    }

    // ------------------------------------------------------------ comandos
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "pack" -> {
                return packCommand(sender, args);
            }
            case "dar", "regalar" -> {
                return give(sender, args);
            }
            case "aceptar" -> {
                return accept(sender);
            }
            case "rechazar" -> {
                return reject(sender);
            }
            case "mis", "misdinos" -> {
                if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
                recall.list(player);
                return true;
            }
            case "llamar" -> {
                if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
                if (args.length < 2) {
                    recall.openMenu(player, false);
                } else {
                    recall.recallById(player, List.of(args).subList(1, args.length));
                }
                return true;
            }
            case "misiones" -> {
                if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
                dex.openMissions(player);
                return true;
            }
            case "top", "tops" -> {
                return top(sender, args);
            }
            case "carrera", "carreras" -> {
                return race(sender, args);
            }
            default -> {
            }
        }
        if (!sender.hasPermission(ADMIN)) {
            lang.chatLines(sender, "ayuda-jugador");
            return true;
        }
        switch (sub) {
            case "item", "items" -> giveItem(sender, args);
            case "spawn" -> spawn(sender, args);
            case "egg", "huevo" -> {
                // /dino egg <especie> [cantidad] [jugador]: con jugador sirve desde la consola (tienda, Tebex)
                Species s = args.length > 1 ? Species.byId(args[1]) : Species.RAPTOR;
                if (s == null || !s.rig.hasEgg()) return chat(sender, "huevo-especies", "lista", String.join(", ", eggSpecies()));
                int amount = args.length > 2 ? (int) parse(args[2], 1) : 1;
                amount = Math.max(1, Math.min(64, amount));
                Player target = args.length > 3 ? getServer().getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
                if (target == null) {
                    return args.length > 3 ? chat(sender, "jugador-desconectado", "player", args[3]) : chat(sender, "solo-jugadores");
                }
                for (var rest : target.getInventory().addItem(eggs.createItem(s, amount)).values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), rest);
                }
                chat(sender, "huevo-entregado", "cantidad", amount, "especie", s.name);
                if (target != sender) chat(target, "huevo-recibido", "cantidad", amount, "especie", s.name);
            }
            case "grow", "crecer" -> {
                if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
                float amount = (float) parse(args.length > 1 ? args[1] : "25", 25) / 100f;
                int n = 0;
                for (Dino d : nearby(player, 10)) {
                    if (d.isAdult()) continue;
                    d.addGrowth(amount);
                    n++;
                }
                chat(sender, "crecer", "cantidad", n);
            }
            case "remove" -> {
                double radius = args.length > 1 ? parse(args[1], 16) : 16;
                Species only = args.length > 2 ? Species.byId(args[2]) : null;
                List<Dino> list = sender instanceof Player p ? nearby(p, radius) : dinos.all();
                int n = 0;
                for (Dino d : list) {
                    if (only != null && d.species() != only) continue;
                    d.wolf().remove();
                    n++;
                }
                chat(sender, "remover", "cantidad", n);
            }
            case "comedero", "feeder" -> {
                if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
                Species.Diet diet = args.length > 1 ? Species.Diet.byId(args[1]) : Species.Diet.MEAT;
                if (diet == null) return chat(sender, "dietas", "lista", String.join(", ", diets()));
                int amount = Math.max(1, Math.min(64, args.length > 2 ? (int) parse(args[2], 1) : 1));
                player.getInventory().addItem(feeders.createItem(diet, amount));
                chat(sender, "comedero-entregado", "cantidad", amount, "dieta", Fmt.diet(diet));
            }
            case "dinopedia", "libro" -> {
                Player target = args.length > 1 ? getServer().getPlayerExact(args[1])
                        : sender instanceof Player p ? p : null;
                if (target == null) return chat(sender, "dinopedia-uso");
                target.getInventory().addItem(dex.createBook());
                chat(sender, "dinopedia-entregada", "player", target.getName());
            }
            case "hambre", "hunger" -> {
                if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
                float value = (float) parse(args.length > 1 ? args[1] : "100", 100) / 100f;
                int n = 0;
                for (Dino d : nearby(player, 10)) {
                    if (!d.tracksHunger()) continue;
                    d.setHunger(value);
                    n++;
                }
                chat(sender, "hambre-puesta", "porcentaje", Math.round(value * 100), "cantidad", n);
            }
            case "tame", "domesticar" -> tame(sender, args);
            case "nido", "nest" -> {
                if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
                Dino d = lookedAt(player);
                if (d == null) return chat(sender, "nido-mira");
                chat(sender, nests.forceNest(d) ? "nido-hecho" : "nido-no", "especie", d.species().name);
            }
            case "stats", "estadisticas" -> stats(sender);
            case "bench", "carga" -> bench(sender, args);
            case "mapa", "region" -> {
                if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
                if (args.length > 1 && args[1].equalsIgnoreCase("probar")) natural.probe(player, 30);
                else natural.info(player);
            }
            case "reload", "recargar" -> {
                lang.reload();
                reloadConfig();
                races.loadConfig();
                plusAbilities.loadConfig();
                natural.loadConfig();
                paleoMap.reload();
                for (Species s : Species.ALL) s.applyTexts(lang);
                chat(sender, "recargado");
            }
            case "count" -> {
                List<String> parts = new ArrayList<>();
                for (Species s : Species.ALL) {
                    long c = dinos.all().stream().filter(d -> d.species() == s).count();
                    if (c > 0) parts.add(Lang.apply(lang.raw("messages.yml", "chat.contar-especie"), "especie", s.name, "cantidad", c));
                }
                lang.chat(sender, "contar", "cantidad", dinos.count(), "lista", Lang.parse(String.join("&8, ", parts)));
            }
            default -> lang.chatLines(sender, "ayuda-admin");
        }
        return true;
    }

    /**
     * Admin: domestica el dino que estas mirando (o el mas cercano a 4 bloques) a tu nombre o al de otro
     * jugador. Sirve tambien para los que solo se crian desde el huevo (T-Rex, Giganotosaurus).
     */
    private boolean tame(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
        Player owner = player;
        if (args.length > 1) {
            owner = getServer().getPlayerExact(args[1]);
            if (owner == null) return chat(sender, "jugador-desconectado", "player", args[1]);
        }
        Dino d = lookedAt(player);
        if (d == null) return chat(sender, "tame-mira");
        Species s = d.species();
        if (!s.tameable()) return chat(sender, "tame-salvaje", "especie", s.name);
        Wolf wolf = d.wolf();
        if (wolf.isTamed() && owner.getUniqueId().equals(wolf.getOwnerUniqueId())) {
            return chat(sender, "tame-ya-es", "especie", s.name, "player", owner.getName());
        }
        wolf.setOwner(owner);
        wolf.setTarget(null);
        wolf.setAngry(false);
        wolf.setSitting(true);
        d.healFully();
        d.setHunger(1f);
        d.onTamed();
        wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0, wolf.getHeight() + 0.3, 0), 9, 0.6, 0.4, 0.6);
        registry.update(d);
        dex.addTamed(owner, s, d.variant());
        int limit = dinos.tamedLimit(owner), has = dinos.ownedCount(owner.getUniqueId());
        if (limit > 0 && has > limit) {
            chat(sender, "tame-limite", "player", owner.getName(), "cantidad", has, "limite", limit);
        }
        chat(sender, "tame-hecho", "especie", s.name, "color", s.colorName(d.variant()), "player", owner.getName());
        if (owner != player) chat(owner, "tame-regalo", "player", player.getName(), "especie", s.name);
        return true;
    }

    /** El dino al que apunta la mira (las cajas se agrandan un poco porque el modelo es mas grande que el lobo). */
    private Dino lookedAt(Player player) {
        var hit = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                24, 0.8, e -> e instanceof Wolf && dinos.isDino(e));
        if (hit != null && hit.getHitEntity() != null) return dinos.get(hit.getHitEntity());
        Dino best = null;
        double bestD = 4 * 4;
        for (Dino d : nearby(player, 4)) {
            double dist = d.wolf().getLocation().distanceSquared(player.getLocation());
            if (dist < bestD) {
                best = d;
                bestD = dist;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ items propios
    private void giveItem(CommandSender sender, String[] args) {
        boolean map = args.length > 1 && com.dinoearth.spawn.PaleoMap.isMapId(args[1]);
        if (args.length < 2 || !map && items.def(args[1].toLowerCase()) == null) {
            chat(sender, "items-lista", "lista", String.join(", ", itemIds()));
            return;
        }
        String id = args[1].toLowerCase();
        int amount = Math.max(1, Math.min(64, args.length > 2 ? (int) parse(args[2], 1) : 1));
        Player target = args.length > 3 ? getServer().getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
        if (target == null) {
            chat(sender, "item-uso");
            return;
        }
        if (map) {
            for (int i = 0; i < amount; i++) {
                for (var rest : target.getInventory().addItem(paleoMap.createItem(1)).values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), rest);
                }
            }
            chat(sender, "item-entregado", "cantidad", amount, "item", lang.plain("mapa.nombre"), "player", target.getName());
            return;
        }
        int max = items.def(id).maxStack();
        for (int left = amount; left > 0; left -= max) {
            for (var rest : target.getInventory().addItem(items.create(id, Math.min(max, left))).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), rest);
            }
        }
        chat(sender, "item-entregado", "cantidad", amount, "item", items.plainName(id), "player", target.getName());
    }

    // ------------------------------------------------------------ regalar dinos
    /** /dino dar <jugador>: ofrece el dino que estas mirando; el otro tiene 60 segundos para aceptarlo. */
    private boolean give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
        if (args.length < 2) return chat(sender, "dar-uso");
        Player target = getServer().getPlayerExact(args[1]);
        if (target == null) return chat(sender, "jugador-desconectado", "player", args[1]);
        if (target == player) return chat(sender, "dar-mismo");
        Dino d = lookedAt(player);
        if (d == null) return chat(sender, "dar-mira");
        if (!d.wolf().isTamed() || !player.getUniqueId().equals(d.wolf().getOwnerUniqueId())) return chat(sender, "no-es-tuyo");
        if (d.rider() != null) return chat(sender, "dar-bajate");
        offers.put(target.getUniqueId(), new Offer(d.wolf().getUniqueId(), player.getUniqueId(),
                getServer().getCurrentTick() + OFFER_TICKS));
        String detail = d.species().name + " " + d.species().colorName(d.variant()) + ", " + Fmt.sex(d.isMale());
        chat(sender, "dar-ofrecido", "player", target.getName());
        Component yes = lang.chatMsg("boton-aceptar").clickEvent(ClickEvent.runCommand("/dino aceptar"))
                .hoverEvent(HoverEvent.showText(lang.chatMsg("boton-aceptar-hover")));
        Component no = lang.chatMsg("boton-rechazar").clickEvent(ClickEvent.runCommand("/dino rechazar"))
                .hoverEvent(HoverEvent.showText(lang.chatMsg("boton-rechazar-hover")));
        lang.chat(target, "dar-oferta", "aceptar", yes, "rechazar", no, "player", player.getName(),
                "dino", OrderMenu.label(d), "detalle", detail);
        return true;
    }

    /**
     * /dino carrera ...: los jugadores se anotan (unirse, lo usa el NPC de FancyNpcs), salen y ven las pistas; los
     * admins arman la pista parados en el lugar (crear, largada, puerta, deshacer, limpiar, vueltas, especie, ver, info)
     * y manejan las carreras (iniciar, cancelar, borrar).
     */
    private boolean race(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return chat(sender, "solo-jugadores");
        String action = args.length > 1 ? args[1].toLowerCase() : "";
        String id = args.length > 2 ? args[2].toLowerCase() : null;
        var t = races.track(id);
        switch (action) {
            case "unirse", "anotarme", "entrar" -> {
                if (t == null) return chat(sender, "carrera-no-existe", "pista", id != null ? id : "?");
                races.join(p, t);
                return true;
            }
            case "salir" -> {
                races.leave(p);
                return true;
            }
            case "pistas", "lista" -> {
                races.list(p);
                return true;
            }
            default -> {
            }
        }
        if (!sender.hasPermission(ADMIN)) {
            lang.chatLines(sender, "carrera-ayuda");
            return true;
        }
        if (action.equals("crear")) {
            if (id == null) return chat(sender, "carrera-uso");
            races.create(p, id);
            return true;
        }
        if (action.isEmpty()) {
            lang.chatLines(sender, "carrera-ayuda-admin");
            return true;
        }
        if (t == null) return chat(sender, "carrera-no-existe", "pista", id != null ? id : "?");
        switch (action) {
            case "largada" -> races.addGrid(p, t);
            case "puerta" -> races.addGate(p, t, args.length > 3 ? parse(args[3], 8) : 8, args.length > 4 ? parse(args[4], 4) : 4);
            case "deshacer" -> races.undoGate(p, t);
            case "limpiar" -> races.clear(p, t);
            case "vueltas" -> races.setLaps(p, t, args.length > 3 ? (int) parse(args[3], 3) : 3);
            case "especie" -> {
                Species s = args.length > 3 ? Species.byId(args[3]) : null;
                if (s == null) return chat(sender, "spawn-especies", "lista", String.join(", ", ids()));
                races.setSpecies(p, t, s);
            }
            case "ver" -> races.show(p, t, 300);
            case "info" -> races.info(p, t);
            case "iniciar" -> races.forceStart(p, t);
            case "cancelar" -> races.forceCancel(p, t);
            case "borrar" -> races.delete(p, t);
            default -> lang.chatLines(sender, "carrera-ayuda-admin");
        }
        return true;
    }

    /** /dino top [categoria]: los mejores del server (los mismos datos que los placeholders de PlaceholderAPI). */
    private boolean top(CommandSender sender, String[] args) {
        String category = args.length > 1 ? args[1].toLowerCase() : "dinos";
        if (!PlayerStats.CATEGORIES.contains(category)) {
            return chat(sender, "top-categorias", "lista", String.join(", ", PlayerStats.CATEGORIES));
        }
        int size = Math.max(1, getConfig().getInt("tops.top-size", 10));
        List<PlayerStats.Row> rows = stats.top(category);
        String title = lang.plain("tops." + category);
        if (rows.isEmpty()) return chat(sender, "top-vacio", "categoria", title);
        chat(sender, "top-titulo", "categoria", title);
        for (int i = 0; i < Math.min(size, rows.size()); i++) {
            PlayerStats.Row r = rows.get(i);
            double v = r.value();
            String value = v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
            if (category.equals("genes")) value = lang.plain("tops.genes-valor", "valor", value);
            chat(sender, "top-linea", "puesto", i + 1, "player", r.name(), "valor", value);
        }
        if (sender instanceof Player p) {
            int pos = stats.position(p.getUniqueId(), category);
            if (pos > size) chat(sender, "top-tu-puesto", "puesto", pos);
        }
        return true;
    }

    /**
     * /dino bench &lt;dinos por jugador, por ejemplo 2,5,10&gt; [minutos por etapa] [radio] | /dino bench parar:
     * prueba de carga con los jugadores (o bots) conectados. Ver docs/prueba-de-carga.md.
     */
    private void bench(CommandSender sender, String[] args) {
        String first = args.length > 1 ? args[1].toLowerCase() : "";
        if (first.equals("parar") || first.equals("stop")) {
            bench.stop(sender);
            return;
        }
        List<Integer> levels = new ArrayList<>();
        for (String part : first.split(",")) {
            if (!part.matches("\\d+")) {
                chat(sender, "bench-uso");
                return;
            }
            levels.add(Math.max(1, Math.min(50, Integer.parseInt(part))));
        }
        double minutes = Math.max(0.5, Math.min(30, args.length > 2 ? parse(args[2], 2) : 2));
        double radius = Math.max(8, Math.min(64, args.length > 3 ? parse(args[3], 24) : 24));
        bench.start(sender, levels, minutes, radius);
    }

    /** /dino stats: cuanto consume el plugin ahora mismo. */
    private void stats(CommandSender sender) {
        List<Dino> all = dinos.all();
        long tamed = all.stream().filter(d -> d.wolf().isTamed()).count();
        long seen = all.stream().filter(Dino::isSeen).count();
        long quiet = all.stream().filter(d -> d.isSeen() && d.isQuiet()).count();
        long parts = all.stream().mapToLong(Dino::modelParts).sum();
        double[] r = dinos.poseRates();
        double sent = r[0] + r[2];
        int players = Math.max(1, getServer().getOnlinePlayers().size());
        double kb = r[1] * 90 / 1024.0;      // ~90 bytes por hueso mandado a cada jugador
        Map<UUID, Integer> owners = new HashMap<>();
        for (var e : registry.all()) owners.merge(e.owner(), 1, Integer::sum);
        int total = owners.values().stream().mapToInt(Integer::intValue).sum();
        int limit = getConfig().getInt("limits.max-tamed-per-player", 20);
        String top = owners.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(5)
                .map(e -> {
                    String name = getServer().getOfflinePlayer(e.getKey()).getName();
                    return lang.plain("stats.top", "player", name != null ? name : e.getKey().toString().substring(0, 8),
                            "cantidad", e.getValue());
                }).reduce((a, b) -> a + ", " + b).orElse(lang.plain("stats.nadie"));
        lang.chatLines(sender, "stats",
                "dinos", all.size(), "salvajes", all.size() - tamed, "domesticados", tamed, "displays", parts,
                "vistos", seen, "quietos", quiet,
                "mandados", String.format("%.0f", r[0]), "ahorrados", String.format("%.0f", r[2]),
                "porcentaje", String.format("%.0f", sent > 0 ? r[2] * 100 / sent : 0),
                "kb_total", String.format("%.1f", kb), "kb_jugador", String.format("%.1f", kb / players),
                "tick", String.format("%.2f", dinos.avgTickMs()), "tick_max", String.format("%.2f", dinos.maxTickMs()),
                "total", total, "jugadores", owners.size(),
                "limite", limit > 0 ? String.valueOf(limit) : lang.plain("stats.sin-limite"),
                "top", top, "vista", getServer().getViewDistance(), "simulacion", getServer().getSimulationDistance());
    }

    private boolean accept(CommandSender sender) {
        if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
        Offer offer = offers.remove(player.getUniqueId());
        if (offer == null || offer.expires() < getServer().getCurrentTick()) return chat(sender, "aceptar-nada");
        Entity e = Bukkit.getEntity(offer.wolf());
        Dino d = e == null ? null : dinos.get(e);
        if (d == null || d.isDying() || !offer.from().equals(d.wolf().getOwnerUniqueId())) {
            return chat(sender, "aceptar-no-disponible");
        }
        if (!dinos.canOwnMore(player.getUniqueId())) {
            offers.put(player.getUniqueId(), offer);
            return chat(sender, "limite", "cantidad", dinos.ownedCount(player.getUniqueId()), "limite", dinos.tamedLimit(player));
        }
        if (d.rider() != null) d.wolf().removePassenger(d.rider());
        d.wolf().setOwner(player);
        d.wolf().setTarget(null);
        d.setOrder(Dino.Order.STAY, player);
        d.refreshPlate();
        registry.update(d);
        dex.addTamed(player, d.species(), d.variant());
        chat(sender, "aceptar-hecho");
        Player from = getServer().getPlayer(offer.from());
        if (from != null) chat(from, "aceptar-aviso", "player", player.getName());
        return true;
    }

    private boolean reject(CommandSender sender) {
        if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
        Offer offer = offers.remove(player.getUniqueId());
        if (offer == null) return chat(sender, "rechazar-nada");
        chat(sender, "rechazar-hecho");
        Player from = getServer().getPlayer(offer.from());
        if (from != null) chat(from, "rechazar-aviso", "player", player.getName());
        return true;
    }

    private boolean packCommand(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "";
        if (!action.isEmpty() && !sender.hasPermission(ADMIN)) return chat(sender, "sin-permiso");
        switch (action) {
            case "reload" -> pack.reload(sender);
            case "info" -> lang.chat(sender, "pack-info", "estado", Lang.parse(pack.status()));
            default -> {
                if (!(sender instanceof Player player)) return chat(sender, "solo-jugadores");
                chat(sender, pack.send(player) ? "pack-enviado" : "pack-sin-configurar");
            }
        }
        return true;
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            chat(sender, "solo-jugadores");
            return;
        }
        Species species = args.length > 1 ? Species.byId(args[1]) : null;
        if (species == null) {
            chat(sender, "spawn-especies", "lista", String.join(", ", ids()));
            return;
        }
        // /dino spawn <tipo> [color] [sexo] [edad] [cantidad], cada uno en su lugar
        String color = arg(args, 2);
        String sex = arg(args, 3);
        String ageArg = arg(args, 4);
        String amount = arg(args, 5);

        String variant = null;
        if (color != null && !color.equals("random")) {
            if (!species.rig.variants().containsKey(color)) {
                chat(sender, "spawn-color", "especie", species.name, "lista", String.join(", ", colors(species)));
                return;
            }
            variant = color;
        }
        Boolean male = null;
        if (sex != null && !sex.equals("random")) {
            if (!SEXES.contains(sex)) {
                chat(sender, "spawn-sexo", "lista", String.join(", ", SEXES));
                return;
            }
            male = sex.equals("macho");
        }
        float growth = 1f;
        if (ageArg != null) {
            if (!ages(species).contains(ageArg)) {
                chat(sender, "spawn-edad", "lista", String.join(", ", ages(species)));
                if (!species.hasBabies()) chat(sender, "spawn-sin-crias", "especie", species.name);
                return;
            }
            growth = ageArg.equals("bebe") ? 0f : 1f;
        }
        int count = species.group;
        if (amount != null) {
            if (!amount.matches("\\d+")) {
                chat(sender, "spawn-cantidad");
                return;
            }
            count = Math.max(1, Math.min(20, Integer.parseInt(amount)));
        }
        Dino last = null;
        for (int i = 0; i < count; i++) {
            Location at = player.getLocation();
            if (count > 1) {
                at.add(ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 0, ThreadLocalRandom.current().nextDouble(-1.5, 1.5));
            }
            last = dinos.spawn(species, at, variant, male, growth, null);
        }
        if (last == null) return;
        if (count > 1) {
            chat(sender, "spawn-varios", "cantidad", count, "especie", species.name);
        } else {
            String detail = Fmt.sex(last.isMale()) + (growth < 1 ? ", " + lang.plain("spawn.cria") : "");
            chat(sender, "spawn-uno", "especie", species.name, "color", species.colorName(last.variant()), "detalle", detail);
        }
    }

    private List<Dino> nearby(Player player, double radius) {
        List<Dino> out = new ArrayList<>();
        for (Dino d : dinos.all()) {
            if (d.wolf().getWorld() == player.getWorld()
                    && d.wolf().getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                out.add(d);
            }
        }
        return out;
    }

    private static List<String> ids() {
        return Species.ALL.stream().map(s -> s.id).toList();
    }

    private List<String> itemIds() {
        List<String> ids = new ArrayList<>(items.ids());
        ids.add("mapa");
        return ids;
    }

    private static List<String> eggSpecies() {
        return Species.ALL.stream().filter(s -> s.rig != null && s.rig.hasEgg()).map(s -> s.id).toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            return filter(admin ? List.of("spawn", "egg", "grow", "tame", "nido", "item", "mis", "llamar", "misiones", "top", "carrera",
                    "dar", "aceptar", "rechazar", "remove", "count", "stats", "bench", "mapa", "comedero", "dinopedia", "hambre", "pack", "reload")
                    : List.of("mis", "llamar", "misiones", "top", "carrera", "dar", "aceptar", "rechazar", "pack"), args[0]);
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("llamar") && args.length <= 3 && sender instanceof Player p) {
            return filter(registry.ownedBy(p.getUniqueId()).stream().map(e -> RecallManager.shortId(e.id())).toList(),
                    args[args.length - 1]);
        }
        if (args.length == 2 && (sub.equals("dar") || sub.equals("regalar"))) {
            return filter(getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && sub.equals("pack") && admin) return filter(List.of("reload", "info"), args[1]);
        if (args.length == 2 && sub.equals("mapa") && admin) return filter(List.of("probar"), args[1]);
        if (args.length == 2 && (sub.equals("top") || sub.equals("tops"))) return filter(PlayerStats.CATEGORIES, args[1]);
        if (sub.equals("carrera") || sub.equals("carreras")) {
            if (args.length == 2) {
                return filter(admin ? List.of("unirse", "salir", "pistas", "crear", "largada", "puerta", "deshacer", "limpiar",
                        "vueltas", "especie", "ver", "info", "iniciar", "cancelar", "borrar")
                        : List.of("unirse", "salir", "pistas"), args[1]);
            }
            if (args.length == 3 && !args[1].equalsIgnoreCase("salir") && !args[1].equalsIgnoreCase("pistas")
                    && !args[1].equalsIgnoreCase("crear")) {
                return filter(races.ids(), args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("especie")) {
                return filter(Species.ALL.stream().filter(s -> s.rideable && !s.flies() && !s.aquatic()).map(s -> s.id).toList(), args[3]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("puerta")) return filter(List.of("6", "8", "12"), args[3]);
            if (args.length == 4 && args[1].equalsIgnoreCase("vueltas")) return filter(List.of("1", "2", "3", "5"), args[3]);
            return List.of();
        }
        if (!admin) return List.of();
        if (sub.equals("item") || sub.equals("items")) {
            if (args.length == 2) return filter(itemIds(), args[1]);
            if (args.length == 3) return filter(List.of("1", "16", "64"), args[2]);
            if (args.length == 4) return filter(getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        }
        if (args.length == 2 && sub.equals("spawn")) return filter(ids(), args[1]);
        if (args.length == 2 && (sub.equals("egg") || sub.equals("huevo"))) return filter(eggSpecies(), args[1]);
        if (args.length == 3 && (sub.equals("egg") || sub.equals("huevo"))) return filter(List.of("1", "5", "16"), args[2]);
        if (args.length == 4 && (sub.equals("egg") || sub.equals("huevo"))) {
            return filter(getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        }
        if (args.length == 3 && sub.equals("remove")) return filter(ids(), args[2]);
        if (args.length == 2 && (sub.equals("comedero") || sub.equals("feeder"))) return filter(diets(), args[1]);
        if (args.length == 2 && sub.equals("hambre")) return filter(List.of("0", "25", "50", "100"), args[1]);
        if (args.length == 2 && (sub.equals("dinopedia") || sub.equals("tame") || sub.equals("domesticar"))) {
            return filter(getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (sub.equals("spawn")) {
            Species s = Species.byId(args[1]);
            if (s == null) return List.of();
            String typed = args[args.length - 1];
            return switch (args.length) {
                case 3 -> filter(withRandom(colors(s)), typed);      // <color>
                case 4 -> filter(withRandom(SEXES), typed);          // <sexo>
                case 5 -> filter(ages(s), typed);                    // <edad>
                case 6 -> filter(List.of("1", "5", "10"), typed);    // [cantidad]
                default -> List.of();
            };
        }
        return List.of();
    }

    // argumentos de comandos (no son textos que se muestran: son lo que se escribe)
    private static final List<String> SEXES = List.of("macho", "hembra");

    private static List<String> diets() {
        return java.util.Arrays.stream(Species.Diet.values()).map(d -> d.label).toList();
    }

    private static List<String> colors(Species s) {
        return List.copyOf(s.rig.variants().keySet());
    }

    private static List<String> ages(Species s) {
        return s.hasBabies() ? List.of("adulto", "bebe") : List.of("adulto");
    }

    private static List<String> withRandom(List<String> options) {
        List<String> out = new ArrayList<>();
        out.add("random");
        out.addAll(options);
        return out;
    }

    private static String arg(String[] args, int i) {
        return args.length > i && !args[i].isBlank() ? args[i].toLowerCase() : null;
    }

    private static List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase())).toList();
    }

    private boolean chat(CommandSender to, String key, Object... kv) {
        lang.chat(to, key, kv);
        return true;
    }

    private static double parse(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}

package com.dinoearth;

import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoListener;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.dex.DexManager;
import com.dinoearth.feeder.FeederManager;
import com.dinoearth.egg.EggManager;
import com.dinoearth.item.CageManager;
import com.dinoearth.item.DinoItems;
import com.dinoearth.item.ItemListener;
import com.dinoearth.order.OrderMenu;
import com.dinoearth.model.DinoRig;
import com.dinoearth.pack.PackManager;
import com.dinoearth.species.Species;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DinoEarthPlugin extends JavaPlugin {

    private static final String ADMIN = "dinoearth.admin";

    private DinoManager dinos;
    private EggManager eggs;
    private PackManager pack;
    private FeederManager feeders;
    private DexManager dex;
    private DinoItems items;
    private CageManager cages;

    /** Comandos que puede usar cualquier jugador; el resto es de admin. */
    private static final Set<String> PLAYER_COMMANDS = Set.of("pack", "dar", "regalar", "aceptar", "rechazar");
    private static final int OFFER_TICKS = 60 * 20;

    /** Un dino ofrecido a otro jugador con /dino dar, esperando que lo acepte. */
    private record Offer(UUID wolf, UUID from, int expires) {
    }

    private final Map<UUID, Offer> offers = new HashMap<>();

    @Override
    public void onEnable() {
        ConfigUpdater.update(this);
        reloadConfig();
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
        }
        Keys keys = new Keys(this);
        Settings settings = new Settings(getConfig());
        feeders = new FeederManager(keys, settings);
        dex = new DexManager(keys);
        dinos = new DinoManager(keys, settings, feeders, dex);
        eggs = new EggManager(keys, dinos, settings.hatchTicks);
        try (InputStream in = getResource("items.json")) {
            items = new DinoItems(keys, in);
        } catch (Exception e) {
            getLogger().severe("No se pudo cargar items.json: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        cages = new CageManager(items, dinos, keys);
        getServer().getPluginManager().registerEvents(new OrderMenu(dinos, items, keys), this);
        getServer().getPluginManager().registerEvents(new ItemListener(items, cages, dinos), this);
        getServer().getPluginManager().registerEvents(new DinoListener(this, dinos, eggs, keys, items, cages), this);
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
        });
        pack = new PackManager(this);
        getServer().getPluginManager().registerEvents(pack, this);
        pack.start();
        getLogger().info("DinoEarth activado: " + Species.ALL.size() + " especies");
    }

    @Override
    public void onDisable() {
        if (dinos != null) dinos.shutdown();
        if (eggs != null) eggs.shutdown();
        if (feeders != null) feeders.shutdown();
        if (pack != null) pack.stop();
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
            default -> {
            }
        }
        if (!sender.hasPermission(ADMIN)) {
            return msg(sender, "Podés usar /dino dar <jugador> (regalar el dino que mirás) o /dino pack.", NamedTextColor.GRAY);
        }
        switch (sub) {
            case "item", "items" -> giveItem(sender, args);
            case "spawn" -> spawn(sender, args);
            case "egg", "huevo" -> {
                if (!(sender instanceof Player player)) return msg(sender, "Solo jugadores.", NamedTextColor.RED);
                Species s = args.length > 1 ? Species.byId(args[1]) : Species.RAPTOR;
                if (s == null || !s.rig.hasEgg()) {
                    return msg(sender, "Especies con huevo: " + String.join(", ", eggSpecies()), NamedTextColor.RED);
                }
                int amount = args.length > 2 ? (int) parse(args[2], 1) : 1;
                amount = Math.max(1, Math.min(64, amount));
                player.getInventory().addItem(eggs.createItem(s, amount));
                msg(sender, "Te di " + amount + " huevo(s) de " + s.name + ".", NamedTextColor.GREEN);
            }
            case "grow", "crecer" -> {
                if (!(sender instanceof Player player)) return msg(sender, "Solo jugadores.", NamedTextColor.RED);
                float amount = (float) parse(args.length > 1 ? args[1] : "25", 25) / 100f;
                int n = 0;
                for (Dino d : nearby(player, 10)) {
                    if (d.isAdult()) continue;
                    d.addGrowth(amount);
                    n++;
                }
                msg(sender, "Crías que crecieron: " + n, NamedTextColor.GREEN);
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
                msg(sender, "Dinosaurios eliminados: " + n, NamedTextColor.YELLOW);
            }
            case "comedero", "feeder" -> {
                if (!(sender instanceof Player player)) return msg(sender, "Solo jugadores.", NamedTextColor.RED);
                Species.Diet diet = args.length > 1 ? Species.Diet.byId(args[1]) : Species.Diet.MEAT;
                if (diet == null) return msg(sender, "Dietas: " + String.join(", ", diets()), NamedTextColor.RED);
                int amount = Math.max(1, Math.min(64, args.length > 2 ? (int) parse(args[2], 1) : 1));
                player.getInventory().addItem(feeders.createItem(diet, amount));
                msg(sender, "Te di " + amount + " comedero(s) de " + diet.label + ".", NamedTextColor.GREEN);
            }
            case "dinopedia", "libro" -> {
                Player target = args.length > 1 ? getServer().getPlayerExact(args[1])
                        : sender instanceof Player p ? p : null;
                if (target == null) return msg(sender, "/dino dinopedia [jugador]", NamedTextColor.RED);
                target.getInventory().addItem(dex.createBook());
                msg(sender, "Dinopedia entregada a " + target.getName() + ".", NamedTextColor.GREEN);
            }
            case "hambre", "hunger" -> {
                if (!(sender instanceof Player player)) return msg(sender, "Solo jugadores.", NamedTextColor.RED);
                float value = (float) parse(args.length > 1 ? args[1] : "100", 100) / 100f;
                int n = 0;
                for (Dino d : nearby(player, 10)) {
                    if (!d.tracksHunger()) continue;
                    d.setHunger(value);
                    n++;
                }
                msg(sender, "Hambre al " + Math.round(value * 100) + "% en " + n + " dino(s) domesticado(s).",
                        NamedTextColor.GREEN);
            }
            case "tame", "domesticar" -> tame(sender, args);
            case "count" -> {
                StringBuilder sb = new StringBuilder("Cargados: " + dinos.count());
                for (Species s : Species.ALL) {
                    long c = dinos.all().stream().filter(d -> d.species() == s).count();
                    if (c > 0) sb.append(" · ").append(s.name).append(": ").append(c);
                }
                msg(sender, sb.toString(), NamedTextColor.AQUA);
            }
            default -> {
                msg(sender, "/dino spawn <tipo> [color|random] [macho|hembra|random] [adulto|bebe] [cantidad]", NamedTextColor.GRAY);
                msg(sender, "/dino egg <especie> [cantidad]  ·  /dino grow [%]  ·  /dino remove [radio] [especie]  ·  /dino count",
                        NamedTextColor.GRAY);
                msg(sender, "/dino comedero <carne|plantas|pescado> [cantidad]  ·  /dino dinopedia [jugador]  ·  /dino hambre [%]",
                        NamedTextColor.GRAY);
                msg(sender, "/dino tame [jugador]  (domestica el dino que estás mirando)", NamedTextColor.GRAY);
                msg(sender, "/dino item <id> [cantidad] [jugador]  ·  /dino dar <jugador>  ·  /dino aceptar | rechazar",
                        NamedTextColor.GRAY);
                msg(sender, "/dino pack [reload|info]  ·  Especies: " + String.join(", ", ids()), NamedTextColor.GRAY);
            }
        }
        return true;
    }

    /**
     * Admin: domestica el dino que estas mirando (o el mas cercano a 4 bloques) a tu nombre o al de otro
     * jugador. Sirve tambien para los que solo se crian desde el huevo (T-Rex, Giganotosaurus).
     */
    private boolean tame(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return msg(sender, "Solo jugadores.", NamedTextColor.RED);
        Player owner = player;
        if (args.length > 1) {
            owner = getServer().getPlayerExact(args[1]);
            if (owner == null) return msg(sender, "No encontré a " + args[1] + " conectado.", NamedTextColor.RED);
        }
        Dino d = lookedAt(player);
        if (d == null) return msg(sender, "Mirá a un dino (a 24 bloques o menos) y volvé a usar /dino tame.", NamedTextColor.RED);
        Species s = d.species();
        if (!s.tameable()) return msg(sender, s.name + " es salvaje por naturaleza: no se puede domesticar.", NamedTextColor.RED);
        Wolf wolf = d.wolf();
        if (wolf.isTamed() && owner.getUniqueId().equals(wolf.getOwnerUniqueId())) {
            return msg(sender, "Ese " + s.name + " ya es de " + owner.getName() + ".", NamedTextColor.YELLOW);
        }
        wolf.setOwner(owner);
        wolf.setTarget(null);
        wolf.setAngry(false);
        wolf.setSitting(true);
        d.healFully();
        d.setHunger(1f);
        d.onTamed();
        wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0, wolf.getHeight() + 0.3, 0), 9, 0.6, 0.4, 0.6);
        dex.addTamed(owner, s, d.variant());
        msg(sender, s.name + " " + s.rig.variant(d.variant()).name() + " domesticado para " + owner.getName()
                + ". Quedó sentado: click derecho para que te siga.", NamedTextColor.GREEN);
        if (owner != player) msg(owner, player.getName() + " te regaló un " + s.name + ".", NamedTextColor.GREEN);
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
        if (args.length < 2 || items.def(args[1].toLowerCase()) == null) {
            msg(sender, "Items: " + String.join(", ", items.ids()), NamedTextColor.RED);
            return;
        }
        String id = args[1].toLowerCase();
        int amount = Math.max(1, Math.min(64, args.length > 2 ? (int) parse(args[2], 1) : 1));
        Player target = args.length > 3 ? getServer().getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
        if (target == null) {
            msg(sender, "/dino item <id> [cantidad] [jugador]", NamedTextColor.RED);
            return;
        }
        int max = items.def(id).maxStack();
        for (int left = amount; left > 0; left -= max) {
            for (var rest : target.getInventory().addItem(items.create(id, Math.min(max, left))).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), rest);
            }
        }
        msg(sender, "Diste " + amount + " × " + items.def(id).name() + " a " + target.getName() + ".", NamedTextColor.GREEN);
    }

    // ------------------------------------------------------------ regalar dinos
    /** /dino dar <jugador>: ofrece el dino que estas mirando; el otro tiene 60 segundos para aceptarlo. */
    private boolean give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return msg(sender, "Solo jugadores.", NamedTextColor.RED);
        if (args.length < 2) return msg(sender, "/dino dar <jugador>  (mirando al dino que querés dar)", NamedTextColor.RED);
        Player target = getServer().getPlayerExact(args[1]);
        if (target == null) return msg(sender, "No encontré a " + args[1] + " conectado.", NamedTextColor.RED);
        if (target == player) return msg(sender, "Ya es tuyo.", NamedTextColor.YELLOW);
        Dino d = lookedAt(player);
        if (d == null) return msg(sender, "Mirá a tu dino y volvé a usar /dino dar.", NamedTextColor.RED);
        if (!d.wolf().isTamed() || !player.getUniqueId().equals(d.wolf().getOwnerUniqueId())) {
            return msg(sender, "Ese dino no es tuyo.", NamedTextColor.RED);
        }
        if (d.rider() != null) return msg(sender, "Bajate del dino primero.", NamedTextColor.RED);
        offers.put(target.getUniqueId(), new Offer(d.wolf().getUniqueId(), player.getUniqueId(),
                getServer().getCurrentTick() + OFFER_TICKS));
        Component dino = OrderMenu.label(d).append(Component.text(" (" + d.species().name + " "
                + d.species().rig.variant(d.variant()).name() + ", " + (d.isMale() ? "macho" : "hembra") + ")"));
        msg(sender, "Le ofreciste tu dino a " + target.getName() + ". Tiene 60 segundos para aceptarlo.", NamedTextColor.GREEN);
        target.sendMessage(Component.text(player.getName() + " te quiere dar su ", NamedTextColor.GOLD)
                .append(dino.colorIfAbsent(NamedTextColor.WHITE)).append(Component.text(". ", NamedTextColor.GOLD))
                .append(Component.text("[Aceptar]", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/dino aceptar")))
                .append(Component.text(" "))
                .append(Component.text("[Rechazar]", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/dino rechazar"))));
        return true;
    }

    private boolean accept(CommandSender sender) {
        if (!(sender instanceof Player player)) return msg(sender, "Solo jugadores.", NamedTextColor.RED);
        Offer offer = offers.remove(player.getUniqueId());
        if (offer == null || offer.expires() < getServer().getCurrentTick()) {
            return msg(sender, "No tenés ningún dino ofrecido (o ya pasó el minuto).", NamedTextColor.RED);
        }
        Entity e = Bukkit.getEntity(offer.wolf());
        Dino d = e == null ? null : dinos.get(e);
        if (d == null || d.isDying() || !offer.from().equals(d.wolf().getOwnerUniqueId())) {
            return msg(sender, "Ese dino ya no está disponible.", NamedTextColor.RED);
        }
        if (d.rider() != null) d.wolf().removePassenger(d.rider());
        d.wolf().setOwner(player);
        d.wolf().setTarget(null);
        d.setOrder(Dino.Order.STAY, player);
        d.refreshPlate();
        dex.addTamed(player, d.species(), d.variant());
        msg(sender, "¡Ahora el dino es tuyo! Quedó sentado: click derecho para que te siga.", NamedTextColor.GREEN);
        Player from = getServer().getPlayer(offer.from());
        if (from != null) msg(from, player.getName() + " aceptó tu dino.", NamedTextColor.GREEN);
        return true;
    }

    private boolean reject(CommandSender sender) {
        if (!(sender instanceof Player player)) return msg(sender, "Solo jugadores.", NamedTextColor.RED);
        Offer offer = offers.remove(player.getUniqueId());
        if (offer == null) return msg(sender, "No tenés ningún dino ofrecido.", NamedTextColor.GRAY);
        msg(sender, "Rechazaste el dino.", NamedTextColor.YELLOW);
        Player from = getServer().getPlayer(offer.from());
        if (from != null) msg(from, player.getName() + " rechazó tu dino.", NamedTextColor.YELLOW);
        return true;
    }

    private boolean packCommand(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "";
        if (!action.isEmpty() && !sender.hasPermission(ADMIN)) {
            return msg(sender, "No tenés permiso.", NamedTextColor.RED);
        }
        switch (action) {
            case "reload" -> pack.reload(sender);
            case "info" -> msg(sender, "Resource pack: " + pack.status(), NamedTextColor.AQUA);
            default -> {
                if (!(sender instanceof Player player)) return msg(sender, "Solo jugadores.", NamedTextColor.RED);
                if (pack.send(player)) msg(sender, "Te mandé el pack de dinosaurios.", NamedTextColor.GREEN);
                else msg(sender, "El pack no está configurado todavía.", NamedTextColor.RED);
            }
        }
        return true;
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg(sender, "Solo jugadores.", NamedTextColor.RED);
            return;
        }
        Species species = args.length > 1 ? Species.byId(args[1]) : null;
        if (species == null) {
            msg(sender, "Especies: " + String.join(", ", ids()), NamedTextColor.RED);
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
                msg(sender, "Color inválido. Colores de " + species.name + ": random, " + String.join(", ", colors(species)),
                        NamedTextColor.RED);
                return;
            }
            variant = color;
        }
        Boolean male = null;
        if (sex != null && !sex.equals("random")) {
            if (!SEXES.contains(sex)) {
                msg(sender, "Sexo inválido. Opciones: " + String.join(", ", SEXES), NamedTextColor.RED);
                return;
            }
            male = sex.equals("macho");
        }
        float growth = 1f;
        if (ageArg != null) {
            if (!ages(species).contains(ageArg)) {
                msg(sender, "Edad inválida. Opciones: " + String.join(", ", ages(species))
                        + (species.hasBabies() ? "" : " (" + species.name + " no tiene crías)"), NamedTextColor.RED);
                return;
            }
            growth = ageArg.equals("bebe") ? 0f : 1f;
        }
        int count = species.group;
        if (amount != null) {
            if (!amount.matches("\\d+")) {
                msg(sender, "La cantidad tiene que ser un número.", NamedTextColor.RED);
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
        if (last != null) {
            String desc = count > 1 ? count + " " + species.name
                    : species.name + " " + species.rig.variant(last.variant()).name() + " ("
                    + (last.isMale() ? "macho" : "hembra") + (growth < 1 ? ", cría" : "") + ")";
            msg(sender, desc + " spawneado.", NamedTextColor.GREEN);
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

    private static List<String> eggSpecies() {
        return Species.ALL.stream().filter(s -> s.rig != null && s.rig.hasEgg()).map(s -> s.id).toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            return filter(admin ? List.of("spawn", "egg", "grow", "tame", "item", "dar", "aceptar", "rechazar", "remove", "count",
                    "comedero", "dinopedia", "hambre", "pack") : List.of("dar", "aceptar", "rechazar", "pack"), args[0]);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2 && (sub.equals("dar") || sub.equals("regalar"))) {
            return filter(getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && sub.equals("pack") && admin) return filter(List.of("reload", "info"), args[1]);
        if (!admin) return List.of();
        if (sub.equals("item") || sub.equals("items")) {
            if (args.length == 2) return filter(items.ids(), args[1]);
            if (args.length == 3) return filter(List.of("1", "16", "64"), args[2]);
            if (args.length == 4) return filter(getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        }
        if (args.length == 2 && sub.equals("spawn")) return filter(ids(), args[1]);
        if (args.length == 2 && (sub.equals("egg") || sub.equals("huevo"))) return filter(eggSpecies(), args[1]);
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

    private static boolean msg(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text(text, color));
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

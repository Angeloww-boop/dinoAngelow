package com.dinoearth;

import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoListener;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.egg.EggManager;
import com.dinoearth.model.DinoRig;
import com.dinoearth.pack.PackManager;
import com.dinoearth.species.Species;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class DinoEarthPlugin extends JavaPlugin {

    private static final String ADMIN = "dinoearth.admin";

    private DinoManager dinos;
    private EggManager eggs;
    private PackManager pack;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
        dinos = new DinoManager(keys, settings);
        eggs = new EggManager(keys, dinos, settings.hatchTicks);
        getServer().getPluginManager().registerEvents(new DinoListener(this, dinos, eggs, keys), this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            dinos.tick();
            eggs.tick();
        }, 1L, 1L);
        // los mundos ya estan cargados si es un /reload
        getServer().getScheduler().runTask(this, () -> {
            dinos.attachLoaded();
            eggs.attachLoaded();
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
        if (pack != null) pack.stop();
    }

    // ------------------------------------------------------------ comandos
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        if (sub.equals("pack")) return packCommand(sender, args);
        if (!sender.hasPermission(ADMIN)) {
            return msg(sender, "Podés usar /dino pack para volver a cargar el pack de dinosaurios.", NamedTextColor.GRAY);
        }
        switch (sub) {
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
                msg(sender, "/dino pack [reload|info]  ·  Especies: " + String.join(", ", ids()), NamedTextColor.GRAY);
            }
        }
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
            return filter(admin ? List.of("spawn", "egg", "grow", "remove", "count", "pack") : List.of("pack"), args[0]);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2 && sub.equals("pack") && admin) return filter(List.of("reload", "info"), args[1]);
        if (!admin) return List.of();
        if (args.length == 2 && sub.equals("spawn")) return filter(ids(), args[1]);
        if (args.length == 2 && (sub.equals("egg") || sub.equals("huevo"))) return filter(eggSpecies(), args[1]);
        if (args.length == 3 && sub.equals("remove")) return filter(ids(), args[2]);
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
        return options.stream().filter(o -> o.startsWith(prefix.toLowerCase())).toList();
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

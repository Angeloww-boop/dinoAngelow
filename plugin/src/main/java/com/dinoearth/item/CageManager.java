package com.dinoearth.item;

import com.dinoearth.text.Fmt;
import com.dinoearth.text.Lang;
import com.dinoearth.Keys;
import com.dinoearth.dino.Dino;
import com.dinoearth.dino.DinoManager;
import com.dinoearth.feeder.Protection;
import com.dinoearth.species.Species;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Jaula: guarda un dino domesticado adentro del item (color, sexo, edad, montura, hambre, vida,
 * nombre y modo de combate) y lo saca donde la uses. Quien lo suelta queda como dueño: sirve para
 * llevarlo lejos, resguardarlo o venderlo.
 */
public final class CageManager {

    private final DinoItems items;
    private final DinoManager dinos;
    private final Keys keys;

    public CageManager(DinoItems items, DinoManager dinos, Keys keys) {
        this.items = items;
        this.dinos = dinos;
        this.keys = keys;
    }

    public boolean isFull(ItemStack item) {
        return items.is(item, DinoItems.Kind.CAGE) && item.getItemMeta().getPersistentDataContainer().has(keys.cage);
    }

    /** Mete al dino en la jaula que el jugador tiene en la mano. */
    public void capture(Player player, Dino d, ItemStack hand) {
        if (isFull(hand)) {
            Lang.get().action(player, "jaula-llena");
            return;
        }
        if (d.rider() != null || d.isDying()) {
            Lang.get().action(player, "jaula-ahora-no");
            return;
        }
        Wolf wolf = d.wolf();
        Species s = d.species();
        ItemStack full = items.create(DinoItems.CAGE, 1);
        ItemMeta meta = full.getItemMeta();
        DinoItems.Def def = items.def(DinoItems.CAGE);
        if (def.modelFull() != null) meta.setItemModel(def.modelFull());
        Component who = d.name() != null ? d.name() : Component.text(s.name);
        var max = wolf.getAttribute(Attribute.MAX_HEALTH);
        double ratio = max != null ? wolf.getHealth() / max.getValue() : 1;
        Lang lang = Lang.get();
        String age = d.isAdult() ? lang.plain(d.isMale() ? "edad.Adulto" : "edad.Adulta")
                : lang.plain("edad.cria-porcentaje", "porcentaje", Math.round(d.growth() * 100));
        lang.itemTexts(meta, "jaula-llena", "dino", who, "especie", s.name, "color", s.colorName(d.variant()),
                "sexo", Fmt.sex(d.isMale()), "edad", age, "vida", Math.round(ratio * 100),
                "montura", d.isSaddled() ? List.of(lang.raw("items.yml", "jaula-con-montura")) : List.of());
        meta.setMaxStackSize(1);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.cage, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keys.type, PersistentDataType.STRING, s.id);
        pdc.set(keys.variant, PersistentDataType.STRING, d.variant());
        pdc.set(keys.sex, PersistentDataType.STRING, d.isMale() ? "male" : "female");
        pdc.set(keys.growth, PersistentDataType.FLOAT, d.growth());
        pdc.set(keys.saddled, PersistentDataType.BYTE, (byte) (d.isSaddled() ? 1 : 0));
        pdc.set(keys.hunger, PersistentDataType.FLOAT, d.hunger());
        pdc.set(keys.health, PersistentDataType.DOUBLE, ratio);
        pdc.set(keys.combat, PersistentDataType.STRING, d.combat().name());
        if (d.name() != null) pdc.set(keys.name, PersistentDataType.STRING, GsonComponentSerializer.gson().serialize(d.name()));
        full.setItemMeta(meta);

        Location at = wolf.getLocation().add(0, wolf.getHeight() / 2, 0);
        wolf.getWorld().spawnParticle(Particle.CLOUD, at, 14, wolf.getWidth() / 2, wolf.getHeight() / 3, wolf.getWidth() / 2, 0.02);
        wolf.getWorld().playSound(at, "minecraft:block.chain.place", SoundCategory.PLAYERS, 1f, 0.7f);
        wolf.remove();   // el plugin saca el modelo al ver que el lobo se fue

        if (hand.getAmount() > 1) {
            hand.subtract();
            for (ItemStack left : player.getInventory().addItem(full).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        } else {
            player.getInventory().setItemInMainHand(full);
        }
        Lang.get().action(player, "jaula-guardado", "dino", who);
    }

    /** Suelta al dino de la jaula sobre el bloque clickeado. */
    public void release(Player player, ItemStack hand, Block clicked, BlockFace face) {
        Block target = clicked.getRelative(face);
        if (!target.isPassable() || target.isLiquid() || !target.getRelative(BlockFace.DOWN).getType().isSolid()) {
            Lang.get().action(player, "jaula-piso");
            return;
        }
        if (!Protection.canBuild(player, target)) {
            Lang.get().action(player, "jaula-aca");
            return;
        }
        PersistentDataContainer pdc = hand.getItemMeta().getPersistentDataContainer();
        Species s = Species.byId(pdc.get(keys.type, PersistentDataType.STRING));
        if (s == null || s.rig == null) {
            Lang.get().action(player, "jaula-especie");
            return;
        }
        if (!dinos.canOwnMore(player.getUniqueId())) {
            dinos.limitMessage(player);
            return;
        }
        Location at = target.getLocation().add(0.5, 0, 0.5);
        at.setYaw(player.getYaw() + 180);
        Dino d = dinos.spawn(s, at, pdc.get(keys.variant, PersistentDataType.STRING),
                "male".equals(pdc.get(keys.sex, PersistentDataType.STRING)),
                pdc.getOrDefault(keys.growth, PersistentDataType.FLOAT, 1f), player);
        if (d == null) return;
        if (pdc.getOrDefault(keys.saddled, PersistentDataType.BYTE, (byte) 0) == 1 && s.rideable && d.isAdult()) {
            d.setSaddled(true);
        }
        d.setHunger(pdc.getOrDefault(keys.hunger, PersistentDataType.FLOAT, 1f));
        var max = d.wolf().getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            double ratio = pdc.getOrDefault(keys.health, PersistentDataType.DOUBLE, 1.0);
            d.wolf().setHealth(Math.max(1, Math.min(max.getValue(), max.getValue() * ratio)));
        }
        try {
            d.setCombat(Dino.Combat.valueOf(pdc.getOrDefault(keys.combat, PersistentDataType.STRING, "DEFENSIVE")));
        } catch (IllegalArgumentException ignored) {
            // modo desconocido: queda defensivo
        }
        String name = pdc.get(keys.name, PersistentDataType.STRING);
        if (name != null) d.setName(GsonComponentSerializer.gson().deserialize(name));
        d.setOrder(Dino.Order.STAY, player);

        at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.6, 0), 14, 0.5, 0.4, 0.5, 0.02);
        at.getWorld().playSound(at, "minecraft:block.chain.break", SoundCategory.PLAYERS, 1f, 0.8f);
        player.getInventory().setItemInMainHand(items.create(DinoItems.CAGE, 1));
        Lang.get().action(player, "jaula-soltado", "dino", d.name() != null ? d.name() : Component.text(s.name));
    }
}

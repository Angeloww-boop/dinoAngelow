package com.dinoearth.model;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Consumer;

/**
 * Modelo visual: una item_display por hueso. Las displays van montadas como
 * pasajeros de la entidad base, asi el cliente las mueve exactamente junto con
 * ella (importante cuando alguien lo monta). La rotacion del cuerpo, la escala
 * y la pose de cada hueso van en la matriz de transformacion, y el cliente
 * interpola el movimiento.
 */
public final class DinoModel {

    /** Pose de un hueso: rotacion en grados (orden Y, X, Z), desplazamiento vertical en pixeles y escala. */
    public static final class BonePose {
        public float rx, ry, rz, dy, scale = 1;

        void reset() {
            rx = ry = rz = dy = 0;
            scale = 1;
        }
    }

    private static final float PX = 1f / 16f;

    private final List<DinoRig.BoneDef> bones;
    private final ItemDisplay[] parts;
    private final BonePose[] pose;
    private final Matrix4f[] world;
    private final Matrix4f extra = new Matrix4f();
    private final Matrix4f root = new Matrix4f();
    private final Vector offset = new Vector();
    private final Entity vehicle;

    /**
     * @param vehicle     entidad a la que se montan las partes, o null para un modelo fijo.
     * @param mountOffset estimacion de cuanto mas arriba quedan las partes montadas, hasta poder medirlo.
     * @param tagger  marca cada display (PDC) para reconocerlas despues.
     */
    public DinoModel(List<DinoRig.BoneDef> bones, NamespacedKey[] items, Location at, Entity vehicle,
                     Vector mountOffset, Consumer<ItemDisplay> tagger) {
        this.bones = bones;
        this.vehicle = vehicle;
        this.offset.copy(mountOffset);
        int n = bones.size();
        this.parts = new ItemDisplay[n];
        this.pose = new BonePose[n];
        this.world = new Matrix4f[n];
        Location spawn = at.clone();
        spawn.setYaw(0);
        spawn.setPitch(0);
        for (DinoRig.BoneDef bone : bones) {
            int i = bone.index();
            ItemStack item = itemFor(items[i]);
            parts[i] = at.getWorld().spawn(spawn, ItemDisplay.class, d -> {
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setPersistent(false);
                d.setInterpolationDuration(2);
                // caja de culling: sin esto las partes desaparecen al mirar de costado
                d.setDisplayWidth(4f);
                d.setDisplayHeight(3f);
                if (i == 0) {
                    d.setShadowRadius(0.9f);
                    d.setShadowStrength(0.6f);
                }
                tagger.accept(d);
            });
            if (vehicle != null) vehicle.addPassenger(parts[i]);
            pose[i] = new BonePose();
            world[i] = new Matrix4f();
        }
    }

    private static ItemStack itemFor(NamespacedKey key) {
        if (key == null) return ItemStack.empty();
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(key);
        item.setItemMeta(meta);
        return item;
    }

    public BonePose pose(int bone) {
        return pose[bone];
    }

    public void resetPose() {
        for (BonePose p : pose) p.reset();
        extra.identity();
    }

    /** Transformacion extra aplicada a todo el modelo (por ejemplo, caer de costado al morir). */
    public Matrix4f extra() {
        return extra;
    }

    public void setItem(int bone, NamespacedKey key) {
        parts[bone].setItemStack(itemFor(key));
    }

    /** Jugadores que reciben esta entidad (los que la tienen cerca); vacio = nadie la ve. */
    public java.util.Set<org.bukkit.entity.Player> viewers() {
        return parts[0].getTrackedBy();
    }

    /** Hasta donde la dibuja el cliente (1 = 64 bloques, multiplicado por la distancia de entidades del jugador). */
    public void setViewRange(float range) {
        for (ItemDisplay d : parts) d.setViewRange(range);
    }

    public boolean isValid() {
        for (ItemDisplay d : parts) {
            if (d == null || !d.isValid()) return false;
        }
        return true;
    }

    /** Vuelve a montar las partes si algo las bajo (por ejemplo un teleport). */
    public void remount() {
        if (vehicle == null || !vehicle.isValid()) return;
        for (ItemDisplay d : parts) {
            if (d.getVehicle() != vehicle) {
                d.teleport(vehicle.getLocation());
                vehicle.addPassenger(d);
            }
        }
    }

    /** Las partes dejan de seguir a la entidad (al morir), quedan donde estan. */
    public void dismount() {
        for (ItemDisplay d : parts) {
            if (d.getVehicle() != null) d.leaveVehicle();
        }
    }

    /**
     * Calcula la jerarquia de huesos y manda la pose al cliente.
     *
     * @param base posicion de la entidad base; las partes estan montadas algo mas arriba
     *             (punto de pasajero), asi que compensamos esa diferencia.
     */
    public void applyPose(Location base, float yaw, float scale, int interpolation) {
        if (vehicle != null && parts[0].getVehicle() == vehicle) {
            Location at = parts[0].getLocation();
            double dx = at.getX() - base.getX(), dy = at.getY() - base.getY(), dz = at.getZ() - base.getZ();
            // recien montadas todavia estan en el punto de spawn: seguimos con la estimacion
            if (dx * dx + dy * dy + dz * dz > 1e-4) offset.setX(dx).setY(dy).setZ(dz);
        }
        root.identity()
                .translate((float) -offset.getX(), (float) -offset.getY(), (float) -offset.getZ())
                .rotateY((float) Math.toRadians(-yaw))
                .scale(scale)
                .mul(extra);

        for (DinoRig.BoneDef bone : bones) {
            int i = bone.index();
            Matrix4f m = world[i];
            if (bone.parent() < 0) {
                m.set(root).translate(bone.jx() * PX, bone.jy() * PX, bone.jz() * PX);
            } else {
                DinoRig.BoneDef parent = bones.get(bone.parent());
                m.set(world[bone.parent()]).translate(
                        (bone.jx() - parent.jx()) * PX,
                        (bone.jy() - parent.jy()) * PX,
                        (bone.jz() - parent.jz()) * PX);
            }
            BonePose p = pose[i];
            m.translate(0, p.dy * PX, 0)
                    .rotateY((float) Math.toRadians(p.ry))
                    .rotateX((float) Math.toRadians(p.rx))
                    .rotateZ((float) Math.toRadians(p.rz));
            if (p.scale != 1) m.scale(p.scale);

            ItemDisplay d = parts[i];
            d.setTransformationMatrix(m);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interpolation);
        }
    }

    public void setGlow(Color color) {
        for (ItemDisplay d : parts) {
            if (color == null) {
                d.setGlowing(false);
            } else {
                d.setGlowColorOverride(color);
                d.setGlowing(true);
            }
        }
    }

    public void remove() {
        for (ItemDisplay d : parts) {
            if (d != null) d.remove();
        }
    }
}

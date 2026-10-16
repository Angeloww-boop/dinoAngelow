package com.dinoearth.model;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Consumer;

/**
 * Modelo visual: una item_display por hueso. Las displays van montadas como
 * pasajeros de la entidad base, asi el cliente las mueve exactamente junto con
 * ella (importante cuando alguien lo monta). La rotacion del cuerpo, la escala
 * y la pose de cada hueso van en la matriz de transformacion, y el cliente
 * interpola el movimiento.
 *
 * <p>Ojo: la matriz NO se manda con setTransformationMatrix. Minecraft la descompone con SVD en
 * rotacion izquierda + escala + rotacion derecha, y para una rotacion con escala pareja esa
 * descomposicion no es unica: un cambio minimo (respirar) reparte el giro entre las dos rotaciones de
 * otra forma y el cliente, que interpola cada una por separado, dibuja la pieza temblando. Por eso se
 * arma la transformacion a mano: traslacion + una sola rotacion + escala.
 */
public final class DinoModel {

    /** Pose de un hueso: rotacion en grados (orden Y, X, Z), desplazamiento vertical en pixeles y escala. */
    public static final class BonePose {
        public float rx, ry, rz, dy, scale = 1;
        /** Ocultar la parte (por ejemplo, plegada del todo adentro de otra: si no, sus caras titilan). */
        public boolean hidden;

        void reset() {
            rx = ry = rz = dy = 0;
            scale = 1;
            hidden = false;
        }
    }

    private static final float PX = 1f / 16f;

    private final List<DinoRig.BoneDef> bones;
    private final ItemDisplay[] parts;
    private final ItemStack[] shown;      // lo que muestra cada parte cuando no esta oculta
    private final boolean[] hidden;
    private final Quaternionf[] lastRot;
    private final Transformation[] sent;     // lo ultimo que se mando de cada hueso  // para mandar siempre el cuaternion del mismo "lado" que el anterior
    private final BonePose[] pose;
    private final Matrix4f[] world;
    private final Matrix4f extra = new Matrix4f();
    private final Matrix4f root = new Matrix4f();
    private final Vector offset = new Vector();
    private final Entity vehicle;

    /**
     * @param vehicle     entidad a la que se montan las partes, o null para un modelo fijo.
     * @param mountOffset estimacion de cuanto mas arriba quedan las partes montadas, hasta poder medirlo.
     * @param cullWidth   ancho/alto (bloques) de la caja con la que el cliente decide si dibujar cada parte.
     * @param tagger  marca cada display (PDC) para reconocerlas despues.
     */
    public DinoModel(List<DinoRig.BoneDef> bones, NamespacedKey[] items, Location at, Entity vehicle,
                     Vector mountOffset, float cullWidth, float cullHeight, Consumer<ItemDisplay> tagger) {
        this.bones = bones;
        this.vehicle = vehicle;
        this.offset.copy(mountOffset);
        int n = bones.size();
        this.parts = new ItemDisplay[n];
        this.shown = new ItemStack[n];
        this.hidden = new boolean[n];
        this.lastRot = new Quaternionf[n];
        this.sent = new Transformation[n];
        this.pose = new BonePose[n];
        this.world = new Matrix4f[n];
        Location spawn = at.clone();
        spawn.setYaw(0);
        spawn.setPitch(0);
        for (DinoRig.BoneDef bone : bones) {
            int i = bone.index();
            ItemStack item = itemFor(items[i]);
            shown[i] = item;
            parts[i] = at.getWorld().spawn(spawn, ItemDisplay.class, d -> {
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setPersistent(false);
                d.setInterpolationDuration(2);
                // caja de culling: sin esto las partes desaparecen al mirar de costado
                d.setDisplayWidth(cullWidth);
                d.setDisplayHeight(cullHeight);
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
        shown[bone] = itemFor(key);
        if (!hidden[bone]) parts[bone].setItemStack(shown[bone]);
    }

    private void setHidden(int bone, boolean value) {
        if (hidden[bone] == value) return;
        hidden[bone] = value;
        parts[bone].setItemStack(value ? ItemStack.empty() : shown[bone]);
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

    /**
     * Calcula la jerarquia de huesos y manda la pose al cliente.
     *
     * @param base posicion de la entidad base; las partes estan montadas algo mas arriba
     *             (punto de pasajero), asi que compensamos esa diferencia.
     */
    public void applyPose(Location base, float yaw, float scale, int interpolation) {
        int watching = parts[0].getTrackedBy().size();
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

            setHidden(i, p.hidden);
            Transformation t = decompose(i, m);
            if (sent[i] != null && same(sent[i], t)) {
                skippedUpdates++;
                continue;      // el hueso casi no se movio: no se manda nada
            }
            sent[i] = t;
            ItemDisplay d = parts[i];
            d.setTransformation(t);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interpolation);
            boneUpdates++;
            viewerUpdates += watching;
        }
    }

    /** Diferencia que no se nota: menos de 0,03 px de un bloque, 0,3 grados o 0,1 % de escala. */
    private static boolean same(Transformation a, Transformation b) {
        if (a.getTranslation().distanceSquared(b.getTranslation()) > 0.002f * 0.002f) return false;
        if (a.getScale().distanceSquared(b.getScale()) > 0.001f * 0.001f) return false;
        return Math.abs(a.getLeftRotation().dot(b.getLeftRotation())) > 0.999996f;
    }

    // ------------------------------------------------------------ cuentas para /dino stats
    /** Huesos mandados, huesos mandados x jugadores que los reciben, y huesos que no hizo falta mandar. */
    public static long boneUpdates, viewerUpdates, skippedUpdates;

    public int size() {
        return parts.length;
    }

    /**
     * Traslacion + rotacion + escala de una matriz que es solo eso (rotaciones y escala pareja):
     * siempre da la misma respuesta para la misma pose, asi la interpolacion del cliente es suave.
     */
    private Transformation decompose(int i, Matrix4f m) {
        Vector3f translation = m.getTranslation(new Vector3f());
        Vector3f scale = m.getScale(new Vector3f());
        Quaternionf rot = m.getNormalizedRotation(new Quaternionf());
        Quaternionf prev = lastRot[i];
        if (prev != null && prev.dot(rot) < 0) rot.mul(-1f);   // q y -q son el mismo giro: elegir el cercano
        lastRot[i] = new Quaternionf(rot);
        return new Transformation(translation, rot, scale, new Quaternionf());
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

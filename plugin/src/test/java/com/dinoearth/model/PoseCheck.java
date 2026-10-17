package com.dinoearth.model;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Prueba sin servidor ni cliente la descomposicion de las poses: arma la matriz como DinoModel (raiz con escala y giros),
 * la descompone, y la vuelve a armar e interpola como el cliente de Minecraft (Transformation: traslacion, rotate(izq),
 * escala; interpolacion: lerp, slerp de JOML, lerp). Mismo JOML que el cliente (1.10.8).
 * Uso: java -cp ... com.dinoearth.model.PoseCheck
 */
public final class PoseCheck {

    private static int fails;

    public static void main(String[] args) {
        // escala de la raiz: dimorphodon (0.465), cria, adulto con genes chicos/grandes, macho grande
        float[] scales = {0.465f, 0.6f, 0.95f, 1f, 1.08f, 2.5f};
        float[][] rots = {{0, 0, 0}, {-10, 0, -68}, {35, 145, 30}, {0, 179, 0}, {-80, 20, 5}};
        for (float s : scales) {
            for (float[] r : rots) {
                for (float yaw : new float[]{0, 37, 181}) {
                    Matrix4f m = new Matrix4f().translate(0.2f, -0.6f, 0.1f).rotateY(rad(-yaw)).scale(s)
                            .translate(0.15f, 0.9f, 0.16f)
                            .rotateY(rad(r[1])).rotateX(rad(r[0])).rotateZ(rad(r[2]));
                    String name = "escala " + s + " giro " + r[0] + "/" + r[1] + "/" + r[2] + " yaw " + yaw;
                    Quaternionf q = DinoModel.rotationOf(m);
                    Vector3f t = m.getTranslation(new Vector3f());
                    Vector3f sc = m.getScale(new Vector3f());
                    check(name + ": cuaternion unitario", Math.abs(q.lengthSquared() - 1) < 1e-4f);
                    check(name + ": la matriz armada por el cliente es la misma", diff(client(t, q, sc), m) < 1e-4f);
                    // mandar la misma pose dos veces: a mitad de la interpolacion tiene que verse igual
                    float worst = 0;
                    for (float a = 0; a <= 1.0001f; a += 0.05f) worst = Math.max(worst, diff(lerp(t, q, sc, t, q, sc, a), m));
                    check(name + ": pose repetida no se mueve al interpolar (" + worst + ")", worst < 1e-4f);
                }
            }
        }
        // lo que pasaba antes (getNormalizedRotation con escala): cuanto se inflaba el dimorphodon al reenviar la pose
        Matrix4f m = new Matrix4f().scale(0.465f);
        Quaternionf old = m.getNormalizedRotation(new Quaternionf());
        Vector3f t = new Vector3f(), sc = m.getScale(new Vector3f());
        float base = client(t, old, sc).getScale(new Vector3f()).x;
        float mid = lerp(t, old, sc, t, old, sc, 0.5f).getScale(new Vector3f()).x;
        System.out.printf("antes: largo del cuaternion %.3f, tamaño dibujado %.3f (deberia ser 0.465), a mitad de interpolar %.3f (+%.0f %%)%n",
                (float) Math.sqrt(old.lengthSquared()), base, mid, (mid / base - 1) * 100);
        Quaternionf now = DinoModel.rotationOf(m);
        float nowMid = lerp(t, now, sc, t, now, sc, 0.5f).getScale(new Vector3f()).x;
        System.out.printf("ahora: largo del cuaternion %.3f, tamaño dibujado %.3f, a mitad de interpolar %.3f%n",
                (float) Math.sqrt(now.lengthSquared()), client(t, now, sc).getScale(new Vector3f()).x, nowMid);
        System.out.println(fails == 0 ? "todo ok" : fails + " fallas");
        if (fails > 0) System.exit(1);
    }

    /** Lo que hace el cliente con lo que le mandamos (Transformation.getMatrix). */
    private static Matrix4f client(Vector3f t, Quaternionf q, Vector3f s) {
        return new Matrix4f().translation(t).rotate(q).scale(s).rotate(new Quaternionf());
    }

    /** Transformation.slerp del cliente: lerp de traslacion y escala, slerp de JOML del giro. */
    private static Matrix4f lerp(Vector3f t0, Quaternionf q0, Vector3f s0, Vector3f t1, Quaternionf q1, Vector3f s1, float a) {
        return client(new Vector3f(t0).lerp(t1, a), new Quaternionf(q0).slerp(q1, a), new Vector3f(s0).lerp(s1, a));
    }

    private static float diff(Matrix4f a, Matrix4f b) {
        float worst = 0;
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) worst = Math.max(worst, Math.abs(a.get(c, r) - b.get(c, r)));
        }
        return worst;
    }

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            fails++;
            System.out.println("FALLA: " + name);
        }
    }
}

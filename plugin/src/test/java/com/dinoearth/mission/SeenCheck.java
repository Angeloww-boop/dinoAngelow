package com.dinoearth.mission;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Prueba sin servidor que los dinos avistados para las misiones se guarden y se lean bien (asi el mismo dino no cuenta
 * otra vez despues de salir y entrar, o de pasar por el lobby).
 * Uso: java -cp ... com.dinoearth.mission.SeenCheck
 */
public final class SeenCheck {

    private static int fails;

    public static void main(String[] args) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        String raw = MissionManager.writeSeen("2026-10-16", List.of(a, b));

        Set<UUID> back = new HashSet<>();
        MissionManager.readSeen(raw, "2026-10-16", back);
        check("vuelven los dos del mismo dia", back.equals(Set.of(a, b)));
        check("el mismo dino ya no suma", !back.add(a));

        Set<UUID> otherDay = new HashSet<>();
        MissionManager.readSeen(raw, "2026-10-17", otherDay);
        check("otro dia: arranca de cero", otherDay.isEmpty());

        Set<UUID> none = new HashSet<>();
        MissionManager.readSeen(null, "2026-10-16", none);
        MissionManager.readSeen("2026-10-16|", "2026-10-16", none);
        check("sin datos o vacio: nada", none.isEmpty());

        Set<UUID> broken = new HashSet<>();
        MissionManager.readSeen("2026-10-16|basura," + a, "2026-10-16", broken);
        check("dato roto: se salta y lee el resto", broken.equals(Set.of(a)));

        Set<UUID> many = new HashSet<>();
        for (int i = 0; i < 200; i++) many.add(UUID.randomUUID());
        Set<UUID> capped = new HashSet<>();
        MissionManager.readSeen(MissionManager.writeSeen("2026-10-16", many), "2026-10-16", capped);
        check("tope de " + MissionManager.MAX_SEEN, capped.size() == MissionManager.MAX_SEEN && many.containsAll(capped));

        System.out.println(fails == 0 ? "todo ok" : fails + " fallas");
        if (fails > 0) System.exit(1);
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            fails++;
            System.out.println("FALLA: " + name);
        }
    }
}

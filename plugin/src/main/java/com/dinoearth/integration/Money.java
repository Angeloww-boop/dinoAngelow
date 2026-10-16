package com.dinoearth.integration;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Plata con Vault, sin depender de su API para compilar: se busca por reflexion el servicio
 * net.milkbowl.vault.economy.Economy. Si no hay Vault (o ningun plugin de economia), no hace nada.
 */
public final class Money {

    private static Object economy;
    private static Method deposit;
    private static Method format;

    private Money() {
    }

    public static void setup(Logger log) {
        economy = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        try {
            Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(type);
            if (rsp == null) {
                log.info("Vault esta, pero no hay ningun plugin de economia: las misiones no dan plata.");
                return;
            }
            economy = rsp.getProvider();
            deposit = type.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            format = type.getMethod("format", double.class);
            log.info("Vault conectado: las misiones y sellos pueden dar plata.");
        } catch (ReflectiveOperationException | LinkageError e) {
            economy = null;
            log.warning("No se pudo conectar con Vault: " + e);
        }
    }

    public static boolean enabled() {
        return economy != null;
    }

    /** Le da plata al jugador. Devuelve el monto escrito como lo escribe la economia ("$1,000"), o null si no se pudo. */
    public static String give(OfflinePlayer p, double amount) {
        if (economy == null || amount <= 0) return null;
        try {
            deposit.invoke(economy, p, amount);
            return String.valueOf(format.invoke(economy, amount));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}

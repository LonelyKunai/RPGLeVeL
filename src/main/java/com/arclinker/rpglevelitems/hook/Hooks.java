package com.arclinker.rpglevelitems.hook;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;

/** Registers optional integrations only when the other plugin is present (keeps class loading safe). */
public final class Hooks {
    private Hooks() {}

    public static void registerAll(RPGLevelTier plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new PapiHook(plugin).register();
                plugin.getLogger().info("PlaceholderAPI expansion registered (%rlv_...%).");
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not register PlaceholderAPI expansion: " + t);
            }
        }
    }
}

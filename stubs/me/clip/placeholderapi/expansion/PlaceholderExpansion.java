package me.clip.placeholderapi.expansion;

import org.bukkit.OfflinePlayer;

/**
 * Compile-time stub of PlaceholderAPI's expansion class (the real one is provided by the PlaceholderAPI plugin
 * at runtime). It is NOT packaged in the plugin jar.
 */
public abstract class PlaceholderExpansion {
    public abstract String getIdentifier();

    public abstract String getAuthor();

    public abstract String getVersion();

    public boolean persist() {
        return false;
    }

    public boolean canRegister() {
        return true;
    }

    public String onRequest(OfflinePlayer player, String params) {
        return null;
    }

    public boolean register() {
        return true;
    }
}

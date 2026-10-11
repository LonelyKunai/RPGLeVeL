package com.arclinker.rpglevelitems;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Map;

public final class Util {
    private static final Map<String, Material> ICON_ALIASES = Map.of(
            "SKULL", Material.SKELETON_SKULL,
            "LEAVES", Material.OAK_LEAVES,
            "MUSIC_DISC", Material.MUSIC_DISC_CAT,
            "WOOD_SWORD", Material.WOODEN_SWORD);

    private Util() {}

    /** Sends a legacy-colored (§) action bar message. */
    public static void actionBar(org.bukkit.entity.Player p, String message) {
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
    }

    /** Resolves a material name (accepts legacy aliases); falls back to {@code def}. */
    public static Material icon(String name, Material def) {
        if (name == null || name.isBlank()) return def;
        Material alias = ICON_ALIASES.get(name.trim().toUpperCase(Locale.ROOT));
        if (alias != null) return alias;
        Material m = Material.matchMaterial(name.trim());
        return m != null && !m.isAir() && m.isItem() ? m : def;
    }
}

package com.arclinker.rpglevelitems.rank;

import org.bukkit.Material;

import java.util.Locale;

/** Sections that have their own ranking between players. */
public enum RankCategory {
    CLASS("class", "Clase", Material.IRON_SWORD, "§c"),
    SUBCLASS("subclass", "Subclase", Material.NETHER_STAR, "§d"),
    JOB("job", "Trabajo", Material.IRON_PICKAXE, "§e"),
    QUEST("quest", "Misiones", Material.BOOK, "§a"),
    PVP("pvp", "PvP", Material.DIAMOND_SWORD, "§9");

    public final String id;
    public final String label;
    public final Material icon;
    public final String color;

    RankCategory(String id, String label, Material icon, String color) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.color = color;
    }

    public String coloredLabel() {
        return color + label;
    }

    public static RankCategory parse(String s) {
        if (s == null) return null;
        switch (s.toLowerCase(Locale.ROOT)) {
            case "class": case "clase": case "clases": return CLASS;
            case "subclass": case "subclase": case "subclases": return SUBCLASS;
            case "job": case "jobs": case "trabajo": case "trabajos": return JOB;
            case "quest": case "quests": case "mision": case "misiones": return QUEST;
            case "pvp": case "duel": case "duelo": case "duelos": return PVP;
            default: return null;
        }
    }
}

package com.arclinker.rpglevelitems.ability;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/** One ability (tree node of a class / subclass, or an item ability). */
public final class AbilityDef {
    public enum Kind { CLASS, SUBCLASS, ITEM }

    public final String id;
    public final Kind kind;
    /** Class or subclass id that owns the ability ("" for item abilities). */
    public final String owner;
    public final String name;
    public final String description;
    public final Material icon;
    public final int row, col;
    public final int cost;
    public final int maxLevel;
    public final double cooldown, cooldownPerLevel;
    public final int requiredLevel;
    public final Map<String, Integer> requiredAbilities;
    public final Map<String, Object> effect;
    // item abilities only
    public final String trigger;          // right_click, on_hit, on_damaged
    public final List<String> appliesTo;  // SWORD, AXE, STAFF, ARMOR, WEAPON...
    public final int minTier;
    public final double chance;
    /** Explicit mana cost, or -1 to use the formula from config. */
    public final double manaOverride;

    public AbilityDef(String id, Kind kind, String owner, String name, String description, Material icon, int row, int col,
                      int cost, int maxLevel, double cooldown, double cooldownPerLevel, int requiredLevel,
                      Map<String, Integer> requiredAbilities, Map<String, Object> effect,
                      String trigger, List<String> appliesTo, int minTier, double chance, double manaOverride) {
        this.id = id;
        this.kind = kind;
        this.owner = owner;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.row = row;
        this.col = col;
        this.cost = cost;
        this.maxLevel = maxLevel;
        this.cooldown = cooldown;
        this.cooldownPerLevel = cooldownPerLevel;
        this.requiredLevel = requiredLevel;
        this.requiredAbilities = requiredAbilities;
        this.effect = effect;
        this.trigger = trigger;
        this.appliesTo = appliesTo;
        this.minTier = minTier;
        this.chance = chance;
        this.manaOverride = manaOverride;
    }

    /** Tree key used for ability XP pools: "class:<id>" / "sub:<id>" ("" for items). */
    public String treeKey() {
        return switch (kind) {
            case CLASS -> "class:" + owner;
            case SUBCLASS -> "sub:" + owner;
            default -> "";
        };
    }

    public double cooldownAt(int level) {
        return Math.max(1.0, cooldown + cooldownPerLevel * (Math.max(1, level) - 1));
    }
}

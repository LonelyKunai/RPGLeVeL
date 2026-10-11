package com.arclinker.rpglevelitems;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages rarity definitions and provides multiplier values for drop rate, attribute scaling, and XP gain.
 *
 * The rarity configuration is expected in the plugin's config.yml under the "rarities" section:
 *
 * rarities:
 *   COMMON: 1
 *   UNCOMMON: 2
 *   RARE: 3
 *   EPIC: 4
 *   LEGENDARY: 5
 *   MYTHIC: 6
 *   ... (any custom rarity)
 *
 * The integer value represents the rarity level (starting at 1). Multipliers are calculated using the
 * linear formula defined in the specifications: multiplier = 1 + rarityLevel * 0.05.
 */
public class RarityManager {
    private final JavaPlugin plugin;
    private final Map<String, Integer> rarityLevels = new HashMap<>();

    public RarityManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadRarities();
    }

    /** Loads rarity definitions from config.yml */
    private void loadRarities() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.isConfigurationSection("rarities")) {
            plugin.getLogger().warning("[RarityManager] No 'rarities' section found in config.yml. Using default COMMON level 1.");
            rarityLevels.put("COMMON", 1);
            return;
        }
        cfg.getConfigurationSection("rarities").getValues(false).forEach((key, value) -> {
            try {
                int level = Integer.parseInt(value.toString());
                rarityLevels.put(key.toUpperCase(), level);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[RarityManager] Invalid rarity level for " + key + ": " + value + ". Skipping.");
            }
        });
    }

    /** Returns the integer level of a rarity name, or 1 (common) if unknown. */
    public int getRarityLevel(String rarityName) {
        return rarityLevels.getOrDefault(rarityName.toUpperCase(), 1);
    }

    /** Linear multiplier calculation: 1 + level * 0.05 */
    public double getMultiplier(String rarityName) {
        int level = getRarityLevel(rarityName);
        return 1.0 + level * 0.05;
    }

    /** Convenience getters for each domain (drop, attribute, xp). They currently share the same formula. */
    public double getDropMultiplier(String rarityName) {
        return getMultiplier(rarityName);
    }

    public double getAttributeMultiplier(String rarityName) {
        return getMultiplier(rarityName);
    }

    public double getXpMultiplier(String rarityName) {
        return getMultiplier(rarityName);
    }

    /** Allows hot-reloading of rarities via /rlv reload or similar command. */
    public void reload() {
        rarityLevels.clear();
        loadRarities();
    }
}


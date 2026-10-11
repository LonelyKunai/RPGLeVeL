package com.arclinker.rpglevelitems;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Manages item-related operations for gear sets,
 * including ingredient processing and item meta handling.
 * Supports configurable namespaces from config.yml for custom items.
 */
public class ItemManager {
    private final RPGLevelTier plugin;
    private final String customNamespace; // Namespace from config.yml (e.g., "rpgitems" or "myitems")

    public ItemManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.customNamespace = plugin.getConfig().getString("custom-item-namespace", "");
    }

    /**
     * Converts a material name string to a Material enum, with fallback to STONE.
     * Handles both valid Bukkit Material names and custom item IDs with namespace.
     */
    public Material parseMaterial(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return Material.STONE;
        }
        // Try to parse as valid Bukkit Material enum value
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Not a valid Material enum value - could be a custom item ID
            // Check if customNamespace is configured for custom item lookup
            if (!customNamespace.isEmpty()) {
                // Store the custom item ID in the item's persistent data container
                // This will be used by external plugins or for future custom item rendering
                plugin.getLogger().info("[ItemManager] Custom item ID '" + materialName + "' detected, namespace: " + customNamespace);
                // Return STONE as fallback - the custom ID will be set via createItemStack()
                return Material.STONE;
            }
            plugin.getLogger().warning("[ItemManager] Invalid material name '" + materialName + "', defaulting to STONE.");
            return Material.STONE;
        }
    }

    /**
     * Creates an ItemStack from a material name string.
     * Handles both valid Bukkit Material names and custom item IDs with namespace.
     */
    public ItemStack createItemStack(String materialName, String customId) {
        Material mat = parseMaterial(materialName);
        ItemStack item = new ItemStack(mat);
        
        if (customId != null && !customId.isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // Use the configured namespace if available, otherwise use "custom_item_id"
                String namespace = customNamespace.isEmpty() ? "custom_item_id" : customNamespace;
                meta.getPersistentDataContainer().set(
                    new NamespacedKey(RPGLevelTier.KEY_NS, namespace), 
                    PersistentDataType.STRING, 
                    customId
                );
                item.setItemMeta(meta);
            }
        }
        
        return item;
    }

    /**
     * Processes ingredient list for a gear set tier recipe.
     * Converts ingredient name strings to ItemStacks.
     */
    public List<ItemStack> processIngredients(List<String> ingredientNames) {
        List<ItemStack> ingredients = new java.util.ArrayList<>();
        for (String name : ingredientNames) {
            Material mat = parseMaterial(name);
            ItemStack ingredient = new ItemStack(mat);
            ingredients.add(ingredient);
        }
        return ingredients;
    }

    /**
     * Gets the display name from an ItemMeta, with fallback.
     */
    public String getDisplayName(ItemMeta meta) {
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return "";
    }
}
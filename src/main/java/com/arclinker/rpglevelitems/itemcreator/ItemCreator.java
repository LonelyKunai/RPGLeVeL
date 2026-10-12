package com.arclinker.rpglevelitems.itemcreator;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

/**
 * Utility class for creating ItemStacks with various properties.
 * Similar to ExecutableItems plugin's item creation capabilities.
 * Supports custom names, enchantments, lore, flags, and persistent data.
 */
public final class ItemCreator {

    private final Plugin plugin;

    public ItemCreator(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a basic ItemStack with the given material.
     */
    public ItemStack create(Material material) {
        return new ItemStack(material);
    }

    /**
     * Creates an ItemStack with a custom display name.
     */
    public ItemStack create(Material material, String displayName) {
        ItemStack item = create(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates an ItemStack with display name and lore.
     */
    public ItemStack create(Material material, String displayName, List<String> lore) {
        ItemStack item = create(material, displayName);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates an ItemStack with enchantments.
     */
    public ItemStack addEnchantment(ItemStack item, Enchantment enchantment, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates an ItemStack with multiple enchantments.
     */
    public ItemStack addEnchantments(ItemStack item, Map<Enchantment, Integer> enchantments) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Hides item flags (unbreakable, enchantments, etc.).
     */
    public ItemStack hideFlags(ItemStack item, ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(flags);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Sets the item's unbreakable flag.
     */
    public ItemStack setUnbreakable(ItemStack item) {
        return hideFlags(item, ItemFlag.HIDE_UNBREAKABLE);
    }

    /**
     * Adds a custom namespace key-value pair to the item's persistent data container.
     */
    public ItemStack setCustomData(ItemStack item, String namespace, String key, String value) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(RPGLevelTier.KEY_NS, namespace);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(namespacedKey, org.bukkit.persistence.PersistentDataType.STRING, value);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a fully configured gear set item with name, lore, and custom data.
     * Used by GearSetManager to create gear set items.
     */
    public ItemStack createGearSetItem(String setName, int tier, String customId) {
        // Create item based on configured namespace
        String namespace = plugin.getConfig().getString("custom-item-namespace", "RLV");
        ItemStack item = create(Material.STONE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Set display name with set and tier
            String displayName = setName + " Tier " + tier;
            meta.setDisplayName(displayName);

            // Set custom ID in persistent data container
            NamespacedKey key = new NamespacedKey(RPGLevelTier.KEY_NS, namespace);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(key, org.bukkit.persistence.PersistentDataType.STRING, customId);

            // Add item flags to hide custom data
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Creates a weapon item with standard properties.
     */
    public ItemStack createWeaponItem(String name, List<String> lore, Enchantment primaryEnchant, int enchantLevel) {
        ItemStack item = create(Material.DIAMOND_SWORD, name, lore);
        if (primaryEnchant != null) {
            addEnchantment(item, primaryEnchant, enchantLevel);
        }
        return setUnbreakable(item);
    }

    /**
     * Creates armor item with protection properties.
     */
    public ItemStack createArmorItem(String name, List<String> lore) {
        ItemStack item = create(Material.LEATHER_CHESTPLATE, name, lore);
        return setUnbreakable(item);
    }

    /**
     * Creates a consumable item (food, potion effect, etc.).
     */
    public ItemStack createConsumableItem(Material material, String name, List<String> lore, Integer[] potionEffects) {
        ItemStack item = create(material, name, lore);
        // Could add potion effect data here
        return item;
    }

    /**
     * Creates a copy of an existing item with modified properties.
     */
    public ItemStack modifyItem(ItemStack original, String newName, List<String> newLore) {
        ItemStack newItem = new ItemStack(original);
        ItemMeta meta = newItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(newName);
            if (newLore != null) {
                meta.setLore(newLore);
            }
            newItem.setItemMeta(meta);
        }
        return newItem;
    }

    /**
     * Gets the custom ID from an item's persistent data container.
     */
    public String getCustomId(ItemStack item, String namespace) {
        if (item == null || item.getItemMeta() == null) return null;
        NamespacedKey key = new NamespacedKey(RPGLevelTier.KEY_NS, namespace);
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.get(key, org.bukkit.persistence.PersistentDataType.STRING);
    }

    /**
     * AnacroniCraft - Crea mochila con tier 27/45/81/108/135 - paginada >45 usa GUI compartido
     */
    public ItemStack createBackpackItem(String id, int tier, int slots) {
        Material mat = tier <= 2 ? Material.CHEST : tier <= 4 ? Material.TRAPPED_CHEST : Material.ENDER_CHEST;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean paginated = slots > 45;
            int pages = (int) Math.ceil(slots / 45.0);
            meta.setDisplayName("§6🎒 " + id + " T" + tier + " [" + slots + "]" + (paginated ? " [PAGINADA]" : ""));
            meta.setLore(List.of(
                "§7Tier " + tier + " | " + slots + " slots",
                "§7Paginada: " + (paginated ? "Si - " + pages + "x45 GUI 54" : "No"),
                "§7Mismo GUI que libro recetas",
                "§7data/items/04_mecanismos_industriales.rlvp"
            ));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(RPGLevelTier.KEY_NS, "backpack_tier"), org.bukkit.persistence.PersistentDataType.INTEGER, tier);
            pdc.set(new NamespacedKey(RPGLevelTier.KEY_NS, "backpack_id"), org.bukkit.persistence.PersistentDataType.STRING, id);
            pdc.set(new NamespacedKey(RPGLevelTier.KEY_NS, "backpack_slots"), org.bukkit.persistence.PersistentDataType.INTEGER, slots);
            pdc.set(new NamespacedKey(RPGLevelTier.KEY_NS, "rlvp_id"), org.bukkit.persistence.PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Gets the display name from an item.
     */
    public String getDisplayName(ItemStack item) {

        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getDisplayName();
    }
}

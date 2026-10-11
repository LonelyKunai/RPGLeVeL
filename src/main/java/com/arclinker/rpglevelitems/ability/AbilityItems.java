package com.arclinker.rpglevelitems.ability;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Ability tomes: items that hold a copy of an unlocked ability. Right-click to cast.
 * They only work for players who have unlocked that ability (checked on every use).
 */
public final class AbilityItems {
    private final RPGLevelTier plugin;
    private final NamespacedKey tomeKey;

    public AbilityItems(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.tomeKey = new NamespacedKey(RPGLevelTier.KEY_NS, "ability_tome");
    }

    /** Stable custom model data for a tome (resource pack friendly): 930000 + crc(id) % 60000. */
    public static int modelData(String abilityId) {
        CRC32 crc = new CRC32();
        crc.update(abilityId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return 930000 + (int) (crc.getValue() % 60000);
    }

    public ItemStack create(AbilityDef d) {
        Material mat = d.kind == AbilityDef.Kind.CLASS ? Material.ENCHANTED_BOOK : Material.KNOWLEDGE_BOOK;
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        String owner = d.kind == AbilityDef.Kind.CLASS
                ? nameOfClass(d.owner) : nameOfSub(d.owner);
        meta.setDisplayName("§d§l✦ Tomo: §f" + d.name);
        List<String> lore = new ArrayList<>();
        lore.add("§7" + d.description);
        lore.add("");
        lore.add("§7" + (d.kind == AbilityDef.Kind.CLASS ? "Clase" : "Subclase") + ": §f" + owner);
        lore.add("§7Enfriamiento base: §f" + d.cooldown + "s");
        lore.add("");
        lore.add("§eClic derecho §7para usar");
        lore.add("§cSolo funciona si has desbloqueado");
        lore.add("§cesta habilidad (nivel según tu progreso).");
        meta.setLore(lore);
        meta.setCustomModelData(modelData(d.id));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(tomeKey, PersistentDataType.STRING, d.id);
        it.setItemMeta(meta);
        return it;
    }

    private String nameOfClass(String id) {
        var info = plugin.getClassManager().getClassInfo(id);
        return info != null ? info.name : id;
    }

    private String nameOfSub(String id) {
        var info = plugin.getSubClassManager().getInfo(id);
        return info != null ? info.name : id;
    }

    /** Ability id stored in a tome, or null if the item is not a tome. */
    public String readId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(tomeKey, PersistentDataType.STRING);
    }
}

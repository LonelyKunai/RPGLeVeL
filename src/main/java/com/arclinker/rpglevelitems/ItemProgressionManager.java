package com.arclinker.rpglevelitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ItemProgressionManager {

    private final RPGLevelTier plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();

    public final NamespacedKey keyLevel;
    public final NamespacedKey keyTier;
    public final NamespacedKey keyXp;
    public final NamespacedKey keyInit;
    public final NamespacedKey keyBaseDamage;
    public final NamespacedKey keyBonusDamage;

    private static final String[] ROMAN_NUMERALS = {
        "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    public ItemProgressionManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.keyLevel = new NamespacedKey(RPGLevelTier.KEY_NS, "rpg_level");
        this.keyTier = new NamespacedKey(RPGLevelTier.KEY_NS, "rpg_tier");
        this.keyXp = new NamespacedKey(RPGLevelTier.KEY_NS, "rpg_xp");
        this.keyInit = new NamespacedKey(RPGLevelTier.KEY_NS, "rpg_initialized");
        this.keyBaseDamage = new NamespacedKey(RPGLevelTier.KEY_NS, "rpg_base_damage");
        this.keyBonusDamage = new NamespacedKey(RPGLevelTier.KEY_NS, "rpg_bonus_damage");
    }

    private static final net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer LEGACY =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();

    /** Texto plano de una línea de lore: Bukkit las guarda en legacy (§) y las antiguas pueden traer etiquetas MiniMessage; MiniMessage lanza excepción con §. */
    private static String loreText(String s) {
        return org.bukkit.ChatColor.stripColor(s).replaceAll("<[^>]+>", "");
    }

    public boolean isRpgItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(keyInit, PersistentDataType.BYTE)) {
            return true;
        }

        // Check Oraxen NBT tag
        for (NamespacedKey key : pdc.getKeys()) {
            if (key.getNamespace().equalsIgnoreCase("oraxen")) {
                return true;
            }
        }

        // Check if item has Nivel lore or is an RPG gear piece
        if (meta.hasLore()) {
            List<String> loreStrings = meta.getLore();
            if (loreStrings != null) {
                for (String line : loreStrings) {
                    String plain = loreText(line);
                    if (plain.contains("Nivel:") || plain.contains("Tier:") || plain.contains("Progreso:")) {
                        return true;
                    }
                }
            }
        }

        boolean allowAllWeapons = plugin.getConfig().getBoolean("progression.allow_all_weapons", true);
        if (allowAllWeapons) {
            return isWeapon(item.getType()) || isArmor(item.getType());
        }

        return false;
    }

    public boolean isWeapon(Material mat) {
        String name = mat.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_SHOVEL")
                || name.endsWith("_BOW") || name.endsWith("_CROSSBOW") || mat == Material.TRIDENT
                || mat == Material.MACE || mat == Material.BLAZE_ROD || mat == Material.STICK
                || mat == Material.END_CRYSTAL;
    }

    public boolean isArmor(Material mat) {
        String name = mat.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS") || mat == Material.SHIELD;
    }

    public void initializeItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(keyInit, PersistentDataType.BYTE)) {
            pdc.set(keyInit, PersistentDataType.BYTE, (byte) 1);
            if (!pdc.has(keyLevel, PersistentDataType.INTEGER)) {
                pdc.set(keyLevel, PersistentDataType.INTEGER, 1);
            }
            if (!pdc.has(keyTier, PersistentDataType.INTEGER)) {
                pdc.set(keyTier, PersistentDataType.INTEGER, 1);
            }
            if (!pdc.has(keyXp, PersistentDataType.LONG)) {
                pdc.set(keyXp, PersistentDataType.LONG, 0L);
            }

            // Determine and store base damage
            if (!pdc.has(keyBaseDamage, PersistentDataType.DOUBLE)) {
                double baseDmg = detectBaseDamage(item, meta);
                pdc.set(keyBaseDamage, PersistentDataType.DOUBLE, baseDmg);
            }

            applyAttributesAndLore(item, meta);
            item.setItemMeta(meta);
        }
    }

    private double detectBaseDamage(ItemStack item, ItemMeta meta) {
        if (meta.hasAttributeModifiers()) {
            Collection<AttributeModifier> mods = meta.getAttributeModifiers(Attribute.GENERIC_ATTACK_DAMAGE);
            if (mods != null && !mods.isEmpty()) {
                double sum = 0.0;
                for (AttributeModifier mod : mods) {
                    sum += mod.getAmount();
                }
                if (sum > 0) return sum;
            }
        }

        Material mat = item.getType();
        String name = mat.name();
        if (name.contains("NETHERITE_SWORD")) return 8.0;
        if (name.contains("DIAMOND_SWORD")) return 7.0;
        if (name.contains("IRON_SWORD")) return 6.0;
        if (name.contains("STONE_SWORD")) return 5.0;
        if (name.contains("NETHERITE_AXE")) return 10.0;
        if (name.contains("DIAMOND_AXE") || name.contains("IRON_AXE")) return 9.0;
        if (name.contains("BOW") || name.contains("CROSSBOW")) return 7.0;
        if (mat == Material.TRIDENT) return 9.0;
        if (mat == Material.MACE) return 6.0;
        return 5.0;
    }

    public int getLevel(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(keyLevel, PersistentDataType.INTEGER, 1);
    }

    public int getTier(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(keyTier, PersistentDataType.INTEGER, 1);
    }

    public long getXp(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(keyXp, PersistentDataType.LONG, 0L);
    }

    public double getBaseDamage(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(keyBaseDamage, PersistentDataType.DOUBLE, 6.0);
    }

    public void setLevel(ItemStack item, int level) {
        if (item == null || item.getItemMeta() == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keyLevel, PersistentDataType.INTEGER, Math.max(1, Math.min(30, level)));
        applyAttributesAndLore(item, meta);
        item.setItemMeta(meta);
    }

    public void setTier(ItemStack item, int tier) {
        if (item == null || item.getItemMeta() == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keyTier, PersistentDataType.INTEGER, Math.max(1, Math.min(10, tier)));
        applyAttributesAndLore(item, meta);
        item.setItemMeta(meta);
    }

    public long getNeededXp(int level) {
        long baseXp = plugin.getConfig().getLong("progression.base_xp_per_level", 100L);
        return baseXp * level;
    }

    public String toRoman(int number) {
        if (number >= 1 && number <= 10) {
            return ROMAN_NUMERALS[number];
        }
        return String.valueOf(number);
    }

    public record ProgressionResult(boolean leveledUp, boolean tierAscended, int newLevel, int newTier) {}

    public ProgressionResult addXp(ItemStack item, long amount) {
        if (item == null || item.getItemMeta() == null) {
            return new ProgressionResult(false, false, 1, 1);
        }
        initializeItem(item);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        int level = pdc.getOrDefault(keyLevel, PersistentDataType.INTEGER, 1);
        int tier = pdc.getOrDefault(keyTier, PersistentDataType.INTEGER, 1);
        long xp = pdc.getOrDefault(keyXp, PersistentDataType.LONG, 0L);

        int maxTier = plugin.getConfig().getInt("progression.max_tier", 10);
        int levelsPerTier = plugin.getConfig().getInt("progression.levels_per_tier", 30);

        if (tier >= maxTier && level >= levelsPerTier) {
            return new ProgressionResult(false, false, level, tier);
        }

        xp += amount;
        boolean leveledUp = false;
        boolean tierAscended = false;

        long needed = getNeededXp(level);
        while (xp >= needed) {
            if (level < levelsPerTier) {
                level++;
                xp -= needed;
                leveledUp = true;
            } else {
                // t1 lv31 = t2 lv1 -> Tier Ascension!
                if (tier < maxTier) {
                    tier++;
                    level = 1;
                    xp -= needed;
                    tierAscended = true;
                } else {
                    xp = needed;
                    break;
                }
            }
            needed = getNeededXp(level);
        }

        pdc.set(keyLevel, PersistentDataType.INTEGER, level);
        pdc.set(keyTier, PersistentDataType.INTEGER, tier);
        pdc.set(keyXp, PersistentDataType.LONG, Math.max(0L, xp));
        pdc.set(keyInit, PersistentDataType.BYTE, (byte) 1);

        applyAttributesAndLore(item, meta);
        item.setItemMeta(meta);

        return new ProgressionResult(leveledUp, tierAscended, level, tier);
    }

    public void applyAttributesAndLore(ItemStack item, ItemMeta meta) {
        int level = getLevel(meta);
        int tier = getTier(meta);

        double bonusPerLevel = plugin.getConfig().getDouble("scaling.bonus_damage_per_level", 0.5);
        double bonusPerTier = plugin.getConfig().getDouble("scaling.bonus_damage_per_tier", 2.0);
        double bonusDamage = ((tier - 1) * bonusPerTier) + ((level - 1) * bonusPerLevel);

        if (isWeapon(item.getType())) {
            // Preserve existing attack speed if present
            double attackSpeed = 1.6;
            if (meta.hasAttributeModifiers()) {
                Collection<AttributeModifier> speedMods = meta.getAttributeModifiers(Attribute.GENERIC_ATTACK_SPEED);
                if (speedMods != null && !speedMods.isEmpty()) {
                    attackSpeed = speedMods.iterator().next().getAmount();
                }
            }

            // Remove any existing bonus damage modifier for attack damage
            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(
                    keyBonusDamage,
                    bonusDamage,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            ));

            // Re‑apply attack speed (optional, keeps existing speed unchanged)
            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(RPGLevelTier.KEY_NS, "rpg_attack_speed"),
                    attackSpeed,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            ));
        }

        updateItemLore(item, meta, bonusDamage);
    }

    public void updateItemLore(ItemStack item, ItemMeta meta, double bonusDamage) {
        int level = getLevel(meta);
        int tier = getTier(meta);
        long xp = getXp(meta);
        long needed = getNeededXp(level);

        int barLen = plugin.getConfig().getInt("lore.progress_bar_length", 10);
        String barSymbol = plugin.getConfig().getString("lore.progress_bar_symbol", "■");
        int filled = (int) Math.min(barLen, Math.max(0, (xp * barLen) / Math.max(1, needed)));
        String barStr = "<green>" + barSymbol.repeat(filled) + "<dark_gray>" + barSymbol.repeat(barLen - filled);

        String levelText = plugin.getConfig().getString("lore.level_format", "<gray>• <white>Nivel: <yellow>{level}<gray>/30")
                .replace("{level}", String.valueOf(level));

        String tierText = plugin.getConfig().getString("lore.tier_format", "<gray>• <white>Tier: <gold>{tier_roman} <gray>(Tier {tier}/10)")
                .replace("{tier}", String.valueOf(tier))
                .replace("{tier_roman}", toRoman(tier));

        String progressText = plugin.getConfig().getString("lore.progress_format", "<gray>• <white>Progreso: <green>{progress_bar} <gray>({xp}/{needed_xp} XP)")
                .replace("{progress_bar}", barStr)
                .replace("{xp}", String.valueOf(xp))
                .replace("{needed_xp}", String.valueOf(needed));

        String damageText = String.format("<gray>• <white>Daño Extra: <red>+%.1f <gray>(Lv %d / Tier %s)", bonusDamage, level, toRoman(tier));

        Component levelComp = miniMessage.deserialize(levelText);
        Component tierComp = miniMessage.deserialize(tierText);
        Component progressComp = miniMessage.deserialize(progressText);
        Component damageComp = miniMessage.deserialize(damageText);

        List<String> currentLoreStrings = meta.getLore();
        List<String> keptLore = new ArrayList<>();
        List<Component> newLore = new ArrayList<>();
        if (currentLoreStrings != null) {
            for (String line : currentLoreStrings) {
                String plain = loreText(line);
                if (!plain.contains("Nivel:") && !plain.contains("Tier:") && !plain.contains("Progreso:")
                        && !plain.contains("Daño Extra:") && !plain.contains("Habilidades:")
                        && !plain.contains("Impacto Crítico") && !plain.contains("Llama Volcánica")
                        && !plain.contains("Corte Desgarrador") && !plain.contains("Furia del Trueno")
                        && !plain.contains("Robo de Vida") && !plain.contains("Onda Sísmica")
                        && !plain.contains("Poder Divino") && !plain.contains("Siguiente Habilidad:")) {
                    keptLore.add(line);
                }
            }
        }

        // Add RPG Progression Lore
        newLore.add(levelComp);
        newLore.add(tierComp);
        newLore.add(progressComp);
        if (isWeapon(item.getType())) {
            newLore.add(damageComp);
        }

        // Add Unlocked Abilities based on Tier
        newLore.add(miniMessage.deserialize("<gray>• <white>Habilidades:"));
        boolean hasSkill = false;
        if (tier >= 2) {
            newLore.add(miniMessage.deserialize("  <yellow>💥 Impacto Crítico <gray>(15% prob. x1.5 daño)"));
            hasSkill = true;
        }
        if (tier >= 3) {
            newLore.add(miniMessage.deserialize("  <gold>🔥 Llama Volcánica <gray>(Quema enemigos 4s)"));
            hasSkill = true;
        }
        if (tier >= 4) {
            newLore.add(miniMessage.deserialize("  <red>🩸 Corte Desgarrador <gray>(Aplica sangrado)"));
            hasSkill = true;
        }
        if (tier >= 5) {
            newLore.add(miniMessage.deserialize("  <aqua>⚡ Furia del Trueno <gray>(Invoca relámpagos)"));
            hasSkill = true;
        }
        if (tier >= 6) {
            newLore.add(miniMessage.deserialize("  <dark_purple>🧛 Robo de Vida <gray>(Restaura tu salud)"));
            hasSkill = true;
        }
        if (tier >= 8) {
            newLore.add(miniMessage.deserialize("  <white>🌪️ Onda Sísmica <gray>(Empuje y daño en área)"));
            hasSkill = true;
        }
        if (tier >= 10) {
            newLore.add(miniMessage.deserialize("  <light_purple>👑 Poder Divino <gray>(+30% daño definitivo)"));
            hasSkill = true;
        }

        if (!hasSkill) {
            newLore.add(miniMessage.deserialize("  <dark_gray><i>(Se desbloquean al alcanzar Tier II)</i>"));
        } else if (tier < 10) {
            String nextSkill = switch (tier) {
                case 2 -> "🔥 Llama Volcánica (Tier III)";
                case 3 -> "🩸 Corte Desgarrador (Tier IV)";
                case 4 -> "⚡ Furia del Trueno (Tier V)";
                case 5 -> "🧛 Robo de Vida (Tier VI)";
                case 6, 7 -> "🌪️ Onda Sísmica (Tier VIII)";
                default -> "👑 Poder Divino (Tier X)";
            };
            newLore.add(miniMessage.deserialize("  <dark_gray>Próxima: <gray>" + nextSkill));
        }

        List<String> newLoreStrings = new ArrayList<>(keptLore);
        for (Component comp : newLore) newLoreStrings.add(LEGACY.serialize(comp));
        meta.setLore(newLoreStrings);
    }
}

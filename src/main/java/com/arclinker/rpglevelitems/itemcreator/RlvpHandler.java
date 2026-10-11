package com.arclinker.rpglevelitems.itemcreator;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads item definitions from {@code .rlvp} files (YAML) and builds ItemStacks.
 * <p>
 * ONLY files with the {@code .rlvp} extension are loaded; any other file is ignored.
 * <p>
 * Source folder: {@code plugins/RPGLeVeL/data/items/} (recursive).
 * Bundled {@code data/items/**.rlvp} resources are copied there on first run
 * (existing files are never overwritten). Items are loaded when the handler is
 * constructed, i.e. during plugin enable.
 * <p>
 * File format:
 * <pre>
 * items:
 *   item_id:
 *     material: IRON_SWORD
 *     display_name: "Name"
 *     lore: [ "line1", "line2" ]
 *     enchantments:
 *       - type: DURABILITY
 *         level: 1
 *     unbreakable: true
 *     custom_data:
 *       key: value
 * </pre>
 * The top-level {@code items:} wrapper is optional.
 */
public final class RlvpHandler {

    public static final String EXTENSION = ".rlvp";
    private static final String RESOURCE_ROOT = "data/items/";

    private static final Map<String, String> LEGACY_ENCHANTS = Map.of(
            "DURABILITY", "unbreaking",
            "DAMAGE_ALL", "sharpness",
            "DAMAGE_UNDEAD", "smite",
            "DAMAGE_ARTHROPODS", "bane_of_arthropods",
            "PROTECTION_ENVIRONMENTAL", "protection",
            "PROTECTION_FIRE", "fire_protection",
            "PROTECTION_FALL", "feather_falling",
            "PROTECTION_EXPLOSIONS", "blast_protection",
            "PROTECTION_PROJECTILE", "projectile_protection",
            "LOOT_BONUS_MOBS", "looting");

    /** One ingredient of a station recipe: UPPERCASE = vanilla material, lowercase = .rlvp item id. */
    public record Ingredient(String key, int amount) {}

    /** Station recipe defined by the {@code recipe:} section of an item. */
    public record Recipe(String result, String station, int tier, List<Ingredient> ingredients) {}

    /** Classification of an item for the item list: category (one per file), type, piece and tier. */
    public record ItemInfo(String id, String category, String categoryName, String group, String type, String piece, int tier, Material material) {}

    /** A category of the item list (normally one .rlvp file). */
    public record Category(String id, String name, String group, Material icon, List<String> ids) {}

    private final Plugin plugin;
    private final Logger logger;
    private final File itemsDir;
    private final Map<String, ItemStack> itemCache = new LinkedHashMap<>();
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private final Map<String, ItemInfo> infos = new LinkedHashMap<>();
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final NamespacedKey idKey;

    public RlvpHandler(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.itemsDir = new File(plugin.getDataFolder(), "data/items");
        this.idKey = new NamespacedKey(RPGLevelTier.KEY_NS, "rlvp_id");
        extractBundledItems();
        loadItems();
    }

    /** Copy bundled .rlvp resources into the data folder (never overwrites). */
    private void extractBundledItems() {
        com.arclinker.rpglevelitems.DataFiles.extract((com.arclinker.rpglevelitems.RPGLevelTier) plugin, "items", EXTENSION);
    }

    /** Load every *.rlvp under the items folder (recursive). Other extensions are ignored. */
    private void loadItems() {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(itemsDir.toPath())) {
            files = walk.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            logger.severe("[RlvpHandler] Cannot read " + itemsDir + ": " + e.getMessage());
            return;
        }

        int fileCount = 0;
        for (Path path : files) {
            String fileName = path.getFileName().toString();
            if (!fileName.endsWith(EXTENSION)) {
                logger.warning("[RlvpHandler] Ignored (extension must be " + EXTENSION + "): " + itemsDir.toPath().relativize(path));
                continue;
            }
            fileCount++;
            loadFile(path.toFile());
        }
        buildCategories();
        logger.info("[RlvpHandler] Loaded " + itemCache.size() + " items from " + fileCount + " " + EXTENSION + " files.");
    }

    private void loadFile(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            yaml.load(reader);
        } catch (Exception e) {
            logger.severe("[RlvpHandler] Invalid " + file.getName() + ": " + e.getMessage());
            return;
        }

        ConfigurationSection root = yaml.isConfigurationSection("items") ? yaml.getConfigurationSection("items") : yaml;
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null || !sec.contains("material")) continue;
            try {
                ItemStack item = buildItem(key, sec, file);
                if (item == null) continue;
                String id = key.toLowerCase(Locale.ROOT);
                if (itemCache.put(id, item) != null) {
                    logger.warning("[RlvpHandler] Duplicate item id '" + id + "' (overridden by " + file.getName() + ")");
                }
            } catch (Exception e) {
                logger.warning("[RlvpHandler] Skipped '" + key + "' in " + file.getName() + ": " + e);
            }
        }
    }

    private ItemStack buildItem(String id, ConfigurationSection sec, File file) {
        String matName = sec.getString("material", "");
        Material mat = Material.matchMaterial(matName.trim());
        if (mat == null || mat.isAir()) {
            logger.warning("[RlvpHandler] Unknown material '" + matName + "' in item '" + id + "'");
            return null;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Identity + model: the id survives placing/breaking/stacking, custom_model_data drives the resource pack model
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id.toLowerCase(Locale.ROOT));
        if (sec.contains("custom_model_data")) {
            meta.setCustomModelData(sec.getInt("custom_model_data"));
        }

        if (sec.contains("display_name")) {
            meta.setDisplayName(color(sec.getString("display_name")));
        }
        if (sec.contains("lore")) {
            meta.setLore(sec.getStringList("lore").stream().map(RlvpHandler::color).collect(Collectors.toList()));
        }

        for (Map<?, ?> map : sec.getMapList("enchantments")) {
            Object type = map.get("type");
            Object level = map.get("level");
            if (type == null) continue;
            Enchantment ench = findEnchantment(type.toString());
            if (ench == null) {
                logger.warning("[RlvpHandler] Unknown enchantment '" + type + "' in item '" + id + "'");
                continue;
            }
            int lvl = level instanceof Number ? ((Number) level).intValue() : 1;
            meta.addEnchant(ench, lvl, true);
        }

        if (sec.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }

        ConfigurationSection data = sec.getConfigurationSection("custom_data");
        if (data != null) {
            for (String k : data.getKeys(false)) {
                NamespacedKey nk = NamespacedKey.fromString(RPGLevelTier.KEY_NS + ":" + k.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._/-]", "_"));
                if (nk == null) continue;
                meta.getPersistentDataContainer().set(nk, PersistentDataType.STRING, String.valueOf(data.get(k)));
            }
        }

        // Stats / sockets / requirements (see ItemData)
        ConfigurationSection stats = sec.getConfigurationSection("stats");
        if (stats != null) {
            Map<String, Double> m = new LinkedHashMap<>();
            for (String k : stats.getKeys(false)) {
                if (!com.arclinker.rpglevelitems.stats.Stats.isKnown(k)) {
                    logger.warning("[RlvpHandler] Unknown stat '" + k + "' in item '" + id + "'");
                    continue;
                }
                m.put(k, stats.getDouble(k));
            }
            com.arclinker.rpglevelitems.stats.ItemData.setBaseStats(meta, m);
        }
        if (sec.getInt("sockets", 0) > 0) com.arclinker.rpglevelitems.stats.ItemData.setSockets(meta, sec.getInt("sockets"));
        ConfigurationSection req = sec.getConfigurationSection("requires");
        if (req != null) {
            Map<String, String> m = new LinkedHashMap<>();
            for (String k : req.getKeys(false)) m.put(k, req.getString(k, ""));
            com.arclinker.rpglevelitems.stats.ItemData.setRequirements(meta, m);
        }

        item.setItemMeta(meta);
        com.arclinker.rpglevelitems.stats.ItemData.rebuildLore(item);
        String lid = id.toLowerCase(Locale.ROOT);
        parseRecipe(lid, sec.getConfigurationSection("recipe"));
        infos.put(lid, classify(lid, sec, mat, file));
        return item;
    }

    /** recipe: {station: warrior, tier: 1, ingredients: ["IRON_SWORD:1", "steel_ingot:3"]} */
    private void parseRecipe(String id, ConfigurationSection rs) {
        if (rs == null) return;
        List<Ingredient> list = new ArrayList<>();
        for (Object o : rs.getList("ingredients", List.of())) {
            if (o instanceof Map<?, ?> m) {
                Object k = m.get("item") != null ? m.get("item") : m.get("key");
                Object a = m.get("amount");
                if (k != null) list.add(new Ingredient(k.toString().trim(), a instanceof Number n ? Math.max(1, n.intValue()) : 1));
            } else if (o != null) {
                String t = o.toString().trim();
                int amount = 1;
                int i = t.lastIndexOf(':');
                if (i > 0) {
                    try {
                        amount = Math.max(1, Integer.parseInt(t.substring(i + 1).trim()));
                        t = t.substring(0, i).trim();
                    } catch (NumberFormatException ignored) { }
                }
                list.add(new Ingredient(t, amount));
            }
        }
        String station = rs.getString("station", "");
        if (list.isEmpty() || station.isEmpty()) return;
        recipes.put(id, new Recipe(id, station.toLowerCase(Locale.ROOT), Math.max(1, rs.getInt("tier", 1)), list));
    }

    private static String baseName(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return (i > 0 ? n.substring(0, i) : n).toLowerCase(Locale.ROOT);
    }

    /** Reads the {@code list:} section, falling back to the file name / material / id suffix. */
    private ItemInfo classify(String id, ConfigurationSection sec, Material mat, File file) {
        ConfigurationSection l = sec.getConfigurationSection("list");
        String category = l != null ? l.getString("category", baseName(file)) : baseName(file);
        String name = l != null ? l.getString("name", category) : category;
        String group = l != null ? l.getString("group", "") : "";
        if (group.isEmpty() && file.getParentFile() != null) group = file.getParentFile().getName().toLowerCase(Locale.ROOT);
        String n = mat.name();
        String type = l != null ? l.getString("type", "") : "";
        if (type.isEmpty()) {
            if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) type = "armor";
            else if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                    || mat == Material.BOW || mat == Material.CROSSBOW || mat == Material.TRIDENT || mat == Material.MACE) type = "weapon";
            else type = "other";
        }
        String piece = l != null ? l.getString("piece", "") : "";
        if (piece.isEmpty()) {
            for (String p : new String[]{"helmet", "chestplate", "leggings", "boots"}) if (n.toLowerCase(Locale.ROOT).endsWith(p)) piece = p;
        }
        int tier = l != null ? l.getInt("tier", 0) : 0;
        if (tier == 0 && sec.getConfigurationSection("recipe") != null) tier = sec.getInt("recipe.tier", 0);
        if (tier == 0) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("_t(\\d+)$").matcher(id);
            if (m.find()) tier = Integer.parseInt(m.group(1));
        }
        return new ItemInfo(id, category.toLowerCase(Locale.ROOT), name, group, type, piece, tier, mat);
    }

    private void buildCategories() {
        categories.clear();
        for (ItemInfo i : infos.values()) {
            Category c = categories.get(i.category());
            if (c == null) {
                c = new Category(i.category(), i.categoryName(), i.group(), i.material(), new ArrayList<>());
                categories.put(i.category(), c);
            }
            c.ids().add(i.id());
        }
        // icon of a category = its first weapon (or first item)
        for (Map.Entry<String, Category> e : new ArrayList<>(categories.entrySet())) {
            Category c = e.getValue();
            Material icon = c.icon();
            for (String id : c.ids()) if (infos.get(id).type().equals("weapon")) { icon = infos.get(id).material(); break; }
            if (icon != c.icon()) categories.put(e.getKey(), new Category(c.id(), c.name(), c.group(), icon, c.ids()));
        }
    }

    public ItemInfo getInfo(String id) {
        return id == null ? null : infos.get(id.toLowerCase(Locale.ROOT));
    }

    /** Item-list categories in file order. */
    public Collection<Category> getCategories() {
        return categories.values();
    }

    public Category getCategory(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    /** Station recipe of an item id, or null. */
    public Recipe getRecipe(String itemId) {
        return itemId == null ? null : recipes.get(itemId.toLowerCase(Locale.ROOT));
    }

    /** Ids of the items craftable at the given station, in file order. */
    public List<String> getRecipeIds(String station) {
        List<String> out = new ArrayList<>();
        for (Recipe r : recipes.values()) if (r.station().equalsIgnoreCase(station)) out.add(r.result());
        return out;
    }

    @SuppressWarnings("deprecation")
    private static Enchantment findEnchantment(String name) {
        String n = name.trim();
        String mapped = LEGACY_ENCHANTS.getOrDefault(n.toUpperCase(Locale.ROOT), n.toLowerCase(Locale.ROOT));
        NamespacedKey key = NamespacedKey.fromString(mapped);
        Enchantment e = key == null ? null : Enchantment.getByKey(key);
        return e != null ? e : Enchantment.getByName(n.toUpperCase(Locale.ROOT));
    }

    private static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    /** The .rlvp id stored in the item (null if it is not an .rlvp item). */
    public String getId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    /** Get an item by its id (the key in the .rlvp file, case-insensitive). */
    public ItemStack getItem(String itemId) {
        ItemStack item = itemCache.get(itemId.toLowerCase(Locale.ROOT));
        return item == null ? null : item.clone();
    }

    /** All loaded items (cloned). */
    public Map<String, ItemStack> getAllItems() {
        Map<String, ItemStack> copy = new LinkedHashMap<>();
        itemCache.forEach((k, v) -> copy.put(k, v.clone()));
        return copy;
    }

    public List<String> getItemIds() {
        return new ArrayList<>(itemCache.keySet());
    }

    /** Reload all .rlvp items from disk. */
    public void reload() {
        itemCache.clear();
        recipes.clear();
        infos.clear();
        loadItems();
    }
}

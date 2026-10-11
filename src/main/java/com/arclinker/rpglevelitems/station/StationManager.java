package com.arclinker.rpglevelitems.station;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.itemcreator.RlvpHandler;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

/**
 * Class / subclass work stations. A station is a placed .rlvp block (item {@code station_<set>});
 * right-clicking it opens a menu with the recipes of that set ({@code recipe:} section of the
 * set's .rlvp items). Crafting consumes ingredients from the player's inventory.
 * Stations are defined in {@code data/recipes/stations.yml}; the stations themselves are crafted in
 * vanilla stations through {@link com.arclinker.rpglevelitems.RecipeManager}.
 */
public final class StationManager {

    public enum Kind { CLASS, SUBCLASS, UNIVERSAL }

    /** @param id set id (warrior, paladin, adventurer...), @param owner class / subclass id that may use it. */
    public record Station(String id, String name, Kind kind, String itemId, String owner, String parent, Material block) {}

    private final RPGLevelTier plugin;
    private final Map<String, Station> stations = new LinkedHashMap<>();
    private final Map<String, Station> byItem = new HashMap<>();

    public StationManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        stations.clear();
        byItem.clear();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "recipes");
        ConfigurationSection root = cfg.getConfigurationSection("stations");
        if (root == null) {
            plugin.getLogger().warning("[StationManager] No stations found in data/recipes/.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Kind kind;
            try {
                kind = Kind.valueOf(s.getString("kind", "universal").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                kind = Kind.UNIVERSAL;
            }
            Material block = Material.matchMaterial(s.getString("block", "SMITHING_TABLE"));
            String key = id.toLowerCase(Locale.ROOT);
            Station st = new Station(key, s.getString("name", id), kind, s.getString("item", "station_" + key).toLowerCase(Locale.ROOT),
                    s.getString("owner", "").toLowerCase(Locale.ROOT), s.getString("parent", "").toLowerCase(Locale.ROOT),
                    block == null ? Material.SMITHING_TABLE : block);
            stations.put(key, st);
            byItem.put(st.itemId(), st);
        }
        plugin.getLogger().info("[StationManager] Loaded " + stations.size() + " stations.");
    }

    public Collection<Station> all() {
        return stations.values();
    }

    public Station get(String id) {
        return id == null ? null : stations.get(id.toLowerCase(Locale.ROOT));
    }

    /** Station whose item is the given .rlvp id (a placed station block), or null. */
    public Station byItem(String rlvpId) {
        return rlvpId == null ? null : byItem.get(rlvpId.toLowerCase(Locale.ROOT));
    }

    /** Station of the player's active class or subclass, or null. */
    public Station ownStation(Player p, boolean subclass) {
        PlayerRecord r = plugin.getPlayerStore().get(p);
        String id = subclass ? r.subclassId : r.classId;
        if (id.isEmpty()) return null;
        for (Station s : stations.values()) if (s.owner().equals(id.toLowerCase(Locale.ROOT))) return s;
        return null;
    }

    public List<String> recipeIds(Station st) {
        return plugin.getRlvpHandler() == null ? List.of() : plugin.getRlvpHandler().getRecipeIds(st.id());
    }

    // ---- requirements ---------------------------------------------------------------

    /** Minimum class / subclass level for a recipe tier (config stations.tier_levels). */
    public int levelRequired(int tier) {
        List<Integer> l = plugin.getConfig().getIntegerList("stations.tier_levels");
        if (l.isEmpty()) l = List.of(1, 5, 12, 20, 30);
        return l.get(Math.max(0, Math.min(l.size() - 1, tier - 1)));
    }

    public int levelOf(Player p, Station st) {
        return switch (st.kind()) {
            case CLASS -> plugin.getClassLevelManager().getClassLevel(p);
            case SUBCLASS -> plugin.getClassLevelManager().getSubClassLevel(p);
            case UNIVERSAL -> Integer.MAX_VALUE;
        };
    }

    /** Why the player cannot craft at this station / recipe, or null if allowed. */
    public String denyReason(Player p, Station st, RlvpHandler.Recipe recipe) {
        if (!p.hasPermission("rpgleveltier.station")) return "§cNo tienes permiso para usar estaciones.";
        PlayerRecord r = plugin.getPlayerStore().get(p);
        if (plugin.getConfig().getBoolean("stations.require_owner", true)) {
            if (st.kind() == Kind.CLASS && !r.classId.equalsIgnoreCase(st.owner())) return "§cNecesitas la clase §f" + st.name() + "§c para usar esta estación.";
            if (st.kind() == Kind.SUBCLASS && !r.subclassId.equalsIgnoreCase(st.owner())) return "§cNecesitas la subclase §f" + st.name() + "§c para usar esta estación.";
        }
        if (recipe != null && st.kind() != Kind.UNIVERSAL) {
            int need = levelRequired(recipe.tier());
            int have = levelOf(p, st);
            if (have < need) return "§cNecesitas nivel de " + (st.kind() == Kind.CLASS ? "clase" : "subclase") + " §f" + need + "§c (tienes " + have + ").";
        }
        return null;
    }

    // ---- ingredients ----------------------------------------------------------------

    public boolean isCustom(String key) {
        return plugin.getRlvpHandler() != null && plugin.getRlvpHandler().getItem(key) != null;
    }

    private boolean matches(ItemStack it, String key, boolean custom) {
        if (it == null || it.getType().isAir()) return false;
        RlvpHandler h = plugin.getRlvpHandler();
        String id = h == null ? null : h.getId(it);
        if (custom) return key.equalsIgnoreCase(id);
        if (id != null) return false; // a custom item never counts as the plain vanilla material
        Material m = Material.matchMaterial(key);
        return m != null && it.getType() == m;
    }

    public int count(Player p, RlvpHandler.Ingredient ing) {
        boolean custom = isCustom(ing.key());
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (matches(it, ing.key(), custom)) n += it.getAmount();
        return n;
    }

    /** How many times the recipe can be crafted with the current inventory. */
    public int maxCrafts(Player p, RlvpHandler.Recipe recipe) {
        int max = Integer.MAX_VALUE;
        for (RlvpHandler.Ingredient ing : recipe.ingredients()) max = Math.min(max, count(p, ing) / ing.amount());
        return max == Integer.MAX_VALUE ? 0 : max;
    }

    private void take(Player p, RlvpHandler.Ingredient ing, int total) {
        boolean custom = isCustom(ing.key());
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int left = total;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (!matches(it, ing.key(), custom)) continue;
            int take = Math.min(left, it.getAmount());
            left -= take;
            if (it.getAmount() == take) contents[i] = null; else it.setAmount(it.getAmount() - take);
        }
        inv.setStorageContents(contents);
    }

    /** Pretty name of an ingredient for lore. */
    public String ingredientName(RlvpHandler.Ingredient ing) {
        if (isCustom(ing.key())) {
            ItemStack it = plugin.getRlvpHandler().getItem(ing.key());
            if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) return it.getItemMeta().getDisplayName();
        }
        Material m = Material.matchMaterial(ing.key());
        String raw = (m == null ? ing.key() : m.name()).toLowerCase(Locale.ROOT).replace('_', ' ');
        return "§f" + Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    // ---- crafting -------------------------------------------------------------------

    /**
     * Crafts {@code times} (or as many as possible if {@code max}) copies of the item.
     * Returns the number crafted (0 = nothing, reason sent to the player).
     */
    public int craft(Player p, Station st, String itemId, boolean max) {
        RlvpHandler h = plugin.getRlvpHandler();
        RlvpHandler.Recipe recipe = h == null ? null : h.getRecipe(itemId);
        if (recipe == null || !recipe.station().equalsIgnoreCase(st.id())) return 0;
        String deny = denyReason(p, st, recipe);
        if (deny != null) {
            p.sendMessage(deny);
            return 0;
        }
        var evt = new com.arclinker.rpglevelitems.api.events.StationCraftEvent(p, st.id(), itemId, 1);
        Bukkit.getPluginManager().callEvent(evt);
        if (evt.isCancelled()) return 0;
        int can = maxCrafts(p, recipe);
        if (can <= 0) {
            p.sendMessage(plugin.getLanguageManager().tr(p, "station.missing"));
            return 0;
        }
        int times = max ? Math.min(can, 16) : 1;
        for (RlvpHandler.Ingredient ing : recipe.ingredients()) take(p, ing, ing.amount() * times);
        // Blacksmith job: chance of affixes on the forged piece and of getting the materials back
        int smith = plugin.getJobManager().hasJob(p, "blacksmith") ? plugin.getJobManager().getJobLevel(p, "blacksmith") : 0;
        var jab = plugin.getJobAbilities();
        double artisan = jab.sum(p, "artisans_touch", "chance");
        double affixChance = Math.min(40, smith * 0.5) + artisan;
        double saveChance = Math.min(25, smith * 0.3) + jab.sum(p, "thrifty_forge", "chance");
        for (int i = 0; i < times; i++) {
            ItemStack result = h.getItem(itemId);
            if (result == null) break;
            if (affixChance > 0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100 < Math.min(70, affixChance)) {
                // artisan's touch gives one affix more than usual
                plugin.getAffixManager().roll(result, recipe.tier(), artisan > 0 ? Math.min(5, plugin.getAffixManager().countFor(recipe.tier()) + 1) : -1);
                p.sendMessage("§d✦ Tu habilidad de herrero imbuyó la pieza con propiedades.");
            }
            if (saveChance > 0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100 < Math.min(50, saveChance)) {
                for (RlvpHandler.Ingredient ing : recipe.ingredients()) {
                    if (ing.amount() < 2) continue; // never refund the previous-tier piece / single items
                    ItemStack back = isCustom(ing.key()) ? h.getItem(ing.key()) : new ItemStack(Material.matchMaterial(ing.key()));
                    if (back == null) continue;
                    back.setAmount(Math.max(1, ing.amount() / 2));
                    for (ItemStack rest : p.getInventory().addItem(back).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
                }
                p.sendMessage("§a✦ Ahorraste materiales gracias a tu oficio de herrero.");
            }
            Map<Integer, ItemStack> left = p.getInventory().addItem(result);
            for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        ItemStack shown = h.getItem(itemId);
        String name = shown != null && shown.hasItemMeta() && shown.getItemMeta().hasDisplayName() ? shown.getItemMeta().getDisplayName() : itemId;
        p.sendMessage(plugin.getLanguageManager().tr(p, "station.forged", (times > 1 ? times + "x " : "") + name));
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
        // progression hooks: achievements, quests, blacksmith job
        plugin.getAchievements().add(p, "forged", times);
        plugin.getDailyQuestManager().addProgress(p, "forge", "ANY", times);
        plugin.getJobManager().addJobXP(p, "blacksmith", 5 * times * recipe.tier());
        return times;
    }
}

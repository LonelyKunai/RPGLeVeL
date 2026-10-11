package com.arclinker.rpglevelitems;

import com.arclinker.rpglevelitems.itemcreator.RlvpHandler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registers the recipes of {@code data/recipes/*.yml} (section {@code recipes:}) in the vanilla
 * stations: crafting table (shaped / shapeless), furnace, blast furnace, smoker, campfire and
 * stonecutter. Ingredients can be vanilla materials (UPPERCASE) or .rlvp item ids (lowercase),
 * which are matched exactly. This is how players obtain the class / subclass work stations and
 * the forge materials; the class equipment itself is crafted at those stations.
 *
 * <pre>
 * recipes:
 *   my_recipe:
 *     type: shaped | shapeless | smelting | blasting | smoking | campfire | stonecutting
 *     result: item_id_or_MATERIAL
 *     amount: 1
 *     shape: ["ABA", "BSB", "ABA"]        # shaped
 *     ingredients: {A: IRON_INGOT, S: station_warrior}   # shaped (map) / shapeless (list "ID:amount")
 *     input: IRON_BLOCK  time: 400  xp: 1.0               # cooking / stonecutting
 * </pre>
 */
public class RecipeManager {
    private final RPGLevelTier plugin;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public RecipeManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        unregister();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "recipes");
        ConfigurationSection root = cfg.getConfigurationSection("recipes");
        if (root == null) {
            plugin.getLogger().warning("[RecipeManager] No recipes found in data/recipes/.");
            return;
        }
        int ok = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                if (register(id.toLowerCase(Locale.ROOT), s)) ok++;
            } catch (Exception e) {
                plugin.getLogger().warning("[RecipeManager] Recipe '" + id + "' skipped: " + e);
            }
        }
        plugin.getLogger().info("[RecipeManager] Registered " + ok + " vanilla-station recipes.");
    }

    public void unregister() {
        for (NamespacedKey k : keys) Bukkit.removeRecipe(k);
        keys.clear();
    }

    /** Shows every registered recipe in the player's recipe book. */
    public void discover(Player p) {
        if (!keys.isEmpty() && plugin.getConfig().getBoolean("stations.discover_recipes", true)) p.discoverRecipes(keys);
    }

    private ItemStack result(String spec, int amount) {
        RlvpHandler h = plugin.getRlvpHandler();
        ItemStack it = h == null ? null : h.getItem(spec);
        if (it == null) {
            Material m = Material.matchMaterial(spec);
            if (m == null) throw new IllegalArgumentException("unknown result '" + spec + "'");
            it = new ItemStack(m);
        }
        it.setAmount(Math.max(1, amount));
        return it;
    }

    /** MaterialChoice for vanilla materials, ExactChoice for .rlvp item ids. */
    private RecipeChoice choice(String spec) {
        String key = spec.trim();
        int i = key.lastIndexOf(':');
        if (i > 0 && key.substring(i + 1).trim().matches("\\d+")) key = key.substring(0, i).trim();
        RlvpHandler h = plugin.getRlvpHandler();
        ItemStack custom = h == null ? null : h.getItem(key);
        if (custom != null) {
            custom.setAmount(1);
            return new RecipeChoice.ExactChoice(custom);
        }
        Material m = Material.matchMaterial(key);
        if (m == null || m.isAir()) throw new IllegalArgumentException("unknown ingredient '" + spec + "'");
        return new RecipeChoice.MaterialChoice(m);
    }

    private boolean register(String id, ConfigurationSection s) {
        NamespacedKey key = new NamespacedKey(RPGLevelTier.KEY_NS, id);
        String type = s.getString("type", "shaped").toLowerCase(Locale.ROOT);
        ItemStack res = result(s.getString("result", id), s.getInt("amount", 1));
        Recipe recipe;
        switch (type) {
            case "shaped" -> {
                List<String> shape = s.getStringList("shape");
                ConfigurationSection ing = s.getConfigurationSection("ingredients");
                if (shape.isEmpty() || shape.size() > 3 || ing == null) throw new IllegalArgumentException("bad shape/ingredients");
                ShapedRecipe r = new ShapedRecipe(key, res);
                r.shape(shape.toArray(new String[0]));
                for (String c : ing.getKeys(false)) r.setIngredient(c.charAt(0), choice(ing.getString(c, "")));
                recipe = r;
            }
            case "shapeless" -> {
                ShapelessRecipe r = new ShapelessRecipe(key, res);
                int total = 0;
                for (String spec : s.getStringList("ingredients")) {
                    int n = 1;
                    int i = spec.lastIndexOf(':');
                    if (i > 0 && spec.substring(i + 1).trim().matches("\\d+")) n = Integer.parseInt(spec.substring(i + 1).trim());
                    RecipeChoice c = choice(spec);
                    for (int k = 0; k < n; k++, total++) r.addIngredient(c);
                }
                if (total == 0 || total > 9) throw new IllegalArgumentException("shapeless needs 1-9 ingredients");
                recipe = r;
            }
            case "smelting", "furnace" -> recipe = new FurnaceRecipe(key, res, choice(s.getString("input", "")), (float) s.getDouble("xp", 0.5), s.getInt("time", 200));
            case "blasting" -> recipe = new BlastingRecipe(key, res, choice(s.getString("input", "")), (float) s.getDouble("xp", 0.5), s.getInt("time", 100));
            case "smoking" -> recipe = new SmokingRecipe(key, res, choice(s.getString("input", "")), (float) s.getDouble("xp", 0.5), s.getInt("time", 100));
            case "campfire" -> recipe = new CampfireRecipe(key, res, choice(s.getString("input", "")), (float) s.getDouble("xp", 0.5), s.getInt("time", 600));
            case "stonecutting" -> recipe = new StonecuttingRecipe(key, res, choice(s.getString("input", "")));
            default -> throw new IllegalArgumentException("unknown type '" + type + "'");
        }
        Bukkit.removeRecipe(key);
        if (!Bukkit.addRecipe(recipe)) throw new IllegalStateException("server rejected the recipe");
        keys.add(key);
        return true;
    }
}

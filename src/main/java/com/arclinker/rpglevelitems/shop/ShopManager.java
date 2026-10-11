package com.arclinker.rpglevelitems.shop;

import com.arclinker.rpglevelitems.DataFiles;
import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Server shop with a market:
 * <ul>
 *   <li>Stock capacity per item depends on the number of active players and on the item tier
 *       (high = minerals, medium = renewable raw materials, low = rare items).</li>
 *   <li>The stock is refilled every week; purchase limits reset with it.</li>
 *   <li>Price depends on stock / capacity (scarce = expensive). Players buying lowers the stock, players selling
 *       to the shop raises it, so the market is regulated by supply (weekly refill) and by player activity.</li>
 *   <li>A player can buy at most {@code player_limit} (15%) of an item's capacity per cycle and a whole guild (all
 *       its members together) at most {@code guild_limit} (40%).</li>
 * </ul>
 */
public final class ShopManager {
    public static final class Entry {
        public final String key, category, tier;
        public final double basePrice;
        public int capacity, stock;

        Entry(String key, String category, String tier, double basePrice) {
            this.key = key;
            this.category = category;
            this.tier = tier;
            this.basePrice = basePrice;
        }
    }

    public record Category(String id, String name, Material icon, String tier, List<String> keys) {}

    private final RPGLevelTier plugin;
    private final File stateFile;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<String, Map<UUID, Integer>> boughtPlayer = new HashMap<>();
    private final Map<String, Map<String, Integer>> boughtGuild = new HashMap<>();
    private long nextRefill;
    private int activePlayers = 1;

    // settings
    private final Map<String, Integer> baseCapacity = new HashMap<>();
    private double playersFactor = 0.15, playerLimit = 0.15, guildLimit = 0.40, sellRatio = 0.5, sellOverflow = 1.5;
    private double minMult = 0.75, maxMult = 2.5, exponent = 1.5;
    private int activeDays = 14, refillHour = 4;
    private DayOfWeek refillDay = DayOfWeek.MONDAY;
    private boolean announce = true;

    public ShopManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "shop.yml");
        reload();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() >= nextRefill) refill(announce);
        }, 1200L, 1200L);
    }

    // ---- loading -----------------------------------------------------------------------------

    public void reload() {
        entries.clear();
        categories.clear();
        YamlConfiguration cfg = DataFiles.loadMerged(plugin, "shop");
        ConfigurationSection st = cfg.getConfigurationSection("settings");
        if (st != null) {
            ConfigurationSection cap = st.getConfigurationSection("capacity");
            if (cap != null) for (String k : cap.getKeys(false)) baseCapacity.put(k, cap.getInt(k));
            playersFactor = st.getDouble("players_factor", 0.15);
            activeDays = Math.max(1, st.getInt("active_days", 14));
            playerLimit = st.getDouble("player_limit", 0.15);
            guildLimit = st.getDouble("guild_limit", 0.40);
            sellRatio = st.getDouble("sell_ratio", 0.5);
            sellOverflow = Math.max(1.0, st.getDouble("sell_overflow", 1.5));
            minMult = st.getDouble("price.min_multiplier", 0.75);
            maxMult = st.getDouble("price.max_multiplier", 2.5);
            exponent = st.getDouble("price.exponent", 1.5);
            refillHour = st.getInt("refill_hour", 4);
            announce = st.getBoolean("announce_refill", true);
            try { refillDay = DayOfWeek.valueOf(st.getString("refill_day", "MONDAY").toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { refillDay = DayOfWeek.MONDAY; }
        }
        ConfigurationSection cats = cfg.getConfigurationSection("categories");
        if (cats != null) {
            for (String cid : cats.getKeys(false)) {
                ConfigurationSection c = cats.getConfigurationSection(cid);
                if (c == null) continue;
                String tier = c.getString("stock", "medium");
                List<String> keys = new ArrayList<>();
                ConfigurationSection items = c.getConfigurationSection("items");
                if (items != null) {
                    for (String k : items.getKeys(false)) {
                        String key = k.replace('|', ':');
                        if (create(key, 1) == null) {
                            plugin.getLogger().warning("[Shop] Unknown item '" + key + "' in category " + cid);
                            continue;
                        }
                        entries.put(key, new Entry(key, cid, tier, items.getDouble(k)));
                        keys.add(key);
                    }
                }
                Material icon = Material.matchMaterial(c.getString("icon", "CHEST"));
                categories.put(cid, new Category(cid, c.getString("name", cid), icon == null ? Material.CHEST : icon, tier, keys));
            }
        }
        loadState();
        plugin.getLogger().info("[Shop] " + entries.size() + " items in " + categories.size() + " categories; next refill " + new Date(nextRefill));
    }

    private void loadState() {
        boughtPlayer.clear();
        boughtGuild.clear();
        YamlConfiguration y = stateFile.exists() ? YamlConfiguration.loadConfiguration(stateFile) : new YamlConfiguration();
        nextRefill = y.getLong("next_refill", 0);
        activePlayers = Math.max(1, y.getInt("active_players", countActive()));
        for (Entry e : entries.values()) {
            String k = e.key.replace(':', '|');
            e.capacity = capacityFor(e.tier); // siempre desde la config: cambiar settings.capacity surte efecto al reiniciar
            e.stock = Math.min(y.getInt("stock." + k, e.capacity), (int) Math.floor(e.capacity * sellOverflow));
            ConfigurationSection bp = y.getConfigurationSection("bought_p." + k);
            if (bp != null) {
                Map<UUID, Integer> m = new HashMap<>();
                for (String u : bp.getKeys(false)) { try { m.put(UUID.fromString(u), bp.getInt(u)); } catch (IllegalArgumentException ignored) { } }
                boughtPlayer.put(e.key, m);
            }
            ConfigurationSection bg = y.getConfigurationSection("bought_g." + k);
            if (bg != null) {
                Map<String, Integer> m = new HashMap<>();
                for (String g : bg.getKeys(false)) m.put(g, bg.getInt(g));
                boughtGuild.put(e.key, m);
            }
        }
        if (nextRefill <= 0) {
            nextRefill = computeNextRefill();
            save();
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next_refill", nextRefill);
        y.set("active_players", activePlayers);
        for (Entry e : entries.values()) {
            String k = e.key.replace(':', '|');
            y.set("capacity." + k, e.capacity);
            y.set("stock." + k, e.stock);
            Map<UUID, Integer> bp = boughtPlayer.get(e.key);
            if (bp != null) bp.forEach((u, n) -> y.set("bought_p." + k + "." + u, n));
            Map<String, Integer> bg = boughtGuild.get(e.key);
            if (bg != null) bg.forEach((g, n) -> y.set("bought_g." + k + "." + g, n));
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(stateFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("[Shop] Could not save shop.yml: " + ex.getMessage());
        }
    }

    // ---- stock / refill ---------------------------------------------------------------------------

    private int countActive() {
        long since = System.currentTimeMillis() - activeDays * 86_400_000L;
        Set<UUID> ids = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) ids.add(p.getUniqueId());
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) if (op.getLastPlayed() >= since) ids.add(op.getUniqueId());
        return Math.max(1, ids.size());
    }

    private int capacityFor(String tier) {
        int base = baseCapacity.getOrDefault(tier, 100);
        return Math.max(1, (int) Math.round(base * (1 + playersFactor * activePlayers)));
    }

    private long computeNextRefill() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.with(TemporalAdjusters.nextOrSame(refillDay)).withHour(refillHour).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusWeeks(1);
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** Weekly refill: recounts players, recomputes capacities, fills the stock and resets purchase limits. */
    public void refill(boolean broadcast) {
        activePlayers = countActive();
        for (Entry e : entries.values()) {
            e.capacity = capacityFor(e.tier);
            e.stock = Math.max(e.stock, e.capacity); // surplus sold by players stays: it keeps prices low
        }
        boughtPlayer.clear();
        boughtGuild.clear();
        nextRefill = computeNextRefill();
        save();
        if (broadcast) Bukkit.broadcastMessage("§6§l🛒 ¡La tienda se rellenó! §eNuevo stock según los " + activePlayers + " jugadores activos. §7(/shop)");
    }

    public int activePlayers() {
        return activePlayers;
    }

    public long nextRefill() {
        return nextRefill;
    }

    public String timeToRefill() {
        long ms = Math.max(0, nextRefill - System.currentTimeMillis());
        long d = ms / 86_400_000L, h = (ms / 3_600_000L) % 24, m = (ms / 60_000L) % 60;
        return d > 0 ? d + "d " + h + "h" : h + "h " + m + "m";
    }

    public void setStock(String key, int stock) {
        Entry e = entries.get(key);
        if (e != null) {
            e.stock = Math.max(0, stock);
            save();
        }
    }

    // ---- queries ------------------------------------------------------------------------------------

    public Collection<Category> categories() {
        return categories.values();
    }

    public Category category(String id) {
        return categories.get(id);
    }

    public Entry entry(String key) {
        return entries.get(key);
    }

    public Collection<Entry> all() {
        return entries.values();
    }

    /** Builds the item for a shop key (vanilla material, .rlvp id or gem:<id>:<level>). */
    public ItemStack create(String key, int amount) {
        ItemStack it;
        if (key.startsWith("gem:")) {
            String[] p = key.split(":");
            it = p.length == 3 && plugin.getGemManager() != null ? plugin.getGemManager().item(p[1], safeInt(p[2])) : null;
        } else if (plugin.getRlvpHandler() != null && plugin.getRlvpHandler().getItem(key) != null) {
            it = plugin.getRlvpHandler().getItem(key);
        } else {
            Material m = Material.matchMaterial(key);
            it = m == null || m.isAir() ? null : new ItemStack(m);
        }
        if (it != null) it.setAmount(Math.max(1, amount));
        return it;
    }

    private static int safeInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 1; }
    }

    /** Shop key of an item stack, or null if it is not a shop item (renamed / enchanted vanilla items are rejected). */
    public String keyOf(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        if (plugin.getGemManager() != null) {
            String t = plugin.getGemManager().tokenOf(it);
            if (t != null) return entries.containsKey("gem:" + t) ? "gem:" + t : null;
        }
        String id = plugin.getRlvpHandler() == null ? null : plugin.getRlvpHandler().getId(it);
        if (id != null) return entries.containsKey(id) ? id : null;
        if (!it.isSimilar(new ItemStack(it.getType()))) return null;
        return entries.containsKey(it.getType().name()) ? it.getType().name() : null;
    }

    private double multiplier(double stock, int capacity) {
        double ratio = Math.max(0, Math.min(1, stock / Math.max(1, capacity)));
        return minMult + (maxMult - minMult) * Math.pow(1 - ratio, exponent);
    }

    /** Price of one unit when the stock is {@code stock}. */
    public double unitPrice(Entry e, double stock) {
        return Math.max(1, e.basePrice * multiplier(stock, e.capacity));
    }

    public double buyPrice(Entry e) {
        return Math.ceil(unitPrice(e, e.stock));
    }

    public double sellPrice(Entry e) {
        return Math.max(1, Math.floor(unitPrice(e, e.stock) * sellRatio));
    }

    public int playerLimit(Entry e) {
        return Math.max(1, (int) Math.floor(e.capacity * playerLimit));
    }

    public int guildLimit(Entry e) {
        return Math.max(1, (int) Math.floor(e.capacity * guildLimit));
    }

    public int playerLeft(Entry e, UUID id) {
        return Math.max(0, playerLimit(e) - boughtPlayer.getOrDefault(e.key, Map.of()).getOrDefault(id, 0));
    }

    /** Remaining guild allowance, or -1 if the player has no guild. */
    public int guildLeft(Entry e, Player p) {
        var g = plugin.getGuilds().of(p);
        if (g == null) return -1;
        return Math.max(0, guildLimit(e) - boughtGuild.getOrDefault(e.key, Map.of()).getOrDefault(g.id, 0));
    }

    public double playerLimitPercent() {
        return playerLimit * 100;
    }

    public double guildLimitPercent() {
        return guildLimit * 100;
    }

    // ---- transactions --------------------------------------------------------------------------------

    /** @return an error message (Spanish) or null on success */
    public String buy(Player p, String key, int qty) {
        Entry e = entries.get(key);
        if (e == null) return "Objeto desconocido.";
        if (!p.hasPermission("rpgleveltier.shop")) return "No tienes permiso.";
        int pl = playerLeft(e, p.getUniqueId());
        int gl = guildLeft(e, p);
        int n = Math.min(qty, e.stock);
        if (e.stock <= 0) return "Agotado hasta el próximo relleno (" + timeToRefill() + ").";
        if (pl <= 0) return "Alcanzaste tu límite de compra (" + (int) playerLimitPercent() + "% del stock) de este objeto hasta el relleno.";
        n = Math.min(n, pl);
        if (gl == 0) return "Tu gremio alcanzó su límite de compra (" + (int) guildLimitPercent() + "% del stock) de este objeto.";
        if (gl > 0) n = Math.min(n, gl);
        if (n <= 0) return "No se puede comprar ahora.";
        // sequential price: every unit raises the price a bit
        double total = 0;
        for (int i = 0; i < n; i++) total += Math.ceil(unitPrice(e, e.stock - i));
        if (!plugin.getEconomy().withdraw(p, total)) return "Necesitas " + plugin.getEconomy().format(total) + " (para " + n + ").";
        e.stock -= n;
        boughtPlayer.computeIfAbsent(e.key, k -> new HashMap<>()).merge(p.getUniqueId(), n, Integer::sum);
        var g = plugin.getGuilds().of(p);
        if (g != null) boughtGuild.computeIfAbsent(e.key, k -> new HashMap<>()).merge(g.id, n, Integer::sum);
        int left = n;
        while (left > 0) {
            ItemStack it = create(key, 1);
            int stack = Math.min(left, it.getMaxStackSize());
            it.setAmount(stack);
            for (ItemStack rest : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
            left -= stack;
        }
        save();
        p.sendMessage("§aCompraste §f" + n + "x " + display(key) + " §apor §e" + plugin.getEconomy().format(total) + "§a." + (n < qty ? " §7(limitado por stock o por tus límites)" : ""));
        return null;
    }

    /** Sells up to qty items matching the key from the player's inventory. */
    public String sell(Player p, String key, int qty) {
        Entry e = entries.get(key);
        if (e == null) return "La tienda no compra ese objeto.";
        if (!p.hasPermission("rpgleveltier.shop")) return "No tienes permiso.";
        int maxStock = (int) Math.floor(e.capacity * sellOverflow);
        int room = maxStock - e.stock;
        if (room <= 0) return "El mercado está saturado de este objeto (stock " + e.stock + ").";
        int have = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && key.equals(keyOf(it))) have += it.getAmount();
        int n = Math.min(Math.min(qty, have), room);
        if (n <= 0) return "No tienes ese objeto en el inventario.";
        double total = 0;
        for (int i = 0; i < n; i++) total += Math.max(1, Math.floor(unitPrice(e, e.stock + i) * sellRatio));
        int left = n;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || !key.equals(keyOf(it))) continue;
            int take = Math.min(left, it.getAmount());
            left -= take;
            if (it.getAmount() == take) contents[i] = null; else it.setAmount(it.getAmount() - take);
        }
        p.getInventory().setStorageContents(contents);
        e.stock += n;
        plugin.getEconomy().deposit(p, total);
        save();
        p.sendMessage("§aVendiste §f" + n + "x " + display(key) + " §apor §e" + plugin.getEconomy().format(total) + "§a.");
        return null;
    }

    public String display(String key) {
        ItemStack it = create(key, 1);
        if (it != null && it.hasItemMeta() && it.getItemMeta().hasDisplayName()) return it.getItemMeta().getDisplayName();
        String raw = key.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}

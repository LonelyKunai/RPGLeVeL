package com.arclinker.rpglevelitems.rank;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * In-memory store of every player's data. Persistence backends ({@code storage.type}):
 * <ul>
 *   <li>yaml: {@code players.yml} (default) with rolling backups in {@code backups/}</li>
 *   <li>sqlite / mysql: table {@code rlv_players(uuid, data)}, only changed records are written (asynchronously)</li>
 * </ul>
 */
public final class PlayerStore {
    private final RPGLevelTier plugin;
    private final File file;
    private final Map<UUID, PlayerRecord> records = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastSaved = new ConcurrentHashMap<>();
    private boolean savePending;
    private volatile boolean dirty;
    private String backend = "yaml";
    private Connection sql;

    public PlayerStore(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        backend = plugin.getConfig().getString("storage.type", "yaml").toLowerCase(Locale.ROOT);
        if (!backend.equals("yaml") && !openSql()) backend = "yaml";
        if (backend.equals("yaml")) {
            backup();
            loadYaml();
        } else {
            loadSql();
        }
        plugin.getLogger().info("[PlayerStore] " + records.size() + " player records loaded (" + backend + ").");
    }

    public String backendName() {
        return backend;
    }

    // ---- serialization -----------------------------------------------------------------

    private static String serialize(PlayerRecord r) {
        YamlConfiguration y = new YamlConfiguration();
        r.save(y.createSection("p"));
        return y.saveToString();
    }

    private PlayerRecord deserialize(UUID id, String data) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(data);
        } catch (InvalidConfigurationException e) {
            plugin.getLogger().warning("[PlayerStore] Corrupt record for " + id + ": " + e.getMessage());
            return null;
        }
        ConfigurationSection s = y.getConfigurationSection("p");
        return s == null ? null : PlayerRecord.load(id, s);
    }

    // ---- yaml --------------------------------------------------------------------------

    private void loadYaml() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                records.put(id, PlayerRecord.load(id, root.getConfigurationSection(key)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[PlayerStore] Invalid uuid in players.yml: " + key);
            }
        }
    }

    private void saveYaml() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (PlayerRecord r : records.values()) r.save(cfg.createSection("players." + r.uuid));
        try {
            plugin.getDataFolder().mkdirs();
            File tmp = new File(plugin.getDataFolder(), "players.yml.tmp");
            cfg.save(tmp);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().severe("[PlayerStore] Could not save players.yml: " + e.getMessage());
        }
    }

    /** Daily copy of players.yml into backups/ (keeps storage.backups.keep files). */
    public void backup() {
        if (!backend.equals("yaml") || !plugin.getConfig().getBoolean("storage.backups.enabled", true) || !file.exists()) return;
        File dir = new File(plugin.getDataFolder(), "backups");
        dir.mkdirs();
        long every = plugin.getConfig().getLong("storage.backups.interval_hours", 24) * 3_600_000L;
        File[] existing = dir.listFiles((d, n) -> n.startsWith("players-") && n.endsWith(".yml"));
        long newest = 0;
        if (existing != null) for (File f : existing) newest = Math.max(newest, f.lastModified());
        if (System.currentTimeMillis() - newest < every) return;
        try {
            File out = new File(dir, "players-" + new SimpleDateFormat("yyyyMMdd-HHmm").format(new java.util.Date()) + ".yml");
            Files.copy(file.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            File[] all = dir.listFiles((d, n) -> n.startsWith("players-") && n.endsWith(".yml"));
            if (all != null) {
                Arrays.sort(all, Comparator.comparingLong(File::lastModified).reversed());
                for (int i = plugin.getConfig().getInt("storage.backups.keep", 7); i < all.length; i++) all[i].delete();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[PlayerStore] Backup failed: " + e.getMessage());
        }
    }

    // ---- sql ---------------------------------------------------------------------------

    private boolean openSql() {
        try {
            if (sql != null) { try { sql.close(); } catch (SQLException ignored) { } sql = null; }
            if (backend.equals("sqlite")) {
                plugin.getDataFolder().mkdirs();
                sql = DriverManager.getConnection("jdbc:sqlite:" + new File(plugin.getDataFolder(), "players.db").getAbsolutePath());
            } else if (backend.equals("mysql")) {
                var c = plugin.getConfig();
                sql = DriverManager.getConnection("jdbc:mysql://" + c.getString("storage.mysql.host", "localhost") + ":" + c.getInt("storage.mysql.port", 3306)
                        + "/" + c.getString("storage.mysql.database", "rpgleveltier") + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
                        c.getString("storage.mysql.user", "root"), c.getString("storage.mysql.password", ""));
            } else {
                return false;
            }
            try (Statement st = sql.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS rlv_players (uuid VARCHAR(36) PRIMARY KEY, data MEDIUMTEXT NOT NULL, updated BIGINT NOT NULL)");
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[PlayerStore] Could not open " + backend + " database (" + e.getMessage() + "); using players.yml.");
            sql = null;
            return false;
        }
    }

    private void loadSql() {
        try (Statement st = sql.createStatement(); ResultSet rs = st.executeQuery("SELECT uuid, data FROM rlv_players")) {
            while (rs.next()) {
                try {
                    UUID id = UUID.fromString(rs.getString(1));
                    PlayerRecord r = deserialize(id, rs.getString(2));
                    if (r != null) {
                        records.put(id, r);
                        lastSaved.put(id, rs.getString(2));
                    }
                } catch (IllegalArgumentException ignored) { }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[PlayerStore] SQL load failed: " + e.getMessage());
        }
        // one-time import of an existing players.yml
        if (records.isEmpty() && file.exists()) {
            loadYaml();
            dirty = true;
            plugin.getLogger().info("[PlayerStore] Imported " + records.size() + " records from players.yml into " + backend + ".");
        }
    }

    private void writeSql(Map<UUID, String> changed) {
        if (changed.isEmpty() || sql == null) return;
        try {
            if (!sql.isValid(2)) {
                plugin.getLogger().warning("[PlayerStore] SQL connection lost; reconnecting...");
                openSql();
            }
        } catch (SQLException e) {
            dirty = true;
            return;
        }
        if (sql == null) {
            dirty = true;
            return;
        }
        String upsert = backend.equals("mysql")
                ? "INSERT INTO rlv_players (uuid, data, updated) VALUES (?,?,?) ON DUPLICATE KEY UPDATE data=VALUES(data), updated=VALUES(updated)"
                : "INSERT OR REPLACE INTO rlv_players (uuid, data, updated) VALUES (?,?,?)";
        synchronized (sql) {
            try (PreparedStatement ps = sql.prepareStatement(upsert)) {
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, String> e : changed.entrySet()) {
                    ps.setString(1, e.getKey().toString());
                    ps.setString(2, e.getValue());
                    ps.setLong(3, now);
                    ps.addBatch();
                }
                ps.executeBatch();
                lastSaved.putAll(changed); // only after a successful write
            } catch (SQLException e) {
                plugin.getLogger().severe("[PlayerStore] SQL save failed: " + e.getMessage());
                dirty = true;
            }
        }
    }

    // ---- API ---------------------------------------------------------------------------

    public PlayerRecord get(UUID uuid) {
        return records.computeIfAbsent(uuid, PlayerRecord::new);
    }

    public PlayerRecord get(Player player) {
        PlayerRecord r = get(player.getUniqueId());
        if (!player.getName().equals(r.name)) {
            r.name = player.getName();
            dirty = true;
        }
        return r;
    }

    /** Record of a player if one exists (never creates an empty one). */
    public PlayerRecord peek(UUID uuid) {
        return records.get(uuid);
    }

    /** Debounced save (used when players quit): one write a few seconds later instead of one per quit. */
    public void saveSoon() {
        dirty = true;
        if (savePending || !plugin.isEnabled()) return;
        savePending = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            savePending = false;
            save();
        }, 100L);
    }

    public Collection<PlayerRecord> all() {
        return records.values();
    }

    public void markDirty() {
        dirty = true;
    }

    /** Saves if something changed. SQL writes run asynchronously; call {@link #saveNow()} on shutdown. */
    public synchronized void save() {
        if (!dirty) return;
        dirty = false;
        if (backend.equals("yaml")) {
            saveYaml();
            return;
        }
        Map<UUID, String> changed = new HashMap<>();
        for (PlayerRecord r : records.values()) {
            String data = serialize(r);
            if (!data.equals(lastSaved.get(r.uuid))) changed.put(r.uuid, data);
        }
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeSql(changed));
        else writeSql(changed);
    }

    public void saveNow() {
        save();
        if (sql != null) {
            try {
                synchronized (sql) {
                    sql.close();
                }
            } catch (SQLException ignored) { }
        }
    }
}

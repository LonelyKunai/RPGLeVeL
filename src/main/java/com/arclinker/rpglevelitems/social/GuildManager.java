package com.arclinker.rpglevelitems.social;

import com.arclinker.rpglevelitems.RPGLevelTier;
import com.arclinker.rpglevelitems.rank.PlayerRecord;
import com.arclinker.rpglevelitems.stats.Stats;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Guilds: members, ranks (LEADER/OFFICER/MEMBER), a level that grows with member activity (guild XP) and
 * gives stat bonuses to every member, a coin bank and a shared chest. Stored in guilds.yml.
 */
public final class GuildManager implements Listener {
    public enum Role { MEMBER, OFFICER, LEADER }

    public static final class Guild {
        public final String id;
        public String name, tag;
        public UUID leader;
        public int level = 1;
        public long xp = 0;
        public long bank = 0;
        public final Map<UUID, Role> members = new LinkedHashMap<>();
        public final ItemStack[] chest = new ItemStack[27];

        Guild(String id) {
            this.id = id;
        }
    }

    private final class ChestHolder implements InventoryHolder {
        final Guild guild;
        Inventory inv;

        ChestHolder(Guild guild) {
            this.guild = guild;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    private final RPGLevelTier plugin;
    private final File file;
    private final Map<String, Guild> guilds = new LinkedHashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();

    public GuildManager(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "guilds.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getStatEngine().addProvider(this::bonus);
    }

    // ---- persistence -----------------------------------------------------------------------

    private void load() {
        guilds.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("guilds");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Guild g = new Guild(id);
            g.name = s.getString("name", id);
            g.tag = s.getString("tag", "???");
            try { g.leader = UUID.fromString(s.getString("leader", "")); } catch (IllegalArgumentException e) { continue; }
            g.level = s.getInt("level", 1);
            g.xp = s.getLong("xp", 0);
            g.bank = s.getLong("bank", 0);
            ConfigurationSection m = s.getConfigurationSection("members");
            if (m != null) {
                for (String u : m.getKeys(false)) {
                    try { g.members.put(UUID.fromString(u), Role.valueOf(m.getString(u, "MEMBER"))); } catch (IllegalArgumentException ignored) { }
                }
            }
            List<?> chest = s.getList("chest");
            if (chest != null) for (int i = 0; i < Math.min(27, chest.size()); i++) if (chest.get(i) instanceof ItemStack it) g.chest[i] = it;
            guilds.put(id, g);
        }
        plugin.getLogger().info("[GuildManager] Loaded " + guilds.size() + " guilds.");
    }

    /** Copies the live (open) chest inventory of every guild into the stored arrays. */
    private void syncOpenChests() {
        for (Map.Entry<String, Inventory> e : openChests.entrySet()) {
            Guild g = guilds.get(e.getKey());
            if (g == null) continue;
            ItemStack[] c = e.getValue().getContents();
            for (int i = 0; i < 27; i++) g.chest[i] = i < c.length ? c[i] : null;
        }
    }

    /** Stores and closes the shared chest of a guild for everybody (before disband / membership changes). */
    private void closeChest(Guild g, Player onlyFor) {
        Inventory inv = openChests.get(g.id);
        if (inv == null) return;
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < 27; i++) g.chest[i] = i < c.length ? c[i] : null;
        if (onlyFor == null) {
            openChests.remove(g.id);
            for (var v : new ArrayList<>(inv.getViewers())) v.closeInventory();
        } else if (inv.getViewers().contains(onlyFor)) {
            onlyFor.closeInventory();
        }
    }

    public void save() {
        syncOpenChests();
        YamlConfiguration y = new YamlConfiguration();
        for (Guild g : guilds.values()) {
            String p = "guilds." + g.id + ".";
            y.set(p + "name", g.name);
            y.set(p + "tag", g.tag);
            y.set(p + "leader", g.leader.toString());
            y.set(p + "level", g.level);
            y.set(p + "xp", g.xp);
            y.set(p + "bank", g.bank);
            for (Map.Entry<UUID, Role> e : g.members.entrySet()) y.set(p + "members." + e.getKey(), e.getValue().name());
            y.set(p + "chest", Arrays.asList(g.chest));
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[GuildManager] Could not save guilds.yml: " + e.getMessage());
        }
    }

    // ---- queries -----------------------------------------------------------------------------

    public Collection<Guild> all() {
        return guilds.values();
    }

    public Guild byId(String id) {
        return id == null ? null : guilds.get(id.toLowerCase(Locale.ROOT));
    }

    public Guild of(UUID id) {
        String gid = plugin.getPlayerStore().get(id).guild;
        return gid.isEmpty() ? null : guilds.get(gid);
    }

    public Guild of(Player p) {
        return of(p.getUniqueId());
    }

    public Role role(Guild g, UUID id) {
        return g.members.getOrDefault(id, Role.MEMBER);
    }

    public int maxMembers(Guild g) {
        return Math.min(plugin.getConfig().getInt("guild.max_members_cap", 40), plugin.getConfig().getInt("guild.base_members", 10) + 2 * (g.level - 1));
    }

    public long xpForNext(Guild g) {
        return (long) (plugin.getConfig().getLong("guild.xp_base", 1000) * Math.pow(g.level, 1.4));
    }

    public String tagOf(Player p) {
        Guild g = of(p);
        return g == null ? "" : "§7[§b" + g.tag + "§7] ";
    }

    private Map<String, Double> bonus(Player p) {
        Map<String, Double> out = new LinkedHashMap<>();
        Guild g = of(p);
        if (g == null) return out;
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("guild.stats_per_level");
        if (c == null) return out;
        for (String k : c.getKeys(false)) if (Stats.isKnown(k)) Stats.add(out, k, c.getDouble(k) * (g.level - 1));
        return out;
    }

    // ---- operations ----------------------------------------------------------------------------

    public String create(Player p, String name, String tag) {
        if (of(p) != null) return "Ya estás en un gremio.";
        String id = name.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_]{3,16}")) return "El nombre debe tener 3-16 letras/números/_.";
        if (!tag.matches("[A-Za-z0-9]{2,4}")) return "La etiqueta debe tener 2-4 letras/números.";
        if (guilds.containsKey(id)) return "Ya existe un gremio con ese nombre.";
        for (Guild g : guilds.values()) if (g.tag.equalsIgnoreCase(tag)) return "Esa etiqueta ya está en uso.";
        double cost = plugin.getConfig().getDouble("guild.create_cost", 1000);
        if (!plugin.getEconomy().withdraw(p, cost)) return "Crear un gremio cuesta " + plugin.getEconomy().format(cost) + ".";
        Guild g = new Guild(id);
        g.name = name;
        g.tag = tag.toUpperCase(Locale.ROOT);
        g.leader = p.getUniqueId();
        g.members.put(p.getUniqueId(), Role.LEADER);
        guilds.put(id, g);
        plugin.getPlayerStore().get(p).guild = id;
        plugin.getPlayerStore().markDirty();
        save();
        return null;
    }

    public String invite(Player by, Player target) {
        Guild g = of(by);
        if (g == null) return "No tienes gremio.";
        if (role(g, by.getUniqueId()) == Role.MEMBER) return "Solo oficiales y el líder pueden invitar.";
        if (of(target) != null) return target.getName() + " ya tiene gremio.";
        if (g.members.size() >= maxMembers(g)) return "El gremio está lleno (" + maxMembers(g) + ").";
        invites.put(target.getUniqueId(), g.id);
        target.sendMessage("§b" + by.getName() + " te invitó al gremio §f" + g.name + "§b. §e/guild accept");
        Bukkit.getScheduler().runTaskLater(plugin, () -> invites.remove(target.getUniqueId(), g.id), 20L * 90);
        return null;
    }

    public String accept(Player p) {
        String gid = invites.remove(p.getUniqueId());
        Guild g = gid == null ? null : guilds.get(gid);
        if (g == null) return "No tienes invitaciones.";
        if (of(p) != null) return "Ya tienes gremio.";
        if (g.members.size() >= maxMembers(g)) return "El gremio está lleno.";
        g.members.put(p.getUniqueId(), Role.MEMBER);
        plugin.getPlayerStore().get(p).guild = g.id;
        plugin.getPlayerStore().markDirty();
        save();
        broadcast(g, "§b" + p.getName() + " se unió al gremio.");
        return null;
    }

    public String leave(Player p) {
        Guild g = of(p);
        if (g == null) return "No tienes gremio.";
        if (g.leader.equals(p.getUniqueId())) return "El líder debe usar /guild disband o transferir el liderazgo (/guild promote).";
        closeChest(g, p);
        g.members.remove(p.getUniqueId());
        plugin.getPlayerStore().get(p).guild = "";
        plugin.getPlayerStore().markDirty();
        save();
        broadcast(g, "§e" + p.getName() + " salió del gremio.");
        return null;
    }

    public String kick(Player by, String name) {
        Guild g = of(by);
        if (g == null) return "No tienes gremio.";
        if (role(g, by.getUniqueId()) == Role.MEMBER) return "Sin permiso.";
        UUID target = null;
        for (UUID u : g.members.keySet()) if (plugin.getPlayerStore().get(u).name.equalsIgnoreCase(name)) target = u;
        if (target == null) return "No es miembro.";
        if (role(g, target).ordinal() >= role(g, by.getUniqueId()).ordinal()) return "No puedes expulsar a alguien de rango igual o superior.";
        Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) closeChest(g, kicked);
        g.members.remove(target);
        plugin.getPlayerStore().get(target).guild = "";
        plugin.getPlayerStore().markDirty();
        save();
        broadcast(g, "§e" + name + " fue expulsado del gremio.");
        return null;
    }

    /** Promotes MEMBER→OFFICER; promoting an officer transfers the leadership. */
    public String promote(Player by, String name) {
        Guild g = of(by);
        if (g == null || !g.leader.equals(by.getUniqueId())) return "Solo el líder puede ascender.";
        UUID target = null;
        for (UUID u : g.members.keySet()) if (plugin.getPlayerStore().get(u).name.equalsIgnoreCase(name)) target = u;
        if (target == null || target.equals(by.getUniqueId())) return "Miembro no válido.";
        if (role(g, target) == Role.MEMBER) {
            g.members.put(target, Role.OFFICER);
            broadcast(g, "§b" + name + " ahora es oficial.");
        } else {
            g.members.put(target, Role.LEADER);
            g.members.put(by.getUniqueId(), Role.OFFICER);
            g.leader = target;
            broadcast(g, "§6" + name + " es el nuevo líder del gremio.");
        }
        save();
        return null;
    }

    public String demote(Player by, String name) {
        Guild g = of(by);
        if (g == null || !g.leader.equals(by.getUniqueId())) return "Solo el líder puede degradar.";
        for (UUID u : g.members.keySet()) {
            if (plugin.getPlayerStore().get(u).name.equalsIgnoreCase(name) && role(g, u) == Role.OFFICER) {
                g.members.put(u, Role.MEMBER);
                save();
                broadcast(g, "§e" + name + " ya no es oficial.");
                return null;
            }
        }
        return "Ese jugador no es oficial.";
    }

    public String disband(Player by) {
        Guild g = of(by);
        if (g == null || !g.leader.equals(by.getUniqueId())) return "Solo el líder puede disolver el gremio.";
        closeChest(g, null); // flush the live chest first so the leader gets the real contents
        broadcast(g, "§cEl gremio §f" + g.name + " §cfue disuelto.");
        for (UUID u : g.members.keySet()) plugin.getPlayerStore().get(u).guild = "";
        plugin.getEconomy().deposit(by, g.bank);
        for (ItemStack it : g.chest) if (it != null) for (ItemStack r : by.getInventory().addItem(it).values()) by.getWorld().dropItemNaturally(by.getLocation(), r);
        guilds.remove(g.id);
        plugin.getPlayerStore().markDirty();
        save();
        return null;
    }

    public String deposit(Player p, long amount) {
        Guild g = of(p);
        if (g == null) return "No tienes gremio.";
        if (amount < 1) return "Cantidad inválida.";
        if (!plugin.getEconomy().withdraw(p, amount)) return "No tienes suficiente dinero.";
        g.bank += amount;
        save();
        return null;
    }

    public String withdraw(Player p, long amount) {
        Guild g = of(p);
        if (g == null) return "No tienes gremio.";
        if (role(g, p.getUniqueId()) == Role.MEMBER) return "Solo oficiales y el líder pueden retirar.";
        if (amount < 1 || amount > g.bank) return "Cantidad inválida (banco: " + g.bank + ").";
        g.bank -= amount;
        plugin.getEconomy().deposit(p, amount);
        save();
        return null;
    }

    public void broadcast(Guild g, String msg) {
        for (UUID u : g.members.keySet()) {
            Player m = Bukkit.getPlayer(u);
            if (m != null) m.sendMessage(msg);
        }
    }

    public void chat(Player p, String msg) {
        Guild g = of(p);
        if (g == null) {
            p.sendMessage("§cNo tienes gremio.");
            return;
        }
        broadcast(g, "§3[Gremio] §f" + p.getName() + "§7: §f" + msg);
    }

    /** Adds guild XP (called when members earn combat / quest XP). */
    public void addXp(Player p, long amount) {
        Guild g = of(p);
        if (g == null || amount <= 0) return;
        g.xp += amount;
        boolean up = false;
        while (g.xp >= xpForNext(g)) {
            g.xp -= xpForNext(g);
            g.level++;
            up = true;
        }
        if (up) {
            broadcast(g, "§6§l★ ¡El gremio " + g.name + " subió al nivel " + g.level + "!");
            save();
        }
    }

    public void openChest(Player p) {
        Guild g = of(p);
        if (g == null) {
            p.sendMessage("§cNo tienes gremio.");
            return;
        }
        Inventory inv = openChests.get(g.id);
        if (inv == null) {
            ChestHolder h = new ChestHolder(g);
            inv = Bukkit.createInventory(h, 27, "§3Cofre del gremio §8- " + g.name);
            h.inv = inv;
            inv.setContents(g.chest.clone());
            openChests.put(g.id, inv); // shared by every viewer so edits never overwrite each other
        }
        p.openInventory(inv);
    }

    private final Map<String, Inventory> openChests = new HashMap<>();

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof ChestHolder h)) return;
        ItemStack[] c = e.getInventory().getContents();
        for (int i = 0; i < 27; i++) h.guild.chest[i] = i < c.length ? c[i] : null;
        if (e.getViewers().size() <= 1) openChests.remove(h.guild.id);
        save();
    }
}

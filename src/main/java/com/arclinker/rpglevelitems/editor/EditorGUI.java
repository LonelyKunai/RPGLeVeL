package com.arclinker.rpglevelitems.editor;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * In-game editor for every data file ({@code plugins/RPGLeVeL/data/**}.yml / .rlvp): browse folders and files,
 * navigate sections, click a value and type the new one in chat. Saves a .bak copy and reloads the plugin data.
 * (YAML comments are not preserved when a file is saved.)
 */
public final class EditorGUI implements Listener {
    private enum View { ROOT, FOLDER, FILE }

    private static final class Holder implements InventoryHolder {
        View view = View.ROOT;
        File dir;
        File file;
        String path = "";
        int page = 0;
        final List<String> keys = new ArrayList<>();
        Inventory inv;

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }

        Holder copy() {
            Holder h = new Holder();
            h.view = view; h.dir = dir; h.file = file; h.path = path; h.page = page;
            return h;
        }
    }

    private record Pending(File file, String path, Object old) {}

    private final RPGLevelTier plugin;
    private final File root;
    private final Map<UUID, Pending> pending = new java.util.concurrent.ConcurrentHashMap<>();

    public EditorGUI(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.root = new File(plugin.getDataFolder(), "data");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean inside(File f) {
        try {
            return f.getCanonicalPath().startsWith(root.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static ItemStack icon(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public void open(Player p) {
        show(p, new Holder());
    }

    private void show(Player p, Holder h) {
        List<String> keys = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        String title;
        YamlConfiguration cfg = null;
        switch (h.view) {
            case ROOT -> {
                title = "Editor: data/";
                File[] dirs = root.listFiles(File::isDirectory);
                if (dirs != null) {
                    Arrays.sort(dirs);
                    for (File d : dirs) {
                        keys.add(d.getName());
                        icons.add(icon(Material.CHEST, "§e" + d.getName() + "/", "§7Clic para abrir"));
                    }
                }
            }
            case FOLDER -> {
                title = "Editor: " + rel(h.dir);
                File[] list = h.dir.listFiles();
                if (list != null) {
                    Arrays.sort(list, Comparator.comparing((File f) -> !f.isDirectory()).thenComparing(File::getName));
                    for (File f : list) {
                        if (f.isDirectory()) {
                            keys.add(f.getName());
                            icons.add(icon(Material.CHEST, "§e" + f.getName() + "/", "§7Carpeta"));
                        } else if (f.getName().endsWith(".yml") || f.getName().endsWith(".rlvp")) {
                            keys.add(f.getName());
                            icons.add(icon(Material.PAPER, "§f" + f.getName(), "§7" + f.length() / 1024 + " KB", "§eClic para editar"));
                        }
                    }
                }
            }
            default -> {
                title = h.file.getName() + (h.path.isEmpty() ? "" : " > " + h.path);
                cfg = YamlConfiguration.loadConfiguration(h.file);
                ConfigurationSection sec = h.path.isEmpty() ? cfg : cfg.getConfigurationSection(h.path);
                if (sec != null) {
                    for (String k : sec.getKeys(false)) {
                        Object v = sec.get(k);
                        keys.add(k);
                        if (v instanceof ConfigurationSection cs) {
                            icons.add(icon(Material.CHEST, "§e" + k, "§7" + cs.getKeys(false).size() + " entradas", "§eClic para abrir"));
                        } else if (v instanceof List<?> l) {
                            boolean complex = l.stream().anyMatch(x -> x instanceof Map || x instanceof List);
                            icons.add(icon(Material.BOOK, "§b" + k, "§7Lista (" + l.size() + "): §f" + preview(l), complex ? "§8Contiene estructuras: solo lectura" : "§eClic para reemplazar (separa con |)"));
                        } else {
                            icons.add(icon(v instanceof Boolean ? Material.LEVER : v instanceof Number ? Material.CLOCK : Material.NAME_TAG, "§a" + k,
                                    "§7Valor: §f" + String.valueOf(v).replace('§', '&'), "§8Tipo: " + (v == null ? "?" : v.getClass().getSimpleName()), "§eClic para cambiar"));
                        }
                    }
                }
            }
        }
        int pages = Math.max(1, (keys.size() + 44) / 45);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        h.keys.clear();
        Inventory inv = Bukkit.createInventory(h, 54, "§6" + (title.length() > 48 ? title.substring(0, 48) : title) + " §8" + (h.page + 1) + "/" + pages);
        h.inv = inv;
        for (int i = 0; i < 45 && h.page * 45 + i < keys.size(); i++) {
            inv.setItem(i, icons.get(h.page * 45 + i));
            h.keys.add(keys.get(h.page * 45 + i));
        }
        ItemStack fill = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 45; s < 54; s++) inv.setItem(s, fill);
        if (h.page > 0) inv.setItem(45, icon(Material.ARROW, "§ePágina anterior"));
        if (h.page < pages - 1) inv.setItem(53, icon(Material.ARROW, "§ePágina siguiente"));
        if (h.view != View.ROOT) inv.setItem(48, icon(Material.OAK_DOOR, "§eVolver"));
        inv.setItem(49, icon(Material.EMERALD, "§aRecargar datos", "§7Aplica los cambios guardados"));
        inv.setItem(50, icon(Material.BOOK, "§6Ayuda", "§7Clic en un valor y escribe el nuevo en el chat.", "§7Escribe §fcancelar §7para salir.", "§8Se guarda una copia .bak del archivo."));
        inv.setItem(52, icon(Material.BARRIER, "§cCerrar"));
        p.openInventory(inv);
    }

    private String rel(File f) {
        return root.toPath().getParent().relativize(f.toPath()).toString().replace('\\', '/');
    }

    private static String preview(List<?> l) {
        String s = String.valueOf(l);
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!p.hasPermission("rpgleveltier.admin")) return;
        int slot = e.getRawSlot();
        if (slot == 52) { p.closeInventory(); return; }
        if (slot == 45 && h.page > 0) { Holder n = h.copy(); n.page--; show(p, n); return; }
        if (slot == 53 && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) { Holder n = h.copy(); n.page++; show(p, n); return; }
        if (slot == 49) {
            plugin.reloadAll();
            p.sendMessage("§aDatos recargados.");
            return;
        }
        if (slot == 48 && h.view != View.ROOT) {
            Holder n = h.copy();
            n.page = 0;
            if (h.view == View.FILE) {
                if (!h.path.isEmpty()) {
                    int i = h.path.lastIndexOf('.');
                    n.path = i < 0 ? "" : h.path.substring(0, i);
                } else {
                    n.view = View.FOLDER;
                    n.dir = h.file.getParentFile();
                    n.file = null;
                }
            } else {
                File parent = h.dir.getParentFile();
                if (parent == null || parent.equals(root) || !inside(parent)) { n.view = View.ROOT; n.dir = null; } else n.dir = parent;
            }
            show(p, n);
            return;
        }
        if (slot < 0 || slot >= h.keys.size()) return;
        String key = h.keys.get(slot);
        Holder n = h.copy();
        n.page = 0;
        switch (h.view) {
            case ROOT -> {
                n.view = View.FOLDER;
                n.dir = new File(root, key);
                show(p, n);
            }
            case FOLDER -> {
                File f = new File(h.dir, key);
                if (!inside(f)) return;
                if (f.isDirectory()) { n.dir = f; } else { n.view = View.FILE; n.file = f; n.path = ""; }
                show(p, n);
            }
            default -> {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(h.file);
                String full = h.path.isEmpty() ? key : h.path + "." + key;
                Object v = cfg.get(full);
                if (v instanceof ConfigurationSection) {
                    n.path = full;
                    show(p, n);
                } else if (v instanceof List<?> l && l.stream().anyMatch(x -> x instanceof Map || x instanceof List)) {
                    p.sendMessage("§cEsa lista contiene estructuras (mapas/listas): edítala a mano en el archivo; el editor solo cambia listas simples.");
                } else {
                    pending.put(p.getUniqueId(), new Pending(h.file, full, v));
                    p.closeInventory();
                    p.sendMessage("§6Editando §f" + full + " §7en " + h.file.getName() + " §7(valor actual: §f" + String.valueOf(v).replace('§', '&') + "§7)");
                    p.sendMessage("§eEscribe el nuevo valor en el chat (colores con &). §7'cancelar' para salir" + (v instanceof List ? "; elementos separados por |" : "") + ".");
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Pending pe = pending.remove(e.getPlayer().getUniqueId());
        if (pe == null) return;
        e.setCancelled(true);
        String msg = e.getMessage();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> apply(p, pe, msg));
    }

    @SuppressWarnings("unchecked")
    private void apply(Player p, Pending pe, String msg) {
        if (msg.equalsIgnoreCase("cancelar") || msg.equalsIgnoreCase("cancel")) {
            p.sendMessage("§eEdición cancelada.");
            return;
        }
        if (!inside(pe.file())) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(pe.file());
        Object old = pe.old();
        Object val;
        try {
            if (old instanceof Boolean) val = Boolean.parseBoolean(msg.trim());
            else if (old instanceof Integer) val = Integer.parseInt(msg.trim());
            else if (old instanceof Long) val = Long.parseLong(msg.trim());
            else if (old instanceof Double) val = Double.parseDouble(msg.trim().replace(',', '.'));
            else if (old instanceof List<?> l) {
                List<Object> out = new ArrayList<>();
                boolean numbers = !l.isEmpty() && l.get(0) instanceof Number;
                for (String part : msg.split("\\|")) {
                    String t = ChatColor.translateAlternateColorCodes('&', part.trim());
                    out.add(numbers ? (Object) Double.valueOf(t) : t);
                }
                val = out;
            } else val = ChatColor.translateAlternateColorCodes('&', msg);
        } catch (NumberFormatException ex) {
            p.sendMessage("§cValor inválido para el tipo " + old.getClass().getSimpleName() + ". Edición cancelada.");
            return;
        }
        try {
            Files.copy(pe.file().toPath(), new File(pe.file().getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            cfg.set(pe.path(), val);
            cfg.save(pe.file());
        } catch (IOException ex) {
            p.sendMessage("§cNo se pudo guardar: " + ex.getMessage());
            return;
        }
        plugin.reloadAll();
        p.sendMessage("§a" + pe.path() + " = §f" + String.valueOf(val).replace('§', '&') + " §7(guardado y recargado)");
        Holder h = new Holder();
        h.view = View.FILE;
        h.file = pe.file();
        int i = pe.path().lastIndexOf('.');
        h.path = i < 0 ? "" : pe.path().substring(0, i);
        show(p, h);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }
}

package com.arclinker.rpglevelitems.itemcreator;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Keeps .rlvp identity on placeable items: a placed .rlvp block remembers its id (stored in the
 * chunk) and drops the real .rlvp item again (with its custom model data) instead of a vanilla one.
 */
public final class RlvpBlockListener implements Listener {
    private final RPGLevelTier plugin;
    private final NamespacedKey blocksKey;

    public RlvpBlockListener(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.blocksKey = new NamespacedKey(RPGLevelTier.KEY_NS, "rlvp_blocks");
    }

    private static String pos(Block b) {
        return (b.getX() & 15) + "," + b.getY() + "," + (b.getZ() & 15);
    }

    private List<String> read(Chunk c) {
        List<String> l = c.getPersistentDataContainer().get(blocksKey, PersistentDataType.LIST.strings());
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    private void write(Chunk c, List<String> l) {
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (l.isEmpty()) pdc.remove(blocksKey); else pdc.set(blocksKey, PersistentDataType.LIST.strings(), l);
    }

    /** id of the .rlvp block at the position, or null. */
    public String getBlockId(Block b) {
        return idAt(b);
    }

    private String idAt(Block b) {
        String p = pos(b) + "|";
        for (String e : read(b.getChunk())) if (e.startsWith(p)) return e.substring(p.length());
        return null;
    }

    private String remove(Block b) {
        List<String> l = read(b.getChunk());
        String p = pos(b) + "|", found = null;
        for (Iterator<String> it = l.iterator(); it.hasNext(); ) {
            String e = it.next();
            if (e.startsWith(p)) { found = e.substring(p.length()); it.remove(); }
        }
        if (found != null) write(b.getChunk(), l);
        return found;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getRlvpHandler() == null) return;
        String id = plugin.getRlvpHandler().getId(event.getItemInHand());
        if (id == null) return;
        Block b = event.getBlockPlaced();
        List<String> l = read(b.getChunk());
        l.removeIf(e -> e.startsWith(pos(b) + "|"));
        l.add(pos(b) + "|" + id);
        write(b.getChunk(), l);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        String id = idAt(b);
        if (id == null) return;
        remove(b);
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        ItemStack item = plugin.getRlvpHandler() == null ? null : plugin.getRlvpHandler().getItem(id);
        if (item == null) return;
        event.setDropItems(false);
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item);
    }

    private void dropAll(List<Block> blocks, boolean explosion) {
        for (Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            String id = idAt(b);
            if (id == null) continue;
            remove(b);
            ItemStack item = plugin.getRlvpHandler() == null ? null : plugin.getRlvpHandler().getItem(id);
            if (item == null) continue;
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            b.setType(org.bukkit.Material.AIR);
            at.getWorld().dropItemNaturally(at, item);
            it.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        dropAll(event.blockList(), true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        dropAll(event.blockList(), true);
    }

    /** Pistons would move the block and lose its id; keep it simple and block that. */
    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) if (idAt(b) != null) { event.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) if (idAt(b) != null) { event.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        remove(event.getBlock());
    }
}

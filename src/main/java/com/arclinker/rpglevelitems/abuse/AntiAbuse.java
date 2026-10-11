package com.arclinker.rpglevelitems.abuse;

import com.arclinker.rpglevelitems.RPGLevelTier;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Anti-farm protections for every progress source:
 * <ul>
 *   <li>Blocks placed by players are remembered (per chunk, FIFO) so breaking them gives no job XP / quest progress.</li>
 *   <li>Kills of the same mob type inside a rolling minute are throttled, and spawner mobs give reduced rewards.</li>
 * </ul>
 * Config: {@code anti_abuse.*}.
 */
public final class AntiAbuse implements Listener {
    private static final String SPAWNER_TAG = "rlv_spawner";

    private final RPGLevelTier plugin;
    private final NamespacedKey placedKey;
    private final Map<UUID, Map<EntityType, Deque<Long>>> kills = new HashMap<>();

    public AntiAbuse(RPGLevelTier plugin) {
        this.plugin = plugin;
        this.placedKey = new NamespacedKey(RPGLevelTier.KEY_NS, "placed_blocks");
    }

    // ---- placed blocks --------------------------------------------------------------

    private static long pack(Block b) {
        return ((long) (b.getY() + 128) << 8) | ((long) (b.getX() & 15) << 4) | (b.getZ() & 15);
    }

    private long[] read(Chunk c) {
        long[] a = c.getPersistentDataContainer().get(placedKey, PersistentDataType.LONG_ARRAY);
        return a == null ? new long[0] : a;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("anti_abuse.track_placed_blocks", true)) return;
        Block b = event.getBlockPlaced();
        if (b.getBlockData() instanceof Ageable) return; // planted crops are meant to be harvested
        Chunk c = b.getChunk();
        long[] old = read(c);
        long p = pack(b);
        for (long v : old) if (v == p) return;
        int max = Math.max(64, plugin.getConfig().getInt("anti_abuse.max_tracked_per_chunk", 3000));
        int from = old.length >= max ? old.length - max + 1 : 0;
        long[] now = Arrays.copyOfRange(old, from, old.length + 1);
        now[now.length - 1] = p;
        c.getPersistentDataContainer().set(placedKey, PersistentDataType.LONG_ARRAY, now);
    }

    /** True if a player placed the block (does not forget it). */
    public boolean isPlaced(Block b) {
        long[] old = read(b.getChunk());
        long p = pack(b);
        for (long v : old) if (v == p) return true;
        return false;
    }

    /** True (and forgets the block) if a player placed it: such blocks give no progress when broken. */
    public boolean consumePlaced(Block b) {
        if (!plugin.getConfig().getBoolean("anti_abuse.track_placed_blocks", true)) return false;
        Chunk c = b.getChunk();
        long[] old = read(c);
        long p = pack(b);
        for (int i = 0; i < old.length; i++) {
            if (old[i] != p) continue;
            long[] now = new long[old.length - 1];
            System.arraycopy(old, 0, now, 0, i);
            System.arraycopy(old, i + 1, now, i, old.length - i - 1);
            if (now.length == 0) c.getPersistentDataContainer().remove(placedKey);
            else c.getPersistentDataContainer().set(placedKey, PersistentDataType.LONG_ARRAY, now);
            return true;
        }
        return false;
    }

    // ---- kills ----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) {
            event.getEntity().addScoreboardTag(SPAWNER_TAG);
        }
    }

    /** Reward multiplier (0..1) for a kill, registering it in the player's rolling window. */
    public double killMultiplier(Player killer, LivingEntity dead) {
        double m = 1.0;
        if (dead.getScoreboardTags().contains(SPAWNER_TAG)) {
            m = plugin.getConfig().getDouble("anti_abuse.spawner_multiplier", 0.2);
        }
        int limit = plugin.getConfig().getInt("anti_abuse.kills_per_minute", 25);
        if (limit <= 0) return m;
        long now = System.currentTimeMillis();
        Deque<Long> q = kills.computeIfAbsent(killer.getUniqueId(), k -> new EnumMap<>(EntityType.class))
                .computeIfAbsent(dead.getType(), k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > 60_000L) q.pollFirst();
        q.addLast(now);
        if (q.size() > limit * 2) return 0;
        if (q.size() > limit) m *= plugin.getConfig().getDouble("anti_abuse.over_limit_multiplier", 0.25);
        return m;
    }

    public void forget(UUID id) {
        kills.remove(id);
    }

    /** base * multiplier with probabilistic rounding (so 1 point at 0.25 or x1.5 still pays out fairly). */
    public static long scale(long base, double mult) {
        if (mult <= 0 || base <= 0) return 0;
        double v = base * mult;
        long whole = (long) Math.floor(v);
        return whole + (ThreadLocalRandom.current().nextDouble() < v - whole ? 1 : 0);
    }
}

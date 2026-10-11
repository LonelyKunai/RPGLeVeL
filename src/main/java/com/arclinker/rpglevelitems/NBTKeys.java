package com.arclinker.rpglevelitems;

import org.bukkit.NamespacedKey;

/**
 * Centralized NBT key definitions used across the plugin.
 */
public final class NBTKeys {
    public static final String CLASS = "rlv_class";
    public static final String SUBCLASS = "rlv_subclass";
    public static final String JOB_CLASS = "rlv_job_class"; // class‑specific job
    public static final String JOB_GLOBAL_1 = "rlv_job_1"; // first global job
    public static final String JOB_GLOBAL_2 = "rlv_job_2"; // second global job
    public static final String RARITY = "rlv_rarity"; // rarity name (e.g., COMMON)
    public static final String ITEM_MODEL = "rlv_item_model"; // custom model tag

    private NBTKeys() {}

    /** Helper to create a NamespacedKey from the plugin instance */
    public static NamespacedKey key(RPGLevelTier plugin, String key) {
        return new NamespacedKey(RPGLevelTier.KEY_NS, key);
    }
}


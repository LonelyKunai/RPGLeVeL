package com.arclinker.rpglevelitems;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple language manager supporting multiple languages.
 * Language files are placed in the plugin's data folder under "languages/<code>.yml".
 * The default language is "es" (Spanish).
 */
public class LanguageManager {
    private final JavaPlugin plugin;
    private final Map<String, Map<String, String>> languages = new HashMap<>();
    private String currentLanguage = "es"; // default

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAllLanguages();
    }

    private void loadAllLanguages() {
        // Load built‑in language resources from the jar (if any)
        // Also load external files from the data folder.
        String[] defaultCodes = {"es", "en", "fr", "pt", "zh_cn"};
        for (String code : defaultCodes) {
            loadLanguageFile(code);
        }
    }

    private void loadLanguageFile(String code) {
        try {
            // First try external file in plugin data folder
            java.io.File file = new java.io.File(plugin.getDataFolder(), "languages/" + code + ".yml");
            if (!file.exists()) {
                // If not present, try bundled resource
                InputStream in = plugin.getResource("languages/" + code + ".yml");
                if (in == null) return;
                Yaml yaml = new Yaml();
                Map<String, Object> raw = yaml.load(in);
                Map<String, String> flat = flatten(raw);
                languages.put(code, flat);
                return;
            }
            Yaml yaml = new Yaml();
            try (java.io.InputStream in = new java.io.FileInputStream(file)) {
                Map<String, Object> raw = yaml.load(in);
                languages.put(code, flatten(raw));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[LanguageManager] Failed to load language " + code + ": " + e.getMessage());
        }
    }

    /** Flatten nested YAML into dot‑notation keys */
    private Map<String, String> flatten(Map<String, Object> section) {
        Map<String, String> map = new HashMap<>();
        flattenRecursive("", section, map);
        return map;
    }

    @SuppressWarnings("unchecked")
    private void flattenRecursive(String prefix, Map<String, Object> section, Map<String, String> out) {
        for (Map.Entry<String, Object> e : section.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object val = e.getValue();
            if (val instanceof Map) {
                flattenRecursive(key, (Map<String, Object>) val, out);
            } else {
                out.put(key, String.valueOf(val));
            }
        }
    }

    public void setLanguage(String code) {
        if (languages.containsKey(code)) {
            currentLanguage = code;
        }
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /** Retrieve a message by key, falling back to the default language if missing */
    public String get(String key) {
        Map<String, String> langMap = languages.get(currentLanguage);
        if (langMap != null && langMap.containsKey(key)) {
            return langMap.get(key);
        }
        // fallback to Spanish
        Map<String, String> defaultMap = languages.get("es");
        return defaultMap != null ? defaultMap.getOrDefault(key, "{" + key + "}") : "{" + key + "}";
    }

    /** Language code of a player (stored in the player record), default = server language. */
    public String codeOf(Player p) {
        if (p == null || !(plugin instanceof RPGLevelTier rt)) return currentLanguage;
        String c = rt.getPlayerStore().get(p).language;
        return c == null || c.isEmpty() || !languages.containsKey(c) ? currentLanguage : c;
    }

    public java.util.Set<String> codes() {
        return languages.keySet();
    }

    /** Translated text for a player with {0},{1}... replaced by args. */
    public String tr(Player p, String key, Object... args) {
        Map<String, String> m = languages.get(codeOf(p));
        String t = m != null ? m.get(key) : null;
        if (t == null) {
            Map<String, String> es = languages.get("es");
            t = es != null ? es.get(key) : null;
        }
        if (t == null) return "{" + key + "}";
        for (int i = 0; i < args.length; i++) t = t.replace("{" + i + "}", String.valueOf(args[i]));
        return t;
    }

    /** Send a translated message to a player */
    public void send(Player player, String key) {
        player.sendMessage(get(key));
    }
}


package com.arclinker.rpglevelitems;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every section (items, classes, subclasses, jobs, quests, abilities, stats) lives in its own folder
 * under {@code plugins/RPGLeVeL/data/<section>/}. Bundled files are copied there on first run
 * (never overwriting) and every file of the folder is loaded and merged.
 */
public final class DataFiles {
    public static final String ROOT = "data/";

    private DataFiles() {}

    /** Copies bundled {@code data/<folder>/**<ext>} resources that do not exist yet. Returns how many were copied. */
    public static int extract(RPGLevelTier plugin, String folder, String ext) {
        String prefix = ROOT + folder + "/";
        new File(plugin.getDataFolder(), prefix).mkdirs();
        int copied = 0;
        try {
            URL url = plugin.getClass().getClassLoader().getResource("plugin.yml");
            if (url == null || !"jar".equals(url.getProtocol())) return 0;
            JarURLConnection conn = (JarURLConnection) url.openConnection();
            conn.setUseCaches(false);
            try (JarFile jf = conn.getJarFile()) {
                for (JarEntry e : Collections.list(jf.entries())) {
                    String name = e.getName();
                    if (e.isDirectory() || !name.startsWith(prefix) || !name.endsWith(ext)) continue;
                    if (new File(plugin.getDataFolder(), name).exists()) continue;
                    plugin.saveResource(name, false);
                    copied++;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[DataFiles] Could not extract " + prefix + ": " + e);
        }
        if (copied > 0) plugin.getLogger().info("[DataFiles] Extracted " + copied + " " + ext + " files to " + prefix);
        return copied;
    }

    /** Renames an old single-file config (e.g. data/classes.yml) so it is not confused with the new folder layout. */
    public static void migrateLegacy(RPGLevelTier plugin, String relativePath) {
        File old = new File(plugin.getDataFolder(), relativePath);
        if (!old.isFile()) return;
        File backup = new File(old.getParentFile(), old.getName() + ".old");
        if (old.renameTo(backup)) {
            plugin.getLogger().info("[DataFiles] " + relativePath + " ahora vive en una carpeta; el archivo antiguo quedó como " + backup.getName());
        }
    }

    /** Loads every {@code .yml} under data/<folder> (recursive, alphabetical) merged into one configuration. */
    public static YamlConfiguration loadMerged(RPGLevelTier plugin, String folder) {
        YamlConfiguration merged = new YamlConfiguration();
        Path dir = new File(plugin.getDataFolder(), ROOT + folder).toPath();
        if (!Files.isDirectory(dir)) return merged;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".yml")).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            plugin.getLogger().severe("[DataFiles] Cannot read " + dir + ": " + e.getMessage());
            return merged;
        }
        for (Path p : files) {
            YamlConfiguration y = new YamlConfiguration();
            try (Reader r = new InputStreamReader(Files.newInputStream(p), StandardCharsets.UTF_8)) {
                y.load(r);
            } catch (Exception e) {
                plugin.getLogger().severe("[DataFiles] Invalid " + dir.relativize(p) + ": " + e.getMessage());
                continue;
            }
            for (String key : y.getKeys(true)) {
                if (!y.isConfigurationSection(key)) merged.set(key, y.get(key));
            }
        }
        return merged;
    }
}

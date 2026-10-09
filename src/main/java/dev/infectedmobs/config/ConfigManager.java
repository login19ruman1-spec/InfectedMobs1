package dev.infectedmobs.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Thin live wrapper: always reads the current config, so /infectedmobs reload works. */
public final class ConfigManager {
    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public double d(String path, double def) { return raw().getDouble(path, def); }
    public int i(String path, int def) { return raw().getInt(path, def); }
    public boolean b(String path, boolean def) { return raw().getBoolean(path, def); }
    public String s(String path, String def) { return raw().getString(path, def); }
    public FileConfiguration raw() { return plugin.getConfig(); }
}

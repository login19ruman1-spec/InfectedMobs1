package dev.infectedmobs.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private final FileConfiguration c;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.c = plugin.getConfig();
    }

    public double d(String path, double def) { return c.getDouble(path, def); }
    public int i(String path, int def) { return c.getInt(path, def); }
    public boolean b(String path, boolean def) { return c.getBoolean(path, def); }
    public String s(String path, String def) { return c.getString(path, def); }
    public FileConfiguration raw() { return c; }
}

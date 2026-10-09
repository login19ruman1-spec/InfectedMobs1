package dev.infectedmobs.mob;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** All infected mob types, loaded from the {@code mobs:} section of config.yml. */
public final class MobRegistry {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private volatile Map<String, MobDefinition> defs = Map.of();

    public MobRegistry(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void load() {
        Map<String, MobDefinition> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.raw().getConfigurationSection("mobs");
        if (section == null) {
            plugin.getLogger().warning("config.yml has no 'mobs:' section - no infected mobs are defined.");
        } else {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s == null) continue;
                if (!s.getBoolean("enabled", true)) continue;
                MobDefinition def = MobDefinition.parse(id, s, plugin.getLogger());
                if (def != null) loaded.put(id, def);
            }
        }
        defs = loaded;
        plugin.getLogger().info("Loaded " + loaded.size() + " infected mob definition(s): " + String.join(", ", loaded.keySet()));
    }

    public MobDefinition get(String id) { return id == null ? null : defs.get(id); }

    public Collection<MobDefinition> all() { return defs.values(); }

    /**
     * Definition of an already infected entity. Uses the id stored on the entity; falls back to the
     * first definition for the same entity type + infection if the id no longer exists in the config.
     */
    public MobDefinition of(LivingEntity entity) {
        String infection = InfectedUtil.type(entity, plugin);
        if (infection == null) return null;
        MobDefinition def = get(InfectedUtil.mobId(entity, plugin));
        if (def != null) return def;
        for (MobDefinition d : defs.values()) {
            if (d.entity() == entity.getType() && d.infection().equals(infection)) return d;
        }
        return null;
    }

    /** Weighted random pick. {@code type} may be null for "any entity type". */
    public MobDefinition pick(EntityType type, String infection, Predicate<MobDefinition> filter) {
        List<MobDefinition> candidates = new ArrayList<>();
        double total = 0;
        for (MobDefinition d : defs.values()) {
            if (!d.infection().equals(infection)) continue;
            if (type != null && d.entity() != type) continue;
            if (filter != null && !filter.test(d)) continue;
            candidates.add(d);
            total += d.weight();
        }
        if (candidates.isEmpty()) return null;
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (MobDefinition d : candidates) {
            roll -= d.weight();
            if (roll <= 0) return d;
        }
        return candidates.get(candidates.size() - 1);
    }
}

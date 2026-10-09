package dev.infectedmobs.track;

import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the set of loaded infected mobs so the per-tick task never has to scan
 * every living entity of every world.
 */
public final class InfectedTracker {
    private final JavaPlugin plugin;
    private final Map<UUID, LivingEntity> tracked = new ConcurrentHashMap<>();

    public InfectedTracker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void track(LivingEntity entity) {
        if (entity != null && InfectedUtil.isInfected(entity, plugin)) tracked.put(entity.getUniqueId(), entity);
    }

    public void untrack(LivingEntity entity) {
        tracked.remove(entity.getUniqueId());
    }

    public List<LivingEntity> snapshot() {
        return new ArrayList<>(tracked.values());
    }

    public int size() { return tracked.size(); }

    /** Picks up infected mobs that are already loaded (plugin enable, /infectedmobs reload). */
    public void scanLoaded() {
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity e : world.getLivingEntities()) track(e);
        }
    }
}

package dev.infectedmobs.model;

import com.magmaguy.freeminecraftmodels.customentity.DynamicEntity;
import com.magmaguy.freeminecraftmodels.customentity.ModeledEntity;
import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** FMM bridge. The model ID is the .bbmodel/.fmmodel filename without extension. */
public final class ModelManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, ModeledEntity> models = new ConcurrentHashMap<>();

    public ModelManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean attach(LivingEntity entity, String modelId) {
        if (!config.b("models.use-fmm", true) || modelId == null || modelId.isBlank() || !entity.isValid()) return false;
        remove(entity);
        try {
            ModeledEntity model = DynamicEntity.create(modelId, entity);
            if (model == null) {
                plugin.getLogger().warning("FMM model is not loaded: " + modelId +
                        " (put " + modelId + ".bbmodel into plugins/FreeMinecraftModels/imports/ and run /fmm reload)");
                return false;
            }
            models.put(entity.getUniqueId(), model);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to attach FMM model '" + modelId + "' to " + entity.getType(), ex);
            return false;
        }
    }

    public void ensureAttached(LivingEntity entity, String modelId) {
        if (!entity.isValid()) return;
        ModeledEntity current = models.get(entity.getUniqueId());
        if (current == null || current.isRemoved()) attach(entity, modelId);
    }

    public boolean playAnimation(LivingEntity entity, String animation, boolean blend, boolean loop) {
        ModeledEntity model = models.get(entity.getUniqueId());
        if (model == null || model.isRemoved()) return false;
        try {
            return model.playAnimation(animation, blend, loop);
        } catch (Throwable ex) {
            plugin.getLogger().fine("Could not play animation '" + animation + "' for " + entity.getType() + ": " + ex.getMessage());
            return false;
        }
    }

    public void stopAnimations(LivingEntity entity) {
        ModeledEntity model = models.get(entity.getUniqueId());
        if (model == null || model.isRemoved()) return;
        try { model.stopCurrentAnimations(); } catch (Throwable ignored) {}
    }

    public void remove(LivingEntity entity) {
        ModeledEntity old = models.remove(entity.getUniqueId());
        if (old != null && !old.isRemoved()) old.remove();
    }

    public void removeAllModels() {
        models.values().forEach(m -> { if (m != null && !m.isRemoved()) m.remove(); });
        models.clear();
    }

    public int count() { return models.size(); }

    /** Reattach after /fmm reload or after a server-side model registry reload. */
    public void reattachAll() {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getServer().getWorlds().forEach(world ->
                world.getLivingEntities().forEach(entity -> {
                    if (!InfectedUtil.isInfected(entity, plugin)) return;
                    String type = InfectedUtil.type(entity, plugin);
                    String id = modelId(type, entity);
                    if (id != null) attach(entity, id);
                })));
    }

    public String modelId(String type, LivingEntity entity) {
        String key = switch (type) {
            case "sculk" -> "sculk-" + baseName(entity);
            case "moss" -> "moss-" + baseName(entity);
            default -> null;
        };
        return key == null ? null : config.s("models." + key, null);
    }

    private String baseName(LivingEntity e) {
        return switch (e.getType()) {
            case ZOMBIE -> "zombie";
            case SKELETON -> "skeleton";
            case SPIDER -> "spider";
            case CREEPER -> "creeper";
            default -> e.getType().name().toLowerCase();
        };
    }
}

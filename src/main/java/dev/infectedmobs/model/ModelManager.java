package dev.infectedmobs.model;

import com.magmaguy.freeminecraftmodels.customentity.DynamicEntity;
import com.magmaguy.freeminecraftmodels.customentity.ModeledEntity;
import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.mob.MobDefinition;
import dev.infectedmobs.track.InfectedTracker;
import dev.infectedmobs.mob.MobRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** FMM bridge. The model ID is the .bbmodel/.fmmodel filename without extension. */
public final class ModelManager {
    private static final long RETRY_MILLIS = 5000L;

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, ModeledEntity> models = new ConcurrentHashMap<>();
    private final Map<UUID, Long> retryAt = new ConcurrentHashMap<>();
    private final Set<String> warnedMissing = ConcurrentHashMap.newKeySet();
    private AnimationManager animations;

    public ModelManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setAnimations(AnimationManager animations) {
        this.animations = animations;
    }

    public boolean attach(LivingEntity entity, MobDefinition def) {
        if (!config.b("models.use-fmm", true) || def == null || !entity.isValid()) return false;
        String modelId = def.model();
        remove(entity);
        try {
            ModeledEntity model = DynamicEntity.create(modelId, entity);
            if (model == null) {
                if (warnedMissing.add(modelId)) {
                    plugin.getLogger().warning("FMM model is not loaded: '" + modelId + "' (mob '" + def.id()
                            + "'). Put " + modelId + ".bbmodel into plugins/FreeMinecraftModels/imports/ and run /fmm reload. "
                            + "Will retry every few seconds.");
                }
                return false;
            }
            warnedMissing.remove(modelId);
            models.put(entity.getUniqueId(), model);
            retryAt.remove(entity.getUniqueId());
            if (animations != null) animations.initialize(entity, def);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to attach FMM model '" + modelId + "' to " + entity.getType(), ex);
            return false;
        }
    }

    public boolean isAttached(LivingEntity entity) {
        ModeledEntity m = models.get(entity.getUniqueId());
        return m != null && !m.isRemoved();
    }

    /**
     * Makes sure the entity has a live model. Rate limited, so a missing model file does not
     * spam the console or the server every tick.
     */
    public boolean ensureAttached(LivingEntity entity, MobDefinition def) {
        if (!entity.isValid() || def == null) return false;
        if (isAttached(entity)) return true;
        long now = System.currentTimeMillis();
        Long next = retryAt.get(entity.getUniqueId());
        if (next != null && now < next) return false;
        retryAt.put(entity.getUniqueId(), now + RETRY_MILLIS);
        return attach(entity, def);
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

    public boolean hasAnimation(LivingEntity entity, String animation) {
        ModeledEntity model = models.get(entity.getUniqueId());
        if (model == null || model.isRemoved() || animation == null || animation.isBlank()) return false;
        try { return model.hasAnimation(animation); } catch (Throwable ignored) { return false; }
    }

    public void stopAnimations(LivingEntity entity) {
        ModeledEntity model = models.get(entity.getUniqueId());
        if (model == null || model.isRemoved()) return;
        try { model.stopCurrentAnimations(); } catch (Throwable ignored) {}
    }

    public void removeWithDeathAnimation(LivingEntity entity) {
        ModeledEntity old = models.remove(entity.getUniqueId());
        retryAt.remove(entity.getUniqueId());
        if (animations != null) animations.forget(entity.getUniqueId());
        if (old == null || old.isRemoved()) return;
        try { old.removeWithDeathAnimation(); } catch (Throwable ex) {
            plugin.getLogger().fine("Could not play death animation for " + entity.getType() + ": " + ex.getMessage());
            try { old.remove(); } catch (Throwable ignored) {}
        }
    }

    public void remove(LivingEntity entity) {
        ModeledEntity old = models.remove(entity.getUniqueId());
        retryAt.remove(entity.getUniqueId());
        if (animations != null) animations.forget(entity.getUniqueId());
        if (old != null && !old.isRemoved()) {
            try { old.remove(); } catch (Throwable ignored) {}
        }
    }

    public void removeAllModels() {
        models.values().forEach(m -> {
            try { if (m != null && !m.isRemoved()) m.remove(); } catch (Throwable ignored) {}
        });
        models.clear();
        retryAt.clear();
    }

    public int count() { return models.size(); }

    /** Reattach after /fmm reload or /infectedmobs reload. */
    public void reattachAll(InfectedTracker tracker, MobRegistry registry) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            tracker.scanLoaded();
            for (LivingEntity entity : tracker.snapshot()) {
                MobDefinition def = registry.of(entity);
                if (def != null) attach(entity, def);
            }
        });
    }
}



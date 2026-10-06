package dev.infectedmobs.model;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Skeleton;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single animation contract for every infected mob.
 * FMM itself owns the idle/walk state machine for DynamicEntity models;
 * this class supplies the configured animation names and explicit combat/death triggers.
 */
public final class AnimationManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ModelManager models;
    private final Map<UUID, String> modelIds = new ConcurrentHashMap<>();

    public AnimationManager(JavaPlugin plugin, ConfigManager config, ModelManager models) {
        this.plugin = plugin;
        this.config = config;
        this.models = models;
    }

    /** Called after a model is attached. Validates the complete animation contract. */
    public void initialize(LivingEntity entity) {
        if (!InfectedUtil.isInfected(entity, plugin)) return;
        String modelId = models.modelId(InfectedUtil.type(entity, plugin), entity);
        if (modelId == null) return;
        modelIds.put(entity.getUniqueId(), modelId);

        String key = modelConfigKey(InfectedUtil.type(entity, plugin), entity);
        for (String state : new String[]{"spawn", "idle", "walk", "attack", "death"}) {
            String animation = animationName(key, state, state);
            if (animation != null && !animation.isBlank() && !models.hasAnimation(entity, animation)) {
                // spawn is optional; the four core runtime states are not.
                if (!state.equals("spawn")) {
                    plugin.getLogger().warning("Model '" + modelId + "' is missing required animation '" + animation + "' for " + entity.getType());
                }
            }
        }
    }

    /**
     * We intentionally do not force idle/walk every second. DynamicEntity already switches
     * idle <-> walk from the real Bukkit entity's horizontal velocity every tick.
     */
    public void tick(LivingEntity entity) {
        if (!entity.isValid() || !InfectedUtil.isInfected(entity, plugin)) return;
        if (!modelIds.containsKey(entity.getUniqueId())) initialize(entity);
    }

    public void attack(LivingEntity entity) {
        if (!entity.isValid()) return;
        String key = modelConfigKey(InfectedUtil.type(entity, plugin), entity);
        String configured = entity instanceof Skeleton
                ? animationName(key, "ranged-attack", "attack")
                : animationName(key, "attack", "attack");

        if (configured != null && models.playAnimation(entity, configured, false, false)) {
            return;
        }

        if (!"attack".equals(configured)) {
            models.playAnimation(entity, "attack", false, false);
        }
    }

    public void death(LivingEntity entity) {
        // FMM's removeWithDeathAnimation() is the supported terminal death path.
        models.removeWithDeathAnimation(entity);
        remove(entity);
    }

    public void remove(LivingEntity entity) {
        modelIds.remove(entity.getUniqueId());
    }

    private String modelConfigKey(String infection, LivingEntity entity) {
        String prefix = infection.equals("moss") ? "moss" : "sculk";
        return prefix + "-" + switch (entity.getType()) {
            case ZOMBIE -> "zombie";
            case SKELETON -> "skeleton";
            case SPIDER -> "spider";
            case CREEPER -> "creeper";
            default -> entity.getType().name().toLowerCase();
        };
    }

    private String animationName(String key, String state, String fallback) {
        String path = "animations." + key + "." + state;
        return config.s(path, fallback);
    }

}

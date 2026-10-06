package dev.infectedmobs.model;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Skeleton;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic animation controller for infected DynamicEntity models.
 *
 * We deliberately control the base idle/walk state ourselves instead of relying
 * only on FMM's automatic state detector. This keeps the plugin behaviour stable
 * across FMM versions and guarantees that custom attack/death animations are not
 * immediately overwritten by an idle state.
 */
public final class AnimationManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ModelManager models;

    private final Map<UUID, String> modelIds = new ConcurrentHashMap<>();
    private final Map<UUID, String> states = new ConcurrentHashMap<>();
    private final Map<UUID, Long> actionUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    public AnimationManager(JavaPlugin plugin, ConfigManager config, ModelManager models) {
        this.plugin = plugin;
        this.config = config;
        this.models = models;
    }

    /** Called immediately after a model is attached. */
    public void initialize(LivingEntity entity) {
        if (!entity.isValid() || !InfectedUtil.isInfected(entity, plugin)) return;

        String infection = InfectedUtil.type(entity, plugin);
        String modelId = models.modelId(infection, entity);
        if (modelId == null) return;

        UUID id = entity.getUniqueId();
        modelIds.put(id, modelId);
        lastLocations.put(id, entity.getLocation().clone());

        String key = modelConfigKey(infection, entity);
        validateAnimations(entity, key, modelId);

        // spawn is OPTIONAL. If the model has it, actually play it.
        String spawn = animationName(key, "spawn", "spawn");
        if (has(entity, spawn)) {
            models.playAnimation(entity, spawn, false, false);
            actionUntil.put(id, System.currentTimeMillis() + ticksToMillis(config.i("animations." + key + ".spawn-lock-ticks", 12)));
            states.put(id, "spawn");
            return;
        }

        playBase(entity, false);
    }

    /** Called every tick for infected mobs. */
    public void tick(LivingEntity entity) {
        if (!entity.isValid() || entity.isDead() || !InfectedUtil.isInfected(entity, plugin)) {
            remove(entity);
            return;
        }

        UUID id = entity.getUniqueId();
        String key = modelConfigKey(InfectedUtil.type(entity, plugin), entity);
        String idle = animationName(key, "idle", "idle");
        if (!modelIds.containsKey(id) || !has(entity, idle)) {
            initialize(entity);
            return;
        }

        Location previous = lastLocations.put(id, entity.getLocation().clone());
        boolean moving = isMoving(entity, previous);

        Long lockedUntil = actionUntil.get(id);
        if (lockedUntil != null) {
            if (System.currentTimeMillis() < lockedUntil) return;
            actionUntil.remove(id);
        }

        String wanted = moving ? "walk" : "idle";
        if (!wanted.equals(states.get(id))) {
            playBase(entity, moving);
        }
    }

    public void attack(LivingEntity entity) {
        if (!entity.isValid() || entity.isDead()) return;

        String key = modelConfigKey(InfectedUtil.type(entity, plugin), entity);
        boolean ranged = entity instanceof Skeleton;
        String configured = ranged
                ? animationName(key, "ranged-attack", "attack_ranged")
                : animationName(key, "attack", "attack");

        // Skeletons may not have a ranged animation. Fall back to normal attack.
        if (!has(entity, configured)) configured = animationName(key, "attack", "attack");
        if (!has(entity, configured)) return;

        // Stop the previous looped state first. FMM can otherwise keep the
        // automatic/looped state active and visually swallow a one-shot action.
        models.stopAnimations(entity);
        if (models.playAnimation(entity, configured, false, false)) {
            states.put(entity.getUniqueId(), configured);
            int lockTicks = config.i("animations." + key + ".attack-lock-ticks", ranged ? 15 : 14);
            actionUntil.put(entity.getUniqueId(), System.currentTimeMillis() + ticksToMillis(lockTicks));
        }
    }

    public void death(LivingEntity entity) {
        // FMM's terminal path is responsible for keeping the model alive long enough
        // to display its death animation before removing the display model.
        models.removeWithDeathAnimation(entity);
        remove(entity);
    }

    public void remove(LivingEntity entity) {
        UUID id = entity.getUniqueId();
        modelIds.remove(id);
        states.remove(id);
        actionUntil.remove(id);
        lastLocations.remove(id);
    }

    private void playBase(LivingEntity entity, boolean moving) {
        String key = modelConfigKey(InfectedUtil.type(entity, plugin), entity);
        String state = moving ? "walk" : "idle";
        String animation = animationName(key, state, state);
        if (!has(entity, animation)) return;

        // Explicitly stop the previous state before starting the new loop.
        // This makes walk/idle deterministic instead of depending on FMM's
        // automatic state detector.
        models.stopAnimations(entity);
        if (models.playAnimation(entity, animation, false, true)) {
            states.put(entity.getUniqueId(), state);
        }
    }

    private boolean isMoving(LivingEntity entity, Location previous) {
        if (previous == null) return entity.getVelocity().getX() * entity.getVelocity().getX()
                + entity.getVelocity().getZ() * entity.getVelocity().getZ() > 0.0004;

        double dx = entity.getX() - previous.getX();
        double dz = entity.getZ() - previous.getZ();
        double distanceMoved = dx * dx + dz * dz;
        double velocity = entity.getVelocity().getX() * entity.getVelocity().getX()
                + entity.getVelocity().getZ() * entity.getVelocity().getZ();
        return distanceMoved > 0.000025 || velocity > 0.0004;
    }

    private boolean has(LivingEntity entity, String animation) {
        return animation != null && !animation.isBlank() && models.hasAnimation(entity, animation);
    }

    private void validateAnimations(LivingEntity entity, String key, String modelId) {
        for (String state : new String[]{"idle", "walk", "attack", "death"}) {
            String animation = animationName(key, state, state);
            if (!has(entity, animation)) {
                plugin.getLogger().warning("Model '" + modelId + "' is missing animation '" + animation
                        + "' for " + entity.getType() + " (config: animations." + key + "." + state + ")");
            }
        }

        if (entity instanceof Skeleton) {
            String ranged = animationName(key, "ranged-attack", "attack_ranged");
            if (!has(entity, ranged)) {
                plugin.getLogger().warning("Model '" + modelId + "' has no ranged attack animation '" + ranged
                        + "'. Skeleton will fall back to the normal attack animation.");
            }
        }
    }

    private String modelConfigKey(String infection, LivingEntity entity) {
        String prefix = "moss".equals(infection) ? "moss" : "sculk";
        return prefix + "-" + switch (entity.getType()) {
            case ZOMBIE -> "zombie";
            case SKELETON -> "skeleton";
            case SPIDER -> "spider";
            case CREEPER -> "creeper";
            default -> entity.getType().name().toLowerCase();
        };
    }

    private String animationName(String key, String state, String fallback) {
        return config.s("animations." + key + "." + state, fallback);
    }

    private long ticksToMillis(int ticks) {
        return Math.max(1, ticks) * 50L;
    }
}

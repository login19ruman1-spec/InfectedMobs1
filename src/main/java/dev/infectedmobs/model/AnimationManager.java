package dev.infectedmobs.model;

import dev.infectedmobs.mob.MobDefinition;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-mob animation controller on top of
 * FreeMinecraftModels' own state machine.
 */
public final class AnimationManager {

    private static final double MOVE_EPS_SQ = 0.0009;
    private static final int START_WALK_TICKS = 2;
    private static final int STOP_WALK_TICKS = 4;

    private static final class State {
        final MobDefinition def;

        String mode = "none";
        long lockUntil;

        double lastX;
        double lastZ;

        int moving;
        int still;

        State(MobDefinition def) {
            this.def = def;
        }
    }

    private final JavaPlugin plugin;
    private final ModelManager models;

    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private long now;

    public AnimationManager(JavaPlugin plugin, ModelManager models) {
        this.plugin = plugin;
        this.models = models;
    }

    /**
     * Called once per server tick.
     */
    public void advance() {
        now++;
    }

    /**
     * Called after a model has been attached.
     */
    public void initialize(LivingEntity entity, MobDefinition def) {
        State s = new State(def);

        s.lastX = entity.getX();
        s.lastZ = entity.getZ();

        states.put(entity.getUniqueId(), s);

        validate(entity, def);

        // Allow the spawn animation to finish.
        if (models.hasAnimation(entity, "spawn")) {
            s.mode = "action";
            s.lockUntil = now + def.spawnLockTicks();
        }
    }

    /**
     * Called every tick for every infected mob
     * that currently has a live model.
     */
    public void tick(LivingEntity entity, MobDefinition def) {
        State s = states.get(entity.getUniqueId());

        if (s == null) {
            initialize(entity, def);
            s = states.get(entity.getUniqueId());

            if (s == null) {
                return;
            }
        }

        // Do not update animations for dead entities.
        if (!entity.isValid() || entity.isDead()) {
            remove(entity);
            return;
        }

        double x = entity.getX();
        double z = entity.getZ();

        double dx = x - s.lastX;
        double dz = z - s.lastZ;

        double distanceSquared = dx * dx + dz * dz;

        s.lastX = x;
        s.lastZ = z;

        // Determine movement using actual position changes.
        if (distanceSquared > MOVE_EPS_SQ) {
            s.moving++;
            s.still = 0;
        } else {
            s.still++;
            s.moving = 0;
        }

        // An action animation is currently playing.
        if (s.mode.equals("action")) {
            if (now < s.lockUntil) {
                return;
            }

            s.mode = "none";
        }

        // Choose the appropriate base animation.
        String target;

        if (s.moving >= START_WALK_TICKS) {
            target = "walk";
        } else if (s.still >= STOP_WALK_TICKS) {
            target = "idle";
        } else {
            // Keep the current animation during short transitions.
            return;
        }

        if (target.equals(s.mode)) {
            return;
        }

        if (play(entity, target, true)) {
            s.mode = target;
        }
    }

    /**
     * Play a one-shot action animation.
     */
    public void playAction(
            LivingEntity entity,
            String animation,
            int durationTicks
    ) {
        State s = states.get(entity.getUniqueId());

        if (s == null) {
            return;
        }

        if (!models.hasAnimation(entity, animation)) {
            warnMissing(animation);
            return;
        }

        if (play(entity, animation, false)) {
            s.mode = "action";
            s.lockUntil = now + Math.max(1, durationTicks);
        }
    }

    /**
     * Play the standard attack animation.
     */
    public void attack(LivingEntity entity, MobDefinition def) {
        playAction(entity, "attack", def.attackLockTicks());
    }

    /**
     * Play a ranged attack animation.
     */
    public void rangedAttack(LivingEntity entity, MobDefinition def) {
        playAction(entity, "attack_ranged", def.rangedAttackLockTicks());
    }

    /**
     * Play the hurt animation.
     */
    public void hurt(LivingEntity entity, MobDefinition def) {
        playAction(entity, "hurt", def.hurtLockTicks());
    }

    /**
     * Play the death animation.
     */
    public void death(LivingEntity entity) {
        State s = states.get(entity.getUniqueId());

        if (s == null) {
            return;
        }

        if (play(entity, "death", false)) {
            s.mode = "action";
            s.lockUntil = Long.MAX_VALUE;
        }
    }

    /**
     * Remove the entity from the animation controller.
     */
    public void remove(LivingEntity entity) {
        states.remove(entity.getUniqueId());
    }

    /**
     * Remove all tracked entities.
     */
    public void clear() {
        states.clear();
    }

    /**
     * Validate animation availability.
     */
    private void validate(LivingEntity entity, MobDefinition def) {
        checkAnimation(entity, "idle");
        checkAnimation(entity, "walk");
    }

    private void checkAnimation(LivingEntity entity, String animation) {
        if (!models.hasAnimation(entity, animation)) {
            warnMissing(animation);
        }
    }

    private void warnMissing(String animation) {
        if (warned.add(animation)) {
            plugin.getLogger().warning(
                    "Animation '" + animation
                            + "' is missing from one or more mob models."
            );
        }
    }

    /**
     * Delegate animation playback to ModelManager.
     */
    private boolean play(
            LivingEntity entity,
            String animation,
            boolean blend
    ) {
        if (!models.hasAnimation(entity, animation)) {
            return false;
        }

        try {
            models.playAnimation(entity, animation, blend);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning(
                    "Failed to play animation '" + animation
                            + "' for entity " + entity.getUniqueId()
                            + ": " + ex.getMessage()
            );

            return false;
        }
    }
}
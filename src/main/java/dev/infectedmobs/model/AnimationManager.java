package dev.infectedmobs.model;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Drives FMM animations from the real Bukkit entity state. */
public final class AnimationManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ModelManager models;
    private final Map<UUID, String> states = new ConcurrentHashMap<>();
    private final Map<UUID, Long> attackUntil = new ConcurrentHashMap<>();

    public AnimationManager(JavaPlugin plugin, ConfigManager config, ModelManager models) {
        this.plugin = plugin;
        this.config = config;
        this.models = models;
    }

    public void tick(LivingEntity entity) {
        if (!entity.isValid() || !InfectedUtil.isInfected(entity, plugin)) return;
        Long until = attackUntil.get(entity.getUniqueId());
        if (until != null) {
            if (System.currentTimeMillis() < until) return;
            attackUntil.remove(entity.getUniqueId());
        }

        String state;
        if (entity instanceof Creeper creeper && creeper.isIgnited()) {
            state = "attack";
        } else if (entity.getVelocity().setY(0).lengthSquared() > 0.0025) {
            state = "walk";
        } else {
            state = "idle";
        }
        playState(entity, state);
    }

    public void attack(LivingEntity entity) {
        if (!entity.isValid()) return;
        String animation = entity instanceof org.bukkit.entity.Skeleton ? "attack_ranged" : "attack";
        if (!models.playAnimation(entity, animation, false, false)) {
            models.playAnimation(entity, "attack", false, false);
        }
        attackUntil.put(entity.getUniqueId(), System.currentTimeMillis() +
                (entity instanceof org.bukkit.entity.Skeleton ? 750L : 600L));
    }

    public void death(LivingEntity entity) {
        if (!models.playAnimation(entity, "death", false, false)) {
            models.stopAnimations(entity);
        }
        states.remove(entity.getUniqueId());
    }

    private void playState(LivingEntity entity, String state) {
        UUID id = entity.getUniqueId();
        String previous = states.get(id);
        if (state.equals(previous)) return;
        if (models.playAnimation(entity, state, false, true)) {
            states.put(id, state);
        }
    }

    public void remove(LivingEntity entity) {
        states.remove(entity.getUniqueId());
        attackUntil.remove(entity.getUniqueId());
    }
}

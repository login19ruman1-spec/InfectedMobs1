package dev.infectedmobs.task;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.infection.MossInfectionManager;
import dev.infectedmobs.infection.SculkInfectionManager;
import dev.infectedmobs.mob.MobDefinition;
import dev.infectedmobs.mob.MobRegistry;
import dev.infectedmobs.model.AnimationManager;
import dev.infectedmobs.model.ModelManager;
import dev.infectedmobs.track.InfectedTracker;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runs every tick, but only over tracked infected mobs (never over every entity of the server).
 * Animations are ticked here; day/night + sculk behaviour runs once per second.
 */
public final class InfectionBehaviorTask extends BukkitRunnable {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MobRegistry registry;
    private final InfectedTracker tracker;
    private final ModelManager models;
    private final SculkInfectionManager sculk;
    private final MossInfectionManager moss;
    private final AnimationManager animations;
    private int emergeTimer;
    private int behaviorTimer;

    public InfectionBehaviorTask(JavaPlugin plugin, ConfigManager config, MobRegistry registry,
                                 InfectedTracker tracker, ModelManager models,
                                 SculkInfectionManager sculk, MossInfectionManager moss,
                                 AnimationManager animations) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.tracker = tracker;
        this.models = models;
        this.sculk = sculk;
        this.moss = moss;
        this.animations = animations;
    }

    @Override public void run() {
        animations.advance();
        behaviorTimer++;
        boolean runBehavior = behaviorTimer >= 20;
        if (runBehavior) {
            behaviorTimer = 0;
            emergeTimer += 20;
        }

        for (LivingEntity e : tracker.snapshot()) {
            if (!e.isValid()) {
                // Dead mobs are handled by the death listener (death animation). Anything else that
                // became invalid (chunk unloaded, despawned) must release its model.
                if (!e.isDead()) models.remove(e);
                tracker.untrack(e);
                continue;
            }
            MobDefinition def = registry.of(e);
            if (def == null) { tracker.untrack(e); continue; }

            // Also covers: natural spawns (entity not valid during the spawn event), server restart,
            // chunk reload, and models that were not loaded yet when the mob was created.
            if (models.ensureAttached(e, def)) animations.tick(e, def);

            if (!runBehavior) continue;
            long time = e.getWorld().getTime();
            if (InfectedUtil.is(e, plugin, "moss")) {
                if (InfectedUtil.isDay(time) && config.b("moss.day.frozen", true)) {
                    moss.freeze(e);
                    moss.staticVisual(e);
                } else if (InfectedUtil.isNight(time) && config.b("moss.night.active", true)) {
                    moss.activate(e);
                }
            } else if (InfectedUtil.is(e, plugin, "sculk")) {
                sculk.trail(e);
            }
        }

        if (!runBehavior) return;
        int interval = Math.max(20, config.i("sculk.infection.emerge-interval-ticks", 100));
        if (emergeTimer < interval) return;
        emergeTimer = 0;

        for (World world : plugin.getServer().getWorlds()) {
            if (!InfectedUtil.isNight(world.getTime())) continue;
            for (Player player : world.getPlayers()) {
                if (InfectedUtil.nearMaterial(player.getLocation(),
                        config.i("sculk.infection.emerge-range", 8),
                        Material.SCULK, Material.SCULK_VEIN, Material.SCULK_CATALYST)) {
                    sculk.tryEmerge(player);
                }
            }
        }
    }
}

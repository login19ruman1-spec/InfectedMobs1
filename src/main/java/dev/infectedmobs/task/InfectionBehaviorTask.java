package dev.infectedmobs.task;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.infection.MossInfectionManager;
import dev.infectedmobs.infection.SculkInfectionManager;
import dev.infectedmobs.util.InfectedUtil;
import dev.infectedmobs.model.AnimationManager;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Runs every tick so the animation controller can reliably switch idle/walk. */
public final class InfectionBehaviorTask extends BukkitRunnable {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final SculkInfectionManager sculk;
    private final MossInfectionManager moss;
    private final AnimationManager animations;
    private int emergeTimer;
    private int behaviorTimer;

    public InfectionBehaviorTask(JavaPlugin plugin, ConfigManager config,
                                 SculkInfectionManager sculk, MossInfectionManager moss,
                                 CustomEffectManager ignored, AnimationManager animations) {
        this.plugin = plugin;
        this.config = config;
        this.sculk = sculk;
        this.moss = moss;
        this.animations = animations;
    }

    @Override public void run() {
        behaviorTimer++;
        emergeTimer++;
        boolean runBehavior = behaviorTimer >= 20;
        if (runBehavior) behaviorTimer = 0;

        for (var world : plugin.getServer().getWorlds()) {
            boolean day = InfectedUtil.isDay(world.getTime());
            boolean night = InfectedUtil.isNight(world.getTime());

            for (LivingEntity e : world.getLivingEntities()) {
                // Animation state is deliberately tick-level.
                if (InfectedUtil.isInfected(e, plugin)) animations.tick(e);

                // Infection/visual maintenance remains once per second.
                if (!runBehavior) continue;
                if (InfectedUtil.is(e, plugin, "moss")) {
                    if (day && config.b("moss.day.frozen", true)) {
                        moss.freeze(e);
                        moss.staticVisual(e);
                    } else if (night && config.b("moss.night.active", true)) {
                        moss.activate(e);
                    }
                } else if (InfectedUtil.is(e, plugin, "sculk")) {
                    sculk.trail(e);
                }
            }

            if (runBehavior) {
                int interval = Math.max(20, config.i("sculk.infection.emerge-interval-ticks", 100));
                if (emergeTimer >= interval) {
                    emergeTimer = 0;
                    if (night) {
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
        }
    }
}

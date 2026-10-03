package dev.infectedmobs.task;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.infection.MossInfectionManager;
import dev.infectedmobs.infection.SculkInfectionManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** One pass per second. No per-tick full-world scan. */
public final class InfectionBehaviorTask extends BukkitRunnable {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final SculkInfectionManager sculk;
    private final MossInfectionManager moss;
    private int emergeTimer;

    public InfectionBehaviorTask(JavaPlugin plugin, ConfigManager config,
                                 SculkInfectionManager sculk, MossInfectionManager moss,
                                 CustomEffectManager ignored) {
        this.plugin = plugin;
        this.config = config;
        this.sculk = sculk;
        this.moss = moss;
    }

    @Override public void run() {
        for (var world : plugin.getServer().getWorlds()) {
            boolean day = InfectedUtil.isDay(world.getTime());
            boolean night = InfectedUtil.isNight(world.getTime());

            for (LivingEntity e : world.getLivingEntities()) {
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

            emergeTimer += 20;
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

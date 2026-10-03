package dev.infectedmobs.task;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.infection.MossInfectionManager;
import dev.infectedmobs.infection.SculkInfectionManager;
import dev.infectedmobs.model.ModelManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class InfectionBehaviorTask extends BukkitRunnable {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final SculkInfectionManager sculk;
    private final MossInfectionManager moss;
    private final ModelManager models;
    private final CustomEffectManager effects;
    private long lastTime = -1;
    private int emergeCounter = 0;

    public InfectionBehaviorTask(JavaPlugin plugin, ConfigManager config,
                                 SculkInfectionManager sculk, MossInfectionManager moss,
                                 CustomEffectManager effects) {
        this.plugin = plugin; this.config = config; this.sculk = sculk; this.moss = moss;
        this.models = plugin instanceof dev.infectedmobs.InfectedMobsPlugin p ? p.models() : null;
        this.effects = effects;
    }

    @Override public void run() {
        boolean nowDay = false, nowNight = false;
        for (var world : plugin.getServer().getWorlds()) {
            long time = world.getTime();
            boolean day = InfectedUtil.isDay(time), night = InfectedUtil.isNight(time);

            if (lastTime < 0) lastTime = time;
            boolean crossedNight = (lastTime < 13000 && time >= 13000) || (lastTime > time && time >= 13000);
            boolean crossedDay = (lastTime >= 13000 && time < 12000) || (lastTime > time && time < 12000);

            for (LivingEntity e : world.getLivingEntities()) {
                if (InfectedUtil.is(e, plugin, "moss")) {
                    if (day && config.b("moss.day.frozen", true)) {
                        moss.freeze(e);
                        moss.staticVisual(e);
                    } else if (night && config.b("moss.night.active", true)) {
                        moss.activate(e);
                    }
                    models.ensureAttached(e, moss.modelFor(e));
                } else if (InfectedUtil.is(e, plugin, "sculk")) {
                    sculk.trail(e);
                    models.ensureAttached(e, sculk.modelFor(e));
                }
            }

            if (++emergeCounter >= Math.max(1, config.i("sculk.infection.emerge-interval-ticks", 100) / 20)) {
                emergeCounter = 0;
                if (night) {
                    for (Player player : world.getPlayers()) {
                        if (InfectedUtil.nearMaterial(player.getLocation(),
                                config.i("sculk.infection.emerge-range", 8),
                                Material.SCULK, Material.SCULK_VEIN, Material.SCULK_CATALYST)) {
                            sculk.emerge(player);
                        }
                    }
                }
            }
            if (crossedNight) nowNight = true;
            if (crossedDay) nowDay = true;
            lastTime = time;
        }
    }
}

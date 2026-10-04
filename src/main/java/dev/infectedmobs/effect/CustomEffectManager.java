package dev.infectedmobs.effect;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomEffectManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, Integer> sculkStacks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sculkRemaining = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> mossRemaining = new ConcurrentHashMap<>();

    public CustomEffectManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin; this.config = config;
        new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    public void applySculkInfection(Player p) {
        int max = config.i("sculk.effect.max-stacks", 3);
        sculkStacks.merge(p.getUniqueId(), 1, (a,b) -> Math.min(max, a + b));
        sculkRemaining.put(p.getUniqueId(), config.i("sculk.effect.duration-ticks", 200));
    }

    public void applyMossSlow(Player p) {
        mossRemaining.put(p.getUniqueId(), config.i("moss.night.effect.duration-ticks", 60));
    }

    private void tick() {
        int interval = Math.max(2, config.i("sculk.effect.tick-interval", 40));
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();

            Integer sr = sculkRemaining.get(id);
            if (sr != null) {
                if (sr <= 0 || !isNearSculk(p, 32)) {
                    sculkRemaining.remove(id); sculkStacks.remove(id);
                } else {
                    int stack = Math.max(1, sculkStacks.getOrDefault(id, 1));
                    int elapsed = config.i("sculk.effect.duration-ticks", 200) - sr;
                    if (elapsed % interval < 2) {
                        double damage = config.d("sculk.effect.damage-per-tick", 1.0) * stack;
                        p.damage(damage);
                        p.getWorld().spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0),
                                5, .25, .5, .25, .01);
                        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SCULK_PLACE, .35f, 1.3f);
                    }
                    sculkRemaining.put(id, sr - 2);
                }
            }

            Integer mr = mossRemaining.get(id);
            if (mr != null) {
                if (mr <= 0) {
                    mossRemaining.remove(id);
                    p.setWalkSpeed(.2f);
                } else {
                    // Custom timer/state; Bukkit potion effects are deliberately not used.
                    p.setWalkSpeed(.08f);
                    p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation(), 2, .2, .05, .2,
                            Material.MOSS_BLOCK.createBlockData());
                    mossRemaining.put(id, mr - 2);
                }
            }
        }
    }

    private boolean isNearSculk(Player p, int radius) {
        return InfectedUtil.nearMaterial(p.getLocation(), radius,
                Material.SCULK, Material.SCULK_VEIN, Material.SCULK_CATALYST);
    }

    public void clear(Player p) {
        sculkRemaining.remove(p.getUniqueId());
        sculkStacks.remove(p.getUniqueId());
        mossRemaining.remove(p.getUniqueId());
        p.setWalkSpeed(.2f);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
        sculkRemaining.clear(); sculkStacks.clear(); mossRemaining.clear();
    }
}

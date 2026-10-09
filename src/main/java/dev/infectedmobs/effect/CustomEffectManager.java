package dev.infectedmobs.effect;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Custom effects implemented with PDC + a scheduler; no Bukkit potion effects are used. */
public final class CustomEffectManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, Integer> sculkStacks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sculkRemaining = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> mossRemaining = new ConcurrentHashMap<>();
    private final Map<UUID, Float> originalWalkSpeed = new ConcurrentHashMap<>();
    private final NamespacedKey sculkKey;
    private final NamespacedKey mossKey;

    public CustomEffectManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.sculkKey = new NamespacedKey(plugin, "effect_sculk_infection");
        this.mossKey = new NamespacedKey(plugin, "effect_moss_slow");
        new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    public void applySculkInfection(Player p) {
        int max = config.i("sculk.effect.max-stacks", 3);
        int stacks = Math.min(max, sculkStacks.getOrDefault(p.getUniqueId(), 0) + 1);
        sculkStacks.put(p.getUniqueId(), stacks);
        int duration = config.i("sculk.effect.duration-ticks", 200);
        sculkRemaining.put(p.getUniqueId(), duration);
        p.getPersistentDataContainer().set(sculkKey, PersistentDataType.INTEGER, stacks);
        p.getWorld().spawnParticle(Particle.SCULK_CHARGE, p.getLocation().add(0, 1, 0),
                12 + stacks * 4, .35, .6, .35, .03);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SCULK_PLACE, .65f, 1.15f + stacks * .08f);
    }

    public void applyMossSlow(Player p) {
        UUID id = p.getUniqueId();
        originalWalkSpeed.putIfAbsent(id, p.getWalkSpeed());
        int duration = config.i("moss.night.effect.duration-ticks", 60);
        mossRemaining.put(id, duration);
        p.getPersistentDataContainer().set(mossKey, PersistentDataType.INTEGER, duration);
        p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation().add(0, 1, 0),
                10, .3, .6, .3, Material.MOSS_BLOCK.createBlockData());
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_MOSS_BREAK, .55f, .7f);
    }

    private void tick() {
        int interval = Math.max(2, config.i("sculk.effect.tick-interval", 40));
        int duration = Math.max(2, config.i("sculk.effect.duration-ticks", 200));

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Integer sr = sculkRemaining.get(id);
            if (sr != null) {
                int elapsed = duration - sr;
                if (sr <= 0) {
                    clearSculk(p);
                } else {
                    // Check the 32-block removal rule once per second, not every 2 ticks.
                    if (elapsed % 20 == 0 && !isNearSculk(p, 32)) {
                        clearSculk(p);
                    } else {
                        int stack = Math.max(1, sculkStacks.getOrDefault(id, 1));
                        if (elapsed % interval < 2) {
                            double damage = config.d("sculk.effect.damage-per-tick", 1.0) * stack;
                            p.damage(damage);
                            p.getWorld().spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0),
                                    5 + stack * 2, .25, .5, .25, .01);
                            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SCULK_PLACE, .35f, 1.3f);
                        }
                        sculkRemaining.put(id, sr - 2);
                        p.getPersistentDataContainer().set(sculkKey, PersistentDataType.INTEGER, stack);
                    }
                }
            }

            Integer mr = mossRemaining.get(id);
            if (mr != null) {
                if (mr <= 0) {
                    clearMoss(p);
                } else {
                    p.setWalkSpeed(.08f);
                    if (mr % 10 == 0) {
                        p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation(),
                                2, .2, .05, .2, Material.MOSS_BLOCK.createBlockData());
                    }
                    mossRemaining.put(id, mr - 2);
                    p.getPersistentDataContainer().set(mossKey, PersistentDataType.INTEGER, mr - 2);
                }
            }
        }
    }

    private void clearSculk(Player p) {
        sculkRemaining.remove(p.getUniqueId());
        sculkStacks.remove(p.getUniqueId());
        p.getPersistentDataContainer().remove(sculkKey);
    }

    private void clearMoss(Player p) {
        UUID id = p.getUniqueId();
        mossRemaining.remove(id);
        Float speed = originalWalkSpeed.remove(id);
        if (speed != null) p.setWalkSpeed(speed);
        p.getPersistentDataContainer().remove(mossKey);
    }

    private boolean isNearSculk(Player p, int radius) {
        return InfectedUtil.nearMaterial(p.getLocation(), radius,
                Material.SCULK, Material.SCULK_VEIN, Material.SCULK_CATALYST);
    }

    public void clear(Player p) {
        clearSculk(p);
        clearMoss(p);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
        sculkRemaining.clear();
        sculkStacks.clear();
        mossRemaining.clear();
        originalWalkSpeed.clear();
    }
}


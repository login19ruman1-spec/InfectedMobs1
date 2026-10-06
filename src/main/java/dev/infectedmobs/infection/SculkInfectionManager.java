package dev.infectedmobs.infection;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.model.ModelManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public final class SculkInfectionManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ModelManager models;
    private final CustomEffectManager effects;

    public SculkInfectionManager(JavaPlugin plugin, ConfigManager config, ModelManager models, CustomEffectManager effects) {
        this.plugin = plugin; this.config = config; this.models = models; this.effects = effects;
    }

    public boolean canConvert(Entity entity) {
        return entity instanceof Zombie || entity instanceof Skeleton ||
                entity instanceof Spider || entity instanceof Creeper;
    }

    public void convert(LivingEntity entity, String source) {
        if (!canConvert(entity) || InfectedUtil.isInfected(entity, plugin)) return;
        InfectedUtil.mark(entity, plugin, "sculk", source);
        double hp = entity.getAttribute(Attribute.MAX_HEALTH) != null
                ? entity.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
        setAttribute(entity, Attribute.MAX_HEALTH, config.d("sculk.infection.hp-multiplier", 1.5), hp);
        setAttribute(entity, Attribute.MOVEMENT_SPEED, config.d("sculk.infection.speed-multiplier", 1.2), 0);
        setAttribute(entity, Attribute.ATTACK_DAMAGE, config.d("sculk.infection.damage-multiplier", 1.3), 0);
        entity.setHealth(Math.min(entity.getHealth() * config.d("sculk.infection.hp-multiplier", 1.5),
                entity.getAttribute(Attribute.MAX_HEALTH).getValue()));
        models.attach(entity, modelFor(entity));
    }

    private void setAttribute(LivingEntity e, Attribute attr, double multiplier, double ignored) {
        var a = e.getAttribute(attr);
        if (a == null) return;
        a.setBaseValue(a.getBaseValue() * multiplier);
    }

    public String modelFor(LivingEntity e) {
        return switch (e.getType()) {
            case ZOMBIE -> config.s("models.sculk-zombie", "sculk_infected_zombie");
            case SKELETON -> config.s("models.sculk-skeleton", "sculk_infected_skeleton");
            case SPIDER -> config.s("models.sculk-spider", "sculk_infected_spider");
            case CREEPER -> config.s("models.sculk-creeper", "sculk_infected_creeper");
            default -> null;
        };
    }

    public void attackPlayer(Player player) {
        effects.applySculkInfection(player);
    }

    public void deathVisual(LivingEntity entity) {
        entity.getWorld().spawnParticle(Particle.SCULK_CHARGE, entity.getLocation().add(0, 1, 0),
                18, .35, .6, .35, .05);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_SCULK_PLACE, 1f, .8f);
    }

    public void trail(LivingEntity entity) {
        Location l = entity.getLocation();
        entity.getWorld().spawnParticle(Particle.SCULK_CHARGE, l, 1, .15, .05, .15, .01);
    }

    public void tryEmerge(Player player) {
        int radius = config.i("sculk.infection.emerge-range", 8);
        Block source = InfectedUtil.randomNearbyBlock(player.getLocation(), radius,
                Material.SCULK, Material.SCULK_VEIN, Material.SCULK_CATALYST);
        if (source == null) return;
        if (ThreadLocalRandom.current().nextDouble() >= config.d("sculk.infection.emerge-chance", .03)) return;

        Location spawn = findSpawnLocation(source);
        if (spawn == null) return;
        EntityType type = switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> EntityType.ZOMBIE;
            case 1 -> EntityType.SKELETON;
            default -> EntityType.SPIDER;
        };
        Entity raw = source.getWorld().spawnEntity(spawn, type);
        if (!(raw instanceof LivingEntity mob)) return;
        convert(mob, "emerged");

        source.getWorld().spawnParticle(Particle.SCULK_CHARGE, spawn, 30, .5, .7, .5, .05);
        source.getWorld().playSound(spawn, Sound.BLOCK_SCULK_PLACE, 1f, .7f);

        // The mob keeps normal gravity and AI; the emergence is visual only.
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks;
            @Override public void run() {
                if (!mob.isValid() || ticks++ >= 20) { cancel(); return; }
                mob.getWorld().spawnParticle(Particle.SCULK_CHARGE, mob.getLocation().add(0, .1, 0),
                        3, .25, .2, .25, .01);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
    private Location findSpawnLocation(Block source) {
        var world = source.getWorld();
        int x = source.getX();
        int z = source.getZ();
        for (int dy = 1; dy <= 5; dy++) {
            Location feet = new Location(world, x + 0.5, source.getY() + dy, z + 0.5);
            if (world.getBlockAt(x, source.getY() + dy, z).isPassable()
                    && world.getBlockAt(x, source.getY() + dy + 1, z).isPassable()) return feet;
        }
        return null;
    }

}

package dev.infectedmobs.infection;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.mob.MobDefinition;
import dev.infectedmobs.mob.MobRegistry;
import dev.infectedmobs.track.InfectedTracker;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class SculkInfectionManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MobRegistry registry;
    private final InfectedTracker tracker;
    private final CustomEffectManager effects;

    public SculkInfectionManager(JavaPlugin plugin, ConfigManager config, MobRegistry registry,
                                 InfectedTracker tracker, CustomEffectManager effects) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.tracker = tracker;
        this.effects = effects;
    }

    /** Natural-spawn candidate for this entity type, or null if none is configured. */
    public MobDefinition naturalDefinition(Entity entity) {
        return registry.pick(entity.getType(), "sculk", MobDefinition::natural);
    }

    public void convert(LivingEntity entity, MobDefinition def, String source) {
        if (def == null || InfectedUtil.isInfected(entity, plugin)) return;
        InfectedUtil.mark(entity, plugin, "sculk", source, def.id());

        double hpMul = def.hpMultiplier() != null ? def.hpMultiplier() : config.d("sculk.infection.hp-multiplier", 1.5);
        double speedMul = def.speedMultiplier() != null ? def.speedMultiplier() : config.d("sculk.infection.speed-multiplier", 1.2);
        double dmgMul = def.damageMultiplier() != null ? def.damageMultiplier() : config.d("sculk.infection.damage-multiplier", 1.3);

        multiply(entity, Attribute.MAX_HEALTH, hpMul);
        multiply(entity, Attribute.MOVEMENT_SPEED, speedMul);
        multiply(entity, Attribute.ATTACK_DAMAGE, dmgMul);
        var max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) entity.setHealth(Math.min(entity.getHealth() * hpMul, max.getValue()));

        // The entity may not be "valid" yet (CreatureSpawnEvent). Start tracking one tick later;
        // the behaviour task then attaches the model as soon as the entity is in the world.
        plugin.getServer().getScheduler().runTask(plugin, () -> tracker.track(entity));
    }

    private void multiply(LivingEntity e, Attribute attr, double multiplier) {
        var a = e.getAttribute(attr);
        if (a == null) return; // not every mob has every attribute (skeletons have no attack damage)
        a.setBaseValue(a.getBaseValue() * multiplier);
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

        MobDefinition def = registry.pick(null, "sculk", MobDefinition::emerge);
        if (def == null) return;

        Location spawn = findSpawnLocation(source);
        if (spawn == null) return;
        Entity raw = source.getWorld().spawnEntity(spawn, def.entity());
        if (!(raw instanceof LivingEntity mob)) { raw.remove(); return; }
        convert(mob, def, "emerged");

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


package dev.infectedmobs.infection;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.mob.MobDefinition;
import dev.infectedmobs.mob.MobRegistry;
import dev.infectedmobs.track.InfectedTracker;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MossInfectionManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MobRegistry registry;
    private final InfectedTracker tracker;
    private final CustomEffectManager effects;

    public MossInfectionManager(JavaPlugin plugin, ConfigManager config, MobRegistry registry,
                                InfectedTracker tracker, CustomEffectManager effects) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.tracker = tracker;
        this.effects = effects;
    }

    /** Revive candidate for a killed mob of this type, or null if none is configured. */
    public MobDefinition reviveDefinition(LivingEntity dead) {
        return registry.pick(dead.getType(), "moss", MobDefinition::revive);
    }

    public boolean nearMoss(LivingEntity e) {
        return InfectedUtil.nearMaterial(e.getLocation(), config.i("moss.infection.radius", 8),
                Material.MOSS_BLOCK, Material.MOSS_CARPET, Material.AZALEA, Material.FLOWERING_AZALEA);
    }

    public void convert(LivingEntity entity, MobDefinition def, String source, boolean halfHealth) {
        if (def == null || InfectedUtil.isInfected(entity, plugin)) return;
        InfectedUtil.mark(entity, plugin, "moss", source, def.id());

        double hpMul = def.hpMultiplier() != null ? def.hpMultiplier() : config.d("moss.infection.hp-multiplier", 1.3);
        double speedMul = def.speedMultiplier() != null ? def.speedMultiplier() : config.d("moss.infection.speed-multiplier", 1.1);

        var max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) max.setBaseValue(max.getBaseValue() * hpMul);
        var speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(speed.getBaseValue() * speedMul);

        if (halfHealth) entity.setHealth(Math.max(1.0, max != null ? max.getValue() / 2.0 : entity.getHealth() / 2.0));
        else if (max != null) entity.setHealth(max.getValue());

        if (InfectedUtil.isDay(entity.getWorld().getTime())) freeze(entity);
        else activate(entity);

        plugin.getServer().getScheduler().runTask(plugin, () -> tracker.track(entity));
    }

    public void freeze(LivingEntity e) {
        if (!InfectedUtil.is(e, plugin, "moss")) return;
        e.setAI(false);
        e.setVelocity(e.getVelocity().setX(0).setY(0).setZ(0));
    }

    public void activate(LivingEntity e) {
        if (!InfectedUtil.is(e, plugin, "moss")) return;
        e.setAI(true);
    }

    public void attackPlayer(Player player) {
        effects.applyMossSlow(player);
    }

    public void staticVisual(LivingEntity e) {
        e.getWorld().spawnParticle(Particle.BLOCK, e.getLocation().add(0, 1, 0),
                3, .35, .5, .35, Material.MOSS_BLOCK.createBlockData());
    }
}


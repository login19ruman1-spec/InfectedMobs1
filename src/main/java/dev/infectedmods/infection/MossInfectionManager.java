package dev.infectedmobs.infection;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.model.ModelManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;

public final class MossInfectionManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ModelManager models;
    private final CustomEffectManager effects;

    public MossInfectionManager(JavaPlugin plugin, ConfigManager config, ModelManager models, CustomEffectManager effects) {
        this.plugin = plugin; this.config = config; this.models = models; this.effects = effects;
    }

    public boolean canConvert(Entity e) {
        return e instanceof Zombie || e instanceof Skeleton || e instanceof Creeper;
    }

    public boolean nearMoss(LivingEntity e) {
        return InfectedUtil.nearMaterial(e.getLocation(), config.i("moss.infection.radius", 8),
                Material.MOSS_BLOCK, Material.MOSS_CARPET, Material.AZALEA, Material.FLOWERING_AZALEA);
    }

    public void convert(LivingEntity entity, String source, boolean halfHealth) {
        if (!canConvert(entity) || InfectedUtil.isInfected(entity, plugin)) return;
        InfectedUtil.mark(entity, plugin, "moss", source);

        var max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) max.setBaseValue(max.getBaseValue() * config.d("moss.infection.hp-multiplier", 1.3));
        var speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(speed.getBaseValue() * config.d("moss.infection.speed-multiplier", 1.1));

        if (halfHealth) entity.setHealth(Math.max(1.0, max != null ? max.getValue() / 2.0 : entity.getHealth() / 2.0));
        else if (max != null) entity.setHealth(max.getValue());

        models.attach(entity, modelFor(entity));
        if (InfectedUtil.isDay(entity.getWorld().getTime())) freeze(entity);
        else activate(entity);
    }

    public String modelFor(LivingEntity e) {
        return switch (e.getType()) {
            case ZOMBIE -> config.s("models.moss-zombie", "moss_infected_zombie");
            case SKELETON -> config.s("models.moss-skeleton", "moss_infected_skeleton");
            case CREEPER -> config.s("models.moss-creeper", "moss_infected_creeper");
            default -> null;
        };
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

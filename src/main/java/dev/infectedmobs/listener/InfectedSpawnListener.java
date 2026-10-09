package dev.infectedmobs.listener;

import dev.infectedmobs.InfectedMobsPlugin;
import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.infection.MossInfectionManager;
import dev.infectedmobs.infection.SculkInfectionManager;
import dev.infectedmobs.mob.MobDefinition;
import dev.infectedmobs.mob.MobRegistry;
import dev.infectedmobs.model.AnimationManager;
import dev.infectedmobs.track.InfectedTracker;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public final class InfectedSpawnListener implements Listener {
    private final InfectedMobsPlugin plugin;
    private final ConfigManager config;
    private final MobRegistry registry;
    private final InfectedTracker tracker;
    private final SculkInfectionManager sculk;
    private final MossInfectionManager moss;
    private final CustomEffectManager effects;
    private final AnimationManager animations;

    public InfectedSpawnListener(InfectedMobsPlugin plugin, ConfigManager config, MobRegistry registry,
                                 InfectedTracker tracker, SculkInfectionManager sculk, MossInfectionManager moss,
                                 CustomEffectManager effects, AnimationManager animations) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.tracker = tracker;
        this.sculk = sculk;
        this.moss = moss;
        this.effects = effects;
        this.animations = animations;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        LivingEntity e = event.getEntity();
        if (InfectedUtil.isInfected(e, plugin) || !InfectedUtil.isNight(e.getWorld().getTime())) return;

        MobDefinition def = sculk.naturalDefinition(e);
        if (def == null) return;
        if (InfectedUtil.nearMaterial(e.getLocation(), config.i("sculk.infection.radius", 16),
                Material.SCULK, Material.SCULK_VEIN, Material.SCULK_CATALYST)
                && ThreadLocalRandom.current().nextDouble() < config.d("sculk.infection.spawn-chance", .10)) {
            sculk.convert(e, def, "natural");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (InfectedUtil.isInfected(dead, plugin)) {
            if (InfectedUtil.is(dead, plugin, "sculk")) sculk.deathVisual(dead);
            animations.death(dead);
            tracker.untrack(dead);
            return;
        }
        if (!(dead.getKiller() instanceof Player)) return;
        MobDefinition def = moss.reviveDefinition(dead);
        if (def == null || !moss.nearMoss(dead)) return;
        if (ThreadLocalRandom.current().nextDouble() >= config.d("moss.infection.revive-chance", .15)) return;

        Location loc = dead.getLocation().clone();
        EntityType type = def.entity();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Entity spawned = loc.getWorld().spawnEntity(loc, type);
            if (spawned instanceof LivingEntity living) moss.convert(living, def, "revived", true);
            else spawned.remove();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (event.getFinalDamage() <= 0 || !InfectedUtil.isInfected(living, plugin)) return;
        if (living.getHealth() - event.getFinalDamage() <= 0) return; // lethal hit: the death animation takes over
        animations.hurt(living);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        LivingEntity attacker = null;
        boolean ranged = false;

        if (event.getDamager() instanceof LivingEntity living) {
            attacker = living;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity living) {
            attacker = living;
            ranged = true;
        }

        if (attacker == null || !InfectedUtil.isInfected(attacker, plugin)) return;
        MobDefinition def = registry.of(attacker);

        // Melee animation is tied to the real hit. Ranged animation is played on projectile launch.
        if (!ranged) animations.attack(attacker, false);

        // Infection effects only apply to players.
        if (!(event.getEntity() instanceof Player player)) return;

        if (!ranged && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            double maxDistance = def != null && def.meleeRange() != null
                    ? def.meleeRange() : config.d("combat.melee-effect-distance", 3.25);
            if (attacker.getLocation().distanceSquared(player.getLocation()) > maxDistance * maxDistance) {
                // FMM only changes the visual model; keep the real Bukkit combat range sane.
                event.setCancelled(true);
                return;
            }
        } else if (ranged) {
            double maxDistance = config.d("combat.ranged-effect-distance", 24.0);
            if (attacker.getLocation().distanceSquared(player.getLocation()) > maxDistance * maxDistance) return;
        }

        if (InfectedUtil.is(attacker, plugin, "sculk")) {
            sculk.attackPlayer(player);
        } else if (InfectedUtil.is(attacker, plugin, "moss")
                && InfectedUtil.isNight(attacker.getWorld().getTime())) {
            moss.attackPlayer(player);
        }
    }

    /**
     * Any infected mob that fires a projectile (skeleton arrow, pillager bolt, blaze fireball, ...)
     * plays its ranged attack animation, and the projectile can be moved to the model's hand.
     */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof LivingEntity shooter)) return;
        if (!InfectedUtil.isInfected(shooter, plugin)) return;

        animations.attack(shooter, true);

        MobDefinition def = registry.of(shooter);
        if (def == null || def.projectileOrigin() == null) return;
        MobDefinition.ProjectileOrigin origin = def.projectileOrigin();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!projectile.isValid() || !shooter.isValid()) return;
            Vector velocity = projectile.getVelocity();
            projectile.teleport(originLocation(shooter, origin));
            projectile.setVelocity(velocity);
        });
    }

    private Location originLocation(LivingEntity shooter, MobDefinition.ProjectileOrigin o) {
        Location base = shooter.getLocation().clone();
        double yaw = Math.toRadians(base.getYaw());
        // Minecraft yaw: forward = (-sin, cos), right = (-cos, -sin).
        double x = -Math.cos(yaw) * o.right() + -Math.sin(yaw) * o.forward();
        double z = -Math.sin(yaw) * o.right() + Math.cos(yaw) * o.forward();
        return base.add(x, o.up(), z);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || !InfectedUtil.is(living, plugin, "moss")) return;
        if (InfectedUtil.isDay(living.getWorld().getTime())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMilk(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() == Material.MILK_BUCKET) effects.clear(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        effects.clear(event.getEntity());
    }
}


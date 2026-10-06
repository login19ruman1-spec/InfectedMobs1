package dev.infectedmobs.listener;

import dev.infectedmobs.InfectedMobsPlugin;
import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.infection.MossInfectionManager;
import dev.infectedmobs.infection.SculkInfectionManager;
import dev.infectedmobs.model.AnimationManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public final class InfectedSpawnListener implements Listener {
    private final InfectedMobsPlugin plugin;
    private final ConfigManager config;
    private final SculkInfectionManager sculk;
    private final MossInfectionManager moss;
    private final CustomEffectManager effects;
    private final AnimationManager animations;

    public InfectedSpawnListener(InfectedMobsPlugin plugin, ConfigManager config,
                                 SculkInfectionManager sculk, MossInfectionManager moss,
                                 CustomEffectManager effects, AnimationManager animations) {
        this.plugin = plugin;
        this.config = config;
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

        if (sculk.canConvert(e)
                && InfectedUtil.nearMaterial(e.getLocation(), config.i("sculk.infection.radius", 16),
                Material.SCULK, Material.SCULK_VEIN, Material.SCULK_CATALYST)
                && ThreadLocalRandom.current().nextDouble() < config.d("sculk.infection.spawn-chance", .10)) {
            sculk.convert(e, "natural");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (InfectedUtil.is(dead, plugin, "sculk")) {
            animations.death(dead);
            plugin.models().remove(dead);
            sculk.deathVisual(dead);
            animations.remove(dead);
            return;
        }
        if (InfectedUtil.is(dead, plugin, "moss")) {
            animations.death(dead);
            plugin.models().remove(dead);
            animations.remove(dead);
            return;
        }
        if (!(dead.getKiller() instanceof Player)) return;
        if (!moss.canConvert(dead) || !moss.nearMoss(dead)) return;
        if (ThreadLocalRandom.current().nextDouble() >= config.d("moss.infection.revive-chance", .15)) return;

        Location loc = dead.getLocation().clone();
        EntityType type = dead.getType();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Entity spawned = loc.getWorld().spawnEntity(loc, type);
            if (spawned instanceof LivingEntity living) moss.convert(living, "revived", true);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

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

        double maxDistance = ranged
                ? config.d("combat.ranged-effect-distance", 24.0)
                : config.d("combat.melee-effect-distance", 3.25);
        if (attacker.getLocation().distanceSquared(player.getLocation()) > maxDistance * maxDistance) {
            // FMM only changes the visual model; keep the real Bukkit combat range sane.
            if (!ranged) event.setCancelled(true);
            return;
        }

        animations.attack(attacker);
        if (InfectedUtil.is(attacker, plugin, "sculk")) {
            sculk.attackPlayer(player);
        } else if (InfectedUtil.is(attacker, plugin, "moss")
                && InfectedUtil.isNight(attacker.getWorld().getTime())) {
            moss.attackPlayer(player);
        }
    }

    /** Move infected skeleton arrows to the bow hand instead of leaving the model's forehead. */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Skeleton skeleton)) return;
        if (!InfectedUtil.is(skeleton, plugin, "sculk") && !InfectedUtil.is(skeleton, plugin, "moss")) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!arrow.isValid() || !skeleton.isValid()) return;
            Location hand = handLocation(skeleton);
            arrow.teleport(hand);
        });
    }

    private Location handLocation(Skeleton skeleton) {
        Location base = skeleton.getLocation().clone().add(0, 1.0, 0);
        double yaw = Math.toRadians(base.getYaw());
        // Right hand: about 0.35 blocks to the entity's right and 1 block above feet.
        double x = -Math.cos(yaw) * 0.35;
        double z = -Math.sin(yaw) * 0.35;
        return base.add(x, 0, z);
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

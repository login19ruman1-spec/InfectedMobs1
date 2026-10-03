package dev.infectedmobs.listener;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.infection.MossInfectionManager;
import dev.infectedmobs.infection.SculkInfectionManager;
import dev.infectedmobs.model.ModelManager;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class InfectedSpawnListener implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final SculkInfectionManager sculk;
    private final MossInfectionManager moss;
    private final CustomEffectManager effects;

    public InfectedSpawnListener(JavaPlugin plugin, ConfigManager config,
                                 SculkInfectionManager sculk, MossInfectionManager moss,
                                 CustomEffectManager effects) {
        this.plugin = plugin; this.config = config; this.sculk = sculk; this.moss = moss; this.effects = effects;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        LivingEntity e = event.getEntity();
        if (InfectedUtil.isInfected(e, plugin)) return;
        if (!InfectedUtil.isNight(e.getWorld().getTime())) return;

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
            sculk.deathVisual(dead);
            return;
        }
        if (!(dead.getKiller() instanceof Player)) return;
        if (!moss.canConvert(dead) || !moss.nearMoss(dead)) return;
        if (ThreadLocalRandom.current().nextDouble() >= config.d("moss.infection.revive-chance", .15)) return;

        var loc = dead.getLocation().clone();
        EntityType type = dead.getType();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Entity spawned = loc.getWorld().spawnEntity(loc, type);
            if (spawned instanceof LivingEntity living) moss.convert(living, "revived", true);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (InfectedUtil.is(attacker, plugin, "sculk")) sculk.attackPlayer(player);
        else if (InfectedUtil.is(attacker, plugin, "moss")
                && InfectedUtil.isNight(attacker.getWorld().getTime())) moss.attackPlayer(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && InfectedUtil.is(living, plugin, "moss")
                && InfectedUtil.isDay(living.getWorld().getTime())) {
            event.setCancelled(true);
        }
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

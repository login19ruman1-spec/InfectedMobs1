package dev.infectedmobs.listener;

import dev.infectedmobs.InfectedMobsPlugin;
import dev.infectedmobs.util.InfectedUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.RayTraceResult;

/** Prevents an infected model's visual/body area from leaking interactions through blocks. */
public final class InfectedInteractionListener implements Listener {
    private final InfectedMobsPlugin plugin;

    public InfectedInteractionListener(InfectedMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof LivingEntity living
                && InfectedUtil.isInfected(living, plugin)) {
            // Right click must never deal damage or trigger vanilla/FMM interaction on infected mobs.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getAction().isRightClick() || event.getAction().isLeftClick()) {
            if (infectedEntityIsInFront(event.getPlayer(), 6.0)) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (infectedEntityIsInFront(event.getPlayer(), 6.0)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (infectedEntityIsInFront(event.getPlayer(), 6.0)) event.setCancelled(true);
    }

    private boolean infectedEntityIsInFront(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(
                eye,
                eye.getDirection(),
                maxDistance,
                0.25,
                entity -> entity instanceof LivingEntity living
                        && entity != player
                        && InfectedUtil.isInfected(living, plugin));
        return result != null && result.getHitEntity() != null;
    }
}

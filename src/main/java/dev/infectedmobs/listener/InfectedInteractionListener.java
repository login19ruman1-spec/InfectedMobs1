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
            if (infectedEntityBlocksTarget(event.getPlayer(), event.getClickedBlock())) {
                event.setCancelled(true);
                event.setUseInteractedBlock(PlayerInteractEvent.Result.DENY);
                event.setUseItemInHand(PlayerInteractEvent.Result.DENY);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (infectedEntityBlocksTarget(event.getPlayer(), event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (infectedEntityBlocksTarget(event.getPlayer(), event.getBlock())) event.setCancelled(true);
    }

    /**
     * Only cancel a block interaction when the infected entity is actually
     * between the player's eyes and the clicked block. The previous version
     * cancelled every block click whenever any infected mob was somewhere in
     * the player's 6-block ray, which made normal mining feel broken.
     */
    private boolean infectedEntityBlocksTarget(Player player, org.bukkit.block.Block block) {
        Location eye = player.getEyeLocation();
        double blockDistance = eye.distance(block.getLocation().add(0.5, 0.5, 0.5));
        if (blockDistance <= 0.0) return false;

        RayTraceResult result = player.getWorld().rayTraceEntities(
                eye,
                eye.getDirection(),
                Math.min(6.0, blockDistance + 0.05),
                0.35,
                entity -> entity instanceof LivingEntity living
                        && entity != player
                        && InfectedUtil.isInfected(living, plugin));
        if (result == null || result.getHitEntity() == null) return false;

        return result.getHitEntity().getLocation().distanceSquared(eye)
                < blockDistance * blockDistance;
    }
}

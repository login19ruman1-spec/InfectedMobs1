package dev.infectedmobs.listener;

import dev.infectedmobs.track.InfectedTracker;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Re-registers infected mobs when their chunk loads (after a restart, or after walking back to them). */
public final class TrackingListener implements Listener {
    private final InfectedTracker tracker;

    public TrackingListener(InfectedTracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof LivingEntity living) tracker.track(living);
        }
    }
}

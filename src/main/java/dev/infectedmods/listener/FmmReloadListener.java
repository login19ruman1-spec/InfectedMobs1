package dev.infectedmobs.listener;

import com.magmaguy.freeminecraftmodels.api.FmmReloadedEvent;
import dev.infectedmobs.InfectedMobsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class FmmReloadListener implements Listener {
    private final InfectedMobsPlugin plugin;
    public FmmReloadListener(InfectedMobsPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onFmmReload(FmmReloadedEvent event) {
        plugin.getLogger().info("FMM reload completed; reattaching infected mob models...");
        plugin.models().reattachAll();
    }
}

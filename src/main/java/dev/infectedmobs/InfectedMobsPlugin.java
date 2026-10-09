package dev.infectedmobs;

import dev.infectedmobs.command.InfectedMobsCommand;
import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.infection.MossInfectionManager;
import dev.infectedmobs.infection.SculkInfectionManager;
import dev.infectedmobs.listener.FmmReloadListener;
import dev.infectedmobs.listener.InfectedInteractionListener;
import dev.infectedmobs.listener.InfectedSpawnListener;
import dev.infectedmobs.listener.TrackingListener;
import dev.infectedmobs.mob.MobRegistry;
import dev.infectedmobs.model.AnimationManager;
import dev.infectedmobs.model.ModelManager;
import dev.infectedmobs.task.InfectionBehaviorTask;
import dev.infectedmobs.track.InfectedTracker;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfectedMobsPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private MobRegistry registry;
    private InfectedTracker tracker;
    private ModelManager modelManager;
    private AnimationManager animationManager;
    private CustomEffectManager effectManager;
    private SculkInfectionManager sculkManager;
    private MossInfectionManager mossManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        registry = new MobRegistry(this, configManager);
        registry.load();
        tracker = new InfectedTracker(this);
        modelManager = new ModelManager(this, configManager);
        animationManager = new AnimationManager(this, modelManager);
        modelManager.setAnimations(animationManager);
        effectManager = new CustomEffectManager(this, configManager);
        sculkManager = new SculkInfectionManager(this, configManager, registry, tracker, effectManager);
        mossManager = new MossInfectionManager(this, configManager, registry, tracker, effectManager);

        var cmd = new InfectedMobsCommand(this);
        if (getCommand("infectedmobs") != null) {
            getCommand("infectedmobs").setExecutor(cmd);
            getCommand("infectedmobs").setTabCompleter(cmd);
        }

        getServer().getPluginManager().registerEvents(
                new InfectedSpawnListener(this, configManager, registry, tracker,
                        sculkManager, mossManager, effectManager, animationManager), this);
        getServer().getPluginManager().registerEvents(new FmmReloadListener(this), this);
        getServer().getPluginManager().registerEvents(new InfectedInteractionListener(this), this);
        getServer().getPluginManager().registerEvents(new TrackingListener(tracker), this);

        new InfectionBehaviorTask(this, configManager, registry, tracker, modelManager,
                sculkManager, mossManager, animationManager).runTaskTimer(this, 1L, 1L);

        // Pick up infected mobs that are already loaded.
        getServer().getScheduler().runTask(this, tracker::scanLoaded);

        getLogger().info("InfectedMobs enabled for Paper 1.21.4+.");
    }

    @Override
    public void onDisable() {
        if (effectManager != null) effectManager.shutdown();
        if (modelManager != null) modelManager.removeAllModels();
    }

    /** /infectedmobs reload: re-read config.yml (including the mobs: list) and rebuild all models. */
    public void reloadAll() {
        reloadConfig();
        registry.load();
        reattachAll();
    }

    /** Re-creates the FMM model of every loaded infected mob (after /fmm reload or /infectedmobs reload). */
    public void reattachAll() {
        modelManager.reattachAll(tracker, registry);
    }

    public ConfigManager config() { return configManager; }
    public MobRegistry registry() { return registry; }
    public InfectedTracker tracker() { return tracker; }
    public ModelManager models() { return modelManager; }
    public CustomEffectManager effects() { return effectManager; }
    public AnimationManager animations() { return animationManager; }
    public SculkInfectionManager sculk() { return sculkManager; }
    public MossInfectionManager moss() { return mossManager; }
}


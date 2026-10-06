package dev.infectedmobs;

import dev.infectedmobs.config.ConfigManager;
import dev.infectedmobs.command.InfectedMobsCommand;
import dev.infectedmobs.effect.CustomEffectManager;
import dev.infectedmobs.infection.MossInfectionManager;
import dev.infectedmobs.infection.SculkInfectionManager;
import dev.infectedmobs.listener.InfectedSpawnListener;
import dev.infectedmobs.listener.FmmReloadListener;
import dev.infectedmobs.model.ModelManager;
import dev.infectedmobs.model.AnimationManager;
import dev.infectedmobs.task.InfectionBehaviorTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfectedMobsPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private ModelManager modelManager;
    private AnimationManager animationManager;
    private CustomEffectManager effectManager;
    private SculkInfectionManager sculkManager;
    private MossInfectionManager mossManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        modelManager = new ModelManager(this, configManager);
        animationManager = new AnimationManager(this, configManager, modelManager);
        effectManager = new CustomEffectManager(this, configManager);
        sculkManager = new SculkInfectionManager(this, configManager, modelManager, effectManager);
        mossManager = new MossInfectionManager(this, configManager, modelManager, effectManager);

        var cmd = new InfectedMobsCommand(this);
        if (getCommand("infectedmobs") != null) {
            getCommand("infectedmobs").setExecutor(cmd);
            getCommand("infectedmobs").setTabCompleter(cmd);
        }

        getServer().getPluginManager().registerEvents(
                new InfectedSpawnListener(this, configManager, sculkManager, mossManager, effectManager, animationManager), this);
        getServer().getPluginManager().registerEvents(new FmmReloadListener(this), this);

        InfectionBehaviorTask behaviorTask =
                new InfectionBehaviorTask(this, configManager, sculkManager, mossManager, effectManager, animationManager);
        behaviorTask.runTaskTimer(this, 20L, 20L);

        getLogger().info("InfectedMobs enabled for Paper 1.21.4+.");
    }

    @Override
    public void onDisable() {
        if (effectManager != null) effectManager.shutdown();
        if (modelManager != null) modelManager.removeAllModels();
    }

    public ConfigManager config() { return configManager; }
    public ModelManager models() { return modelManager; }
    public CustomEffectManager effects() { return effectManager; }
    public AnimationManager animations() { return animationManager; }
    public SculkInfectionManager sculk() { return sculkManager; }
    public MossInfectionManager moss() { return mossManager; }
}

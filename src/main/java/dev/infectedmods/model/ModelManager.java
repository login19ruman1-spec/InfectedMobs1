package dev.infectedmobs.model;

import com.magmaguy.freeminecraftmodels.customentity.DynamicEntity;
import com.magmaguy.freeminecraftmodels.customentity.ModeledEntity;
import dev.infectedmobs.config.ConfigManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, ModeledEntity> models = new ConcurrentHashMap<>();

    public ModelManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void attach(LivingEntity entity, String modelId) {
        if (!config.b("models.use-fmm", true)) return;
        if (!entity.isValid()) return;
        remove(entity);
        ModeledEntity model = DynamicEntity.create(modelId, entity);
        if (model != null) models.put(entity.getUniqueId(), model);
    }

    public void ensureAttached(LivingEntity entity, String modelId) {
        ModeledEntity current = models.get(entity.getUniqueId());
        if (current == null || current.isRemoved()) attach(entity, modelId);
    }

    public void remove(LivingEntity entity) {
        ModeledEntity old = models.remove(entity.getUniqueId());
        if (old != null && !old.isRemoved()) old.remove();
    }

    public void removeAllModels() {
        models.values().forEach(m -> { if (m != null && !m.isRemoved()) m.remove(); });
        models.clear();
    }

    public void reattachAll() {
        // FMM reload destroys its display entities. Recreate our attachments.
        models.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isRemoved());
        plugin.getServer().getWorlds().forEach(w ->
            w.getLivingEntities().forEach(e -> {
                if (isInfected(e)) {
                    String type = e.getPersistentDataContainer().get(
                            new org.bukkit.NamespacedKey(plugin, "infected_type"),
                            org.bukkit.persistence.PersistentDataType.STRING);
                    if (type != null) {
                        String id = modelId(type, e);
                        if (id != null) attach(e, id);
                    }
                }
            }));
    }

    private boolean isInfected(LivingEntity e) {
        return e.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, "infected_type"),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    private String modelId(String type, LivingEntity e) {
        String key = switch (type) {
            case "sculk" -> "sculk-" + baseName(e);
            case "moss" -> "moss-" + baseName(e);
            default -> null;
        };
        return key == null ? null : config.s("models." + key, null);
    }

    private String baseName(LivingEntity e) {
        return switch (e.getType()) {
            case ZOMBIE -> "zombie";
            case SKELETON -> "skeleton";
            case SPIDER -> "spider";
            case CREEPER -> "creeper";
            default -> e.getType().name().toLowerCase();
        };
    }
}

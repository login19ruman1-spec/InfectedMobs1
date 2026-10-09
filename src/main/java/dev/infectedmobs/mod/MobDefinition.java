package dev.infectedmobs.mob;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * One entry of the {@code mobs:} section in config.yml.
 * Adding a new infected mob = adding a new entry there. No code changes needed.
 */
public final class MobDefinition {
    public enum AttackTrigger { DAMAGE, PROXIMITY }

    /** Where a projectile should appear relative to the shooter (blocks). */
    public record ProjectileOrigin(double right, double up, double forward) {}

    /** Animation states the plugin knows about, with the default animation names looked up in the model. */
    private static final Map<String, List<String>> DEFAULT_ANIMATIONS = Map.of(
            "spawn", List.of("spawn"),
            "idle", List.of("idle"),
            "walk", List.of("walk"),
            "attack", List.of("attack"),
            "ranged-attack", List.of("attack_ranged", "ranged_attack"),
            "hurt", List.of("hurt")
    );

    private final String id;
    private final EntityType entity;
    private final String infection;
    private final String model;
    private final boolean natural;
    private final boolean emerge;
    private final boolean revive;
    private final double weight;
    private final Double hpMultiplier;
    private final Double speedMultiplier;
    private final Double damageMultiplier;
    private final Double meleeRange;
    private final AttackTrigger attackTrigger;
    private final double attackTriggerRange;
    private final int spawnLockTicks;
    private final int attackLockTicks;
    private final int hurtLockTicks;
    private final ProjectileOrigin projectileOrigin;
    private final Map<String, List<String>> animations;

    private MobDefinition(String id, EntityType entity, String infection, String model, ConfigurationSection s) {
        this.id = id;
        this.entity = entity;
        this.infection = infection;
        this.model = model;
        this.natural = s.getBoolean("natural", true);
        this.emerge = s.getBoolean("emerge", true);
        this.revive = s.getBoolean("revive", true);
        this.weight = Math.max(0.0001, s.getDouble("weight", 1.0));
        this.hpMultiplier = s.contains("hp-multiplier") ? s.getDouble("hp-multiplier") : null;
        this.speedMultiplier = s.contains("speed-multiplier") ? s.getDouble("speed-multiplier") : null;
        this.damageMultiplier = s.contains("damage-multiplier") ? s.getDouble("damage-multiplier") : null;
        this.meleeRange = s.contains("melee-range") ? s.getDouble("melee-range") : null;

        AttackTrigger trigger = AttackTrigger.DAMAGE;
        String rawTrigger = s.getString("attack-trigger", "damage");
        if ("proximity".equalsIgnoreCase(rawTrigger)) trigger = AttackTrigger.PROXIMITY;
        this.attackTrigger = trigger;
        this.attackTriggerRange = s.getDouble("attack-trigger-range", 3.0);

        this.spawnLockTicks = Math.max(1, s.getInt("spawn-lock-ticks", 12));
        this.attackLockTicks = Math.max(1, s.getInt("attack-lock-ticks", 14));
        this.hurtLockTicks = Math.max(1, s.getInt("hurt-lock-ticks", 6));

        ConfigurationSection po = s.getConfigurationSection("projectile-origin");
        this.projectileOrigin = po == null ? null
                : new ProjectileOrigin(po.getDouble("right", 0.0), po.getDouble("up", 1.0), po.getDouble("forward", 0.0));

        Map<String, List<String>> anims = new HashMap<>(DEFAULT_ANIMATIONS);
        ConfigurationSection as = s.getConfigurationSection("animations");
        if (as != null) {
            for (String state : DEFAULT_ANIMATIONS.keySet()) {
                if (!as.contains(state)) continue;
                List<String> names = new ArrayList<>();
                if (as.isList(state)) names.addAll(as.getStringList(state));
                else if (as.getString(state) != null) names.add(as.getString(state));
                names.removeIf(n -> n == null || n.isBlank());
                anims.put(state, names);
            }
        }
        this.animations = anims;
    }

    /** @return the parsed definition, or null (with a logged reason) if the entry is invalid. */
    public static MobDefinition parse(String id, ConfigurationSection s, Logger log) {
        String rawEntity = s.getString("entity");
        if (rawEntity == null) {
            log.warning("mobs." + id + ": missing 'entity' (e.g. ZOMBIE). Skipped.");
            return null;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(rawEntity.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warning("mobs." + id + ": unknown entity '" + rawEntity + "'. Use a Bukkit EntityType name. Skipped.");
            return null;
        }
        Class<?> cls = type.getEntityClass();
        if (type == EntityType.PLAYER || cls == null || !LivingEntity.class.isAssignableFrom(cls)) {
            log.warning("mobs." + id + ": entity '" + rawEntity + "' is not a spawnable living mob. Skipped.");
            return null;
        }
        String infection = s.getString("infection", "").trim().toLowerCase(Locale.ROOT);
        if (!infection.equals("sculk") && !infection.equals("moss")) {
            log.warning("mobs." + id + ": 'infection' must be 'sculk' or 'moss'. Skipped.");
            return null;
        }
        String model = s.getString("model");
        if (model == null || model.isBlank()) {
            log.warning("mobs." + id + ": missing 'model' (the .bbmodel file name without extension). Skipped.");
            return null;
        }
        return new MobDefinition(id, type, infection, model.trim(), s);
    }

    public String id() { return id; }
    public EntityType entity() { return entity; }
    public String infection() { return infection; }
    public String model() { return model; }
    public boolean natural() { return natural; }
    public boolean emerge() { return emerge; }
    public boolean revive() { return revive; }
    public double weight() { return weight; }
    public Double hpMultiplier() { return hpMultiplier; }
    public Double speedMultiplier() { return speedMultiplier; }
    public Double damageMultiplier() { return damageMultiplier; }
    public Double meleeRange() { return meleeRange; }
    public AttackTrigger attackTrigger() { return attackTrigger; }
    public double attackTriggerRange() { return attackTriggerRange; }
    public int spawnLockTicks() { return spawnLockTicks; }
    public int attackLockTicks() { return attackLockTicks; }
    public int hurtLockTicks() { return hurtLockTicks; }
    public ProjectileOrigin projectileOrigin() { return projectileOrigin; }

    /** Candidate animation names for a state, in priority order. The first one present in the model wins. */
    public List<String> animation(String state) {
        return animations.getOrDefault(state, List.of());
    }
}

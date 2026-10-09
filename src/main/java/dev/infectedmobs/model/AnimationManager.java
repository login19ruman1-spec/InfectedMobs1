package dev.infectedmobs.model;

import dev.infectedmobs.mob.MobDefinition;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-mob animation controller on top of
 * FreeMinecraftModels' own state machine.
 *
 * Why this works where the old one did not: FMM
 * switches idle/walk by itself every tick, using the
 * mob's Bukkit velocity, and it silently drops our
 * "walk" back to "idle" whenever that velocity reads
 * 0.
 * FMM's play(name, blend=true, ...) only QUEUES a
 * state, and a queued state beats FMM's own automatic
 * transition. So every tick we queue the state we
 * decided on (idle or walk, from real position
 * change).
 * Queuing the state that is already active is a
 * no-op in FMM, so the animation does not restart.
 *
 * One shot actions (attack, ranged attack, hurt)
 * interrupt immediately and lock idle/walk for
 * a configured number of ticks (the length of the
 * animation), then the base state is queued again.
 *
 * Names FMM hard-codes and that must be used
 * in Blockbench: idle, walk, attack, death, spawn.
 * Extra names (attack_ranged, hurt, ...) are played
 * as FMM "custom" animations.
 */
public final class AnimationManager {
    private static final double MOVE_EPS_SQ = 0.0009; // ~0.03 blocks per tick
    private static final int START_WALK_TICKS = 2;
    private static final int STOP_WALK_TICKS = 4;

    private static final class State {
        final MobDefinition def;
        String mode = "none"; // none | idle | walk | action
        long lockUntil;
        double lastX, lastZ;
        int moving, still;
        State(MobDefinition def) { this.def = def; }
    }

    private final JavaPlugin plugin;
    private final ModelManager models;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private long now;

    public AnimationManager(JavaPlugin plugin, ModelManager models) {
        this.plugin = plugin;
        this.models = models;
    }

    /** Called once per server tick by the behaviour task, before any mob is ticked. */
    public void advance() { now++; }

    /** Called right after a model was attached. */
    public void initialize(LivingEntity entity, MobDefinition def) {
        State s = new State(def);
        s.lastX = entity.getX();
        s.lastZ = entity.getZ();
        states.put(entity.getUniqueId(), s);

        validate(entity, def);

        // FMM starts the "spawn" animation by itself
        // when the model has one, and moves on to idle.
        // We only have to keep idle/walk from cutting
        // it short.
        if (models.hasAnimation(entity, "spawn")) {
            s.mode = "action";
            s.lockUntil = now + def.spawnLockTicks();
        }
    }

    /** Called every tick for every infected mob that currently has a live model. */
    public void tick(LivingEntity entity, MobDefinition def) {
        State s = states.get(entity.getUniqueId());
        if (s == null) return;
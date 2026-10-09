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
 * Per-mob animation state machine. Works for ANY model: it only needs the animation names
 * from the mob's config entry (defaults: spawn, idle, walk, attack, attack_ranged, hurt).
 * Death is always played through FMM, so the death animation in Blockbench must be named "death".
 *
 * Rules:
 *  - idle/walk are looping base states, chosen from real horizontal movement (with hysteresis,
 *    so a jittering mob does not flicker between idle and walk);
 *  - spawn / attack / ranged attack / hurt are one-shot "actions" that lock the base state
 *    for a configurable number of ticks, then the base state is restored;
 *  - an action never restarts itself while it is still running;
 *  - any missing animation is skipped gracefully (walk falls back to idle, ranged to attack).
 */
public final class AnimationManager {
    private static final double MOVE_EPS_SQ = 0.0009; // ~0.03 blocks per tick
    private static final int START_WALK_TICKS = 2;
    private static final int STOP_WALK_TICKS = 4;

    private static final class State {
        final MobDefinition def;
        String mode = "none";   // none | idle | walk | action
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

        String spawn = resolve(entity, def, "spawn");
        if (spawn != null) {
            models.stopAnimations(entity);
            if (models.playAnimation(entity, spawn, false, false)) {
                s.mode = "action";
                s.lockUntil = now + def.spawnLockTicks();
                return;
            }
        }
        playBase(entity, s, "idle");
    }

    /** Called every tick for every infected mob that currently has a live model. */
    public void tick(LivingEntity entity, MobDefinition def) {
        State s = states.get(entity.getUniqueId());
        if (s == null) return;

        double dx = entity.getX() - s.lastX;
        double dz = entity.getZ() - s.lastZ;
        s.lastX = entity.getX();
        s.lastZ = entity.getZ();
        // A frozen mob (AI off) must never walk, even if it gets pushed.
        boolean moved = entity.hasAI() && (dx * dx + dz * dz) > MOVE_EPS_SQ;
        if (moved) { s.moving++; s.still = 0; } else { s.still++; s.moving = 0; }

        // Mobs whose "attack" is not a damage hit (creeper fuse): trigger by target proximity.
        if (def.attackTrigger() == MobDefinition.AttackTrigger.PROXIMITY
                && entity instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isValid()) {
            double r = def.attackTriggerRange();
            if (entity.getLocation().distanceSquared(mob.getTarget().getLocation()) <= r * r) {
                attack(entity, false);
            }
        }

        if (now < s.lockUntil) return;

        String desired;
        if (s.mode.equals("walk")) desired = s.still >= STOP_WALK_TICKS ? "idle" : "walk";
        else desired = s.moving >= START_WALK_TICKS ? "walk" : "idle";

        if (!desired.equals(s.mode)) playBase(entity, s, desired);
    }

    /** Melee / ranged / fuse attack. Ignored while another action is still running. */
    public void attack(LivingEntity entity, boolean ranged) {
        State s = states.get(entity.getUniqueId());
        if (s == null || now < s.lockUntil) return;

        String name = ranged ? resolve(entity, s.def, "ranged-attack") : null;
        if (name == null) name = resolve(entity, s.def, "attack");
        if (name == null) return;

        playAction(entity, s, name, s.def.attackLockTicks());
    }

    /** Optional "hurt" animation. Never interrupts an attack or spawn animation. */
    public void hurt(LivingEntity entity) {
        State s = states.get(entity.getUniqueId());
        if (s == null || now < s.lockUntil) return;
        String name = resolve(entity, s.def, "hurt");
        if (name == null) return;
        playAction(entity, s, name, s.def.hurtLockTicks());
    }

    public void death(LivingEntity entity) {
        // FMM keeps the model alive long enough to show the "death" animation, then removes it.
        models.removeWithDeathAnimation(entity);
        states.remove(entity.getUniqueId());
    }

    public void forget(UUID id) { states.remove(id); }

    /** Debug helper for /infectedmobs anim: plays any animation by exact name. */
    public boolean debugPlay(LivingEntity entity, String animation, boolean loop) {
        State s = states.get(entity.getUniqueId());
        if (s == null || !models.hasAnimation(entity, animation)) return false;
        models.stopAnimations(entity);
        if (!models.playAnimation(entity, animation, false, loop)) return false;
        s.mode = "action";
        s.lockUntil = now + (loop ? 200 : 40);
        return true;
    }

    /** For /infectedmobs info: which model animation each state resolves to. */
    public List<String> describe(LivingEntity entity, MobDefinition def) {
        List<String> lines = new ArrayList<>();
        for (String state : new String[]{"spawn", "idle", "walk", "attack", "ranged-attack", "hurt"}) {
            String found = resolve(entity, def, state);
            lines.add(state + " -> " + (found == null ? "(not in model: " + def.animation(state) + ")" : found));
        }
        lines.add("death -> " + (models.hasAnimation(entity, "death") ? "death" : "(not in model: death)"));
        return lines;
    }

    // ------------------------------------------------------------------------------------------

    private void playAction(LivingEntity entity, State s, String name, int lockTicks) {
        // Stop the running loop first, otherwise FMM can keep it active and swallow the one-shot.
        models.stopAnimations(entity);
        if (models.playAnimation(entity, name, false, false)) {
            s.mode = "action";
            s.lockUntil = now + lockTicks;
        } else {
            // Could not play: restore the base state on the next tick instead of staying blank.
            s.mode = "none";
        }
    }

    private void playBase(LivingEntity entity, State s, String want) {
        String name = resolve(entity, s.def, want);
        if (name == null && !want.equals("idle")) name = resolve(entity, s.def, "idle");
        s.mode = want; // set even on failure so we do not retry every tick
        if (name == null) return;
        models.stopAnimations(entity);
        models.playAnimation(entity, name, false, true);
    }

    /** First configured animation name for the state that really exists in the model. */
    private String resolve(LivingEntity entity, MobDefinition def, String state) {
        for (String name : def.animation(state)) {
            if (models.hasAnimation(entity, name)) return name;
        }
        return null;
    }

    private void validate(LivingEntity entity, MobDefinition def) {
        for (String state : new String[]{"idle", "walk", "attack"}) {
            if (resolve(entity, def, state) == null && warned.add(def.model() + "/" + state)) {
                plugin.getLogger().warning("Model '" + def.model() + "' (mob '" + def.id() + "') has no '"
                        + state + "' animation. Looked for " + def.animation(state)
                        + ". Add it in Blockbench or change mobs." + def.id() + ".animations." + state + " in config.yml.");
            }
        }
        if (!models.hasAnimation(entity, "death") && warned.add(def.model() + "/death")) {
            plugin.getLogger().warning("Model '" + def.model() + "' has no 'death' animation. "
                    + "The model will just disappear when the mob dies (FMM requires the name 'death').");
        }
    }
}

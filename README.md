# InfectedMobs

Paper 1.21.4+ plugin with two infection systems: Sculk and Moss.

## Requirements

- Paper 1.21.4+
- Java 21
- FreeMinecraftModels 2.12.3 (installed separately)
- Blockbench `.bbmodel` models

FMM currently documents Java 21 and Paper/Spigot 1.21.4+, and exposes `DynamicEntity.create(modelId, livingEntity)` for model attachments. FMM is a separate server plugin and is a compile-only dependency.

## Models

Put the loose `.bbmodel` files into `plugins/FreeMinecraftModels/imports/` for conversion. After `/fmm reload`, FMM creates/uses the active `.fmmodel` files under `plugins/FreeMinecraftModels/models/`.

- `sculk_infected_zombie`
- `sculk_infected_skeleton`
- `sculk_infected_spider`
- `moss_infected_zombie`
- `moss_infected_skeleton`
- `moss_infected_creeper`

Then run `/fmm reload`.

## Build

Local:

```bash
gradle clean build
```

Output:

`build/libs/InfectedMobs-1.0.0.jar`

### GitHub compiler

`.github/workflows/build.yml` is included. Every push and pull request uses Java 21 + Gradle 8.10.2 and uploads the compiled JAR as an Actions artifact.

Create a GitHub repository, upload this project, push it, then open **Actions → Build InfectedMobs**. Download the artifact from a successful run.

## Mechanics

### Sculk

- Natural zombie/skeleton/spider spawns are converted only if night and sculk is within the configured radius.
- Vanilla spawning is not cancelled.
- Every configured interval, players near sculk can trigger a 3% emergence roll.
- Emerged mobs rise for 20 ticks with sculk particles/sound.
- HP +50%, speed +20%, damage +30%.
- Attacks add custom `SCULK_INFECTION` stacks.
- Death creates sculk particles.

### Moss

- A zombie/skeleton/creeper killed by a player near moss has a 15% revival roll.
- The revived mob is marked as moss-infected, so it cannot revive again.
- Day: AI off, still damageable, moss particles.
- Night: AI on and attacks players.
- HP +30%, speed +10%.
- Attacks apply the custom `MOSS_SLOW` timer.

## Custom effects

`CustomEffectManager` uses PDC/state and `BukkitRunnable`, not Bukkit potion effects.

`SCULK_INFECTION`:
- 3-stack maximum
- 10-second default duration
- 2-second default damage interval
- damage scales with stacks
- milk/death clears it
- moving 32+ blocks away from sculk clears it

`MOSS_SLOW`:
- custom timer
- movement restriction
- moss particles

True client-side blindness is not available through the public Paper/Bukkit API without a potion effect or version-specific packet/NMS implementation. This project intentionally does not sneak in a vanilla blindness potion. The architecture leaves room for a 1.21.4 packet adapter if exact blindness is required.

## PDC

- `infected_type`: `sculk` or `moss`
- `infection_source`: `natural`, `emerged`, or `revived`

## Config

All probabilities, radii, multipliers and intervals are in `src/main/resources/config.yml`.

## FMM API

The project uses:

```java
DynamicEntity.create("sculk_infected_zombie", entity);
```

not the old BetterModel example `BetterModel.model(...)`.

FMM must remain installed separately and must not be shaded into this plugin.

## Performance

The behavior loop runs once per second. Sculk emergence uses the configured 100-tick interval by default. Infected mobs are filtered from each world's living-entity list; there is no repeated global server scan.

## Blockbench workflow

1. Build and paint each model in Blockbench.
2. Save/export `.bbmodel`.
3. Copy the `.bbmodel` to `plugins/FreeMinecraftModels/imports/`.
4. Run `/fmm reload`.
5. Make sure the client receives `plugins/FreeMinecraftModels/output/FreeMinecraftModels.zip`.
5. Make sure the model ID matches `config.yml`.
6. Run InfectedMobs.

## Files

- `InfectedMobsPlugin.java`
- `ConfigManager.java`
- `ModelManager.java`
- `SculkInfectionManager.java`
- `MossInfectionManager.java`
- `CustomEffectManager.java`
- `InfectedSpawnListener.java`
- `InfectionBehaviorTask.java`
- `config.yml`
- `plugin.yml`
- `.github/workflows/build.yml`

## IMPORTANT: first test

After installing both JARs and starting the server:

1. Put `sculk_infected_zombie.bbmodel` into `plugins/FreeMinecraftModels/imports/`.
2. Run `/fmm reload`.
3. Check `/fmm stats` and the console for import errors.
4. Make sure your client has accepted the generated resource pack. FMM generates it at `plugins/FreeMinecraftModels/output/FreeMinecraftModels.zip`.
5. Run `/infectedmobs spawn sculk_zombie`.
6. Run `/infectedmobs info`.

If the command spawns a normal zombie but the model is not visible, the infection plugin is working and the problem is FMM model loading/resource-pack delivery. If `/infectedmobs spawn sculk_zombie` says the model is not loaded, fix the FMM import first.

FMM's current documentation states that loose `.bbmodel` files belong in `imports/`, `/fmm reload` converts them, and the generated resource pack must actually be delivered to clients. It also states that `DynamicEntity.create(...)` returns null when the model ID is not loaded.

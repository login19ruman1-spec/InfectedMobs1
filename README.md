# InfectedMobs — Paper 1.21.4+

Plugin for Paper 1.21.4+ / Java 21 with Sculk and Moss infected hostile mobs. FreeMinecraftModels (FMM) is a required server plugin and is used only at runtime (`compileOnly`).

## Included in this revision

- Sculk infection for zombie, skeleton, spider and creeper.
- Moss infection for zombie, skeleton and creeper.
- FMM model attachment and reattachment after `/fmm reload`.
- Blockbench/FMM animations: `idle`, `walk`, `attack`, `attack_ranged`, `death` where present.
- Skeleton bow model attached to the right arm.
- Infected skeleton arrows are moved to the bow-hand position immediately after launch so they do not visually originate from the forehead.
- Direct melee hits from infected mobs are rejected outside the configured combat range.
- Sculk infection and Moss slow are custom effects implemented with PDC + scheduler; no Bukkit potion effects are required.
- Effects work for both melee attacks and skeleton projectiles.
- Sculk emergence keeps normal gravity/AI instead of lifting the mob into the air.
- Creeper charge uses the custom `attack` animation while the vanilla creeper is ignited.

## Build

Use Java 21 and Gradle 8.10.2+:

```text
gradle clean build
```

The GitHub Actions workflow installs Gradle 8.10.2 and builds `build/libs/InfectedMobs-*.jar`.

## Server installation

1. Install Paper 1.21.4+ and Java 21.
2. Install FreeMinecraftModels 2.12.3 (or keep the dependency version matched to the installed FMM version).
3. Put `InfectedMobs-*.jar` into `plugins/`.
4. Put the `.bbmodel` files into the FMM import/model location used by your installed FMM version.
5. Make sure players receive the FMM generated resource pack.
6. Run `/fmm reload`.
7. Run `/infectedmobs info` and test with `/infectedmobs spawn sculk_skeleton` or `/infectedmobs spawn sculk_creeper`.

## Sounds

You do **not** need to add sound files for the current build. It uses vanilla Minecraft sounds such as sculk and moss block sounds. Custom `.ogg` sounds are only needed if you want unique infection/attack sounds. Those require adding the sound to the resource pack and registering/playing the custom namespaced sound.

## Test commands

```text
/infectedmobs spawn sculk_zombie
/infectedmobs spawn sculk_skeleton
/infectedmobs spawn sculk_spider
/infectedmobs spawn sculk_creeper
/infectedmobs spawn moss_zombie
/infectedmobs spawn moss_skeleton
/infectedmobs spawn moss_creeper
/infectedmobs info
```

## Important model note

FMM's model is visual. The actual hitbox and AI remain the underlying Bukkit entity. Therefore the plugin also checks combat distance for direct melee attacks instead of relying on the apparent size of the custom model.

## Animation contract

InfectedMobs uses FreeMinecraftModels `DynamicEntity` for every infected mob. The plugin reserves these animation names for every model:

- `idle` — looping idle pose
- `walk` — looping movement pose
- `attack` — one-shot melee attack
- `death` — one-shot death animation
- `spawn` — optional one-shot spawn animation; FMM automatically falls back to `idle` when it is absent

Skeletons may additionally use `attack_ranged`; InfectedMobs uses it for infected skeletons when configured.

FMM automatically switches `idle`/`walk` from the real Bukkit entity's horizontal movement. The plugin explicitly triggers attack and death. Death uses FMM's `removeWithDeathAnimation()` so the model is not deleted before the death animation can render.

The exact animation names for each infected mob are in `src/main/resources/config.yml` under `animations:`. If a model is missing a required animation, InfectedMobs logs a warning naming the model, mob and missing animation.

A `spawn` animation is **not required**.

## Included test models

The archive currently includes these two already-animated models under `fmm-models/`:

- `sculk_infected_skeleton.bbmodel` — `idle`, `walk`, `attack`, `attack_ranged`, `death`
- `sculk_infected_creeper.bbmodel` — `idle`, `walk`, `attack`, `death`

The plugin is also configured for the other four IDs, but their `.bbmodel` files are intentionally not fabricated; put your real models into FMM's `imports/` directory using the exact IDs from `config.yml`.

## Server test

1. Install FreeMinecraftModels compatible with the FMM API version used by the build.
2. Copy each `.bbmodel` into `plugins/FreeMinecraftModels/imports/`.
3. Run `/fmm reload` and wait for the reload to finish.
4. Install `InfectedMobs.jar` and restart the server.
5. Test with `/infectedmobs spawn sculk_skeleton` or `/infectedmobs spawn sculk_creeper`.
6. Walk the mob around: `idle`/`walk` are driven by the DynamicEntity backing mob. Attack: `attack` or configured `attack_ranged`. Death: `death` via FMM's death-removal path.

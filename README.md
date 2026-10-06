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

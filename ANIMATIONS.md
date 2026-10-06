# InfectedMobs animation contract

Every configured infected model uses the same core contract:

| State | Required | Trigger |
|---|---|---|
| `idle` | yes | FMM DynamicEntity automatic state |
| `walk` | yes | FMM DynamicEntity automatic state from horizontal movement |
| `attack` | yes | InfectedMobs melee trigger |
| `death` | yes | InfectedMobs death trigger + FMM `removeWithDeathAnimation()` |
| `spawn` | no | FMM automatic one-shot on model creation |
| `attack_ranged` | skeletons only if used | InfectedMobs `EntityShootBowEvent` |

The names are configured per mob in `src/main/resources/config.yml` under `animations:`. This means a model may use a different custom name if necessary, without changing Java code.

Do not add a `spawn` animation unless you actually want a custom entrance animation. FMM starts at `idle` when `spawn` is absent.

## Runtime control

InfectedMobs explicitly controls the visual state instead of relying on FMM's automatic movement detector:

- `spawn` is played once when the model is attached, if present.
- `idle` loops while the backing Bukkit mob is not moving horizontally.
- `walk` loops while the backing Bukkit mob is moving horizontally.
- `attack` is played when the mob lands a melee attack.
- `attack_ranged` is played when an infected skeleton fires a bow.
- `death` is delegated to FMM's death-removal path.

The plugin stops the previous loop before changing state so a persistent idle loop cannot swallow walk/attack animations.

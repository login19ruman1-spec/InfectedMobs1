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

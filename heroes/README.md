# Heroes (Palladium addon, Fabric 1.20.1)

A pack of superpowers for Palladium. Each hero has its own package and Palladium namespace:

- `hulk:hulk` - see below.
- `viltrumite:viltrumite` - flight (with sonic boom at speed), super strength, durability and regeneration, space survival.

Give yourself a power with `/superpower set @s <power id>`. Art for Viltrumites is a placeholder suit overlay.

## Hulk

A Hulk superpower for [Palladium](https://modrinth.com/mod/threetag-palladium). It is a small Fabric mod:
the power itself is Palladium JSON (`data/hulk/palladium/powers/hulk.json`), with Java only for the custom abilities.

## Requirements
Minecraft 1.20.1, Fabric Loader >= 0.18.2, Fabric API, Palladium >= 4.5.0, GeckoLib, Pehkui.

## Giving yourself the power
Use Palladium's own tools (or your power-assigning system):

    /superpower set @s hulk:hulk

## What it does
- **Rage** (energy bar, 0-100): rises when Hulk takes or deals damage, slowly drains when calm.
- **Rage scaling** (`hulk:rage_scaling`): size 1.3x -> 2.0x (Pehkui), plus health, damage, speed and knockback resistance, all scaling with rage.
- **Ground Smash** (`hulk:ground_smash`, action key, 3s cooldown): shockwave, damage, knockback, breaks weak blocks.
- **Super Jump** (`hulk:super_jump`, hold **Space** to charge for up to 3 s, release to take off; the longer you hold, the higher): lands with a small shockwave; Hulk takes no fall damage.
- **Thunderclap** (`hulk:thunderclap`, action key, 5s cooldown): wide blast; slows, concusses (nausea + brief blindness) and knocks back enemies, shatters glass and snuffs out fires.
- **Stomp Quake** (`hulk:stomp_quake`, action key, 6s cooldown): seismic stomp that launches enemies and flings ground blocks.
- **Grab & Throw** (`hulk:grab_throw`, action key): press to grab the mob, player or block in front of you, press again to throw it. Thrown things explode on impact (damage scales with rage and tier). Held for at most 10 s.
- **Shoulder Charge** (`hulk:charge`, hold key, up to 8 s): super sprint that keeps accelerating the longer you run (full speed after 4 s), that bowls over mobs and smashes weak blocks.
- **Wall Climb** (`hulk:wall_climb`, hold key): climb the wall you are pushing against.
- **Immortality** (`hulk:immortality`, passive): instead of dying, Hulk revives at 40% health at full rage (5 min cooldown).
- **Regeneration**: heals faster the higher his rage.

### Rage tiers (comic forms)
| Rage | Tier | Power multiplier |
|---|---|---|
| 0-59% | Savage | x1.0 |
| 60-94% | Worldbreaker | x1.6 |
| 95-100% | Titan (kaiju scale, up to ~3x size) | x2.2 |

Ability keys are Palladium's standard ability keybinds (Controls -> Palladium), assigned in the order of `list_index`.

Grab & Throw, Shoulder Charge and Wall Climb are inspired by the GTA V Hulk script mod.
Sprint and climb movement is applied on the client for players, like a vanilla ladder; the server follows their position.

## Placeholder art
`assets/hulk/geo/hulk.geo.json`, `animations/hulk.animation.json` and `textures/models/hulk.png` are generated placeholders
(a bulky green GeckoLib overlay with idle / smash / clap animations). Replace them with Blockbench exports using the same
bone names (`armorHead`, `armorBody`, `armorRightArm`, `armorLeftArm`, `armorRightLeg`, `armorLeftLeg`) and animation names.

## Building
    gradle build        # jar in build/libs

For `gradle runClient/runServer`, drop the Palladium jar and the jars bundled inside it (META-INF/jars) into `dev-libs/`
(see `build.gradle`); that folder is git-ignored.

## License
GPL-3.0 (Palladium is GPL-3.0).

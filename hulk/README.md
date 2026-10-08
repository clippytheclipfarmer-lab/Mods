# Hulk (Palladium addon, Fabric 1.20.1)

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
- **Super Jump** (`hulk:super_jump`, hold key to charge, release to leap): lands with a small shockwave; Hulk takes no fall damage.
- **Thunderclap** (`hulk:thunderclap`, action key, 5s cooldown): wide blast, slowness stun, shatters glass.

Ability keys are Palladium's standard ability keybinds (Controls -> Palladium), assigned in the order of `list_index`.

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

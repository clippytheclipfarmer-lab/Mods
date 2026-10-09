# Fisk's Superheroes (Fabric 1.20.1, Palladium addon)

A recreation of [Fisk's Superheroes](https://www.curseforge.com/minecraft/mc-mods/fisks-superheroes) for **Minecraft 1.20.1 on Fabric**, built as an addon for [Palladium](https://modrinth.com/mod/threetag-palladium).
Everything here (code, models, textures) was written from scratch for this project; no assets of the original mod are used.

Eight heroes, each a full four-piece suit with its own 3D model (cape, ears, wings, thrusters, ...), glowing details, gadgets and powers.
Wear **all four pieces** and the hero's power switches on. Abilities use Palladium's ability keys (Controls -> Palladium); "hold", "toggle" and "scroll" are noted below.

## Requirements
Minecraft 1.20.1, Fabric Loader >= 0.14, Fabric API, [Palladium](https://modrinth.com/mod/threetag-palladium) >= 4.5.0, [GeckoLib](https://modrinth.com/mod/geckolib) 4.4.x.

## The heroes
| Hero | Powers |
|---|---|
| **Iron Man** (Mark VII) | Flight, **Repulsor Blasts** (hold, both palms), **Unibeam** (hold, chest), **Missile**, HUD night vision, life support, heavy armor. All weapons draw on the **Arc Reactor** energy bar (200, recharges). |
| **Captain America** | Super-soldier strength, speed, jump, health and slow regeneration. **Shield Throw**: the Vibranium Shield ricochets between up to four enemies and returns. The shield also blocks. |
| **Thor** | **Mjolnir**: throw it (it calls lightning on whatever it hits and comes back), **Call Lightning** where you look, **Summon Storm** (a thunderstorm of ten bolts), a **Lightning Bolt** beam from the hand, and **flight while Mjolnir is in your main hand**. Immune to lightning. |
| **Spider-Man** | **Wall Crawl** (hold Space against a wall), **Web Zip** (hold to reel yourself to what you look at), **Web Shot** (glues what it hits), **Spider Sense** (hostiles glow when they get close), no fall damage, big jumps. |
| **Black Panther** | **Kinetic Absorption**: hits are soaked into the kinetic energy bar instead of hurting (until it is full); **Kinetic Release** spends it as a shockwave. **Vibranium Claws** (toggle, with a model), enhanced senses (night vision), silent steps, agility. |
| **Superman** | Flight, **Heat Vision** (hold; smelts blocks, sets fires), **Freeze Breath** (hold right-click: slows and freezes creatures, ices water, snuffs fires), super strength, near invulnerability. **Kryptonite** in your inventory (or on the ground within 6 blocks) gives him Kryptonite Poisoning and switches the powers off. |
| **Batman** | **Grapple Hook** (hold), **Batarang** (endless, returns), **Smoke Bomb**, **Detective Vision** (toggle: night vision and nearby creatures glow), **Cape Glide** (hold Space in the air), strong armor. |
| **The Flash** | **Speed Force** (toggle; scroll for levels 1 to 5), running up walls and across water while sprinting, a **lightning trail**, phasing through walls, speed punches that bowl enemies over. |

## Items
- Four armor pieces per hero (see the creative tab **Fisk's Superheroes**).
- **Vibranium Shield**, **Mjolnir**, **Batarang** (a stack of 16 is only for show: the Batman power throws its own) and **Kryptonite**.

### Crafting
Suit pieces use the standard armor shapes with the hero's cloth or metal (`M`), accent (`A`) and core (`C`):

| | Helmet `MMM / MAM` | Chestplate `M M / MCM / MAM` | Leggings `MAM / M M / M M` | Boots `M M / A A` |
|---|---|---|---|---|
| Iron Man | M iron ingot, A gold ingot, C diamond |
| Captain America | M blue wool, A red wool, C white wool |
| Thor | M iron ingot, A red wool, C lightning rod |
| Spider-Man | M red wool, A blue wool, C string |
| Black Panther | M black wool, A amethyst shard, C netherite scrap |
| Superman | M blue wool, A red wool, C gold ingot |
| Batman | M gray wool, A black wool, C yellow dye |
| Flash | M red wool, A gold ingot, C glowstone dust |

- **Vibranium Shield**: `IVI / VSV / IVI` with iron ingots, vibranium ingots (Palladium) and a vanilla shield.
- **Mjolnir**: `TTT / TLT / _B_` with titanium ingots (Palladium), a lightning rod and a blaze rod.
- **Batarang** (x4): `IKI / _I_` with iron ingots and black dye. **Kryptonite**: emerald + glowstone dust + slime ball (shapeless).

## How it is built
Palladium does the heavy lifting, so most of the mod is JSON:

| What | Where |
|---|---|
| Suit sets, armor materials, gadget items, creative tab | `src/main/resources/addon/fiskheroes/` |
| Powers (abilities, conditions, energy bars) and the suit-to-power links | `src/main/resources/data/fiskheroes/palladium/` |
| Suit models, textures, render layers, beams, trails | `src/main/resources/assets/fiskheroes/` |
| The abilities JSON cannot express (gadget throwing, zip line, wall climb, speed force, kinetic energy, freeze breath, ...), the thrown gadget entity and the kryptonite effect | `src/main/java/com/fiskheroes/` |

All of the JSON and art is **generated** (and committed) by the scripts in `tools/`, so a new hero is mostly a few lines:
```
python3 tools/generate_models.py [--preview out_dir]   # suit geo + textures + render layers (preview needs numpy and Pillow)
python3 tools/generate_icons.py [--sheet sheet.png]    # item icons
python3 tools/generate_data.py                         # items, suit sets, powers, recipes, lang, beams, trails
python3 ../tools/validate_palladium_json.py .          # checks every JSON file against Palladium's own source
```
To add a hero: add a `build_<hero>()` painter in `tools/suits.py`, a hero entry plus a power function in `tools/generate_data.py`, and a palette in `tools/generate_icons.py`, then run the four commands.
`validate_palladium_json.py` knows Palladium's ability, condition and property names, item types, and checks that every referenced model, texture, render layer and recipe ingredient exists and that every cube fits its texture.

## Building
    ./gradlew build        # jar in build/libs
`./gradlew runClient` / `runServer` need Palladium and the jars bundled inside it in `dev-libs/` (see `build.gradle`; the folder is git-ignored).

## Not done compared with the original
The original's Suit Fabricator and customisable suits, per-hero animations (the suits are static models; capes do not sway), many more heroes (Hawkeye, Black Widow, Ant-Man, Deadpool, ...), and hero-specific sounds. Powers use Palladium's flight, beams and key system instead of the original's own.

## License
GPL-3.0-only. Marvel and DC characters and names belong to their owners; this is an unofficial fan project.

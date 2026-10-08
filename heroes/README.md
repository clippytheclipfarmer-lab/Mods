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
- **Gamma Nuke** (`hulk:gamma_nuke`, passive, no button): if rage stays at 100% for 10 s straight, Hulk detonates. Everything alive within 100 blocks takes massive damage (he is spared), and terrain in a 100-block sphere is erased as an expanding wave over ~10 s (nearest first, so the server does not freeze). Bedrock and unloaded chunks are skipped; it needs the `mobGriefing` gamerule on for terrain damage. Afterwards rage resets to 0, he is weakened and slowed, and it cannot happen again for 20 min. All numbers are properties of the ability in `hulk.json` (`hold_ticks`, `radius`, `damage`, `block_damage`, `cooldown`); set `block_damage` to false to only damage entities. The cooldown is not saved across server restarts.
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

## City regeneration
Mark part of a map (for example a city) and everything destroyed inside it grows back, bottom-up, like a construction site.

- **City Wand** (creative menu, Tools tab): left-click a block = corner 1, right-click a block = corner 2.
- `/city create <name>` (uses the wand selection) or `/city create <name> <from> <to>`.
- `/city list`, `/city info <name>`, `/city remove <name>`.
- `/city finish <name>`: rebuild everything now (about 5 s). `/city forget <name>`: accept the current state as the new baseline.
- `/city set <name> rebuild_seconds|idle_seconds|builders|sounds <value>`.

How it works: every live block change inside a region (hero powers, explosions, players, other mods) saves the original block, including chests and other block data.
Only real destruction or replacement is recorded; placing blocks into air, doors opening, crops growing, fluids and fire are ignored.
After `idle_seconds` without new damage, blocks come back in order from the lowest layer up, paced so a big job takes about `rebuild_seconds`.
Rebuilding pauses while a player in the region is fighting, skips spots blocked by entities, and waits for unloaded chunks.
Builder villagers (invulnerable, no AI, a mason profession) appear near the rebuild front with hammering and construction sounds while a player is within 96 blocks, and disappear when finished.
Damage records are saved with the world (capped at 6 million blocks per region); the nuke can destroy millions of blocks, so saves after a big blast can be large and slow.

## Space
Fly straight up past the build limit and you leave the planet (normal dimension-change loading screen) and arrive in the **space dimension**, above the planet you left.
There the planets are real block spheres you can see and fly around, plus an orbit station. Fly into a planet and you land on its surface (another loading screen);
flying back into the planet you launched from returns you to the spot you left. In space survival players get free flight and no gravity.

Built-in destinations: Earth (the overworld), the Moon, Verdant (alien jungle), Dune (hot desert), Glacier (cold ice), and the Orbit Station.

**Per-planet rules:** gravity (client-side, so low-gravity worlds make you jump higher and fall softer), oxygen, and a heat or cold hazard.
Protection: creative/spectator, the Viltrumite Space Survival ability, and any helmet in the item tag `heroes:oxygen_helmets` (turtle helmet by default; extend it with a datapack) for oxygen.
Vanilla fire resistance and leather armor still protect against heat and cold.

**Commands (op):** `/space list`, `/space land <planet> [players]`, `/space orbit <planet> [players]`.

### Adding or changing planets
A planet is one JSON file: `data/<namespace>/heroes/planets/<name>.json`.

    {
      "name": "My Planet",
      "dimension": "heroes:moon",          // the world you land on (needs its own dimension, see data/heroes/dimension/)
      "position": [330, 200, 110],         // center of its sphere in the space dimension
      "radius": 24,
      "blocks": { "surface": "minecraft:light_gray_concrete_powder", "subsurface": "minecraft:andesite",
                  "core": "minecraft:stone", "accent": "minecraft:gray_concrete", "accent_scale": 10.0 },
      "gravity": 0.16,                     // 1.0 = normal
      "oxygen": false,
      "hazard": "none",                    // none | heat | cold
      "landing": [0, 0],                   // x, z where you touch down (unless returning to where you launched)
      "can_launch": true,                  // whether flying up from this world leads to space
      "launch_altitude": -1                // -1 = just under the build limit
    }

Set `"shape": "station"` for a hollow glass-and-iron station instead of a sphere.
The sphere is generated into space chunks when they first generate, so changing a planet after chunks exist needs a fresh space dimension (delete `dimensions/heroes/space` from the world).
Each landing dimension is ordinary datapack worldgen: see `dimension`, `worldgen/noise_settings` and `worldgen/biome` under `data/heroes`.

## Space pod and star map
- **Space Pod** (craftable: glass, 4 iron blocks, redstone block, firework rocket; also in the creative Tools tab): right-click a block to place it, right-click the pod to sit in it. One seat.
- **Fly it**: W moves you in the direction you look, sneak to get out. Fly straight up past the build limit and you reach space (the pod comes with you), fly into a planet and you land on it.
- **Star map (H while in the pod)**: top-down map of every planet, station, star and black hole (distances on a square-root scale). Pick one, press **Launch** and the pod flies there by itself at high speed (it climbs out of the atmosphere first if you are on a planet). **Stop autopilot** cancels.
- Planets are landed on automatically on arrival. Stations, stars and black holes are not landable: the pod stops at a safe distance. Stars burn you if you get close; a black hole pulls you in and crushes you at the event horizon.
- More bodies: add a planet JSON with `"kind": "star"` or `"kind": "black_hole"` (and optional `"color"`, `"ring": {"inner_radius", "outer_radius", "thickness", "block"}`).

## Infinity Stone hunt
Six stones: Space, Mind, Reality, Power, Time, Soul (`heroes:<name>_stone`). In the hunt each stone sits sealed inside its own **placeable container** (see below), not loose.
When the first player joins a world, the stones are shuffled: **two** go into secret spots in the map, the other **four** each get a **shrine** on one of the generated planets (built the first time a player is on that planet, 36 blocks from the landing spot).
Hidden stones are put, inside their filled container, into an existing chest/barrel at the spot when there is one (so it looks like normal loot), otherwise a chest is placed. Shrines put the container block on the pedestal.

1. Find the secret spots with the scanner: `python3 tools/find_hidden_spots.py <world folder> --count 10` (no extra packages; run it on a copy of the map). It lists deep, isolated containers and unopened loot chests.
2. Put its output in `config/heroes/stone_hunt_locations.json` (created empty on first run). At least two locations are needed; the rest are spare.
3. Commands (op): `/stonehunt status` (spoilers!), `/stonehunt reroll`, `/stonehunt place` (do the placement now), `/stonehunt give <stone> [player]`.

Notes: positions are chosen from the world seed, so a fresh world gets a fresh roll. Unplaced stones (not enough locations) stay in `/stonehunt status` as `unplaced`.

## Symbiote
A random event on the generated planets (not Earth, and not planets with `"symbiote_meteors": false` such as **Klyntar**, the new symbiote homeworld).

**Meteor event.** Every ~10 s each planet that has players has a 1-in-90 chance (about once per 15 minutes) of a burning meteor falling 45-90 blocks from a random player, with a chat warning giving the direction. It leaves a crater and a hollow shell of blackened rock with a **symbiote blob** inside. At most 3 blobs per world.

**The blob** drifts slowly, pulls nearby mobs towards it and absorbs them (it grows as it eats), and hunts the nearest player or villager. On contact it lifts the victim with a black tendril and holds them for 3.5 seconds (hitting the blob breaks the grip), then rolls:
- **20%** - the victim dies (the blob survives),
- **60%** - an ordinary bond,
- **20%** - a perfect host.

**A bonded villager** becomes a hostile **Symbiote Villager** (the perfect-host roll gives a tougher **elite** that yanks you in with a tendril). When it dies the symbiote escapes as a blob again.

**A bonded player** gets the Palladium power `symbiote:symbiote` (perfect host: `symbiote:apex`):
- **Strength and living armor**: extra damage, health, armor, speed, a higher jump and no fall damage (black suit overlay).
- **Tendril** (action): hit a creature and it is yanked to you; hit a block and you swing to it.
- **Wall Cling** (hold): stick to walls, move forward to climb.
- **Consume** (action): eat a weakened creature in front of you to heal 4 hearts and feed the symbiote; stronger ones are just bitten.
- **The symbiote's own hunger** (red bar; this is the symbiote's food, never the player's hunger bar): full = sated, empty = starving. It drains slowly (1 point per 10 s, a perfect host's symbiote half as fast) and quickly while the suit is on (1 per 2 s, so about 3 minutes of suit time empties it). It grumbles at 30% and again at 10%. **At zero it takes control of your body**: the suit goes on by itself and it hunts, animals and monsters first (people only if nothing else is around), eats food lying on the ground, and keeps going **until the bar is full again**, then it lets go. **When the host eats, the symbiote eats first** (`PlayerMixin`): while its bar is not full, the whole item goes to the symbiote (meat counts 4x its food value, other food 2x) and the host gets no food level and none of the food's effects, so the symbiote cannot get food poisoning from rotten flesh, raw chicken and the like. Once it is full, the host eats normally. **Kills feed it** (12 points, or 15-60 when it kills while in control, by the prey's size); **Consume** gives 40. Takeover is tracked with the entity tag `heroes_symbiote_control`, so logging out does not escape it.
- **Apex form** (Apex only, toggle): +35% size, +10 damage, +15 hearts, heavy knockback resistance.
- There is no easy way out: the symbiote leaves only if the host dies (it becomes a blob at the body) or with `/symbiote release`.

**The suit (key press).** The first ability in the symbiote and Apex powers is **Symbiote Suit**, a toggle on the first ability key. Pressing it makes the black Venom suit spread over the body in stages (torso, then arms, then legs, then the head with the white eyes and toothy grin, about 1.2 s in total) with a squelch sound; pressing it again peels it off in reverse. It is done with a `palladium:animation_timer` (`suit_timer`) and four `palladium:render_layer` abilities (`suit_torso`, `suit_arms`, `suit_legs`, `suit_head`) that each turn on at a different timer value. The model is generated by `tools/models/venom_model.py` (preview: `previews/venom.png`). 
**What needs the suit and what does not.**
- *Suit only:* strength, armor, extra health, speed, leap, no fall damage, Tendril, Consume, Wall cling (the attribute abilities use an `enabling` condition on `suit`, the key abilities an `unlocking` one).
- *Always on while bonded:* the hunger bar and the starving takeover, a small passive boost (+2 attack damage, +4% speed), **danger sense** (`symbiote:sense`: actionbar warning and heartbeat when hostiles or other players are within 14 blocks), and **fast healing** (`symbiote:regen`: +0.5 heart per 2 s, or +1 heart per second in the suit).
- *The suit itself:* the player's name tag is hidden while suited (client mixin `EntityRendererMixin`). Taking a hit of 3+ hearts, or the symbiote taking control when starving, spreads the suit automatically (`SymbioteHost.requestSuit` simulates the key press). Wearing it burns the symbiote's food (`symbiote:upkeep`).

**Three layers of health.** A host has (1) their **own health**; (2) **symbiote armor**, a second pool equal to the host's maximum health (grey-blue bar); and (3) **the symbiote itself**, 5 hearts (red bar).
- While suited, ordinary damage hits the armor first, and only what is left reaches the host.
- **Only fire and sonic attacks hurt the symbiote itself.** Fire (burning, lava, fire blocks) and sonic damage (the warden's sonic boom) go straight to its 5 hearts, and a suited host takes nothing from them. Not suited, the host takes the hit as well. **Sonic attacks also include:** a **ringing bell** within 16 blocks (2 HP per ring, with the bell sound), the **Viltrumite sonic boom** (2 HP) and the **Hulk thunderclap** (6 HP) within their radius.
- At 0 the symbiote **tears free and becomes a blob entity that cannot bond for 10 minutes** (`SymbioteBlobEntity.lockBonding`, saved with the entity). The host loses the power.
- Mending: the armor refills 5% a second (3% unsuited) once the host has been left alone for 5 s; the symbiote itself recovers 0.1 heart a second. A new bond starts with both full (`SymbioteHost.tickServer` fills the bars once the power exists).
- The two bars are Palladium energy bars `armor` (max 1000, per mille of the host's max health) and `core` (max 100, percent of 5 hearts) in `symbiote.json` and `apex.json`; the damage model is the `LivingEntityEvents.HURT` handler in `SymbioteHero`. Armor tracks the host's *current* maximum health (it grows with the suit's +10 health).

**Commands (op):** `/symbiote meteor` (drop one near you), `/symbiote blob`, `/symbiote bond [apex]`, `/symbiote release`.

## Infinity Stones (powers)
Six stones plus four holders. **Loose stones cannot be worn**: a stone only works once it is **socketed into a holder**, and the holder must be **worn in an accessory slot** (needs Trinkets; without an accessory mod it counts while in either hand).
The **Stone Ring** (cheap: 4 gold ingots + an amethyst shard) holds **one** stone and goes in a ring slot; the Gauntlet and Bracers (six sockets) go in the glove slots; the Necklace (six sockets) in the necklace slot.

**Containers in hand:** a filled stone container (Orb, Tesseract, Scepter, Aether, Eye, Soul Urn) gives its stone's powers while you hold it in either hand, and keeps the stone safe.

| Stone | Power (action key) | Boosts other heroes |
|---|---|---|
| Space | Blink (teleport to where you look), heroic flight, higher jump | Hulk super jump +30%, space pod +50% speed |
| Mind | Telekinesis (grab and throw, same system as the Hulk's) | Grab reach x2 and can lift bigger things |
| Reality | Reality Shift (turn the mob you look at into another mob) | Smashes/charges break harder blocks, wider radius |
| Power | Power Blast (energy beam and explosion), more damage and armor | All shockwaves/blasts +50% damage |
| Time | Rewind (back 5 seconds with that health), faster movement | Every ability cooldown ticks down twice as fast |
| Soul | Soul Drain (steal life from a creature in front of you), slow regeneration | Hulk regeneration x2, revive cooldown halved, symbiote Consume heals x2 |

Every stone you wear also adds a general +10% to blasts (and doubles at all six).

**Holders** (Stone Ring with one socket; Infinity Gauntlet, Necklace and Bracers with six, craftable with gold blocks, netherite and diamond blocks): worn, they give the powers of every stone socketed in them.
Sneak + right-click with the holder in your main hand and a stone in your off hand to socket it; sneak + right-click with an empty off hand takes the last one out.
With **all six** socketed in a worn holder you also get **The Snap**: half of all creatures (not players or bosses) within 128 blocks turn to ash, with a 20 minute cooldown.

## Stone containers
Every stone has a themed container that can be **placed in the world** like a block:

| Stone | Container |
|---|---|
| Power | The Orb |
| Space | The Tesseract |
| Mind | The Scepter |
| Reality | The Aether |
| Time | Eye of Agamotto |
| Soul | The Soul Urn (invented - the Soul Stone has no film container) |

A filled container glows and shows its gem. **Right-click a filled one to take the stone out** (the container stays, empty); right-click an empty one while holding its stone to put it back. Breaking a container drops it with the stone still inside, so you can carry and re-place it. The creative menu has a filled and an empty version of each. Containers are only for keeping and displaying the stone: to use its power, socket the stone into a holder and wear the holder.

### The Power Stone is dangerous loose
While a loose Power Stone is in your inventory (any slot) it hurts you (3 hearts every 2 s, and it can kill you) and sends out a **pulsing purple shockwave** every 2 seconds that expands 9 blocks and damages (2.5 hearts) and knocks back every creature it passes through. It stays safe when **socketed in a holder (worn or not) or kept in the Orb**; creative and spectator players are immune. The numbers are constants at the top of `PowerStonePulse.java`.

### The rule: loose stones are cursed
A stone loose in your inventory is a drawback (safe once socketed in a holder or kept in its container; creative/spectator are immune):

| Loose stone | What it does to you |
|---|---|
| Power | 3 hearts every 2 s plus purple damaging shockwaves. **It can kill you.** |
| Space | Tears you 5-10 blocks sideways to a safe spot every 10 s |
| Mind | Nausea and blindness, and monsters within 16 blocks turn on you (every 15 s) |
| Reality | A random bad effect (blindness, hunger, mining fatigue, slowness, weakness, glowing, nausea) every 20 s |
| Time | Slowness, mining fatigue and weakness every 25 s |
| Soul | 1 heart and hunger every 3 s |

Only the Power Stone can kill you; the others stop hurting you before your last 3.5 hearts. The timings are in `LooseStones.java` and `PowerStonePulse.java`.

## Klyntar (the symbiote homeworld)
A dark organic hive world with a permanent night sky (End-style), 1.2x gravity, breathable air, and no meteors.

- **Terrain:** black rock (blackstone/deepslate) with patches of basalt and magma, glowing veins of crying obsidian, black spires topped with crying obsidian, and shallow pools of **Symbiote Tar**.
- **Hazards:**
  - **Spores** (purple spores in the air): constant weakness and bouts of nausea.
  - **Symbiote Tar:** you sink, crawl slowly and are slowly eaten (1 HP/s). It sometimes rears up as a blob.
  - **Tendril Nubs:** small black sprouts on the ground. Step on one and it drags you towards the hive for six seconds (it re-arms after ten).
  - Bonded **hosts**, symbiote creatures and creative players are immune to all of these.
- **Creatures:** blobs, **Symbiote Crawlers** (fast wall-climbing spiders) and **Symbiote Brutes** (big, tough, 70 HP) keep spawning around you (caps: 6 / 8 / 2 within 64 blocks).
- **The hive:** a tar-floored dome 120 blocks east and 70 south of the landing site, with the core (crying obsidian and end rods) on an island in the tar. Getting within 20 blocks wakes **Knull**.
- **Knull, God of the Abyss:** a giant boss (900 HP, boss bar, can't take more than 60 per hit). Phase 1: tendril yanks and crawler summons. Phase 2 (below 60%): brutes, and a ground-shaking stomp. Phase 3 (below 30%): darkness for everyone nearby, and he speeds up. Drops 2 nether stars, 8 netherite scrap and 4 echo shards; he does not respawn once beaten.
- **Bonding Altar:** at -70 / -50 from the landing site. Right-click it with an empty hand for a willing bond: **5% death, 65% ordinary bond, 30% perfect host**. 10 minute cooldown per player.
- The hive and altar are built the first time someone lands on Klyntar. Admin commands on Klyntar: `/symbiote klyntar build`, `/symbiote klyntar knull`.

## Model tools (`tools/`)
Models are authored in code so they can be looked at and improved without launching the game:

- `tools/modelpreview.py model.geo.json texture.png out.png` renders a front / 3/4 / side / back preview sheet of any GeckoLib cube model (needs `numpy` and `Pillow`). Calibrated against the vanilla Steve skin.
- `tools/models/mc_model.py` is a small builder: describe cubes and how to paint them and it packs the texture, paints it and writes the `.geo.json` and `.png`.
- `tools/models/hulk_model.py` generates the Hulk model and texture (about 3 blocks tall: wide shoulders, long legs set apart, 512x512 texture; the eyes are a second render layer, hulk:hulk_eyes, with the glow render type) (re-run it after editing, then re-run the preview). Latest preview: `previews/hulk.png`.

The generated files are ordinary GeckoLib assets, so you can still open them in Blockbench and edit or replace them; keep the bone names (`armorHead`, `armorBody`, `armorRightArm`, `armorLeftArm`, `armorRightLeg`, `armorLeftLeg`).
While the Hulk power is active the player's own body is hidden (`palladium:remove_body_part`), so only the Hulk model shows.

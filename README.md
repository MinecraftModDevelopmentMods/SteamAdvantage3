# SteamAdvantage 3

NeoForge 1.21.1 port of the mod retained in `SA2/`. The root project is named
`SteamAdvantage`, with Java package `zone.moddev.mc.SteamAdvantage`. It follows the
[MMD mod template](https://github.com/MinecraftModDevelopmentMods/mmd-mod-template/tree/1.21.1):
Java 21, ModDevGradle with Parchment mappings, and NeoForge **21.1.265**. Project
metadata and dependency versions live in `gradle.properties`.

MMDLib and PowerAdvantage 3 are resolved from `https://maven.moddev.zone/releases`
(`zone.moddev.mc.lib:mmdlib` and `zone.moddev.mc.poweradvantage:PowerAdvantage`).
They remain separate installed mods; the SteamAdvantage JAR does not embed them.
`SA2/` and `other-docs/` are reference inputs, not Gradle subprojects.

```sh
./gradlew build
./gradlew runClient
./gradlew runServer
./gradlew -PgameTests runGameTestServer
python3 scripts/validate_resources.py
```

Local builds use `mod_version` from `gradle.properties`, for example
`build/libs/SteamAdvantage-3.0.2.121012.jar`. Releases are made by pushing a
Git tag in the `major.minor.bug.mcversion+loader` form (for example `3.0.2.121012`); the
publish workflow uses the tag as the version and uploads to GitHub, and to Maven and
CurseForge when their secrets are configured. GameTests and their fixture datapacks are
excluded from the release JAR.

## Content and operation

MMDLib standalone definitions register all 17 legacy block IDs and four legacy item IDs under
`steamadvantage`. The art and translations originate in SA2; texture paths,
blockstates, models, enchantments, recipes, mining tags, and loot use 1.21.1 formats.
There is a SteamAdvantage creative tab.

| Content | Operation |
| --- | --- |
| Coal boiler | Burns furnace fuels, consumes water, outputs steam |
| Electric boiler | Consumes 16 FE and 1 mB water per active tick, produces 50 mB steam |
| Geothermal boiler | Gains heat beside lava or magma, boils water after reaching operating temperature |
| Oil boiler | Burns 100 mB aliquots of tagged liquid fuel, consumes water, outputs steam |
| Steam tank | Stores 1,000,000 mB steam; comparator reports fill level |
| Steam furnace | Three independent vanilla smelting lanes; fuel heats the furnace, steam increases heating |
| Rock crusher | Uses MMDLib `mmdlib:crushing` recipes; 125 processing ticks at 150 mB steam/tick |
| Steam drill | Mines in its facing direction, extends a damaging drill shaft, retains drops, follows adjacent steam track at range/obstruction |
| Steam elevator | Redstone raises the platform and passengers; removing power retracts it; 16-block search range |
| Steam still | Runs PA3 distilling recipes with persisted progress and output |
| Steam pump | Extends a downward intake shaft and collects whole source buckets; bounded searches only access loaded chunks |
| Steam switch | Facing output valve; redstone stops output; steam is buffered between separate pipe segments |
| Steam pipe / track | PA3 fluid-pipe subclasses routed by the existing PA3 dimension manager |
| Platform / drillbit / pump shaft | Generated machine parts; no item drops |
| Governor / drill-bit item / cartridge | Crafting components and musket ammunition |
| Musket | Hold use to load, then use to fire; 64-block hitscan, persistent loaded state, durability and configurable damage |

The four musket enchantments are `rapid_reload`, `powderless`, `high_explosive`
(levels 1–5), and `recoil` (levels 1–5). They are data registry entries, eligible
for enchanting, trades, and random loot. Custom musket and machinery damage types
retain the legacy death-message keys. Villagers offer governors, pipes,
cartridges, and muskets; the legacy six chest categories receive supply loot.

MMDLib owns the inventory, tank, energy, role checks, persistence helpers and
neighbor transfers. PA3 owns steam's fluid registration and pipe routing.
One legacy steam unit is represented by 100 mB for fractional steam costs.
Boilers push steam into pipes; consumers expose steam-input capabilities.
Machine-specific XML files use MMDLib's screen runtime for discovery, validation, getter binding,
layout, menus, value synchronization and rendering; `SteamMachine` only implements `ScreenHost`.
See [MMDLib integration](docs/mmdlib-integration.md). Tank
names appear on hover. Fluid containers interact directly with machine tanks;
items can also move through NeoForge item capabilities and shift-click.

PA3's **`poweradvantage:fluid_pipe_terminal`** extracts from a neighboring handler
into its network. It is separate from **`steamadvantage:pump_pipe_steam`**, the
shaft the steam pump creates to reach world fluids. The port reuses PA3's
terminal/network behavior rather than registering another extraction terminal.

## Recipes, configuration and downstream fluids

As requested, recipes retain the legacy material requirements through conventional
item tags: brass/steel ingots, lead nuggets, iron/copper plates, rods, gears and
wire. A metals/content mod or datapack must supply the corresponding tags. The
two libraries alone do not supply every material; no vanilla substitutions are
silently introduced and BaseMetals is not a mandatory Java dependency.

`config/steamadvantage-common.toml` configures musket availability, reload duration,
damage, oil burn duration, and `recipeMode`:

- `NORMAL`: the ordinary governor and machine recipes, including the diamond-pickaxe drill alternative.
- `TECH_PROGRESSION`: gear/rod governor recipe.
- `APOCALYPTIC`: technical governor recipe, machine/governor salvage through MMDLib crushing, no musket crafting recipe.

Restart or reload server data after recipe-mode changes. Because PA3 no longer
exposes its old shared recipe-mode enum, this setting now belongs to SteamAdvantage.
The old generic sprocket requirement maps to `c:gears`; governor salvage produces
MMDLib's iron gears.

Oil fuel membership is `steamadvantage:boiler_fuels` (fluid tag), initially
including optional `c:fuels`, `c:oil`, and `c:biofuel` tags. Datapacks can extend it.
No new oil fluid is invented by this port.

The still uses PA3's `poweradvantage:distilling` recipe type. PA3 supplies the
default crude-oil-to-refined-oil recipe; datapacks and recipe scripting mods can
add, replace, or remove distillation recipes. The still consumes 150 mB steam and
the recipe's input amount when an operation starts, advances for the recipe's
duration, and outputs the saved result when complete. Progress and pending output
survive save/load. See [PA3 distillation docs](other-docs/PA3-docs/distillation.md)
for the recipe format and timing rules. The slow water-to-lava recipe under
`src/gameTest` is only a test fixture.

## Future plans

These are ideas from the original PowerAdvantage notes, not commitments or
scheduled work:

- [ ] Explore extracting reusable automation/crafting support into PA3, so
  future planned mods (potentially including Quantum Advantage) can build on it.
- [ ] Steam-powered machine shop: an automatable crafter, with the automated
  crafter in EA3 as a possible reference.
- [ ] Steam-powered defense cannon: manually aimed and activated by redstone.
- [x] Oil-burning steam boiler: implemented in SA3.
- [ ] Bioreactor: slowly converts organic matter into liquid fuel.

## Verification and compatibility boundaries

The integration suite checks all machine registrations, capabilities, NBT
persistence, steam filtering, boiler fuel/energy/water use, redstone behavior,
PA3 pipe delivery, pumping conservation, crushing and blocked-output behavior,
three furnace lanes, drill movement/inventory preservation, elevator movement,
valve operation, distillation ratios, and musket loading/firing.

The development client completed resource reload and atlas creation without
SteamAdvantage missing-model or missing-texture warnings. Actual in-world
appearance, interactive menus over a remote connection, moving passengers,
weapon aiming/recoil, and interoperability with a chosen metals/fluid mod pack
still need interactive gameplay verification.

This is a modern implementation, not binary compatibility with SA2's Forge API
or save format. Menus use MMDLib's shared layout; the drill model is static rather
than the legacy animated tile renderer. Steam uses PA3's general fluid network;
its steam pipes can carry other fluids when connected to other mods' endpoints,
while steam machines reject non-steam in their steam tanks. Timing, buffers and
transport scheduling are adapted to NeoForge capabilities rather than the old
float-valued conduit network. No automatic 1.10.2 world migration is provided.

`python3 scripts/port_resources.py` reproduces the migrated static assets and
recipes from `SA2/` and the supplied PA3 JAR. Edit that script when changing
generated resources so subsequent runs preserve the changes.

## Source organization

The root `zone.moddev.mc.SteamAdvantage` package contains only the mod entry point.
The subpackages follow SA2's separation of content, machines, blocks and items:

| Package | Responsibility |
| --- | --- |
| `init` | `ModContent` queues MMDLib standalone plans, registers block-entity handles and capabilities; `Villages` trades; `TreasureChests` loot |
| `machines` | `MachineBlock`, shared `SteamMachine` storage/persistence/tick dispatch, and one focused operation class per machine family |
| `blocks` | Drill-bit, elevator-platform, and pump-shaft blocks |
| `items` | Musket behavior |
| `client` | Client-only item model properties |
| `recipes` | Recipe-mode conditions |
| `config` | Common configuration and recipe-mode settings |

Machine operation classes stay package-private and share the block entity's
package-local state. Other packages use its `items()`, `fluids()`, `energy()`, and
`kind()` accessors. New machine behavior belongs in a focused operation class;
wire its tick dispatch in `SteamMachine` and its content in `init.ModContent`.
The package-only reorganization preserved registry IDs. The subsequent standalone-content
refactor gives each machine a block-entity type matching its block/screen ID, replacing the
earlier shared `steamadvantage:machine` type. Existing saves from that earlier development
build need a separate migration before reuse; none is silently assumed. Item/block IDs and
storage NBT keys are preserved. The integration tests live in the corresponding
`machines` package under `src/gameTest` and remain outside the release JAR.

Java formatting is recorded in `.clang-format` (four-space indentation and a
100-column limit). For example:

```sh
rg --files src/main/java src/gameTest/java -g '*.java' -0 | xargs -0 clang-format -i
```

The current MMDLib showcase path and GUI wiring are documented
in [MMDLib integration](docs/mmdlib-integration.md). Content definitions are static resources
under `src/main/resources/mmdlib/`; no material definition is introduced by SteamAdvantage.

For the isolated graphical smoke test, run `./gradlew -PguiSmoke runClient` after the
GameTest server run. It opens and captures all twelve XML screens in a copied test world,
checks their synchronized values and dimensions, then exits. It does not use the normal
`run/` world directory. A virtual display can be supplied with `xvfb-run -a`.

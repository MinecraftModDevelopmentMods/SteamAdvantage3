# MMDLib integration

SteamAdvantage is a consumer and showcase of MMDLib's public APIs. Its twelve machines are
MMDLib XML machine definitions (MMDLib 2.0.3 or later); its other blocks and items are MMDLib
standalone plans. It declares no synthetic material.

## Machines

Each machine is `src/main/resources/mmdlib/machine/<block id>.xml`. MMDLib registers its block,
item, block-entity type, capabilities, screen, recipe type and JEI catalyst, and generates its
blockstate, item model and loot table from the XML. All of them extend the abstract
`steamadvantage:steam_base` (hardness, pickaxe, required tool), which extends
`mmdlib:base_machine` (redstone and side-configuration drawers, recovery capsule, base screen).

MMDLib's generated resource pack sits below mod resources, so SteamAdvantage must not ship its
own blockstate, item model or loot table for a machine. A machine's `appearance` names a model
that is not `block/<id>`, so SteamAdvantage's models are `block/<id>_model`.

| Machine | Work | Tanks (index order) | Items |
| --- | --- | --- | --- |
| Coal / oil / electric / geothermal boiler | `*BoilerProcessor` | steam, water, (oil: fuel) | coal: fuel |
| Steam tank | `SteamTankProcessor` | steam | |
| Steam switch | `SteamValveProcessor` | steam | |
| Steam pump | `PumpProcessor` | steam, product | |
| Steam-powered still | `StillProcessor` | steam, product, input | |
| Steam elevator | `ElevatorProcessor` | steam | |
| Steam drill | `DrillProcessor` (six-way facing) | steam | 5 outputs |
| Steam crusher | MMDLib recipe lane + `RecipeMachineSensor` | steam | input, 5 outputs |
| Steam furnace | three MMDLib recipe lanes + `RecipeMachineSensor` | steam | 3 inputs, 3 outputs |

Custom processors extend `SteamProcessor` (a `StoredProcessor`). It provides the steam helpers,
SA2's rule that a machine stops while it receives any redstone signal (the elevator uses the
signal instead), and the comparator reading every machine XML selects with
`<redstone comparator-source="steamadvantage/comparator"/>`. Machine state (burn timers,
temperature, drill depth, elevator position, distillation batches) is processor state.
Admission rules that tags cannot express (furnace fuel, the config-selected oil fuel tag, still
inputs from Power Advantage's distilling recipes) are `filter-processor` bindings on processors
that implement `StorageAdmission`.

The crusher and furnace use MMDLib recipe processors with recipe types declared in their XML:

- `steamadvantage:steam_crushing`, from `recipes.SteamCrushingRecipes`: every MMDLib crushing
  recipe, 125 ticks and 18,750 mB of steam per item by default.
- `steamadvantage:steam_smelting`, from `recipes.SteamSmeltingRecipes`: every smelting recipe at
  its own cooking time, with MMDLib's FE price converted to steam (a 200-tick recipe costs
  2000 mB). The furnace's three lanes bind `input`/`output` to their own slots and share the
  steam tank.

Authored recipe JSON with a generated recipe's ID replaces it. `RecipeMachineSensor` supplies
their comparator reading (input fullness) and lets a working furnace melt snow above it.

### Block entity

`machines.SteamMachineEntity` extends `DefinedMachineEntity` and is registered for every machine
with `MachineContent.registerEntityFactory` in the mod constructor. It:

- imports saves from before the XML port (`migrateMachineData`): each processor's
  `importLegacy` copies the top-level keys it used (`Burn`, `Temperature`, `Depth`...) into its
  state, the pre-2.0.2 `Energy` key becomes `energy`, and `legacySlots` maps the old shared
  nine-slot inventory onto each machine's declared slots (unplaced contents, such as the
  retired furnace fuel slot, go to recovery). Storage keeps the `Items` and `Fluids` keys and
  the old tank order;
- holds recipe machines while they are powered (`processingAllowed`);
- clears the drill pipe when a drill is removed (`dropContents`, which MMDLib never calls on chunk
  unload) or turned (`setBlockState`), and skips dropping contents while a drill moves itself
  along a track.

### Screens

Machine XML screens extend MMDLib's base machine screen: generated, interactive tank gauges,
generated slot groups and recipe progress, a pause button, and the redstone and
side-configuration drawers. XML machine screens bind to `DefinedMachineEntity`, so the extra
displays SteamAdvantage adds in a machine's `machine` region (burn meter, heat gauge, drill
depth, distillation progress, help text) read processor state through static helpers,
`value="zone.moddev.mc.SteamAdvantage.machines.SteamScreens#burnTime"` and so on.

## Other content

1. `mmdlib/block/*.json` and `mmdlib/item/*.json` select public implementation factories.
2. `mmdlib/standalone/block/*.json` and `mmdlib/standalone/item/*.json` declare the pipes, track,
   drill bit, platform, pump shaft, components and musket.
3. `init.ContentFactories` registers their `BlockImplementation`, `ItemImplementation` and
   `BlockEntityFactory` contracts; `init.ModContent` queues this namespace's standalone plans on
   `RuntimeContentRegistrar` and registers the pipes' capabilities.

## Verification

The GameTest suite covers each machine's behavior, comparator readings (real comparators
included), automation sides and their persistence, Power Advantage pump add-ons, menu slot
roles and shift-click, rejection of mismatched screen definitions, an XML-only variant machine
(a GameTest-only tank that extends `steam_tank`), and imports of saves written by the pre-XML
entity, kept as SNBT fixtures in `src/gameTest/resources/data/steamadvantage/legacy_saves`.

An opt-in client fixture copies the disposable GameTest world, opens each of the twelve screens
through the actual block interaction, checks that each layout fits and has its gauges, captures
each rendered screen, and shuts down automatically:

```sh
./gradlew -PgameTests runGameTestServer --offline
xvfb-run -a ./gradlew -PguiSmoke runClient --offline
```

`-PguiSmoke` changes only the development run. Fixtures live under `src/guiSmoke`, outside
normal builds; the copied world, pass marker and captures live in `build/gui-smoke-client`.
A passing client writes `GUI_SMOKE_PASS.txt` and twelve screenshots. This verifies an
integrated local client; it does not substitute for remote multiplayer, arbitrary resource packs,
or every GUI-scale/language combination.

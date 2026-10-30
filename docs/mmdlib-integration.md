# MMDLib integration

SteamAdvantage is a consumer and showcase of MMDLib's public standalone APIs.
It does not declare a synthetic material to register machines or crafting parts.

## Content path

1. `mmdlib/block/*.json` and `mmdlib/item/*.json` select public implementation factories.
2. `mmdlib/standalone/block/*.json` and `mmdlib/standalone/item/*.json` declare instances.
3. MMDLib's startup loader discovers and validates those files.
4. `init.ContentFactories` registers public `BlockImplementation`, `ItemImplementation`,
   and `BlockEntityFactory` contracts.
5. `init.ModContent` queues only this namespace's standalone plans on
   `RuntimeContentRegistrar`, then passes its own block-entity `DeferredRegister` to every
   `BlockEntityHandle` the registrar prepared. It has no block or item registers.
6. MMDLib creates the blocks and paired items, and gives each machine block a prepared
   `BlockEntityBinding`. `MachineBlock` delegates construction and ticking to
   `MaterialEntityBlock`.
7. `MachineSettings.CODEC` validates behavior selection and storage capacities. Each machine
   type has its own ID matching its block and XML screen.

The library registrar also provides the creative tab. Capability storage/roles, transfer
operations, `MachineCapabilities` registration and crushing recipes use MMDLib.

A test-only standalone tank changes capacity to 2,345,678 mB and inventory size to 12.
It registers, persists and exposes its fluid through XML-bound getters without adding a
Java registration or material definition. Its data and screen are excluded from releases.

## GUI

Twelve machine-specific XML files live under `mmdlib/screen/`. Each declares its slots,
gauges, progress indicators, labels and tooltips, and its file name matches the machine's
block-entity type, which is how MMDLib finds it. SteamAdvantage contains no menu, screen,
renderer, layout or synchronization code:

| Concern | Provided by MMDLib |
| --- | --- |
| Schema, reading, getter binding, layout | `gui.load`, `gui.model`, `gui.layout` |
| Menu, slots, shift-click, opening | `gui.runtime.MachineMenu`, `ScreenRuntime`, `BlockEvents.openMenu` |
| Value synchronization | `gui.runtime.ScreenNetwork`, `ScreenValues`, `ScreenSnapshots` |
| Screen and gauge rendering | `gui.client.MachineScreen`, `GaugeRenderer` |

The block-entity class `SteamMachine` implements `gui.runtime.ScreenHost`: `screenItems()`
exposes its `ItemStorage` (so roles and filters apply to clicks and shift-click) and
`screenRecipeSlots()` names the crusher and furnace recipe inputs for optional JEI transfer.
Right-click goes through the `steamadvantage:use_machine` handler declared in
`mmdlib/extension/machines.json`: fluid containers act on the machine's tanks first, otherwise
`BlockEvents.openMenu` opens the XML screen.

Remaining SteamAdvantage-side code that MMDLib has no equivalent for: the comparator reading in
`MachineBlock` (MMDLib's comparator support does not yet read block-entity getters) and the
fluid-container interaction above.

## Verification

The GameTest suite checks the XML-declared versus hidden slots, output-role protection,
shift-click routing, host removal, rejection of mismatched screen definitions, codec validation,
payload round trips for numbers exceeding `long`, decimals and fluid amounts, and layout of every
screen through MMDLib.

An opt-in client fixture copies the disposable GameTest world and opens each of the twelve
screens through the actual block interaction. It checks synchronized getters, verifies that
each translated layout fits, captures each rendered screen, and shuts down automatically:

```sh
./gradlew -PgameTests runGameTestServer --offline
xvfb-run -a ./gradlew -PguiSmoke runClient --offline
```

`-PguiSmoke` changes only the development run. Fixtures live under `src/guiSmoke`, outside
normal builds; the copied world, pass marker and captures live in `build/gui-smoke-client`.
A passing client writes `GUI_SMOKE_PASS.txt` and twelve screenshots. This verifies an
integrated local client; it does not substitute for remote multiplayer, arbitrary resource packs,
or every GUI-scale/language combination.

# SA2 → SA3 parity checklist

Source comparison recorded October 31, 2026. These findings describe the source
tree at review time, not verified in-game behavior. Check off an entry after the
implementation and its relevant gameplay checks are complete.

## Missing or reduced functionality

- [x] Restore missing villager offers.
  - Toolsmiths and weaponsmiths both offer 5–7 cartridges for one emerald at
    apprentice level 2, and one ordinary musket for 10–15 emeralds at journeyman
    level 3. These retain SA3's existing weaponsmith tier mapping; SA2 put both
    smith offers at legacy tier 1. Governor/pipe offers remain at novice level 1.
  - Clerics offer one enchanted musket at journeyman level 3, matching SA2's tier.
    The legacy 15–25 emerald base price is increased by enchantment power.
    SA3 uses power 12–19 (27–44 emerald total) rather than vanilla's 5–19 roll:
    current musket enchantments require power 10, and vanilla random adjustment
    can otherwise leave the offered musket unenchanted. The trade uses the
    registry-aware `on_traded_equipment` pool and is omitted if datapacks leave
    no eligible enchantments. Stock is 3 uses, 10 villager XP, multiplier 0.05.
  - All 31 server GameTests passed November 5, 2026. Tested registered smith
    listings, tiers, prices/quantities and 256 generated cleric offers for actual
    enchantments and price/stock bounds. Manual trading UI not checked.
  - Offers join vanilla's random trade pools; individual villagers may not select
    them. Already generated offers are not rewritten.
  - References: [SA2 trades](SA2/src/main/java/cyano/steamadvantage/init/Villages.java),
    [SA3 trades](src/main/java/zone/moddev/mc/SteamAdvantage/init/Villages.java).

- [x] Restore the steam drill's automatic crushing behavior.
  - Looks up the mined block's item in MMDLib's live crushing recipes, matching
    the steam crusher's recipe type. Uses the recipe's complete output when
    present; otherwise uses normal unenchanted diamond-pickaxe drops.
  - Checks all output slots together before mining spends steam or advances
    progress. Insufficient space preserves the block and pauses mining until
    the entire result fits; recipe output replaces rather than adds to loot.
  - All 33 server GameTests passed November 5, 2026: cobblestone → gravel,
    bricks → four bricks, dirt fallback, full/partially full output, resuming
    across multiple output slots, and no duplicate yield. Manual play not checked.
  - References: [SA2 drill](SA2/src/main/java/cyano/steamadvantage/machines/SteamDrillTileEntity.java),
    [SA3 drill](src/main/java/zone/moddev/mc/SteamAdvantage/machines/DrillOperation.java).

- [x] Restore machine-specific comparator signals.
  - Sources: elevator raised state (0/15); drill output slots 4–8; furnace input
    lanes 1–3; crusher input slot 0; coal-boiler fuel slot 0; geothermal
    temperature; electric-boiler energy; oil-boiler fuel tank; pump output tank;
    still input tank; tank/switch steam storage.
  - Approved correction to legacy quirks: proportional fullness and zero for
    empty/cold machines, rather than always-full coal/crusher inputs or a
    minimum signal of 1 for empty electric/geothermal boilers. Furnace counts
    all three input lanes; drill uses all five output slots in its denominator.
  - Fullness scales as floor(15 × fraction), clamped to 0–15. Nonempty measured
    inventories retain a minimum signal of 1 and normalize each item's stack
    limit. Inventory fuel/output slots outside the measured range are ignored.
  - Storage changes notify comparators through setChanged; per-tick detection
    also catches raw stack mutations, temperature and elevator state changes.
  - All 35 server GameTests passed November 5, 2026: every machine's empty,
    half/full scaling, unrelated steam isolation, item stack-size normalization,
    and real placed comparators rising/falling for all 12 machine types.
    Manual client play not checked.
  - References: [SA2 machines](SA2/src/main/java/cyano/steamadvantage/machines/),
    [SA3 MachineBlock](src/main/java/zone/moddev/mc/SteamAdvantage/machines/MachineBlock.java).

- [x] Restore musket steel-ingot anvil repair.
  - Uses `c:ingots/steel`; vanilla anvil repair restores one quarter of maximum
    durability per ingot and preserves loaded state. Nonsteel materials rejected.
- [x] Restore musket damage, loaded/unloaded, and ammunition tooltips.
  - Shows configured shot damage, loaded state, and the localized cartridge name
    when unloaded. Infinity/Powderless show that ammunition is not required.
    Legacy `%x` localization placeholders converted to Minecraft `%s` arguments.
- [x] Restore the musket's Infinity ammunition exemption.
  - Both loading checks and consumption exempt Infinity, Powderless, and creative
    mode. Infinity books can be applied through a survival anvil.
- [x] Accept compatible ammunition through the shared blackpowder ammunition tag,
  rather than checking only SA3's own cartridge.
  - Uses the existing `c:ammo/blackpowder` tag, consuming one tagged item per load.
  - All 39 server GameTests passed November 5, 2026. New tests cover steel-tagged
    repair in a real AnvilMenu, rejected nonsteel repair, Infinity book application,
    tag-compatible ammunition, rejected untagged ammunition, Infinity/Powderless
    loading with no ammo and preserving existing cartridges, and tooltip contents.
    Test tag additions are temporary and are not shipped. Manual client UI not checked.
- [x] Restore or explicitly document changes to musket block interactions.
  - Restored fragile-block destruction with normal loot, thin-block outline
    tracing, source-fluid interception, impact sound, and shooter-attributed TNT.
  - Modern categories use the datapack-editable `steamadvantage:musket_breakable`
    block tag instead of removed SA2 materials or the impermeable tag.
  - Mapping, intentional differences, loot conditions, and verification:
    [Musket block interactions](docs/MUSKET_BLOCK_INTERACTIONS.md).
  - All 42 server GameTests passed November 5, 2026; manual client play not checked.
- [x] Restore musket muzzle/impact particles and ordinary camera kick.
  - Server broadcasts one explosion puff at the muzzle and large smoke plus
    a small puff at actual block/entity impacts. Misses have no impact particles.
  - Server-confirmed shooter-only feedback kicks the camera upward 15 degrees,
    clamped at straight up; the shot uses the original aim. This does not add
    movement or change the separate Recoil enchantment's knockback.
  - All 43 server GameTests passed November 5, 2026. A real software-rendered
    client verified block hits, entity hits and misses: exactly one muzzle packet
    and one shot acknowledgement each, smoke/puff only for hits, and pitch -15.
    Inspected actual rendered muzzle-effect captures. Client evidence:
    `build/musket-smoke-client/MUSKET_EFFECTS_PASS.txt` and `effects-0.png` through
    `effects-2.png`. Temporary client verification code is excluded from releases.
  - Musket references: [SA2 implementation](SA2/src/main/java/cyano/steamadvantage/items/MusketItem.java),
    [SA3 implementation](src/main/java/zone/moddev/mc/SteamAdvantage/items/MusketItem.java).

- [x] Restore rotating drill-bit rendering.
  - Implemented a client block-entity renderer using the existing model/texture;
    all axes spin continuously at SA2's one revolution per second.
  - Server verification: all 30 required GameTests passed November 5, 2026,
    including six-facing render-anchor persistence, capability isolation, line
    cleanup, and retraction.
  - Client verification: inspected two actual rendered frames of all six facings
    in a disposable world using software graphics; visible rotation confirmed.
    Captures: `build/drill-smoke-client/rotation-0.png` and `rotation-1.png`.
- [x] Restore connected drill-bit line removal when an individual bit is broken.
  - Server GameTest verified horizontal/vertical middle-bit breaks, drill removal,
    direction changes, progress reset, and preservation of disconnected bits.
  - All 28 required GameTests passed November 5, 2026. Manual client play not checked.
  - References: [SA2 drill-bit entity](SA2/src/main/java/cyano/steamadvantage/blocks/DrillBitTileEntity.java),
    [SA2 drill-bit block](SA2/src/main/java/cyano/steamadvantage/blocks/DrillBitBlock.java),
    [SA3 drill-bit block](src/main/java/zone/moddev/mc/SteamAdvantage/blocks/DrillBitBlock.java).

## Drill retraction feedback

- [ ] Check gradual drill retraction visually and audibly in-game.
  - Implemented: bedrock/unbreakable blocks, block entities, world bounds, and
    maximum reach trigger tip-first retraction at one segment per four ticks.
  - Start/completion sounds and piston sounds with steam puffs at the machine
    indicate retraction. Retraction does not require steam.
  - After retraction, follow available track when powered by steam; otherwise
    remain retracted until a redstone reset or rotation. Output still exports.
  - Server GameTests passed November 5, 2026 (29 required tests): gradual
    retraction, retained inner bits, save/load, idle completion, redstone and
    rotation resets, maximum-range trigger, and existing track/inventory behavior.

## Present in source; preserve during parity work

These are inventory findings, not completed gameplay acceptance tests.

- All 17 SA2 blocks have SA3 standalone definitions, including all boilers,
  machines, steam pipe/track/switch, elevator platform, drill bit, and pump shaft.
- All four standalone items have counterparts: steam governor, steam drill bit,
  blackpowder cartridge, and musket.
- All four musket enchantments exist with effect-handling code: High Explosive,
  Powderless, Rapid Reload, and Recoil. Enchanting costs and weights differ from
  SA2 and should be reviewed if exact balance parity is desired.
- Chest loot targets the same six categories, with village weaponsmith chests
  replacing old village blacksmith chests. Item weights and stack ranges match
  SA2. See [SA3 chest loot](src/main/java/zone/moddev/mc/SteamAdvantage/init/TreasureChests.java).
- Crafting and apocalyptic salvage recipe counterparts are present. The single
  shaped musket recipe supports mirroring, covering SA2's two mirrored recipes.

## Scope notes

- The machinist villager was only a TODO in SA2; it is not missing implemented
  legacy content.
- This checklist records the identified gaps, not a claim that every machine's
  behavior, balance, networking, rendering, and integration has been exhaustively
  validated. Build success and in-game verification should be recorded separately.

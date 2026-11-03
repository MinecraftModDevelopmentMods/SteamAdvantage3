# SA2 → SA3 parity checklist

Source comparison recorded October 31, 2026. These findings describe the source
tree at review time, not verified in-game behavior. Check off an entry after the
implementation and its relevant gameplay checks are complete.

## Missing or reduced functionality

- [ ] Restore missing villager offers.
  - SA2 offered cartridges and ordinary muskets through both toolsmiths and
    weaponsmiths. SA3 currently offers these only through weaponsmiths.
  - Restore the cleric's enchanted-musket offer.
  - Review profession levels against SA2 and document intentional modern mappings.
  - References: [SA2 trades](SA2/src/main/java/cyano/steamadvantage/init/Villages.java),
    [SA3 trades](src/main/java/zone/moddev/mc/SteamAdvantage/init/Villages.java).

- [ ] Restore the steam drill's automatic crushing behavior.
  - SA2 used crusher-recipe output when a recipe existed for the mined block,
    otherwise normal drops. SA3 currently uses normal diamond-pickaxe drops.
  - Verify a block with a crushing recipe, a block without one, and full output
    inventory behavior.
  - References: [SA2 drill](SA2/src/main/java/cyano/steamadvantage/machines/SteamDrillTileEntity.java),
    [SA3 drill](src/main/java/zone/moddev/mc/SteamAdvantage/machines/DrillOperation.java).

- [ ] Restore machine-specific comparator signals.
  - SA3 currently reports steam-tank fullness for every machine.
  - SA2 reported elevator position; drill/furnace/crusher inventory contents;
    coal-boiler fuel inventory; geothermal temperature; electric-boiler energy;
    oil-boiler fuel; pump output fluid; and still input fluid.
  - Verify both signal values and comparator updates as machine state changes.
  - References: [SA2 machines](SA2/src/main/java/cyano/steamadvantage/machines/),
    [SA3 MachineBlock](src/main/java/zone/moddev/mc/SteamAdvantage/machines/MachineBlock.java).

- [ ] Restore musket steel-ingot anvil repair.
- [ ] Restore musket damage, loaded/unloaded, and ammunition tooltips.
- [ ] Restore the musket's Infinity ammunition exemption.
- [ ] Accept compatible ammunition through the shared blackpowder ammunition tag,
  rather than checking only SA3's own cartridge.
- [ ] Restore or explicitly document changes to musket block interactions.
  - SA2 could break plants, vines, webs, and circuit-material blocks as well as
    glass and leaves, and requested block drops.
  - SA3 currently explicitly breaks leaves and impermeable-tag blocks without
    drops, and ignites TNT.
- [ ] Restore musket muzzle/impact particles and ordinary camera kick.
  - Musket references: [SA2 implementation](SA2/src/main/java/cyano/steamadvantage/items/MusketItem.java),
    [SA3 implementation](src/main/java/zone/moddev/mc/SteamAdvantage/items/MusketItem.java).

- [ ] Restore rotating drill-bit rendering.
- [ ] Restore connected drill-bit line removal when an individual bit is broken.
  - Verify breaking a middle bit, removing the drill, and changing drill direction.
  - References: [SA2 drill-bit entity](SA2/src/main/java/cyano/steamadvantage/blocks/DrillBitTileEntity.java),
    [SA2 drill-bit block](SA2/src/main/java/cyano/steamadvantage/blocks/DrillBitBlock.java),
    [SA3 drill-bit block](src/main/java/zone/moddev/mc/SteamAdvantage/blocks/DrillBitBlock.java).

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

# Musket block impacts

Ordinary shots stop at the nearest entity, block outline, or source fluid within
64 blocks. Thin outlines (plants, wire, rails, buttons, etc.) can intercept a shot
even when they have no collision shape. Flowing fluids do not intercept shots.
Source-fluid interception restores SA2's liquid ray-tracing setting. SA3 keeps
its nearest-obstruction handling rather than SA2's entity-first lookup, which
could select entities behind blocks.

On a block impact, the musket plays the gravel impact sound and applies these
rules in order:

1. TNT blocks, including subclasses, receive the normal ignition callback with
   the shooter as igniter. The unprimed block is removed without an item drop.
2. Blocks in `steamadvantage:musket_breakable` are destroyed with normal loot
   drops enabled and the shooter supplied as the breaking entity.
3. Other blocks remain intact. The shot does not continue through the impact.

High Explosive continues to apply its existing explosion separately; its blast
can damage surrounding blocks beyond these ordinary-shot rules.

## Mapping the removed SA2 material categories

Minecraft 1.21.1 has no direct counterpart to the old `Material` categories.
The default mapping lives in
[`musket_breakable.json`](../src/main/resources/data/steamadvantage/tags/block/musket_breakable.json):

| SA2 material | SA3 default mapping |
| --- | --- |
| Glass | `c:glass_blocks` and `c:glass_panes` |
| Leaves | `minecraft:leaves` |
| Plants | Flowers, saplings and crops tags; explicit grass, fern, bush, mushroom, sugar cane, lily pad, roots, seagrass and kelp entries |
| Vine | Vine blocks |
| Web | Cobwebs |
| Circuits | Rails, buttons and pressure plates tags; redstone wire, torches, repeaters, comparators, levers, tripwire/hooks and ladders |

This is an explicit modern mapping, not a claim that every modded plant or
component matches a former material. Modded blocks in the referenced shared
tags participate automatically. Datapacks can add other blocks to the musket tag
or replace its values. Untagged modern vegetation/components are not inferred
from hardness, appearance, or collision shape.

Loot uses the block's ordinary loot table without a tool or Silk Touch. Glass,
panes, webs and leaves therefore follow their usual loot conditions; enabling
drops does not force the original block item to appear. Drops also respect the
world's normal loot gamerules. SA3 no longer uses `minecraft:impermeable` as a
proxy for glass and no longer suppresses ordinary block loot.

## Verification

November 5, 2026: all 42 required server GameTests passed. Shot tests cover glass,
stained glass panes, leaves, cobwebs, vines, grass, flowers, crops, redstone wire,
repeaters, rails, pressure plates, buttons, intact stone, normal redstone loot,
TNT priming/ownership, source-water interception, and a nearer entity shielding
the block behind it. Manual client play was not checked for this change.

## Shot effects

The server broadcasts an explosion puff one block forward from the shooter's eye
position along the original aim. Actual block/entity impacts also broadcast one
large smoke particle and one small puff; a miss has no impact effect. Nearby
clients receive these normal particle packets, including the shooter.

The shooter receives one server-confirmed `steamadvantage:musket_shot` message
per firing action. On the client main thread it raises the camera by 15 degrees,
clamped at -90 degrees. The server computes the shot before this feedback, so
the kick does not change the bullet's aim. No predicted copy is played locally.
The Recoil enchantment's physical knockback remains independent.

November 5, 2026: all 43 required server GameTests passed. A real client verified
block hits, entity hits and misses with exactly one muzzle packet and one shot
acknowledgement per shot, smoke/puff packets only for hits, and a resulting pitch
of -15 degrees. Rendered muzzle captures were inspected. Evidence is under
`build/musket-smoke-client/` (`MUSKET_EFFECTS_PASS.txt`, `effects-0.png` through
`effects-2.png`). The temporary verification fixture is not packaged.

package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.SteamAdvantage.SteamAdvantage;
import zone.moddev.mc.SteamAdvantage.init.ModContent;
import zone.moddev.mc.SteamAdvantage.items.MusketItem;

@GameTestHolder(SteamAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class SteamGameTests {
    private static SteamMachine place(GameTestHelper h, String name, int x, int y, int z) {
        var p = new BlockPos(x, y, z);
        h.setBlock(p, ModContent.block(name));
        var be = h.getLevel().getBlockEntity(h.absolutePos(p));
        h.assertTrue(be instanceof SteamMachine, "Machine entity: " + name);
        return (SteamMachine) be;
    }
    @GameTest(template = "empty")
    public static void drillBitLineCleanup(GameTestHelper h) {
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
        // Exercise horizontal and vertical lines, both halves of a middle break,
        // owner progress, an isolated bit, drill removal and direction changes.
        for (Direction direction : new Direction[] {Direction.EAST, Direction.UP}) {
            BlockPos origin = new BlockPos(2, 2, 2);
            h.setBlock(origin, ModContent.block("steam_drill").defaultBlockState()
                    .setValue(facing, direction));
            var drill = (SteamMachine) h.getLevel().getBlockEntity(h.absolutePos(origin));
            var bit = ModContent.block("drillbit").defaultBlockState().setValue(facing, direction);
            BlockPos isolated = new BlockPos(6, 2, 6);
            for (int scenario = 0; scenario < 3; scenario++) {
                for (int n = 1; n <= 3; n++) h.setBlock(origin.relative(direction, n), bit);
                h.setBlock(isolated, bit);
                drill.depth = 3;
                drill.progress[0] = 12;
                if (scenario == 0)
                    h.getLevel().destroyBlock(h.absolutePos(origin.relative(direction, 2)), true);
                else if (scenario == 1)
                    h.setBlock(origin, drill.getBlockState().setValue(facing, direction.getOpposite()));
                else
                    h.getLevel().removeBlock(h.absolutePos(origin), false);
                for (int n = 1; n <= 3; n++)
                    h.assertTrue(h.getBlockState(origin.relative(direction, n)).isAir(),
                            "Connected bit removed: " + direction + " scenario " + scenario);
                h.assertTrue(h.getBlockState(isolated).is(ModContent.block("drillbit")),
                        "Disconnected bit preserved");
                h.assertTrue(drill.depth == 0 && drill.progress[0] == 0, "Drill progress reset");
                if (scenario == 1)
                    h.setBlock(origin, drill.getBlockState().setValue(facing, direction));
            }
            h.setBlock(isolated, Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void drillRetractsGraduallyAndWaitsForReset(GameTestHelper h) {
        var drill = place(h, "steam_drill", 2, 2, 2);
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
        h.setBlock(new BlockPos(2, 2, 2), drill.getBlockState().setValue(facing, Direction.UP));
        var bit = ModContent.block("drillbit").defaultBlockState().setValue(facing, Direction.UP);
        for (int n = 1; n <= 3; n++) h.setBlock(new BlockPos(2, 2 + n, 2), bit);
        h.setBlock(new BlockPos(2, 6, 2), Blocks.BEDROCK);
        drill.depth = 3;
        DrillOperation.drill(drill);
        h.assertTrue(drill.drillPhase == 1, "Bedrock starts retraction without steam");
        for (int n = 0; n < 4; n++) DrillOperation.drill(drill);
        h.assertTrue(drill.depth == 2 && h.getBlockState(new BlockPos(2, 5, 2)).isAir(),
                "Only the outermost segment retracts first");
        h.assertTrue(h.getBlockState(new BlockPos(2, 3, 2)).is(ModContent.block("drillbit")),
                "Inner line survives controlled retraction");
        var saved = drill.saveWithoutMetadata(h.getLevel().registryAccess());
        drill.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(drill.drillPhase == 1 && drill.depth == 2, "Retraction survives save/load");
        for (int n = 0; n < 20; n++) DrillOperation.drill(drill);
        h.assertTrue(drill.depth == 0 && drill.drillPhase == 2, "Drill stays retracted");
        drill.fluids.fill(steam(10000), FluidAction.EXECUTE);
        for (int n = 0; n < 10; n++) DrillOperation.drill(drill);
        h.assertTrue(drill.depth == 0, "Steam does not restart completed drill");
        h.setBlock(new BlockPos(3, 2, 2), Blocks.REDSTONE_BLOCK);
        SteamMachine.tick(h.getLevel(), drill.getBlockPos(), drill.getBlockState(), drill);
        h.assertTrue(drill.drillPhase == 0, "Redstone resets completion");
        h.setBlock(new BlockPos(3, 2, 2), Blocks.AIR);
        DrillOperation.drill(drill);
        h.assertTrue(drill.depth == 1, "Drilling resumes after reset");
        drill.clearDrill();
        drill.depth = DrillOperation.MAX_RANGE;
        DrillOperation.drill(drill);
        h.assertTrue(drill.drillPhase == 1, "Maximum range also starts retraction");
        drill.clearDrill();
        drill.drillPhase = 2;
        h.setBlock(new BlockPos(2, 2, 2), drill.getBlockState().setValue(facing, Direction.DOWN));
        h.assertTrue(drill.drillPhase == 0, "Rotation resets completion");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void drillBitRenderAnchorsPersistWithoutMachineCapabilities(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2);
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
        for (Direction direction : Direction.values()) {
            h.setBlock(pos, ModContent.block("drillbit").defaultBlockState().setValue(facing, direction));
            var bit = h.getLevel().getBlockEntity(h.absolutePos(pos));
            h.assertTrue(bit instanceof zone.moddev.mc.SteamAdvantage.blocks.DrillBitBlockEntity,
                    "Drill bit has a render anchor for " + direction);
            var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                    bit.getBlockPos(), bit.getBlockState(),
                    bit.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertTrue(restored instanceof zone.moddev.mc.SteamAdvantage.blocks.DrillBitBlockEntity
                            && restored.getBlockState().getValue(facing) == direction,
                    "Render anchor survives persistence with facing " + direction);
            h.assertTrue(h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                            bit.getBlockPos(), direction) == null,
                    "Drill bit does not receive machine capabilities");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void villagerOffersIncludeBothSmithsAndEnchantedCleric(GameTestHelper h) {
        var trader = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(2, 2, 2));
        var random = net.minecraft.util.RandomSource.create(20261105L);
        for (var profession : new net.minecraft.world.entity.npc.VillagerProfession[] {
                net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH,
                net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH}) {
            var tiers = net.minecraft.world.entity.npc.VillagerTrades.TRADES.get(profession);
            int cartridgeOffers = 0, musketOffers = 0;
            for (int level = 1; level <= 5; level++) {
                var listings = tiers.get(level);
                if (listings == null) continue;
                for (var listing : listings) {
                    var offer = listing.getOffer(trader, random);
                    if (offer == null) continue;
                    var result = offer.getResult();
                    if (result.is(ModContent.item("blackpowder_cartridge"))) {
                        cartridgeOffers++;
                        h.assertTrue(level == 2 && result.getCount() >= 5 && result.getCount() <= 7
                                        && offer.getBaseCostA().is(Items.EMERALD)
                                        && offer.getBaseCostA().getCount() == 1,
                                "Smith cartridge tier, price and quantity");
                    }
                    if (result.is(ModContent.item("musket"))) {
                        musketOffers++;
                        h.assertTrue(level == 3 && !result.isEnchanted() && result.getCount() == 1
                                        && offer.getBaseCostA().getCount() >= 10
                                        && offer.getBaseCostA().getCount() <= 15,
                                "Smith ordinary musket tier and price");
                    }
                }
            }
            h.assertTrue(cartridgeOffers == 1 && musketOffers == 1,
                    "Both smith professions have exactly one listing per restored item");
        }
        int clericMusketListings = 0;
        var cleric = net.minecraft.world.entity.npc.VillagerTrades.TRADES.get(
                net.minecraft.world.entity.npc.VillagerProfession.CLERIC);
        for (int level = 1; level <= 5; level++) {
            var listings = cleric.get(level);
            if (listings == null) continue;
            for (var listing : listings) {
                var offer = listing.getOffer(trader, random);
                if (offer == null || !offer.getResult().is(ModContent.item("musket"))) continue;
                clericMusketListings++;
                h.assertTrue(level == 3, "Cleric enchanted musket is journeyman tier");
                for (int sample = 0; sample < 256; sample++) {
                    offer = listing.getOffer(trader, random);
                    h.assertTrue(offer != null && offer.getResult().isEnchanted()
                                    && offer.getResult().getCount() == 1,
                            "Cleric generates an actually enchanted musket");
                    h.assertTrue(offer.getBaseCostA().is(Items.EMERALD)
                                    && offer.getBaseCostA().getCount() >= 27
                                    && offer.getBaseCostA().getCount() <= 44
                                    && offer.getMaxUses() == 3,
                            "Cleric price and stock bounds");
                }
            }
        }
        h.assertTrue(clericMusketListings == 1, "Exactly one cleric enchanted-musket listing");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void drillUsesCrushingRecipesAndFallsBackToNormalDrops(GameTestHelper h) {
        var drill = place(h, "steam_drill", 3, 2, 3);
        var target = new BlockPos(3, 2, 2);
        drill.fluids.fill(steam(5000), FluidAction.EXECUTE);
        for (var block : new net.minecraft.world.level.block.Block[] {
                Blocks.COBBLESTONE, Blocks.BRICKS, Blocks.DIRT}) {
            h.setBlock(target, block);
            drill.depth = 0;
            drill.progress[0] = 0;
            for (int slot = 4; slot < 9; slot++) drill.items.setStackInSlot(slot, ItemStack.EMPTY);
            if (block == Blocks.DIRT)
                h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(
                                zone.moddev.mc.lib.common.recipes.CrushingRecipe.TYPE.get(),
                                new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(block)),
                                h.getLevel()).isEmpty(), "Fallback block has no crushing recipe");
            int ticks = DrillOperation.breakTicks(block.defaultBlockState(), h.getLevel(), h.absolutePos(target));
            for (int tick = 0; tick < ticks; tick++) DrillOperation.drill(drill);
            var expected = block == Blocks.COBBLESTONE ? Items.GRAVEL
                    : block == Blocks.BRICKS ? Items.BRICK : Items.DIRT;
            int count = block == Blocks.BRICKS ? 4 : 1;
            h.assertTrue(drill.items.getStackInSlot(4).is(expected)
                            && drill.items.getStackInSlot(4).getCount() == count,
                    "Correct crushing/fallback output for " + block);
            h.assertTrue(drill.depth == 1 && h.getBlockState(target).is(ModContent.block("drillbit")),
                    "Mining replaces the block with one drill segment");
            for (int slot = 5; slot < 9; slot++)
                h.assertTrue(drill.items.getStackInSlot(slot).isEmpty(), "No extra normal drops");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void drillWaitsForCompleteCrushingOutputCapacity(GameTestHelper h) {
        var drill = place(h, "steam_drill", 3, 2, 3);
        var target = new BlockPos(3, 2, 2);
        h.setBlock(target, Blocks.BRICKS);
        h.setBlock(new BlockPos(3, 2, 1), Blocks.BEDROCK);
        drill.fluids.fill(steam(5000), FluidAction.EXECUTE);
        for (int slot = 4; slot < 9; slot++)
            drill.items.setStackInSlot(slot, new ItemStack(Items.DIAMOND, 64));
        drill.items.setStackInSlot(4, new ItemStack(Items.BRICK, 63));
        for (int tick = 0; tick < 20; tick++) DrillOperation.drill(drill);
        h.assertTrue(h.getBlockState(target).is(Blocks.BRICKS) && drill.depth == 0
                        && drill.progress[0] == 0 && drill.fluids.tank(0).getFluidAmount() == 5000,
                "Full output preserves target, depth, progress and steam");
        drill.items.setStackInSlot(5, new ItemStack(Items.BRICK, 62));
        DrillOperation.drill(drill);
        h.assertTrue(drill.items.getStackInSlot(4).getCount() == 63
                        && drill.items.getStackInSlot(5).getCount() == 62
                        && drill.fluids.tank(0).getFluidAmount() == 5000,
                "Three spaces cannot partially accept a four-brick recipe");
        drill.items.setStackInSlot(5, new ItemStack(Items.BRICK, 61));
        int ticks = DrillOperation.breakTicks(Blocks.BRICKS.defaultBlockState(), h.getLevel(), h.absolutePos(target));
        for (int tick = 0; tick < ticks; tick++) DrillOperation.drill(drill);
        h.assertTrue(drill.items.getStackInSlot(4).getCount() == 64
                        && drill.items.getStackInSlot(5).getCount() == 64 && drill.depth == 1,
                "Mining resumes and distributes the complete recipe across output slots");
        for (int tick = 0; tick < 8; tick++) DrillOperation.drill(drill);
        h.assertTrue(drill.items.getStackInSlot(4).getCount() == 64
                        && drill.items.getStackInSlot(5).getCount() == 64,
                "Subsequent ticks do not duplicate crushing output");
        h.succeed();
    }

    private static void setComparatorSource(SteamMachine m, int fraction) {
        switch (m.kind) {
            case "steam_elevator" -> m.raised = fraction > 0;
            case "steam_boiler_geothermal" -> m.temperature = 1000 * fraction;
            case "steam_boiler_electric" -> m.energy.setStored(m.energy.capacity() * fraction / 2);
            case "steam_drill", "steam_furnace", "steam_crusher", "steam_boiler_coal" -> {
                int first = m.kind.equals("steam_drill") ? 4 : m.kind.equals("steam_furnace") ? 1 : 0;
                int end = m.kind.equals("steam_drill") ? 9 : m.kind.equals("steam_furnace") ? 4 : 1;
                for (int slot = first; slot < end; slot++)
                    m.items.setStackInSlot(slot, fraction == 0 ? ItemStack.EMPTY
                            : new ItemStack(Items.COAL, fraction * 32));
            }
            default -> {
                int tank = m.kind.equals("steam_boiler_oil") ? m.fuel
                        : m.kind.equals("steam_pump") ? m.product
                        : m.kind.equals("steam_still") ? m.water : m.steam;
                var fluid = m.kind.equals("steam_boiler_oil") ? Fluids.LAVA
                        : m.kind.equals("steam_pump") || m.kind.equals("steam_still") ? Fluids.WATER
                        : zone.moddev.mc.poweradvantage.init.Fluids.STEAM.get();
                m.fluids.tank(tank).drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
                if (fraction > 0) m.fluids.tank(tank).fill(
                        new FluidStack(fluid, m.fluids.tank(tank).getCapacity() * fraction / 2),
                        FluidAction.EXECUTE);
            }
        }
    }

    @GameTest(template = "empty")
    public static void machineComparatorSourcesAndScaling(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2);
        for (String kind : ModContent.MACHINES) {
            h.setBlock(pos, Blocks.AIR);
            var m = place(h, kind, 2, 2, 2);
            for (int fraction = 0; fraction <= 2; fraction++) {
                setComparatorSource(m, fraction);
                int expected = kind.equals("steam_elevator") ? (fraction == 0 ? 0 : 15)
                        : fraction == 0 ? 0 : fraction == 1 ? 7 : 15;
                h.assertTrue(m.getBlockState().getAnalogOutputSignal(h.getLevel(), m.getBlockPos()) == expected,
                        kind + " comparator fraction " + fraction + " expected " + expected);
            }
            setComparatorSource(m, 0);
            if (!kind.equals("steam_tank") && !kind.equals("steam_switch")) {
                m.fluids.tank(m.steam).fill(steam(m.getSteamCapacity()), FluidAction.EXECUTE);
                h.assertTrue(m.comparatorSignal() == 0, kind + " ignores unrelated steam fullness");
            }
        }
        var drill = place(h, "steam_drill", 2, 2, 2);
        drill.items.setStackInSlot(4, new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(drill.comparatorSignal() == 3, "Nonstackable item fills one of five drill slots");
        drill.items.setStackInSlot(4, new ItemStack(Items.ENDER_PEARL, 8));
        h.assertTrue(drill.comparatorSignal() == 1, "Sixteen-stack item fullness is normalized");
        drill.items.setStackInSlot(4, new ItemStack(Items.COAL));
        h.assertTrue(drill.comparatorSignal() == 1, "Nonempty inventory has a minimum signal of one");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void realComparatorsRefreshWhenMachineSourcesChange(GameTestHelper h) {
        verifyComparatorUpdate(h, 0);
    }

    private static void verifyComparatorUpdate(GameTestHelper h, int index) {
        if (index == ModContent.MACHINES.size()) { h.succeed(); return; }
        var machinePos = new BlockPos(2, 2, 2);
        var comparatorPos = new BlockPos(3, 2, 2);
        h.setBlock(comparatorPos, Blocks.AIR);
        h.setBlock(machinePos, Blocks.AIR);
        var m = place(h, ModContent.MACHINES.get(index), 2, 2, 2);
        h.setBlock(machinePos.above(), Blocks.REDSTONE_BLOCK); // Pause processing without powering the comparator.
        h.setBlock(comparatorPos.below(), Blocks.STONE);
        h.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                Direction.WEST));
        setComparatorSource(m, 2);
        h.runAfterDelay(4, () -> {
            var comparator = (net.minecraft.world.level.block.entity.ComparatorBlockEntity)
                    h.getLevel().getBlockEntity(h.absolutePos(comparatorPos));
            h.assertTrue(comparator.getOutputSignal() == 15, m.kind + " comparator rises to 15");
            // A raw stack mutation bypasses onChange, as some machine operations do.
            if (m.kind.equals("steam_boiler_coal") || m.kind.equals("steam_crusher"))
                m.items.getStackInSlot(0).shrink(64);
            else setComparatorSource(m, 0);
            h.runAfterDelay(4, () -> {
                h.assertTrue(comparator.getOutputSignal() == 0, m.kind + " comparator falls to zero");
                verifyComparatorUpdate(h, index + 1);
            });
        });
    }

    /** Temporary tag membership exercises datapack integration without shipping test tag entries. */
    private static void withTestItemTag(Item item, String id, Runnable test) {
        var registry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.ResourceLocation.parse(id));
        var previous = new java.util.HashMap<net.minecraft.tags.TagKey<Item>,
                java.util.List<net.minecraft.core.Holder<Item>>>();
        registry.getTags().forEach(pair -> previous.put(pair.getFirst(), pair.getSecond().stream().toList()));
        previous.putIfAbsent(tag, java.util.List.of());
        var changed = new java.util.HashMap<>(previous);
        var members = new java.util.ArrayList<>(previous.get(tag));
        members.add(item.builtInRegistryHolder());
        changed.put(tag, members);
        registry.bindTags(changed);
        try { test.run(); } finally { registry.bindTags(previous); }
    }

    @GameTest(template = "empty")
    public static void musketAcceptsSharedAmmoAndRejectsUntaggedItems(GameTestHelper h) {
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var gun = new ItemStack(ModContent.item("musket"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        player.getInventory().add(new ItemStack(Items.SNOWBALL, 3));
        var musket = (MusketItem) gun.getItem();
        h.assertTrue(musket.use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND)
                        .getResult() == net.minecraft.world.InteractionResult.FAIL,
                "Untagged ammunition cannot start loading");
        withTestItemTag(Items.SNOWBALL, "c:ammo/blackpowder", () -> {
            h.assertTrue(musket.use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND)
                            .getResult() != net.minecraft.world.InteractionResult.FAIL,
                    "Shared ammunition can start loading");
            musket.finishUsingItem(gun, h.getLevel(), player);
            h.assertTrue(MusketItem.loaded(gun) && player.getInventory().countItem(Items.SNOWBALL) == 2,
                    "Loading consumes exactly one compatible cartridge");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void musketFreeAmmoEnchantmentsLoadWithoutCartridges(GameTestHelper h) {
        for (String name : new String[] {"minecraft:infinity", "steamadvantage:powderless"}) {
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var gun = new ItemStack(ModContent.item("musket"));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                    net.minecraft.resources.ResourceLocation.parse(name));
            gun.enchant(h.getLevel().registryAccess().registryOrThrow(
                    net.minecraft.core.registries.Registries.ENCHANTMENT).getHolderOrThrow(key), 1);
            var musket = (MusketItem) gun.getItem();
            h.assertTrue(musket.use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND)
                            .getResult() != net.minecraft.world.InteractionResult.FAIL,
                    name + " allows loading with no ammunition");
            musket.finishUsingItem(gun, h.getLevel(), player);
            h.assertTrue(MusketItem.loaded(gun), name + " finishes loading");
            musket.use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
            player.getInventory().add(new ItemStack(ModContent.item("blackpowder_cartridge"), 2));
            musket.finishUsingItem(gun, h.getLevel(), player);
            h.assertTrue(MusketItem.loaded(gun)
                            && player.getInventory().countItem(ModContent.item("blackpowder_cartridge")) == 2,
                    name + " preserves existing ammunition");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void musketSteelRepairAndInfinityBookWorkInAnvil(GameTestHelper h) {
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var gun = new ItemStack(ModContent.item("musket"));
        gun.setDamageValue(100);
        net.minecraft.world.item.component.CustomData.update(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA, gun, tag -> tag.putBoolean("loaded", true));
        var menu = new net.minecraft.world.inventory.AnvilMenu(1, player.getInventory());
        withTestItemTag(Items.IRON_INGOT, "c:ingots/steel", () -> {
            menu.getSlot(0).set(gun.copy());
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
            menu.createResult();
            var result = menu.getSlot(2).getItem();
            h.assertTrue(!result.isEmpty() && result.getDamageValue() == 38 && MusketItem.loaded(result),
                    "One tagged steel ingot repairs a quarter durability and preserves loaded state");
        });
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND));
        menu.createResult();
        h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Nonsteel materials cannot repair the musket");
        var infinity = h.getLevel().registryAccess().registryOrThrow(
                net.minecraft.core.registries.Registries.ENCHANTMENT).getHolderOrThrow(
                net.minecraft.world.item.enchantment.Enchantments.INFINITY);
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        var enchants = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        enchants.set(infinity, 1);
        book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, enchants.toImmutable());
        menu.getSlot(1).set(book);
        menu.createResult();
        h.assertTrue(!menu.getSlot(2).getItem().isEmpty()
                        && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                                infinity, menu.getSlot(2).getItem()) == 1,
                "Survival anvil can apply an Infinity book");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void musketTooltipsReflectDamageLoadedStateAndFreeAmmo(GameTestHelper h) {
        var gun = new ItemStack(ModContent.item("musket"));
        var lines = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        var musket = (MusketItem) gun.getItem();
        musket.appendHoverText(gun, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
        h.assertTrue(lines.size() == 3
                        && lines.get(0).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents damage
                        && damage.getKey().equals("tooltip.musket.damage")
                        && damage.getArgs()[0].equals(java.math.BigDecimal.valueOf(
                                zone.moddev.mc.SteamAdvantage.config.SteamConfig.DAMAGE.get()).stripTrailingZeros().toPlainString()),
                "Damage tooltip uses the configured shot damage and a translation argument");
        h.assertTrue(lines.get(1).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents state
                        && state.getKey().equals("tooltip.musket.unloaded")
                        && lines.get(2).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents ammo
                        && ammo.getKey().equals("tooltip.musket.ammo") && ammo.getArgs().length == 1,
                "Unloaded musket shows state and localized ammunition name");
        gun.enchant(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.INFINITY), 1);
        lines.clear();
        musket.appendHoverText(gun, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
        h.assertTrue(lines.get(2).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents free
                        && free.getKey().equals("tooltip.musket.ammo_free"), "Infinity tooltip requires no ammunition");
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        musket.finishUsingItem(gun, h.getLevel(), player);
        lines.clear();
        musket.appendHoverText(gun, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
        h.assertTrue(lines.size() == 2
                        && lines.get(1).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents loaded
                        && loaded.getKey().equals("tooltip.musket.loaded"),
                "Loaded state replaces the reload requirement");
        h.succeed();
    }

    private static void fireMusketAt(GameTestHelper h, net.minecraft.world.entity.player.Player player,
            BlockPos target, double height) {
        var absolute = h.absolutePos(target);
        player.setPos(absolute.getX() + .5, absolute.getY() + height - player.getEyeHeight(),
                absolute.getZ() + 3.5);
        player.setYRot(180);
        player.setXRot(0);
        var gun = new ItemStack(ModContent.item("musket"));
        net.minecraft.world.item.component.CustomData.update(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA, gun, tag -> tag.putBoolean("loaded", true));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        gun.getItem().use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    @GameTest(template = "empty")
    public static void musketBreaksLegacyFragileBlockCategories(GameTestHelper h) {
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var target = new BlockPos(3, 3, 3);
        for (var block : new net.minecraft.world.level.block.Block[] {Blocks.GLASS, Blocks.RED_STAINED_GLASS_PANE,
                Blocks.OAK_LEAVES, Blocks.COBWEB, Blocks.VINE, Blocks.SHORT_GRASS, Blocks.POPPY,
                Blocks.WHEAT, Blocks.REDSTONE_WIRE, Blocks.REPEATER, Blocks.RAIL,
                Blocks.STONE_PRESSURE_PLATE, Blocks.STONE_BUTTON}) {
            h.setBlock(target.below(), block == Blocks.WHEAT ? Blocks.FARMLAND : Blocks.STONE);
            h.setBlock(target.north(), Blocks.STONE);
            var state = block.defaultBlockState();
            if (block == Blocks.VINE) state = state.setValue(net.minecraft.world.level.block.VineBlock.NORTH, true);
            h.setBlock(target, state);
            // Low aim intersects thin, noncolliding redstone/rail/plant outlines too.
            double height = block == Blocks.STONE_BUTTON ? .5
                    : block == Blocks.RED_STAINED_GLASS_PANE ? .5 : .03;
            fireMusketAt(h, player, target, height);
            h.assertTrue(!h.getBlockState(target).is(block), "Musket breaks " + block);
            h.assertTrue(h.getBlockState(target.north()).is(Blocks.STONE), "Shot stops after first block impact");
        }
        h.setBlock(target, Blocks.STONE);
        fireMusketAt(h, player, target, .5);
        h.assertTrue(h.getBlockState(target).is(Blocks.STONE), "Ordinary solid blocks remain intact");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void musketBlockImpactsDropLootAndAttributeTnt(GameTestHelper h) {
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var target = new BlockPos(3, 3, 3);
        h.setBlock(target.below(), Blocks.STONE);
        h.setBlock(target, Blocks.REDSTONE_WIRE);
        fireMusketAt(h, player, target, .03);
        var area = new net.minecraft.world.phys.AABB(h.absolutePos(target)).inflate(1);
        var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area);
        h.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(Items.REDSTONE)),
                "Breaking a circuit block requests its normal loot drops");
        h.setBlock(target, Blocks.TNT);
        fireMusketAt(h, player, target, .5);
        var primed = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.PrimedTnt.class, area);
        h.assertTrue(h.getBlockState(target).isAir() && primed.size() == 1
                        && primed.getFirst().getOwner() == player,
                "TNT is primed once, removed and attributed to the shooter");
        primed.forEach(net.minecraft.world.entity.Entity::discard);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void musketRespectsWaterAndNearerEntityImpacts(GameTestHelper h) {
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var target = new BlockPos(3, 3, 3);
        h.setBlock(target, Blocks.GLASS);
        h.setBlock(target.south(), Blocks.WATER);
        fireMusketAt(h, player, target, .5);
        h.assertTrue(h.getBlockState(target).is(Blocks.GLASS), "Source water intercepts the shot");
        h.setBlock(target.south(), Blocks.AIR);
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, target.south());
        zombie.setNoAi(true);
        zombie.setPos(h.absolutePos(target).getX() + .5, h.absolutePos(target).getY(),
                h.absolutePos(target).getZ() + 1.5);
        float health = zombie.getHealth();
        fireMusketAt(h, player, target, .5);
        h.assertTrue(zombie.getHealth() < health && h.getBlockState(target).is(Blocks.GLASS),
                "A nearer entity takes the shot without breaking the block behind it");
        zombie.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void musketCameraKickIsClampedAndSeparateFromServerAim(GameTestHelper h) {
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var target = new BlockPos(3, 3, 3);
        h.setBlock(target, Blocks.STONE);
        fireMusketAt(h, player, target, .5);
        h.assertTrue(player.getXRot() == 0, "Server firing preserves the original aim");
        zone.moddev.mc.SteamAdvantage.network.MusketShot.kick(player);
        h.assertTrue(player.getXRot() == -15 && player.getDeltaMovement().lengthSqr() == 0,
                "Ordinary feedback kicks the camera upward without moving the player");
        player.setXRot(-85);
        zone.moddev.mc.SteamAdvantage.network.MusketShot.kick(player);
        h.assertTrue(player.getXRot() == -90, "Camera kick clamps at the upward limit");
        player.setXRot(90);
        zone.moddev.mc.SteamAdvantage.network.MusketShot.kick(player);
        h.assertTrue(player.getXRot() == 75, "Camera kick works when aiming down");
        h.succeed();
    }

    private static FluidStack steam(int amount) {
        return new FluidStack(zone.moddev.mc.poweradvantage.init.Fluids.STEAM.get(), amount);
    }
    @GameTest(template = "empty")
    public static void registrationCapabilitiesPersistence(GameTestHelper h) {
        for (String name : ModContent.MACHINES) {
            var m = place(h, name, 2, 2, 2);
            for (var side : Direction.values())
                h.assertTrue(h.getLevel().getCapability(
                                     Capabilities.FluidHandler.BLOCK, m.getBlockPos(), side)
                                != null,
                        "Missing fluid capability " + name);
        }
        var tank = place(h, "steam_tank", 2, 2, 2);
        h.assertTrue(tank.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0,
                "Steam-only filter");
        tank.fluids.fill(steam(123456), FluidAction.EXECUTE);
        var saved = tank.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored = new SteamMachine(tank.getBlockPos(), tank.getBlockState());
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(restored.fluids.tank(0).getFluidAmount() == 123456, "Fluid persistence");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void coalBoilerAndRedstone(GameTestHelper h) {
        var boiler = place(h, "steam_boiler_coal", 2, 2, 2);
        boiler.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        boiler.items.setStackInSlot(0, new ItemStack(Items.COAL));
        h.setBlock(new BlockPos(2, 3, 2), Blocks.SNOW);
        h.runAfterDelay(20, () -> {
            h.assertTrue(boiler.fluids.tank(0).getFluidAmount() > 0, "Boiler produces steam");
            h.assertTrue(boiler.fluids.tank(1).getFluidAmount() < 1000, "Boiler spends water");
            h.assertTrue(h.getBlockState(new BlockPos(2, 3, 2)).isAir(),
                    "Running boiler melts snow above it");
            h.setBlock(new BlockPos(3, 2, 2), Blocks.REDSTONE_BLOCK);
            h.setBlock(new BlockPos(2, 3, 2), Blocks.SNOW);
            int before = boiler.fluids.tank(0).getFluidAmount();
            h.runAfterDelay(10, () -> {
                h.assertTrue(boiler.fluids.tank(0).getFluidAmount() == before,
                        "Redstone disables boiler");
                h.assertTrue(h.getBlockState(new BlockPos(2, 3, 2)).is(Blocks.SNOW),
                        "Disabled boiler does not melt snow");
                h.succeed();
            });
        });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void electricBoilerSpendsEnergy(GameTestHelper h) {
        var boiler = place(h, "steam_boiler_electric", 2, 2, 2);
        boiler.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        boiler.energy.setStored(160);
        h.runAfterDelay(20, () -> {
            h.assertTrue(boiler.energy.stored() == 0, "Energy consumed");
            h.assertTrue(boiler.fluids.tank(0).getFluidAmount() == 500, "FE-to-steam conversion");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pumpConservesWorldFluid(GameTestHelper h) {
        var pump = place(h, "steam_pump", 2, 4, 2);
        pump.fluids.fill(steam(6400), FluidAction.EXECUTE);
        h.setBlock(new BlockPos(2, 3, 2), Blocks.WATER);
        h.runAfterDelay(40, () -> {
            h.assertTrue(pump.fluids.tank(1).getFluidAmount() == 1000, "Pump collected bucket");
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(2, 3, 2))).isEmpty(),
                    "Source removed");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void boilerPipeConsumer(GameTestHelper h) {
        var boiler = place(h, "steam_boiler_coal", 2, 2, 2);
        h.setBlock(new BlockPos(3, 2, 2), ModContent.block("steam_pipe"));
        var furnace = place(h, "steam_furnace", 4, 2, 2);
        boiler.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        boiler.items.setStackInSlot(0, new ItemStack(Items.COAL));
        h.runAfterDelay(60, () -> {
            h.assertTrue(furnace.fluids.tank(0).getFluidAmount() > 0,
                    "PowerAdvantage routes steam to consumer");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void crushingUsesLibraryRecipes(GameTestHelper h) {
        var crusher = place(h, "steam_crusher", 2, 2, 2);
        crusher.fluids.fill(steam(20000), FluidAction.EXECUTE);
        crusher.items.setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
        h.runAfterDelay(130, () -> {
            h.assertTrue(crusher.items.getStackInSlot(0).isEmpty(), "Crushing consumes input");
            h.assertTrue(crusher.items.getStackInSlot(4).is(Items.GRAVEL),
                    "Library recipe produced gravel");
            h.assertTrue(crusher.fluids.tank(0).getFluidAmount() == 1250,
                    "Crushing spends 125 x 150 mB");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void drillMovesAlongTrackWithoutLosingInventory(GameTestHelper h) {
        var drill = place(h, "steam_drill", 3, 2, 3);
        drill.fluids.fill(steam(5000), FluidAction.EXECUTE);
        drill.items.setStackInSlot(4, new ItemStack(Items.DIAMOND, 3));
        h.setBlock(new BlockPos(3, 2, 2), Blocks.BEDROCK);
        h.setBlock(new BlockPos(4, 2, 3), ModContent.block("steam_track"));
        h.runAfterDelay(5, () -> {
            var moved = h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4, 2, 3)));
            h.assertTrue(moved instanceof SteamMachine, "Drill follows track");
            h.assertTrue(((SteamMachine) moved).items.getStackInSlot(4).getCount() == 3,
                    "Drill preserves inventory");
            h.assertTrue(h.getBlockState(new BlockPos(3, 2, 3)).is(ModContent.block("steam_pipe")),
                    "Drill leaves steam pipe");
            h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void drillBreaksBlocksAtDiamondPickaxeSpeed(GameTestHelper h) {
        var level = h.getLevel();
        var p = h.absolutePos(BlockPos.ZERO);
        // Vanilla diamond pickaxe ticks: stone 1.5*30/8, dirt (wrong tool) 0.5*30/1, obsidian 50*30/8.
        h.assertTrue(DrillOperation.breakTicks(Blocks.STONE.defaultBlockState(), level, p) == 6,
                "Stone breaks in 6 ticks");
        h.assertTrue(DrillOperation.breakTicks(Blocks.DIRT.defaultBlockState(), level, p) == 15,
                "Dirt breaks in 15 ticks");
        h.assertTrue(DrillOperation.breakTicks(Blocks.OBSIDIAN.defaultBlockState(), level, p) == 188,
                "Obsidian breaks in 188 ticks");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void starvedDrillPausesInsteadOfRetracting(GameTestHelper h) {
        var drill = place(h, "steam_drill", 3, 2, 3);
        // Enough steam for one pipe segment through air, then starved.
        drill.fluids.fill(steam(300), FluidAction.EXECUTE);
        h.runAfterDelay(5, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(3, 2, 2)).is(ModContent.block("drillbit")),
                    "Starved drill keeps its pipe extended");
            h.assertTrue(drill.getDrillDepth() == 1, "Starved drill keeps its depth");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void elevatorExtendsAndRetracts(GameTestHelper h) {
        var elevator = place(h, "steam_elevator", 3, 2, 3);
        elevator.fluids.fill(steam(10000), FluidAction.EXECUTE);
        h.setBlock(new BlockPos(3, 8, 3), Blocks.STONE);
        h.setBlock(new BlockPos(4, 2, 3), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(5, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(3, 5, 3)).is(ModContent.block("platform")),
                    "Elevator platform raised");
            h.setBlock(new BlockPos(4, 2, 3), Blocks.AIR);
            h.runAfterDelay(5, () -> {
                h.assertTrue(h.getBlockState(new BlockPos(3, 3, 3)).isAir(), "Elevator retracted");
                h.succeed();
            });
        });
    }
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void valveStopsOutputWhenPowered(GameTestHelper h) {
        var valve = place(h, "steam_switch", 3, 2, 3);
        var tank = place(h, "steam_tank", 3, 2, 2);
        valve.fluids.fill(steam(10000), FluidAction.EXECUTE);
        h.setBlock(new BlockPos(4, 2, 3), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(5, () -> {
            h.assertTrue(tank.fluids.tank(0).isEmpty(), "Powered valve blocks output");
            h.setBlock(new BlockPos(4, 2, 3), Blocks.AIR);
            h.runAfterDelay(5, () -> {
                h.assertTrue(tank.fluids.tank(0).getFluidAmount() > 0, "Unpowered valve exports");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void oilAndGeothermalBoilers(GameTestHelper h) {
        var oil = place(h, "steam_boiler_oil", 2, 2, 2);
        oil.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        oil.fluids.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        var geo = place(h, "steam_boiler_geothermal", 6, 2, 2);
        geo.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        h.setBlock(new BlockPos(6, 1, 2), Blocks.MAGMA_BLOCK);
        h.runAfterDelay(60, () -> {
            h.assertTrue(oil.fluids.tank(0).getFluidAmount() > 0
                            && oil.fluids.tank(2).getFluidAmount() == 900,
                    "Oil fuel aliquot consumed");
            h.assertTrue(geo.fluids.tank(0).getFluidAmount() > 0, "Geothermal heat produces steam");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void distillationUsesPowerAdvantageDefaultRecipe(GameTestHelper h) {
        var still = place(h, "steam_still", 2, 2, 2);
        var crudeOil = zone.moddev.mc.poweradvantage.init.Fluids.CRUDE_OIL.get();
        var refinedOil = zone.moddev.mc.poweradvantage.init.Fluids.REFINED_OIL.get();
        h.setBlock(new BlockPos(2, 3, 2), Blocks.SNOW);
        still.fluids.tank(0).fill(steam(150), FluidAction.EXECUTE);
        still.fluids.tank(2).fill(new FluidStack(crudeOil, 2), FluidAction.EXECUTE);
        h.assertTrue(still.fluids.tank(2).getFluidAmount() == 2,
                "PA3's default crude-oil input is accepted");
        h.runAfterDelay(1, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(2, 3, 2)).isAir(),
                    "Running still melts snow above it");
            h.assertTrue(still.fluids.tank(2).isEmpty(), "Input is consumed when operation starts");
            h.assertTrue(still.fluids.tank(1).isEmpty(), "Output waits for recipe duration");
            h.assertTrue(still.getProgress() == 1 && still.getProgressMax() == 2,
                    "PA3 recipe duration drives progress");
            h.runAfterDelay(1, () -> {
                h.assertTrue(still.fluids.tank(1).getFluidAmount() == 1
                                && still.fluids.tank(1).getFluid().getFluid() == refinedOil,
                        "PA3's built-in recipe converts crude oil to refined oil");
                h.assertTrue(still.fluids.tank(0).isEmpty(), "Steam is charged once per operation");
                h.assertTrue(still.getProgress() == 0, "Completed operation clears progress");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void distillationProgressPersistsUntilOutput(GameTestHelper h) {
        var still = place(h, "steam_still", 2, 2, 2);
        still.fluids.tank(0).fill(steam(150), FluidAction.EXECUTE);
        still.fluids.tank(2).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        h.runAfterDelay(10, () -> {
            h.assertTrue(still.getProgress() == 10 && still.getProgressMax() == 20,
                    "Timed recipe accumulates progress");
            h.assertTrue(still.fluids.tank(2).isEmpty()
                            && still.pendingDistillation.getAmount() == 100,
                    "Input and pending output persist during processing");
            var saved = still.saveWithoutMetadata(h.getLevel().registryAccess());
            still.loadWithComponents(saved, h.getLevel().registryAccess());
            h.runAfterDelay(10, () -> {
                h.assertTrue(still.fluids.tank(1).getFluidAmount() == 100
                                && still.fluids.tank(1).getFluid().getFluid() == Fluids.LAVA,
                        "Saved operation completes after the remaining duration");
                h.assertTrue(still.pendingDistillation.isEmpty(), "Completed output is cleared");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void distillationBlockedOutputDoesNotConsumeResources(GameTestHelper h) {
        var still = place(h, "steam_still", 2, 2, 2);
        still.fluids.tank(0).fill(steam(150), FluidAction.EXECUTE);
        still.fluids.tank(1).fill(new FluidStack(Fluids.LAVA, 4000), FluidAction.EXECUTE);
        still.fluids.tank(2).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        h.runAfterDelay(5, () -> {
            h.assertTrue(still.fluids.tank(0).getFluidAmount() == 150,
                    "Blocked output preserves steam");
            h.assertTrue(still.fluids.tank(2).getFluidAmount() == 1000,
                    "Blocked output preserves input");
            h.assertTrue(still.pendingDistillation.isEmpty(),
                    "Blocked output does not start an operation");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void musketLoadsConsumesAmmoAndUnloads(GameTestHelper h) {
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModContent.item("musket"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        player.getInventory().add(new ItemStack(ModContent.item("blackpowder_cartridge"), 2));
        ((MusketItem) gun.getItem()).finishUsingItem(gun, h.getLevel(), player);
        h.assertTrue(MusketItem.loaded(gun), "Musket loaded");
        h.assertTrue(player.getInventory().countItem(ModContent.item("blackpowder_cartridge")) == 1,
                "Loading consumes one cartridge");
        gun.getItem().use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(!MusketItem.loaded(gun), "Firing unloads");
        h.assertTrue(gun.getDamageValue() == 1, "Firing wears weapon");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void furnaceProcessesThreeLanes(GameTestHelper h) {
        var furnace = place(h, "steam_furnace", 2, 2, 2);
        furnace.fluids.fill(steam(20000), FluidAction.EXECUTE);
        furnace.items.setStackInSlot(0, new ItemStack(Items.COAL));
        furnace.items.setStackInSlot(1, new ItemStack(Items.RAW_IRON));
        furnace.items.setStackInSlot(2, new ItemStack(Items.RAW_GOLD));
        furnace.items.setStackInSlot(3, new ItemStack(Items.RAW_COPPER));
        h.setBlock(new BlockPos(2, 3, 2), Blocks.SNOW);
        h.runAfterDelay(500, () -> {
            h.assertTrue(furnace.items.getStackInSlot(4).is(Items.IRON_INGOT), "Furnace iron lane");
            h.assertTrue(furnace.items.getStackInSlot(5).is(Items.GOLD_INGOT), "Furnace gold lane");
            h.assertTrue(
                    furnace.items.getStackInSlot(6).is(Items.COPPER_INGOT), "Furnace copper lane");
            h.assertTrue(h.getBlockState(new BlockPos(2, 3, 2)).isAir(),
                    "Running furnace melts snow above it");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void hotIdleFurnaceStillMeltsSnow(GameTestHelper h) {
        var furnace = place(h, "steam_furnace", 2, 2, 2);
        furnace.temperature = 1500;
        h.setBlock(new BlockPos(2, 3, 2), Blocks.SNOW);
        h.runAfterDelay(1, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(2, 3, 2)).isAir(),
                    "Furnace without fuel melts snow while it is still hot");
            furnace.temperature = 50;
            h.setBlock(new BlockPos(2, 3, 2), Blocks.SNOW);
            h.runAfterDelay(1, () -> {
                h.assertTrue(h.getBlockState(new BlockPos(2, 3, 2)).is(Blocks.SNOW),
                        "Cool furnace does not melt snow");
                h.succeed();
            });
        });
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fullGeothermalBoilerStillMeltsSnow(GameTestHelper h) {
        var geo = place(h, "steam_boiler_geothermal", 2, 2, 2);
        geo.fluids.tank(geo.steam).fill(steam(geo.fluids.tank(geo.steam).getCapacity()),
                FluidAction.EXECUTE);
        geo.temperature = 500;
        h.setBlock(new BlockPos(2, 3, 2), Blocks.SNOW);
        h.runAfterDelay(1, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(2, 3, 2)).isAir(),
                    "Hot geothermal boiler melts snow with a full steam tank and no water");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void furnaceCoolingClearsResidualTemperature(GameTestHelper h) {
        var furnace = place(h, "steam_furnace", 2, 2, 2);
        furnace.temperature = 0.005f;
        FurnaceOperation.smelt(furnace);
        h.assertTrue(furnace.getTemperature() == 0,
                "Cooling snaps negligible residual temperature to zero");
        h.assertTrue(Float.isFinite(furnace.getTemperature()),
                "Furnace temperature remains finite");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fullCrusherOutputDoesNotConsumeResources(GameTestHelper h) {
        var crusher = place(h, "steam_crusher", 2, 2, 2);
        crusher.fluids.fill(steam(1000), FluidAction.EXECUTE);
        crusher.items.setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
        crusher.items.setStackInSlot(4, new ItemStack(Items.GRAVEL, 64));
        h.runAfterDelay(10, () -> {
            h.assertTrue(crusher.fluids.tank(0).getFluidAmount() == 1000,
                    "Blocked output preserves steam");
            h.assertTrue(crusher.items.getStackInSlot(0).getCount() == 1,
                    "Blocked output preserves input");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void standaloneVariantNeedsNoJavaRegistration(GameTestHelper h) {
        var variant = place(h, "test_large_steam_tank", 2, 2, 2);
        h.assertTrue(variant.getSteamCapacity() == 2345678, "MMDLib decoded variant capacity");
        h.assertTrue(variant.items().getSlots() == 12, "MMDLib decoded variant inventory size");
        h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                             .getKey(variant.getType())
                             .equals(ModContent.id("test_large_steam_tank")),
                "Standalone block entity has its own ID");
        h.assertTrue(zone.moddev.mc.lib.MMDLib.standaloneBlocks().containsKey(
                             ModContent.id("test_large_steam_tank")),
                "Variant came through MMDLib's standalone loader");
        h.assertTrue(!zone.moddev.mc.lib.MMDLib.loadedMaterials().keySet().stream().anyMatch(
                             id -> id.getNamespace().equals(SteamAdvantage.MODID)),
                "No synthetic SteamAdvantage materials");
        variant.fluids().fill(steam(1234567), FluidAction.EXECUTE);
        var bound = zone.moddev.mc.lib.gui.Screens
                            .get(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                                            .getKey(variant.getType()))
                            .orElseThrow();
        var element = bound.screen().find("steam").orElseThrow();
        try {
            var fluid = (FluidStack) bound.values().get(element).read(variant);
            var capacity = (Number) bound.maxima().get(element).read(variant);
            h.assertTrue(fluid.getAmount() == 1234567 && capacity.intValue() == 2345678,
                    "XML getters bind to variant storage");
        } catch (Throwable failure) {
            throw new IllegalStateException("Bound XML getter failed", failure);
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void standaloneItemsAndScreenCatalog(GameTestHelper h) {
        for (String name : java.util.List.of(
                     "steam_governor", "steam_drill_bit", "blackpowder_cartridge", "musket")) {
            h.assertTrue(
                    zone.moddev.mc.lib.MMDLib.standaloneItems().containsKey(ModContent.id(name)),
                    "Standalone item plan " + name);
            h.assertTrue(ModContent.runtime().items().containsKey(ModContent.id(name)),
                    "Runtime item plan " + name);
        }
        var gun = new ItemStack(ModContent.item("musket"));
        h.assertTrue(gun.getMaxDamage() == 250 && gun.getMaxStackSize() == 1,
                "Musket properties come from its module");
        for (String name : ModContent.MACHINES) {
            var bound = zone.moddev.mc.lib.gui.Screens.get(ModContent.id(name)).orElseThrow();
            var layout = zone.moddev.mc.lib.gui.layout.ScreenLayout.of(
                    bound.screen(), zone.moddev.mc.lib.gui.layout.TextMeasure.NONE);
            h.assertTrue(layout.panel().width() > 0 && layout.panel().height() > 0,
                    "MMDLib laid out " + name);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void declarativeMenuRespectsHiddenSlotsAndRoles(GameTestHelper h) {
        var crusher = place(h, "steam_crusher", 2, 2, 2);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(crusher.getBlockPos().getX() + .5, crusher.getBlockPos().getY() + .5,
                crusher.getBlockPos().getZ() + .5);
        var menu =
                new zone.moddev.mc.lib.gui.runtime.MachineMenu(1, player.getInventory(), crusher);
        h.assertTrue(menu.slots.get(0).isActive() && menu.slots.get(4).isActive(),
                "XML declares input/output slots");
        h.assertTrue(!menu.slots.get(1).isActive()
                        && !menu.slots.get(1).mayPlace(new ItemStack(Items.COBBLESTONE)),
                "Undeclared slot refuses clicks");
        h.assertTrue(!menu.slots.get(4).mayPlace(new ItemStack(Items.GRAVEL)),
                "Output role refuses insertion");
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        menu.quickMoveStack(player, menu.slots.size() - 9);
        h.assertTrue(crusher.items().getStackInSlot(0).getCount() == 3,
                "Shift-click follows declared input");
        crusher.items().setStackInSlot(4, new ItemStack(Items.GRAVEL, 2));
        menu.quickMoveStack(player, 4);
        h.assertTrue(
                player.getInventory().countItem(Items.GRAVEL) == 2, "Shift-click extracts output");
        h.assertTrue(menu.slots.get(4).index == 4, "MMDLib preserves XML slot indices");
        h.assertTrue(menu.stillValid(player), "Nearby original host is valid");
        h.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        h.assertTrue(!menu.stillValid(player), "Removed host invalidates menu");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void screenSnapshotsPreserveLargeAndFluidValues(GameTestHelper h) {
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            String number = java.math.BigInteger.ONE.shiftLeft(200).toString();
            var packet = new zone.moddev.mc.lib.gui.runtime.ScreenNetwork.Values(4,
                    java.util.Map.of(
                            0, new java.math.BigInteger(number), 1, steam(1234567), 2, 12.375));
            zone.moddev.mc.lib.gui.runtime.ScreenNetwork.Values.CODEC.encode(buffer, packet);
            var copy = zone.moddev.mc.lib.gui.runtime.ScreenNetwork.Values.CODEC.decode(buffer);
            h.assertTrue(copy.delta().get(0).equals(new java.math.BigInteger(number)),
                    "Library preserves whole values exceeding long");
            h.assertTrue(((FluidStack) copy.delta().get(1)).getAmount() == 1234567,
                    "Library preserves fluid amounts exceeding 16 bits");
            h.assertTrue(copy.delta().get(2).equals(12.375), "Library preserves decimals");
            h.assertTrue(copy.menu() == 4, "Snapshot identifies its menu");
        } finally {
            buffer.release();
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void mismatchedScreenAndInvalidSettingsAreRejected(GameTestHelper h) {
        var tank = place(h, "steam_tank", 2, 2, 2);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        boolean rejected = false;
        try {
            buffer.writeBlockPos(tank.getBlockPos());
            buffer.writeUtf("wrong-definition");
            new zone.moddev.mc.lib.gui.runtime.MachineMenu(1, player.getInventory(), buffer);
        } catch (IllegalStateException expected) {
            rejected = true;
        } finally {
            buffer.release();
        }
        h.assertTrue(rejected, "MMDLib rejects mismatched XML definitions");
        var invalid = com.google.gson.JsonParser.parseString(
                "{\"kind\":\"steam_boiler_coal\",\"steam_capacity\":1000,\"water_capacity\":0}");
        boolean bad = false;
        try {
            bad = MachineSettings.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, invalid)
                          .error()
                          .isPresent();
        } catch (IllegalArgumentException expected) {
            bad = true;
        }
        h.assertTrue(bad, "DFU settings reject missing mandatory storage");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void partialModelsDoNotOccludeNeighbours(GameTestHelper h) {
        for (String name : ModContent.MACHINES) {
            boolean partial =
                    java.util.Set.of("steam_still", "steam_tank", "steam_boiler_oil", "steam_drill")
                            .contains(name);
            h.assertTrue(ModContent.block(name).defaultBlockState().canOcclude() != partial,
                    name + (partial ? " must not occlude" : " is a full cube and may occlude"));
        }
        h.succeed();
    }
}

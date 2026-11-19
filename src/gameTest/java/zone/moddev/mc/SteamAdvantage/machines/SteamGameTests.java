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
    /** Machines already ported to MMDLib XML definitions. */
    private static final java.util.Set<String> XML_MACHINES =
            java.util.Set.copyOf(ModContent.XML_MACHINES);
    /** The machine's processor of the given type. */
    private static <T> T processor(zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity m, Class<T> type) {
        return m.features.allProcessors().stream().filter(type::isInstance).map(type::cast)
                .findFirst().orElseThrow();
    }
    private static zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity placeXml(
            GameTestHelper h, String name, int x, int y, int z) {
        var p = new BlockPos(x, y, z);
        h.setBlock(p, ModContent.block(name));
        var be = h.getLevel().getBlockEntity(h.absolutePos(p));
        h.assertTrue(be instanceof zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity,
                "XML machine entity: " + name);
        return (zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity) be;
    }
    /** Sets an XML machine's comparator source to empty, half or full. */
    private static void setXmlComparatorSource(zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity m, int fraction) {
        switch (m.definition.id().getPath()) {
            case "steam_elevator" -> processor(m, ElevatorProcessor.class).setRaised(fraction > 0);
            case "steam_boiler_geothermal" ->
                processor(m, GeothermalBoilerProcessor.class).setTemperature(1000 * fraction);
            case "steam_boiler_electric" -> m.energy.setStored(m.energy.capacity() * fraction / 2);
            case "steam_boiler_coal" -> m.items.setStackInSlot(0, fraction == 0 ? ItemStack.EMPTY
                    : new ItemStack(Items.COAL, fraction * 32));
            case "steam_drill" -> {
                for (int slot = 0; slot < 5; slot++)
                    m.items.setStackInSlot(slot, fraction == 0 ? ItemStack.EMPTY
                            : new ItemStack(Items.COAL, fraction * 32));
            }
            default -> {
                String kind = m.definition.id().getPath();
                int index = kind.equals("steam_boiler_oil") || kind.equals("steam_still") ? 2
                        : kind.equals("steam_pump") ? 1 : 0;
                var fluid = kind.equals("steam_boiler_oil") ? Fluids.LAVA
                        : index > 0 ? Fluids.WATER
                        : zone.moddev.mc.poweradvantage.init.Fluids.STEAM.get();
                var tank = m.fluids.tank(index);
                tank.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
                if (fraction > 0)
                    tank.fill(new FluidStack(fluid, tank.getCapacity() * fraction / 2),
                            FluidAction.EXECUTE);
            }
        }
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
            var drill = (zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity) h.getLevel().getBlockEntity(h.absolutePos(origin));
            var dp = processor(drill, DrillProcessor.class);
            var bit = ModContent.block("drillbit").defaultBlockState().setValue(facing, direction);
            BlockPos isolated = new BlockPos(6, 2, 6);
            for (int scenario = 0; scenario < 3; scenario++) {
                for (int n = 1; n <= 3; n++) h.setBlock(origin.relative(direction, n), bit);
                h.setBlock(isolated, bit);
                dp.set(3, DrillProcessor.DRILLING, 12);
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
                h.assertTrue(dp.depth() == 0 && dp.progress() == 0, "Drill progress reset");
                if (scenario == 1)
                    h.setBlock(origin, drill.getBlockState().setValue(facing, direction));
            }
            h.setBlock(isolated, Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void drillRetractsGraduallyAndWaitsForReset(GameTestHelper h) {
        var drill = placeXml(h, "steam_drill", 2, 2, 2);
        var dp = processor(drill, DrillProcessor.class);
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
        h.setBlock(new BlockPos(2, 2, 2), drill.getBlockState().setValue(facing, Direction.UP));
        var bit = ModContent.block("drillbit").defaultBlockState().setValue(facing, Direction.UP);
        for (int n = 1; n <= 3; n++) h.setBlock(new BlockPos(2, 2 + n, 2), bit);
        h.setBlock(new BlockPos(2, 6, 2), Blocks.BEDROCK);
        dp.set(3, DrillProcessor.DRILLING, 0);
        dp.step();
        h.assertTrue(dp.phase() == DrillProcessor.RETRACTING, "Bedrock starts retraction without steam");
        for (int n = 0; n < 4; n++) dp.step();
        h.assertTrue(dp.depth() == 2 && h.getBlockState(new BlockPos(2, 5, 2)).isAir(),
                "Only the outermost segment retracts first");
        h.assertTrue(h.getBlockState(new BlockPos(2, 3, 2)).is(ModContent.block("drillbit")),
                "Inner line survives controlled retraction");
        var saved = drill.saveWithoutMetadata(h.getLevel().registryAccess());
        drill.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(dp.phase() == DrillProcessor.RETRACTING && dp.depth() == 2,
                "Retraction survives save/load");
        for (int n = 0; n < 20; n++) dp.step();
        h.assertTrue(dp.depth() == 0 && dp.phase() == DrillProcessor.RETRACTED, "Drill stays retracted");
        drill.fluids.fill(steam(10000), FluidAction.EXECUTE);
        for (int n = 0; n < 10; n++) dp.step();
        h.assertTrue(dp.depth() == 0, "Steam does not restart completed drill");
        h.setBlock(new BlockPos(3, 2, 2), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(2, () -> {
            h.assertTrue(dp.phase() == DrillProcessor.DRILLING, "Redstone resets completion");
            h.setBlock(new BlockPos(3, 2, 2), Blocks.AIR);
            dp.step();
            h.assertTrue(dp.depth() == 1, "Drilling resumes after reset");
            dp.clear();
            dp.set(DrillProcessor.MAX_RANGE, DrillProcessor.DRILLING, 0);
            dp.step();
            h.assertTrue(dp.phase() == DrillProcessor.RETRACTING, "Maximum range also starts retraction");
            dp.clear();
            dp.set(0, DrillProcessor.RETRACTED, 0);
            h.setBlock(new BlockPos(2, 2, 2), drill.getBlockState().setValue(facing, Direction.DOWN));
            h.assertTrue(dp.phase() == DrillProcessor.DRILLING, "Rotation resets completion");
            h.succeed();
        });
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
    public static void drillReturnsNormalBlockDropsWithoutCrushing(GameTestHelper h) {
        var drill = placeXml(h, "steam_drill", 3, 2, 3);
        var dp = processor(drill, DrillProcessor.class);
        var target = new BlockPos(3, 2, 2);
        // Cobblestone and bricks have crushing recipes; the drill must not apply them.
        for (var block : new net.minecraft.world.level.block.Block[] {
                Blocks.STONE, Blocks.COBBLESTONE, Blocks.BRICKS, Blocks.DIRT}) {
            h.setBlock(target, block);
            dp.set(0, DrillProcessor.DRILLING, 0);
            drill.fluids.fill(steam(5000), FluidAction.EXECUTE);
            for (int slot = 0; slot < 5; slot++) drill.items.setStackInSlot(slot, ItemStack.EMPTY);
            int ticks = DrillProcessor.breakTicks(block.defaultBlockState(), h.getLevel(), h.absolutePos(target));
            for (int tick = 0; tick < ticks; tick++) dp.step();
            var expected = block == Blocks.STONE ? Items.COBBLESTONE : block.asItem();
            h.assertTrue(drill.items.getStackInSlot(0).is(expected)
                            && drill.items.getStackInSlot(0).getCount() == 1,
                    "Normal block drop for " + block);
            h.assertTrue(dp.depth() == 1 && h.getBlockState(target).is(ModContent.block("drillbit")),
                    "Mining replaces the block with one drill segment");
            for (int slot = 1; slot < 5; slot++)
                h.assertTrue(drill.items.getStackInSlot(slot).isEmpty(), "No extra drops");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void drillWaitsForOutputCapacity(GameTestHelper h) {
        var drill = placeXml(h, "steam_drill", 3, 2, 3);
        var dp = processor(drill, DrillProcessor.class);
        var target = new BlockPos(3, 2, 2);
        h.setBlock(target, Blocks.COBBLESTONE);
        h.setBlock(new BlockPos(3, 2, 1), Blocks.BEDROCK);
        drill.fluids.fill(steam(5000), FluidAction.EXECUTE);
        for (int slot = 0; slot < 5; slot++)
            drill.items.setStackInSlot(slot, new ItemStack(Items.DIAMOND, 64));
        for (int tick = 0; tick < 30; tick++) dp.step();
        h.assertTrue(h.getBlockState(target).is(Blocks.COBBLESTONE) && dp.depth() == 0
                        && dp.progress() == 0 && drill.fluids.tank(0).getFluidAmount() == 5000,
                "Full output preserves target, depth, progress and steam");
        drill.items.setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 63));
        int ticks = DrillProcessor.breakTicks(Blocks.COBBLESTONE.defaultBlockState(), h.getLevel(), h.absolutePos(target));
        for (int tick = 0; tick < ticks; tick++) dp.step();
        h.assertTrue(drill.items.getStackInSlot(2).getCount() == 64 && dp.depth() == 1,
                "Mining resumes once there is room");
        for (int tick = 0; tick < 8; tick++) dp.step();
        h.assertTrue(drill.items.getStackInSlot(2).getCount() == 64,
                "Subsequent ticks do not duplicate output");
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
            if (XML_MACHINES.contains(kind))
                continue; // Sampled once per tick; see xmlComparatorScaling.
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
        var drill = placeXml(h, "steam_drill", 2, 2, 2);
        var reading = processor(drill, DrillProcessor.class).redstoneOutputs().getFirst().signal();
        drill.items.setStackInSlot(0, new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(reading.getAsInt() == 3, "Nonstackable item fills one of five drill slots");
        drill.items.setStackInSlot(0, new ItemStack(Items.ENDER_PEARL, 8));
        h.assertTrue(reading.getAsInt() == 1, "Sixteen-stack item fullness is normalized");
        drill.items.setStackInSlot(0, new ItemStack(Items.COAL));
        h.assertTrue(reading.getAsInt() == 1, "Nonempty inventory has a minimum signal of one");
        h.succeed();
    }

    /** MMDLib samples an XML machine's comparator source once per tick, so step a tick apart. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void xmlComparatorScaling(GameTestHelper h) {
        verifyXmlScaling(h, XML_MACHINES.stream().sorted().toList(), 0, 0);
    }
    private static void verifyXmlScaling(
            GameTestHelper h, java.util.List<String> kinds, int index, int fraction) {
        if (index == kinds.size()) { h.succeed(); return; }
        String kind = kinds.get(index);
        if (fraction == 0) h.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        var x = fraction == 0 ? placeXml(h, kind, 2, 2, 2)
                : (zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity)
                          h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 2, 2)));
        // Hold processing so a tank does not empty itself into the test structure.
        h.setBlock(new BlockPos(2, 3, 2), Blocks.REDSTONE_BLOCK);
        setXmlComparatorSource(x, fraction);
        h.runAfterDelay(2, () -> {
            int expected = kind.equals("steam_elevator") ? (fraction == 0 ? 0 : 15)
                    : fraction == 0 ? 0 : fraction == 1 ? 7 : 15;
            h.assertTrue(x.getBlockState().getAnalogOutputSignal(h.getLevel(), x.getBlockPos())
                                 == expected,
                    kind + " comparator fraction " + fraction + " expected " + expected);
            if (fraction < 2) verifyXmlScaling(h, kinds, index, fraction + 1);
            else verifyXmlScaling(h, kinds, index + 1, 0);
        });
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
        String kind = ModContent.MACHINES.get(index);
        if (XML_MACHINES.contains(kind)) {
            var x = placeXml(h, kind, 2, 2, 2);
            h.setBlock(machinePos.above(), Blocks.REDSTONE_BLOCK);
            h.setBlock(comparatorPos.below(), Blocks.STONE);
            h.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                    Direction.WEST));
            setXmlComparatorSource(x, 2);
            h.runAfterDelay(4, () -> {
                var comparator = (net.minecraft.world.level.block.entity.ComparatorBlockEntity)
                        h.getLevel().getBlockEntity(h.absolutePos(comparatorPos));
                h.assertTrue(comparator.getOutputSignal() == 15, kind + " comparator rises to 15");
                setXmlComparatorSource(x, 0);
                h.runAfterDelay(4, () -> {
                    h.assertTrue(comparator.getOutputSignal() == 0, kind + " comparator falls to zero");
                    verifyComparatorUpdate(h, index + 1);
                });
            });
            return;
        }
        var m = place(h, kind, 2, 2, 2);
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
        // Flowers and similar plants have a position-seeded random offset, and GameTests run at a
        // different position each time; aim at the outline's actual centre.
        double offsetX = h.getBlockState(target).getOffset(h.getLevel(), absolute).x;
        player.setPos(absolute.getX() + .5 + offsetX, absolute.getY() + height - player.getEyeHeight(),
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
            var m = XML_MACHINES.contains(name) ? placeXml(h, name, 2, 2, 2)
                                                : place(h, name, 2, 2, 2);
            for (var side : Direction.values())
                h.assertTrue(h.getLevel().getCapability(
                                     Capabilities.FluidHandler.BLOCK, m.getBlockPos(), side)
                                != null,
                        "Missing fluid capability " + name);
        }
        var tank = placeXml(h, "steam_tank", 2, 2, 2);
        h.assertTrue(tank.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0,
                "Steam-only filter");
        tank.fluids.fill(steam(123456), FluidAction.EXECUTE);
        var registries = h.getLevel().registryAccess();
        var saved = tank.saveWithFullMetadata(registries);
        var restored = (zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity)
                net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        tank.getBlockPos(), tank.getBlockState(), saved, registries);
        h.assertTrue(restored.fluids.tank(0).getFluidAmount() == 123456, "Fluid persistence");
        h.succeed();
    }
    private static net.neoforged.neoforge.items.IItemHandler itemsAt(
            GameTestHelper h, net.minecraft.world.level.block.entity.BlockEntity m, Direction side) {
        return h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, m.getBlockPos(), side);
    }
    private static net.neoforged.neoforge.fluids.capability.IFluidHandler fluidsAt(
            GameTestHelper h, net.minecraft.world.level.block.entity.BlockEntity m, Direction side) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, m.getBlockPos(), side);
    }
    @GameTest(template = "empty")
    public static void defaultAutomationSidesMatchRoles(GameTestHelper h) {
        var furnace = place(h, "steam_furnace", 1, 2, 1);
        var boiler = placeXml(h, "steam_boiler_electric", 4, 2, 1);
        var tank = placeXml(h, "steam_tank", 1, 2, 4);
        furnace.items.setStackInSlot(4, new ItemStack(Items.IRON_INGOT, 6));
        for (var side : Direction.values()) {
            var items = itemsAt(h, furnace, side);
            h.assertTrue(items.insertItem(0, new ItemStack(Items.COAL), true).isEmpty(),
                    "Fuel accepted from " + side);
            h.assertTrue(items.insertItem(1, new ItemStack(Items.RAW_IRON), true).isEmpty(),
                    "Smeltable input accepted from " + side);
            h.assertTrue(!items.insertItem(4, new ItemStack(Items.IRON_INGOT), true).isEmpty(),
                    "Output refuses insertion from " + side);
            h.assertTrue(items.extractItem(4, 1, true).getCount() == 1,
                    "Output extractable from " + side);
            var water = fluidsAt(h, boiler, side);
            h.assertTrue(water.fill(new FluidStack(Fluids.WATER, 100), FluidAction.SIMULATE) == 100,
                    "Boiler water input from " + side);
            h.assertTrue(water.fill(steam(100), FluidAction.SIMULATE) == 0,
                    "Boiler steam output refuses filling from " + side);
            var energy = h.getLevel().getCapability(
                    Capabilities.EnergyStorage.BLOCK, boiler.getBlockPos(), side);
            h.assertTrue(energy != null && energy.receiveEnergy(10, true) == 10,
                    "Electric boiler accepts FE from " + side);
            h.assertTrue(fluidsAt(h, tank, side).fill(steam(100), FluidAction.SIMULATE) == 100,
                    "Tank accepts steam from " + side);
        }
        h.assertTrue(h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                             tank.getBlockPos(), Direction.UP) == null,
                "Unpowered machines expose no energy");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void automationSideConfigGatesAndPersists(GameTestHelper h) {
        var boiler = placeXml(h, "steam_boiler_coal", 2, 2, 2);
        var automation = boiler.screenAutomation();
        var water = automation.group(automation.index("water"));
        var facing = boiler.getBlockState().getValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        water.sides().set(
                zone.moddev.mc.lib.api.capabilities.RelativeFace.of(facing, Direction.UP),
                zone.moddev.mc.lib.api.capabilities.IoMode.DISABLED);
        h.assertTrue(fluidsAt(h, boiler, Direction.UP)
                                .fill(new FluidStack(Fluids.WATER, 100), FluidAction.SIMULATE) == 0,
                "Disabled face refuses water");
        h.assertTrue(fluidsAt(h, boiler, Direction.DOWN)
                                .fill(new FluidStack(Fluids.WATER, 100), FluidAction.SIMULATE) == 100,
                "Other faces still accept water");
        var registries = h.getLevel().registryAccess();
        var restored = (zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity) net.minecraft.world.level.block.entity.BlockEntity
                .loadStatic(boiler.getBlockPos(), boiler.getBlockState(),
                        boiler.saveWithFullMetadata(registries), registries);
        var restoredWater = restored.screenAutomation().group(
                restored.screenAutomation().index("water"));
        h.assertTrue(restoredWater.mode(Direction.UP)
                        == zone.moddev.mc.lib.api.capabilities.IoMode.DISABLED
                        && restoredWater.mode(Direction.DOWN)
                                == zone.moddev.mc.lib.api.capabilities.IoMode.INPUT,
                "Side configuration persists");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void powerAdvantagePumpAddonWorksAndDrops(GameTestHelper h) {
        var boiler = placeXml(h, "steam_boiler_coal", 2, 2, 2);
        var still = placeXml(h, "steam_still", 3, 2, 2);
        still.fluids.tank(1).fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
        var automation = boiler.screenAutomation();
        int water = automation.index("water");
        var slot = zone.moddev.mc.lib.api.automation.AutomationInventory.addonSlot(water, 0);
        var pump = new ItemStack(zone.moddev.mc.poweradvantage.init.ModItems.FLUID_INTAKE_PUMP.get());
        h.assertTrue(automation.slots().insertItem(slot, pump, false).isEmpty(),
                "Water group accepts PA's intake pump");
        h.runAfterDelay(25, () -> {
            h.assertTrue(boiler.fluids.tank(1).getFluidAmount() > 0,
                    "Intake pump pulls water from the neighbouring still");
            h.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
            var area = new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(2, 2, 2))).inflate(2);
            h.assertTrue(h.getLevel()
                                    .getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area)
                                    .stream()
                                    .anyMatch(e -> e.getItem().is(
                                            zone.moddev.mc.poweradvantage.init.ModItems.FLUID_INTAKE_PUMP.get())),
                    "Installed addon drops when the machine is broken");
            h.succeed();
        });
    }
    /**
     * A save written by the shared pre-XML SteamMachine entity, with its machine state in
     * top-level keys and the electric boiler's pre-2.0.2 "Energy" key.
     */
    private static zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity importLegacy(
            GameTestHelper h, String kind, java.util.function.Consumer<SteamMachine> setup,
            java.util.function.Consumer<net.minecraft.nbt.CompoundTag> edit) {
        var registries = h.getLevel().registryAccess();
        var placed = placeXml(h, kind, 2, 2, 2);
        var crusher = ModContent.block("steam_crusher").defaultBlockState();
        var settings = switch (kind) {
            case "steam_boiler_electric" -> new MachineSettings(kind, 20000, 4000, 0, 0, 9, 1600, 160);
            case "steam_boiler_coal" -> new MachineSettings(kind, 100000, 4000, 0, 0, 9, 0, 0);
            case "steam_boiler_oil" -> new MachineSettings(kind, 100000, 4000, 4000, 0, 9, 0, 0);
            case "steam_boiler_geothermal" -> new MachineSettings(kind, 10000, 4000, 0, 0, 9, 0, 0);
            case "steam_still" -> new MachineSettings(kind, 10000, 4000, 0, 4000, 9, 0, 0);
            case "steam_pump" -> new MachineSettings(kind, 20000, 0, 0, 4000, 9, 0, 0);
            default -> new MachineSettings(kind, 6400, 0, 0, 0, 9, 0, 0);
        };
        var legacy = new SteamMachine(ModContent.machineType(crusher), placed.getBlockPos(), crusher,
                settings);
        legacy.setLevel(h.getLevel()); // The still's input filter consults the level's recipes.
        setup.accept(legacy);
        var saved = legacy.saveWithFullMetadata(registries);
        saved.putString("id", ModContent.id(kind).toString());
        edit.accept(saved);
        h.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        h.setBlock(new BlockPos(2, 2, 2), placed.getBlockState());
        var imported = (zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity)
                net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        h.absolutePos(new BlockPos(2, 2, 2)), placed.getBlockState(), saved, registries);
        h.assertTrue(imported != null, kind + " imports a pre-XML save");
        var resaved = imported.saveWithFullMetadata(registries);
        h.assertTrue(!resaved.contains("TotalBurn") && !resaved.contains("Burn")
                        && !resaved.contains("Energy"),
                kind + " saves in the current format");
        return imported;
    }
    @GameTest(template = "empty")
    public static void preXmlMachineSavesLoad(GameTestHelper h) {
        var coal = importLegacy(h, "steam_boiler_coal", m -> {
            m.items().setStackInSlot(0, new ItemStack(Items.COAL, 5));
            m.fluids().tank(1).fill(new FluidStack(Fluids.WATER, 777), FluidAction.EXECUTE);
            m.burn = 17;
            m.totalBurn = 1600;
        }, tag -> {});
        var burner = processor(coal, CoalBoilerProcessor.class);
        h.assertTrue(coal.items.getStackInSlot(0).getCount() == 5, "Coal stays in the fuel slot");
        h.assertTrue(coal.fluids.tank(1).getFluidAmount() == 777, "Water stays in tank 1");
        h.assertTrue(burner.burnTime() == 17 && burner.totalBurnTime() == 1600, "Burn timer imported");

        var electric = importLegacy(h, "steam_boiler_electric", m -> m.energy().setStored(1234),
                tag -> {
                    // SA3 before MMDLib 2.0.2 saved the buffer as "Energy".
                    tag.put("Energy", tag.get("energy"));
                    tag.remove("energy");
                });
        h.assertTrue(electric.energy.stored() == 1234, "Legacy Energy key is imported");

        var oil = importLegacy(h, "steam_boiler_oil", m -> {
            m.fluids().tank(2).fill(new FluidStack(Fluids.LAVA, 900), FluidAction.EXECUTE);
            m.burn = 40;
        }, tag -> {});
        h.assertTrue(oil.fluids.tank(2).getFluidAmount() == 900
                        && processor(oil, OilBoilerProcessor.class).burnTime() == 40,
                "Oil fuel tank and burn timer imported");

        var geo = importLegacy(h, "steam_boiler_geothermal", m -> m.temperature = 640, tag -> {});
        h.assertTrue(processor(geo, GeothermalBoilerProcessor.class).temperature() == 640,
                "Geothermal temperature imported");

        var elevator = importLegacy(h, "steam_elevator", m -> {
            m.raised = true;
            m.elevatorHeight = 5;
        }, tag -> {});
        h.assertTrue(processor(elevator, ElevatorProcessor.class).raised(), "Raised state imported");

        var still = importLegacy(h, "steam_still", m -> {
            m.fluids().tank(2).fill(new FluidStack(Fluids.WATER, 300), FluidAction.EXECUTE);
            m.pendingDistillation = new FluidStack(Fluids.LAVA, 100);
            m.distillationDuration = 20;
            m.progress[0] = 7;
        }, tag -> {});
        var batch = processor(still, StillProcessor.class);
        h.assertTrue(still.fluids.tank(2).getFluidAmount() == 300, "Still input stays in tank 2");
        h.assertTrue(batch.pending().getFluid() == Fluids.LAVA && batch.pending().getAmount() == 100
                        && batch.progress() == 7 && batch.duration() == 20,
                "Pending distillation batch imported");

        var pump = importLegacy(h, "steam_pump", m ->
                m.fluids().tank(1).fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE),
                tag -> {});
        h.assertTrue(pump.fluids.tank(1).getFluidAmount() == 2000, "Pumped fluid stays in tank 1");

        var drill = importLegacy(h, "steam_drill", m -> {
            m.items().setStackInSlot(4, new ItemStack(Items.COBBLESTONE, 12));
            m.items().setStackInSlot(8, new ItemStack(Items.FLINT, 2));
            m.depth = 9;
            m.drillPhase = 1;
            m.drillRetractionTicks = 2;
            m.progress[0] = 5;
        }, tag -> {});
        var bore = processor(drill, DrillProcessor.class);
        h.assertTrue(drill.items.getStackInSlot(0).getCount() == 12
                        && drill.items.getStackInSlot(4).is(Items.FLINT),
                "Drill output slots 4-8 move to 0-4");
        h.assertTrue(bore.depth() == 9 && bore.phase() == DrillProcessor.RETRACTING
                        && bore.progress() == 5,
                "Drill depth, phase and progress imported");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void brokenMachineDropsItemsOnce(GameTestHelper h) {
        var crusher = place(h, "steam_crusher", 2, 2, 2);
        crusher.items.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 5));
        crusher.items.setStackInSlot(4, new ItemStack(Items.GRAVEL, 3));
        h.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        var area = new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(2, 2, 2))).inflate(2);
        var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area);
        int cobble = drops.stream().filter(e -> e.getItem().is(Items.COBBLESTONE))
                .mapToInt(e -> e.getItem().getCount()).sum();
        int gravel = drops.stream().filter(e -> e.getItem().is(Items.GRAVEL))
                .mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(cobble == 5 && gravel == 3, "Contents drop exactly once: " + cobble + "/" + gravel);
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void coalBoilerAndRedstone(GameTestHelper h) {
        var boiler = placeXml(h, "steam_boiler_coal", 2, 2, 2);
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
        var boiler = placeXml(h, "steam_boiler_electric", 2, 2, 2);
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
        var pump = placeXml(h, "steam_pump", 2, 4, 2);
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
        var boiler = placeXml(h, "steam_boiler_coal", 2, 2, 2);
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
        var drill = placeXml(h, "steam_drill", 3, 2, 3);
        drill.fluids.fill(steam(5000), FluidAction.EXECUTE);
        drill.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
        h.setBlock(new BlockPos(3, 2, 2), Blocks.BEDROCK);
        h.setBlock(new BlockPos(4, 2, 3), ModContent.block("steam_track"));
        h.runAfterDelay(5, () -> {
            var moved = h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4, 2, 3)));
            h.assertTrue(moved instanceof SteamMachineEntity, "Drill follows track");
            h.assertTrue(((SteamMachineEntity) moved).items.getStackInSlot(0).getCount() == 3,
                    "Drill preserves inventory");
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(3, 2, 3))).inflate(2))
                            .isEmpty(), "Moving drops nothing");
            h.assertTrue(h.getBlockState(new BlockPos(3, 2, 3)).is(ModContent.block("steam_pipe")),
                    "Drill leaves steam pipe");
            h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void drillBreaksBlocksAtDiamondPickaxeSpeed(GameTestHelper h) {
        var level = h.getLevel();
        var p = h.absolutePos(BlockPos.ZERO);
        // SA2 timing at the default 12 ticks per hardness, minimum 6.
        h.assertTrue(DrillProcessor.breakTicks(Blocks.STONE.defaultBlockState(), level, p) == 18,
                "Stone breaks in 18 ticks");
        h.assertTrue(DrillProcessor.breakTicks(Blocks.DIRT.defaultBlockState(), level, p) == 6,
                "Dirt breaks in 6 ticks");
        h.assertTrue(DrillProcessor.breakTicks(Blocks.IRON_ORE.defaultBlockState(), level, p) == 36,
                "Iron ore breaks in 36 ticks");
        h.assertTrue(DrillProcessor.breakTicks(Blocks.OBSIDIAN.defaultBlockState(), level, p) == 600,
                "Obsidian breaks in 600 ticks");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void starvedDrillPausesInsteadOfRetracting(GameTestHelper h) {
        var drill = placeXml(h, "steam_drill", 3, 2, 3);
        // Enough steam for one pipe segment through air, then starved.
        drill.fluids.fill(steam(300), FluidAction.EXECUTE);
        h.runAfterDelay(5, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(3, 2, 2)).is(ModContent.block("drillbit")),
                    "Starved drill keeps its pipe extended");
            h.assertTrue(SteamScreens.drillDepth(drill) == 1, "Starved drill keeps its depth");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void elevatorExtendsAndRetracts(GameTestHelper h) {
        var elevator = placeXml(h, "steam_elevator", 3, 2, 3);
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
    /**
     * A tank or switch saved by SA3 before the XML port (the shared SteamMachine entity, with a
     * nine-slot "Items" inventory and an unnamed steam tank under "Fluids") loads into the XML
     * machine with its steam intact.
     */
    @GameTest(template = "empty")
    public static void preXmlTankAndSwitchSavesLoad(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var crusher = ModContent.block("steam_crusher").defaultBlockState();
        for (String kind : java.util.List.of("steam_tank", "steam_switch")) {
            var placed = placeXml(h, kind, 2, 2, 2);
            var legacy = new SteamMachine(ModContent.machineType(crusher), placed.getBlockPos(),
                    crusher, new MachineSettings(kind, kind.equals("steam_tank") ? 1000000 : 20000,
                                     0, 0, 0, 9, 0, 0));
            legacy.fluids().tank(0).fill(steam(4321), FluidAction.EXECUTE);
            var saved = legacy.saveWithFullMetadata(registries);
            saved.putString("id", ModContent.id(kind).toString());
            var imported = (zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity)
                    net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                            placed.getBlockPos(), placed.getBlockState(), saved, registries);
            h.assertTrue(imported != null && imported.fluids.tank(0).getFluidAmount() == 4321
                                 && imported.fluids.tank(0).getFluid().getFluid()
                                         == zone.moddev.mc.poweradvantage.init.Fluids.STEAM.get(),
                    kind + " keeps its pre-XML steam");
            h.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void valveStopsOutputWhenPowered(GameTestHelper h) {
        var valve = placeXml(h, "steam_switch", 3, 2, 3);
        var tank = placeXml(h, "steam_tank", 3, 2, 2);
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
        var oil = placeXml(h, "steam_boiler_oil", 2, 2, 2);
        oil.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        oil.fluids.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        var geo = placeXml(h, "steam_boiler_geothermal", 6, 2, 2);
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
    @GameTest(template = "empty", timeoutTicks = 140)
    public static void distillationUsesPowerAdvantageDefaultRecipe(GameTestHelper h) {
        // PA 3.2's built-in poweradvantage:distilling_refined_oil: 100 mB crude -> 50 mB refined in 100 ticks.
        var still = placeXml(h, "steam_still", 2, 2, 2);
        var crudeOil = zone.moddev.mc.poweradvantage.init.Fluids.CRUDE_OIL.get();
        var refinedOil = zone.moddev.mc.poweradvantage.init.Fluids.REFINED_OIL.get();
        h.setBlock(new BlockPos(2, 3, 2), Blocks.SNOW);
        still.fluids.tank(0).fill(steam(150), FluidAction.EXECUTE);
        still.fluids.tank(2).fill(new FluidStack(crudeOil, 100), FluidAction.EXECUTE);
        h.assertTrue(still.fluids.tank(2).getFluidAmount() == 100,
                "PA3's default crude-oil input is accepted");
        h.runAfterDelay(1, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(2, 3, 2)).isAir(),
                    "Running still melts snow above it");
            h.assertTrue(still.fluids.tank(2).isEmpty(), "Input is consumed when operation starts");
            h.assertTrue(still.fluids.tank(1).isEmpty(), "Output waits for recipe duration");
            h.assertTrue(zone.moddev.mc.SteamAdvantage.machines.SteamScreens.distillProgress(still) == 1
                            && zone.moddev.mc.SteamAdvantage.machines.SteamScreens.distillDuration(still) == 100,
                    "PA3 recipe duration drives progress");
            h.runAfterDelay(99, () -> {
                h.assertTrue(still.fluids.tank(1).getFluidAmount() == 50
                                && still.fluids.tank(1).getFluid().getFluid() == refinedOil,
                        "PA3's built-in recipe converts crude oil to refined oil");
                h.assertTrue(still.fluids.tank(0).isEmpty(), "Steam is charged once per operation");
                h.assertTrue(SteamScreens.distillProgress(still) == 0, "Completed operation clears progress");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void distillationProgressPersistsUntilOutput(GameTestHelper h) {
        var still = placeXml(h, "steam_still", 2, 2, 2);
        still.fluids.tank(0).fill(steam(150), FluidAction.EXECUTE);
        still.fluids.tank(2).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        h.runAfterDelay(10, () -> {
            h.assertTrue(SteamScreens.distillProgress(still) == 10
                            && SteamScreens.distillDuration(still) == 20,
                    "Timed recipe accumulates progress");
            h.assertTrue(still.fluids.tank(2).isEmpty()
                            && processor(still, StillProcessor.class).pending().getAmount() == 100,
                    "Input and pending output persist during processing");
            var saved = still.saveWithoutMetadata(h.getLevel().registryAccess());
            still.loadWithComponents(saved, h.getLevel().registryAccess());
            h.runAfterDelay(10, () -> {
                h.assertTrue(still.fluids.tank(1).getFluidAmount() == 100
                                && still.fluids.tank(1).getFluid().getFluid() == Fluids.LAVA,
                        "Saved operation completes after the remaining duration");
                h.assertTrue(processor(still, StillProcessor.class).pending().isEmpty(), "Completed output is cleared");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void distillationBlockedOutputDoesNotConsumeResources(GameTestHelper h) {
        var still = placeXml(h, "steam_still", 2, 2, 2);
        still.fluids.tank(0).fill(steam(150), FluidAction.EXECUTE);
        still.fluids.tank(1).fill(new FluidStack(Fluids.LAVA, 4000), FluidAction.EXECUTE);
        still.fluids.tank(2).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        h.runAfterDelay(5, () -> {
            h.assertTrue(still.fluids.tank(0).getFluidAmount() == 150,
                    "Blocked output preserves steam");
            h.assertTrue(still.fluids.tank(2).getFluidAmount() == 1000,
                    "Blocked output preserves input");
            h.assertTrue(processor(still, StillProcessor.class).pending().isEmpty(),
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
        var geo = placeXml(h, "steam_boiler_geothermal", 2, 2, 2);
        geo.fluids.tank(0).fill(steam(geo.fluids.tank(0).getCapacity()), FluidAction.EXECUTE);
        processor(geo, GeothermalBoilerProcessor.class).setTemperature(500);
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
        // MMDLib 2.0.2 also adds armor and offhand slots, so locate hotbar slot 0 directly.
        int hotbar = menu.slots.stream()
                .filter(slot -> slot.container == player.getInventory() && slot.getContainerSlot() == 0)
                .findFirst().orElseThrow().index;
        menu.quickMoveStack(player, hotbar);
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
        var tank = placeXml(h, "steam_tank", 2, 2, 2);
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

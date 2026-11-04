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

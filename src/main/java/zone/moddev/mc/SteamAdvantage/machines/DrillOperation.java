package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import zone.moddev.mc.SteamAdvantage.init.ModContent;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.Transfer;

/** Drill extension, mining and track movement. */
final class DrillOperation {
    private DrillOperation() {}

    /**
     * Maximum pipe length. SA2 used 64, which reached bedrock from sea level when the world
     * bottom was y=0; with the floor now at -64, 128 covers sea level down to bedrock.
     */
    static final int MAX_RANGE = 128;

    static void drill(SteamMachine machine) {
        var level = machine.getLevel();
        var pos = machine.getBlockPos();
        Direction direction = machine.getBlockState().getValue(BlockStateProperties.FACING);
        // Empty the output every tick, so a full inventory can drain and mining resume.
        Transfer.pushItems(
                level, pos, direction.getOpposite(), machine.items.view(IoMode.OUTPUT), 64);
        if (machine.drillPhase == 1) {
            retract(machine, direction);
            return;
        }
        if (machine.drillPhase == 2) {
            followTrack(machine);
            return;
        }
        if (machine.depth >= MAX_RANGE) {
            beginRetraction(machine);
            return;
        }
        BlockPos target = pos.relative(direction, machine.depth + 1);
        if (!level.isInWorldBounds(target)) {
            // Reached the top or bottom of the world: treat it like bedrock.
            beginRetraction(machine);
            return;
        }
        if (!level.hasChunkAt(target))
            return;
        BlockState state = level.getBlockState(target);
        if (state.is(ModContent.block("drillbit"))) {
            machine.depth++;
            return;
        }
        if (state.getDestroySpeed(level, target) < 0 || state.hasBlockEntity()) {
            beginRetraction(machine);
            return;
        }
        // Air, fluids, plants etc. are simply displaced by the pipe rather than mined.
        boolean replace = state.canBeReplaced();
        // Steam starvation pauses the drill rather than retracting its existing line.
        if (!machine.hasSteam(replace ? 250 : 100))
            return;
        if (!replace) {
            var drops = miningDrops(state, (net.minecraft.server.level.ServerLevel) level, target);
            // Simulate all drops together before altering the world.
            var test = new net.neoforged.neoforge.items.ItemStackHandler(5);
            for (int i = 0; i < 5; i++)
                test.setStackInSlot(i, machine.items.getStackInSlot(i + 4).copy());
            for (ItemStack drop : drops) {
                ItemStack remaining = drop.copy();
                for (int i = 0; i < 5; i++) remaining = test.insertItem(i, remaining, false);
                if (!remaining.isEmpty())
                    return;
            }
            // A blocked output must pause before mining consumes steam or progress.
            int ticks = breakTicks(state, level, target);
            if (machine.progress[0] < ticks) {
                machine.spendSteam(100);
                if (++machine.progress[0] < ticks)
                    return;
            }
            if (!level.destroyBlock(target, false))
                return;
            machine.progress[0] = 0;
            for (int i = 0; i < 5; i++) machine.items.setStackInSlot(i + 4, test.getStackInSlot(i));
        } else
            machine.spendSteam(250);
        level.setBlockAndUpdate(target,
                ModContent.block("drillbit")
                        .defaultBlockState()
                        .setValue(BlockStateProperties.FACING, direction));
        machine.depth++;
    }

    private static java.util.List<ItemStack> miningDrops(BlockState state,
            net.minecraft.server.level.ServerLevel level, BlockPos target) {
        // SA2 crushed the block itself, rather than each item in its loot table.
        var input = new SingleRecipeInput(new ItemStack(state.getBlock()));
        if (!input.item().isEmpty()) {
            var recipe = level.getRecipeManager().getRecipeFor(CrushingRecipe.TYPE.get(), input, level);
            if (recipe.isPresent())
                return java.util.List.of(recipe.get().value().assemble(input, level.registryAccess()));
        }
        return Block.getDrops(state, level, target, null, null, tool());
    }

    private static void beginRetraction(SteamMachine machine) {
        machine.drillPhase = 1;
        machine.drillRetractionTicks = 0;
        machine.progress[0] = 0;
        machine.getLevel().playSound(null, machine.getBlockPos(),
                net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,
                net.minecraft.sounds.SoundSource.BLOCKS, .8f, .7f);
        machine.setChanged();
    }

    private static void retract(SteamMachine machine, Direction direction) {
        var level = machine.getLevel();
        if (machine.depth > 0) {
            BlockPos tip = machine.getBlockPos().relative(direction, machine.depth);
            // Never load chunks or lose our place while a tip is unavailable.
            if (!level.hasChunkAt(tip)) return;
            if (++machine.drillRetractionTicks < 4) return;
            machine.drillRetractionTicks = 0;
            if (level.getBlockState(tip).is(ModContent.block("drillbit")))
                zone.moddev.mc.SteamAdvantage.blocks.DrillBitBlock.retractSegment(level, tip);
            machine.depth--;
            var pos = machine.getBlockPos();
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.PISTON_CONTRACT,
                    net.minecraft.sounds.SoundSource.BLOCKS, .5f, 1.2f);
            ((net.minecraft.server.level.ServerLevel) level).sendParticles(
                    net.minecraft.core.particles.ParticleTypes.CLOUD,
                    pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 3, .2, .1, .2, .01);
        }
        if (machine.depth == 0) {
            machine.drillPhase = 2;
            level.playSound(null, machine.getBlockPos(),
                    net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS, .8f, 1);
            followTrack(machine);
        }
        machine.setChanged();
    }

    /** The drill mines as an unenchanted diamond pickaxe. */
    private static ItemStack tool() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }

    /**
     * Ticks to break a block, using vanilla's player formula for the drill's tool: tool speed
     * over hardness, divided by 30 when the tool can harvest the block or 100 when it cannot.
     */
    static int breakTicks(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0)
            return 1;
        ItemStack tool = tool();
        boolean harvestable =
                !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        float perTick = tool.getDestroySpeed(state) / hardness / (harvestable ? 30f : 100f);
        return Math.max(1, net.minecraft.util.Mth.ceil(1f / perTick));
    }

    static void clearDrill(SteamMachine machine) {
        var level = machine.getLevel();
        var pos = machine.getBlockPos();
        Direction direction = machine.getBlockState().getValue(BlockStateProperties.FACING);
        for (int i = 1; i <= MAX_RANGE; i++) {
            BlockPos p = pos.relative(direction, i);
            if (!level.hasChunkAt(p) || !level.getBlockState(p).is(ModContent.block("drillbit")))
                break;
            level.removeBlock(p, false);
        }
        machine.depth = 0;
        machine.progress[0] = 0;
        machine.drillPhase = 0;
        machine.drillRetractionTicks = 0;
    }

    static void followTrack(SteamMachine machine) {
        var level = machine.getLevel();
        var pos = machine.getBlockPos();
        if (!machine.hasSteam(1000))
            return;
        for (Direction d : Direction.values()) {
            BlockPos target = pos.relative(d);
            if (!level.hasChunkAt(target)
                    || !level.getBlockState(target).is(ModContent.block("steam_track")))
                continue;
            machine.spendSteam(1000);
            clearDrill(machine);
            CompoundTag saved = machine.saveWithoutMetadata(level.registryAccess());
            BlockState state = machine.getBlockState();
            for (int i = 0; i < machine.items.getSlots(); i++)
                machine.items.setStackInSlot(i, ItemStack.EMPTY);
            level.setBlockAndUpdate(target, state);
            if (level.getBlockEntity(target) instanceof SteamMachine moved) {
                moved.loadWithComponents(saved, level.registryAccess());
                moved.setChanged();
            }
            level.setBlockAndUpdate(pos, ModContent.block("steam_pipe").defaultBlockState());
            return;
        }
    }
}

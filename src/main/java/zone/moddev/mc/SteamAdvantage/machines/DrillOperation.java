package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        if (machine.depth >= MAX_RANGE) {
            followTrack(machine);
            return;
        }
        Direction direction = machine.getBlockState().getValue(BlockStateProperties.FACING);
        // Empty the output every tick, so a full inventory can drain and mining resume.
        Transfer.pushItems(
                level, pos, direction.getOpposite(), machine.items.view(IoMode.OUTPUT), 64);
        BlockPos target = pos.relative(direction, machine.depth + 1);
        if (!level.isInWorldBounds(target)) {
            // Reached the top or bottom of the world: treat it like bedrock.
            followTrack(machine);
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
            followTrack(machine);
            return;
        }
        // Air, fluids, plants etc. are simply displaced by the pipe rather than mined.
        boolean replace = state.canBeReplaced();
        // Running short of steam pauses the drill; only redstone retracts it. Retracting
        // here made a starved drill extend one segment and pull it back every tick.
        if (!machine.hasSteam(replace ? 250 : 100))
            return;
        if (!replace) {
            int ticks = breakTicks(state, level, target);
            if (machine.progress[0] < ticks) {
                machine.spendSteam(100);
                if (++machine.progress[0] < ticks)
                    return;
            }
            // Fully cut: hold here (without spending steam) until the drops fit.
            var drops = Block.getDrops(state, (net.minecraft.server.level.ServerLevel) level,
                    target, null, null, tool());
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

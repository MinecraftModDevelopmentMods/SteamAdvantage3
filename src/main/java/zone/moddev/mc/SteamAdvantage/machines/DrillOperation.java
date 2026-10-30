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

    static void drill(SteamMachine machine) {
        var level = machine.getLevel();
        var pos = machine.getBlockPos();
        if (machine.depth >= 64) {
            followTrack(machine);
            return;
        }
        if (!machine.hasSteam(100)) {
            clearDrill(machine);
            return;
        }
        Direction direction = machine.getBlockState().getValue(BlockStateProperties.FACING);
        BlockPos target = pos.relative(direction, machine.depth + 1);
        if (!level.hasChunkAt(target) || !level.isInWorldBounds(target))
            return;
        BlockState state = level.getBlockState(target);
        if (state.getDestroySpeed(level, target) < 0 || state.hasBlockEntity()) {
            followTrack(machine);
            return;
        }
        if (state.is(ModContent.block("drillbit"))) {
            machine.depth++;
            return;
        }
        if (!state.isAir()) {
            machine.spendSteam(100);
            if (++machine.progress[0] < Math.max(1, state.getDestroySpeed(level, target) * 12))
                return;
            machine.progress[0] = 0;
        } else if (!machine.hasSteam(250))
            return;
        if (!state.isAir()) {
            var drops = Block.getDrops(state, (net.minecraft.server.level.ServerLevel) level,
                    target, null, null, new ItemStack(Items.DIAMOND_PICKAXE));
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
            for (int i = 0; i < 5; i++) machine.items.setStackInSlot(i + 4, test.getStackInSlot(i));
        }
        if (state.isAir())
            machine.spendSteam(250);
        level.setBlockAndUpdate(target,
                ModContent.block("drillbit")
                        .defaultBlockState()
                        .setValue(BlockStateProperties.FACING, direction));
        machine.depth++;
        Transfer.pushItems(
                level, pos, direction.getOpposite(), machine.items.view(IoMode.OUTPUT), 64);
    }

    static void clearDrill(SteamMachine machine) {
        var level = machine.getLevel();
        var pos = machine.getBlockPos();
        Direction direction = machine.getBlockState().getValue(BlockStateProperties.FACING);
        for (int i = 1; i <= 64; i++) {
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

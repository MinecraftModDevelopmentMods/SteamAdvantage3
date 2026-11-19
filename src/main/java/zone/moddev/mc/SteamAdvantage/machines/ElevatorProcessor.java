package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import zone.moddev.mc.SteamAdvantage.blocks.PlatformBlock;
import zone.moddev.mc.SteamAdvantage.init.ModContent;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;

/**
 * Steam elevator: a redstone signal raises a platform column (up to 16 blocks, stopping two
 * short of an obstruction) and lifts what stands on it; losing the signal lowers it. Each move
 * costs 3200 mB of steam. Unlike other machines, redstone drives it rather than pausing it.
 */
public final class ElevatorProcessor extends SteamProcessor {
    private final ProcessorState.Key<Boolean> raised =
            variables.define("raised", com.mojang.serialization.Codec.BOOL, false);
    private final ProcessorState.Key<Integer> height = intVariable("height");

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) {
        return true;
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        move(powered());
        return ProcessState.IDLE;
    }

    private void move(boolean signal) {
        var level = machine.level();
        var pos = machine.position();
        if (signal == variables.get(raised) || !hasSteam(3200))
            return;
        Block platform = ModContent.block("platform");
        int columns = variables.get(height);
        if (signal) {
            int top = 0;
            for (int i = 1; i <= 16; i++) {
                BlockPos p = pos.above(i);
                if (!level.hasChunkAt(p) || !level.isEmptyBlock(p))
                    break;
                top = i;
            }
            top -= 2;
            if (top < 1)
                return;
            columns = top;
            for (int i = 1; i <= columns; i++)
                level.setBlockAndUpdate(pos.above(i), platform.defaultBlockState()
                        .setValue(PlatformBlock.HEIGHT, i == columns ? 4 : 0));
            for (var entity :
                    level.getEntities(null, new AABB(pos.above()).expandTowards(0, columns, 0))) {
                entity.teleportTo(entity.getX(), pos.getY() + columns + 1.01, entity.getZ());
                entity.fallDistance = 0;
            }
        } else {
            for (int i = 1; i <= columns; i++) {
                BlockPos p = pos.above(i);
                if (!level.hasChunkAt(p) || !level.getBlockState(p).is(platform))
                    return;
            }
            for (int i = 1; i <= columns; i++)
                level.removeBlock(pos.above(i), false);
            for (var entity : level.getEntities(null,
                         new AABB(pos.above(columns + 1)).expandTowards(0, 2, 0))) {
                entity.teleportTo(entity.getX(), pos.getY() + 1.01, entity.getZ());
                entity.fallDistance = 0;
            }
        }
        variables.set(height, columns);
        spendSteam(3200);
        variables.set(raised, signal);
    }

    @Override
    void importLegacy(CompoundTag old, CompoundTag state) {
        state.putBoolean("raised", old.getBoolean("Raised"));
        state.putInt("height", old.getInt("ElevatorHeight"));
    }

    @Override
    protected int comparatorSignal() {
        return variables.get(raised) ? 15 : 0;
    }

    public boolean raised() {
        return variables.get(raised);
    }

    /** Test and tooling access; outside a processor tick. */
    public void setRaised(boolean value) {
        variables.update(() -> variables.set(raised, value));
    }
}

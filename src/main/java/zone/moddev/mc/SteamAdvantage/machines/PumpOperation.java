package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import zone.moddev.mc.SteamAdvantage.init.ModContent;

/** Intake-shaft extension and bounded world-fluid collection. */
final class PumpOperation {
    private PumpOperation() {}

    static void pump(SteamMachine machine) {
        var level = machine.getLevel();
        var pos = machine.getBlockPos();
        if (!machine.hasSteam(500))
            return;
        Block pipe = ModContent.block("pump_pipe_steam");
        BlockPos end = pos.below();
        for (int i = 0; i < 64; i++, end = end.below()) {
            if (!level.hasChunkAt(end) || !level.isInWorldBounds(end))
                return;
            BlockState s = level.getBlockState(end);
            if (s.is(pipe))
                continue;
            if (s.isAir()) {
                boolean fluidNearby = false;
                for (Direction d : Direction.values())
                    if (level.hasChunkAt(end.relative(d))
                            && !level.getFluidState(end.relative(d)).isEmpty())
                        fluidNearby = true;
                if (fluidNearby)
                    break;
                if (level.getGameTime() % 11 == 0) {
                    level.setBlockAndUpdate(end, pipe.defaultBlockState());
                    machine.spendSteam(500);
                }
                return;
            }
            break;
        }
        if (level.getGameTime() % 32 != 0 || !machine.hasSteam(3200)
                || machine.fluids.tank(machine.product).getSpace() < 1000)
            return;
        var queue = new java.util.ArrayDeque<BlockPos>();
        var seen = new java.util.HashSet<BlockPos>();
        queue.add(end);
        for (Direction d : Direction.values()) queue.add(end.relative(d));
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 125) {
            BlockPos p = queue.removeFirst();
            if (!seen.add(p) || !level.hasChunkAt(p) || p.distManhattan(end) > 32)
                continue;
            BlockState state = level.getBlockState(p);
            if (state.getFluidState().isEmpty())
                continue;
            if (state.getBlock() instanceof BucketPickup pickup) {
                FluidStack candidate = new FluidStack(state.getFluidState().getType(), 1000);
                if (machine.fluids.tank(machine.product).fill(candidate, FluidAction.SIMULATE)
                        == 1000) {
                    ItemStack bucket = pickup.pickupBlock(null, level, p, state);
                    if (!bucket.isEmpty()) {
                        machine.fluids.tank(machine.product).fill(candidate, FluidAction.EXECUTE);
                        machine.spendSteam(3200);
                        return;
                    }
                }
            }
            for (Direction d : Direction.values()) queue.addLast(p.relative(d));
        }
    }
}

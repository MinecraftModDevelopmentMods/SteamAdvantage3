package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import zone.moddev.mc.SteamAdvantage.blocks.PlatformBlock;
import zone.moddev.mc.SteamAdvantage.init.ModContent;

/** Platform and passenger movement. */
final class ElevatorOperation {
    private ElevatorOperation() {}

    static void elevator(SteamMachine machine, boolean signal) {
        var level = machine.getLevel();
        var pos = machine.getBlockPos();
        if (signal == machine.raised || !machine.hasSteam(3200))
            return;
        Block platform = ModContent.block("platform");
        if (signal) {
            int height = 0;
            for (int i = 1; i <= 16; i++) {
                BlockPos p = pos.above(i);
                if (!level.hasChunkAt(p) || !level.isEmptyBlock(p))
                    break;
                height = i;
            }
            height -= 2;
            if (height < 1)
                return;
            machine.elevatorHeight = height;
            for (int i = 1; i <= height; i++)
                level.setBlockAndUpdate(pos.above(i),
                        platform.defaultBlockState().setValue(
                                PlatformBlock.HEIGHT, i == height ? 4 : 0));
            for (var entity :
                    level.getEntities(null, new AABB(pos.above()).expandTowards(0, height, 0))) {
                entity.teleportTo(entity.getX(), pos.getY() + height + 1.01, entity.getZ());
                entity.fallDistance = 0;
            }
        } else {
            for (int i = 1; i <= machine.elevatorHeight; i++) {
                BlockPos p = pos.above(i);
                if (!level.hasChunkAt(p) || !level.getBlockState(p).is(platform))
                    return;
            }
            for (int i = 1; i <= machine.elevatorHeight; i++)
                level.removeBlock(pos.above(i), false);
            for (var entity : level.getEntities(null,
                         new AABB(pos.above(machine.elevatorHeight + 1)).expandTowards(0, 2, 0))) {
                entity.teleportTo(entity.getX(), pos.getY() + 1.01, entity.getZ());
                entity.fallDistance = 0;
            }
        }
        machine.spendSteam(3200);
        machine.raised = signal;
    }
}

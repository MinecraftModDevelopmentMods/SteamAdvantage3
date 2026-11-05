package zone.moddev.mc.SteamAdvantage.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Render anchor only. Facing belongs to the block state; animation uses client world time. */
public final class DrillBitBlockEntity extends BlockEntity {
    public DrillBitBlockEntity(BlockEntityType<DrillBitBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}

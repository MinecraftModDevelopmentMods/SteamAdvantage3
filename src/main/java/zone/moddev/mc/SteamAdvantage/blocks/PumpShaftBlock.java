package zone.moddev.mc.SteamAdvantage.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Pump-created vertical intake shaft, distinct from PA3's network extraction terminal. */
public final class PumpShaftBlock extends Block {
    public PumpShaftBlock(Properties properties) {
        super(properties.noOcclusion());
    }
    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(4, 0, 4, 12, 16, 12);
    }
    @Override
    protected void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && !next.is(this)) {
            // Remove bottom-up so each callback sees no remaining shaft below it.
            int length = 0;
            while (length < 64 && level.hasChunkAt(pos.below(length + 1))
                    && level.getBlockState(pos.below(length + 1)).is(this))
                length++;
            for (int i = length; i > 0; i--) level.removeBlock(pos.below(i), false);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}

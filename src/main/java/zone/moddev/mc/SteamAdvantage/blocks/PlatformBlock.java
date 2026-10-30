package zone.moddev.mc.SteamAdvantage.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PlatformBlock extends Block {
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 0, 4);
    public PlatformBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(HEIGHT, 4));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(HEIGHT);
    }
    @Override
    protected VoxelShape getShape(
            BlockState s, BlockGetter level, BlockPos p, CollisionContext context) {
        int h = s.getValue(HEIGHT);
        return h == 0 ? box(4, 0, 4, 12, 16, 12) : box(0, 0, 0, 16, h * 4, 16);
    }
}

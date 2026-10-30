package zone.moddev.mc.SteamAdvantage.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DrillBitBlock extends Block {
    public DrillBitBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(
                stateDefinition.any().setValue(BlockStateProperties.FACING, Direction.NORTH));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(BlockStateProperties.FACING);
    }
    @Override
    protected VoxelShape getShape(
            BlockState s, BlockGetter level, BlockPos p, CollisionContext context) {
        return switch (s.getValue(BlockStateProperties.FACING).getAxis()) {
            case X -> box(0, 5, 5, 16, 11, 11);
            case Y -> box(5, 0, 5, 11, 16, 11);
            case Z -> box(5, 5, 0, 11, 11, 16);
        };
    }
    @Override
    protected void entityInside(BlockState s, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide)
            entity.hurt(
                    new net.minecraft.world.damagesource.DamageSource(level.registryAccess()
                                    .registryOrThrow(
                                            net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                                    .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                                            net.minecraft.resources.ResourceLocation.parse(
                                                    "steamadvantage:steam_machine")))),
                    2);
    }
}

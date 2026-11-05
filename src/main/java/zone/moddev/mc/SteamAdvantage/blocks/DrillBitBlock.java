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
import zone.moddev.mc.SteamAdvantage.init.ModContent;
import zone.moddev.mc.SteamAdvantage.machines.SteamMachine;

public final class DrillBitBlock extends zone.moddev.mc.lib.common.runtime.MaterialEntityBlock<DrillBitBlockEntity> {
    // Removing a segment invokes onRemove again; keep long lines off the call stack.
    private static final ThreadLocal<Boolean> CLEARING = ThreadLocal.withInitial(() -> false);

    /** Controlled retraction must not trigger the broken-line cleanup. */
    public static void retractSegment(Level level, BlockPos pos) {
        boolean previous = CLEARING.get();
        CLEARING.set(true);
        try {
            level.removeBlock(pos, false);
        } finally {
            if (previous) CLEARING.set(true);
            else CLEARING.remove();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
            BlockState next, boolean moving) {
        if (!level.isClientSide && !state.is(next.getBlock()) && !CLEARING.get()) {
            CLEARING.set(true);
            try {
                Direction facing = state.getValue(BlockStateProperties.FACING);
                for (Direction direction : new Direction[] {facing, facing.getOpposite()}) {
                    BlockPos cursor = pos.relative(direction);
                    while (level.hasChunkAt(cursor) && level.getBlockState(cursor).is(this)) {
                        level.removeBlock(cursor, false);
                        cursor = cursor.relative(direction);
                    }
                    if (level.hasChunkAt(cursor)
                            && level.getBlockState(cursor).is(
                                    ModContent.block("steam_drill"))
                            && level.getBlockState(cursor).getValue(BlockStateProperties.FACING)
                                    == direction.getOpposite()
                            && level.getBlockEntity(cursor) instanceof SteamMachine drill) {
                        drill.clearDrill();
                        drill.setChanged();
                    }
                }
            } finally {
                CLEARING.remove();
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    public DrillBitBlock(Properties properties,
            zone.moddev.mc.lib.api.BlockEntityBinding<DrillBitBlockEntity> binding) {
        super(properties.noOcclusion(), binding.prepared(), binding.type());
        registerDefaultState(
                stateDefinition.any().setValue(BlockStateProperties.FACING, Direction.NORTH));
    }
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(BlockStateProperties.FACING);
    }
    @Override
    protected VoxelShape getShape(
            BlockState s, BlockGetter level, BlockPos p, CollisionContext context) {
        return switch (s.getValue(BlockStateProperties.FACING).getAxis()) {
            case X -> box(0, 4, 4, 16, 12, 12);
            case Y -> box(4, 0, 4, 12, 16, 12);
            case Z -> box(4, 4, 0, 12, 12, 16);
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

package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class MachineBlock
        extends zone.moddev.mc.lib.common.runtime.MaterialEntityBlock<SteamMachine> {
    private final MachineSettings settings;
    public MachineBlock(Properties properties,
            zone.moddev.mc.lib.api.BlockEntityBinding<SteamMachine> binding) {
        super(properties, binding.prepared(), binding.type());
        settings = binding.prepared()
                           .definition()
                           .factoryData()
                           .decode(MachineSettings.CODEC)
                           .getOrThrow();
        registerDefaultState(
                stateDefinition.any().setValue(BlockStateProperties.FACING, Direction.NORTH));
    }
    public MachineSettings settings() {
        return settings;
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(BlockStateProperties.FACING);
    }
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(BlockStateProperties.FACING,
                settings.kind().equals("steam_drill") ? c.getNearestLookingDirection().getOpposite()
                                                      : c.getHorizontalDirection().getOpposite());
    }
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
    @Override
    protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (l.getBlockEntity(p) instanceof SteamMachine machine) {
            if (machine.kind.equals("steam_drill") && (!s.is(next.getBlock())
                    || s.getValue(BlockStateProperties.FACING)
                            != next.getValue(BlockStateProperties.FACING)))
                machine.clearDrill();
            if (!s.is(next.getBlock()))
                machine.items.dropAll(l, p);
        }
        super.onRemove(s, l, p, next, moving);
    }
    @Override
    protected boolean hasAnalogOutputSignal(BlockState s) {
        return true;
    }
    @Override
    protected int getAnalogOutputSignal(BlockState s, Level l, BlockPos p) {
        return l.getBlockEntity(p) instanceof SteamMachine m
                ? m.comparatorSignal()
                : 0;
    }
}

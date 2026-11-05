package zone.moddev.mc.SteamAdvantage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.data.ModelData;
import zone.moddev.mc.SteamAdvantage.blocks.DrillBitBlockEntity;

/** All segments share a world-time phase, matching SA2's one revolution per second. */
public final class DrillBitRenderer implements BlockEntityRenderer<DrillBitBlockEntity> {
    private final BlockRenderDispatcher blocks;

    public DrillBitRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(DrillBitBlockEntity bit, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay) {
        if (bit.getLevel() == null) return;
        var state = bit.getBlockState();
        var facing = state.getValue(BlockStateProperties.FACING);
        float angle = ((bit.getLevel().getGameTime() % 20) + partialTick) * 18f;
        poses.pushPose();
        try {
            poses.translate(.5, .5, .5);
            // The baked state already supplies orientation. Spin about its world-space axis.
            poses.mulPose(switch (facing.getAxis()) {
                case X -> Axis.XP.rotationDegrees(angle);
                case Y -> Axis.YP.rotationDegrees(angle);
                case Z -> Axis.ZP.rotationDegrees(angle);
            });
            poses.translate(-.5, -.5, -.5);
            blocks.getModelRenderer().renderModel(poses.last(), buffers.getBuffer(RenderType.cutout()),
                    state, blocks.getBlockModel(state), 1, 1, 1, light, overlay,
                    ModelData.EMPTY, RenderType.cutout());
        } finally {
            poses.popPose();
        }
    }
}

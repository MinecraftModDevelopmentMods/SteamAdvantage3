package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.FluidUtil;
import zone.moddev.mc.lib.api.MaterialEventContexts.BlockUse;
import zone.moddev.mc.lib.common.runtime.BlockEvents;

/** Declared block callback: fluid containers take priority, MMDLib owns XML menu opening. */
public final class MachineInteractions {
    private MachineInteractions() {}
    public static void use(Player player, BlockUse use) {
        if (FluidUtil.getFluidHandler(use.stack()).isPresent()) {
            if (!use.level().isClientSide
                    && use.level().getBlockEntity(use.position()) instanceof SteamMachine machine)
                FluidUtil.interactWithFluidHandler(player, use.hand(), machine.fluids());
            use.consume();
            return;
        }
        BlockEvents.openMenu(player, use);
    }
}

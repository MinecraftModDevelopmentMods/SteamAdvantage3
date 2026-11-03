package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import zone.moddev.mc.SteamAdvantage.config.SteamConfig;

/** Shared boiler conversion and heat logic. */
final class BoilerOperation {
    private BoilerOperation() {}

    static boolean boil(SteamMachine machine) {
        // Geothermal heat comes from the surroundings, so it stays hot even when it can't boil.
        boolean geothermalHot =
                machine.kind.equals("steam_boiler_geothermal") && geothermalHeat(machine);
        if (machine.fluids.tank(machine.water).isEmpty()
                || machine.fluids.tank(machine.steam).getSpace() < 100)
            return geothermalHot;
        boolean heat = switch (machine.kind) {
            case "steam_boiler_electric" -> machine.energy.consume(16);
            case "steam_boiler_geothermal" -> geothermalHot;
            case "steam_boiler_oil" -> {
                if (machine.burn <= 0
                        && machine.fluids.tank(machine.fuel).getFluidAmount() >= 100) {
                    machine.fluids.tank(machine.fuel).drain(100, FluidAction.EXECUTE);
                    machine.burn = machine.totalBurn = SteamConfig.OIL_BURN_TICKS.get();
                }
                yield machine.burn-- > 0;
            }
            default -> machine.burnFuel();
        };
        if (heat) {
            machine.fluids.tank(machine.water).drain(1, FluidAction.EXECUTE);
            machine.fluids.tank(machine.steam)
                    .fill(new FluidStack(zone.moddev.mc.poweradvantage.init.Fluids.STEAM.get(),
                                  machine.kind.equals("steam_boiler_electric") ? 50 : 100),
                            FluidAction.EXECUTE);
        }
        return heat;
    }

    private static boolean geothermalHeat(SteamMachine machine) {
        var level = machine.getLevel();
        var pos = machine.getBlockPos();
        int hot = 0;
        for (Direction d : Direction.values()) {
            BlockPos p = pos.relative(d);
            if (level.hasChunkAt(p)
                    && (level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA)
                            || level.getBlockState(p).is(Blocks.MAGMA_BLOCK)))
                hot++;
        }
        machine.temperature = Math.min(2000, Math.max(0, machine.temperature + hot * 4 - 1));
        return machine.temperature >= 100;
    }
}

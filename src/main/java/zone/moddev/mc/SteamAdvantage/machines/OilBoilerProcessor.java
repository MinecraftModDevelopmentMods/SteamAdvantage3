package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import zone.moddev.mc.SteamAdvantage.config.SteamConfig;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.StorageAdmission;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;

/**
 * Oil boiler: burns 100 mB of fuel for {@code oilBurnTicks} ticks. Its fuel tank (2) accepts
 * the fluid tag named in the common config.
 */
public final class OilBoilerProcessor extends BoilerProcessor implements StorageAdmission, Burner {
    private static final int FUEL = 2;
    private final zone.moddev.mc.lib.machine.feature.ProcessorState.Key<Integer> burn =
            intVariable("burn"), totalBurn = intVariable("totalBurn");

    @Override
    protected boolean heat(boolean ambient) {
        if (variables.get(burn) <= 0 && tank(FUEL).getFluidAmount() >= 100) {
            tank(FUEL).drain(100, FluidAction.EXECUTE);
            int ticks = SteamConfig.OIL_BURN_TICKS.get();
            variables.set(burn, ticks);
            variables.set(totalBurn, ticks);
        }
        int left = variables.get(burn);
        variables.set(burn, left - 1);
        return left > 0;
    }

    @Override
    public boolean acceptsFluid(ProcessorMachine host, String member, FluidAmount fluid) {
        return fluid.fluid().is(SteamConfig.fuelTag());
    }

    @Override
    void importLegacy(CompoundTag old, CompoundTag state) {
        state.putInt("burn", old.getInt("Burn"));
        state.putInt("totalBurn", old.getInt("TotalBurn"));
    }

    @Override
    protected int comparatorSignal() {
        return tankSignal(FUEL);
    }

    public int burnTime() {
        return variables.get(burn);
    }

    public int totalBurnTime() {
        return variables.get(totalBurn);
    }
}

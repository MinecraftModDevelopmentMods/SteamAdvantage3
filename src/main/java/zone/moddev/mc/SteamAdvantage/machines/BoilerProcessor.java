package zone.moddev.mc.SteamAdvantage.machines;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;

/**
 * Shared boiler behavior: while unpowered, each tick a heated boiler turns 1 mB of water into
 * steam and pushes up to 100 mB of steam out of every face. Tanks: steam (0), water (1).
 */
public abstract class BoilerProcessor extends SteamProcessor {
    protected static final int WATER = 1;

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) {
        return !powered();
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        boolean running = false;
        for (int step = 0; step < ticks; step++) {
            if (powered())
                return ProcessState.IDLE;
            running |= boil();
            pushAllSides(STEAM, 100);
        }
        if (running)
            meltSnow();
        return running ? ProcessState.RUNNING : ProcessState.IDLE;
    }

    private boolean boil() {
        // Surrounding heat (geothermal) is measured even when the boiler cannot boil.
        boolean ambient = ambientHeat();
        if (tank(WATER).isEmpty() || tank(STEAM).getSpace() < 100)
            return ambient;
        boolean heat = heat(ambient);
        if (heat) {
            tank(WATER).drain(1, FluidAction.EXECUTE);
            tank(STEAM).fill(new FluidStack(
                    zone.moddev.mc.poweradvantage.init.Fluids.STEAM.get(), steamPerTick()),
                    FluidAction.EXECUTE);
        }
        return heat;
    }

    /** Heat from the surroundings, updated every unpowered tick. */
    protected boolean ambientHeat() {
        return false;
    }

    /** Spends this tick's fuel or energy; true when the boiler is heated. */
    protected abstract boolean heat(boolean ambient);

    protected int steamPerTick() {
        return 100;
    }
}

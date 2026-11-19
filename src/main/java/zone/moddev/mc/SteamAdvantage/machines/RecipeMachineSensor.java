package zone.moddev.mc.SteamAdvantage.machines;

import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;

/**
 * Companion processor for the recipe-driven crusher and furnace. MMDLib's recipe lanes do the
 * work; this supplies SA2's comparator reading (input fullness) and lets a working furnace melt
 * snow above it. It does no processing of its own.
 */
public final class RecipeMachineSensor extends SteamProcessor {
    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) {
        return true;
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        if (melts() && entity().features.processors().stream().anyMatch(lane -> lane.processing()))
            meltSnow();
        return ProcessState.IDLE;
    }

    private boolean melts() {
        return entity().definition.id().getPath().equals("steam_furnace");
    }

    @Override
    protected int comparatorSignal() {
        // Crusher: its input slot. Furnace: its three lane inputs.
        return inventorySignal(0, melts() ? 3 : 1);
    }
}

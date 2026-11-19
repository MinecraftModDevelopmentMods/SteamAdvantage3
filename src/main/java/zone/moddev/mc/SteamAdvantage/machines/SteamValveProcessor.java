package zone.moddev.mc.SteamAdvantage.machines;

import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;

/** Steam switch (valve): passes up to 1000 mB of steam out of its front each tick unless powered. */
public final class SteamValveProcessor extends SteamProcessor {
    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) {
        return !tank(STEAM).isEmpty() && !powered();
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        if (tank(STEAM).isEmpty() || powered())
            return ProcessState.IDLE;
        int moved = 0;
        for (int step = 0; step < ticks; step++)
            moved += Transfer.pushFluids(host.level(), host.position(), front(), tank(STEAM), 1000);
        return moved > 0 ? ProcessState.RUNNING : ProcessState.IDLE;
    }

    @Override
    protected int comparatorSignal() {
        return tankSignal(STEAM);
    }
}

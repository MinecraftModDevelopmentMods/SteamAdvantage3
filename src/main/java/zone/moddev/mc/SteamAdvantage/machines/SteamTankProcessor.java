package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.Direction;
import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;

/** Steam tank: pushes up to 100 mB of steam out of every face each tick while unpowered. */
public final class SteamTankProcessor extends SteamProcessor {
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
            for (Direction side : Direction.values())
                moved += Transfer.pushFluids(host.level(), host.position(), side, tank(STEAM), 100);
        return moved > 0 ? ProcessState.RUNNING : ProcessState.IDLE;
    }

    @Override
    protected int comparatorSignal() {
        return tankSignal(STEAM);
    }
}

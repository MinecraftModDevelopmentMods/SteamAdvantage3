package zone.moddev.mc.SteamAdvantage.machines;

/** Electric boiler: 16 FE makes 50 mB of steam. */
public final class ElectricBoilerProcessor extends BoilerProcessor {
    @Override
    protected boolean heat(boolean ambient) {
        return entity().energy.consume(16);
    }

    @Override
    protected int steamPerTick() {
        return 50;
    }

    @Override
    protected int comparatorSignal() {
        var energy = entity().energy;
        return proportionalSignal(energy.stored(), energy.capacity());
    }
}

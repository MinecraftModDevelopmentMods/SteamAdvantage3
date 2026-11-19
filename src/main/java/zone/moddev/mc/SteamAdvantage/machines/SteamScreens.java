package zone.moddev.mc.SteamAdvantage.machines;

import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/**
 * Screen values for SteamAdvantage's XML machines. MMDLib binds an XML machine's screen to
 * {@link DefinedMachineEntity}, so machine XML names these as
 * {@code zone.moddev.mc.SteamAdvantage.machines.SteamScreens#burnTime} and so on; each reads
 * the machine's processor.
 */
public final class SteamScreens {
    private SteamScreens() {}

    private static <T> java.util.Optional<T> processor(DefinedMachineEntity machine, Class<T> type) {
        return machine.features.allProcessors().stream()
                .filter(type::isInstance).map(type::cast).findFirst();
    }

    public static int burnTime(DefinedMachineEntity machine) {
        return processor(machine, Burner.class).map(Burner::burnTime).orElse(0);
    }

    public static int totalBurnTime(DefinedMachineEntity machine) {
        return processor(machine, Burner.class).map(Burner::totalBurnTime).orElse(0);
    }

    public static float temperature(DefinedMachineEntity machine) {
        return processor(machine, GeothermalBoilerProcessor.class)
                .map(GeothermalBoilerProcessor::temperature).orElse(0f);
    }

    public static int maxTemperature(DefinedMachineEntity machine) {
        return GeothermalBoilerProcessor.MAX_TEMPERATURE;
    }

    public static int distillProgress(DefinedMachineEntity machine) {
        return processor(machine, StillProcessor.class).map(StillProcessor::progress).orElse(0);
    }

    public static int distillDuration(DefinedMachineEntity machine) {
        return processor(machine, StillProcessor.class).map(StillProcessor::duration).orElse(1);
    }
}

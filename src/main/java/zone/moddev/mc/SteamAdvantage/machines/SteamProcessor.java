package zone.moddev.mc.SteamAdvantage.machines;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import zone.moddev.mc.lib.api.redstone.IRedstoneOutput;
import zone.moddev.mc.lib.api.redstone.RedstoneOutput;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/**
 * Shared base for SteamAdvantage's XML machine processors.
 *
 * <p>Every SteamAdvantage machine keeps its steam tank first, so tank 0 is always steam; the
 * remaining tank indexes follow each machine's XML declaration order, which matches the order
 * SA3 used before the XML port so existing saves load into the same tanks.
 *
 * <p>Like SA2, a machine stops working while it receives any redstone signal. That check lives
 * here, rather than in MMDLib's per-face redstone control, so the default is unchanged.
 *
 * <p>Each processor also supplies the machine's comparator reading, selected in the machine XML
 * with {@code <redstone comparator-source="steamadvantage/comparator"/>}.
 */
public abstract class SteamProcessor extends StoredProcessor implements IRedstoneOutput {
    /** The comparator descriptor ID every SteamAdvantage machine XML selects. */
    public static final String COMPARATOR = "steamadvantage/comparator";
    protected static final int STEAM = 0;

    /** The machine block entity; only valid on a live host, never on the schema prototype. */
    protected final DefinedMachineEntity entity() {
        return (DefinedMachineEntity) machine;
    }

    protected final FluidTank tank(int index) {
        return entity().fluids.tank(index);
    }

    protected final boolean powered() {
        return machine.level().hasNeighborSignal(machine.position());
    }

    protected final boolean hasSteam(int amount) {
        return tank(STEAM).getFluidAmount() >= amount;
    }

    protected final void spendSteam(int amount) {
        tank(STEAM).drain(amount, FluidAction.EXECUTE);
    }

    /** The machine's front, for horizontal and six-way machines alike. */
    protected final Direction front() {
        var state = machine.blockState();
        for (var property : state.getProperties())
            if (property.getName().equals("facing") && property.getValueClass() == Direction.class)
                return (Direction) state.getValue(property);
        return Direction.NORTH;
    }

    /** Current comparator reading, 0..15. Called only on a live host. */
    protected abstract int comparatorSignal();

    @Override
    public final List<RedstoneOutput> redstoneOutputs() {
        return List.of(new RedstoneOutput(COMPARATOR,
                Component.translatable("redstone.steamadvantage.comparator"),
                Component.translatable("redstone.steamadvantage.comparator.desc"), 0xC8C8C8,
                Optional.empty(), () -> machine == null || machine.removed()
                        ? 0 : comparatorSignal()));
    }

    /** SA2's proportional reading: floor(15 * fraction), zero when empty. */
    protected static int proportionalSignal(double amount, double capacity) {
        return capacity <= 0 || amount <= 0 ? 0 : (int) Math.clamp(15 * amount / capacity, 0, 15);
    }

    protected final int tankSignal(int index) {
        var tank = tank(index);
        return proportionalSignal(tank.getFluidAmount(), tank.getCapacity());
    }
}

package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StorageAdmission;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.poweradvantage.api.distillation.DistillingRecipe;

/**
 * Steam-powered still: runs Power Advantage distilling recipes. Spends 150 mB of steam and the
 * recipe's input when a batch starts, holds the result until the batch time has passed and the
 * product tank (1) can take it all. Tanks: steam (0), product (1), input (2).
 */
public final class StillProcessor extends SteamProcessor implements StorageAdmission {
    private static final int PRODUCT = 1, INPUT = 2;
    private final ProcessorState.Key<FluidStack> pending = variables.define("pending",
            FluidStack.OPTIONAL_CODEC, FluidStack.EMPTY, FluidStack::copy, false, false);
    private final ProcessorState.Key<Integer> duration = intVariable("duration"),
            progress = intVariable("progress");

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) {
        return true;
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        boolean running = false;
        for (int step = 0; step < ticks; step++) {
            if (!powered())
                running |= distill();
            pushAllSides(PRODUCT, 100);
        }
        if (running)
            meltSnow();
        return running ? ProcessState.RUNNING : ProcessState.IDLE;
    }

    private boolean distill() {
        boolean running = !variables.get(pending).isEmpty();
        if (!running) {
            if (!hasSteam(150))
                return false;
            var input = tank(INPUT);
            var holder = DistillingRecipe.find(machine.level(), input.getFluid());
            if (holder.isEmpty())
                return false;
            var recipe = holder.get().value();
            FluidStack result = recipe.output().copy();
            if (tank(PRODUCT).fill(result, FluidAction.SIMULATE) != result.getAmount())
                return false;
            int amount = recipe.input().amount();
            if (input.drain(amount, FluidAction.SIMULATE).getAmount() != amount)
                return false;
            input.drain(amount, FluidAction.EXECUTE);
            spendSteam(150);
            variables.set(pending, result);
            variables.set(duration, Math.max(1, recipe.duration()));
            variables.set(progress, 0);
            running = true;
        }
        int time = variables.get(duration);
        if (variables.get(progress) < time)
            variables.set(progress, variables.get(progress) + 1);
        if (variables.get(progress) < time)
            return running;
        var result = variables.get(pending);
        if (tank(PRODUCT).fill(result, FluidAction.SIMULATE) != result.getAmount())
            return running;
        tank(PRODUCT).fill(result, FluidAction.EXECUTE);
        variables.set(pending, FluidStack.EMPTY);
        variables.set(progress, 0);
        variables.set(duration, 0);
        return running;
    }

    @Override
    public boolean acceptsFluid(ProcessorMachine host, String member, FluidAmount fluid) {
        return host.level() != null && DistillingRecipe.accepts(host.level(),
                new FluidStack(fluid.fluid(), Math.max(1, fluid.amount()), fluid.components()));
    }

    @Override
    void importLegacy(CompoundTag old, CompoundTag state) {
        int[] progress = old.getIntArray("Progress");
        if (old.contains("PendingDistillation")) {
            state.put("pending", old.getCompound("PendingDistillation"));
            state.putInt("duration", Math.max(1, old.getInt("DistillationDuration")));
            state.putInt("progress", progress.length > 0 ? progress[0] : 0);
        }
    }

    @Override
    protected int comparatorSignal() {
        return tankSignal(INPUT);
    }

    public int progress() {
        return variables.get(progress);
    }

    public int duration() {
        return Math.max(1, variables.get(duration));
    }

    /** The batch result waiting to be delivered, or empty. */
    public FluidStack pending() {
        return variables.get(pending).copy();
    }
}

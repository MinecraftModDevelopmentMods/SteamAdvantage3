package zone.moddev.mc.SteamAdvantage.machines;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import zone.moddev.mc.poweradvantage.api.distillation.DistillingRecipe;

/** PA3 recipe processing with persisted operation progress and output. */
final class StillOperation {
    private StillOperation() {}

    static void distill(SteamMachine machine) {
        if (machine.pendingDistillation.isEmpty()) {
            if (!machine.hasSteam(150))
                return;
            var inputTank = machine.fluids.tank(machine.water);
            var recipeHolder = DistillingRecipe.find(machine.getLevel(), inputTank.getFluid());
            if (recipeHolder.isEmpty())
                return;
            var recipe = recipeHolder.get().value();
            FluidStack result = recipe.output().copy();
            if (machine.fluids.tank(machine.product).fill(result, FluidAction.SIMULATE)
                    != result.getAmount())
                return;
            int amount = recipe.input().amount();
            if (inputTank.drain(amount, FluidAction.SIMULATE).getAmount() != amount)
                return;
            inputTank.drain(amount, FluidAction.EXECUTE);
            machine.spendSteam(150);
            machine.pendingDistillation = result;
            machine.distillationDuration = Math.max(1, recipe.duration());
            machine.progress[0] = 0;
        }

        if (machine.progress[0] < machine.distillationDuration)
            machine.progress[0]++;
        if (machine.progress[0] < machine.distillationDuration)
            return;

        var outputTank = machine.fluids.tank(machine.product);
        if (outputTank.fill(machine.pendingDistillation, FluidAction.SIMULATE)
                != machine.pendingDistillation.getAmount())
            return;
        outputTank.fill(machine.pendingDistillation, FluidAction.EXECUTE);
        machine.pendingDistillation = FluidStack.EMPTY;
        machine.progress[0] = 0;
        machine.distillationDuration = 0;
    }
}

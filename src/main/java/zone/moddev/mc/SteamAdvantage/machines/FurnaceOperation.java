package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/** Three-lane smelting and temperature calculation. */
final class FurnaceOperation {
    private FurnaceOperation() {}

    static void smelt(SteamMachine machine) {
        var level = machine.getLevel();
        boolean canProcess = false;
        for (int i = 1; i <= 3; i++)
            if (level.getRecipeManager()
                            .getRecipeFor(RecipeType.SMELTING,
                                    new SingleRecipeInput(machine.items.getStackInSlot(i)), level)
                            .isPresent())
                canProcess = true;
        boolean burning = (machine.burn > 0 || canProcess) && machine.burnFuel();
        float input = burning ? 2.5f : 0;
        if (burning && machine.hasSteam(50)) {
            input = 15;
            machine.spendSteam(50);
        }
        machine.temperature = Math.clamp(machine.temperature
                        + .3f
                                * (10 * input - .00003f * machine.temperature * machine.temperature
                                        - .03f * machine.temperature),
                0, 2000);
        for (int i = 0; i < 3; i++) {
            var inputStack = new SingleRecipeInput(machine.items.getStackInSlot(i + 1));
            var recipe =
                    level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, inputStack, level);
            if (recipe.isEmpty() || machine.temperature < 100) {
                machine.progress[i] = 0;
                continue;
            }
            ItemStack output = recipe.get().value().assemble(inputStack, level.registryAccess());
            if (!machine.items.fits(i + 4, output))
                continue;
            machine.progress[i] += Math.max(1, (int) (machine.temperature * .035));
            if (machine.progress[i] >= 10000) {
                machine.progress[i] = 0;
                machine.items.getStackInSlot(i + 1).shrink(1);
                machine.items.insertResult(i + 4, output, false);
            }
        }
    }
}

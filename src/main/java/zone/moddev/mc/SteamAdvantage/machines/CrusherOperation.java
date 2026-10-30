package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;

/** MMDLib crushing recipe processing. */
final class CrusherOperation {
    private CrusherOperation() {}

    static void crush(SteamMachine machine) {
        var level = machine.getLevel();
        var input = new SingleRecipeInput(machine.items.getStackInSlot(0));
        var recipe = level.getRecipeManager().getRecipeFor(CrushingRecipe.TYPE.get(), input, level);
        if (recipe.isEmpty()) {
            machine.progress[0] = 0;
            return;
        }
        ItemStack output = recipe.get().value().assemble(input, level.registryAccess());
        if (!machine.items.fits(4, output) || !machine.hasSteam(150))
            return;
        machine.spendSteam(150);
        if (++machine.progress[0] >= 125) {
            machine.progress[0] = 0;
            machine.items.getStackInSlot(0).shrink(1);
            machine.items.insertResult(4, output, false);
        }
    }
}

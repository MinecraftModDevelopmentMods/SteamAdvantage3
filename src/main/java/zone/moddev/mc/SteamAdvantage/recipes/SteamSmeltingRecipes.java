package zone.moddev.mc.SteamAdvantage.recipes;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeOutput;
import zone.moddev.mc.lib.machine.recipe.generation.RecipeGenerationContext;
import zone.moddev.mc.lib.machine.recipe.generation.RecipeTransforms;
import zone.moddev.mc.lib.machine.recipe.generation.SmeltingRecipeGenerator;

/**
 * Steam furnace recipes: every smelting recipe, at its own cooking time, paid in steam. MMDLib's
 * smelting adapter prices a recipe at {@code energy-per-tick} FE per cooking tick (20); this
 * converts that to steam at {@code energy-per-mb} FE per mB (2), so a 200-tick recipe costs
 * 2000 mB.
 */
public final class SteamSmeltingRecipes extends SmeltingRecipeGenerator {
    @Override
    public void generate(RecipeGenerationContext ctx, MachineRecipeOutput output) {
        var p = ctx.properties();
        String group = p.string("steam-group", "steam");
        long rate = p.longValue("energy-per-mb", 2, 1, Long.MAX_VALUE);
        var steam = HolderSet.direct(ctx.registries().lookupOrThrow(Registries.FLUID)
                .getOrThrow(SteamCrushingRecipes.STEAM));
        super.generate(ctx.withProperties(p.without("steam-group", "energy-per-mb")),
                (id, recipe) -> output.accept(id,
                        RecipeTransforms.energyToFluid(recipe, group, steam, rate)));
    }
}

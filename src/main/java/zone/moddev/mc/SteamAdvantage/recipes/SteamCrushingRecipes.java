package zone.moddev.mc.SteamAdvantage.recipes;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeGenerator;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeOutput;
import zone.moddev.mc.lib.machine.recipe.generation.RecipeGenerationContext;

/**
 * Steam crusher recipes: every MMDLib crushing recipe (including other mods' and datapacks'),
 * taking {@code time} ticks and {@code steam} mB of steam per item. The defaults match the
 * crusher before the XML port, 125 ticks at 150 mB per tick.
 */
public final class SteamCrushingRecipes implements MachineRecipeGenerator {
    static final ResourceKey<net.minecraft.world.level.material.Fluid> STEAM =
            ResourceKey.create(Registries.FLUID, ResourceLocation.parse("poweradvantage:steam"));

    @Override
    public void generate(RecipeGenerationContext ctx, MachineRecipeOutput output) {
        var p = ctx.properties();
        p.requireOnly("input-group", "output-group", "steam-group", "steam", "time");
        String input = p.string("input-group", "input");
        String result = p.string("output-group", "output");
        String steamGroup = p.string("steam-group", "steam");
        int steam = p.integer("steam", 18750, 1, Integer.MAX_VALUE);
        int time = p.integer("time", 125, 1, Integer.MAX_VALUE);
        var steamFluid = HolderSet.direct(
                ctx.registries().lookupOrThrow(Registries.FLUID).getOrThrow(STEAM));
        for (var holder : ctx.recipes()) {
            if (!(holder.value() instanceof CrushingRecipe recipe))
                continue;
            var product = recipe.getResultItem(ctx.registries());
            if (product.isEmpty() || recipe.getIngredients().size() != 1)
                continue;
            var id = ResourceLocation.fromNamespaceAndPath(ctx.recipeType().id().getNamespace(),
                    "generated/" + ctx.recipeType().id().getPath() + "/crushing/"
                            + holder.id().getNamespace() + "/" + holder.id().getPath());
            output.accept(id, MachineRecipeBuilder.recipe(ctx.recipeType())
                    .inputItems(input, recipe.getIngredients().getFirst(), 1)
                    .inputFluids(steamGroup, steamFluid, steam)
                    .outputItem(result, product.copy())
                    .time(time)
                    .build());
        }
    }
}

package zone.moddev.mc.SteamAdvantage.machines;

import java.util.function.Supplier;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;

/** Shared inventory policy for server storage and client menu prediction. */
public final class MachineInventory {
    private MachineInventory() {}
    public static ItemStorage create(
            MachineSettings settings, Supplier<Level> level, Runnable changed) {
        String kind = settings.kind();
        var items = new ItemStorage(settings.inventorySize()).onChange(changed);
        items.role(Role.INPUT, 0, 1, 2, 3).role(Role.OUTPUT, 4, 5, 6, 7, 8);
        items.filter(stack
                -> kind.equals("steam_crusher")
                        || ((kind.equals("steam_furnace") || kind.equals("steam_boiler_coal"))
                                && stack.getBurnTime(RecipeType.SMELTING) > 0),
                0);
        items.filter(stack
                -> kind.equals("steam_furnace") && level.get() != null
                        && level.get()
                                .getRecipeManager()
                                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack),
                                        level.get())
                                .isPresent(),
                1, 2, 3);
        return items;
    }
}

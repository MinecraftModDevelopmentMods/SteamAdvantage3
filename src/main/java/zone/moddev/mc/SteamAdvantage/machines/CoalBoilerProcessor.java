package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.StorageAdmission;

/** Coal boiler: burns any furnace fuel from its fuel slot (item slot 0). */
public final class CoalBoilerProcessor extends BoilerProcessor implements StorageAdmission, Burner {
    private final zone.moddev.mc.lib.machine.feature.ProcessorState.Key<Integer> burn =
            intVariable("burn"), totalBurn = intVariable("totalBurn");

    @Override
    protected boolean heat(boolean ambient) {
        return burnFuel(entity(), variables, burn, totalBurn);
    }

    /** Spends one tick of burn time, lighting the next fuel item from slot 0 when out. */
    static boolean burnFuel(zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity machine,
            zone.moddev.mc.lib.machine.feature.ProcessorState variables,
            zone.moddev.mc.lib.machine.feature.ProcessorState.Key<Integer> burn,
            zone.moddev.mc.lib.machine.feature.ProcessorState.Key<Integer> totalBurn) {
        if (variables.get(burn) > 0) {
            variables.set(burn, variables.get(burn) - 1);
            return true;
        }
        var items = machine.items;
        ItemStack stack = items.getStackInSlot(0);
        int ticks = stack.getBurnTime(RecipeType.SMELTING);
        if (ticks <= 0)
            return false;
        ItemStack remainder = stack.getCraftingRemainingItem();
        ItemStack rest = stack.copyWithCount(stack.getCount() - 1);
        items.setStackInSlot(0, rest.isEmpty() ? remainder : rest);
        variables.set(burn, ticks);
        variables.set(totalBurn, ticks);
        return true;
    }

    @Override
    public boolean acceptsItem(ProcessorMachine host, String member, ItemStack stack) {
        return stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    @Override
    void importLegacy(CompoundTag old, CompoundTag state) {
        state.putInt("burn", old.getInt("Burn"));
        state.putInt("totalBurn", old.getInt("TotalBurn"));
    }

    @Override
    protected int comparatorSignal() {
        return inventorySignal(0, 1);
    }

    public int burnTime() {
        return variables.get(burn);
    }

    public int totalBurnTime() {
        return variables.get(totalBurn);
    }
}

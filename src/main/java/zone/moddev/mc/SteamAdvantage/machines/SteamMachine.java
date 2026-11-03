package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import zone.moddev.mc.SteamAdvantage.config.SteamConfig;
import zone.moddev.mc.SteamAdvantage.init.ModContent;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.poweradvantage.api.distillation.DistillingRecipe;

/** Shared persisted machine storage. One steam unit is represented by 100 mB. */
public final class SteamMachine
        extends BlockEntity implements zone.moddev.mc.lib.gui.runtime.ScreenHost {
    final String kind;
    final ItemStorage items;
    final FluidStorage fluids = new FluidStorage().onChange(this::setChanged);
    final EnergyBuffer energy;
    private final MachineSettings settings;
    int burn, totalBurn, depth, elevatorHeight;
    float temperature;
    final int[] progress = new int[3];
    net.neoforged.neoforge.fluids.FluidStack pendingDistillation =
            net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    int distillationDuration;
    int steam, water = -1, fuel = -1, product = -1;
    boolean raised;
    public SteamMachine(BlockPos pos, BlockState state) {
        this(ModContent.machineType(state), pos, state,
                ((MachineBlock) state.getBlock()).settings());
    }
    public SteamMachine(net.minecraft.world.level.block.entity.BlockEntityType<SteamMachine> type,
            BlockPos pos, BlockState state, MachineSettings settings) {
        super(type, pos, state);
        this.settings = settings;
        items = MachineInventory.create(settings, this::getLevel, this::setChanged);
        energy = new EnergyBuffer(settings.energyCapacity(), settings.energyTransfer(), 0)
                         .onChange(this::setChanged);
        kind = settings.kind();
        boolean boiler = kind.startsWith("steam_boiler");
        int capacity = settings.steamCapacity();
        steam = fluids.addTank(capacity,
                kind.equals("steam_tank") ? Role.STORAGE
                        : boiler          ? Role.OUTPUT
                                          : Role.INPUT,
                f -> f.getFluid() == zone.moddev.mc.poweradvantage.init.Fluids.STEAM.get());
        if (boiler)
            water = fluids.addTank(
                    settings.waterCapacity(), Role.INPUT, f -> f.getFluid() == Fluids.WATER);
        if (kind.equals("steam_boiler_oil"))
            fuel = fluids.addTank(
                    settings.fuelCapacity(), Role.INPUT, f -> f.is(SteamConfig.fuelTag()));
        if (kind.equals("steam_pump") || kind.equals("steam_still"))
            product = fluids.addTank(settings.productCapacity(), Role.OUTPUT);
        if (kind.equals("steam_still"))
            water = fluids.addTank(settings.waterCapacity(), Role.INPUT,
                    f -> getLevel() != null && DistillingRecipe.accepts(getLevel(), f));
    }
    public String kind() {
        return kind;
    }
    public ItemStorage items() {
        return items;
    }
    public FluidStorage fluids() {
        return fluids;
    }
    public EnergyBuffer energy() {
        return energy;
    }

    public void clearDrill() {
        DrillOperation.clearDrill(this);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SteamMachine m) {
        boolean powered = level.hasNeighborSignal(pos);
        boolean running = false;
        if (powered && m.kind.equals("steam_drill"))
            DrillOperation.clearDrill(m);
        if (m.kind.equals("steam_elevator"))
            ElevatorOperation.elevator(m, powered);
        else if (!powered) {
            switch (m.kind) {
                case "steam_boiler_coal", "steam_boiler_oil", "steam_boiler_electric",
                        "steam_boiler_geothermal" ->
                    running = BoilerOperation.boil(m);
                case "steam_furnace" -> running = FurnaceOperation.smelt(m);
                case "steam_crusher" -> CrusherOperation.crush(m);
                case "steam_drill" -> DrillOperation.drill(m);
                case "steam_pump" -> PumpOperation.pump(m);
                case "steam_still" -> running = StillOperation.distill(m);
                case "steam_switch" -> m.exportSteam();
                default -> {
                }
            }
        }
        if (running) {
            BlockPos snowPos = pos.above();
            if (level.getBlockState(snowPos).is(Blocks.SNOW))
                level.removeBlock(snowPos, false);
        }
        if (!powered && (m.kind.startsWith("steam_boiler") || m.kind.equals("steam_tank"))) {
            for (Direction side : Direction.values())
                Transfer.pushFluids(level, pos, side, m.fluids.tank(m.steam), 100);
        }
        if (m.product >= 0)
            for (Direction side : Direction.values())
                Transfer.pushFluids(level, pos, side, m.fluids.view(IoMode.OUTPUT), 100);
        if (level.getGameTime() % 20 == 0) {
            m.setChanged();
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }
    private void exportSteam() {
        Direction output = getBlockState().getValue(BlockStateProperties.FACING);
        Transfer.pushFluids(level, worldPosition, output, fluids.tank(steam), 1000);
    }
    boolean hasSteam(int amount) {
        return fluids.tank(steam).getFluidAmount() >= amount;
    }
    void spendSteam(int amount) {
        fluids.tank(steam).drain(amount, FluidAction.EXECUTE);
    }
    boolean burnFuel() {
        if (burn > 0) {
            burn--;
            return true;
        }
        ItemStack stack = items.getStackInSlot(0);
        int ticks = stack.getBurnTime(RecipeType.SMELTING);
        if (ticks <= 0)
            return false;
        ItemStack remainder = stack.getCraftingRemainingItem();
        stack.shrink(1);
        if (stack.isEmpty())
            items.setStackInSlot(0, remainder);
        burn = totalBurn = ticks;
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.put("Fluids", fluids.serializeNBT(registries));
        tag.put("Energy", energy.serializeNBT(registries));
        tag.putInt("Burn", burn);
        tag.putInt("TotalBurn", totalBurn);
        tag.putFloat("Temperature", temperature);
        tag.putIntArray("Progress", progress);
        if (!pendingDistillation.isEmpty())
            tag.put("PendingDistillation", pendingDistillation.save(registries));
        tag.putInt("DistillationDuration", distillationDuration);
        tag.putInt("Depth", depth);
        tag.putBoolean("Raised", raised);
        tag.putInt("ElevatorHeight", elevatorHeight);
    }
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("Items"));
        fluids.deserializeNBT(registries, tag.getCompound("Fluids"));
        energy.deserializeNBT(registries, tag.get("Energy"));
        burn = tag.getInt("Burn");
        totalBurn = tag.getInt("TotalBurn");
        temperature = tag.getFloat("Temperature");
        int[] p = tag.getIntArray("Progress");
        System.arraycopy(p, 0, progress, 0, Math.min(p.length, 3));
        pendingDistillation = tag.contains("PendingDistillation")
                ? net.neoforged.neoforge.fluids.FluidStack.parseOptional(
                        registries, tag.getCompound("PendingDistillation"))
                : net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        distillationDuration = pendingDistillation.isEmpty()
                ? 0 : Math.max(1, tag.getInt("DistillationDuration"));
        depth = tag.getInt("Depth");
        raised = tag.getBoolean("Raised");
        elevatorHeight = tag.getInt("ElevatorHeight");
    }
    public MachineSettings settings() {
        return settings;
    }
    public net.neoforged.neoforge.fluids.FluidStack getSteamTank() {
        return fluids.tank(steam).getFluid().copy();
    }
    public int getSteamCapacity() {
        return fluids.tank(steam).getCapacity();
    }
    public net.neoforged.neoforge.fluids.FluidStack getWaterTank() {
        return water < 0 ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                         : fluids.tank(water).getFluid().copy();
    }
    public int getWaterCapacity() {
        return water < 0 ? 0 : fluids.tank(water).getCapacity();
    }
    public net.neoforged.neoforge.fluids.FluidStack getFuelTank() {
        return fuel < 0 ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                        : fluids.tank(fuel).getFluid().copy();
    }
    public int getFuelCapacity() {
        return fuel < 0 ? 0 : fluids.tank(fuel).getCapacity();
    }
    public net.neoforged.neoforge.fluids.FluidStack getProductTank() {
        return product < 0 ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                           : fluids.tank(product).getFluid().copy();
    }
    public int getProductCapacity() {
        return product < 0 ? 0 : fluids.tank(product).getCapacity();
    }
    public long getEnergy() {
        return energy.stored();
    }
    public long getEnergyCapacity() {
        return energy.capacity();
    }
    public float getTemperature() {
        return temperature;
    }
    public int getMaxTemperature() {
        return 2000;
    }
    public int getBurnTime() {
        return burn;
    }
    public int getTotalBurnTime() {
        return totalBurn;
    }
    public int getProgress() {
        return progress[0];
    }
    public int getSecondProgress() {
        return progress[1];
    }
    public int getThirdProgress() {
        return progress[2];
    }
    public int getProgressMax() {
        return kind.equals("steam_crusher") ? 125
                : kind.equals("steam_still") ? Math.max(1, distillationDuration)
                                              : 10000;
    }
    public int getDrillDepth() {
        return depth;
    }
    public int getDrillRange() {
        return 64;
    }
    @Override
    public net.neoforged.neoforge.items.IItemHandlerModifiable screenItems() {
        return items;
    }
    @Override
    public int[] screenRecipeSlots(net.minecraft.resources.ResourceLocation category) {
        if (kind.equals("steam_crusher") && category.toString().equals("mmdlib:crushing"))
            return new int[] {0};
        if (kind.equals("steam_furnace") && category.toString().equals("minecraft:smelting"))
            return new int[] {1};
        return new int[0];
    }
}

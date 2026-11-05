package zone.moddev.mc.SteamAdvantage.init;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import zone.moddev.mc.SteamAdvantage.SteamAdvantage;
import zone.moddev.mc.SteamAdvantage.blocks.DrillBitBlockEntity;
import zone.moddev.mc.SteamAdvantage.machines.MachineBlock;
import zone.moddev.mc.SteamAdvantage.machines.SteamMachine;
import zone.moddev.mc.SteamAdvantage.recipes.RecipeModeCondition;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.MachineCapabilities;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;
import zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock;

/**
 * Applies this mod's MMDLib standalone plans; owns the block-entity register and the
 * recipe-condition registry.
 */
public final class ModContent {
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, SteamAdvantage.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SteamAdvantage.MODID);
    private static RuntimeContentRegistrar runtime;
    // Acceptance-test catalog, not an alternative registration list. Definitions own registration.
    public static final List<String> MACHINES =
            List.of("steam_boiler_coal", "steam_boiler_electric", "steam_boiler_geothermal",
                    "steam_boiler_oil", "steam_tank", "steam_furnace", "steam_crusher",
                    "steam_drill", "steam_elevator", "steam_still", "steam_pump", "steam_switch");
    private ModContent() {}
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SteamAdvantage.MODID, path);
    }
    public static Block block(String name) {
        return BuiltInRegistries.BLOCK.getOptional(id(name)).orElseThrow();
    }
    public static Item item(String name) {
        return BuiltInRegistries.ITEM.getOptional(id(name)).orElseThrow();
    }
    public static RuntimeContentRegistrar runtime() {
        return java.util.Objects.requireNonNull(runtime, "Content has not been initialized");
    }
    @SuppressWarnings("unchecked")
    public static BlockEntityType<SteamMachine> machineType(BlockState state) {
        return (BlockEntityType<SteamMachine>) runtime()
                .blockEntityTypes()
                .get(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                .get();
    }
    @SuppressWarnings("unchecked")
    public static BlockEntityType<DrillBitBlockEntity> drillBitType() {
        return (BlockEntityType<DrillBitBlockEntity>)
                runtime().blockEntityTypes().get(id("drillbit")).get();
    }
    public static void register(IEventBus bus) {
        CONDITIONS.register("recipe_mode", () -> RecipeModeCondition.CODEC);
        CONDITIONS.register(bus);
        BLOCK_ENTITY_TYPES.register(bus);
        var api = MaterialExtensions.GLOBAL;
        ContentFactories.register(api);
        runtime = new RuntimeContentRegistrar(SteamAdvantage.MODID, api, bus);
        bus.addListener((FMLConstructModEvent event) -> event.enqueueWork(() -> {
            MMDLib.standaloneBlocks().forEach((id, plan) -> {
                if (id.getNamespace().equals(SteamAdvantage.MODID))
                    runtime.queue(plan);
            });
            MMDLib.standaloneItems().forEach((id, plan) -> {
                if (id.getNamespace().equals(SteamAdvantage.MODID))
                    runtime.queue(plan);
            });
            runtime.blockEntityHandles().values().forEach(
                    handle -> handle.register(BLOCK_ENTITY_TYPES));
        }));
        bus.addListener(ModContent::capabilities);
    }
    @SuppressWarnings("unchecked")
    private static void capabilities(RegisterCapabilitiesEvent event) {
        runtime.blockEntityTypes().forEach((id, holder) -> {
            if (!(block(id.getPath()) instanceof MachineBlock)) return;
            var type = (BlockEntityType<SteamMachine>) holder.get();
            MachineCapabilities.items(event, type, SteamMachine::items);
            MachineCapabilities.fluids(event, type, SteamMachine::fluids);
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type,
                    (be, side)
                            -> be.kind().equals("steam_boiler_electric")
                            ? be.energy().view(IoMode.INPUT)
                            : null);
        });
        FluidPipeBlock.registerCapabilities(
                event, (FluidPipeBlock) block("steam_pipe"), (FluidPipeBlock) block("steam_track"));
    }
}

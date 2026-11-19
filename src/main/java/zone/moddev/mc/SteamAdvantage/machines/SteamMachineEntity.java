package zone.moddev.mc.SteamAdvantage.machines;

import java.util.List;
import java.util.function.IntUnaryOperator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * Block entity for SteamAdvantage's XML machines. The XML definition supplies storage,
 * processors, capabilities and screen; this subclass only imports saves written before the
 * XML port, when every machine shared one entity class.
 *
 * <p>Those saves kept machine state in top-level keys ({@code Burn}, {@code Temperature},
 * {@code Raised} and so on), every machine's items in one nine-slot {@code Items} inventory, and
 * the electric boiler's energy under {@code Energy} before MMDLib 2.0.2. Each processor copies
 * the keys it uses into its own state ({@link SteamProcessor#importLegacy}); the next save is
 * in the current format.
 */
public class SteamMachineEntity extends DefinedMachineEntity {
    /** Every pre-XML save wrote this key, whatever the machine; XML saves never do. */
    private static final String LEGACY_MARKER = "TotalBurn";
    private static final List<String> LEGACY_KEYS = List.of("Burn", "TotalBurn", "Temperature",
            "Progress", "PendingDistillation", "DistillationDuration", "Depth", "DrillPhase",
            "DrillRetractionTicks", "Raised", "ElevatorHeight", "Energy");

    public SteamMachineEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
            MachineDefinition definition, @Nullable MachineRecipeType recipes) {
        super(type, pos, state, definition, recipes);
    }

    /** True for a save written before the XML port. */
    static boolean isLegacy(CompoundTag tag) {
        return tag.contains(LEGACY_MARKER, Tag.TAG_ANY_NUMERIC);
    }

    @Override
    protected CompoundTag migrateMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        if (!isLegacy(tag))
            return tag;
        var converted = tag.copy();
        if (!converted.contains("energy") && converted.contains("Energy"))
            converted.put("energy", converted.get("Energy").copy());
        var saved = converted.getCompound("features");
        for (var config : definition.processors())
            features.findProcessor(config.id(), SteamProcessor.class).ifPresent(processor -> {
                var state = saved.getCompound(config.id());
                processor.importLegacy(tag, state);
                saved.put(config.id(), state);
            });
        converted.put("features", saved);
        LEGACY_KEYS.forEach(converted::remove);
        return converted;
    }

    /**
     * Pre-XML item slots: 0 fuel or input, 1-3 furnace inputs, 4-8 outputs. Each XML machine
     * declares only the slots it uses, so outputs move down.
     */
    @Override
    protected IntUnaryOperator legacySlots(String key, CompoundTag tag) {
        if (!"Items".equals(key))
            return IntUnaryOperator.identity();
        return switch (definition.id().getPath()) {
            case "steam_crusher" -> slot -> slot == 0 ? 0 : slot >= 4 ? slot - 3 : -1;
            case "steam_drill" -> slot -> slot >= 4 ? slot - 4 : -1;
            default -> IntUnaryOperator.identity();
        };
    }
}

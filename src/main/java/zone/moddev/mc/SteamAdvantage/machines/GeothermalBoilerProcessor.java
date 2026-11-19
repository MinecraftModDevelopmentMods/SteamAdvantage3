package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;

/**
 * Geothermal boiler: each adjacent lava or magma block adds heat (up to 2000); it boils at 100
 * or more and cools by one each tick.
 */
public final class GeothermalBoilerProcessor extends BoilerProcessor {
    public static final int MAX_TEMPERATURE = 2000;
    private final zone.moddev.mc.lib.machine.feature.ProcessorState.Key<Float> temperature =
            variables.define("temperature", com.mojang.serialization.Codec.FLOAT, 0f);

    @Override
    protected boolean ambientHeat() {
        var level = machine.level();
        var pos = machine.position();
        int hot = 0;
        for (Direction d : Direction.values()) {
            BlockPos p = pos.relative(d);
            if (level.hasChunkAt(p)
                    && (level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA)
                            || level.getBlockState(p).is(Blocks.MAGMA_BLOCK)))
                hot++;
        }
        float next = Math.min(MAX_TEMPERATURE, Math.max(0, variables.get(temperature) + hot * 4 - 1));
        variables.set(temperature, next);
        return next >= 100;
    }

    @Override
    protected boolean heat(boolean ambient) {
        return ambient;
    }

    @Override
    void importLegacy(CompoundTag old, CompoundTag state) {
        state.putFloat("temperature", old.getFloat("Temperature"));
    }

    @Override
    protected int comparatorSignal() {
        return proportionalSignal(variables.get(temperature), MAX_TEMPERATURE);
    }

    public float temperature() {
        return variables.get(temperature);
    }

    /** Test and tooling access; outside a processor tick. */
    public void setTemperature(float value) {
        variables.update(() -> variables.set(temperature, value));
    }
}

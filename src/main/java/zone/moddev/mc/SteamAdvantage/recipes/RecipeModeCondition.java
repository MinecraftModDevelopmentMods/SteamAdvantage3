package zone.moddev.mc.SteamAdvantage.recipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.neoforged.neoforge.common.conditions.ICondition;
import zone.moddev.mc.SteamAdvantage.config.SteamConfig;

/** Preserves the three legacy recipe modes, selected in the SteamAdvantage common config. */
public record RecipeModeCondition(List<String> modes) implements ICondition {
    public static final MapCodec<RecipeModeCondition> CODEC =
            Codec.STRING.listOf().fieldOf("modes").xmap(
                    RecipeModeCondition::new, RecipeModeCondition::modes);
    @Override
    public boolean test(IContext context) {
        return modes.contains(SteamConfig.RECIPE_MODE.get().name());
    }
    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}

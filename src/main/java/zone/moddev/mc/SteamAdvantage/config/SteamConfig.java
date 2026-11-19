package zone.moddev.mc.SteamAdvantage.config;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SteamConfig {
    public enum RecipeMode { NORMAL, TECH_PROGRESSION, APOCALYPTIC }
    public static final ModConfigSpec.EnumValue<RecipeMode> RECIPE_MODE;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue OIL_BURN_TICKS, RELOAD;
    public static final ModConfigSpec.DoubleValue DAMAGE, DRILL_TICKS_PER_HARDNESS;
    public static final ModConfigSpec.BooleanValue MUSKETS;
    static {
        var builder = new ModConfigSpec.Builder();
        RECIPE_MODE = builder.defineEnum("recipeMode", RecipeMode.NORMAL);
        OIL_BURN_TICKS = builder.defineInRange("oilBurnTicksPer100mb", 1600, 1, 1000000);
        RELOAD = builder.defineInRange("musketReloadTicks", 100, 20, 300);
        DAMAGE = builder.defineInRange("musketDamage", 20.0, 0, 100);
        MUSKETS = builder.define("musketsEnabled", true);
        DRILL_TICKS_PER_HARDNESS = builder
                .comment("Steam drill mining time: ticks per point of block hardness (stone 1.5,",
                        "obsidian 50), with a minimum of half this value. 12 matches Steam Advantage 2.")
                .defineInRange("drillTicksPerHardness", 12.0, 0.1, 1000.0);
        SPEC = builder.build();
    }
    public static TagKey<Fluid> fuelTag() {
        return TagKey.create(
                Registries.FLUID, ResourceLocation.parse("steamadvantage:boiler_fuels"));
    }
}

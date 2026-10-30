package zone.moddev.mc.SteamAdvantage.machines;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Validated settings decoded by MMDLib for every standalone machine definition. */
public record MachineSettings(String kind, int steamCapacity, int waterCapacity, int fuelCapacity,
        int productCapacity, int inventorySize, long energyCapacity, long energyTransfer) {
    public static final Codec<MachineSettings> CODEC = RecordCodecBuilder.create(instance
            -> instance
                    .group(Codec.STRING.fieldOf("kind").forGetter(MachineSettings::kind),
                            Codec.intRange(1, 10000000)
                                    .fieldOf("steam_capacity")
                                    .forGetter(MachineSettings::steamCapacity),
                            Codec.intRange(0, 1000000)
                                    .optionalFieldOf("water_capacity", 0)
                                    .forGetter(MachineSettings::waterCapacity),
                            Codec.intRange(0, 1000000)
                                    .optionalFieldOf("fuel_capacity", 0)
                                    .forGetter(MachineSettings::fuelCapacity),
                            Codec.intRange(0, 1000000)
                                    .optionalFieldOf("product_capacity", 0)
                                    .forGetter(MachineSettings::productCapacity),
                            Codec.intRange(9, 64)
                                    .optionalFieldOf("inventory_size", 9)
                                    .forGetter(MachineSettings::inventorySize),
                            Codec.LONG
                                    .validate(value
                                            -> value >= 0
                                                    ? com.mojang.serialization.DataResult.success(
                                                              value)
                                                    : com.mojang.serialization.DataResult.error(
                                                              ()
                                                                      -> "Expected nonnegative "
                                                                      + "energy value"))
                                    .optionalFieldOf("energy_capacity", 1600L)
                                    .forGetter(MachineSettings::energyCapacity),
                            Codec.LONG
                                    .validate(value
                                            -> value >= 0
                                                    ? com.mojang.serialization.DataResult.success(
                                                              value)
                                                    : com.mojang.serialization.DataResult.error(
                                                              ()
                                                                      -> "Expected nonnegative "
                                                                      + "energy value"))
                                    .optionalFieldOf("energy_transfer", 160L)
                                    .forGetter(MachineSettings::energyTransfer))
                    .apply(instance, MachineSettings::new));
    public MachineSettings {
        if (!java.util.Set
                        .of("steam_boiler_coal", "steam_boiler_electric", "steam_boiler_geothermal",
                                "steam_boiler_oil", "steam_tank", "steam_furnace", "steam_crusher",
                                "steam_drill", "steam_elevator", "steam_still", "steam_pump",
                                "steam_switch")
                        .contains(kind))
            throw new IllegalArgumentException("Unknown SteamAdvantage machine behavior: " + kind);
        if ((kind.startsWith("steam_boiler") || kind.equals("steam_still")) && waterCapacity == 0)
            throw new IllegalArgumentException("Machine requires water/input storage: " + kind);
        if (kind.equals("steam_boiler_oil") && fuelCapacity == 0)
            throw new IllegalArgumentException("Oil boiler requires fuel storage");
        if ((kind.equals("steam_still") || kind.equals("steam_pump")) && productCapacity == 0)
            throw new IllegalArgumentException("Machine requires output storage: " + kind);
    }
}

package zone.moddev.mc.SteamAdvantage;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import zone.moddev.mc.SteamAdvantage.config.SteamConfig;
import zone.moddev.mc.SteamAdvantage.init.ModContent;

/** Mod entry point; content and behavior live in their respective packages. */
@Mod(SteamAdvantage.MODID)
public final class SteamAdvantage {
    public static final String MODID = "steamadvantage";

    public SteamAdvantage(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SteamConfig.SPEC);
        ModContent.register(bus);
    }
}

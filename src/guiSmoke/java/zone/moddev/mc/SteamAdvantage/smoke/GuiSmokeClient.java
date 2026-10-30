package zone.moddev.mc.SteamAdvantage.smoke;

import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import zone.moddev.mc.SteamAdvantage.SteamAdvantage;
import zone.moddev.mc.SteamAdvantage.init.ModContent;
import zone.moddev.mc.lib.gui.client.MachineScreen;

/** Captures actual rendered screens and checks the server-to-client XML getter snapshots. */
@EventBusSubscriber(modid = SteamAdvantage.MODID, value = Dist.CLIENT)
public final class GuiSmokeClient {
    private static final Set<String> captured = new LinkedHashSet<>();
    private static int settled, ticks;
    private static String previous = "";
    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (++ticks > 3000)
            throw new IllegalStateException("GUI smoke timed out: " + minecraft.screen);
        if (ticks % 200 == 0)
            org.slf4j.LoggerFactory.getLogger(GuiSmokeClient.class)
                    .info("GUI_SMOKE_WAIT {}", minecraft.screen);
        if (minecraft.screen
                instanceof net.minecraft.client.gui.screens
                        .AccessibilityOnboardingScreen onboarding) {
            minecraft.options.onboardingAccessibilityFinished();
            onboarding.onClose();
            return;
        }
        if (!(minecraft.screen instanceof MachineScreen screen))
            return;
        var menu = screen.getMenu();
        String name = menu.bound.screen().id().getPath();
        if (!name.equals(previous)) {
            previous = name;
            settled = 0;
        }
        if (++settled < 10 || captured.contains(name))
            return;
        var steam = menu.bound.screen().find("steam").orElseThrow();
        if (((Number) menu.value(steam, true)).doubleValue() <= 0
                || !(menu.value(steam, false)
                                instanceof net.neoforged.neoforge.fluids.FluidStack fluid)
                || fluid.getAmount() <= 0)
            throw new IllegalStateException("XML getter synchronization failed for " + name);
        var layout = screen.layout;
        if (layout.panel().width() > screen.width || layout.panel().height() > screen.height)
            throw new IllegalStateException("Screen does not fit: " + name + " " + layout.panel()
                    + " on " + screen.width + "x" + screen.height);
        var directory = minecraft.gameDirectory.toPath().resolve("screenshots");
        try {
            Files.createDirectories(directory);
            try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                image.writeToFile(directory.resolve(name + ".png"));
            }
            captured.add(name);
            org.slf4j.LoggerFactory.getLogger(GuiSmokeClient.class)
                    .info("GUI_SMOKE_CAPTURE {} steam={} capacity={}", name,
                            menu.value(steam, false), menu.value(steam, true));
            if (captured.size() == ModContent.MACHINES.size()) {
                Files.writeString(minecraft.gameDirectory.toPath().resolve("GUI_SMOKE_PASS.txt"),
                        String.join("\n", captured) + "\n");
                minecraft.stop();
            }
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}

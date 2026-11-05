package zone.moddev.mc.SteamAdvantage.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import zone.moddev.mc.SteamAdvantage.SteamAdvantage;
import zone.moddev.mc.SteamAdvantage.init.ModContent;
import zone.moddev.mc.SteamAdvantage.items.MusketItem;

@EventBusSubscriber(
        modid = SteamAdvantage.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent
    static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.drillBitType(), DrillBitRenderer::new);
    }
    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var musket = ModContent.item("musket");
            ItemProperties.register(musket, ResourceLocation.parse("steamadvantage:loaded"),
                    (stack, level, entity, seed) -> MusketItem.loaded(stack) ? 1 : 0);
            ItemProperties.register(musket, ResourceLocation.parse("steamadvantage:loading"),
                    (stack, level, entity, seed)
                            -> entity != null && entity.isUsingItem()
                                    && entity.getUseItem() == stack
                            ? 1
                            : 0);
        });
    }
}

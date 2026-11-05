package zone.moddev.mc.SteamAdvantage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import zone.moddev.mc.SteamAdvantage.SteamAdvantage;

/** Server-confirmed shot feedback, sent only to the shooter and handled on the client main thread. */
@EventBusSubscriber(modid = SteamAdvantage.MODID, bus = EventBusSubscriber.Bus.MOD)
public record MusketShot() implements CustomPacketPayload {
    public static final MusketShot INSTANCE = new MusketShot();
    public static final Type<MusketShot> TYPE = new Type<>(ResourceLocation.parse("steamadvantage:musket_shot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusketShot> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<MusketShot> type() { return TYPE; }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (shot, context) -> kick(context.player()));
    }

    /** Does not alter server aim or add movement; enchanted Recoil is independent. */
    public static void kick(Player player) {
        player.setXRot(Math.clamp(player.getXRot() - 15, -90, 90));
    }
}

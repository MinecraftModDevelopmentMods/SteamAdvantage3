package zone.moddev.mc.SteamAdvantage.init;

import static zone.moddev.mc.SteamAdvantage.init.ModContent.item;

import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import zone.moddev.mc.SteamAdvantage.SteamAdvantage;

@EventBusSubscriber(modid = SteamAdvantage.MODID)
public final class Villages {
    @SubscribeEvent
    static void trades(VillagerTradesEvent event) {
        var profession = event.getType();
        if (profession == VillagerProfession.ARMORER || profession == VillagerProfession.TOOLSMITH
                || profession == VillagerProfession.WEAPONSMITH
                || profession == VillagerProfession.LIBRARIAN) {
            event.getTrades().get(1).add(
                    (entity, random)
                            -> new MerchantOffer(new ItemCost(Items.EMERALD, 1),
                                    new ItemStack(item("steam_governor"), 1 + random.nextInt(4)),
                                    12, 2, .05f));
        }
        if (profession == VillagerProfession.LIBRARIAN)
            event.getTrades().get(1).add(
                    (entity, random)
                            -> new MerchantOffer(new ItemCost(Items.EMERALD, 1),
                                    new ItemStack(item("steam_pipe"), 4 + random.nextInt(5)), 12, 2,
                                    .05f));
        if (profession == VillagerProfession.WEAPONSMITH) {
            event.getTrades().get(2).add(
                    (entity, random)
                            -> new MerchantOffer(new ItemCost(Items.EMERALD, 1),
                                    new ItemStack(
                                            item("blackpowder_cartridge"), 5 + random.nextInt(3)),
                                    12, 5, .05f));
            event.getTrades().get(3).add(
                    (entity, random)
                            -> new MerchantOffer(
                                    new ItemCost(Items.EMERALD, 10 + random.nextInt(6)),
                                    new ItemStack(item("musket")), 3, 10, .05f));
        }
    }
}

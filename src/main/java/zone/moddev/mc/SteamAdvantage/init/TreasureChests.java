package zone.moddev.mc.SteamAdvantage.init;

import static zone.moddev.mc.SteamAdvantage.init.ModContent.item;

import java.util.Set;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import zone.moddev.mc.SteamAdvantage.SteamAdvantage;

@EventBusSubscriber(modid = SteamAdvantage.MODID)
public final class TreasureChests {
    private static final Set<String> CHESTS = Set.of("chests/abandoned_mineshaft",
            "chests/simple_dungeon", "chests/village/village_weaponsmith",
            "chests/stronghold_corridor", "chests/stronghold_crossing", "chests/nether_bridge");
    @SubscribeEvent
    static void loot(LootTableLoadEvent event) {
        var id = event.getName();
        if (!id.getNamespace().equals("minecraft") || !CHESTS.contains(id.getPath()))
            return;
        var pool =
                LootPool.lootPool()
                        .name("steamadvantage_supplies")
                        .setRolls(ConstantValue.exactly(1))
                        .add(EmptyLootItem.emptyItem().setWeight(135))
                        .add(LootItem.lootTableItem(item("steam_governor"))
                                        .setWeight(50)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(1, 6))))
                        .add(LootItem.lootTableItem(item("steam_pipe"))
                                        .setWeight(25)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(3, 6))))
                        .add(LootItem.lootTableItem(item("blackpowder_cartridge"))
                                        .setWeight(50)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(5, 15))))
                        .add(LootItem.lootTableItem(item("musket")).setWeight(18))
                        .add(LootItem.lootTableItem(item("musket"))
                                        .setWeight(2)
                                        .apply(EnchantWithLevelsFunction.enchantWithLevels(
                                                event.getRegistries(), ConstantValue.exactly(10))));
        event.getTable().addPool(pool.build());
    }
}

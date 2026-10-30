package zone.moddev.mc.SteamAdvantage.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import zone.moddev.mc.SteamAdvantage.SteamAdvantage;
import zone.moddev.mc.SteamAdvantage.init.ModContent;
import zone.moddev.mc.SteamAdvantage.machines.SteamMachine;

/** Opt-in disposable-world fixture. Never included in released mods. */
@EventBusSubscriber(modid = SteamAdvantage.MODID)
public final class GuiSmokeServer {
    private static int ticks, index;
    private static BlockPos pending;
    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        var players = event.getServer().getPlayerList().getPlayers();
        if (players.isEmpty() || index >= ModContent.MACHINES.size() && pending == null)
            return;
        var player = players.getFirst();
        var level = player.serverLevel();
        // The client needs the placed block entity before it receives the menu-open packet.
        if (pending != null) {
            if (++ticks % 20 != 0)
                return;
            var pos = pending;
            pending = null;
            var name = ModContent.MACHINES.get(index - 1);
            var machine = (SteamMachine) level.getBlockEntity(pos);
            machine.fluids().tank(0).fill(
                    new FluidStack(zone.moddev.mc.poweradvantage.init.Fluids.STEAM.get(),
                            machine.getSteamCapacity() * 3 / 4),
                    FluidAction.EXECUTE);
            for (int tank = 1; tank < machine.fluids().getTanks(); tank++) {
                machine.fluids().tank(tank).fill(
                        new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2000),
                        FluidAction.EXECUTE);
            }
            machine.energy().setStored(1200);
            machine.items().setStackInSlot(0, new ItemStack(Items.COAL, 8));
            if (name.equals("steam_furnace"))
                for (int i = 1; i <= 3; i++)
                    machine.items().setStackInSlot(i, new ItemStack(Items.RAW_IRON, 8));
            if (name.equals("steam_crusher"))
                machine.items().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 8));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.gameMode.useItemOn(player, level, ItemStack.EMPTY,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                            Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
            return;
        }
        if (++ticks % 60 != 0)
            return;
        player.setGameMode(GameType.CREATIVE);
        var pos = player.blockPosition().offset(2, 0, 0);
        level.setBlockAndUpdate(player.blockPosition().below(), Blocks.STONE.defaultBlockState());
        String name = ModContent.MACHINES.get(index++);
        level.setBlockAndUpdate(pos, ModContent.block(name).defaultBlockState());
        pending = pos;
    }
}

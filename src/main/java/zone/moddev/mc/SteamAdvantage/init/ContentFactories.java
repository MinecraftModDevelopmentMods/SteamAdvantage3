package zone.moddev.mc.SteamAdvantage.init;

import com.mojang.serialization.Codec;
import com.mojang.datafixers.util.Unit;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import zone.moddev.mc.SteamAdvantage.blocks.DrillBitBlock;
import zone.moddev.mc.SteamAdvantage.blocks.DrillBitBlockEntity;
import zone.moddev.mc.SteamAdvantage.blocks.PlatformBlock;
import zone.moddev.mc.SteamAdvantage.blocks.PumpShaftBlock;
import zone.moddev.mc.SteamAdvantage.items.MusketItem;
import zone.moddev.mc.SteamAdvantage.machines.MachineBlock;
import zone.moddev.mc.SteamAdvantage.machines.MachineSettings;
import zone.moddev.mc.SteamAdvantage.machines.SteamMachine;
import zone.moddev.mc.lib.api.BlockContext;
import zone.moddev.mc.lib.api.BlockImplementation;
import zone.moddev.mc.lib.api.ItemContext;
import zone.moddev.mc.lib.api.ItemImplementation;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDBlockEntity;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;

/** Public MMDLib factory contracts; all instances and settings come from standalone plans. */
final class ContentFactories {
    private ContentFactories() {}
    static void register(MaterialExtensions api) {
        api.registerBlockImplementation(
                ModContent.id("machine"), new BlockImplementation<MachineBlock>() {
                    public Class<MachineBlock> blockClass() {
                        return MachineBlock.class;
                    }
                    public MachineBlock create(MMDMaterial material, MMDBlock definition) {
                        throw new IllegalStateException("Machine requires BlockContext");
                    }
                    public MachineBlock create(BlockContext context) {
                        return new MachineBlock(context.properties(),
                                context.blockEntity().orElseThrow().as(SteamMachine.class));
                    }
                });
        block(api, "platform", PlatformBlock.class, PlatformBlock::new);
        api.registerBlockImplementation(ModContent.id("drillbit"), new BlockImplementation<DrillBitBlock>() {
            public Class<DrillBitBlock> blockClass() { return DrillBitBlock.class; }
            public DrillBitBlock create(MMDMaterial material, MMDBlock definition) {
                throw new IllegalStateException("Drill bit requires BlockContext");
            }
            public DrillBitBlock create(BlockContext context) {
                return new DrillBitBlock(context.properties(),
                        context.blockEntity().orElseThrow().as(DrillBitBlockEntity.class));
            }
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(
                ModContent.id("drillbit"), new BlockEntityFactory<DrillBitBlockEntity, Unit>() {
                    public Class<DrillBitBlockEntity> blockEntityClass() { return DrillBitBlockEntity.class; }
                    public Codec<Unit> settingsCodec() {
                        return Codec.unit(Unit.INSTANCE);
                    }
                    public DrillBitBlockEntity create(BlockEntityType<DrillBitBlockEntity> type,
                            BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                            MMDBlockEntity definition, Unit settings) {
                        return new DrillBitBlockEntity(type, pos, state);
                    }
                });
        block(api, "pump_shaft", PumpShaftBlock.class, PumpShaftBlock::new);
        api.registerItemImplementation(
                ModContent.id("musket"), new ItemImplementation<MusketItem>() {
                    public Class<MusketItem> itemClass() {
                        return MusketItem.class;
                    }
                    public MusketItem create(MMDMaterial material, MMDItem definition) {
                        throw new IllegalStateException("Musket requires ItemContext");
                    }
                    public MusketItem create(ItemContext context) {
                        return new MusketItem(context.properties());
                    }
                });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(
                ModContent.id("machine"), new BlockEntityFactory<SteamMachine, MachineSettings>() {
                    public Class<SteamMachine> blockEntityClass() {
                        return SteamMachine.class;
                    }
                    public Codec<MachineSettings> settingsCodec() {
                        return MachineSettings.CODEC;
                    }
                    public SteamMachine create(BlockEntityType<SteamMachine> type, BlockPos pos,
                            net.minecraft.world.level.block.state.BlockState state,
                            MMDBlockEntity definition, MachineSettings settings) {
                        return new SteamMachine(type, pos, state, settings);
                    }
                    public BlockEntityTicker<SteamMachine> ticker(
                            Level level, MMDBlockEntity definition, MachineSettings settings) {
                        return level.isClientSide ? null : SteamMachine::tick;
                    }
                });
    }
    private static <B extends Block> void block(MaterialExtensions api, String name, Class<B> type,
            Function<net.minecraft.world.level.block.state.BlockBehaviour.Properties, B>
                    constructor) {
        api.registerBlockImplementation(ModContent.id(name), new BlockImplementation<B>() {
            public Class<B> blockClass() {
                return type;
            }
            public B create(MMDMaterial material, MMDBlock definition) {
                throw new IllegalStateException("Requires BlockContext");
            }
            public B create(BlockContext context) {
                return constructor.apply(context.properties());
            }
        });
    }
}

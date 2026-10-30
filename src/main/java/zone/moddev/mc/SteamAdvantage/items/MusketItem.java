package zone.moddev.mc.SteamAdvantage.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import zone.moddev.mc.SteamAdvantage.SteamAdvantage;
import zone.moddev.mc.SteamAdvantage.config.SteamConfig;
import zone.moddev.mc.SteamAdvantage.init.ModContent;

/** Two-step load/fire hitscan weapon; loaded state persists in the stack's data component. */
public final class MusketItem extends Item {
    public MusketItem(Properties properties) {
        super(properties.attributes(
                net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                                new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                        Item.BASE_ATTACK_DAMAGE_ID, 2,
                                        net.minecraft.world.entity.ai.attributes.AttributeModifier
                                                .Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build()));
    }
    public static boolean loaded(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getBoolean("loaded");
    }
    private static void loaded(ItemStack stack, boolean loaded) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putBoolean("loaded", loaded));
    }
    private int enchant(ItemStack stack, Level level, String name) {
        var key = ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(SteamAdvantage.MODID, name));
        return level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(key)
                .map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack))
                .orElse(0);
    }
    private ItemStack ammo(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(ModContent.item("blackpowder_cartridge")))
                return s;
        }
        return ItemStack.EMPTY;
    }
    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SteamConfig.MUSKETS.get())
            return InteractionResultHolder.fail(stack);
        if (loaded(stack)) {
            if (!level.isClientSide)
                fire(level, player, stack, hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!player.getAbilities().instabuild && ammo(player).isEmpty()
                && enchant(stack, level, "powderless") == 0)
            return InteractionResultHolder.fail(stack);
        if (enchant(stack, level, "rapid_reload") > 0) {
            if (!level.isClientSide)
                load(level, player, stack);
        } else
            player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return SteamConfig.RELOAD.get();
    }
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CROSSBOW;
    }
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player)
            load(level, player, stack);
        return stack;
    }
    private void load(Level level, Player player, ItemStack stack) {
        ItemStack ammo = ammo(player);
        boolean free = player.getAbilities().instabuild || enchant(stack, level, "powderless") > 0;
        if (!free && ammo.isEmpty())
            return;
        if (!free)
            ammo.shrink(1);
        loaded(stack, true);
        level.playSound(
                null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, .5f, 1);
    }
    private void fire(Level level, Player player, ItemStack stack, InteractionHand hand) {
        loaded(stack, false);
        Vec3 start = player.getEyePosition(), direction = player.getLookAngle(),
             end = start.add(direction.scale(64));
        BlockHitResult block = level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double distance = start.distanceToSqr(block.getLocation());
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, start, end,
                player.getBoundingBox().expandTowards(direction.scale(64)).inflate(1),
                e -> !e.isSpectator() && e.isPickable(), distance);
        Vec3 impact = hit == null ? block.getLocation() : hit.getLocation();
        if (hit != null)
            hit.getEntity().hurt(
                    new net.minecraft.world.damagesource.DamageSource(
                            level.registryAccess()
                                    .registryOrThrow(Registries.DAMAGE_TYPE)
                                    .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,
                                            ResourceLocation.parse("steamadvantage:musket"))),
                            player, player),
                    SteamConfig.DAMAGE.get().floatValue());
        int explosive = enchant(stack, level, "high_explosive");
        if (hit == null && block.getType() == HitResult.Type.BLOCK) {
            var state = level.getBlockState(block.getBlockPos());
            if (state.is(net.minecraft.world.level.block.Blocks.TNT)) {
                net.minecraft.world.level.block.TntBlock.explode(level, block.getBlockPos());
                level.removeBlock(block.getBlockPos(), false);
            } else if (state.is(net.minecraft.tags.BlockTags.LEAVES)
                    || state.is(net.minecraft.tags.BlockTags.IMPERMEABLE))
                level.destroyBlock(block.getBlockPos(), false, player);
        }
        if (explosive > 0) {
            level.explode(player, impact.x, impact.y, impact.z, .75f * explosive,
                    Level.ExplosionInteraction.TNT);
            for (var victim : level.getEntitiesOfClass(
                         LivingEntity.class, new AABB(impact, impact).inflate(explosive)))
                victim.igniteForSeconds(explosive);
        }
        int recoil = enchant(stack, level, "recoil");
        if (recoil > 0) {
            player.push(-.6 * direction.x * recoil, -.3 * direction.y * recoil,
                    -.6 * direction.z * recoil);
            player.hurtMarked = true;
        }
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        player.getCooldowns().addCooldown(this, 10);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 2, .5f);
    }
    @Override
    public int getEnchantmentValue() {
        return 1;
    }
}

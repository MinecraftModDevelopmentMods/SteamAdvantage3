package zone.moddev.mc.SteamAdvantage.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import zone.moddev.mc.SteamAdvantage.config.SteamConfig;
import zone.moddev.mc.SteamAdvantage.init.ModContent;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;

/**
 * Steam drill: extends a drill pipe from its front, mining each block as an unenchanted diamond
 * pickaxe into its five output slots. At bedrock, the world edge or its range it retracts the
 * pipe one segment at a time, then follows an adjacent steam track if there is one. A redstone
 * signal resets it. It pushes its output out of its back every tick.
 */
public final class DrillProcessor extends SteamProcessor {
    /**
     * Maximum pipe length. SA2 used 64, which reached bedrock from sea level when the world
     * bottom was y=0; with the floor now at -64, 128 covers sea level down to bedrock.
     */
    public static final int MAX_RANGE = 128;
    private static final int OUTPUTS = 5;
    /** 0: drilling, 1: retracting, 2: fully retracted and waiting for reset or track. */
    static final int DRILLING = 0, RETRACTING = 1, RETRACTED = 2;
    private final ProcessorState.Key<Integer> depth = intVariable("depth"),
            phase = intVariable("phase"), retractTicks = intVariable("retractTicks"),
            progress = intVariable("progress");

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) {
        return true;
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        if (powered()) {
            clear();
            return ProcessState.IDLE;
        }
        for (int step = 0; step < ticks && !machine.removed(); step++)
            drill();
        return ProcessState.IDLE;
    }

    private void drill() {
        var level = machine.level();
        var pos = machine.position();
        Direction direction = front();
        // Empty the output every tick, so a full inventory can drain and mining resume.
        Transfer.pushItems(level, pos, direction.getOpposite(),
                entity().items.view(slot -> IoMode.OUTPUT), 64);
        int phase = variables.get(this.phase);
        if (phase == RETRACTING) {
            retract(direction);
            return;
        }
        if (phase == RETRACTED) {
            followTrack();
            return;
        }
        int depth = variables.get(this.depth);
        if (depth >= MAX_RANGE) {
            beginRetraction();
            return;
        }
        BlockPos target = pos.relative(direction, depth + 1);
        if (!level.isInWorldBounds(target)) {
            // Reached the top or bottom of the world: treat it like bedrock.
            beginRetraction();
            return;
        }
        if (!level.hasChunkAt(target))
            return;
        BlockState state = level.getBlockState(target);
        if (state.is(ModContent.block("drillbit"))) {
            variables.set(this.depth, depth + 1);
            return;
        }
        if (state.getDestroySpeed(level, target) < 0 || state.hasBlockEntity()) {
            beginRetraction();
            return;
        }
        // Air, fluids, plants etc. are simply displaced by the pipe rather than mined.
        boolean replace = state.canBeReplaced();
        // Steam starvation pauses the drill rather than retracting its existing line.
        if (!hasSteam(replace ? 250 : 100))
            return;
        if (!replace) {
            var items = entity().items;
            var drops = Block.getDrops(state, (ServerLevel) level, target, null, null, tool());
            // Simulate all drops together before altering the world.
            var test = new net.neoforged.neoforge.items.ItemStackHandler(OUTPUTS);
            for (int i = 0; i < OUTPUTS; i++)
                test.setStackInSlot(i, items.getStackInSlot(i).copy());
            for (ItemStack drop : drops) {
                ItemStack remaining = drop.copy();
                for (int i = 0; i < OUTPUTS; i++) remaining = test.insertItem(i, remaining, false);
                if (!remaining.isEmpty())
                    return;
            }
            // A blocked output must pause before mining consumes steam or progress.
            int ticks = breakTicks(state, level, target);
            int done = variables.get(progress);
            if (done < ticks) {
                spendSteam(100);
                variables.set(progress, ++done);
                if (done < ticks)
                    return;
            }
            if (!level.destroyBlock(target, false))
                return;
            variables.set(progress, 0);
            for (int i = 0; i < OUTPUTS; i++)
                items.setStackInSlot(i, test.getStackInSlot(i));
        } else
            spendSteam(250);
        level.setBlockAndUpdate(target, ModContent.block("drillbit").defaultBlockState()
                .setValue(BlockStateProperties.FACING, direction));
        variables.set(this.depth, depth + 1);
    }

    private void beginRetraction() {
        variables.set(phase, RETRACTING);
        variables.set(retractTicks, 0);
        variables.set(progress, 0);
        machine.level().playSound(null, machine.position(), SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.BLOCKS, .8f, .7f);
    }

    private void retract(Direction direction) {
        var level = machine.level();
        var pos = machine.position();
        int depth = variables.get(this.depth);
        if (depth > 0) {
            BlockPos tip = pos.relative(direction, depth);
            // Never load chunks or lose our place while a tip is unavailable.
            if (!level.hasChunkAt(tip))
                return;
            int waited = variables.get(retractTicks) + 1;
            variables.set(retractTicks, waited < 4 ? waited : 0);
            if (waited < 4)
                return;
            if (level.getBlockState(tip).is(ModContent.block("drillbit")))
                zone.moddev.mc.SteamAdvantage.blocks.DrillBitBlock.retractSegment(level, tip);
            variables.set(this.depth, --depth);
            level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, .5f, 1.2f);
            ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 3, .2, .1, .2, .01);
        }
        if (depth == 0) {
            variables.set(phase, RETRACTED);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, .8f, 1);
            followTrack();
        }
    }

    /** The drill mines as an unenchanted diamond pickaxe. */
    private static ItemStack tool() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }

    /**
     * Ticks to break a block, as in SA2: a configurable number of ticks per point of hardness
     * (12 by default), never less than half that.
     */
    static int breakTicks(BlockState state, Level level, BlockPos pos) {
        double factor = SteamConfig.DRILL_TICKS_PER_HARDNESS.get();
        float hardness = state.getDestroySpeed(level, pos);
        return Math.max(1, (int) Math.ceil(Math.max(factor * hardness, factor * .5)));
    }

    /** Removes the drill pipe and resets the drill to its starting state. */
    public void clear() {
        variables.update(this::clearPipe);
    }

    private void clearPipe() {
        var level = machine.level();
        var pos = machine.position();
        Direction direction = front();
        for (int i = 1; i <= MAX_RANGE; i++) {
            BlockPos p = pos.relative(direction, i);
            if (!level.hasChunkAt(p) || !level.getBlockState(p).is(ModContent.block("drillbit")))
                break;
            level.removeBlock(p, false);
        }
        variables.set(depth, 0);
        variables.set(progress, 0);
        variables.set(phase, DRILLING);
        variables.set(retractTicks, 0);
    }

    /** Moves the whole machine one block onto an adjacent steam track, leaving a pipe behind. */
    private void followTrack() {
        var level = machine.level();
        var pos = machine.position();
        if (!hasSteam(1000))
            return;
        for (Direction d : Direction.values()) {
            BlockPos target = pos.relative(d);
            if (!level.hasChunkAt(target)
                    || !level.getBlockState(target).is(ModContent.block("steam_track")))
                continue;
            spendSteam(1000);
            clearPipe();
            var self = (SteamMachineEntity) entity();
            CompoundTag saved = self.saveWithoutMetadata(level.registryAccess());
            BlockState state = machine.blockState();
            level.setBlockAndUpdate(target, state);
            if (level.getBlockEntity(target) instanceof SteamMachineEntity moved) {
                moved.loadWithComponents(saved, level.registryAccess());
                moved.setChanged();
            }
            // The contents went with the moved drill; replacing this block must not drop them.
            self.relocating = true;
            level.setBlockAndUpdate(pos, ModContent.block("steam_pipe").defaultBlockState());
            return;
        }
    }

    @Override
    void importLegacy(CompoundTag old, CompoundTag state) {
        int[] progress = old.getIntArray("Progress");
        state.putInt("depth", old.getInt("Depth"));
        state.putInt("phase", Math.clamp(old.getInt("DrillPhase"), DRILLING, RETRACTED));
        state.putInt("retractTicks", Math.clamp(old.getInt("DrillRetractionTicks"), 0, 3));
        state.putInt("progress", progress.length > 0 ? progress[0] : 0);
    }

    @Override
    protected int comparatorSignal() {
        return inventorySignal(0, OUTPUTS);
    }

    public int depth() {
        return variables.get(depth);
    }

    public int phase() {
        return variables.get(phase);
    }

    public int progress() {
        return variables.get(progress);
    }

    /** Runs one unpowered drill tick immediately; for tests. */
    public void step() {
        variables.update(this::drill);
    }

    /** Test and tooling access; outside a processor tick. */
    public void set(int depth, int phase, int progress) {
        variables.update(() -> {
            variables.set(this.depth, depth);
            variables.set(this.phase, phase);
            variables.set(this.progress, progress);
        });
    }
}

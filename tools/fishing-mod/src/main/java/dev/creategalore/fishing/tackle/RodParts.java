package dev.creategalore.fishing.tackle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The parts fitted to a rod blank, stored on the rod item. Each part is its own item stack, so it keeps its own
 * wear and data (a reel's damage, a spool's metres left) and comes back unchanged when taken off at the Tackle
 * Bench.
 *
 * <p>Immutable: every change makes a new value, so changes always reach the save (ItemStacks inside are copied in
 * and never handed out to be modified).
 */
public final class RodParts {
    public static final RodParts EMPTY = new RodParts(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

    public static final Codec<RodParts> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("reel", ItemStack.EMPTY).forGetter(RodParts::reel),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("line", ItemStack.EMPTY).forGetter(RodParts::line),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("leader", ItemStack.EMPTY).forGetter(RodParts::leader),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("hook", ItemStack.EMPTY).forGetter(RodParts::hook),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("terminal", ItemStack.EMPTY).forGetter(RodParts::terminal),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("bait", ItemStack.EMPTY).forGetter(RodParts::bait)
    ).apply(i, RodParts::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RodParts> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, RodParts::reel,
            ItemStack.OPTIONAL_STREAM_CODEC, RodParts::line,
            ItemStack.OPTIONAL_STREAM_CODEC, RodParts::leader,
            ItemStack.OPTIONAL_STREAM_CODEC, RodParts::hook,
            ItemStack.OPTIONAL_STREAM_CODEC, RodParts::terminal,
            ItemStack.OPTIONAL_STREAM_CODEC, RodParts::bait,
            RodParts::new);

    /** Slot order used by the Tackle Bench and {@link #get}/{@link #with}. */
    public enum Slot { REEL, LINE, LEADER, HOOK, TERMINAL, BAIT }

    private final ItemStack reel;
    private final ItemStack line;
    private final ItemStack leader;
    private final ItemStack hook;
    private final ItemStack terminal;
    private final ItemStack bait;

    public RodParts(ItemStack reel, ItemStack line, ItemStack leader, ItemStack hook, ItemStack terminal, ItemStack bait) {
        this.reel = reel.copy();
        this.line = line.copy();
        this.leader = leader.copy();
        this.hook = hook.copy();
        this.terminal = terminal.copy();
        this.bait = bait.copy();
    }

    // Getters hand out copies so nobody changes a stored part in place.
    public ItemStack reel() {
        return reel.copy();
    }

    public ItemStack line() {
        return line.copy();
    }

    public ItemStack leader() {
        return leader.copy();
    }

    public ItemStack hook() {
        return hook.copy();
    }

    public ItemStack terminal() {
        return terminal.copy();
    }

    public ItemStack bait() {
        return bait.copy();
    }

    public ItemStack get(Slot slot) {
        return switch (slot) {
            case REEL -> reel();
            case LINE -> line();
            case LEADER -> leader();
            case HOOK -> hook();
            case TERMINAL -> terminal();
            case BAIT -> bait();
        };
    }

    public RodParts with(Slot slot, ItemStack stack) {
        return new RodParts(slot == Slot.REEL ? stack : reel, slot == Slot.LINE ? stack : line,
                slot == Slot.LEADER ? stack : leader, slot == Slot.HOOK ? stack : hook,
                slot == Slot.TERMINAL ? stack : terminal, slot == Slot.BAIT ? stack : bait);
    }

    public boolean isEmpty() {
        return reel.isEmpty() && line.isEmpty() && leader.isEmpty() && hook.isEmpty() && terminal.isEmpty() && bait.isEmpty();
    }

    // Stacks compare by item, count and components, so equal parts mean equal components (and stacking works).
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RodParts p)) return false;
        return ItemStack.matches(reel, p.reel) && ItemStack.matches(line, p.line) && ItemStack.matches(leader, p.leader)
                && ItemStack.matches(hook, p.hook) && ItemStack.matches(terminal, p.terminal) && ItemStack.matches(bait, p.bait);
    }

    @Override
    public int hashCode() {
        return Objects.hash(h(reel), h(line), h(leader), h(hook), h(terminal), h(bait));
    }

    private static int h(ItemStack s) {
        return s.isEmpty() ? 0 : 31 * ItemStack.hashItemAndComponents(s) + s.getCount();
    }

    @Override
    public String toString() {
        return "RodParts[reel=" + reel + ", line=" + line + ", leader=" + leader + ", hook=" + hook + ", terminal="
                + terminal + ", bait=" + bait + "]";
    }
}

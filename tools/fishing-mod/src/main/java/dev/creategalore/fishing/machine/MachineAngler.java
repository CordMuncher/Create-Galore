package dev.creategalore.fishing.machine;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The fake player a machine fishes as. It owns the machine's cast hook and holds the machine's rod in its main hand
 * (the very same stack, so wear, bait and parts land on the machine's rod). It never ticks, never shows up to anyone,
 * and earns nothing itself: catches are credited to the player who set the rod up. One per machine, stood on it.
 */
public class MachineAngler extends FakePlayer {
    private final RodMount mount;

    public MachineAngler(ServerLevel level, RodMount mount) {
        super(level, profile(level, mount.mountPos()));
        this.mount = mount;
        BlockPos pos = mount.mountPos();
        setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** A stable identity per machine, so the same machine is the same angler after a reload. */
    private static GameProfile profile(ServerLevel level, BlockPos pos) {
        String key = "cgfish-machine:" + level.dimension().location() + ":" + pos.asLong();
        return new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), "[Reel Drive]");
    }

    public RodMount mount() {
        return mount;
    }

    /** Puts the machine's rod (the same stack object) in the angler's hand. */
    public void hold(ItemStack rod) {
        setItemInHand(InteractionHand.MAIN_HAND, rod);
    }
}

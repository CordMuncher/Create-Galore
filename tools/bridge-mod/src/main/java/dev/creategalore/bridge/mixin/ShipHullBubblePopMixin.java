package dev.creategalore.bridge.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BubbleColumnUpParticle;
import net.minecraft.client.particle.BubbleParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A bubble only lives while it is in water, so under a world block it pops as soon as it reaches the block. Under a
 * Sable ship it never did: the ship's blocks sit in plot space far away, the world under the hull is still sea, and
 * Sable's particle collision holds the bubble against the hull. With Make Bubbles Pop (bubbles last until they leave the
 * water, up to 16 s) breath bubbles piled up under every hull. After each move, a bubble whose top reaches a ship
 * block that holds no water, or the dry air of a ship's hold, now pops there the way it would under a world block
 * (through Make Bubbles Pop when it is installed, so its pop particle, sound and settings apply). Other particles,
 * and how bubbles collide, are left alone.
 */
@Mixin(Particle.class)
public abstract class ShipHullBubblePopMixin {
    /** Make Bubbles Pop and vanilla both look for water this far above a bubble's position. */
    @Unique
    private static final double create_galore_bridge$TOP = 0.1;
    @Unique
    private static MethodHandle create_galore_bridge$popBubble;
    @Unique
    private static boolean create_galore_bridge$lookedUpPop;

    @Shadow
    @Final
    protected ClientLevel level;
    @Shadow
    public double x;
    @Shadow
    public double y;
    @Shadow
    public double z;
    @Shadow
    protected double xd;
    @Shadow
    protected double yd;
    @Shadow
    protected double zd;

    @Shadow
    public abstract boolean isAlive();

    @Shadow
    public abstract void remove();

    @Inject(method = "move", at = @At("TAIL"))
    private void create_galore_bridge$popOnShipHull(double dx, double dy, double dz, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof BubbleParticle) && !(self instanceof BubbleColumnUpParticle) || !isAlive()) return;
        if (!create_galore_bridge$touchesShip()) return;
        remove();
        create_galore_bridge$pop((SingleQuadParticle) self);
    }

    @Unique
    private boolean create_galore_bridge$touchesShip() {
        Vec3 top = new Vec3(this.x, this.y + create_galore_bridge$TOP, this.z);
        BoundingBox3d near = new BoundingBox3d(top.x - 0.5, top.y - 0.5, top.z - 0.5, top.x + 0.5, top.y + 0.5, top.z + 0.5);
        Vector3d local = new Vector3d();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (SubLevel ship : Sable.HELPER.getAllIntersecting(this.level, near)) {
            if (ship.isRemoved()) continue;
            ship.logicalPose().transformPositionInverse(local.set(top.x, top.y, top.z));
            BlockState state = this.level.getBlockState(pos.set(local.x, local.y, local.z));
            if (!state.isAir() && !state.getFluidState().is(FluidTags.WATER)) return true;
        }
        // Inside a ship's dry hold the world still has sea, but the ship keeps it out (Sable drops floating motes there too).
        WaterOcclusionContainer<?> dry = WaterOcclusionContainer.getContainer(this.level);
        return dry != null && dry.isOccluded(top);
    }

    @Unique
    private void create_galore_bridge$pop(SingleQuadParticle bubble) {
        if (!create_galore_bridge$lookedUpPop) {
            create_galore_bridge$lookedUpPop = true;
            try {
                Class<?> util = Class.forName("net.tschipcraft.make_bubbles_pop.impl.BubbleUtil");
                create_galore_bridge$popBubble = MethodHandles.publicLookup().findStatic(util, "popBubble", MethodType.methodType(void.class,
                        Level.class, double.class, double.class, double.class, double.class, double.class, double.class, float.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                create_galore_bridge$popBubble = null;
            }
        }
        if (create_galore_bridge$popBubble == null) return; // vanilla bubbles just vanish when they leave the water
        try {
            create_galore_bridge$popBubble.invokeExact((Level) this.level, this.x, this.y, this.z, this.xd, this.yd, this.zd, bubble.getQuadSize(0.0F));
        } catch (Throwable e) {
            create_galore_bridge$popBubble = null;
        }
    }
}

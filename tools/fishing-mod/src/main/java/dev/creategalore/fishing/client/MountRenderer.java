package dev.creategalore.fishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.machine.MachineInfo;
import dev.creategalore.fishing.machine.MountedRod;
import dev.creategalore.fishing.machine.RodMount;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.TackleManager;
import dev.creategalore.fishing.tackle.TacklePart;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Draws the rod a machine holds: the same 3D rod as in the hand, set in the machine at 45 degrees over the way it
 * faces, bending toward the line under load, its reel turning while it winds; and a landed fish left hanging at the
 * tip. It records where each tip was drawn so the line ({@link CastHookRenderer}) starts there.
 */
class MountRenderer<T extends BlockEntity & RodMount> implements BlockEntityRenderer<T> {
    /** Where machines' rod tips were last drawn, in world space, and in which frame. */
    private record Tip(Vec3 world, long frame) {
    }

    private static final Map<BlockPos, Tip> TIPS = new HashMap<>();
    private static final Map<BlockPos, float[]> SPRINGS = new HashMap<>();
    private static final long START = System.nanoTime();
    private static long frame;

    /** Where the tip of the rod in the machine at {@code pos} was drawn lately, if it was. */
    static Optional<Vec3> tip(BlockPos pos) {
        Tip tip = TIPS.get(pos);
        return tip == null || frame - tip.frame() > 2 ? Optional.empty() : Optional.of(tip.world());
    }

    /** Called once a frame (from the line renderer), so recorded tips can tell when they are stale. */
    static void nextFrame() {
        frame++;
    }

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderRod(be, partialTick, pose, buffers, light);
    }

    /** Draws the rod (and a hanging fish); shared with the drives' renderers, which draw their shafts too. */
    static <T extends BlockEntity & RodMount> void renderRod(T be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                                            int light) {
        MountedRod mounted = be.mounted();
        ItemStack stack = mounted.rod();
        Optional<TacklePart.Rod> blank = TackleManager.of(stack, TacklePart.Rod.class);
        if (blank.isEmpty()) return;
        RodParts parts = RodItem.parts(stack);
        RodModel.Build build = new RodModel.Build(blank.get(), TackleManager.of(parts.reel(), TacklePart.Reel.class),
                TackleManager.of(parts.line(), TacklePart.Line.class).map(l -> RodModel.color(l.color())));
        CastHook hook = MachineInfo.clientHook(be.getBlockPos());
        Vector3f origin = pose.last().pose().getTranslation(new Vector3f());

        pose.pushPose();
        Vec3 base = be.rodBase();
        pose.translate(base.x, base.y, base.z);
        pose.mulPose(Axis.YP.rotationDegrees(-be.rodFacing().toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(45));
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        RodModel.Pose state = bend(be.getBlockPos(), build, hook, pose, partialTick);
        RodModel.Result result = new RodModel(buffers, light).draw(pose, build, state);
        pose.popPose();

        Vector3f tip = result.tip();
        // A shader pack's shadow pass draws the rod again from the sun: only the player's view says where the tip is.
        if (!ShaderPass.shadow()) {
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            TIPS.put(be.getBlockPos().immutable(), new Tip(cam.add(tip.x, tip.y, tip.z), frame));
        }

        ItemStack fish = mounted.dangling();
        if (!fish.isEmpty()) {
            // Hung from the tip by a short piece of line, head up, swinging a little.
            Vector3f local = new Vector3f(tip).sub(origin);
            float clock = be.getLevel().getGameTime() + partialTick;
            double cm = FishItemRenderer.lengthCm(fish);
            float length = Mth.clamp((float) cm / 100, 0.15f, 1.6f);
            float drop = 0.35f;
            int color = build.lineColor().orElse(RodModel.NO_LINE);
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            PoseStack.Pose p = pose.last();
            lines.addVertex(p, local.x, local.y, local.z).setColor(color).setNormal(p, 0, -1, 0);
            lines.addVertex(p, local.x, local.y - drop, local.z).setColor(color).setNormal(p, 0, -1, 0);
            pose.pushPose();
            pose.translate(local.x, local.y - drop - length / 2, local.z);
            pose.mulPose(Axis.YP.rotation(Mth.sin(clock * 0.07f) * 0.6f));
            pose.mulPose(Axis.ZP.rotationDegrees(90 + Mth.sin(clock * 0.31f) * 12));
            FishView.draw(fish, length * 100, FishItemRenderer.drawnKg(fish, cm, length * 100), pose, buffers, light, clock * 0.4f);
            pose.popPose();
        }
    }

    /**
     * This frame's bend: toward the line, more the harder it pulls (as {@code RodItemRenderer} does for a rod in the
     * hand), sprung so the tip bucks and settles. The reel turns while the machine winds.
     */
    private static RodModel.Pose bend(BlockPos pos, RodModel.Build build, CastHook hook, PoseStack pose, float partialTick) {
        // Times are seconds since the game started: a float can't hold System.nanoTime() to the frame.
        float now = (System.nanoTime() - START) / 1e9f;
        float[] s = SPRINGS.computeIfAbsent(pos.immutable(), k -> new float[]{0, 0, now, 1, 0, 0, 0});
        // A shader pack's shadow pass draws the rod again from the sun: show it as it is, without stepping the springs.
        if (ShaderPass.shadow()) return pose(s);
        float dt = Mth.clamp(now - s[2], 0, 0.05f);
        s[2] = now;
        float target = 0;
        float depth = 0;
        if (hook != null) {
            float load = switch (hook.phase()) {
                case FIGHTING -> hook.shownRodLoad();
                case BITE -> 0.12f + 0.1f * Mth.sin(hook.tickCount * 1.7f);
                case WAITING -> 0.02f;
                default -> 0;
            };
            Vec3 from = tip(pos).orElse(Vec3.atCenterOf(pos));
            Vec3 d = hook.getPosition(partialTick).subtract(from);
            if (d.lengthSqr() > 1e-4) {
                Vector3f toFish = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
                new Matrix3f(pose.last().normal()).transpose().transform(toFish).normalize();
                Vector3f axis = new Vector3f(toFish.z, 0, -toFish.x);
                if (axis.lengthSquared() > 1e-5f) {
                    axis.normalize();
                    s[4] = axis.x;
                    s[5] = axis.z;
                    float share = 1 - (float) Math.exp(-3.4f * Mth.clamp(load, 0, 1.5f));
                    target = Math.min(2.8f, (float) Math.acos(Mth.clamp(toFish.y, -1, 1)) * share * 0.97f);
                    depth = share;
                }
            }
            if (hook.flag(CastHook.FLAG_REELING) && !hook.flag(CastHook.FLAG_STALLED)) s[6] = (s[6] + dt * Mth.TWO_PI * 2) % Mth.TWO_PI;
        }
        s[1] += (140 * (target - s[0]) - 15 * s[1]) * dt;
        s[0] += s[1] * dt;
        s[3] += (depth - s[3]) * Math.min(1, dt * 6);
        return pose(s);
    }

    private static RodModel.Pose pose(float[] s) {
        Vector3f axis = new Vector3f(s[4], 0, s[5]);
        if (axis.lengthSquared() < 1e-5f) axis.set(1, 0, 0);
        return new RodModel.Pose(Math.max(0, s[0]), axis.normalize(), 1, s[6], s[3]);
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(3);
    }
}

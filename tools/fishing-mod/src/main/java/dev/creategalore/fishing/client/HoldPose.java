package dev.creategalore.fishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.cast.CatchHolding;
import dev.creategalore.fishing.fish.CatchInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;

/**
 * An angler holding up a landed fish, as everyone sees it (the {@link CatchHolding#HELD} attachment is synced to
 * everyone nearby): a small fish hangs from one hand by its mouth, a fish of {@link CatchHolding#TWO_HANDS_KG} or
 * more is held across both hands, side on, the classic catch photo. It kicks now and then. The rod is put away
 * meanwhile (not drawn).
 *
 * <p>Third person: the arms are posed by {@code PlayerModelHoldMixin} calling {@link #pose} and the fish is drawn by
 * a player render layer. First person: the hands are replaced in {@link RenderHandEvent}.
 */
public final class HoldPose {
    /**
     * Two-handed hold in third person: arm angles (radians) and where the fish rests in front of the chest (model
     * pixels, y down, -z forward). Mutable only through the debug command ({@code /cgfishc holdpose}), for tuning.
     */
    static final float[] TWO_HANDS = {-1.15f, 0.08f, 6.2f, -9.4f};

    private HoldPose() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.AddLayers event) -> {
            for (PlayerSkin.Model skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new Layer(renderer));
            }
        });
        NeoForge.EVENT_BUS.addListener(HoldPose::firstPerson);
    }

    static boolean holding(Player player) {
        return player.getData(CatchHolding.HELD).holding();
    }

    private static double lengthCm(CatchHolding.Held held) {
        if (held.lengthCm() > 0) return held.lengthCm();
        CatchInfo info = held.fish().get(FishingRegistry.CATCH.get());
        return info == null ? 30 : info.cm().orElse(30.0);
    }

    /** A kick every couple of seconds, strongest at its start. */
    private static float kick(float age) {
        float phase = Mth.positiveModulo(age, 47) / 47;
        return phase < 0.2f ? Mth.sin(phase * 5 * Mth.TWO_PI * 2) * (1 - phase * 5) : 0;
    }

    /**
     * Arm poses for a player holding a fish, or lifting their rod (the rod arm comes up with the rod, as everyone else
     * sees it in third person); called after vanilla set up the model.
     */
    public static void pose(PlayerModel<?> model, LivingEntity entity, float age) {
        if (!(entity instanceof Player player)) return;
        if (!holding(player)) {
            liftArm(model, player);
            return;
        }
        CatchHolding.Held held = player.getData(CatchHolding.HELD);
        float k = kick(age) * 0.08f;
        if (held.twoHands()) {
            // Both forearms out under the fish, hands apart: one under the head, one toward the tail.
            model.rightArm.xRot = TWO_HANDS[0] + k;
            model.rightArm.yRot = -TWO_HANDS[1];
            model.rightArm.zRot = 0;
            model.leftArm.xRot = TWO_HANDS[0] - k;
            model.leftArm.yRot = TWO_HANDS[1];
            model.leftArm.zRot = 0;
        } else {
            boolean right = player.getMainArm() == HumanoidArm.RIGHT;
            ModelPart arm = right ? model.rightArm : model.leftArm;
            arm.xRot = -2.05f + k;
            arm.yRot = right ? -0.25f : 0.25f;
            arm.zRot = 0;
        }
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    private static void liftArm(PlayerModel<?> model, Player player) {
        if (!(player.fishing instanceof CastHook hook) || hook.isRemoved()) return;
        float h = hook.rodHeight();
        if (h <= 0) return;
        boolean rightHand = (hook.hand() == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
        ModelPart arm = rightHand ? model.rightArm : model.leftArm;
        arm.xRot -= 0.9f * h * h * (3 - 2 * h);
        (rightHand ? model.rightSleeve : model.leftSleeve).copyFrom(arm);
    }

    /** Draws the held fish on other players (and on yourself in third person). */
    static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw,
                           float headPitch) {
            CatchHolding.Held held = player.getData(CatchHolding.HELD);
            if (!held.holding()) return;
            double cm = lengthCm(held);
            float length = (float) Mth.clamp(cm / 100, 0.08, 4);
            PlayerModel<AbstractClientPlayer> model = getParentModel();
            pose.pushPose();
            if (held.twoHands()) {
                // Model space is y-down and faces -Z: resting on the hands, out in front of the forearms so the arms
                // don't hide it.
                model.body.translateAndRotate(pose);
                pose.translate(0, TWO_HANDS[2] / 16, TWO_HANDS[3] / 16);
                pose.mulPose(Axis.ZP.rotationDegrees(180));
                pose.mulPose(Axis.ZP.rotationDegrees(kick(age) * 6));
            } else {
                boolean right = player.getMainArm() == HumanoidArm.RIGHT;
                ModelPart arm = right ? model.rightArm : model.leftArm;
                arm.translateAndRotate(pose);
                pose.translate((right ? -1 : 1) / 16f, 10.5f / 16, 0);
                // Back to the body's orientation, so the fish hangs straight down from the hand.
                pose.mulPose(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).invert());
                pose.mulPose(Axis.ZP.rotationDegrees(180));
                pose.translate(0, -length / 2 - 0.02f, 0);
                pose.mulPose(Axis.ZP.rotationDegrees(90 + kick(age) * 12));
            }
            FishView.draw(held.fish(), cm, held.kg(), pose, buffers, light, age);
            pose.popPose();
        }
    }

    /** First person: your hands hold the fish up in front of you instead of the rod. */
    private static void firstPerson(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !holding(mc.player)) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        CatchHolding.Held held = mc.player.getData(CatchHolding.HELD);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float age = mc.player.tickCount + event.getPartialTick();
        double cm = lengthCm(held);
        float length = (float) Mth.clamp(cm / 100, 0.08, 4);
        boolean right = mc.player.getMainArm() == HumanoidArm.RIGHT;
        float side = right ? 1 : -1;
        float k = kick(age);

        if (held.twoHands()) {
            arm(pose, buffers, light, mc.player, HumanoidArm.RIGHT, 0.26f, k * 0.02f);
            arm(pose, buffers, light, mc.player, HumanoidArm.LEFT, 0.26f, -k * 0.02f);
            pose.pushPose();
            // Across the view, side on, held up clear of the hotbar; further away for a big fish so it fits.
            pose.translate(0, -0.2f - length * 0.05f, -0.75f - length * 0.35f);
            pose.mulPose(Axis.ZP.rotationDegrees(side * 4 + k * 5));
            pose.mulPose(Axis.YP.rotationDegrees(side * 12));
            FishView.draw(held.fish(), cm, held.kg(), pose, buffers, light, age);
            pose.popPose();
        } else {
            arm(pose, buffers, light, mc.player, right ? HumanoidArm.RIGHT : HumanoidArm.LEFT, 0.2f, k * 0.02f);
            pose.pushPose();
            // Held up toward the middle of the view, close enough to look at.
            pose.translate(side * 0.3f, -0.02f, -0.62f);
            pose.translate(0, -length / 2, 0);
            pose.mulPose(Axis.YP.rotationDegrees(side * 25));
            pose.mulPose(Axis.ZP.rotationDegrees(90 + k * 12));
            FishView.draw(held.fish(), cm, held.kg(), pose, buffers, light, age);
            pose.popPose();
        }
    }

    /** An empty first-person arm (vanilla's placement), raised by {@code lift}. */
    private static void arm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                            HumanoidArm side, float lift, float shake) {
        float f = side == HumanoidArm.RIGHT ? 1 : -1;
        pose.pushPose();
        pose.translate(f * 0.64f, -0.6f + lift + shake, -0.72f);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45));
        pose.translate(f * -1.0f, 3.6f, 3.5f);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120));
        pose.mulPose(Axis.XP.rotationDegrees(200));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135));
        pose.translate(f * 5.6f, 0, 0);
        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (side == HumanoidArm.RIGHT) renderer.renderRightHand(pose, buffers, light, player);
        else renderer.renderLeftHand(pose, buffers, light, player);
        pose.popPose();
    }
}

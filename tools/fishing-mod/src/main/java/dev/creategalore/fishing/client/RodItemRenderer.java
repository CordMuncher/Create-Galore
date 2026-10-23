package dev.creategalore.fishing.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.cast.RodLift;
import dev.creategalore.fishing.sim.Fight;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.TackleManager;
import dev.creategalore.fishing.tackle.TacklePart;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Rods in the hands are drawn as {@link RodModel}s: held at the reel seat, bent toward the fish by the load on the
 * line (springy, so it bucks with the fish), telescopic sections sliding out while the line is out, the crank
 * turning while reeling, pulled back while a cast is charged, and the rig dangling from the tip when nothing is
 * cast. In the inventory, on the ground and in frames the rod keeps its flat sprite.
 *
 * <p>Each frame it also records where every rod tip was drawn ({@link #tip}), so the line starts at the tip you can
 * see instead of an estimate.
 */
public final class RodItemRenderer extends BlockEntityWithoutLevelRenderer {
    /**
     * Placement in the hand, first and third person: translation (blocks), rotation about X, Y, Z (degrees), scale.
     * Mirrored for the left hand. Mutable only through the debug command, for tuning.
     */
    static final float[] FIRST_PERSON = {0.02f, 0.25f, -0.12f, -62, 20, -8, 0.8f};
    static final float[] THIRD_PERSON = {0, 0.02f, -0.05f, 18, 0, -15, 0.85f};
    /** Debug: force the bend load (NaN = off). */
    static float debugLoad = Float.NaN;
    private static final boolean DEBUG = Boolean.getBoolean("cgfish.debug");
    private static long lastLog;

    private static final float DANGLE = 0.32f;
    private static RodItemRenderer instance;
    /** The living entity being rendered right now (for third-person items, which aren't told whose hand). */
    private static Entity rendering;

    private static final Map<Long, Motion> MOTION = new HashMap<>();
    private static final Map<Long, Tip> TIPS = new HashMap<>();

    private RodItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterClientExtensionsEvent event) -> event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (instance == null) instance = new RodItemRenderer();
                return instance;
            }
        }, rods()));
        modBus.addListener((ModelEvent.ModifyBakingResult event) -> {
            for (Item item : rods()) {
                ModelResourceLocation key = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item));
                BakedModel model = event.getModels().get(key);
                if (model != null) event.getModels().put(key, new InHand(model));
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderLivingEvent.Pre<?, ?> event) -> rendering = event.getEntity());
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Post<?, ?> event) -> rendering = null);
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> frame++);
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) worldProjection = event.getProjectionMatrix().m11();
        });
    }

    private static Item[] rods() {
        return BuiltInRegistries.ITEM.stream().filter(i -> i instanceof RodItem).toArray(Item[]::new);
    }

    private static boolean inHand(ItemDisplayContext context) {
        return context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    /** The rod's item model: its sprite, except in the hands where this renderer draws it. */
    static final class InHand extends BakedModelWrapper<BakedModel> {
        private final BakedModel custom;

        InHand(BakedModel sprite) {
            super(sprite);
            custom = new BakedModelWrapper<>(sprite) {
                @Override
                public boolean isCustomRenderer() {
                    return true;
                }

                @Override
                public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(BlockState state, Direction side,
                                                                                        RandomSource rand) {
                    return List.of();
                }

                @Override
                public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(BlockState state, Direction side,
                                                                                        RandomSource rand, ModelData data,
                                                                                        RenderType renderType) {
                    return List.of();
                }

                @Override
                public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
                    return this;
                }
            };
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            return inHand(context) ? custom : originalModel.applyTransform(context, pose, leftHand);
        }
    }

    /** Spring state of one rod in one hand, kept between frames. */
    private static final class Motion {
        float bend;
        float bendSpeed;
        final Vector3f axis = new Vector3f(1, 0, 0);
        float extension;
        float crank;
        float depth;
        float lift;
        long lastNanos = System.nanoTime();
    }

    /**
     * Where a rod tip was drawn: for first person in view space (it moves with the camera) with the hand's
     * projection scale, else as an offset from the holder's position.
     */
    private record Tip(boolean firstPerson, Vector3f point, float handProjection, int frame) {
    }

    /** The world pass's projection (its m11), recorded each frame; NaN until the first frame. */
    private static float worldProjection = Float.NaN;

    /** Frames rendered so far; a tip is fresh if drawn in the last couple of frames, however slow they are. */
    private static int frame;

    private static long key(Entity entity, InteractionHand hand) {
        return ((long) entity.getId() << 1) | hand.ordinal();
    }

    /** Where the tip of {@code holder}'s rod in {@code hand} was last drawn, in world space, if it's fresh. */
    public static Optional<Vec3> tip(Player holder, InteractionHand hand, float partialTick) {
        Tip tip = TIPS.get(key(holder, hand));
        if (tip == null || frame - tip.frame() > 2) return Optional.empty();
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (tip.firstPerson()) {
            if (!mc.options.getCameraType().isFirstPerson() || mc.getCameraEntity() != holder) return Optional.empty();
            // The world (and the line) is drawn before the hand, so this is last frame's tip, kept in view space so
            // it follows the camera. The hand is projected with its own field of view: scale the point across the
            // view so it lands on the same pixel in the world's projection.
            // The world's projection as the main pass set it: a shader pack's shadow pass draws the line too, with its
            // own projection current.
            float world = Float.isNaN(worldProjection) ? RenderSystem.getProjectionMatrix().m11() : worldProjection;
            float k = tip.handProjection() / world;
            Vector3f view = new Vector3f(tip.point()).mul(k, k, 1);
            camera.rotation().transform(view);
            return Optional.of(camera.getPosition().add(view.x, view.y, view.z));
        }
        return Optional.of(holder.getPosition(partialTick).add(tip.point().x, tip.point().y, tip.point().z));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        Optional<TacklePart.Rod> blank = TackleManager.of(stack, TacklePart.Rod.class);
        if (blank.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean first = context.firstPerson();
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        Entity holder = first ? mc.getCameraEntity() : rendering;
        Player player = holder instanceof Player p ? p : null;
        // Hands full with a fish: the rod is put down meanwhile.
        if (player != null && HoldPose.holding(player)) return;
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (player != null) {
            HumanoidArm arm = left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            hand = player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        }
        CastHook hook = player != null && player.fishing instanceof CastHook h && h.hand() == hand && !h.isRemoved() ? h : null;

        RodParts parts = RodItem.parts(stack);
        RodModel.Build build = new RodModel.Build(blank.get(), TackleManager.of(parts.reel(), TacklePart.Reel.class),
                TackleManager.of(parts.line(), TacklePart.Line.class).map(l -> RodModel.color(l.color())));

        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        boolean charging = player != null && RodItem.charging(player) && player.getUsedItemHand() == hand;
        float charge = charging ? Math.min(1, (player.getTicksUsingItem() + partialTick) / RodItem.FULL_CHARGE_TICKS) : 0;

        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        float[] t = first ? FIRST_PERSON : THIRD_PERSON;
        float m = left ? -1 : 1;
        pose.translate(m * t[0], t[1], t[2]);
        pose.mulPose(Axis.YP.rotationDegrees(m * t[4]));
        // Charging a cast draws the rod back over the shoulder.
        pose.mulPose(Axis.XP.rotationDegrees(t[3] + 38 * charge));
        pose.mulPose(Axis.ZP.rotationDegrees(m * t[5]));
        float scale = t[6] / 16;
        pose.scale(scale, scale, scale);

        Motion motion = player == null ? null : MOTION.computeIfAbsent(key(player, hand), k -> new Motion());
        if (motion != null) {
            // Lifted (attack held, see FishingInput): up by as much as CastHook.ROD_LIFT, as fast as the fight lets it.
            float dt = Mth.clamp((System.nanoTime() - motion.lastNanos) / 1e9f, 0, 0.05f);
            motion.lift = liftHeight(motion.lift, hook, player, dt);
            float eased = motion.lift * motion.lift * (3 - 2 * motion.lift);
            // In third person the arm comes up with it (HoldPose.liftArm, about 52 degrees); the rest is the wrist.
            pose.mulPose(Axis.XP.rotationDegrees((first ? CastHook.ROD_LIFT : CastHook.ROD_LIFT - 52) * eased));
        }
        RodModel.Pose state = motion == null ? RodModel.Pose.REST : animate(motion, pose, build, hook, charging, first, player, hand, partialTick);
        RodModel.Result result = new RodModel(buffers, light).draw(pose, build, state);
        pose.popPose();

        // The tip is where the player sees it; a shadow pass draws the rod again from the sun.
        if (player != null && !ShaderPass.shadow()) {
            Vector3f point = result.tip();
            if (first) {
                // Keep the tip in view space. Vanilla draws the hand camera-relative with world axes (the inverse camera
                // rotation is in the pose stack, the model-view matrix turns it back); a shader pack draws it straight
                // in view space with an identity model-view. Either way the model-view matrix takes it to view space.
                Vector3f view = RenderSystem.getModelViewMatrix().transformPosition(new Vector3f(point));
                TIPS.put(key(player, hand), new Tip(true, view, RenderSystem.getProjectionMatrix().m11(), frame));
            } else {
                Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
                Vec3 at = player.getPosition(partialTick);
                TIPS.put(key(player, hand), new Tip(false, new Vector3f((float) (cam.x + point.x - at.x),
                        (float) (cam.y + point.y - at.y), (float) (cam.z + point.z - at.z)), 0, frame));
            }
        }
        if (hook == null) dangle(pose, buffers, light, parts, result.tip(), first, build);
    }

    /**
     * This frame's rod height. Your own rod follows your input at once, at the fight's lift speed for the load on it
     * ({@link Fight#liftRate}), and is pulled gently toward the server's height so the two never drift apart; other
     * anglers' rods follow the server's height.
     */
    private static float liftHeight(float current, CastHook hook, Player player, float dt) {
        if (hook == null) return Math.max(0, current - (float) Fight.LOWER_RATE * dt);
        float server = hook.rodHeight();
        if (player != Minecraft.getInstance().player) return current + (server - current) * Math.min(1, dt * 12);
        double load = hook.phase() == CastHook.Phase.FIGHTING ? hook.shownRodLoad() : 0;
        float local = RodLift.lifting(player) ? current + (float) Fight.liftRate(load) * dt
                : current - (float) (hook.flag(CastHook.FLAG_REELING) && hook.phase() == CastHook.Phase.FIGHTING
                        ? Math.min(Fight.LOWER_RATE, 0.5) : Fight.LOWER_RATE) * dt;
        local += (server - local) * Math.min(1, dt * 2);
        return Mth.clamp(local, 0, 1);
    }

    /** Works out this frame's bend, extension and crank angle, springing toward where the line pulls. */
    private static RodModel.Pose animate(Motion motion, PoseStack pose, RodModel.Build build, CastHook hook,
                                         boolean charging, boolean first, Player player, InteractionHand hand,
                                         float partialTick) {
        // A shader pack's shadow pass draws the rod again from the sun: show it as it is, without stepping the springs.
        if (ShaderPass.shadow()) {
            return new RodModel.Pose(Math.max(0, motion.bend), new Vector3f(motion.axis), motion.extension, motion.crank,
                    motion.depth);
        }
        long now = System.nanoTime();
        float dt = Mth.clamp((now - motion.lastNanos) / 1e9f, 0, 0.05f);
        motion.lastNanos = now;

        float load = 0;
        Vector3f toFish = null;
        if (hook != null) {
            load = switch (hook.phase()) {
                case FIGHTING -> hook.shownRodLoad();
                case BITE -> 0.12f + 0.1f * Mth.sin(hook.tickCount * 1.7f);
                case WAITING -> hook.flag(CastHook.FLAG_REELING) ? 0.06f : 0.02f;
                default -> 0;
            };
            Vec3 from = tip(player, hand, partialTick).orElse(player.getEyePosition(partialTick));
            Vec3 d = hook.getPosition(partialTick).subtract(from);
            if (d.lengthSqr() > 1e-4) {
                toFish = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
                // Into the rod's own frame. The camera takes it to view space; the model-view matrix maps the pose
                // stack's root to view space (vanilla's root has world axes and the camera's rotation in the
                // model-view; a shader pack draws the first-person hand with its root already in view space); the
                // pose's own rotation maps the rod's frame to the root.
                Minecraft.getInstance().gameRenderer.getMainCamera().rotation().conjugate(new Quaternionf()).transform(toFish);
                new Matrix3f(RenderSystem.getModelViewMatrix()).invert().transform(toFish);
                new Matrix3f(pose.last().normal()).transpose().transform(toFish).normalize();
            }
        }
        if (!Float.isNaN(debugLoad)) {
            load = debugLoad;
            if (toFish == null) toFish = new Vector3f(0, 0.3f, -1).normalize();
        }

        float target = 0;
        float depthTarget = 0;
        if (toFish != null) {
            Vector3f axis = new Vector3f(toFish.z, 0, -toFish.x);
            if (axis.lengthSquared() > 1e-5f) {
                axis.normalize();
                motion.axis.lerp(axis, Math.min(1, dt * 12)).normalize();
                // A loaded rod curls until its tip lines up with the line; how far it gets there rises fast with the
                // load (rods are very bendy and take huge bends without breaking: a quarter of the rated load already
                // brings the tip most of the way round). So holding the rod high or away from the fish, the line
                // meets the blank at a wide angle and the blank curls into a deep U; pointing it at the fish, there
                // is nothing to bend toward. Softer blanks give in sooner.
                float give = switch (build.rod().action()) {
                    case FAST -> 6.5f;
                    case MODERATE -> 7.5f;
                    case SLOW -> 9f;
                };
                float share = 1 - (float) Math.exp(-give * Mth.clamp(load, 0, 1.5f));
                float towardLine = (float) Math.acos(Mth.clamp(toFish.y, -1, 1));
                target = Math.min(3.0f, towardLine * share * 0.97f);
                depthTarget = share;
            }
        }
        if (DEBUG && hook != null && now - lastLog > 2_000_000_000L) {
            lastLog = now;
            CreateGaloreFishing.LOG.info("[cgfish] rod {}: phase {} load {} toFish(local) {} target {} bend {} first {}",
                    hand, hook.phase(), load, toFish, target, motion.bend, first);
        }
        // A spring a little under critical damping: the tip bucks and settles.
        float stiffness = 140;
        float damping = 15;
        motion.bendSpeed += (stiffness * (target - motion.bend) - damping * motion.bendSpeed) * dt;
        motion.bend += motion.bendSpeed * dt;

        boolean out = hook != null || charging;
        motion.extension = Mth.clamp(motion.extension + (out ? 4f : -2.5f) * dt, 0, 1);
        if (!build.rod().telescopic()) motion.extension = 1;

        if (hook != null && hook.flag(CastHook.FLAG_REELING) && !hook.flag(CastHook.FLAG_STALLED)) {
            float heavy = hook.phase() == CastHook.Phase.FIGHTING ? Mth.clamp(hook.shownTension(), 0, 1) : 0;
            motion.crank += dt * Mth.TWO_PI * 2.2f * (1.05f - 0.5f * heavy);
            motion.crank %= Mth.TWO_PI;
        }
        motion.depth += (depthTarget - motion.depth) * Math.min(1, dt * 6);
        return new RodModel.Pose(Math.max(0, motion.bend), new Vector3f(motion.axis), motion.extension, motion.crank,
                motion.depth);
    }

    /** The rig hanging a little way below the tip while nothing is cast: float, sinker, lure or a bare hook. */
    private static void dangle(PoseStack pose, MultiBufferSource buffers, int light, RodParts parts, Vector3f tip,
                               boolean first, RodModel.Build build) {
        ItemStack terminal = parts.terminal();
        ItemStack hookItem = parts.hook();
        if (terminal.isEmpty() && hookItem.isEmpty()) return;
        // The pose stack's root is world-aligned in both views, so down is down.
        Vector3f down = new Vector3f(0, -1, 0);
        Vector3f end = new Vector3f(down).mul(DANGLE).add(tip);
        pose.pushPose();
        pose.last().pose().identity();
        pose.last().normal().identity();
        int lineColor = build.lineColor().orElse(RodModel.NO_LINE);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose root = pose.last();
        lines.addVertex(root, tip.x, tip.y, tip.z).setColor(lineColor).setNormal(root, down.x, down.y, down.z);
        lines.addVertex(root, end.x, end.y, end.z).setColor(lineColor).setNormal(root, down.x, down.y, down.z);
        pose.translate(end.x, end.y, end.z);
        float s = (first ? 0.9f : 0.85f) / 16;
        pose.scale(s, s, s);
        RodModel model = new RodModel(buffers, light).material(RodModel.Mat.PAINT);
        Optional<TacklePart> part = TackleManager.of(terminal);
        if (part.isPresent() && part.get() instanceof TacklePart.Bobber bobber) {
            int c = RodModel.color(bobber.color());
            model.box(pose, -0.7f, -0.2f, -0.7f, 0.7f, 1.2f, 0.7f, 0xFFF0F0F0);
            model.box(pose, -0.7f, -1.6f, -0.7f, 0.7f, -0.2f, 0.7f, c);
            model.box(pose, -0.15f, 1.2f, -0.15f, 0.15f, 2.0f, 0.15f, c);
        } else if (part.isPresent() && part.get() instanceof TacklePart.Sinker) {
            model.box(pose, -0.55f, -1.1f, -0.55f, 0.55f, 0, 0.55f, 0xFF6A6C70);
        } else if (part.isPresent() && part.get() instanceof TacklePart.Lure lure) {
            int c = RodModel.color(lure.color());
            model.box(pose, -0.4f, -2.2f, -0.6f, 0.4f, 0, 0.6f, c);
            model.box(pose, -0.1f, -2.9f, -0.1f, 0.1f, -2.2f, 0.1f, 0xFF9A9A9A);
        } else {
            model.box(pose, -0.1f, -1.0f, -0.1f, 0.1f, 0, 0.1f, 0xFF9A9A9A);
            model.box(pose, -0.1f, -1.2f, -0.1f, 0.6f, -1.0f, 0.1f, 0xFF9A9A9A);
        }
        pose.popPose();
    }
}

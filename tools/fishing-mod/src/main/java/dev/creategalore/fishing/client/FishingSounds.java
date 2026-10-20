package dev.creategalore.fishing.client;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The fight's continuous sounds, worked out on each client from the hooks' synced state so they follow the action
 * exactly and cost no network traffic:
 * <ul>
 *   <li>drag ratchet: one click per {@link #CM_PER_CLICK} cm of line actually going out while the drag slips, so the
 *       click rate is the fish's run speed; pitched by the reel (copper rattly, brass smooth, big-game deep);</li>
 *   <li>reel crank: a looping whirr while reeling, slower and lower as the load rises;</li>
 *   <li>grind: cranking against a slipping drag;</li>
 *   <li>line strain: creaks that start at about 65% of the breaking load and come faster and louder toward a snap.</li>
 * </ul>
 * One-off sounds (cast, splash, bite, strike, snap) are played by the server.
 */
public final class FishingSounds {
    private static final double CM_PER_CLICK = 12;
    private static final double HEAR_DISTANCE = 48;

    private static final class State {
        float lastLineOut = -1;
        double payout;
        Crank crank;
        int strainCooldown;
        int grindCooldown;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    private FishingSounds() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) {
            if (level == null) clear();
            return;
        }
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof CastHook hook) || e.distanceTo(mc.player) > HEAR_DISTANCE) continue;
            Player owner = hook.getPlayerOwner();
            if (owner == null) continue;
            State s = STATES.computeIfAbsent(hook.getId(), id -> new State());
            sounds(mc, hook, owner, s);
        }
        for (Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, State> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            if (!(entity instanceof CastHook) || entity.isRemoved()) {
                if (e.getValue().crank != null) e.getValue().crank.finish();
                it.remove();
            }
        }
    }

    private static void clear() {
        STATES.values().forEach(s -> {
            if (s.crank != null) s.crank.finish();
        });
        STATES.clear();
    }

    private static void sounds(Minecraft mc, CastHook hook, Player owner, State s) {
        RandomSource random = owner.getRandom();
        float out = hook.lineOut();
        boolean fighting = hook.phase() == CastHook.Phase.FIGHTING;
        boolean slipping = fighting && hook.flag(CastHook.FLAG_SLIPPING);
        boolean reeling = hook.flag(CastHook.FLAG_REELING);
        float reelPitch = reelPitch(owner.getItemInHand(hook.hand()));

        if (s.lastLineOut >= 0 && slipping) {
            s.payout += Math.max(0, out - s.lastLineOut) * 100;
            int clicks = 0;
            while (s.payout >= CM_PER_CLICK && clicks < 4) {
                s.payout -= CM_PER_CLICK;
                clicks++;
                play(owner, FishingRegistry.DRAG_CLICK.get(), 0.45f, reelPitch * (0.92f + random.nextFloat() * 0.16f));
            }
            if (s.payout > CM_PER_CLICK * 4) s.payout = 0;
        } else {
            s.payout = 0;
        }
        s.lastLineOut = out;

        boolean cranking = reeling && hook.phase() != CastHook.Phase.FLYING;
        if (cranking && (s.crank == null || s.crank.isStopped())) {
            s.crank = new Crank(owner, hook, reelPitch);
            mc.getSoundManager().play(s.crank);
        } else if (!cranking && s.crank != null) {
            s.crank.finish();
            s.crank = null;
        }

        if (s.grindCooldown > 0) s.grindCooldown--;
        if (reeling && slipping && s.grindCooldown == 0) {
            play(owner, FishingRegistry.REEL_GRIND.get(), 0.5f, reelPitch * 0.9f);
            s.grindCooldown = 8;
        }

        if (s.strainCooldown > 0) s.strainCooldown--;
        float tension = fighting ? hook.tension() : 0;
        if (tension > 0.65f && s.strainCooldown == 0) {
            float over = Math.min(1, (tension - 0.65f) / 0.35f);
            play(owner, FishingRegistry.LINE_STRAIN.get(), 0.25f + 0.55f * over, 0.9f + 0.35f * over);
            s.strainCooldown = (int) (16 - 11 * over) + random.nextInt(4);
        }
    }

    private static void play(Player at, SoundEvent sound, float volume, float pitch) {
        at.level().playLocalSound(at.getX(), at.getEyeY() - 0.4, at.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    /** Copper reels rattle high, brass purrs, big-game reels are deep. */
    static float reelPitch(ItemStack rod) {
        if (!(rod.getItem() instanceof RodItem)) return 1;
        ItemStack reel = RodItem.parts(rod).get(RodParts.Slot.REEL);
        if (reel.isEmpty()) return 1.1f;
        return switch (BuiltInRegistries.ITEM.getKey(reel.getItem()).getPath()) {
            case "copper_reel" -> 1.2f;
            case "brass_reel" -> 0.95f;
            case "steel_baitcaster" -> 1.05f;
            case "trolling_reel" -> 0.72f;
            default -> 1.0f;
        };
    }

    /** The looping crank whirr that follows the angler while they reel; heavier load, slower and lower. */
    static final class Crank extends AbstractTickableSoundInstance {
        private final Player owner;
        private final CastHook hook;
        private final float basePitch;

        Crank(Player owner, CastHook hook, float basePitch) {
            super(FishingRegistry.REEL_CRANK.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.owner = owner;
            this.hook = hook;
            this.basePitch = basePitch;
            looping = true;
            delay = 0;
            volume = 0.35f;
            pitch = basePitch;
            x = owner.getX();
            y = owner.getEyeY() - 0.4;
            z = owner.getZ();
        }

        @Override
        public void tick() {
            if (owner.isRemoved() || hook.isRemoved()) {
                stop();
                return;
            }
            x = owner.getX();
            y = owner.getEyeY() - 0.4;
            z = owner.getZ();
            float load = hook.phase() == CastHook.Phase.FIGHTING ? Math.min(1, hook.tension()) : 0;
            boolean stalled = hook.flag(CastHook.FLAG_STALLED);
            pitch = basePitch * (stalled ? 0.6f : 1.05f - 0.35f * load);
            volume = 0.3f + 0.2f * load;
        }

        void finish() {
            stop();
        }
    }
}

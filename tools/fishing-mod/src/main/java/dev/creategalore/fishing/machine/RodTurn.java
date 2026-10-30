package dev.creategalore.fishing.machine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * The rod in a machine turning on its mount, from one pose to another over a few ticks. A pose is a yaw from the way
 * the machine faces (degrees, clockwise seen from above, as {@code Direction.toYRot}) and the rod's rise over the
 * horizontal (degrees). The server sets it and clients draw it from the same numbers, so both agree on where the rod
 * points at any game time. Everything is in the machine's own block space, so on a ship the rod turns with the hull.
 */
public record RodTurn(float fromYaw, float fromRise, float toYaw, float toRise, long start, int ticks) {
    /** How the rod sits when it fishes: over the way the machine faces, 45 degrees up. */
    public static final float REST_RISE = 45;
    public static final RodTurn REST = new RodTurn(0, REST_RISE, 0, REST_RISE, 0, 0);
    /** Degrees of yaw the rod turns in a tick at speed 1 (a half turn takes a second). */
    static final float YAW_PER_TICK = 9;
    /** Degrees of rise it changes in a tick at speed 1. */
    static final float RISE_PER_TICK = 4;

    /** 0 to 1 through the turn at {@code time} (game ticks, with the partial tick on the client), eased at both ends. */
    private float progress(double time) {
        if (ticks <= 0) return 1;
        float t = (float) Mth.clamp((time - start) / ticks, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public float yaw(double time) {
        return Mth.lerp(progress(time), fromYaw, toYaw);
    }

    public float rise(double time) {
        return Mth.lerp(progress(time), fromRise, toRise);
    }

    public boolean done(long time) {
        return time >= start + ticks;
    }

    public long end() {
        return start + ticks;
    }

    public boolean atRest() {
        return Math.abs(Mth.wrapDegrees(toYaw)) < 0.01f && Math.abs(toRise - REST_RISE) < 0.01f;
    }

    /**
     * A turn from where the rod is at {@code now} to {@code yaw} and {@code rise}, the short way round, at
     * {@code speed} (1 for a drive in gear 1.0, see {@link RodMount#turnSpeed}).
     */
    public RodTurn to(long now, float yaw, float rise, double speed) {
        float fy = yaw(now);
        float fr = rise(now);
        float delta = Mth.wrapDegrees(yaw - fy);
        // A half turn either way: always swing the same way, so it doesn't look undecided.
        if (Math.abs(delta) >= 179.9f) delta = 180;
        double s = Math.max(0.05, speed);
        int t = (int) Math.ceil(Math.max(Math.abs(delta) / (YAW_PER_TICK * s), Math.abs(rise - fr) / (RISE_PER_TICK * s)));
        return new RodTurn(fy, fr, fy + delta, rise, now, t);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("from_yaw", fromYaw);
        tag.putFloat("from_rise", fromRise);
        tag.putFloat("to_yaw", toYaw);
        tag.putFloat("to_rise", toRise);
        tag.putLong("start", start);
        tag.putInt("ticks", ticks);
        return tag;
    }

    public static RodTurn load(CompoundTag tag) {
        if (tag.isEmpty()) return REST;
        return new RodTurn(tag.getFloat("from_yaw"), tag.getFloat("from_rise"), tag.getFloat("to_yaw"), tag.getFloat("to_rise"),
                tag.getLong("start"), tag.getInt("ticks"));
    }
}

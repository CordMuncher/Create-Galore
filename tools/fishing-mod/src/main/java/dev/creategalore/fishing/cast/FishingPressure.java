package dev.creategalore.fishing.cast;

import dev.creategalore.fishing.CreateGaloreFishing;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * How hard each spot has been fished lately. Every fish landed adds pressure to its chunk; pressure fades by
 * half every {@link #HALF_LIFE_TICKS}. Bites come slower in pressured water, so camping one spot with a dozen reel
 * drives yields less and less, and moving on (or trolling) pays.
 */
public final class FishingPressure extends SavedData {
    /** Pressure halves every 10 in-game minutes. */
    static final long HALF_LIFE_TICKS = 20 * 60 * 10;
    private static final String NAME = CreateGaloreFishing.ID + "_pressure";

    private final Long2DoubleOpenHashMap pressure = new Long2DoubleOpenHashMap();
    private final Long2LongOpenHashMap since = new Long2LongOpenHashMap();

    public static FishingPressure of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(FishingPressure::new, FishingPressure::load), NAME);
    }

    /** Pressure at {@code pos} now (0 = untouched). */
    public double at(ServerLevel level, BlockPos pos) {
        long key = ChunkPos.asLong(pos);
        double p = pressure.get(key);
        if (p <= 0) return 0;
        long elapsed = level.getGameTime() - since.get(key);
        return p * Math.pow(0.5, elapsed / (double) HALF_LIFE_TICKS);
    }

    /** Bite rate multiplier at {@code pos}: 1 untouched, 0.5 after four quick catches, never below 0.2. */
    public double biteFactor(ServerLevel level, BlockPos pos) {
        return Math.max(0.2, 1 / (1 + 0.25 * at(level, pos)));
    }

    public void addCatch(ServerLevel level, BlockPos pos) {
        long key = ChunkPos.asLong(pos);
        pressure.put(key, at(level, pos) + 1);
        since.put(key, level.getGameTime());
        // Forget spots that have recovered, so the save stays small.
        if (pressure.size() > 512) {
            pressure.keySet().removeIf(k -> pressure.get(k) * Math.pow(0.5, (level.getGameTime() - since.get(k))
                    / (double) HALF_LIFE_TICKS) < 0.05);
            since.keySet().retainAll(pressure.keySet());
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        pressure.long2DoubleEntrySet().forEach(e -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("chunk", e.getLongKey());
            entry.putDouble("pressure", e.getDoubleValue());
            entry.putLong("since", since.get(e.getLongKey()));
            list.add(entry);
        });
        tag.put("spots", list);
        return tag;
    }

    private static FishingPressure load(CompoundTag tag, HolderLookup.Provider registries) {
        FishingPressure data = new FishingPressure();
        for (Tag t : tag.getList("spots", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            data.pressure.put(entry.getLong("chunk"), entry.getDouble("pressure"));
            data.since.put(entry.getLong("chunk"), entry.getLong("since"));
        }
        return data;
    }
}

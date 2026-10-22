package dev.creategalore.bridge;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Before 0.4.16 the Physics Assembler glued the sea into ships: every water (or lava) block inside a ship's super glue or
 * honey glue came along as a floating block (see SimAssemblyNoFluidsMixin), and before 0.4.22 a waterlogged block built
 * in the sea came aboard with its water (see SableAssemblyWaterloggedMixin). That water also flowed out across the ship
 * as thin sheets, past the glue. Ships assembled since can't carry any, so each older ship is cleaned once, the first
 * time it is loaded: every loose fluid block on it is removed and every waterlogged block is dried. Ships cleaned by
 * 0.4.16, which only removed the water inside the glue, get this second pass too. Ships assembled or split from now on
 * are marked clean as they appear, so water put on a ship later, or Deep Seas' flooding of a holed hull, is never
 * touched.
 */
public final class ShipGluedWater {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL = 20;
    // 1: the glue-only clean of 0.4.16. 2: every loose fluid and waterlogged block (0.4.22).
    private static final int CURRENT = 2;

    private ShipGluedWater() {
    }

    public static void register() {
        if (!ModList.get().isLoaded("sable") || !ModList.get().isLoaded("simulated")) return;
        NeoForge.EVENT_BUS.addListener(ShipGluedWater::onServerTick);
    }

    /** A ship that comes out of assembly or a split now holds no sea water: never clean it. */
    public static void markClean(ServerLevel level, UUID ship) {
        Cleaned.get(level).set(ship, CURRENT);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL != 0) return;
        Cleaned cleaned = Cleaned.get(event.getServer().overworld());
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) continue;
            for (SubLevel ship : container.getAllSubLevels()) {
                if (ship.isRemoved() || cleaned.version(ship.getUniqueId()) >= CURRENT) continue;
                LevelPlot plot = ship.getPlot();
                if (plot == null || !loaded(level, plot)) continue;
                int[] counts = clean(level, plot);
                cleaned.set(ship.getUniqueId(), CURRENT);
                if (counts[0] + counts[1] > 0) {
                    LOGGER.info("Removed {} water blocks from ship {} and dried {} waterlogged blocks", counts[0], ship.getUniqueId(), counts[1]);
                }
            }
        }
    }

    // Wait for the plot's blocks and entities (the glue) before deciding: a ship looked at too early would be marked
    // clean with its water still on board.
    private static boolean loaded(ServerLevel level, LevelPlot plot) {
        BoundingBox3ic b = plot.getBoundingBox();
        for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
            for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                if (plot.getChunk(plot.toLocal(chunk)) == null || !level.areEntitiesLoaded(chunk.toLong())) return false;
            }
        }
        return true;
    }

    // Returns {fluid blocks removed, waterlogged blocks dried}. No neighbour updates: the water next to each one is going
    // too, and nothing on the ship should flow.
    private static int[] clean(ServerLevel level, LevelPlot plot) {
        BoundingBox3ic b = plot.getBoundingBox();
        int removed = 0, dried = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    p.set(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (state.getBlock() instanceof LiquidBlock) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        removed++;
                    } else if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
                        level.setBlock(p, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        dried++;
                    }
                }
            }
        }
        return new int[] {removed, dried};
    }

    /** How far each ship has been cleaned (or that it was assembled clean), kept with the world. */
    private static final class Cleaned extends SavedData {
        private static final String NAME = "create_galore_ship_glued_water";
        private final Map<UUID, Integer> ships = new HashMap<>();

        static Cleaned get(ServerLevel level) {
            return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(Cleaned::new, Cleaned::load, null), NAME);
        }

        private static Cleaned load(CompoundTag tag, HolderLookup.Provider registries) {
            Cleaned data = new Cleaned();
            // 0.4.16 kept a plain list of the ships it had cleaned.
            for (Tag t : tag.getList("ships", Tag.TAG_STRING)) {
                data.ships.put(UUID.fromString(t.getAsString()), 1);
            }
            CompoundTag versions = tag.getCompound("versions");
            for (String id : versions.getAllKeys()) {
                data.ships.put(UUID.fromString(id), versions.getInt(id));
            }
            return data;
        }

        int version(UUID ship) {
            return ships.getOrDefault(ship, 0);
        }

        void set(UUID ship, int version) {
            Integer old = ships.put(ship, version);
            if (old == null || old != version) setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag versions = new CompoundTag();
            ListTag list = new ListTag();
            ships.forEach((id, version) -> {
                versions.putInt(id.toString(), version);
                list.add(StringTag.valueOf(id.toString()));
            });
            tag.put("versions", versions);
            // The 0.4.16 list too, so a server moved back to an older pack doesn't clean these ships again.
            tag.put("ships", list);
            return tag;
        }
    }
}

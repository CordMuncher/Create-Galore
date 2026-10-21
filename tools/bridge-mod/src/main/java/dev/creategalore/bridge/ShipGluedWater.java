package dev.creategalore.bridge;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Before 0.4.14 the Physics Assembler glued the sea into ships: every water (or lava) block inside a ship's super glue or
 * honey glue came along as a floating block (see SimAssemblyNoFluidsMixin). Ships assembled since then can't carry any,
 * so each older ship is cleaned once, the first time it is loaded: plain fluid blocks inside its glue are removed.
 * Waterlogged blocks are untouched. Ships assembled or split from now on are marked clean as they appear, so water put
 * on a ship later, or Deep Seas' flooding of a holed hull, is never touched.
 */
public final class ShipGluedWater {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL = 20;

    private ShipGluedWater() {
    }

    public static void register() {
        if (!ModList.get().isLoaded("sable") || !ModList.get().isLoaded("simulated")) return;
        NeoForge.EVENT_BUS.addListener(ShipGluedWater::onServerTick);
    }

    /** A ship that comes out of assembly or a split now holds no glued water: never clean it. */
    public static void markClean(ServerLevel level, UUID ship) {
        Cleaned.get(level).add(ship);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL != 0) return;
        Cleaned cleaned = Cleaned.get(event.getServer().overworld());
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) continue;
            for (SubLevel ship : container.getAllSubLevels()) {
                if (ship.isRemoved() || cleaned.contains(ship.getUniqueId())) continue;
                LevelPlot plot = ship.getPlot();
                if (plot == null || !loaded(level, plot)) continue;
                int removed = clean(level, plot);
                cleaned.add(ship.getUniqueId());
                if (removed > 0) {
                    LOGGER.info("Removed {} glued water blocks from ship {}", removed, ship.getUniqueId());
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

    private static int clean(ServerLevel level, LevelPlot plot) {
        BoundingBox3ic b = plot.getBoundingBox();
        AABB area = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        List<Entity> glues = new ArrayList<>(level.getEntitiesOfClass(SuperGlueEntity.class, area.inflate(1)));
        glues.addAll(level.getEntitiesOfClass(HoneyGlueEntity.class, area.inflate(1)));
        int removed = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    p.set(x, y, z);
                    if (!(level.getBlockState(p).getBlock() instanceof LiquidBlock) || !glued(glues, p)) continue;
                    // No neighbour updates: the water next to it is going too, and nothing on the ship should flow.
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    removed++;
                }
            }
        }
        return removed;
    }

    private static boolean glued(List<Entity> glues, BlockPos pos) {
        for (Entity glue : glues) {
            if (glue instanceof SuperGlueEntity s ? s.contains(pos) : ((HoneyGlueEntity) glue).contains(pos)) return true;
        }
        return false;
    }

    /** Ships already cleaned (or assembled clean), kept with the world. */
    private static final class Cleaned extends SavedData {
        private static final String NAME = "create_galore_ship_glued_water";
        private final Set<UUID> ships = new HashSet<>();

        static Cleaned get(ServerLevel level) {
            return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(Cleaned::new, Cleaned::load, null), NAME);
        }

        private static Cleaned load(CompoundTag tag, HolderLookup.Provider registries) {
            Cleaned data = new Cleaned();
            for (Tag t : tag.getList("ships", Tag.TAG_STRING)) {
                data.ships.add(UUID.fromString(t.getAsString()));
            }
            return data;
        }

        boolean contains(UUID ship) {
            return ships.contains(ship);
        }

        void add(UUID ship) {
            if (ships.add(ship)) setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            ships.forEach(id -> list.add(StringTag.valueOf(id.toString())));
            tag.put("ships", list);
            return tag;
        }
    }
}

package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.TraderMapSearch;
import java.util.ArrayList;
import java.util.List;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.entities.SoaringTrader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * See {@link TraderMapSearch}. The Soaring Trader's other offers are made at once as before; its structure map (same
 * price, same structures, same nearest find) joins the end of the list once the search has found one. A player already
 * looking at the trades sees it appear; adding it at the end keeps the trades they see where they were.
 */
@Mixin(value = SoaringTrader.class, remap = false)
public abstract class DiscoveryTraderMapMixin {
    @Shadow
    private int angryTicks;

    @Unique
    private BlockPos create_galore_bridge$found;

    @Shadow
    private void syncOffers() {}

    @Shadow
    private ItemStack tryStructure(ServerLevel level, Registry<Structure> registry, ChunkGeneratorStructureState state, long seed, String id) {
        return null;
    }

    @WrapOperation(
        method = "updateTrades",
        at = @At(value = "INVOKE", target = "Lme/corvino/aeronauticsdiscovery/entities/SoaringTrader;buildMapTrade()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack create_galore_bridge$searchLater(SoaringTrader self, Operation<ItemStack> original) {
        if (!(self.level() instanceof ServerLevel level)) return original.call(self);
        List<String> ids = new ArrayList<>(Config.traderStructureMaps);
        if (ids.isEmpty()) return null;
        RandomSource random = self.getRandom();
        if (Config.traderGuaranteedMap) {
            // Discovery's own shuffle: every kind in random order, the first one found wins.
            for (int i = ids.size() - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                String tmp = ids.get(i);
                ids.set(i, ids.get(j));
                ids.set(j, tmp);
            }
        } else {
            ids = List.of(ids.get(random.nextInt(ids.size())));
        }
        new TraderMapSearch(level, self, ids, (id, pos) -> create_galore_bridge$addMap(level, id, pos)).start();
        return null;
    }

    /** While the search's find is handed over, Discovery's map maker uses it instead of searching again. */
    @WrapOperation(
        method = "tryStructure",
        at = @At(value = "INVOKE", target = "Lme/corvino/aeronauticsdiscovery/util/StructureSearchWorker;searchNearest(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/levelgen/structure/Structure;Lnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;JLnet/minecraft/core/BlockPos;II)Lnet/minecraft/core/BlockPos;"))
    private BlockPos create_galore_bridge$useFound(ServerLevel level, Structure structure, RandomSpreadStructurePlacement placement, long seed,
                                                   BlockPos origin, int maxChunkRadius, int maxChecks, Operation<BlockPos> original) {
        BlockPos found = create_galore_bridge$found;
        return found != null ? found : original.call(level, structure, placement, seed, origin, maxChunkRadius, maxChecks);
    }

    @Unique
    private void create_galore_bridge$addMap(ServerLevel level, String id, BlockPos pos) {
        SoaringTrader self = (SoaringTrader) (Object) this;
        if (self.isRemoved()) return;
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        ItemStack map;
        create_galore_bridge$found = pos;
        try {
            map = tryStructure(level, level.registryAccess().registryOrThrow(Registries.STRUCTURE), state, state.getLevelSeed(), id);
        } finally {
            create_galore_bridge$found = null;
        }
        if (map == null) return;
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD, 5), map, 1, 0, 0.0F);
        // An angry trader doubles every price, this one included.
        if (angryTicks > 0) offer.setSpecialPriceDiff(offer.getItemCostA().count());
        self.getOffers().add(offer);
        syncOffers();
    }
}

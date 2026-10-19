package dev.creategalore.bridge.mixin;

import com.li64.tide.data.rods.CustomRodManager;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hybrid Aquatic's Ominous Hook (summons the Karkinos boss) and CreeperMagnet Hook only work on the vanilla bobber,
 * which no rod in the pack casts any more since Tide took over the fishing rod. Tide's Angling Table accepts both hooks
 * but ignored them. Do what Hybrid Aquatic does: a catch on such a hook pulls the creature out of the water at the
 * player instead of the catch, and the single-use hook breaks.
 */
@Mixin(targets = "com.li64.tide.registries.entities.misc.fishing.TideFishingHook", remap = false)
public abstract class TideSummoningHookMixin {
    @Shadow private int nibble;
    @Shadow private Entity hookedIn;
    @Shadow protected List<ItemStack> hookedItems;

    @Inject(method = "retrieve(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;)I", at = @At("HEAD"))
    private void create_galore_bridge$summoningHook(ItemStack rod, ServerLevel level, Player player, CallbackInfoReturnable<Integer> cir) {
        if (level == null || player == null || nibble <= 0 || hookedIn != null || hookedItems == null || hookedItems.isEmpty()) return;
        ItemStack hook = CustomRodManager.getHook(rod);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(hook.getItem());
        if (!"hybrid_aquatic".equals(id.getNamespace())) return;
        EntityType<?> type = switch (id.getPath()) {
            case "ominous_hook" -> BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("hybrid_aquatic", "karkinos"));
            case "creepermagnet_hook" -> EntityType.CREEPER;
            default -> null;
        };
        if (type == null) return;

        Entity self = (Entity) (Object) this;
        Entity summoned = type.spawn(level, self.blockPosition(), MobSpawnType.MOB_SUMMONED);
        if (summoned != null) {
            Vec3 toPlayer = player.position().subtract(self.position());
            double lift = Math.sqrt(toPlayer.length()) * 0.08;
            summoned.setDeltaMovement(toPlayer.x * 0.15, toPlayer.y * 0.15 + lift, toPlayer.z * 0.15);
        }
        hookedItems = List.of();

        ItemStack worn = hook.copy();
        worn.setDamageValue(worn.getDamageValue() + 1);
        if (worn.isDamageableItem() && worn.getDamageValue() >= worn.getMaxDamage()) {
            CustomRodManager.setHook(rod, ItemStack.EMPTY);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
        } else if (worn.isDamageableItem()) {
            CustomRodManager.setHook(rod, worn);
        }
    }
}

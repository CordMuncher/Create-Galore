package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.client.PatPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A right-click on a mob while PatPat's pat keys (which include the use key) are held: a mob with a Shift +
 * right-click interaction of its own gets it first, with either hand, and is patted only when neither hand's
 * interaction did anything; any other mob is patted straight away and its plain right-click is skipped. See
 * {@link PatPriority}.
 */
@Mixin(Minecraft.class)
public abstract class PatUseItemMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void create_galore_bridge$patClickStart(CallbackInfo ci) {
        PatPriority.clickStart();
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;interactAt(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/EntityHitResult;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult create_galore_bridge$patInteractAt(MultiPlayerGameMode mode, Player player, Entity target,
            EntityHitResult hit, InteractionHand hand, Operation<InteractionResult> original) {
        if (hand == InteractionHand.MAIN_HAND && PatPriority.loaded()) {
            LivingEntity patted = PatPriority.patTarget(target);
            if (patted != null && !PatPriority.hasSneakInteraction(target)) return InteractionResult.PASS;
        }
        InteractionResult result = original.call(mode, player, target, hit, hand);
        if (result.consumesAction()) PatPriority.interactionTaken();
        return result;
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult create_galore_bridge$patInteract(MultiPlayerGameMode mode, Player player, Entity target,
            InteractionHand hand, Operation<InteractionResult> original) {
        LivingEntity patted = null;
        if (hand == InteractionHand.MAIN_HAND && PatPriority.loaded()) {
            patted = PatPriority.patTarget(target);
            if (patted != null && !PatPriority.hasSneakInteraction(target)) {
                // No Shift + right-click of its own: the pat wins, as PatPat does on its own (no hand swing: PatPat has its own).
                PatPriority.pat(patted);
                return InteractionResult.CONSUME;
            }
        }
        InteractionResult result = original.call(mode, player, target, hand);
        if (result.consumesAction()) PatPriority.interactionTaken();
        else if (patted != null) PatPriority.setPending(patted);
        return result;
    }

    /** Reached only when no hand did anything with the click. */
    @Inject(method = "startUseItem", at = @At("TAIL"))
    private void create_galore_bridge$patClickEnd(CallbackInfo ci) {
        PatPriority.clickEnd();
    }
}

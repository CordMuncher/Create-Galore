/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.mixin;

import dev.creategalore.fishing.tide.registries.TideItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ProjectileWeaponItem.class)
public class ProjectileWeaponItemMixin {

    @Inject(at = @At("HEAD"), method = "draw", cancellable = true)
    private static void draw(ItemStack weapon, ItemStack ammo, LivingEntity shooter, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (weapon.is(TideItems.STARLIGHT_BOW) && shooter.getRandom().nextFloat() < 0.6f) cir.setReturnValue(List.of(Items.ARROW.getDefaultInstance()));
    }

}
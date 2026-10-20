/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.mixin;

import dev.creategalore.fishing.tide.data.DoubleJumper;
import dev.creategalore.fishing.tide.registries.TideParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
public class PlayerMixin implements DoubleJumper {
    @Unique private boolean hasJump = true;
    @Unique private boolean wasJumpPressed = false;

    @Override
    public boolean tide$hasDoubleJump() {
        return this.hasJump;
    }

    @Override
    public void tide$doDoubleJump() {
        Player player = (Player)(Object)this;
        if (this.wasJumpPressed || !this.hasJump) return;
        if (player.onGround() || player.getDeltaMovement().y() > 0) return;
        if (player.isCreative() || player.getAbilities().flying) return;
        if (player.isFallFlying()) return;
        player.jumpFromGround();
        player.level().addParticle(TideParticleTypes.VOID_RIPPLE_LARGE,
                player.getX(), player.getY() + 0.2, player.getZ(),
                0, 0, 0);
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS,
                0.3f, 1.25f, false);
        this.hasJump = false;
    }

    @Override
    public void tide$restoreDoubleJump() {
        this.hasJump = true;
    }

    @Override
    public void tide$setWasJumpPressed(boolean jumpPressed) {
        this.wasJumpPressed = jumpPressed;
    }
}

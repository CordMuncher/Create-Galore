package dev.creategalore.dlsstestkit;

import net.minecraft.client.Minecraft;

/**
 * The held item, for the "hand" viewpoint: the hand shows (and the HUD with it) holding a sword, nothing else changes.
 * With the "rise" path it shows whether the light on a held item holds while the player moves fast without moving
 * their arm (flying); the "ride" viewpoints (see RideScene) do the same standing on a sailing ship.
 */
final class HandScene {
    static final String NAME = "hand";

    private HandScene() {}

    static void enter() {
        Minecraft.getInstance().options.hideGui = false;
        Capture.server("item replace entity @a weapon.mainhand with minecraft:iron_sword");
    }

    static void leave() {
        Minecraft.getInstance().options.hideGui = true;
        Capture.server("item replace entity @a weapon.mainhand with air");
    }
}

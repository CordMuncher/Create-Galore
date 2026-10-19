package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.AirshipCaptains;
import dev.thebluetaco.hostileskies.raid.RaidManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Hostile Skies says "Captain was killed!" whatever killed the captain; say so only when a player did (see AirshipCaptains). */
@Mixin(value = RaidManager.class, remap = false)
public abstract class AirshipCaptainAnnounceMixin {
    @ModifyConstant(method = "beginEmergencyDeparture", constant = @Constant(stringValue = "§4§lCaptain was killed! §cThe ship is going down!"))
    private static String create_galore_bridge$accident(String message) {
        return AirshipCaptains.lastDeathCredited ? message : AirshipCaptains.ACCIDENT;
    }
}

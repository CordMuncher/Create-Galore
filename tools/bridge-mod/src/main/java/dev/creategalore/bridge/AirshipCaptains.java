package dev.creategalore.bridge;

import com.mojang.authlib.GameProfile;
import dev.thebluetaco.hostileskies.raid.RaidSavedData;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Hostile Skies airship captains. When a captain dies of anything (a fall, suffocation, its own ship breaking up) the
 * mod tells everyone near the ship "Captain was killed!", which reads as if the players did it. Its kill count, which
 * unlocks the bigger ship tiers, goes to whoever last hit the captain, even if that hit was long before the death,
 * and a Create deployer's fake player counts as a player.
 *
 * Here a captain death counts for a player only when a real player hurt it in the last 30 seconds, directly or through
 * anything the game credits to them (arrows, tridents, other projectiles they own). That player gets the kill and the
 * Captain's Orders even when the captain then fell or burned to death. Any other death counts for nobody and the ship
 * announces an accident instead. {@code /hostileskies resetkills <players>} (op) clears a player's kill count, which
 * the mod itself has no command for.
 */
public final class AirshipCaptains {
    private static final String CAPTAIN = "hostile_skies_captain";
    private static final String ACTIVATED = "hostile_skies_activated_captain";
    private static final String HIT_BY = "create_galore_bridge:captain_hit_by";
    private static final String HIT_AT = "create_galore_bridge:captain_hit_at";
    private static final long WINDOW = 30 * 20;
    public static final String ACCIDENT = "§6§lThe Captain died in an accident! §eThe ship is going down!";

    /** Whether the captain death being handled right now counts for a player; read by the announcement mixin. */
    public static boolean lastDeathCredited = true;

    private AirshipCaptains() {
    }

    public static void register() {
        if (!ModList.get().isLoaded("hostile_skies")) return;
        NeoForge.EVENT_BUS.addListener(AirshipCaptains::onDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, AirshipCaptains::onDeath);
        NeoForge.EVENT_BUS.addListener(AirshipCaptains::onCommands);
    }

    private static boolean isCaptain(Mob mob) {
        return mob.getTags().contains(CAPTAIN) || mob.getTags().contains(ACTIVATED);
    }

    private static ServerPlayer realPlayer(DamageSource source) {
        return source.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) ? p : null;
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob captain) || captain.level().isClientSide || !isCaptain(captain)) return;
        ServerPlayer player = realPlayer(event.getSource());
        if (player == null || event.getNewDamage() <= 0) return;
        CompoundTag data = captain.getPersistentData();
        data.putUUID(HIT_BY, player.getUUID());
        data.putLong(HIT_AT, captain.level().getGameTime());
    }

    /** Runs before Hostile Skies' own death handler and sets the captain up so that it credits the right player. */
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob captain) || !(captain.level() instanceof ServerLevel level) || !isCaptain(captain)) return;
        ServerPlayer credited = credited(captain, level, event.getSource());
        lastDeathCredited = credited != null;
        if (credited == null) {
            // Hostile Skies only counts activated captains; an unactivated one still sends the ship down.
            if (captain.removeTag(ACTIVATED)) captain.addTag(CAPTAIN);
            captain.setLastHurtByMob(null);
            return;
        }
        if (captain.removeTag(CAPTAIN)) captain.addTag(ACTIVATED);
        if (!(event.getSource().getEntity() instanceof Player)) captain.setLastHurtByMob(credited);
    }

    private static ServerPlayer credited(Mob captain, ServerLevel level, DamageSource source) {
        ServerPlayer killer = realPlayer(source);
        if (killer != null) return killer;
        CompoundTag data = captain.getPersistentData();
        if (!data.hasUUID(HIT_BY) || level.getGameTime() - data.getLong(HIT_AT) > WINDOW) return null;
        ServerPlayer hitter = level.getServer().getPlayerList().getPlayer(data.getUUID(HIT_BY));
        return hitter != null && hitter.level() == level ? hitter : null;
    }

    private static void onCommands(RegisterCommandsEvent event) {
        // Brigadier merges this into Hostile Skies' own /hostileskies command.
        event.getDispatcher().register(Commands.literal("hostileskies").then(Commands.literal("resetkills")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("players", GameProfileArgument.gameProfile()).executes(ctx -> {
                    Collection<GameProfile> players = GameProfileArgument.getGameProfiles(ctx, "players");
                    RaidSavedData data = RaidSavedData.get(ctx.getSource().getServer());
                    for (GameProfile profile : players) {
                        UUID id = profile.getId();
                        data.resetCaptainKills(id);
                    }
                    String names = String.join(", ", players.stream().map(GameProfile::getName).toList());
                    ctx.getSource().sendSuccess(() -> Component.literal("Airship captain kills reset to 0 for " + names), true);
                    return players.size();
                }))));
    }
}

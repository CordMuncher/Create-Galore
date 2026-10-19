package dev.creategalore.lagwatch;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dedicated-server lag alarm. Says in chat when TPS stays low, records a spark profile to a file,
 * and writes a snapshot (players, entities, chunks) to logs/lagwatch.log. Single long freezes get the
 * server thread's stack written there too. Never loads on a client.
 */
@Mod(value = LagWatch.ID, dist = Dist.DEDICATED_SERVER)
public final class LagWatch {
    public static final String ID = "create_galore_lagwatch";

    static final ModConfigSpec SPEC;
    static final ModConfigSpec.BooleanValue ENABLED;
    static final ModConfigSpec.DoubleValue ALERT_TPS;
    static final ModConfigSpec.DoubleValue RECOVER_TPS;
    static final ModConfigSpec.IntValue ALERT_AFTER_SECONDS;
    static final ModConfigSpec.IntValue STARTUP_GRACE_SECONDS;
    static final ModConfigSpec.BooleanValue TELL_EVERYONE;
    static final ModConfigSpec.BooleanValue PROFILE;
    static final ModConfigSpec.IntValue PROFILE_SECONDS;
    static final ModConfigSpec.IntValue PROFILE_ONLY_TICKS_OVER_MS;
    static final ModConfigSpec.IntValue PROFILE_COOLDOWN_MINUTES;
    static final ModConfigSpec.IntValue KEEP_PROFILES;
    static final ModConfigSpec.BooleanValue FREEZE;
    static final ModConfigSpec.IntValue FREEZE_SECONDS;
    static final ModConfigSpec.IntValue FREEZE_DUMPS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Turn the whole thing on or off.").define("enabled", true);
        ALERT_TPS = b.comment("Warn when TPS (ticks per second, 20 is full speed) stays below this.")
                .defineInRange("alertTps", 15.0, 1.0, 19.9);
        RECOVER_TPS = b.comment("The lag spell counts as over once TPS is back above this for 10 seconds.")
                .defineInRange("recoverTps", 18.0, 1.0, 20.0);
        ALERT_AFTER_SECONDS = b.comment("How long TPS must stay low before the alert, so a single hiccup stays quiet.")
                .defineInRange("alertAfterSeconds", 10, 1, 600);
        STARTUP_GRACE_SECONDS = b.comment("Stay quiet this long after the server starts (world loading is always slow).")
                .defineInRange("startupGraceSeconds", 120, 0, 3600);
        TELL_EVERYONE = b.comment("true: every player sees the alert. false: only operators do. The console and logs/lagwatch.log always get it.")
                .define("tellEveryone", true);
        b.push("profile");
        PROFILE = b.comment("Record a spark profile when the alert fires. Needs spark (it's in the pack).")
                .define("enabled", true);
        PROFILE_SECONDS = b.comment("How long each profile records.").defineInRange("seconds", 60, 10, 600);
        PROFILE_ONLY_TICKS_OVER_MS = b.comment("Only keep slow ticks in the profile (50 ms is one full tick budget). 0 keeps every tick.")
                .defineInRange("onlyTicksOverMs", 50, 0, 1000);
        PROFILE_COOLDOWN_MINUTES = b.comment("At most one automatic profile per this many minutes. Alerts still come through.")
                .defineInRange("cooldownMinutes", 10, 0, 1440);
        KEEP_PROFILES = b.comment("Delete the oldest Lag Watch profiles beyond this many. 0 keeps them all.")
                .defineInRange("keep", 30, 0, 10000);
        b.pop();
        b.push("freeze");
        FREEZE = b.comment("Catch single long freezes (the TPS alert above lets one short spell through on purpose) and write what",
                        "the server thread was doing to logs/lagwatch.log, so the mod behind it is named. Costs nothing until a freeze.")
                .define("enabled", true);
        FREEZE_SECONDS = b.comment("A tick that takes this long counts as a freeze. The server thread is looked at again at 2x, 4x, 8x... this while it lasts.")
                .defineInRange("seconds", 2, 1, 60);
        FREEZE_DUMPS = b.comment("At most this many looks per freeze (5 looks at 2 s reach 32 s).").defineInRange("looks", 5, 1, 50);
        b.pop();
        SPEC = b.build();
    }

    public LagWatch(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
        Watcher watcher = new Watcher();
        NeoForge.EVENT_BUS.addListener(watcher::onServerStarted);
        NeoForge.EVENT_BUS.addListener(watcher::onTick);
        NeoForge.EVENT_BUS.addListener(watcher::onRegisterCommands);
        FreezeWatch freezes = new FreezeWatch();
        NeoForge.EVENT_BUS.addListener(freezes::onServerStarted);
        NeoForge.EVENT_BUS.addListener(freezes::onServerStopping);
        NeoForge.EVENT_BUS.addListener(freezes::onTick);
    }
}

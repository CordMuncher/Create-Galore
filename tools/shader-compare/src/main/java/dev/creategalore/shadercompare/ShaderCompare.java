package dev.creategalore.shadercompare;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The shader compare kit: /shadercompare run [packs] photographs every scene in config/create_galore_shader_compare.json
 * with every shader pack there, at its defaults and at its highest preset (screenshots/shader-compare/RUN/PACK-PASS/
 * SCENE.png, with a manifest.json that tools/dlss-testkit/compare.html opens); /shadercompare stop ends a run;
 * /shadercompare where prints the camera, for adding a scene.
 *
 * -Dcreate_galore_shader_compare.autorun=WORLD opens that world from the title screen, runs, and closes the game
 * (-Dcreate_galore_shader_compare.stay=true keeps it open after). -Dcreate_galore_shader_compare.packs=a+b,
 * .passes=defaults+highest and .scenes=a+b run only those.
 */
@Mod(value = ShaderCompare.MOD_ID, dist = Dist.CLIENT)
public final class ShaderCompare {
    public static final String MOD_ID = "create_galore_shader_compare";
    private static boolean autorunOpened;

    public ShaderCompare(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ShaderCompare::commands);
        NeoForge.EVENT_BUS.addListener(Runner::beforeFrame);
        NeoForge.EVENT_BUS.addListener(Runner::afterFrame);
        NeoForge.EVENT_BUS.addListener(ShaderCompare::titleScreen);
    }

    private static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shadercompare")
                .then(Commands.literal("run")
                        .executes(c -> start(null))
                        .then(Commands.argument("packs", StringArgumentType.greedyString())
                                .executes(c -> start(StringArgumentType.getString(c, "packs")))))
                .then(Commands.literal("stop").executes(c -> {
                    Runner.stop("stopped by command");
                    return 1;
                }))
                .then(Commands.literal("where").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    String where = String.format(java.util.Locale.ROOT,
                            "\"dimension\": \"%s\", \"x\": %.2f, \"y\": %.2f, \"z\": %.2f, \"yaw\": %.1f, \"pitch\": %.1f",
                            mc.level.dimension().location(), mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            mc.player.getYRot(), mc.player.getXRot());
                    mc.player.displayClientMessage(Component.literal(where), false);
                    return 1;
                })));
    }

    private static int start(String packs) {
        String error = Runner.start(packs != null ? packs : System.getProperty(MOD_ID + ".packs"));
        if (error != null) {
            Runner.log(error);
        }
        return 1;
    }

    // -Dcreate_galore_shader_compare.autorun=WORLD: open the world once the title screen shows.
    private static void titleScreen(ScreenEvent.Init.Post event) {
        String world = System.getProperty(MOD_ID + ".autorun");
        if (world == null || autorunOpened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        autorunOpened = true;
        Minecraft mc = Minecraft.getInstance();
        Runner.armAutorun();
        mc.execute(() -> mc.createWorldOpenFlows().openWorld(world, () -> {
            Runner.log("autorun: could not open world " + world);
            mc.stop();
        }));
    }
}

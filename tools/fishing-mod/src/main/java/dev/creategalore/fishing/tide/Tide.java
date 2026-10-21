/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide;

import dev.creategalore.fishing.tide.config.TideClientConfig;
import dev.creategalore.fishing.tide.config.TideConfig;
import dev.creategalore.fishing.tide.config.TideServerConfig;
import dev.creategalore.fishing.tide.loaders.LoaderPlatform;
import dev.creategalore.fishing.tide.loaders.NetworkPlatform;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.creategalore.fishing.tide.loaders.neoforge.*;

// looks like you've stumbled upon the code for
// .___________. __   _______   _______
// |           ||  | |       \ |   ____|
// `---|  |----`|  | |  .--.  ||  |__
//     |  |     |  | |  |  |  ||   __|
//     |  |     |  | |  '--'  ||  |____
//     |__|     |__| |_______/ |_______|  (v2)
// everyone's favorite fishing mod! (probably)
// released in 2023 by Lightning64 (v2 in 2026)
/**
 * Entry point of the ported Tide code: its namespace, logger, platform services and settings. Everything ported keeps
 * Tide's ids (namespace {@code tide}), so worlds and data made with Tide load unchanged.
 */
public class Tide {
    public static final String MOD_ID = "tide";
    public static final String MOD_NAME = "Tide";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    public static final LoaderPlatform PLATFORM;
    public static final NetworkPlatform NETWORK;
    static {

        PLATFORM = new NeoforgePlatform();
        NETWORK = new NeoforgeNetworkPlatform();

    }

    public static TideConfig CONFIG;
    public static TideClientConfig CLIENT_CONFIG;
    public static TideServerConfig SERVER_CONFIG;

    public static ResourceLocation resource(String path) {
        return resource(MOD_ID, path);
    }

    public static ResourceLocation resource(String namespace, String path) {
         return ResourceLocation.fromNamespaceAndPath(namespace, path);

    }

    public static void initialize() {
        CONFIG = new TideConfig();
        CLIENT_CONFIG = CONFIG.client();
        SERVER_CONFIG = CONFIG.server();
        LOG.info("Initialized the Tide port on {} {}", PLATFORM.getPlatformName(), PLATFORM.getMCVersion());
    }
}
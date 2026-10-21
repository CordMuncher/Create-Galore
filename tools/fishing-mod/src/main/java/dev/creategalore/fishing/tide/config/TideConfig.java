/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.config;

import dev.creategalore.fishing.tide.Tide;
/**
 * Tide's settings, kept as plain objects with the values the pack used. Tide read them with Cloth Config; this port
 * has no config screen or file, so they are fixed (the minigame and rod settings no longer apply at all).
 */
public class TideConfig {
    TideClientConfig client = new TideClientConfig();

    public TideClientConfig client() {
        return this.client;
    }
    TideServerConfig server = new TideServerConfig();

    public TideServerConfig server() {
        return this.server;
    }
}
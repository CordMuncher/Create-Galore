/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client;

import net.minecraft.client.renderer.ShaderInstance;

public class TideCoreShaders {
    public static ShaderInstance FULL_WHITE;
    public static ShaderInstance FULL_WHITE_ITEM;

    public static ShaderInstance fullWhite() {
        return FULL_WHITE;
    }

    public static ShaderInstance fullWhiteItem() {
        return FULL_WHITE_ITEM;
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries;

import dev.creategalore.fishing.tide.Tide;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public class TideParticleTypes {
    public static final SimpleParticleType VOID_RIPPLE_LARGE = register("void_ripple_large", new AlwaysShown());
    public static final SimpleParticleType VOID_RIPPLE_SMALL = register("void_ripple_small", new AlwaysShown());
    public static final SimpleParticleType MAGIC_CHAIN = register("magic_chain", new AlwaysShown());

    public static SimpleParticleType register(String key, SimpleParticleType particleType) {
        return Tide.PLATFORM.register(BuiltInRegistries.PARTICLE_TYPE, Tide.resource(key), particleType);
    }

    public static void init() {}

    /** A simple particle type shown even with particles turned down (SimpleParticleType's constructor is protected). */
    private static final class AlwaysShown extends SimpleParticleType {
        AlwaysShown() {
            super(true);
        }
    }
}

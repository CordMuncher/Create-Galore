/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.network.messages;

import dev.creategalore.fishing.tide.network.TideMessages;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface TidePacketPayload extends CustomPacketPayload {
    ResourceLocation id();

    @Override
    default Type<? extends CustomPacketPayload> type() {
        return TideMessages.ID_TO_TYPE.computeIfAbsent(this.id(), Type::new);
    }
}


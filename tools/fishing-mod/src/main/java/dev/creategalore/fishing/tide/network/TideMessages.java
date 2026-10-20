/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.network;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.loaders.NetworkPlatform;
import dev.creategalore.fishing.tide.network.messages.*;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

public class TideMessages {
    public static final Map<ResourceLocation, CustomPacketPayload.Type<? extends CustomPacketPayload>> ID_TO_TYPE = new HashMap<>();

    public static void init(NetworkPlatform network) {
        network.registerClientBoundPacket(OpenJournalMsg.class, OpenJournalMsg.ID, OpenJournalMsg::encode, OpenJournalMsg::new, OpenJournalMsg::handle);
        network.registerClientBoundPacket(ViewNoteMsg.class, ViewNoteMsg.ID, ViewNoteMsg::encode, ViewNoteMsg::new, ViewNoteMsg::handle);
        network.registerClientBoundPacket(InfoDataMsg.class, InfoDataMsg.ID, InfoDataMsg::encode, InfoDataMsg::new, InfoDataMsg::handle);
        network.registerClientBoundPacket(SyncPlayerDataMsg.class, SyncPlayerDataMsg.ID, SyncPlayerDataMsg::encode, SyncPlayerDataMsg::new, SyncPlayerDataMsg::handle);
        network.registerClientBoundPacket(ShowToastMsg.class, ShowToastMsg.ID, ShowToastMsg::encode, ShowToastMsg::new, ShowToastMsg::handle);

        network.registerServerBoundPacket(ReadProfileMsg.class, ReadProfileMsg.ID, ReadProfileMsg::encode, ReadProfileMsg::new, ReadProfileMsg::handle);
    }
}

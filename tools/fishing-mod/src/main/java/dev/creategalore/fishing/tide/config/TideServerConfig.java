/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.config;

import com.google.gson.Gson;
import dev.creategalore.fishing.tide.Tide;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
public final class TideServerConfig {
    public General general;
    public Items items;
    public Journal journal;
    public Minigame minigame;

    public TideServerConfig() {
        this.general = new General();
        this.items = new Items();
        this.journal = new Journal();
        this.minigame = new Minigame();
    }

    public static void writeToPacket(FriendlyByteBuf buf, Gson gson) {
        String json = gson.toJson(Tide.SERVER_CONFIG);
        buf.writeUtf(json);
    }

    public static void readFromPacket(FriendlyByteBuf buf, Gson gson) {
        String json = buf.readUtf();
        Tide.SERVER_CONFIG = gson.fromJson(json, TideServerConfig.class);
    }

    public static class General {
        public boolean overrideVanillaRod = true;
        public boolean holdToCast = true;
        public double rodDurabilityMultiplier = 1.0;
        public List<VoidHeightEntry> fishableVoidHeights = List.of(
                new VoidHeightEntry(Level.OVERWORLD, VoidHeightEntry.Type.RELATIVE_TO_BOTTOM, -6),
                new VoidHeightEntry(Level.NETHER, VoidHeightEntry.Type.RELATIVE_TO_BOTTOM, -6),
                new VoidHeightEntry(Level.END, VoidHeightEntry.Type.RELATIVE_TO_BOTTOM, 50)
        );

        public static class VoidHeightEntry {
            public String dimension = "minecraft:overworld";
            public Type type = Type.RELATIVE_TO_BOTTOM;
            public Integer height = -6;

            public VoidHeightEntry() {}

            public VoidHeightEntry(ResourceKey<Level> dimension, Type type, int height) {
                this.dimension = dimension.location().toString();
                this.type = type;
                this.height = height;
            }

            public enum Type {
                RELATIVE_TO_BOTTOM, RELATIVE_TO_TOP, ABSOLUTE;
                public String getKey() {
                    return "text.autoconfig.tide.option.VoidHeightEntry.Type." + name().toLowerCase();
                }
            }
        }
        public List<String> autoFishDataBlacklist = List.of();
        public double crateWeight = 4.0;
        public double crateQuality = 1.0;
        public boolean logDataErrors = false;

        @Override
        public String toString() {
            return "General{" +
                    "overrideVanillaRod=" + overrideVanillaRod +
                    ", holdToCast=" + holdToCast +
                    ", rodDurabilityMultiplier=" + rodDurabilityMultiplier +
                    ", fishableVoidHeights=" + fishableVoidHeights +
                    ", autoFishDataBlacklist=" + autoFishDataBlacklist +
                    ", crateWeight=" + crateWeight +
                    ", crateQuality=" + crateQuality +
                    ", logDataErrors=" + logDataErrors +
                    '}';
        }
    }

    public static class Items {
        public SizeMode fishItemSizes = SizeMode.ALWAYS;

        public enum SizeMode {
            ALWAYS, IN_JOURNAL, NEVER;
            public String getKey() {
                return "text.autoconfig.tide.option.server.items.fishItemSizes." + name().toLowerCase();
            }
        }
        public BucketableMode bucketableFishItems = BucketableMode.WHEN_LIVING;

        public enum BucketableMode {
            ALWAYS, WHEN_LIVING, NEVER;
            public String getKey() {
                return "text.autoconfig.tide.option.server.items.bucketableFishItems." + name().toLowerCase();
            }
        }
        // The pack turns these off (it shipped Tide with enableBedrockBreakingItems = false).
        public boolean enableBedrockBreakingItems = false;
        public List<String> pocketWatchBlacklist = List.of();

        @Override
        public String toString() {
            return "Items{" +
                    "fishItemSizes=" + fishItemSizes +
                    ", bucketableFishItems=" + bucketableFishItems +
                    ", enableBedrockBreakingItems=" + enableBedrockBreakingItems +
                    ", pocketWatchBlacklist=" + pocketWatchBlacklist +
                    '}';
        }
    }

    public static class Journal {
        public boolean giveJournal = true;
        public boolean showToasts = true;

        @Override
        public String toString() {
            return "Journal{" +
                    "giveJournal=" + giveJournal +
                    ", showToasts=" + showToasts +
                    '}';
        }
    }

    public static class Minigame {
        public boolean doMinigame = true;
        public float minigameDifficultyMultiplier = 1.0f;
        public boolean doSuccessSound = true;
        public boolean doFailSound = true;
        public boolean useThirdPartyMinigames = true;
        public boolean useThirdPartyFishData = false;

        @Override
        public String toString() {
            return "Minigame{" +
                    "doMinigame=" + doMinigame +
                    ", minigameDifficultyMultiplier=" + minigameDifficultyMultiplier +
                    ", doSuccessSound=" + doSuccessSound +
                    ", doFailSound=" + doFailSound +
                    ", useThirdPartyMinigames=" + useThirdPartyMinigames +
                    ", useThirdPartyFishData=" + useThirdPartyFishData +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "TideConfig{" +
                "general=" + general +
                ", items=" + items +
                ", journal=" + journal +
                ", minigame=" + minigame +
                '}';
    }
}
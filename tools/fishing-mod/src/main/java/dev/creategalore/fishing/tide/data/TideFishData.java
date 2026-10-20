/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * adapted for Create Galore Fishing: the record and its codec are Tide's, so Tide's
 * fish files (data/<namespace>/fishing/fish/**.json) load unchanged; the journal,
 * display and minigame fields are kept for the ported journal and trophy mounts.
 */
package dev.creategalore.fishing.tide.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/**
 * One fish as Tide describes it: which item it is, where and when it bites (conditions), how often (selection
 * weight, scaled by modifiers and luck), its sizes in cm, its journal page and how it sits on a display.
 *
 * <p>A fish with a {@code parent} is merged into that fish: it never bites itself, and its item counts as the
 * parent for the journal. The pack uses this to make one mod's fish the canonical version of a species.
 */
public record TideFishData(Holder<Item> fish,
                           Optional<Holder<Item>> bucket,
                           List<String> associatedMods,
                           boolean showInJournal,
                           List<FishingCondition> conditions,
                           List<FishingModifier> modifiers,
                           double weight,
                           double quality,
                           float strength,
                           float speed,
                           Behavior behavior,
                           Profile profile,
                           Optional<Size> size,
                           Optional<Display> display,
                           Optional<Holder<Item>> parent) {

    public static final Codec<TideFishData> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("fish").forGetter(TideFishData::fish),
            BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("bucket").forGetter(TideFishData::bucket),
            Codec.STRING.listOf().optionalFieldOf("associated_mods", List.of()).forGetter(TideFishData::associatedMods),
            Codec.BOOL.optionalFieldOf("show_in_journal", true).forGetter(TideFishData::showInJournal),
            FishingCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(TideFishData::conditions),
            FishingModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(TideFishData::modifiers),
            Codec.DOUBLE.optionalFieldOf("selection_weight", 0.0).forGetter(TideFishData::weight),
            Codec.DOUBLE.optionalFieldOf("selection_quality", 0.0).forGetter(TideFishData::quality),
            Codec.FLOAT.optionalFieldOf("strength", 0.3f).forGetter(TideFishData::strength),
            Codec.FLOAT.optionalFieldOf("speed", 1.0f).forGetter(TideFishData::speed),
            Behavior.CODEC.optionalFieldOf("behavior", Behavior.SINE).forGetter(TideFishData::behavior),
            Profile.CODEC.optionalFieldOf("journal_profile", Profile.DEFAULT).forGetter(TideFishData::profile),
            Size.CODEC.optionalFieldOf("size").forGetter(TideFishData::size),
            Display.CODEC.optionalFieldOf("display_data").forGetter(TideFishData::display),
            BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("parent").forGetter(TideFishData::parent)
    ).apply(i, TideFishData::new));

    /** Whether all of this fish's conditions pass at the spot. */
    public boolean canBiteAt(FishingContext context) {
        for (FishingCondition condition : conditions) {
            if (!condition.test(context)) return false;
        }
        return true;
    }

    /** Selection weight at the spot: base + quality x luck, then each modifier; never below 0. */
    public double weightAt(FishingContext context) {
        double w = weight + quality * context.luck();
        if (w <= 0) return 0;
        for (FishingModifier modifier : modifiers) {
            w = modifier.apply(w, context);
            if (w <= 0) return 0;
        }
        return w;
    }

    /** The fluid this fish lives in ("water" unless a fluid condition says otherwise). */
    public String medium() {
        for (FishingCondition condition : conditions) {
            if (condition instanceof FishingCondition.Medium m) return m.medium();
        }
        return "water";
    }

    /** Sizes in cm: typical range and record. */
    public record Size(double typicalLowCm, double typicalHighCm, Optional<Double> recordLowCm, double recordHighCm) {
        public static final Codec<Size> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("typical_low_cm").forGetter(Size::typicalLowCm),
                Codec.DOUBLE.fieldOf("typical_high_cm").forGetter(Size::typicalHighCm),
                Codec.DOUBLE.optionalFieldOf("record_low_cm").forGetter(Size::recordLowCm),
                Codec.DOUBLE.fieldOf("record_high_cm").forGetter(Size::recordHighCm)
        ).apply(i, Size::new));
    }

    /** The fish's journal page. */
    public record Profile(Optional<String> description, Optional<String> location, Optional<ResourceLocation> altSprite,
                          Optional<Integer> altSpriteSize, Rarity rarity, Group group) {
        public static final Profile DEFAULT = new Profile(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Rarity.COMMON, Group.MISC);
        public static final Codec<Profile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("description").forGetter(Profile::description),
                Codec.STRING.optionalFieldOf("location").forGetter(Profile::location),
                ResourceLocation.CODEC.optionalFieldOf("alt_sprite").forGetter(Profile::altSprite),
                Codec.INT.optionalFieldOf("alt_sprite_size").forGetter(Profile::altSpriteSize),
                Rarity.CODEC.optionalFieldOf("rarity", Rarity.COMMON).forGetter(Profile::rarity),
                Group.CODEC.optionalFieldOf("group", Group.MISC).forGetter(Profile::group)
        ).apply(i, Profile::new));
    }

    /** How the fish sits on a display: which entity to show, its footprint and offsets. */
    public record Display(ResourceKey<EntityType<?>> entity, Optional<CompoundTag> nbt, Shape shape,
                          float x, float y, float z, float roll, float pitch, float yaw) {
        public static final Codec<Display> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.ENTITY_TYPE).fieldOf("entity").forGetter(Display::entity),
                CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(Display::nbt),
                Shape.CODEC.optionalFieldOf("shape", Shape.SHAPE_1X1).forGetter(Display::shape),
                Codec.FLOAT.optionalFieldOf("x", 0f).forGetter(Display::x),
                Codec.FLOAT.optionalFieldOf("y", 0f).forGetter(Display::y),
                Codec.FLOAT.optionalFieldOf("z", 0f).forGetter(Display::z),
                Codec.FLOAT.optionalFieldOf("roll", 0f).forGetter(Display::roll),
                Codec.FLOAT.optionalFieldOf("pitch", 0f).forGetter(Display::pitch),
                Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(Display::yaw)
        ).apply(i, Display::new));
    }

    public enum Shape implements StringRepresentable {
        SHAPE_1X1("1x1"), SHAPE_2X1("2x1"), SHAPE_3X1("3x1");

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);
        private final String id;

        Shape(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public enum Rarity implements StringRepresentable {
        COMMON, UNCOMMON, RARE, VERY_RARE, LEGENDARY;

        public static final Codec<Rarity> CODEC = StringRepresentable.fromEnum(Rarity::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Group implements StringRepresentable {
        FRESHWATER, MISC, SALTWATER, UNDERGROUND, LAVA, VOID;

        public static final Codec<Group> CODEC = StringRepresentable.fromEnum(Group::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * How the fish's marker moved in Tide's minigame. Not used for fights directly; it seeds the default fight
     * profile for fish that have no fight data of their own.
     */
    public enum Behavior implements StringRepresentable {
        SINE, PLATEAU, JITTER, DARTS, LINEAR, LINEAR_WRAP;

        public static final Codec<Behavior> CODEC = StringRepresentable.fromEnum(Behavior::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}

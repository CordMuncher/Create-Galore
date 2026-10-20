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
import dev.creategalore.fishing.tide.data.TideTags;
import dev.creategalore.fishing.tide.registries.blocks.FishDisplayShape;
import dev.creategalore.fishing.tide.registries.entities.fish.*;
import dev.creategalore.fishing.tide.registries.entities.fish.lava.TideSchoolingLavaFish;
import dev.creategalore.fishing.tide.registries.items.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.material.Fluids;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unused")
public class TideFish {
    public static final ArrayList<Item> ORDERED = new ArrayList<>();
    public static final List<ResourceKey<Item>> FISH_KEYS = new ArrayList<>();
    public static final Map<Item, List<ResourceKey<Item>>> COOKABLE_FISH_MAP = new HashMap<>();
    public static final Map<String, FishEntityData<? extends Mob>> ENTITY_DATA = new HashMap<>();
    public static final ArrayList<Item> SPAWNING_ITEMS = new ArrayList<>();

    // Freshwater fish


    public static final Item BROOK_TROUT = new Builder("brook_trout")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.6f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item LARGEMOUTH_BASS = new Builder("largemouth_bass")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.65f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();


    public static final Item WHITE_CRAPPIE = new Builder("white_crappie")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.4f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 2.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item BLACK_CRAPPIE = new Builder("black_crappie")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.4f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 2.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item YELLOW_PERCH = new Builder("yellow_perch")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.4f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 2.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();


    public static final Item PIKE = new Builder("pike")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.75f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();

    public static final Item GUPPY = new Builder("guppy")
            .food(TideFoods.TINY_RAW_FISH)
            .cookedItem(TideItems.SMALL_COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.3f, 0.15f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 1.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();



    public static final Item WALLEYE = new Builder("walleye")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.6f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();


    public static final Item MIRAGE_CATFISH = new Builder("mirage_catfish")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.85f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();

    public static final Item SAND_TIGER_SHARK = new Builder("sand_tiger_shark")
            .food(TideFoods.BIG_RAW_FISH)
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingFish::new,
                    builder -> builder
                            .sized(1.8f, 0.6f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 25.0f)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item SLIMY_SALMON = new Builder("slimy_salmon")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.9f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item FROSTBITE_FLOUNDER = new Builder("frostbite_flounder")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.65f, 0.2f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 0.8f)
            ))
            .build();

    public static final Item STURGEON = new Builder("sturgeon")
            .food(TideFoods.BIG_RAW_FISH)
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingFish::new,
                    builder -> builder
                            .sized(1.6f, 0.5f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 18.0f)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();

    public static final Item BLOSSOM_BASS = new Builder("blossom_bass")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.65f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item SPORE_STALKER = new Builder("spore_stalker")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.63f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item MOONEYE = new Builder("mooneye")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.5f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 2.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item BULL_SHARK = new Builder("bull_shark")
            .food(TideFoods.BIG_RAW_FISH)
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingFish::new,
                    builder -> builder
                            .sized(1.8f, 0.6f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 25.0f)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    // Saltwater fish

    public static final Item OCEAN_PERCH = new Builder("ocean_perch")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.6f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item RED_SNAPPER = new Builder("red_snapper")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH).entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.65f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item FLOUNDER = new Builder("flounder")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.65f, 0.2f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 0.8f)
            ))
            .build();

    public static final Item ANCHOVY = new Builder("anchovy")
            .food(TideFoods.TINY_RAW_FISH)
            .cookedItem(TideItems.SMALL_COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.25f, 0.1f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 1.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.5f)
            ))
            .build();



    public static final Item SNOOK = new Builder("snook")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.65f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item ANGELFISH = new Builder("angelfish")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.28f, 0.32f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 2.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();


    public static final Item SAILFISH = new Builder("sailfish")
            .item(properties -> new SwordfishItem(Tiers.IRON, 2, -3.0f, properties))
            .food(TideFoods.BIG_RAW_FISH)
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingFish::new,
                    builder -> builder
                            .sized(1.25f, 0.65f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 10.0f)
                            .add(Attributes.MOVEMENT_SPEED, 1.3f)
            ))
            .build();

    public static final Item SWORDFISH = new Builder("swordfish")
            .item(properties -> new SwordfishItem(Tiers.IRON, 3, -2.8f, properties))
            .food(TideFoods.BIG_RAW_FISH)
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingFish::new,
                    builder -> builder
                            .sized(1.3f, 0.6f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 12.0f)
                            .add(Attributes.MOVEMENT_SPEED, 1.32f)
            ))
            .build();

    public static final Item MANTA_RAY = new Builder("manta_ray")
            .food(TideFoods.BIG_RAW_FISH)
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingFish::new,
                    builder -> builder
                            .sized(1.5f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 18.0f)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item AQUATHORN = new Builder("aquathorn")
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.45f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();

    public static final Item NEPTUNE_KOI = new Builder("neptune_koi")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.45f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.5f)
            ))
            .build();

    public static final Item PLUTO_SNAIL = new Builder("pluto_snail")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    Snail::new,
                    builder -> builder
                            .sized(0.45f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 2.0)
                            .add(Attributes.MOVEMENT_SPEED, 0.4f)
            ))
            .build();

    public static final Item SUN_EMBLEM = new Builder("sun_emblem")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    Jellyfish::new,
                    builder -> builder
                            .sized(0.4f, 0.55f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 0.6f)
            ))
            .build();

    public static final Item SATURN_CUTTLEFISH = new Builder("saturn_cuttlefish")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingFish::new,
                    builder -> builder
                            .sized(0.8f, 0.25f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 6.0)
                            .add(Attributes.MOVEMENT_SPEED, 0.8f)
            ))
            .build();

    public static final Item MARSTILUS = new Builder("marstilus")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingFish::new,
                    builder -> builder
                            .sized(0.8f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 8.0)
                            .add(Attributes.MOVEMENT_SPEED, 0.8f)
            ))
            .build();

    public static final Item URANIAS_PISCES = new Builder("uranias_pisces")
            .food(TideFoods.BIG_RAW_FISH.withEffects(List.of(new TideFoods.FoodEffect(
                    new MobEffectInstance(MobEffects.LUCK, 12000), 1.0f))))
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingFish::new,
                    builder -> builder
                            .sized(1.1f, 0.6f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 12.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.25f)
            ))
            .build();

    public static final Item GREAT_WHITE_SHARK = new Builder("great_white_shark")
            .food(TideFoods.BIG_RAW_FISH)
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingFish::new,
                    builder -> builder
                            .sized(2.1f, 0.75f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 32.0f)
                            .add(Attributes.MOVEMENT_SPEED, 1.15f)
            ))
            .build();

    public static final Item SHOOTING_STARFISH = new Builder("shooting_starfish")
            .properties(p -> p.rarity(Rarity.RARE))
            .entityData(FishEntityData.of(
                    ShootingStarfish::new,
                    builder -> builder
                            .sized(0.65f, 0.15f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 10.0)
                            .add(Attributes.MOVEMENT_SPEED, 0.0f)
            ))
            .build();


    // Underground fish

    public static final Item CAVE_EEL = new Builder("cave_eel")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.95f, 0.25f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item CAVE_CRAWLER = new Builder("cave_crawler")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    AmphibiousFish::new,
                    builder -> builder
                            .sized(0.65f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item DEEP_GROUPER = new Builder("deep_grouper")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.6f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item SHADOW_SNAPPER = new Builder("shadow_snapper")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.6f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();

    public static final Item GLOWFISH = new Builder("glowfish")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.6f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();


    public static final Item ABYSS_ANGLER = new Builder("abyss_angler")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.5f, 0.25f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 0.7f)
            ))
            .build();

    public static final Item IRON_TETRA = new Builder("iron_tetra")
            .food(TideFoods.TINY_RAW_FISH)
            .cookedItem(TideItems.SMALL_COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.3f, 0.25f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 6.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.6f)
            ))
            .build();

    public static final Item LAPIS_LANTERNFISH = new Builder("lapis_lanternfish")
            .food(TideFoods.TINY_RAW_FISH)
            .cookedItem(TideItems.SMALL_COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.3f, 0.15f)
                               .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 2.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.6f)
            ))
            .build();

    public static final Item CRYSTAL_SHRIMP = new Builder("crystal_shrimp")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.4f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 2.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item DRIPSTONE_DARTER = new Builder("dripstone_darter")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.5f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.5f)
            ))
            .build();

    public static final Item LUMINESCENT_JELLYFISH = new Builder("luminescent_jellyfish")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    Jellyfish::new,
                    builder -> builder
                            .sized(0.4f, 0.55f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 0.6f)
            ))
            .build();

    public static final Item CRYSTALLINE_CARP = new Builder("crystalline_carp")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.5f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 7.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.2f)
            ))
            .build();

    public static final Item GILDED_MINNOW = new Builder("gilded_minnow")
            .food(TideFoods.TINY_RAW_FISH)
            .cookedItem(TideItems.SMALL_COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.27f, 0.12f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.5f)
            ))
            .build();

    public static final Item BEDROCK_TETRA = new Builder("bedrock_tetra")
            .food(TideFoods.TINY_RAW_FISH)
            .cookedItem(TideItems.SMALL_COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.3f, 0.25f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 10.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.6f)
            ))
            .build();

    public static final Item CHASM_EEL = new Builder("chasm_eel")
            .item(ChasmEelItem::new)
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.9f, 0.25f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 8.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item ECHO_SNAPPER = new Builder("echo_snapper")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.65f, 0.125f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 0.75f)
            ))
            .build();

    public static final Item WINDBASS = new Builder("windbass")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    Windbass::new,
                    builder -> builder
                            .sized(0.5f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 2.0f)
            ))
            .build();

    public static final Item DEVILS_HOLE_PUPFISH = new Builder("devils_hole_pupfish")
            .food(TideFoods.TINY_RAW_FISH)
            .cookedItem(TideItems.SMALL_COOKED_FISH)
            .properties(p -> p.rarity(Rarity.RARE))
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.25f, 0.12f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 1.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.6f)
            ))
            .build();

    public static final Item MIDAS_FISH = new Builder("midas_fish")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .properties(p -> p.rarity(Rarity.RARE))
            .entityData(FishEntityData.of(
                    TideFishEntity::new,
                    builder -> builder
                            .sized(0.7f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 20.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    // Void fish

    public static final Item INCANDESCENT_LARVA = new Builder("incandescent_larva")
            .food(TideFoods.TINY_RAW_FISH)
            .cookedItem(TideItems.SMALL_COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.35f, 0.12f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 1.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item BEDROCK_BUG = new Builder("bedrock_bug")
            .food(TideFoods.TINY_RAW_FISH)
            .cookedItem(TideItems.SMALL_COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.35f, 0.13f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();

    public static final Item SLEEPY_CARP = new Builder("sleepy_carp")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.65f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 6.0)
                            .add(Attributes.MOVEMENT_SPEED, 0.6f)
            ))
            .build();

    public static final Item BLUE_NEONFISH = new Builder("blue_neonfish")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.5f, 0.25f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item JUDGMENT_FISH = new Builder("judgment_fish")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.4f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.25f)
            ))
            .build();

    public static final Item DEEP_BLUE = new Builder("deep_blue")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.6f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 8.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();

    public static final Item NEPHROSILU = new Builder("nephrosilu")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.5f,0.2f)
                               .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 7.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.15f)
            ))
            .build();

    public static final Item VENGEANCE = new Builder("vengeance")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.6f, 0.45f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 8.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();

    public static final Item PENTAPUS = new Builder("pentapus")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    Pentapus::new,
                    builder -> builder
                            .sized(0.45f, 0.5f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item DARKNESS_EATER = new Builder("darkness_eater")
            .food(TideFoods.BIG_RAW_FISH)
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingVoidFish::new,
                    builder -> builder
                            .sized(1.7f, 0.65f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 25.0f)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item SHADOW_SHARK = new Builder("shadow_shark")
            .food(TideFoods.BIG_RAW_FISH)
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingVoidFish::new,
                    builder -> builder
                            .sized(1.8f, 0.6f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 28.0f)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();

    public static final Item ALPHA_FISH = new Builder("alpha_fish")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .properties(p -> p.rarity(Rarity.RARE))
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.5f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.25f)
            ))
            .build();

    // Lava fish

    public static final Item MAGMA_MACKEREL = new Builder("magma_mackerel")
            .food(TideFoods.FIERY_FISH)
            .fireproof()
            .entityData(FishEntityData.of(
                    TideSchoolingLavaFish::new,
                    builder -> builder
                            .sized(0.4f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f),
                    (type, properties) -> new MobBucketItem(
                            type, Fluids.LAVA, SoundEvents.BUCKET_EMPTY_LAVA, properties)
            ))
            .build();

    public static final Item EMBER_KOI = new Builder("ember_koi")
            .food(TideFoods.FIERY_FISH)
            .fireproof()
            .entityData(FishEntityData.of(
                    TideSchoolingLavaFish::new,
                    builder -> builder
                            .sized(0.4f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f),
                    (type, properties) -> new MobBucketItem(
                            type, Fluids.LAVA, SoundEvents.BUCKET_EMPTY_LAVA, properties)
            ))
            .build();

    public static final Item ASH_PERCH = new Builder("ash_perch")
            .food(TideFoods.FIERY_FISH)
            .fireproof()
            .entityData(FishEntityData.of(
                    TideSchoolingLavaFish::new,
                    builder -> builder
                            .sized(0.4f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f),
                    (type, properties) -> new MobBucketItem(
                            type, Fluids.LAVA, SoundEvents.BUCKET_EMPTY_LAVA, properties)
            ))
            .build();

    public static final Item OBSIDIAN_PIKE = new Builder("obsidian_pike")
            .food(TideFoods.FIERY_FISH)
            .fireproof()
            .entityData(FishEntityData.of(
                    TideSchoolingLavaFish::new,
                    builder -> builder
                            .sized(0.75f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 14.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f),
                    (type, properties) -> new MobBucketItem(
                            type, Fluids.LAVA, SoundEvents.BUCKET_EMPTY_LAVA, properties)
            ))
            .build();

    public static final Item VOLCANO_TUNA = new Builder("volcano_tuna")
            .food(TideFoods.FIERY_FISH)
            .fireproof()
            .entityData(FishEntityData.of(
                    TideSchoolingLavaFish::new,
                    builder -> builder
                            .sized(0.6f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f),
                    (type, properties) -> new MobBucketItem(
                            type, Fluids.LAVA, SoundEvents.BUCKET_EMPTY_LAVA, properties)
            ))
            .build();

    public static final Item INFERNO_GUPPY = new Builder("inferno_guppy")
            .food(TideFoods.TINY_FIERY_FISH)
            .fireproof()
            .entityData(FishEntityData.of(
                    TideSchoolingLavaFish::new,
                    builder -> builder
                            .sized(0.3f, 0.15f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 1.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f),
                    (type, properties) -> new MobBucketItem(
                            type, Fluids.LAVA, SoundEvents.BUCKET_EMPTY_LAVA, properties)
            ))
            .build();

    public static final Item WARPED_GUPPY = new Builder("warped_guppy")
            .food(TideFoods.TINY_FIERY_FISH)
            .fireproof()
            .entityData(FishEntityData.of(
                    TideSchoolingLavaFish::new,
                    builder -> builder
                            .sized(0.3f, 0.15f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 1.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f),
                    (type, properties) -> new MobBucketItem(
                            type, Fluids.LAVA, SoundEvents.BUCKET_EMPTY_LAVA, properties)
            ))
            .build();

    public static final Item CRIMSON_FANGJAW = new Builder("crimson_fangjaw")
            .food(TideFoods.FIERY_FISH)
            .fireproof()
            .entityData(FishEntityData.of(
                    TideSchoolingLavaFish::new,
                    builder -> builder
                            .sized(0.6f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f),
                    (type, properties) -> new MobBucketItem(
                            type, Fluids.LAVA, SoundEvents.BUCKET_EMPTY_LAVA, properties)
            ))
            .build();

    public static final Item SOULSCALE = new Builder("soulscale")
            .food(TideFoods.FIERY_FISH)
            .fireproof()
            .entityData(FishEntityData.of(
                    TideSchoolingLavaFish::new,
                    builder -> builder
                            .sized(0.65f, 0.4f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f),
                    (type, properties) -> new MobBucketItem(
                            type, Fluids.LAVA, SoundEvents.BUCKET_EMPTY_LAVA, properties)
            ))
            .build();

    public static final Item WITHERFIN = new Builder("witherfin")
            .food(TideFoods.RAW_FISH.withEffects(List.of(new TideFoods.FoodEffect(
                    new MobEffectInstance(MobEffects.WITHER, 140, 3), 1.0f))))
            .fireproof()
            .entityData(FishEntityData.of(
                    Witherfin::new,
                    builder -> builder
                            .sized(0.5f, 0.3f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 6.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f),
                    (type, properties) -> new MobBucketItem(
                            type, Fluids.LAVA, SoundEvents.BUCKET_EMPTY_LAVA, properties)
            ))
            .build();

    public static final Item BLAZING_SWORDFISH = new Builder("blazing_swordfish")
            .item(properties -> new BlazingSwordfishItem(Tiers.IRON, 4, -2.8f, properties))
            .food(TideFoods.BIG_FIERY_FISH)
            .fireproof()
            .entityData(FishEntityData.of(
                    BlazingSwordfish::new,
                    builder -> builder
                            .sized(1.3f, 0.6f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 15.0f)
                            .add(Attributes.MOVEMENT_SPEED, 1.3f),
                    (type, properties) -> new MobBucketItem(
                            type, Fluids.LAVA, SoundEvents.BUCKET_EMPTY_LAVA, properties)
            ))
            .build();

    // End fish

    public static final Item PALE_CLUBFISH = new Builder("pale_clubfish")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.55f, 0.35f)
                               .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 4.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item AMBER_ROCKFISH = new Builder("amber_rockfish")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.45f, 0.45f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item ENDERFIN = new Builder("enderfin")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.4f, 0.38f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();

    public static final Item CHORUS_COD = new Builder("chorus_cod")
            .item(ChorusFruitItem::new)
            .food(TideFoods.RAW_FISH.withAlwaysEdible())
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.6f, 0.38f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.1f)
            ))
            .build();

    public static final Item ENDER_GLIDER = new Builder("ender_glider")
            .food(TideFoods.RAW_FISH.withEffects(List.of(new TideFoods.FoodEffect(
                    new MobEffectInstance(MobEffects.SLOW_FALLING, 4000), 1.0f))))
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.6f, 0.25f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.4f)
            ))
            .build();

    public static final Item ENDERGAZER = new Builder("endergazer")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.4f, 0.25f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.2f)
            ))
            .build();

    public static final Item VIOLET_CARP = new Builder("violet_carp")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.65f, 0.35f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 5.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();

    public static final Item RED_40 = new Builder("red_40")
            .food(TideFoods.TINY_RAW_FISH)
            .cookedItem(TideItems.SMALL_COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.26f, 0.22f)
                               .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 2.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.25f)
            ))
            .build();

    public static final Item DUTCHMAN_SOCK = new Builder("dutchman_sock")
            .food(TideFoods.TINY_RAW_FISH)
            .cookedItem(TideItems.SMALL_COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.3f, 0.125f)
                               .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 1.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.3f)
            ))
            .build();

    public static final Item ELYTROUT = new Builder("elytrout")
            .food(TideFoods.RAW_FISH)
            .cookedItem(TideItems.COOKED_FISH)
            .entityData(FishEntityData.of(
                    TideVoidFish::new,
                    builder -> builder
                            .sized(0.5f, 0.125f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 3.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.15f)
            ))
            .build();

    public static final Item MANTYVERN = new Builder("mantyvern")
            .food(TideFoods.BIG_RAW_FISH.withEffects(List.of(new TideFoods.FoodEffect(
                    new MobEffectInstance(MobEffects.SLOW_FALLING, 6000), 1.0f))))
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingVoidFish::new,
                    builder -> builder
                            .sized(1.8f, 0.5f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 20.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.4f)
            ))
            .build();

    public static final Item SNATCHER_SQUID = new Builder("snatcher_squid")
            .food(TideFoods.BIG_RAW_FISH)
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .entityData(FishEntityData.of(
                    SmoothSwimmingVoidFish::new,
                    builder -> builder
                            .sized(1.0f, 0.2f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 16.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.0f)
            ))
            .build();


    public static final Item DRAGON_FISH = new Builder("dragon_fish")
            .food(TideFoods.BIG_RAW_FISH)
            .cookedItem(TideItems.LARGE_COOKED_FISH)
            .properties(p -> p.rarity(Rarity.RARE))
            .entityData(FishEntityData.of(
                    SmoothSwimmingVoidFish::new,
                    builder -> builder
                            .sized(4f, 1f)
                            .clientTrackingRange(4),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 60.0)
                            .add(Attributes.MOVEMENT_SPEED, 1.25f)
            ))
            .build();

    public static void init() {}

    public static ArrayList<Item> getOrderedFishItems() {
        return ORDERED;
    }

    public static class Builder {
        private final String id;
        private final Item.Properties properties = new Item.Properties();
        private Function<Item.Properties, Item> factory = Item::new;
        private Item cookedItem;
        private FishEntityData<?> entityData;

        public Builder(String id) {
            this.id = id;
        }

        public Builder item(Function<Item.Properties, Item> factory) {
            this.factory = factory;
            return this;
        }

        public Builder properties(Consumer<Item.Properties> properties) {
            properties.accept(this.properties);
            return this;
        }

        public Builder food(TideFoods.FoodDefinition def) {
            TideFoods.apply(properties, def);
            return this;
        }

        public Builder fireproof() {
            this.properties.fireResistant();
            return this;
        }

        public Builder cookedItem(Item cookedItem) {
            this.cookedItem = cookedItem;
            return this;
        }

        public <T extends Mob> Builder entityData(FishEntityData<T> entityData) {
            this.entityData = entityData;
            return this;
        }

        public Item build() {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Tide.resource(id));
            Item item = factory.apply(properties);

            // Saving all of this datagen utility data may take up large portions
            // of memory, so only add these when running in a dev environment
            if (Tide.PLATFORM.isDevEnvironment()) {
                FISH_KEYS.add(key);
                if (cookedItem != null) {
                    if (COOKABLE_FISH_MAP.containsKey(cookedItem))
                        COOKABLE_FISH_MAP.get(cookedItem).add(key);
                    else {
                        ArrayList<ResourceKey<Item>> cookables = new ArrayList<>();
                        cookables.add(key);
                        COOKABLE_FISH_MAP.put(cookedItem, cookables);
                    }
                }
            }

            if (entityData != null) {
                String path = key.location().getPath();
                ENTITY_DATA.put(path, entityData);
                createSpawningItems(path, entityData);
            }

            ORDERED.add(item);
            return TideItems.register(key, item);
        }

        @SuppressWarnings("unchecked")
        private <T extends Mob> void createSpawningItems(String key, FishEntityData<T> entityData) {
            EntityType<T> type = (EntityType<T>) TideEntityTypes.ENTITY_TYPES
                    .computeIfAbsent(key, k -> entityData.createEntityBuilder().build(k));

            Item egg = TideItems.register(key + "_spawn_egg", properties ->
                    new SpawnEggItem(type, 0xffffff, 0x000000, properties));
            Item bucket = TideItems.register(key + "_bucket", properties -> entityData.bucketBuilder().apply(type, properties),
                    new Item.Properties().stacksTo(1).component(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY));

            if (Tide.PLATFORM.isDevEnvironment()) {
                SPAWNING_ITEMS.add(egg);
                SPAWNING_ITEMS.add(bucket);
            }
        }
    }

    public record FishEntityData<T extends Mob>(EntityType.EntityFactory<T> factory,
                                                Consumer<EntityType.Builder<T>> entityBuilderConsumer,
                                                AttributeSupplier.Builder attributesBuilder,
                                                BiFunction<EntityType<T>, Item.Properties, Item> bucketBuilder) {

        public static <T extends Mob> FishEntityData<T> of(EntityType.EntityFactory<T> factory,
                                                           Consumer<EntityType.Builder<T>> entityBuilderConsumer,
                                                           AttributeSupplier.Builder attributesBuilder) {
            return FishEntityData.of(factory, entityBuilderConsumer, attributesBuilder, (type, properties) ->
                    new MobBucketItem(type, Fluids.WATER, SoundEvents.BUCKET_EMPTY_FISH, properties));
        }

        public static <T extends Mob> FishEntityData<T> of(EntityType.EntityFactory<T> factory,
                                                           Consumer<EntityType.Builder<T>> entityBuilderConsumer,
                                                           AttributeSupplier.Builder attributesBuilder,
                                                           BiFunction<EntityType<T>, Item.Properties, Item> bucketBuilder) {
            return new FishEntityData<>(factory, entityBuilderConsumer, attributesBuilder, bucketBuilder);
        }

        public EntityType.Builder<T> createEntityBuilder() {
            EntityType.Builder<T> builder = EntityType.Builder.of(factory, MobCategory.WATER_AMBIENT);
            entityBuilderConsumer.accept(builder);
            return builder;
        }
    }
}

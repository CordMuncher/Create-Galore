package dev.creategalore.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The same animal from two mods should give the same things. Critters and Companions' snail can be tamed and
 * bottled for Snail Slime, its dragonfly can be tamed and armored; Naturalist's lookalikes can do neither, and a
 * Naturalist snail hides in its shell whenever a player comes within 5 blocks, so it can't follow anyone. Each
 * Naturalist snail and dragonfly therefore becomes the Critters one on the tick after it appears (natural spawns,
 * old worlds, eggs, spawn eggs, a released caught snail), keeping its name, age and persistence. The Critters ones
 * take over Naturalist's drops in the pack datapack (snail shell and slime; froglass when a frog eats a dragonfly).
 *
 * Right-click harvests that only one mod's animal had are given to its lookalikes here, with the same 5 minute
 * wait per animal: a glass bottle on any snail (Hybrid Aquatic's scalyfoot, Northstar's moon snail) gives Snail
 * Slime, on Hybrid Aquatic's sea slug Sea Bunny Slime (Critters' sea bunny is switched off in the pack), and a
 * Critters clam handed to a Hybrid Aquatic otter is cracked open, a pearl one time in four, as the switched-off
 * Critters otter did.
 */
public final class CritterUnify {
    private static final String CAC = "crittersandcompanions";
    private static final Map<String, String> REPLACED_BY = Map.of(
            "naturalist:snail", CAC + ":snail",
            "naturalist:dragonfly", CAC + ":dragonfly");
    private static final Map<String, String> BOTTLED = Map.of(
            "hybrid_aquatic:scalyfoot_snail", CAC + ":snail_slime_bottle",
            "northstar:moon_snail", CAC + ":snail_slime_bottle",
            "hybrid_aquatic:sea_slug", CAC + ":sea_bunny_slime_bottle");
    private static final String OTTER = "hybrid_aquatic:otter";
    private static final ResourceLocation CLAM = ResourceLocation.fromNamespaceAndPath(CAC, "clam");
    private static final ResourceLocation PEARL = ResourceLocation.fromNamespaceAndPath(CAC, "pearl");
    /** Critters and Companions' SlimeHarvestBehaviour.HARVEST_COOLDOWN. */
    private static final long HARVEST_COOLDOWN = 6000;
    private static final String READY_AT = "create_galore_bridge:harvest_ready_at";

    private static final List<Mob> PENDING = new ArrayList<>();

    private CritterUnify() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CritterUnify::onJoin);
        NeoForge.EVENT_BUS.addListener(CritterUnify::onServerTick);
        NeoForge.EVENT_BUS.addListener(CritterUnify::onInteract);
    }

    private static String id(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        // Entities join while a chunk loads, when adding another one isn't safe, so they change on the next tick.
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Mob mob && REPLACED_BY.containsKey(id(mob))) {
            PENDING.add(mob);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        List<Mob> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Mob old : batch) {
            if (!old.isRemoved() && old.level() instanceof ServerLevel level) replace(level, old);
        }
    }

    private static void replace(ServerLevel level, Mob old) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(REPLACED_BY.get(id(old)))).orElse(null);
        if (type == null || !(type.create(level) instanceof Mob mob)) return;
        mob.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
        mob.setYHeadRot(old.getYHeadRot());
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(old.blockPosition()), MobSpawnType.CONVERSION, null);
        if (old instanceof AgeableMob oldAge && mob instanceof AgeableMob age) age.setAge(oldAge.getAge());
        if (old.hasCustomName()) {
            mob.setCustomName(old.getCustomName());
            mob.setCustomNameVisible(old.isCustomNameVisible());
        }
        if (old.isPersistenceRequired() || old.requiresCustomPersistence()) mob.setPersistenceRequired();
        mob.setNoAi(old.isNoAi());
        mob.setInvulnerable(old.isInvulnerable());
        mob.setHealth(Math.min(mob.getMaxHealth(), mob.getMaxHealth() * old.getHealth() / old.getMaxHealth()));
        old.discard();
        level.addFreshEntity(mob);
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        String id = id(target);
        InteractionResult result = InteractionResult.PASS;
        if (BOTTLED.containsKey(id) && held.is(Items.GLASS_BOTTLE)) {
            result = bottle(player, event, (LivingEntity) target, item(BOTTLED.get(id)));
        } else if (OTTER.equals(id) && held.is(item(CLAM))) {
            result = crackClam(player, held, (LivingEntity) target);
        }
        if (result != InteractionResult.PASS) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    private static Item item(String id) {
        return item(ResourceLocation.parse(id));
    }

    private static Item item(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    private static InteractionResult bottle(Player player, PlayerInteractEvent.EntityInteract event, LivingEntity animal, Item slime) {
        if (slime == Items.AIR || animal.isBaby()) return InteractionResult.PASS;
        // The wait is kept on the server only; the client just swings the arm.
        if (animal.level().isClientSide) return InteractionResult.SUCCESS;
        long now = animal.level().getGameTime();
        if (now < animal.getPersistentData().getLong(READY_AT)) return InteractionResult.CONSUME;
        animal.getPersistentData().putLong(READY_AT, now + HARVEST_COOLDOWN);
        player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(event.getItemStack(), player, new ItemStack(slime)));
        animal.level().playSound(null, animal.getX(), animal.getY(), animal.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0f, 1.8f);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult crackClam(Player player, ItemStack clam, LivingEntity otter) {
        if (otter.level().isClientSide) return InteractionResult.SUCCESS;
        ServerLevel level = (ServerLevel) otter.level();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, clam.copyWithCount(1)),
                otter.getX(), otter.getEyeY(), otter.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
        level.playSound(null, otter.getX(), otter.getY(), otter.getZ(), SoundEvents.TURTLE_EGG_BREAK, SoundSource.NEUTRAL, 0.8f, 1.5f);
        clam.consume(1, player);
        if (otter.getRandom().nextFloat() <= 0.25f) {
            ItemEntity pearl = new ItemEntity(level, otter.getX(), otter.getEyeY(), otter.getZ(), new ItemStack(item(PEARL)));
            pearl.setDeltaMovement(otter.getRandom().nextGaussian() * 0.05, 0.2, otter.getRandom().nextGaussian() * 0.05);
            level.addFreshEntity(pearl);
        }
        return InteractionResult.SUCCESS;
    }
}

package dev.creategalore.fishing.cast;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.net.CatchOffer;
import dev.creategalore.fishing.tide.data.FishLengthHolder;import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Catch and release by hand. A landed fish isn't put away at once: the angler lifts it and holds it (everyone sees
 * it, through the synced {@link #HELD} attachment), and a screen offers Keep (the fish and its XP) or Release (the
 * XP plus a quarter more; the fish swims off and the spot isn't pressured). The fish lives
 * {@link #ALIVE_TICKS} from the catch; after that it can only be kept, and it is kept automatically. Logging out
 * also keeps it.
 */
public final class CatchHolding {
    /** Two minutes. */
    public static final int ALIVE_TICKS = 20 * 120;
    /** Release bonus by hand (machines never get it). */
    public static final double RELEASE_BONUS = 1.25;
    /** Fish heavier than this are held in both hands. */
    public static final double TWO_HANDS_KG = 4.0;

    /**
     * What everyone sees: the fish in the angler's hands, with its length and weight (small fish items don't carry
     * them) so it's drawn at its real size. Not saved; only while holding.
     */
    public record Held(ItemStack fish, boolean twoHands, long aliveUntil, float lengthCm, float kg) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Held::fish, ByteBufCodecs.BOOL, Held::twoHands,
                ByteBufCodecs.VAR_LONG, Held::aliveUntil, ByteBufCodecs.FLOAT, Held::lengthCm,
                ByteBufCodecs.FLOAT, Held::kg, Held::new);
        public static final Held NONE = new Held(ItemStack.EMPTY, false, 0, 0, 0);

        public boolean holding() {
            return !fish.isEmpty();
        }
    }

    /** Server-side details of a held catch. */
    record Holding(ItemStack fish, Species species, BiteSampler.Bite bite, int keepXp, int releaseXp, long aliveUntil,
                   BlockPos water) {
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CreateGaloreFishing.ID);
    public static final Supplier<AttachmentType<Held>> HELD = ATTACHMENTS.register("held_catch",
            () -> AttachmentType.builder(() -> Held.NONE).sync(Held.STREAM_CODEC).build());
    /** Personal best weight per species, in grams; kept through death. */
    public static final Supplier<AttachmentType<Map<String, Integer>>> BESTS = ATTACHMENTS.register("catch_records",
            () -> AttachmentType.<Map<String, Integer>>builder(() -> new HashMap<>())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(HashMap::new, m -> m)).copyOnDeath().build());

    private static final Map<UUID, Holding> HOLDING = new HashMap<>();

    private CatchHolding() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer player) tick(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) decide(player, false);
        });
        // Dying keeps the fish: it goes into the inventory just before the inventory drops (or goes to a grave).
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) decide(player, false);
        });
        // In single player the server restarts inside the same game; nobody can be holding a fish from before.
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> HOLDING.clear());
    }

    public static boolean holding(ServerPlayer player) {
        return HOLDING.containsKey(player.getUUID());
    }

    /**
     * Starts holding a landed fish. {@code xp} is what keeping it gives; {@code water} is where it goes back if
     * released (the spot it was hooked).
     */
    public static void start(ServerPlayer player, ItemStack fish, Species species, BiteSampler.Bite bite, int xp, BlockPos water) {
        if (holding(player)) decide(player, false);
        long until = player.level().getGameTime() + ALIVE_TICKS;
        Holding h = new Holding(fish, species, bite, xp, (int) Math.round(xp * RELEASE_BONUS), until, water);
        HOLDING.put(player.getUUID(), h);
        player.setData(HELD, new Held(fish.copy(), bite.kg() >= TWO_HANDS_KG, until, (float) bite.lengthCm(), (float) bite.kg()));
        offer(player);
    }

    /** (Re)opens the Keep / Release screen for the held fish. */
    public static void offer(ServerPlayer player) {
        Holding h = HOLDING.get(player.getUUID());
        if (h == null) return;
        int best = player.getData(BESTS).getOrDefault(h.species().id().toString(), 0);
        PacketDistributor.sendToPlayer(player, new CatchOffer(h.fish(), h.species().rarity().getSerializedName(),
                h.bite().size().key(), (float) h.bite().kg(), (float) h.bite().lengthCm(), h.keepXp(), h.releaseXp(),
                h.aliveUntil(), best));
    }

    private static void tick(ServerPlayer player) {
        Holding h = HOLDING.get(player.getUUID());
        if (h != null && player.level().getGameTime() >= h.aliveUntil()) {
            player.displayClientMessage(Component.translatable("message.create_galore_fishing.fish_died"), true);
            decide(player, false);
        }
    }

    /** The fish {@code player} is holding up, while it is still alive; empty otherwise. */
    public static ItemStack aliveFish(ServerPlayer player) {
        Holding h = HOLDING.get(player.getUUID());
        return h == null || player.level().getGameTime() >= h.aliveUntil() ? ItemStack.EMPTY : h.fish().copy();
    }

    /**
     * Keeps the held fish alive somewhere other than the inventory (an aquarium): the angler gets the keep XP and
     * the personal best as for keeping it, and the fish item is returned for the tank. Empty if it isn't alive.
     */
    public static ItemStack keepAlive(ServerPlayer player) {
        ItemStack fish = aliveFish(player);
        if (fish.isEmpty()) return fish;
        Holding h = HOLDING.remove(player.getUUID());
        player.setData(HELD, Held.NONE);
        ServerLevel level = player.serverLevel();
        ExperienceOrb.award(level, player.position().add(0, 0.5, 0), h.keepXp());
        FishingPressure.of(level).addCatch(level, h.water());
        recordBest(player, h);
        return fish;
    }

    /** Applies the angler's choice (or the default: keep). */
    public static void decide(ServerPlayer player, boolean release) {
        Holding h = HOLDING.remove(player.getUUID());
        if (h == null) return;
        player.setData(HELD, Held.NONE);
        ServerLevel level = player.serverLevel();
        boolean alive = level.getGameTime() < h.aliveUntil();
        if (release && alive) {
            ExperienceOrb.award(level, player.position().add(0, 0.5, 0), h.releaseXp());
            swimOff(level, h);
            player.displayClientMessage(Component.translatable("message.create_galore_fishing.released",
                    h.fish().getHoverName(), h.releaseXp()), true);
        } else {
            Catches.give(player, List.of(h.fish()), h.keepXp());
            FishingPressure.of(level).addCatch(level, h.water());
            recordBest(player, h);
        }
    }

    private static void recordBest(ServerPlayer player, Holding h) {
        recordBest(player, h.species(), h.bite().kg());
    }

    /** Notes a catch of {@code kg} as {@code player}'s personal best for the species if it beats it. */
    public static void recordBest(ServerPlayer player, Species species, double kg) {
        Map<String, Integer> bests = player.getData(BESTS);
        String key = species.id().toString();
        int grams = (int) Math.round(kg * 1000);
        if (grams > bests.getOrDefault(key, 0)) {
            Map<String, Integer> copy = new HashMap<>(bests);
            copy.put(key, grams);
            player.setData(BESTS, copy);
        }
    }

    /** The released fish goes back: as its own swimming mob where the species has one, else just a splash. */
    static void swimOff(ServerLevel level, Holding h) {
        BlockPos at = h.water();
        level.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 20, 0.3, 0.1, 0.3, 0.1);
        level.playSound(null, at, SoundEvents.FISH_SWIM, SoundSource.NEUTRAL, 0.8f, 1f);
        EntityType<?> type = entityFor(h.species());
        if (type == null || FishingWater.medium(level, at) == null) return;
        Entity fish = type.spawn(level, at, MobSpawnType.BUCKET);
        if (fish == null) return;
        fish.setDeltaMovement(level.random.nextGaussian() * 0.2, -0.1, level.random.nextGaussian() * 0.2);
        // Tide's fish mobs carry their length; this one is the fish that was caught.
        if (fish instanceof FishLengthHolder sized) sized.tide$setLength(h.bite().lengthCm());
    }

    /** The live mob for a species: its display entity, else an entity with the fish item's id. */
    public static EntityType<?> entityFor(Species species) {
        ResourceLocation id = species.tide().display().map(d -> d.entityKey().location()).orElse(species.id());
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.get(id) : null;
    }
}

package dev.creategalore.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.athisworkshop.animalhusbandry.care.AnimalCareData;
import org.athisworkshop.animalhusbandry.config.ModConfig;
import org.athisworkshop.animalhusbandry.genetics.AnimalGenetics;
import org.athisworkshop.animalhusbandry.registry.ModAttachments;
import org.athisworkshop.animalhusbandry.registry.neoforge.ModAttachmentsImpl;

/**
 * Animal Husbandry's tamed animals only mate on their own: about every 5 s each healthy adult with happiness 80+ may
 * look for a tamed, healthy, happy adult of the other sex within 8 blocks. Its Farm Magnifying Glass said "Breeding:
 * Ready" whenever the animal had no cooldown, and showed neither its sex nor its happiness, so a pen that never mates
 * (all ewes, or unhappy from crowding) looked fine. The glass now shows the sex, the happiness, why it isn't mating
 * yet (happiness, no mate, sick) and what is pulling happiness down (crowding, hunger). Everything else on the
 * hologram is as Animal Husbandry draws it.
 *
 * The Fertility Potion marks the first animal of a pair and waits for the second, but the mark never went away: once
 * the first animal's 30 s of love ran out, or after the pair had mated (the male), a later potion paired with that
 * old mark, was used up and nothing happened. The mark now ends with the love (a pregnant mother keeps it, its lamb's
 * bonus needs it).
 *
 * The hologram is a text display riding the animal, and Animal Husbandry only took it down from the animal's own
 * tick after 5 s. An animal killed or removed in those 5 s dropped its rider, and the panel then floated where the
 * animal had been, for good (it is saved with the world). Every glass hologram now takes itself down after 5 s, or as
 * soon as it isn't riding a living animal, and the ones already left floating go away when their chunk loads.
 * A sick cow gives no milk and a bull none at all, so the glass no longer counts milk for them.
 * The glass also says when the animal next eats and drinks (a meal or a drink lasts two and a half days).
 *
 * Animal Husbandry rolls for sickness once a day (or once for all the days a farm stood unloaded, the moment it runs
 * again), but a sick animal only showed what bothers it now, so one that fell ill while nobody watched gave no clue.
 * The moment an animal falls sick, the reasons that made the roll possible are kept with it (a sick animal within 4
 * blocks, crowding with the kinds counted, happiness under 50, days unfed, darkness), and the glass shows them with
 * when it happened until it is well again.
 *
 * The "kept dark" check read the light at whatever time of day the animal's daily check fell on. The check keeps the
 * time of day it was tamed at (and a skipped night moves it), so a roofed barn lit by daylight through its open sides
 * passed by day but at night counted as dark: a sickness chance and 5 happiness lost each day, only for the animals
 * whose check happened to fall at night. During that check the light now counts the sky as at daytime, so a barn is
 * dark only when it is dim by day too (a torch-lit barn stays fine).
 */
public final class AnimalCare {
    private static final int MATE_HAPPINESS = 80;
    private static final double MATE_RANGE = 8.0;
    private static final double CROWD_RANGE = 4.0;
    private static final String HOLOGRAM = "AH_Hologram";
    private static final int HOLOGRAM_TICKS = 100;
    private static final int SICK_DAYS_HARMLESS = 4;
    private static final int UNHAPPY = 50;
    private static final int DARK_LIGHT = 4;
    private static final long DAY = 24000L;
    private static final String SICK_CAUSE = "create_galore_bridge:sick_cause";
    /** Healthy tamed animals having their daily check this tick, and how many days it covers. */
    private static final Map<Animal, Integer> CHECKING = new WeakHashMap<>();
    private static Level daylightLevel;

    private AnimalCare() {
    }

    public static void register() {
        if (!ModList.get().isLoaded("animalhusbandry")) return;
        NeoForge.EVENT_BUS.addListener(AnimalCare::onTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, AnimalCare::beforeDailyCheck);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, AnimalCare::afterDailyCheck);
    }

    /** True while an animal in this level has its daily care check: its light counts the sky as at daytime. */
    public static boolean daylightCheck(Level level) {
        return daylightLevel == level;
    }

    /** Runs before Animal Husbandry's daily check (same tick and test as its own). */
    private static void beforeDailyCheck(EntityTickEvent.Pre event) {
        daylightLevel = null;
        if (!(event.getEntity() instanceof Animal animal) || animal.level().isClientSide) return;
        if (animal.level().getGameTime() % 100L != 0L || !animal.hasData(ModAttachmentsImpl.CARE_DATA.get())) return;
        AnimalCareData care = ModAttachments.getCareData(animal);
        if (care == null || !care.isDomesticated) return;
        long passed = animal.level().getDayTime() - care.lastTickTime;
        if (passed < DAY) return;
        daylightLevel = animal.level();
        if (!care.isSick) CHECKING.put(animal, (int) (passed / DAY));
    }

    /**
     * Runs right after Animal Husbandry's daily check, before anything else changes the animal: an animal that just
     * fell sick keeps the reasons its roll had, measured the way Animal Husbandry measured them.
     */
    private static void afterDailyCheck(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Animal animal) || animal.level().isClientSide) return;
        daylightLevel = null;
        Integer days = CHECKING.remove(animal);
        if (days == null) return;
        AnimalCareData care = ModAttachments.getCareData(animal);
        if (care == null || !care.isSick) return;
        AnimalGenetics genetics = ModAttachments.getGenetics(animal);
        CompoundTag cause = new CompoundTag();
        cause.putLong("day", animal.level().getDayTime() / DAY);
        cause.putInt("days", days);
        List<Animal> near = animal.level().getEntitiesOfClass(Animal.class, animal.getBoundingBox().inflate(CROWD_RANGE));
        if (genetics == null || !"iron_stomach".equals(genetics.trait)) {
            Map<String, Integer> sick = new TreeMap<>();
            for (Animal a : near) {
                if (a == animal || !a.hasData(ModAttachmentsImpl.CARE_DATA.get())) continue;
                AnimalCareData c = ModAttachments.getCareData(a);
                if (c != null && c.isSick) sick.merge(a.getType().getDescription().getString(), 1, Integer::sum);
            }
            if (!sick.isEmpty()) cause.putString("from", kinds(sick));
        }
        if (near.size() > ModConfig.INSTANCE.crammerMaxAnimals) {
            Map<String, Integer> kinds = new TreeMap<>();
            for (Animal a : near) kinds.merge(a.getType().getDescription().getString(), 1, Integer::sum);
            cause.putInt("crowd", near.size());
            cause.putString("crowd_kinds", kinds(kinds));
        }
        if (care.happiness < UNHAPPY) cause.putInt("happiness", care.happiness);
        if (care.daysSinceFed > ModConfig.INSTANCE.daysWithoutFoodUntilSick) cause.putInt("unfed", care.daysSinceFed);
        if (!animal.level().canSeeSky(animal.blockPosition()) && daylightBrightness(animal) < DARK_LIGHT) {
            cause.putInt("light", daylightBrightness(animal));
        }
        animal.getPersistentData().put(SICK_CAUSE, cause);
    }

    private static int daylightBrightness(Animal animal) {
        return animal.level().getMaxLocalRawBrightness(animal.blockPosition(), 0);
    }

    /** "5 Cow, 2 Bee", most first. */
    private static String kinds(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Integer> e : list) {
            if (!out.isEmpty()) out.append(", ");
            out.append(e.getValue()).append(' ').append(e.getKey());
        }
        return out.toString();
    }

    /** Why the animal fell sick, as kept the moment it did, or null when nothing was kept. */
    private static String sickCause(Animal animal) {
        CompoundTag data = animal.getPersistentData();
        if (!data.contains(SICK_CAUSE)) return null;
        CompoundTag cause = data.getCompound(SICK_CAUSE);
        List<String> reasons = new ArrayList<>();
        if (cause.contains("crowd")) reasons.add("crowded (" + cause.getInt("crowd") + " animals within 4 blocks: " + cause.getString("crowd_kinds") + ")");
        if (cause.contains("happiness")) reasons.add("unhappy (happiness " + cause.getInt("happiness") + ", under " + UNHAPPY + ")");
        if (cause.contains("unfed")) reasons.add("unfed (" + days(cause.getInt("unfed")) + ")");
        if (cause.contains("light")) reasons.add("kept dark (no sky, light " + cause.getInt("light") + " by day)");
        StringBuilder text = new StringBuilder("§cWhy it got sick: ");
        if (cause.contains("from")) {
            text.append("a sick animal nearby (").append(cause.getString("from")).append(")");
            if (!reasons.isEmpty()) text.append("; or ");
        }
        if (reasons.isEmpty() && !cause.contains("from")) text.append("§7no cause seen");
        text.append(String.join("; ", reasons));
        int days = cause.getInt("days");
        long ago = animal.level().getDayTime() / DAY - cause.getLong("day");
        text.append("\n§7Fell sick ").append(ago <= 0 ? "today" : days((int) ago) + " ago");
        if (days > 1) text.append(", when its farm ran again after " + days(days) + " unloaded");
        return text.append("\n").toString();
    }

    private static void onTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Display.TextDisplay display) {
            if (!display.level().isClientSide && display.getTags().contains(HOLOGRAM)
                && (display.tickCount > HOLOGRAM_TICKS || !(display.getVehicle() instanceof Animal animal) || !animal.isAlive())) {
                display.discard();
            }
            return;
        }
        if (!(event.getEntity() instanceof Animal animal) || animal.level().isClientSide || animal.tickCount % 20 != 0) return;
        if (!animal.hasData(ModAttachmentsImpl.CARE_DATA.get())) return;
        AnimalCareData care = ModAttachments.getCareData(animal);
        if (!care.isSick && animal.getPersistentData().contains(SICK_CAUSE)) animal.getPersistentData().remove(SICK_CAUSE);
        if (care.isFertilityBoosted && !care.isPregnant && !animal.isInLove()) {
            care.isFertilityBoosted = false;
            ModAttachments.setCareData(animal, care);
        }
    }

    /** The Farm Magnifying Glass hologram for a tamed animal, as Animal Husbandry draws it plus sex, happiness and why it isn't mating. */
    public static void inspect(Player player, Animal animal, AnimalCareData care) {
        animal.getPassengers().forEach(p -> {
            if (p instanceof Display.TextDisplay && p.getTags().contains(HOLOGRAM)) p.discard();
        });
        AnimalGenetics genetics = ModAttachments.getGenetics(animal);
        boolean male = genetics != null && genetics.isMale;
        MutableComponent text = Component.literal("§6§l✦ " + animal.getName().getString() + " ✦\n");
        text.append(Component.literal(male ? "§b♂ Male\n" : "§d♀ Female\n"));
        if (!care.isSick) {
            text.append(Component.literal("§fStatus: §a✔ HEALTHY\n"));
        } else {
            text.append(Component.literal("§fStatus: §c✖ SICK " + (care.daysSick > SICK_DAYS_HARMLESS ? "§c(losing health)" : "§7(day " + (care.daysSick + 1) + ")")
                + "\n"));
            String cause = sickCause(animal);
            text.append(Component.literal(cause != null ? cause : "§7Why it got sick: not recorded\n"));
            text.append(Component.literal("§7Animal Medicine cures it\n"));
        }
        text.append(Component.literal("§fHappiness: " + (care.happiness >= MATE_HAPPINESS ? "§a" : "§e") + care.happiness
            + " §7(mates at " + MATE_HAPPINESS + ")\n"));

        int age = animal.getAge();
        boolean pregnant = !male && care.isPregnant;
        if (age < 0) {
            text.append(Component.literal("§fGrowth: §e~" + -age / 1200 + "m\n"));
        } else if (pregnant) {
            text.append(Component.literal("§fPregnancy: §d♥ Pregnant! §7(~" + (int) Math.ceil(care.pregnancyTicks / 24000.0) + "d)\n"));
        } else if (age > 0) {
            text.append(Component.literal("§fBreeding: §e~" + age / 1200 + "m\n"));
        } else {
            text.append(Component.literal("§fBreeding: " + whyNotMating(animal, care, male) + "\n"));
        }

        int crowd = animal.level().getEntitiesOfClass(Animal.class, animal.getBoundingBox().inflate(CROWD_RANGE)).size();
        if (crowd > ModConfig.INSTANCE.crammerMaxAnimals) {
            text.append(Component.literal("§cCrowded: " + crowd + " animals within 4 blocks §7(happiness falls)\n"));
        }
        if (care.daysSinceFed > 1) {
            text.append(Component.literal("§cHungry: " + care.daysSinceFed + " days §7(happiness falls)\n"));
        } else {
            text.append(Component.literal("§fFood: " + (care.daysSinceFed > 0 ? "§eHungry"
                : "§a✔ next meal in " + days(AnimalTroughs.daysLeft(animal, true) + 1)) + "\n"));
        }
        text.append(Component.literal("§fWater: " + (care.daysDehydrated > 0 ? "§eThirsty"
            : "§a✔ next drink in " + days(AnimalTroughs.daysLeft(animal, false) + 1)) + "\n"));
        if (care.daysSinceFed > 0 && !AnimalTroughs.foodInReach(animal)) {
            text.append(Component.literal("§cNo trough it can reach has its food §7(eats " + AnimalTroughs.foodNames(animal)
                + ", within " + AnimalTroughs.REACH + " blocks)\n"));
        }

        if (animal instanceof Cow && !male && care.isSick) {
            text.append(Component.literal("§fMilk: §cnone while sick"));
        } else if (animal instanceof Cow && !male) {
            long today = animal.level().getDayTime() / 24000L;
            int milked = today > care.lastMilkedTime / 24000L ? 0 : care.timesMilkedToday;
            int most = 1 + (int) ((genetics == null ? 0.5f : genetics.producYield) * 3.0f * care.getYieldMultiplier());
            int left = Math.max(0, most - milked);
            text.append(Component.literal("§fMilk: " + (left > 0 ? "§b" : "§c") + left + " / " + most));
        } else if (animal instanceof Sheep sheep) {
            text.append(Component.literal(sheep.readyForShearing() ? "§fWool: §a✔ Ready" : "§fWool: §eGrowing..."));
        }

        Display.TextDisplay hologram = EntityType.TEXT_DISPLAY.create(animal.level());
        if (hologram == null) return;
        hologram.setPos(animal.getX(), animal.getY() + animal.getBbHeight() + 0.4, animal.getZ());
        CompoundTag tag = new CompoundTag();
        hologram.saveWithoutId(tag);
        tag.putString("text", Component.Serializer.toJson(text, animal.registryAccess()));
        tag.putString("billboard", "center");
        tag.putInt("background", 0x40000000);
        tag.putBoolean("shadow", true);
        CompoundTag transformation = new CompoundTag();
        ListTag scale = new ListTag();
        for (int i = 0; i < 3; i++) scale.add(FloatTag.valueOf(0.65f));
        transformation.put("scale", scale);
        tag.put("transformation", transformation);
        hologram.load(tag);
        hologram.addTag(HOLOGRAM);
        animal.level().addFreshEntity(hologram);
        hologram.startRiding(animal, true);
    }

    private static String days(int days) {
        return days == 1 ? "1 day" : days + " days";
    }

    /** The same checks Animal Husbandry makes before two tamed animals fall in love, first one that fails. */
    private static String whyNotMating(Animal animal, AnimalCareData care, boolean male) {
        if (care.isSick) return "§cnot while sick";
        if (care.happiness < MATE_HAPPINESS) return "§eneeds happiness " + MATE_HAPPINESS;
        String other = male ? "♀ female" : "♂ male";
        List<? extends Animal> near = animal.level().getEntitiesOfClass(animal.getClass(), animal.getBoundingBox().inflate(MATE_RANGE),
            a -> a != animal && a.getAge() >= 0 && a.hasData(ModAttachmentsImpl.CARE_DATA.get()));
        int best = -1;
        boolean pregnant = false;
        for (Animal a : near) {
            AnimalCareData c = ModAttachments.getCareData(a);
            AnimalGenetics g = ModAttachments.getGenetics(a);
            if (!c.isDomesticated || c.isSick || g == null || g.isMale == male) continue;
            if (!g.isMale && c.isPregnant) {
                pregnant = true;
                continue;
            }
            best = Math.max(best, c.happiness);
        }
        if (best < 0) return pregnant ? "§e" + other + " nearby is pregnant" : "§cno tame " + other + " within 8 blocks";
        if (best < MATE_HAPPINESS) return "§e" + other + " nearby, happiness " + best;
        return "§a✔ Ready";
    }
}

package dev.creategalore.fishing.bait;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * What a fish eats, as far as bait goes. Every bait item and lure has one kind; every species has a preference
 * per kind (its diet). A bait the fish doesn't care for still catches it now and then, just rarely.
 */
public enum BaitKind implements StringRepresentable {
    /** Worms: most freshwater fish. */
    WORM(false),
    /** Maggots and grubs: panfish, trout. */
    MAGGOT(false),
    /** Insects (caterpillars, butterflies): trout, bass, panfish. */
    INSECT(false),
    /** Leeches: walleye, catfish, pike. */
    LEECH(false),
    /** Dough, bread, corn: carp and other bottom feeders. */
    DOUGH(false),
    /** Boilies (cooked fish meal and flour): carp, catfish. */
    BOILIE(false),
    /** Pieces of fish: sea fish, catfish, sharks. */
    CUT_BAIT(false),
    /** A live baitfish: predators. */
    LIVE_FISH(false),
    /** Squid strips: saltwater predators. */
    SQUID(false),
    /** Shrimp, crab: saltwater bottom fish. */
    CRUSTACEAN(false),
    /** Bait made for lava fish. */
    LAVA_BAIT(false),
    /** Bait made for void fish. */
    VOID_BAIT(false),
    /** Metal spoons that wobble and flash. */
    SPOON(true),
    /** Spinners with a turning blade. */
    SPINNER(true),
    /** Crankbaits that dive to a depth on the retrieve: the trolling lure. */
    CRANKBAIT(true),
    /** Weighted jigs worked off the bottom. */
    JIG(true),
    /** Surface lures that pop and splash. */
    TOPWATER(true),
    /** Soft plastic worms and swimbaits. */
    SOFT_PLASTIC(true);

    public static final Codec<BaitKind> CODEC = StringRepresentable.fromEnum(BaitKind::values);

    private final boolean lure;

    BaitKind(boolean lure) {
        this.lure = lure;
    }

    /** Artificial: reusable, not used up by a bite. */
    public boolean isLure() {
        return lure;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}

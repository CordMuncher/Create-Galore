package dev.creategalore.fishing.sim;

/**
 * Size classes, after Fishing Planet's Young / Common / Trophy / Unique. Small fish stack per class; trophies and
 * uniques are always single items with their exact numbers.
 */
public enum SizeClass {
    /** Below the species' usual size. */
    YOUNG,
    /** The usual size range. */
    COMMON,
    /** Above the usual range. */
    LARGE,
    /** Well into the record range: worth mounting. */
    TROPHY,
    /** Near the species' record: very rare. */
    UNIQUE;

    public boolean isTrophy() {
        return this == TROPHY || this == UNIQUE;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}

package dev.creategalore.fishing.machine;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;

/**
 * What a Geared or Fuel Reel Drive does with its line between bites, set with its value box.
 */
public enum DriveMode implements INamedIconOptions {
    /** Casts once and holds still: for floats, bottom rigs and trolling. The default. */
    WAIT(AllIcons.I_PAUSE),
    /** Lobs the rig out to the set distance and winds it back slowly to work a lure, then casts again. */
    CAST(AllIcons.I_SEQ_REPEAT);

    private final AllIcons icon;

    DriveMode(AllIcons icon) {
        this.icon = icon;
    }

    @Override
    public AllIcons getIcon() {
        return icon;
    }

    @Override
    public String getTranslationKey() {
        return "create_galore_fishing.drive_mode." + name().toLowerCase(java.util.Locale.ROOT);
    }
}

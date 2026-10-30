package dev.creategalore.fishing.machine;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.creategalore.fishing.sim.Gearbox;
import java.util.List;
import java.util.Locale;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The geared drives' three value boxes, all on the back face (the rod points out over the front): the mode on top,
 * the cast distance bottom left, the top gear bottom right. The drive's window ({@link DriveMenu}) sets the same
 * three, for when something covers the back face.
 */
final class DriveSlots {
    private DriveSlots() {
    }

    /** The behaviours a geared drive's block entity owns. */
    record Settings(ScrollOptionBehaviour<DriveMode> mode, DriveSetting distance, DriveSetting topGear) {
        void addTo(List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> list) {
            list.add(mode);
            list.add(distance);
            list.add(topGear);
        }

        /** The window's buttons: the next mode, the cast distance or top gear stepped by {@code by}. */
        void nextMode() {
            mode.setValue((mode.getValue() + 1) % DriveMode.values().length);
        }

        void stepDistance(int by) {
            distance.setValue(distance.getValue() + by);
        }

        void stepTopGear(int by) {
            topGear.setValue(topGear.getValue() + by);
        }

        void applyTo(ReelDrive drive) {
            drive.configure(mode.get(), distance.getValue(), topGear.getValue() - 1);
        }
    }

    static Settings create(SmartBlockEntity be) {
        ScrollOptionBehaviour<DriveMode> mode = new ScrollOptionBehaviour<>(DriveMode.class,
                Component.translatable("create_galore_fishing.drive.mode"), be, new Slot(8, 11));
        DriveSetting distance = new DriveSetting(DriveSetting.CAST_DISTANCE, "CastDistance", 1,
                Component.translatable("create_galore_fishing.drive.cast_distance"), be, new Slot(4.5, 4.5));
        distance.between(3, 48);
        distance.withFormatter(v -> v + "m");
        distance.value = 12;
        DriveSetting top = new DriveSetting(DriveSetting.TOP_GEAR, "TopGear", 2,
                Component.translatable("create_galore_fishing.drive.top_gear"), be, new Slot(11.5, 4.5));
        top.between(1, Gearbox.GEARS.length);
        top.withFormatter(v -> String.format(Locale.ROOT, "%.2fx", Gearbox.GEARS[Math.max(0, Math.min(v, Gearbox.GEARS.length) - 1)]));
        top.value = Gearbox.GEARS.length;
        return new Settings(mode, distance, top);
    }

    /**
     * A small box at ({@code x}, {@code y}) pixels on the back face. Create draws a box's number and icon
     * 5 font units in front of its anchor: 0.625 px at its usual scale of 0.5, but exactly 0.5 px at our 0.4.
     * Anchored half a pixel inside the face as Create's own boxes are, ours landed exactly on the casing and
     * z-fought with it (flickering, or hidden behind it under shaders), so the anchor sits on the face instead and
     * the readouts sit 0.5 px and the hover outline 0.4 px proud of it.
     */
    static final class Slot extends ValueBoxTransform.Sided {
        private final double x;
        private final double y;

        Slot(double x, double y) {
            this.x = x;
            this.y = y;
            scale = 0.4f;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(x, y, 16);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction == state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        }
    }
}

package dev.creategalore.fishing.machine;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * A second (or third) number box on a drive. Create keeps one behaviour per type on a block entity and saves every
 * scroll value under the same key, so each extra box needs its own type, save key and network id.
 */
final class DriveSetting extends ScrollValueBehaviour {
    static final BehaviourType<DriveSetting> CAST_DISTANCE = new BehaviourType<>("cgfish_cast_distance");
    static final BehaviourType<DriveSetting> TOP_GEAR = new BehaviourType<>("cgfish_top_gear");

    private final BehaviourType<DriveSetting> type;
    private final String key;
    private final int id;

    DriveSetting(BehaviourType<DriveSetting> type, String key, int id, Component label, SmartBlockEntity be, ValueBoxTransform slot) {
        super(label, be, slot);
        this.type = type;
        this.key = key;
        this.id = id;
    }

    @Override
    public BehaviourType<?> getType() {
        return type;
    }

    @Override
    public int netId() {
        return id;
    }

    @Override
    public String getClipboardKey() {
        return key;
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        nbt.putInt(key, value);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        if (nbt.contains(key)) value = nbt.getInt(key);
    }
}

package dev.creategalore.bridge.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reaches a mob's goals, so AnimalTroughs can swap Animal Husbandry's trough goal for its own. */
@Mixin(Mob.class)
public interface MobGoalsAccessor {
    @Accessor("goalSelector")
    GoalSelector create_galore_bridge$goals();
}

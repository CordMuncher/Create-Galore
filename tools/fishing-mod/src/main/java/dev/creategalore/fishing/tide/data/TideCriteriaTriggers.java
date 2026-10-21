/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data;

import dev.creategalore.fishing.tide.data.triggers.TideSimpleTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;

import dev.creategalore.fishing.tide.Tide;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.advancements.Criterion;
import java.util.Optional;

import java.util.HashMap;
import java.util.function.Function;

public class TideCriteriaTriggers {
    public static final HashMap<String, CriterionTrigger<?>> CRITERIA_TRIGGERS = new HashMap<>();

    public static TideSimpleTrigger PULLED_CRATE = (TideSimpleTrigger) register("pull_crate", TideSimpleTrigger::new);
    public static TideSimpleTrigger FISHED_IN_LAVA = (TideSimpleTrigger) register("fish_in_lava", TideSimpleTrigger::new);
    public static TideSimpleTrigger FISHED_IN_VOID = (TideSimpleTrigger) register("fish_in_void", TideSimpleTrigger::new);
    public static TideSimpleTrigger RELEASED_PUPFISH = (TideSimpleTrigger) register("released_pupfish", TideSimpleTrigger::new);
    public static TideSimpleTrigger FROZE_COELACANTH = (TideSimpleTrigger) register("froze_coelacanth", TideSimpleTrigger::new);
    public static TideSimpleTrigger JOURNAL_HALFWAY_COMPLETE = (TideSimpleTrigger) register("journal_halfway_complete", TideSimpleTrigger::new);
    public static TideSimpleTrigger JOURNAL_COMPLETE = (TideSimpleTrigger) register("journal_complete", TideSimpleTrigger::new);

    public static <T extends SimpleCriterionTrigger.SimpleInstance>
            SimpleCriterionTrigger<T> register(String key, Function<String, SimpleCriterionTrigger<T>> triggerFunc) {
        SimpleCriterionTrigger<T> trigger = triggerFunc.apply(key);
        CRITERIA_TRIGGERS.put(key, trigger);
        return Tide.PLATFORM.register(BuiltInRegistries.TRIGGER_TYPES, Tide.resource(key), trigger);

    }

    public static void init() {}

    public static Criterion<TideSimpleTrigger.TriggerInstance> simpleInstance(TideSimpleTrigger trigger) {
        return trigger.createCriterion(new TideSimpleTrigger.TriggerInstance(Optional.empty()));
    }

}

package dev.creategalore.bridge;

import com.drmangotea.tfmg.content.engines.types.AbstractSmallEngineBlockEntity;
import com.drmangotea.tfmg.content.engines.types.large_engine.LargeEngineBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.blast_furnace.BlastFurnaceOutputBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * How hard a TFMG machine is working right now, for Create: Smog. TFMG engines and the blast furnace
 * have no lit/burning block state, so Smog would count them as burning whenever they exist.
 */
public final class TfmgActivity {
    /** Smog's own rule applies. */
    public static final float UNKNOWN = -1f;

    private TfmgActivity() {}

    public static float of(BlockEntity be) {
        // Small engines (regular, radial, turbine): fuel and throttle live on the controller of the chain.
        // Fuel use scales with the redstone throttle, so pollution does too.
        if (be instanceof AbstractSmallEngineBlockEntity engine) {
            AbstractSmallEngineBlockEntity controller = engine.getControllerBE();
            return controller.rpm != 0f && controller.canWork() ? Mth.clamp(controller.highestSignal, 0f, 1f) : 0f;
        }
        // Large and simple large engines: burning when they have fuel, air and exhaust room and turn their shaft.
        if (be instanceof LargeEngineBlockEntity engine) {
            KineticBlockEntity shaft = engine.getShaft();
            return shaft != null && shaft.getSpeed() != 0f && engine.canWork()
                && engine.validFuels().test(engine.fuelTank.getFluid()) ? 1f : 0f;
        }
        // Blast furnace: burning while it smelts a charge with coke in it.
        if (be instanceof BlastFurnaceOutputBlockEntity furnace) {
            return furnace.timer > 0 && furnace.fuel > 0 && !furnace.inputInventory.isEmpty() ? 1f : 0f;
        }
        return UNKNOWN;
    }
}

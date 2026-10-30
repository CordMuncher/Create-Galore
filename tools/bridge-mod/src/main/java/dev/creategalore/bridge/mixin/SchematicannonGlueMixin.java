package dev.creategalore.bridge.mixin;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import com.simibubi.create.content.schematics.cannon.MaterialChecklist;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.StackRequirement;
import dev.creategalore.bridge.SchematicannonGlue;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pre-glue on the Schematicannon (see SchematicannonGlue). While it is on, a shot that places a block first makes sure
 * one use of Super Glue is paid for: it adds the glue to what Create takes from the chests for that block, so a cannon
 * without glue pauses on it like on any missing item. Each glue box the landing blocks need spends one paid use.
 */
@Mixin(value = SchematicannonBlockEntity.class, remap = false)
public abstract class SchematicannonGlueMixin implements SchematicannonGlue.Cannon {
    @Shadow
    public SchematicPrinter printer;
    @Shadow
    public boolean skipMissing;
    @Shadow
    public int blocksPlaced;
    @Shadow
    public List<LaunchedItem> flyingBlocks;
    @Shadow
    public MaterialChecklist checklist;

    @Shadow
    protected abstract boolean grabItemsFromAttachedInventories(StackRequirement required, boolean simulate);

    @Unique
    private boolean create_galore_bridge$preGlue;
    @Unique
    private int create_galore_bridge$charges;
    /** Uses added to the shot being weighed now; paid once it fires. */
    @Unique
    private int create_galore_bridge$asking;
    @Unique
    private SchematicLevel create_galore_bridge$schematic;
    @Unique
    private final List<SchematicannonGlue.Box> create_galore_bridge$boxes = new ArrayList<>();

    @Override
    public boolean create_galore_bridge$preGlue() {
        return create_galore_bridge$preGlue;
    }

    @Override
    public void create_galore_bridge$setPreGlue(boolean on) {
        create_galore_bridge$preGlue = on;
    }

    @Override
    public int create_galore_bridge$glueCharges() {
        return create_galore_bridge$charges;
    }

    @Override
    public void create_galore_bridge$spendGlue() {
        create_galore_bridge$charges--;
    }

    @Override
    public SchematicLevel create_galore_bridge$glueSchematic() {
        return create_galore_bridge$schematic;
    }

    @Override
    public List<SchematicannonGlue.Box> create_galore_bridge$glueBoxes() {
        return create_galore_bridge$boxes;
    }

    @Unique
    private static StackRequirement create_galore_bridge$glueUse() {
        return new StackRequirement(AllItems.SUPER_GLUE.asStack(), ItemUseType.DAMAGE);
    }

    @Redirect(method = "tickPrinter", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/schematics/SchematicPrinter;getCurrentRequirement()Lcom/simibubi/create/content/schematics/requirement/ItemRequirement;"))
    private ItemRequirement create_galore_bridge$withGlue(SchematicPrinter printer) {
        ItemRequirement requirement = printer.getCurrentRequirement();
        create_galore_bridge$asking = 0;
        if (!create_galore_bridge$preGlue || requirement.isInvalid()) return requirement;
        SchematicLevel schematic = ((SchematicPrinterAccessor) printer).create_galore_bridge$schematic();
        if (printer.getPrintStage() == SchematicPrinter.PrintStage.ENTITIES) {
            // The build's own glue boxes: pre-glue covers every pair they would, so they aren't printed (or paid for).
            List<Entity> entities = schematic.getEntityList();
            int index = ((SchematicPrinterAccessor) printer).create_galore_bridge$entityIndex();
            if (index >= 0 && index < entities.size() && entities.get(index) instanceof SuperGlueEntity) return ItemRequirement.INVALID;
            return requirement;
        }
        BlockState state = schematic.getBlockState(printer.getCurrentTarget());
        if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || requirement.isEmpty()) return requirement;
        if (create_galore_bridge$charges >= 1) return requirement;
        create_galore_bridge$asking = 1;
        return requirement.union(new ItemRequirement(create_galore_bridge$glueUse()));
    }

    /** Skip Missing never skips for want of glue: a block printed without it would be left unglued. */
    @Redirect(method = "tickPrinter", at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETFIELD,
            target = "Lcom/simibubi/create/content/schematics/cannon/SchematicannonBlockEntity;skipMissing:Z"))
    private boolean create_galore_bridge$noSkipWithoutGlue(SchematicannonBlockEntity self) {
        if (!skipMissing) return false;
        return create_galore_bridge$asking == 0 || grabItemsFromAttachedInventories(create_galore_bridge$glueUse(), true);
    }

    @Inject(method = "launchBlockOrBelt", at = @At("HEAD"))
    private void create_galore_bridge$paid(BlockPos target, ItemStack icon, BlockState state, BlockEntity be, CallbackInfo ci) {
        create_galore_bridge$charges += create_galore_bridge$asking;
        create_galore_bridge$asking = 0;
    }

    @Redirect(method = "tickFlyingBlocks", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/schematics/cannon/LaunchedItem;update(Lnet/minecraft/world/level/Level;)Z"))
    private boolean create_galore_bridge$glueLanded(LaunchedItem shot, Level level) {
        boolean landed = shot.update(level);
        if (landed && create_galore_bridge$preGlue && !level.isClientSide)
            SchematicannonGlue.landed((SchematicannonBlockEntity) (Object) this, shot);
        return landed;
    }

    @Inject(method = "tickFlyingBlocks", at = @At("TAIL"))
    private void create_galore_bridge$forgetBuild(CallbackInfo ci) {
        // The last shot has landed and the print is over: let the build go.
        if (create_galore_bridge$schematic != null && flyingBlocks.isEmpty() && !printer.isLoaded()) create_galore_bridge$schematic = null;
    }

    @Inject(method = "initializePrinter", at = @At("TAIL"))
    private void create_galore_bridge$keepBuild(ItemStack blueprint, CallbackInfo ci) {
        if (!printer.isLoaded() || printer.isErrored()) return;
        create_galore_bridge$schematic = ((SchematicPrinterAccessor) printer).create_galore_bridge$schematic();
        // A new print starts with no boxes of its own; one picked up again (after a restart) keeps growing its boxes.
        if (blocksPlaced == 0) create_galore_bridge$boxes.clear();
    }

    /** The clipboard and book list a use of glue while one is still to be paid for. */
    @Inject(method = "updateChecklist", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lcom/simibubi/create/content/schematics/SchematicPrinter;markAllEntityRequirements(Lcom/simibubi/create/content/schematics/cannon/MaterialChecklist;)V"))
    private void create_galore_bridge$listGlue(CallbackInfo ci) {
        if (create_galore_bridge$preGlue && create_galore_bridge$charges < 1)
            checklist.require(new ItemRequirement(create_galore_bridge$glueUse()));
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void create_galore_bridge$writeGlue(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        tag.putBoolean("CreateGalorePreGlue", create_galore_bridge$preGlue);
        if (clientPacket) return;
        tag.putInt("CreateGaloreGlueCharges", create_galore_bridge$charges);
        tag.put("CreateGaloreGlueBoxes", SchematicannonGlue.saveBoxes(create_galore_bridge$boxes));
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void create_galore_bridge$readGlue(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        create_galore_bridge$preGlue = tag.getBoolean("CreateGalorePreGlue");
        if (clientPacket) return;
        create_galore_bridge$charges = tag.getInt("CreateGaloreGlueCharges");
        SchematicannonGlue.loadBoxes(tag.getList("CreateGaloreGlueBoxes", Tag.TAG_COMPOUND), create_galore_bridge$boxes);
    }
}

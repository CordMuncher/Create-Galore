package dev.creategalore.bridge.mixin;

import javax.annotation.Nullable;

import com.simibubi.create.AllDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Delightful Creators overwrites NeoForge's IItemStackExtension.getFoodProperties so that every stack in the middle of
 * a sequenced assembly is food (3 hunger, 0.3 saturation), meant for half-made sandwiches and pies. That also made
 * every Incomplete Precision Mechanism, track, shell and circuit edible (238 recipes in the pack), with AppleSkin's food
 * icons in the tooltip. Declaring the method on ItemStack itself (a class method wins over the interface's default)
 * brings back NeoForge's rule, a stack is food when its item says so, and keeps Delightful Creators' rule only where
 * the assembly ends in food (Garnished and Create Cafe half-made foods have no food of their own).
 */
@Mixin(ItemStack.class)
public abstract class HalfBuiltNotFoodMixin {
    // Looked up on first use: ItemStack loads before the mod list exists.
    @Unique
    private static Boolean createGalore$halfFoods;
    @Unique
    private static final FoodProperties createGalore$halfFood = new FoodProperties.Builder().nutrition(3).fast().saturationModifier(0.3f).build();

    @Nullable
    public FoodProperties getFoodProperties(@Nullable LivingEntity entity) {
        ItemStack self = (ItemStack) (Object) this;
        FoodProperties own = self.getItem().getFoodProperties(self, entity);
        if (own != null) return own;
        if (createGalore$halfFoods == null) createGalore$halfFoods = ModList.get().isLoaded("delightfulcreators");
        if (!createGalore$halfFoods) return null;
        var assembly = self.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        if (assembly == null) return null;
        // The eater's world has the recipes; without one (a machine asking), the running server's.
        RecipeManager recipes;
        HolderLookup.Provider registries;
        if (entity != null) {
            recipes = entity.level().getRecipeManager();
            registries = entity.level().registryAccess();
        } else {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            recipes = server.getRecipeManager();
            registries = server.registryAccess();
        }
        return recipes.byKey(assembly.id()).map(h -> {
            ItemStack result = h.value().getResultItem(registries);
            return result.getItem().getFoodProperties(result, entity) != null ? createGalore$halfFood : null;
        }).orElse(null);
    }
}

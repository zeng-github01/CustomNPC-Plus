package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#WORKBENCH_RECIPES}.
 * RELOAD-only family — does not support individual UPDATE or REMOVE.
 * Client-side RELOAD routes through {@link RecipeController#reloadGlobalRecipes}.
 */
public class WorkbenchRecipeSyncHandler implements SyncHandler {

    private static final WorkbenchRecipeSyncHandler INSTANCE = new WorkbenchRecipeSyncHandler();

    public static WorkbenchRecipeSyncHandler getInstance() {
        return INSTANCE;
    }
    // ========== SERVER-SIDE ==========

    @Override
    public NBTTagCompound serializeAll() {
        RecipeController controller = RecipeController.Instance;
        NBTTagList list = new NBTTagList();
        NBTTagCompound compound = new NBTTagCompound();
        for (RecipeCarpentry recipe : controller.globalRecipes.values()) {
            list.appendTag(recipe.writeNBT(false));
        }
        compound.setTag("recipes", list);
        return compound;
    }

    // ========== CLIENT-SIDE ==========

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleReload(NBTTagCompound fullCompound) {
        NBTTagList list = fullCompound.getTagList("recipes", 10);
        if (list == null)
            return;

        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound recipeCompound = list.getCompoundTagAt(i);
            RecipeCarpentry recipe = RecipeCarpentry.create(recipeCompound);
            recipe.readNBT(recipeCompound);
            RecipeController.syncRecipes.put(recipe.id, recipe);
        }

        RecipeController.reloadGlobalRecipes(RecipeController.syncRecipes);
        RecipeController.syncRecipes = new HashMap<>();
    }

    @Override
    public Set<SyncType> getInvalidationTargets(SyncType self) {
        Set<SyncType> set = new HashSet<>();
        set.add(SyncType.WORKBENCH_RECIPES);
        return set;
    }

    // RELOAD-only: supportsUpdate() and supportsRemove() remain false (default)
}

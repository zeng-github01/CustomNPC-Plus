package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeAnvil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#ANVIL_RECIPES}.
 * RELOAD-only family — does not support individual UPDATE or REMOVE.
 */
public class AnvilRecipeSyncHandler implements SyncHandler {

    // ========== SERVER-SIDE ==========

    @Override
    public NBTTagCompound serializeAll() {
        RecipeController controller = RecipeController.Instance;
        NBTTagList list = new NBTTagList();
        NBTTagCompound compound = new NBTTagCompound();
        for (RecipeAnvil recipe : controller.anvilRecipes.values()) {
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
            RecipeAnvil recipe = new RecipeAnvil();
            recipe.readNBT(list.getCompoundTagAt(i));
            RecipeController.syncAnvilRecipes.put(recipe.id, recipe);
        }

        RecipeController.Instance.anvilRecipes = RecipeController.syncAnvilRecipes;
        RecipeController.syncAnvilRecipes = new HashMap<>();
    }
    
    // RELOAD-only: supportsUpdate() and supportsRemove() remain false (default)
}

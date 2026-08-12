package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.DialogController;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.DialogCategory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#DIALOG_CATEGORY}.
 * Parent-child category family. DIALOG children share this cache.
 * Invalidation from DIALOG or DIALOG_CATEGORY both target this cache.
 */
public class DialogCategorySyncHandler implements SyncHandler {

    /**
     * Serialize a single dialog category for UPDATE packets.
     */
    public static NBTTagCompound serializeCategory(DialogCategory dialogCategory) {
        NBTTagCompound dialogCompound = new NBTTagCompound();
        NBTTagList dialogList = new NBTTagList();
        for (int questID : dialogCategory.dialogs.keySet()) {
            Dialog dialog = dialogCategory.dialogs.get(questID);
            dialogList.appendTag(dialog.writeToNBT(new NBTTagCompound()));
        }
        dialogCompound.setTag("Data", dialogList);
        dialogCompound.setTag("CatNBT", dialogCategory.writeSmallNBT(new NBTTagCompound()));
        return dialogCompound;
    }
    
    // ========== SERVER-SIDE ==========

    @Override
    public NBTTagCompound serializeAll() {
        NBTTagCompound compound = new NBTTagCompound();
        NBTTagList categoryList = new NBTTagList();
        for (DialogCategory category : DialogController.Instance.categories.values()) {
            NBTTagCompound questCompound = new NBTTagCompound();
            NBTTagList dialogList = new NBTTagList();
            for (int dialogID : category.dialogs.keySet()) {
                Dialog quest = category.dialogs.get(dialogID);
                dialogList.appendTag(quest.writeToNBT(new NBTTagCompound()));
            }
            questCompound.setTag("Data", dialogList);
            questCompound.setTag("CatNBT", category.writeSmallNBT(new NBTTagCompound()));
            categoryList.appendTag(questCompound);
        }
        compound.setTag("DialogCategories", categoryList);
        return compound;
    }


    // ========== CLIENT-SIDE ==========

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleReload(NBTTagCompound fullCompound) {
        if (!fullCompound.hasNoTags()) {
            NBTTagList categories = fullCompound.getTagList("DialogCategories", 10);
            for (int j = 0; j < fullCompound.getTagList("DialogCategories", 10).tagCount(); j++) {
                NBTTagCompound categoryCompound = categories.getCompoundTagAt(j);
                if (categoryCompound.hasNoTags())
                    continue;

                DialogCategory category = new DialogCategory();
                category.readSmallNBT(categoryCompound.getCompoundTag("CatNBT"));
                NBTTagList dialogList = categoryCompound.getTagList("Data", 10);
                if (DialogController.Instance.categoriesSync.containsKey(category.id)) {
                    category = DialogController.Instance.categoriesSync.get(category.id);
                    category.readSmallNBT(categoryCompound.getCompoundTag("CatNBT"));
                }
                for (int i = 0; i < dialogList.tagCount(); i++) {
                    Dialog dialog = new Dialog();
                    dialog.readNBT(dialogList.getCompoundTagAt(i));
                    dialog.category = category;
                    category.dialogs.put(dialog.id, dialog);
                }
                DialogController.Instance.categoriesSync.put(category.id, category);
            }
        }

        HashMap<Integer, Dialog> dialogs = new HashMap<>();
        for (DialogCategory category : DialogController.Instance.categoriesSync.values()) {
            for (Dialog dialog : category.dialogs.values()) {
                dialogs.put(dialog.id, dialog);
            }
        }

        DialogController.Instance.categories = DialogController.Instance.categoriesSync;
        DialogController.Instance.dialogs = dialogs;
        DialogController.Instance.categoriesSync = new HashMap<>();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleUpdate(NBTTagCompound compound, int categoryId) {
        DialogCategory category = new DialogCategory();
        category.readSmallNBT(compound.getCompoundTag("CatNBT"));
        NBTTagList list = compound.getTagList("Data", 10);
        if (DialogController.Instance.categoriesSync.containsKey(category.id)) {
            category = DialogController.Instance.categoriesSync.get(category.id);
            category.readSmallNBT(compound.getCompoundTag("CatNBT"));
        }
        for (int i = 0; i < list.tagCount(); i++) {
            Dialog dialog = new Dialog();
            dialog.readNBT(list.getCompoundTagAt(i));
            dialog.category = category;
            category.dialogs.put(dialog.id, dialog);
        }
        DialogController.Instance.categories.put(category.id, category);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleRemove(int id) {
        DialogCategory dialogCategory = DialogController.Instance.categories.remove(id);
        if (dialogCategory != null) {
            DialogController.Instance.dialogs.keySet().removeAll(dialogCategory.dialogs.keySet());
        }
    }

    @Override
    public boolean supportsUpdate() {
        return true;
    }

    @Override
    public boolean supportsRemove() {
        return true;
    }
    
}

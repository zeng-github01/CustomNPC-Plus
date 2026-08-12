package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.DialogController;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.DialogCategory;

import java.util.HashSet;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#DIALOG}.
 * Child family — does not have its own cache. UPDATE and REMOVE only.
 * Invalidation targets the parent {@link SyncType#DIALOG_CATEGORY} cache.
 */
public class DialogSyncHandler implements SyncHandler {

    // ========== SERVER-SIDE ==========

    /**
     * DIALOG does not have its own independent cache; it shares
     * the DIALOG_CATEGORY parent cache.
     */
    @Override
    public NBTTagCompound serializeAll() {
        return null;
    }

    // ========== CLIENT-SIDE ==========

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleReload(NBTTagCompound fullCompound) {
        // DIALOG does not handle standalone RELOAD; it's part of DIALOG_CATEGORY
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleUpdate(NBTTagCompound compound, int categoryId) {
        DialogCategory category = DialogController.Instance.categories.get(categoryId);
        if (category == null) {
            return; // Defensive: category may have been removed
        }
        Dialog dialog = new Dialog();
        dialog.category = category;
        dialog.readNBT(compound);
        DialogController.Instance.dialogs.put(dialog.id, dialog);
        category.dialogs.put(dialog.id, dialog);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleRemove(int id) {
        Dialog dialog = DialogController.Instance.dialogs.remove(id);
        if (dialog != null) {
            dialog.category.dialogs.remove(id);
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

    /**
     * Not a cached type — DIALOG shares the DIALOG_CATEGORY cache.
     */
    @Override
    public boolean isCachedType() {
        return false;
    }

    @Override
    public Set<SyncType> getInvalidationTargets(SyncType self) {
        // Child invalidates the parent cache
        Set<SyncType> set = new HashSet<>();
        set.add(SyncType.DIALOG_CATEGORY);
        return set;
    }
}

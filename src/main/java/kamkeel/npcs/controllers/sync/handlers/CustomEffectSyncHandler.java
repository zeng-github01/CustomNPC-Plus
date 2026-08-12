package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.client.ClientCacheHandler;
import noppes.npcs.controllers.CustomEffectController;
import noppes.npcs.controllers.data.CustomEffect;

import java.util.HashSet;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#CUSTOM_EFFECTS}.
 * Simple int-keyed map family with RELOAD, UPDATE, and REMOVE support.
 * Client-side RELOAD precaches effect icons via {@link ClientCacheHandler#getImageData}.
 */
public class CustomEffectSyncHandler implements SyncHandler {

    // ========== SERVER-SIDE ==========

    @Override
    public NBTTagCompound serializeAll() {
        NBTTagList list = new NBTTagList();
        NBTTagCompound compound = new NBTTagCompound();
        for (CustomEffect effect : CustomEffectController.getInstance().getCustomEffects().values()) {
            list.appendTag(effect.writeToNBT(false));
        }
        compound.setTag("Data", list);
        return compound;
    }

    // ========== CLIENT-SIDE ==========

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleReload(NBTTagCompound fullCompound) {
        NBTTagList list = fullCompound.getTagList("Data", 10);
        CustomEffectController ce = CustomEffectController.getInstance();
        ce.customEffectsSync.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            CustomEffect effect = new CustomEffect();
            effect.readFromNBT(list.getCompoundTagAt(i));
            ClientCacheHandler.getImageData(effect.icon);
            ce.customEffectsSync.put(effect.id, effect);
        }

        ce.getCustomEffects().clear();
        ce.getCustomEffects().putAll(ce.customEffectsSync);
        ce.customEffectsSync.clear();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleUpdate(NBTTagCompound compound, int categoryId) {
        CustomEffect effect = new CustomEffect();
        effect.readFromNBT(compound);
        ClientCacheHandler.getImageData(effect.icon);

        CustomEffectController.Instance.getCustomEffects().put(effect.id, effect);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleRemove(int id) {
        CustomEffectController.Instance.getCustomEffects().remove(id);
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

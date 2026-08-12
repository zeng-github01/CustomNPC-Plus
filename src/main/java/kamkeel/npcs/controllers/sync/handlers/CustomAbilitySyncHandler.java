package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.AbilityController;
import kamkeel.npcs.controllers.data.ability.Ability;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#CUSTOM_ABILITY}.
 * String-keyed family using ordered {@link LinkedHashMap} to preserve
 * registration/reload order. Supports RELOAD only for global sync.
 */
public class CustomAbilitySyncHandler implements SyncHandler {

    // ========== SERVER-SIDE ==========

    @Override
    public NBTTagCompound serializeAll() {
        NBTTagList list = new NBTTagList();
        NBTTagCompound compound = new NBTTagCompound();
        for (Map.Entry<String, Ability> entry : AbilityController.Instance.getCustomAbilities().entrySet()) {
            NBTTagCompound abilityNBT = entry.getValue().writeNBT(false);
            abilityNBT.setString("CustomAbilityId", entry.getKey());
            list.appendTag(abilityNBT);
        }
        compound.setTag("Data", list);
        return compound;
    }

    // ========== CLIENT-SIDE ==========

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleReload(NBTTagCompound fullCompound) {
        NBTTagList list = fullCompound.getTagList("Data", 10);
        LinkedHashMap<String, Ability> sync = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound nbt = list.getCompoundTagAt(i);
            String name = nbt.getString("CustomAbilityId");
            Ability ability = AbilityController.Instance.fromNBT(nbt);
            if (ability != null) {
                ability.setName(name);
                sync.put(name, ability);
            }
        }
        AbilityController.Instance.setCustomAbilities(sync);
    }
    
    // RELOAD-only for global sync: supportsUpdate() and supportsRemove() remain false (default)
}

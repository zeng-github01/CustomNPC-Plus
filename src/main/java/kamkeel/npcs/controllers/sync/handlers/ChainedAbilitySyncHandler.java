package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.AbilityController;
import kamkeel.npcs.controllers.data.ability.data.ChainedAbility;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#CHAINED_ABILITY}.
 * String-keyed family using ordered {@link LinkedHashMap} to preserve
 * registration/reload order. Supports RELOAD only for global sync.
 */
public class ChainedAbilitySyncHandler implements SyncHandler {

    // ========== SERVER-SIDE ==========

    @Override
    public NBTTagCompound serializeAll() {
        NBTTagList list = new NBTTagList();
        NBTTagCompound compound = new NBTTagCompound();
        for (Map.Entry<String, ChainedAbility> entry : AbilityController.Instance.getChainedAbilities().entrySet()) {
            NBTTagCompound chainNBT = entry.getValue().writeNBT(false);
            chainNBT.setString("ChainedAbilityId", entry.getKey());
            list.appendTag(chainNBT);
        }
        compound.setTag("Data", list);
        return compound;
    }

    // ========== CLIENT-SIDE ==========

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleReload(NBTTagCompound fullCompound) {
        NBTTagList list = fullCompound.getTagList("Data", 10);
        LinkedHashMap<String, ChainedAbility> sync = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound nbt = list.getCompoundTagAt(i);
            String id = nbt.getString("ChainedAbilityId");
            ChainedAbility chain = new ChainedAbility();
            chain.readNBT(nbt);
            chain.setName(id);
            sync.put(id, chain);
        }
        AbilityController.Instance.setChainedAbilities(sync);
    }
    
    // RELOAD-only for global sync: supportsUpdate() and supportsRemove() remain false (default)
}

package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.FactionController;
import noppes.npcs.controllers.data.Faction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#FACTION}.
 * Simple int-keyed map family with RELOAD, UPDATE, and REMOVE support.
 */
public class FactionSyncHandler implements SyncHandler {

    // ========== SERVER-SIDE ==========

    @Override
    public NBTTagCompound serializeAll() {
        NBTTagList list = new NBTTagList();
        NBTTagCompound compound = new NBTTagCompound();
        for (Faction faction : FactionController.getInstance().factions.values()) {
            NBTTagCompound factionNBT = new NBTTagCompound();
            faction.writeNBT(factionNBT);
            list.appendTag(factionNBT);
        }
        compound.setTag("Factions", list);
        return compound;
    }

    // ========== CLIENT-SIDE ==========

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleReload(NBTTagCompound fullCompound) {
        NBTTagList list = fullCompound.getTagList("Factions", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            Faction faction = new Faction();
            faction.readNBT(list.getCompoundTagAt(i));
            FactionController.getInstance().factionsSync.put(faction.id, faction);
        }
        FactionController.getInstance().factions = FactionController.getInstance().factionsSync;
        FactionController.getInstance().factionsSync = new HashMap<Integer, Faction>();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleUpdate(NBTTagCompound compound, int categoryId) {
        Faction faction = new Faction();
        faction.readNBT(compound);
        FactionController.getInstance().factions.put(faction.id, faction);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleRemove(int id) {
        FactionController.getInstance().factions.remove(id);
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

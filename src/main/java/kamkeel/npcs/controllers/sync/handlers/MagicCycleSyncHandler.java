package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.MagicController;
import noppes.npcs.controllers.data.MagicCycle;

import java.util.HashSet;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#MAGIC_CYCLE}.
 * Simple int-keyed map family with RELOAD, UPDATE, and REMOVE support.
 */
public class MagicCycleSyncHandler implements SyncHandler {

    // ========== SERVER-SIDE ==========

    @Override
    public NBTTagCompound serializeAll() {
        NBTTagList list = new NBTTagList();
        NBTTagCompound compound = new NBTTagCompound();
        for (MagicCycle cycle : MagicController.getInstance().cycles.values()) {
            NBTTagCompound cycleCompound = new NBTTagCompound();
            cycle.writeNBT(cycleCompound);
            list.appendTag(cycleCompound);
        }
        compound.setTag("Data", list);
        return compound;
    }

    // ========== CLIENT-SIDE ==========

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleReload(NBTTagCompound fullCompound) {
        NBTTagList list = fullCompound.getTagList("Data", 10);
        MagicController mc = MagicController.getInstance();
        mc.cyclesSync.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            MagicCycle cycle = new MagicCycle();
            cycle.readNBT(list.getCompoundTagAt(i));
            mc.cyclesSync.put(cycle.id, cycle);
        }

        mc.cycles.clear();
        mc.cycles.putAll(mc.cyclesSync);
        mc.cyclesSync.clear();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleUpdate(NBTTagCompound compound, int categoryId) {
        MagicCycle cycle = new MagicCycle();
        cycle.readNBT(compound);
        MagicController.getInstance().cycles.put(cycle.id, cycle);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleRemove(int id) {
        MagicController.getInstance().cycles.remove(id);
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

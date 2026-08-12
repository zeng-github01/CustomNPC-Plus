package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.MagicController;
import noppes.npcs.controllers.data.Magic;
import noppes.npcs.controllers.data.MagicCycle;

import java.util.HashSet;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#MAGIC}.
 * Simple int-keyed map family.
 * REMOVE dissociates cycle associations before removing the magic entry.
 *
 * <p>Note: The original code had a bug where MAGIC REMOVE incorrectly
 * removed from {@code cycles} instead of {@code magics}. This handler
 * fixes that by properly removing from {@code magics} after dissociating.</p>
 */
public class MagicSyncHandler implements SyncHandler {

    // ========== SERVER-SIDE ==========

    @Override
    public NBTTagCompound serializeAll() {
        NBTTagList list = new NBTTagList();
        NBTTagCompound compound = new NBTTagCompound();
        for (Magic magic : MagicController.getInstance().magics.values()) {
            NBTTagCompound magicCompound = new NBTTagCompound();
            magic.writeNBT(magicCompound);
            list.appendTag(magicCompound);
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
        mc.magicSync.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            Magic magic = new Magic();
            magic.readNBT(list.getCompoundTagAt(i));
            mc.magicSync.put(magic.id, magic);
        }

        mc.magics.clear();
        mc.magics.putAll(mc.magicSync);
        mc.magicSync.clear();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleUpdate(NBTTagCompound compound, int categoryId) {
        Magic magic = new Magic();
        magic.readNBT(compound);
        MagicController.getInstance().magics.put(magic.id, magic);
    }

    /**
     * Removes a magic entry from the client. First dissociates cycle
     * references, then removes from the magics map.
     * <p>Bug fix: The original code incorrectly removed from {@code cycles}
     * instead of {@code magics} after dissociating cycle associations.</p>
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleRemove(int id) {
        // First remove this magic's association from all cycles
        for (MagicCycle cycle : MagicController.getInstance().cycles.values()) {
            cycle.associations.remove(id);
        }
        // Then remove the magic itself (fixed: was cycles.remove(id))
        MagicController.getInstance().magics.remove(id);
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

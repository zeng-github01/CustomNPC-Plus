package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.QuestController;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.controllers.data.QuestCategory;

import java.util.HashSet;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#QUEST}.
 * Child family — does not have its own cache. UPDATE and REMOVE only.
 * Invalidation targets the parent {@link SyncType#QUEST_CATEGORY} cache.
 */
public class QuestSyncHandler implements SyncHandler {

    // ========== SERVER-SIDE ==========

    /**
     * QUEST does not have its own independent cache; it shares
     * the QUEST_CATEGORY parent cache.
     */
    @Override
    public NBTTagCompound serializeAll() {
        return null;
    }


    // ========== CLIENT-SIDE ==========

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleReload(NBTTagCompound fullCompound) {
        // QUEST does not handle standalone RELOAD; it's part of QUEST_CATEGORY
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleUpdate(NBTTagCompound compound, int categoryId) {
        QuestCategory category = QuestController.Instance.categories.get(categoryId);
        if (category == null) 
            return; // Defensive: category may have been removed
        
        Quest quest = new Quest();
        quest.category = category;
        quest.readNBT(compound);
        QuestController.Instance.quests.put(quest.id, quest);
        category.quests.put(quest.id, quest);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleRemove(int id) {
        Quest quest = QuestController.Instance.quests.remove(id);
        if (quest != null) 
            quest.category.quests.remove(id);
        
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
     * Not a cached type — QUEST shares the QUEST_CATEGORY cache.
     */
    @Override
    public boolean isCachedType() {
        return false;
    }

    @Override
    public Set<SyncType> getInvalidationTargets(SyncType self) {
        // Child invalidates the parent cache
        Set<SyncType> set = new HashSet<>();
        set.add(SyncType.QUEST_CATEGORY);
        return set;
    }
}

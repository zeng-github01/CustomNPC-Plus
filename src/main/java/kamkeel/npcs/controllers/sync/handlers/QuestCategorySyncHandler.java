package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.QuestController;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.controllers.data.QuestCategory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Sync handler for {@link SyncType#QUEST_CATEGORY}.
 * Parent-child category family. QUEST children share this cache.
 * Invalidation from QUEST or QUEST_CATEGORY both target this cache.
 */
public class QuestCategorySyncHandler implements SyncHandler {

    // ========== SERVER-SIDE ==========

    @Override
    public NBTTagCompound serializeAll() {
        NBTTagCompound compound = new NBTTagCompound();
        NBTTagList categoryList = new NBTTagList();
        for (QuestCategory category : QuestController.Instance.categories.values()) {
            NBTTagCompound questCompound = new NBTTagCompound();
            ;
            NBTTagList questList = new NBTTagList();
            for (int questID : category.quests.keySet()) {
                Quest quest = category.quests.get(questID);
                questList.appendTag(quest.writeToNBT(new NBTTagCompound()));
            }
            questCompound.setTag("Data", questList);
            questCompound.setTag("CatNBT", category.writeSmallNBT(new NBTTagCompound()));
            categoryList.appendTag(questCompound);
        }
        compound.setTag("QuestCategories", categoryList);
        return compound;
    }

    /**
     * Serialize a single quest category for UPDATE packets.
     */
    public static NBTTagCompound serializeCategory(QuestCategory questCategory) {
        NBTTagCompound questCompound = new NBTTagCompound();
        NBTTagList questList = new NBTTagList();
        for (int questID : questCategory.quests.keySet()) {
            Quest quest = questCategory.quests.get(questID);
            questList.appendTag(quest.writeToNBT(new NBTTagCompound()));
        }
        questCompound.setTag("Data", questList);
        questCompound.setTag("CatNBT", questCategory.writeSmallNBT(new NBTTagCompound()));
        return questCompound;
    }


    // ========== CLIENT-SIDE ==========

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleReload(NBTTagCompound fullCompound) {
        if (!fullCompound.hasNoTags()) {
            NBTTagList categories = fullCompound.getTagList("QuestCategories", 10);
            for (int j = 0; j < fullCompound.getTagList("QuestCategories", 10).tagCount(); j++) {
                NBTTagCompound categoryCompound = categories.getCompoundTagAt(j);
                if (categoryCompound.hasNoTags())
                    continue;

                QuestCategory category = new QuestCategory();
                category.readSmallNBT(categoryCompound.getCompoundTag("CatNBT"));
                NBTTagList questList = categoryCompound.getTagList("Data", 10);
                if (QuestController.Instance.categoriesSync.containsKey(category.id)) {
                    category = QuestController.Instance.categoriesSync.get(category.id);
                    category.readSmallNBT(categoryCompound.getCompoundTag("CatNBT"));
                }
                for (int i = 0; i < questList.tagCount(); i++) {
                    Quest quest = new Quest();
                    quest.readNBT(questList.getCompoundTagAt(i));
                    quest.category = category;
                    category.quests.put(quest.id, quest);
                }
                QuestController.Instance.categoriesSync.put(category.id, category);
            }
        }

        HashMap<Integer, Quest> quests = new HashMap<>();
        for (QuestCategory category : QuestController.Instance.categoriesSync.values()) {
            for (Quest quest : category.quests.values()) {
                quests.put(quest.id, quest);
            }
        }

        QuestController.Instance.categories = QuestController.Instance.categoriesSync;
        QuestController.Instance.quests = quests;
        QuestController.Instance.categoriesSync = new HashMap<>();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleUpdate(NBTTagCompound compound, int categoryId) {
        QuestCategory category = new QuestCategory();
        category.readSmallNBT(compound.getCompoundTag("CatNBT"));
        NBTTagList list = compound.getTagList("Data", 10);
        if (QuestController.Instance.categoriesSync.containsKey(category.id)) {
            category = QuestController.Instance.categoriesSync.get(category.id);
            category.readSmallNBT(compound.getCompoundTag("CatNBT"));
        }
        for (int i = 0; i < list.tagCount(); i++) {
            Quest quest = new Quest();
            quest.readNBT(list.getCompoundTagAt(i));
            quest.category = category;
            category.quests.put(quest.id, quest);
        }
        QuestController.Instance.categories.put(category.id, category);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleRemove(int id) {
        QuestCategory questCategory = QuestController.Instance.categories.remove(id);
        if (questCategory != null) {
            QuestController.Instance.quests.keySet().removeAll(questCategory.quests.keySet());
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

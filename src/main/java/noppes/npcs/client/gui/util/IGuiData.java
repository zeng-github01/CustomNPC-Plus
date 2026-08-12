package noppes.npcs.client.gui.util;

import kamkeel.npcs.controllers.SyncController;
import kamkeel.npcs.controllers.sync.SyncHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;

/**
 * OVERHAULED IGuiData FRAMEWORK!
 * Based on the new {@link SyncController} and {@link SyncHandler} infrastructure
 */
public interface IGuiData {

    /**
     * Legacy data setter
     * @Deprecated Use {@link #onDataUpdated(NBTTagCompound)} or {@link #onDataRemoved(String)} instead
     */
    default void setGuiData(NBTTagCompound compound) {

    }

    // ═══════════════════════════════════════════════════════════════════════
    // USE THESE !!!
    // ═══════════════════════════════════════════════════════════════════════

    /** NOTE:
     * No need for full object dataset onDataReload on GUI open, as these datasets are  
     * always freshly synced to their client's controllers thanks to {@link SyncController}
     */

    /**
     * When a controller's object gets saved on server side, the updated object is synced to the client
     * @param compound the compound of the updated object 
     */
    default void onDataUpdated(NBTTagCompound compound) {
    }

    /**
     * When a controller's object gets saved on server side, the updated object is synced to the client
     * @param key key of updated object
     * @param compound compound of updated object 
     */
    default void onDataUpdated(NBTTagCompound compound, String key) {
    }

    /**
     * When a controller's object gets saved on server side, the updated object is synced to the client
     * @param id id of updated object
     * @param compound compound of updated object 
     */
    default void onDataUpdated(NBTTagCompound compound, int id) {
    }

    /**
     * When a controller's object gets deleted on server side, the deleted object is removed from the client
     * @param key key of removed object
     */
    default void onDataRemoved(String key) {
    }

    /**
     * When a controller's object gets deleted on server side, the deleted object is removed from the client
     * @param id id of removed object
     */
    default void onDataRemoved(int id) {
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Static dispatchers
    // ═══════════════════════════════════════════════════════════════════════

    static void legacySetGuiData(NBTTagCompound nbt) {
        GuiScreen gui = activeGui();
        if (gui instanceof IGuiData)
            ((IGuiData) gui).setGuiData(nbt);
    }

    static void notifyUpdate(NBTTagCompound nbt, String key, int id) {
        GuiScreen gui = activeGui();
        if (gui instanceof IGuiData) {
            ((IGuiData) gui).onDataUpdated(nbt);
            ((IGuiData) gui).onDataUpdated(nbt, key);
            ((IGuiData) gui).onDataUpdated(nbt, id);
        }
    }

    static void notifyRemove(String key, int id) {
        GuiScreen gui = activeGui();
        if (gui instanceof IGuiData) {
            ((IGuiData) gui).onDataRemoved(key);
            ((IGuiData) gui).onDataRemoved(id);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Active Gui fetcher
    // ═══════════════════════════════════════════════════════════════════════

    static GuiScreen activeGui() {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        if (gui instanceof GuiNPCInterface && ((GuiNPCInterface) gui).hasSubGui())
            return ((GuiNPCInterface) gui).getSubGui();
        if (gui instanceof GuiContainerNPCInterface && ((GuiContainerNPCInterface) gui).hasSubGui())
            return ((GuiContainerNPCInterface) gui).getSubGui();
        return gui;
    }
}

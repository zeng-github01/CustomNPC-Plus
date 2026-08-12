package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.controllers.SyncController;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.ClientCacheHandler;
import noppes.npcs.controllers.data.PlayerData;

/**
 * Sync handler for {@link SyncType#PLAYERDATA}.
 * Special non-cached per-player path. Not included in login cache iteration.
 * RELOAD and UPDATE are sent as direct per-player packets.
 */
public class PlayerDataSyncHandler implements SyncHandler {

    public static void syncPlayerData(EntityPlayerMP player, boolean update) {
        PlayerData data = PlayerData.get(player);
        if (data != null)
            SyncController.syncPlayerData(SyncType.PLAYERDATA, update ? data.getSyncNBTFull() : data.getSyncNBT(), update, player);
    }

    // ========== SERVER-SIDE ==========

    /**
     * PLAYERDATA is not a global cached type — it is per-player.
     */
    @Override
    public NBTTagCompound serializeAll() {
        return null;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void clientHandleUpdate(NBTTagCompound compound, int categoryId) {
        if (categoryId == 1) ClientCacheHandler.playerData.setSyncNBTFull(compound);
        else ClientCacheHandler.playerData.setSyncNBT(compound);
    }

    @Override
    public boolean supportsUpdate() {
        return true;
    }

    /**
     * Not a cached type — PLAYERDATA uses direct per-player sync.
     */
    @Override
    public boolean isCachedType() {
        return false;
    }
}

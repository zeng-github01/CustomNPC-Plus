package kamkeel.npcs.controllers.sync.handlers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.network.PacketHandler;
import kamkeel.npcs.network.packets.data.large.SyncEffectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.CustomEffectController;
import noppes.npcs.controllers.data.EffectKey;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerEffect;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Direct per-player sync helper for player effects.
 * This is NOT a cached global sync family — it operates outside the
 * registry-driven reload/revision system.
 *
 * <p>Effects are synced per-player on demand (e.g. effect application,
 * periodic ticks). The data mutates {@code playerData.effectData}
 * before sending the packet.</p>
 */
public final class PlayerEffectSyncHelper {

    private PlayerEffectSyncHelper() {
    }

    /**
     * Server-side: sync the player's active effects to their client.
     * Mutates {@code playerData.effectData} before sending.
     */
    public static void syncEffects(EntityPlayerMP playerMP) {
        ConcurrentHashMap<EffectKey, PlayerEffect> playerEffects = CustomEffectController.getInstance().getPlayerEffects(playerMP);
        PlayerData playerData = PlayerData.get(playerMP);
        playerData.effectData.setEffects(playerEffects);

        NBTTagCompound compound = playerData.getPlayerEffects();
        PacketHandler.Instance.sendToPlayer(new SyncEffectPacket(compound), playerMP);
    }

    /**
     * Client-side: apply incoming effect data to the local player.
     */
    @SideOnly(Side.CLIENT)
    public static void clientSyncEffects(NBTTagCompound compound) {
        PlayerData playerData = PlayerData.get(Minecraft.getMinecraft().thePlayer);
        if (playerData != null) {
            playerData.setPlayerEffects(compound);
        }
    }
}

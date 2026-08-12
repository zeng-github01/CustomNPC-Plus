package kamkeel.npcs.controllers.sync.handlers;

import kamkeel.npcs.network.packets.data.ability.AbilityCooldownSyncPacket;
import kamkeel.npcs.network.packets.data.ability.AbilityHotbarSyncPacket;
import kamkeel.npcs.network.packets.data.ability.PlayerAbilitySyncPacket;
import net.minecraft.entity.player.EntityPlayerMP;
import noppes.npcs.controllers.data.PlayerData;

/**
 * Direct per-player sync helper for player ability state.
 * This is NOT a cached global sync family — it operates outside the
 * registry-driven reload/revision system.
 *
 * <p>Abilities are synced per-player on demand (e.g. ability unlock,
 * entity reconstruction, periodic cooldown ticks).</p>
 */
public final class PlayerAbilitySyncHelper {

    private PlayerAbilitySyncHelper() {
    }

    /**
     * Full sync of all player ability state to the client: unlocked abilities,
     * selection, toggles, hotbar layout, and cooldowns.
     * Used for entity reconstruction and other full-refresh scenarios.
     */
    public static void syncAbilities(EntityPlayerMP playerMP) {
        PlayerData playerData = PlayerData.get(playerMP);
        if (playerData == null) return;

        PlayerAbilitySyncPacket.sendToPlayer(playerMP);
        AbilityHotbarSyncPacket.sendToPlayer(playerMP);
        AbilityCooldownSyncPacket.sendToPlayer(playerMP);
    }

    /**
     * Lightweight sync of cooldown state only (global + per-ability).
     * Called periodically (every 10 ticks) to keep the client's cooldown
     * display accurate. Cooldowns are transient and not part of NBT sync.
     */
    public static void syncAbilityCooldowns(EntityPlayerMP playerMP) {
        PlayerData playerData = PlayerData.get(playerMP);
        if (playerData == null) return;

        AbilityCooldownSyncPacket.sendToPlayer(playerMP);
    }
}

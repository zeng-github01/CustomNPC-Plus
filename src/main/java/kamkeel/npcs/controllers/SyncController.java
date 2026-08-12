package kamkeel.npcs.controllers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import kamkeel.npcs.addon.DBCAddon;
import kamkeel.npcs.controllers.sync.SyncHandler;
import kamkeel.npcs.controllers.sync.SyncRegistry;
import kamkeel.npcs.controllers.sync.handlers.*;
import kamkeel.npcs.network.LargeAbstractPacket;
import kamkeel.npcs.network.PacketHandler;
import kamkeel.npcs.network.enums.EnumSyncAction;
import kamkeel.npcs.network.enums.SyncType;
import kamkeel.npcs.network.packets.data.LoginPacket;
import kamkeel.npcs.network.packets.data.ProfileSharedQuestPacket;
import kamkeel.npcs.network.packets.data.large.SyncPacket;
import kamkeel.npcs.network.packets.request.party.PartyInfoPacket;
import kamkeel.npcs.util.ByteBufUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import noppes.npcs.LogWriter;
import noppes.npcs.client.ClientCacheHandler;
import noppes.npcs.client.gui.util.IGuiData;
import noppes.npcs.controllers.data.PlayerData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Central sync backend for CNPC+.
 *
 * <p>This controller manages two distinct sync lanes:</p>
 * <ul>
 *   <li><b>Global cached registry families</b> — registered via {@link SyncRegistry}
 *       with concrete {@link SyncHandler} implementations. These families use
 *       cached serialized payloads, revision-based delta sync on reconnect,
 *       and chunked delivery. All family-specific RELOAD/UPDATE/REMOVE logic
 *       lives in the handler implementations, not here.</li>
 *   <li><b>Direct per-player sync helpers</b> — for transient per-player state
 *       like effects, ability hotbars, and cooldowns. These bypass the cached
 *       registry system and are sent as direct packets. Owned by
 *       {@link PlayerEffectSyncHelper} and {@link PlayerAbilitySyncHelper}.</li>
 * </ul>
 *
 * <p>This class retains ownership of:</p>
 * <ul>
 *   <li>Cache entry storage and lazy rebuild ({@link SyncCacheEntry})</li>
 *   <li>Cached payload byte[] + precomputed chunks ({@link CachedSyncPayload})</li>
 *   <li>Per-player revision tracking ({@link PlayerSyncState})</li>
 *   <li>Login handshake and post-login packet sequencing</li>
 *   <li>Generic sync dispatch helpers (syncAll, syncUpdate, syncRemove)</li>
 * </ul>
 */
public class SyncController {

    /** Cached serialized reload payloads and revision state for each global sync family. */
    private static final Map<SyncType, SyncCacheEntry> cacheEntries = new LinkedHashMap<>();

    /** Last known synced revision map for each connected player. */
    private static final ConcurrentHashMap<UUID, PlayerSyncState> playerSyncState = new ConcurrentHashMap<>();

    /** Stable identity token for the current dedicated-server process, used by the revision handshake. */
    private static final String SERVER_IDENTITY_KEY = UUID.randomUUID().toString();

    // ═══════════════════════════════════════════════════════════════════════
    // Lifecycle — handler registration and cache initialization
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Registers all core CNPC+ sync handlers in deterministic order.
     * Called on server start. The registration order determines login
     * sync ordering via {@link SyncRegistry#getLoginTypes()}.
     *
     * <p>Handler registration order matches the original hard-coded
     * LOGIN_SYNC_TYPES array for behavioral parity.</p>
     */
    public static void register() {
        SyncRegistry.register(SyncType.FACTION, new FactionSyncHandler());
        SyncRegistry.register(SyncType.DIALOG, new DialogSyncHandler());
        SyncRegistry.register(SyncType.DIALOG_CATEGORY, new DialogCategorySyncHandler());
        SyncRegistry.register(SyncType.QUEST, new QuestSyncHandler());
        SyncRegistry.register(SyncType.QUEST_CATEGORY, new QuestCategorySyncHandler());
        SyncRegistry.register(SyncType.PLAYERDATA, new PlayerDataSyncHandler());
        SyncRegistry.register(SyncType.WORKBENCH_RECIPES, new WorkbenchRecipeSyncHandler());
        SyncRegistry.register(SyncType.CARPENTRY_RECIPES, new CarpentryRecipeSyncHandler());
        SyncRegistry.register(SyncType.ANVIL_RECIPES, new AnvilRecipeSyncHandler());
        SyncRegistry.register(SyncType.CUSTOM_EFFECTS, new CustomEffectSyncHandler());
        SyncRegistry.register(SyncType.MAGIC, new MagicSyncHandler());
        SyncRegistry.register(SyncType.MAGIC_CYCLE, new MagicCycleSyncHandler());
        SyncRegistry.register(SyncType.CUSTOM_ABILITY, new CustomAbilitySyncHandler());
        SyncRegistry.register(SyncType.CHAINED_ABILITY, new ChainedAbilitySyncHandler());
    }
    
    public static void load() {
        cacheEntries.clear();
        playerSyncState.clear();

        // Register caches for all cached (login-sync) types
        // The cache supplier delegates to the handler's serializeAll()
        for (SyncType type : SyncRegistry.getLoginTypes()) {
            SyncHandler handler = SyncRegistry.getHandler(type);
            if (handler != null)
                registerCache(type, handler::serializeAll);
        }
    }


    // ═══════════════════════════════════════════════════════════════════════
    // Other Login syncers
    // ═══════════════════════════════════════════════════════════════════════

    public static void beginLogin(EntityPlayerMP player) {
        playerSyncState.computeIfAbsent(player.getUniqueID(), PlayerSyncState::new);
        PacketHandler.Instance.sendToPlayer(new LoginPacket(getServerCacheKey(), getServerRevisionSnapshot()), player);
    }

    private static void sendPostLoginPackets(EntityPlayerMP player) {
        DBCAddon.instance.syncPlayer(player);
        PlayerDataSyncHandler.syncPlayerData(player, false);
        PartyInfoPacket.sendPartyData(player);
        ProfileSharedQuestPacket.sendToPlayer(player);

        // Sync skin overlays after full handshake to ensure the client is ready.
        // Overlays are not part of getSyncNBTFull(), and packets sent during
        // PlayerLoggedInEvent can arrive before the client entity exists.
        PlayerData data = PlayerData.get(player);
        if (data != null) data.skinOverlays.updateClient();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAIN PIPELINE — completion of login handshake and full catalog sync
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Runs the cached-family sync pass for one player using the currently tracked
     * revision state. Used after the login handshake and by server-side callers
     * that need to refresh a player's global cached sync families.
     */
    public static void dispatchSyncForAllTypes(EntityPlayerMP player) {
        PlayerSyncState state = playerSyncState.computeIfAbsent(player.getUniqueID(), PlayerSyncState::new);

        // Registry-driven login iteration: iterate only cached types
        for (SyncType type : SyncRegistry.getLoginTypes()) {
            SyncCacheEntry entry = cacheEntries.get(type);
            if (entry == null) continue;

            int currentRevision = entry.getRevisionValue();
            int lastRevision = state.getRevision(type);
            if (lastRevision == currentRevision) continue;
            
            CachedSyncPayload payload = entry.getPayload(type);
            if (payload == null) continue;

            if (lastRevision != payload.getRevision()) {
                PacketHandler.Instance.sendToPlayer(new SyncPacket(type, payload), player);
                state.updateRevision(type, payload.getRevision());
            }
        }

        sendPostLoginPackets(player);
    }

    /**
     * Completes the server-side revision handshake that follows {@link LoginPacket}.
     *
     * The client reports which cached revisions it believes are still valid for the
     * current server identity. If the reported server identity is stale or missing,
     * we reset the stored state and force a full cached resync. Otherwise we seed the
     * player's tracked revisions from the client report and only send outdated families.
     */
    public static void completeLoginRevisionHandshake(EntityPlayerMP player, String serverKey, String previousServerKey,
                                                      Map<SyncType, Integer> clientRevisions) {
        String currentServerKey = getServerCacheKey();
        PlayerSyncState state = playerSyncState.computeIfAbsent(player.getUniqueID(), PlayerSyncState::new);

        boolean serverIdentityMismatch = !currentServerKey.equals(serverKey);
        boolean previousServerMismatch = !currentServerKey.isEmpty() && (previousServerKey == null || !currentServerKey.equals(
                previousServerKey));
        boolean canReuseClientRevisions = clientRevisions != null && !clientRevisions.isEmpty();

        // Any server identity mismatch means the client's cached revision map is unsafe to reuse.
        if (serverIdentityMismatch || previousServerMismatch || !canReuseClientRevisions)
            state.reset();
        else // The client and server agree on identity, so seed the stored state from the report.
            state.applyHandshake(clientRevisions);

        dispatchSyncForAllTypes(player);
    }


    // ═══════════════════════════════════════════════════════════════════════
    // Generic dispatcher — called from outside SyncController
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Generic full-broadcast helper. Rebuilds the cache for the given type
     * and sends to all connected players.
     */
    public static void syncAll(SyncType type) {
        CachedSyncPayload payload = rebuildCache(type);
        if (payload == null) return;

        PacketHandler.Instance.sendToAll(new SyncPacket(type, payload));
        updateAllPlayerRevisions(type, payload.getRevision());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Player data sync
    // ═══════════════════════════════════════════════════════════════════════

    public static void syncPlayerData(SyncType type, NBTTagCompound compound, boolean fullData, EntityPlayerMP player) {
        Map<SyncType, Integer> revisions = invalidateCaches(type);
        int revision = getRequestedInvalidationRevision(type, revisions);
        PacketHandler.Instance.sendToPlayer(
                new SyncPacket(type, EnumSyncAction.UPDATE, fullData ? 1 : 0, null, revision, compound), player);
        updateAllPlayerRevisions(revisions);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Server-side dispatchers — registry-driven
    // ═══════════════════════════════════════════════════════════════════════
    public static void syncUpdate(SyncType type, NBTTagCompound compound) {
        Map<SyncType, Integer> revisions = invalidateCaches(type);
        int revision = getRequestedInvalidationRevision(type, revisions);
        PacketHandler.Instance.sendToAll(new SyncPacket(type, EnumSyncAction.UPDATE, -1, null, revision, compound));
        updateAllPlayerRevisions(revisions);
    }

    public static void syncUpdate(SyncType type, String key, NBTTagCompound compound) {
        Map<SyncType, Integer> revisions = invalidateCaches(type);
        int revision = getRequestedInvalidationRevision(type, revisions);
        PacketHandler.Instance.sendToAll(new SyncPacket(type, EnumSyncAction.UPDATE, -1, key, revision, compound));
        updateAllPlayerRevisions(revisions);
    }

    public static void syncUpdate(SyncType type, int cat, NBTTagCompound compound) {
        Map<SyncType, Integer> revisions = invalidateCaches(type);
        int revision = getRequestedInvalidationRevision(type, revisions);
        PacketHandler.Instance.sendToAll(new SyncPacket(type, EnumSyncAction.UPDATE, cat, null, revision, compound));
        updateAllPlayerRevisions(revisions);
    }

    public static void syncRemove(SyncType syncType, String key) {
        Map<SyncType, Integer> revisions = invalidateCaches(syncType);
        int revision = getRequestedInvalidationRevision(syncType, revisions);
        PacketHandler.Instance.sendToAll(new SyncPacket(syncType, EnumSyncAction.REMOVE, -1, key, revision, null));
        updateAllPlayerRevisions(revisions);
    }

    public static void syncRemove(SyncType syncType, int id) {
        Map<SyncType, Integer> revisions = invalidateCaches(syncType);
        int revision = getRequestedInvalidationRevision(syncType, revisions);
        PacketHandler.Instance.sendToAll(new SyncPacket(syncType, EnumSyncAction.REMOVE, id, null, revision, null));
        updateAllPlayerRevisions(revisions);
    }


    // ═══════════════════════════════════════════════════════════════════════
    // Client-side handlers — registry-driven handler delegation
    // ═══════════════════════════════════════════════════════════════════════

    @SideOnly(Side.CLIENT)
    public static void clientHandleAll(SyncType syncType, int revision, NBTTagCompound fullCompound) {
        SyncHandler handler = SyncRegistry.getHandler(syncType);
        if (handler == null) return;
        try {
            handler.clientHandleReload(fullCompound);
        } catch (Exception e) {
            LogWriter.error("[SyncController] Failed to handle RELOAD for sync type " + syncType, e);
        }
        ClientCacheHandler.updateClientRevision(syncType, revision);

    }

    @SideOnly(Side.CLIENT)
    public static void clientHandleUpdate(SyncType syncType, int id, String key, int revision, NBTTagCompound compound) {
        SyncHandler handler = SyncRegistry.getHandler(syncType);
        if (handler == null) return;
        try {
            handler.clientHandleUpdate(compound);
            handler.clientHandleUpdate(compound, id);
            handler.clientHandleUpdate(compound, key);
        } catch (Exception e) {
            LogWriter.error("[SyncController] Failed to handle UPDATE for sync type " + syncType, e);
        }
        
        ClientCacheHandler.updateClientRevision(syncType, revision);
        
        // New GUI data handling approach
        IGuiData.notifyUpdate(compound, key, id);
    }

    @SideOnly(Side.CLIENT)
    public static void clientHandleRemove(SyncType syncType, int id, String key, int revision) {
        SyncHandler handler = SyncRegistry.getHandler(syncType);
        if (handler == null) return;
        try {
            handler.clientHandleRemove(id);
            handler.clientHandleRemove(key);
        } catch (Exception e) {
            LogWriter.error("[SyncController] Failed to handle REMOVE for sync type " + syncType, e);
        }
        
        ClientCacheHandler.updateClientRevision(syncType, revision);

        // New GUI data handling approach
        IGuiData.notifyRemove(key, id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cache and revision internals
    // ═══════════════════════════════════════════════════════════════════════

    private static void registerCache(SyncType type, Supplier<NBTTagCompound> supplier) {
        cacheEntries.put(type, new SyncCacheEntry(supplier));
    }

    private static CachedSyncPayload rebuildCache(SyncType type) {
        SyncCacheEntry entry = cacheEntries.get(type);
        if (entry == null) return null;
        
        entry.invalidate();
        return entry.getPayload(type);
    }

    /**
     * Registry-driven invalidation: delegates to handler-owned
     * invalidation targets via {@link SyncRegistry#getInvalidationTargets}.
     */
    private static Map<SyncType, Integer> invalidateCaches(SyncType type) {
        Set<SyncType> targets = SyncRegistry.getInvalidationTargets(type);
        Map<SyncType, Integer> revisions = new LinkedHashMap<>();
        for (SyncType target : targets) {
            SyncCacheEntry entry = cacheEntries.get(target);
            if (entry != null) {
                int newRevision = entry.invalidate();
                revisions.put(target, newRevision);
            }
        }
        return revisions;
    }

    private static int getRequestedInvalidationRevision(SyncType type, Map<SyncType, Integer> revisions) {
        Integer revision = revisions.get(type);
        return revision != null ? revision : getCurrentServerRevision(type);
    }

    public static int getCurrentServerRevision(SyncType type) {
        SyncCacheEntry entry = cacheEntries.get(type);
        return entry == null ? -1 : entry.getRevisionValue();
    }

    private static Map<SyncType, Integer> getServerRevisionSnapshot() {
        // Registry-driven: iterate login types from registry
        Map<SyncType, Integer> snapshot = new LinkedHashMap<>();
        for (SyncType type : SyncRegistry.getLoginTypes()) {
            SyncCacheEntry entry = cacheEntries.get(type);
            if (entry != null)
                snapshot.put(type, entry.getRevisionValue());
        }
        return snapshot;
    }

    private static void updateAllPlayerRevisions(SyncType type, int revision) {
        if (revision < 0) return;

        for (PlayerSyncState state : playerSyncState.values()) 
            state.updateRevision(type, revision);
    }

    private static void updateAllPlayerRevisions(Map<SyncType, Integer> revisions) {
        if (revisions.isEmpty()) return;

        for (PlayerSyncState state : playerSyncState.values()) {
            for (Map.Entry<SyncType, Integer> entry : revisions.entrySet()) 
                state.updateRevision(entry.getKey(), entry.getValue());
        }
    }

    private static String getServerCacheKey() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || !server.isDedicatedServer()) return "";

        return SERVER_IDENTITY_KEY;
    }


    // ═══════════════════════════════════════════════════════════════════════
    // Inner classes — cache payload and player revision state
    // ═══════════════════════════════════════════════════════════════════════

    public static final class CachedSyncPayload {
        private final int revision;
        private final byte[] payload;
        private final byte[][] chunks;

        private CachedSyncPayload(int revision, byte[] payload, byte[][] chunks) {
            this.revision = revision;
            this.payload = payload;
            this.chunks = chunks;
        }

        public int getRevision() {
            return revision;
        }

        public byte[] getPayload() {
            return payload;
        }

        public byte[][] getChunks() {
            return chunks;
        }
    }

    private static final class SyncCacheEntry {
        private final Supplier<NBTTagCompound> supplier;
        private volatile CachedSyncPayload payload;
        private int revision = 0;
        private boolean dirty = true;

        private SyncCacheEntry(Supplier<NBTTagCompound> supplier) {
            this.supplier = supplier;
        }

        private synchronized CachedSyncPayload getPayload(SyncType requestedType) {
            if (!dirty && payload != null) return payload;

            int payloadRevision = revision;
            ByteBuf buffer = Unpooled.buffer();
            byte[] bytes;
            try {
                NBTTagCompound data = supplier.get();
                
                buffer.writeInt(requestedType.ordinal());
                buffer.writeInt(EnumSyncAction.RELOAD.ordinal());
                buffer.writeInt(-1);
                buffer.writeInt(payloadRevision);
                ByteBufUtils.writeBigNBT(buffer, data);

                bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
            } catch (Exception e) {
                LogWriter.error("Failed to serialize sync payload for " + requestedType, e);
                return null;
            } finally {
                buffer.release();
            }

            byte[][] chunks = splitIntoChunks(bytes);
            payload = new CachedSyncPayload(payloadRevision, bytes, chunks);
            dirty = false;
            return payload;
        }

        private static byte[][] splitIntoChunks(byte[] payload) {
            int totalSize = payload.length;
            int chunkCount = (totalSize + LargeAbstractPacket.CHUNK_SIZE - 1) / LargeAbstractPacket.CHUNK_SIZE;
            if (chunkCount <= 0)
                chunkCount = 1;

            byte[][] chunks = new byte[chunkCount][];
            for (int i = 0; i < chunkCount; i++) {
                int offset = i * LargeAbstractPacket.CHUNK_SIZE;
                int length = Math.min(LargeAbstractPacket.CHUNK_SIZE, totalSize - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(payload, offset, chunk, 0, length);
                chunks[i] = chunk;
            }
            return chunks;
        }

        private synchronized int invalidate() {
            dirty = true;
            payload = null;
            revision++;
            return revision;
        }

        private synchronized int getRevisionValue() {
            return revision;
        }

        private synchronized void reset() {
            dirty = true;
            payload = null;
            revision = 0;
        }
    }

    private static final class PlayerSyncState {
        private final Map<SyncType, Integer> revisions = new HashMap<>();

        private PlayerSyncState(UUID ignored) {
        }

        private synchronized int getRevision(SyncType type) {
            Integer value = revisions.get(type);
            return value == null ? -1 : value;
        }

        private synchronized void updateRevision(SyncType type, int revision) {
            revisions.put(type, revision);
        }

        private synchronized void applyHandshake(Map<SyncType, Integer> incoming) {
            if (incoming == null || incoming.isEmpty()) return;
            revisions.putAll(incoming);
        }

        private synchronized void reset() {
            revisions.clear();
        }
    }
}

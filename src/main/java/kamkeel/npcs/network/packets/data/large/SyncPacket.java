package kamkeel.npcs.network.packets.data.large;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import kamkeel.npcs.controllers.SyncController;
import kamkeel.npcs.network.LargeAbstractPacket;
import kamkeel.npcs.network.PacketChannel;
import kamkeel.npcs.network.PacketHandler;
import kamkeel.npcs.network.enums.EnumDataPacket;
import kamkeel.npcs.network.enums.EnumSyncAction;
import kamkeel.npcs.network.enums.SyncType;
import kamkeel.npcs.util.ByteBufUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.CustomNpcs;
import noppes.npcs.LogWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SyncPacket extends LargeAbstractPacket {

    private SyncType syncType;
    private EnumSyncAction enumSyncAction;
    private NBTTagCompound syncData;
    private int operationID;
    private String operationKey;
    private int revision = -1;
    private byte[] cachedPayload;
    private byte[][] cachedChunks;

    public SyncPacket() {
    }

    public SyncPacket(SyncType syncType, EnumSyncAction enumSyncAction, int catId, NBTTagCompound syncData) {
        this(syncType, enumSyncAction, catId, null, -1, syncData);
    }

    public SyncPacket(SyncType syncType, EnumSyncAction enumSyncAction, int operationId, String operationKey, int revision, NBTTagCompound syncData) {
        this.syncType = syncType;
        this.enumSyncAction = enumSyncAction;
        this.syncData = syncData;
        this.operationID = operationId;
        this.operationKey = operationKey;
        this.revision = revision;
    }

    public SyncPacket(SyncType syncType, SyncController.CachedSyncPayload payload) {
        this.syncType = syncType;
        this.enumSyncAction = EnumSyncAction.RELOAD;
        this.operationID = -1;
        this.revision = payload.getRevision();
        this.cachedPayload = payload.getPayload();
        this.cachedChunks = payload.getChunks();
    }

    @Override
    public Enum getType() {
        return EnumDataPacket.SYNC;
    }

    @Override
    public PacketChannel getChannel() {
        return PacketHandler.DATA_PACKET;
    }

    @Override
    protected byte[] getData() throws IOException {
        if (cachedPayload != null) {
            return cachedPayload;
        }

        boolean hasKey = operationKey != null;
        boolean hasNbt = syncData != null;
        
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(syncType.ordinal());
            buffer.writeInt(enumSyncAction.ordinal());
            buffer.writeInt(operationID);
            buffer.writeInt(revision);

            // UPDATE/REMOVE UNIQUE FIELDS
            buffer.writeBoolean(hasKey);
            if (hasKey) ByteBufUtils.writeString(buffer, operationKey);
            buffer.writeBoolean(hasNbt);
            if (hasNbt) ByteBufUtils.writeBigNBT(buffer, syncData);

            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    @Override
    public List<FMLProxyPacket> generatePackets() {
        if (cachedChunks == null) {
            return super.generatePackets();
        }

        List<FMLProxyPacket> packets = new ArrayList<>(cachedChunks.length);
        PacketChannel packetChannel = getChannel();
        EnumDataPacket dataPacket = (EnumDataPacket) getType();
        UUID packetId = UUID.randomUUID();

        int totalSize = cachedPayload.length;
        int offset = 0;

        for (byte[] chunk : cachedChunks) {
            ByteBuf chunkBuf = Unpooled.buffer();
            chunkBuf.writeInt(packetChannel.getChannelType().ordinal());
            chunkBuf.writeInt(dataPacket.ordinal());
            chunkBuf.writeLong(packetId.getMostSignificantBits());
            chunkBuf.writeLong(packetId.getLeastSignificantBits());
            chunkBuf.writeInt(totalSize);
            chunkBuf.writeInt(offset);
            chunkBuf.writeInt(cachedChunks.length);
            chunkBuf.writeBytes(chunk);
            packets.add(new FMLProxyPacket(chunkBuf, packetChannel.getChannelName()));
            offset += chunk.length;
        }

        return packets;
    }

    @Override
    protected void handleCompleteData(ByteBuf data, EntityPlayer player) throws IOException {
        if (CustomNpcs.side() != Side.CLIENT)
            return;

        int syncTypeOrdinal = data.readInt();
        int syncActionOrdinal = data.readInt();
        operationID = data.readInt();
        revision = data.readInt();

        EnumSyncAction action = EnumSyncAction.values()[syncActionOrdinal];

        // These fields only exist for UPDATE/REMOVE
        if (action != EnumSyncAction.RELOAD) {
            if (data.readBoolean()) operationKey = ByteBufUtils.readString(data);
            if (data.readBoolean()) syncData = ByteBufUtils.readBigNBT(data);
        } else
            syncData = ByteBufUtils.readBigNBT(data);


        syncType = SyncType.byOrdinal(syncTypeOrdinal);
        if (syncType == null) {
            LogWriter.error("[SyncPacket] Unknown sync type ordinal: " + syncTypeOrdinal + "; skipping");
            return;
        }

        handleSync(syncType, action, operationID, operationKey, revision, syncData);
    }

    private void handleSync(SyncType type, EnumSyncAction action, int id, String key, int revision, NBTTagCompound data) {
        switch (action) {
            case RELOAD:
                SyncController.clientHandleAll(type, revision, data);
                break;
            case UPDATE:
                SyncController.clientHandleUpdate(type, id, key, revision, data);
                break;
            case REMOVE:
                SyncController.clientHandleRemove(type, id, key, revision);
                break;
        }
    }
}

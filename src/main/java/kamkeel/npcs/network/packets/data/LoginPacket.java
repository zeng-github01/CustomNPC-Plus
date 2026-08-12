package kamkeel.npcs.network.packets.data;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import kamkeel.npcs.network.AbstractPacket;
import kamkeel.npcs.network.PacketChannel;
import kamkeel.npcs.network.PacketClient;
import kamkeel.npcs.network.PacketHandler;
import kamkeel.npcs.network.enums.EnumDataPacket;
import kamkeel.npcs.network.enums.SyncType;
import kamkeel.npcs.network.packets.player.SyncRevisionInfoPacket;
import kamkeel.npcs.util.ByteBufUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.AuctionClientConfig;
import noppes.npcs.client.ClientCacheHandler;
import noppes.npcs.client.ScriptClientConfig;
import noppes.npcs.config.ConfigMain;
import noppes.npcs.controllers.AuctionConfigSync;
import noppes.npcs.controllers.ScriptConfigSync;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LoginPacket extends AbstractPacket {
    public static final String packetName = "CNPC+|Login";

    private String serverCacheKey = "";
    private final Map<SyncType, Integer> revisionSnapshot = new LinkedHashMap<>();

    public LoginPacket() {
    }

    public LoginPacket(String serverCacheKey, Map<SyncType, Integer> revisionSnapshot) {
        if (serverCacheKey != null) {
            this.serverCacheKey = serverCacheKey;
        }
        if (revisionSnapshot != null) {
            this.revisionSnapshot.putAll(revisionSnapshot);
        }
    }

    @Override
    public Enum getType() {
        return EnumDataPacket.LOGIN;
    }

    @Override
    public PacketChannel getChannel() {
        return PacketHandler.DATA_PACKET;
    }

    @Override
    public void sendData(ByteBuf out) throws IOException {
        out.writeBoolean(ConfigMain.PartiesEnabled);
        out.writeBoolean(ConfigMain.ProfilesEnabled);
        ByteBufUtils.writeString(out, serverCacheKey);
        out.writeShort(revisionSnapshot.size());
        for (Map.Entry<SyncType, Integer> entry : revisionSnapshot.entrySet()) {
            out.writeInt(entry.getKey().ordinal());
            out.writeInt(entry.getValue());
        }

        NBTTagCompound auctionConfig = new NBTTagCompound();
        AuctionConfigSync.writeToNBT(auctionConfig);
        ByteBufUtils.writeNBT(out, auctionConfig);

        NBTTagCompound scriptConfig = new NBTTagCompound();
        ScriptConfigSync.writeToNBT(scriptConfig);
        ByteBufUtils.writeNBT(out, scriptConfig);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void receiveData(ByteBuf in, EntityPlayer player) throws IOException {
        ClientCacheHandler.allowParties = in.readBoolean();
        ClientCacheHandler.allowProfiles = in.readBoolean();

        String serverKey = ByteBufUtils.readString(in);
        int revisionCount = in.readUnsignedShort();
        Map<SyncType, Integer> serverRevisions = new LinkedHashMap<>();
        for (int i = 0; i < revisionCount; i++) {
            SyncType type = SyncType.byOrdinal(in.readInt());
            int revision = in.readInt();
            if (type != null) {
                serverRevisions.put(type, revision);
            }
        }

        ClientCacheHandler.setActiveServer(serverKey, serverRevisions);

        NBTTagCompound auctionConfig = ByteBufUtils.readNBT(in);
        AuctionClientConfig.readFromNBT(auctionConfig);

        NBTTagCompound scriptConfig = ByteBufUtils.readNBT(in);
        ScriptClientConfig.readFromNBT(scriptConfig);

        PacketClient.sendClient(new SyncRevisionInfoPacket(
            serverKey,
            ClientCacheHandler.getLastServerKey(),
            ClientCacheHandler.getCachedRevisionsForServer(serverKey)
        ));
    }
}

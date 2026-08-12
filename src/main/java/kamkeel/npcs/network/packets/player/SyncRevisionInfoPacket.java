package kamkeel.npcs.network.packets.player;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import kamkeel.npcs.controllers.SyncController;
import kamkeel.npcs.network.AbstractPacket;
import kamkeel.npcs.network.PacketChannel;
import kamkeel.npcs.network.PacketHandler;
import kamkeel.npcs.network.enums.EnumPlayerPacket;
import kamkeel.npcs.network.enums.SyncType;
import kamkeel.npcs.util.ByteBufUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SyncRevisionInfoPacket extends AbstractPacket {

    public static final String packetName = "Player|SyncRevisionInfo";

    private String serverKey = "";
    private String previousServerKey = "";
    private final Map<SyncType, Integer> revisions = new LinkedHashMap<>();

    public SyncRevisionInfoPacket() {
    }

    public SyncRevisionInfoPacket(String serverKey, String previousServerKey, Map<SyncType, Integer> revisions) {
        if (serverKey != null) {
            this.serverKey = serverKey;
        }
        if (previousServerKey != null) {
            this.previousServerKey = previousServerKey;
        }
        if (revisions != null) {
            this.revisions.putAll(revisions);
        }
    }

    @Override
    public Enum getType() {
        return EnumPlayerPacket.SyncRevisionInfo;
    }

    @Override
    public PacketChannel getChannel() {
        return PacketHandler.PLAYER_PACKET;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void sendData(ByteBuf out) throws IOException {
        ByteBufUtils.writeString(out, serverKey);
        ByteBufUtils.writeString(out, previousServerKey);
        out.writeShort(revisions.size());
        for (Map.Entry<SyncType, Integer> entry : revisions.entrySet()) {
            out.writeInt(entry.getKey().ordinal());
            out.writeInt(entry.getValue());
        }
    }

    @Override
    public void receiveData(ByteBuf in, EntityPlayer player) throws IOException {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }

        String incomingServerKey = ByteBufUtils.readString(in);
        String incomingPreviousKey = ByteBufUtils.readString(in);
        int revisionCount = in.readUnsignedShort();
        Map<SyncType, Integer> incomingRevisions = new LinkedHashMap<>();
        for (int i = 0; i < revisionCount; i++) {
            SyncType type = SyncType.byOrdinal(in.readInt());
            int revision = in.readInt();
            if (type != null) {
                incomingRevisions.put(type, revision);
            }
        }

        SyncController.completeLoginRevisionHandshake((EntityPlayerMP) player, incomingServerKey, incomingPreviousKey, incomingRevisions);
    }
}

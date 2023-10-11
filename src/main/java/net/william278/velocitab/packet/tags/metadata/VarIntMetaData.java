package net.william278.velocitab.packet.tags.metadata;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

public class VarIntMetaData extends EntityMetaData {

    private final int value;

    public VarIntMetaData(int index, int value) {
        super(index);
        this.value = value;
    }


    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        byteBuf.writeByte(index);
        ProtocolUtils.writeVarInt(byteBuf, getType(protocolVersion));
        ProtocolUtils.writeVarInt(byteBuf, value);
    }

    @Override
    public int getType(ProtocolVersion version) {
        if (version.compareTo(ProtocolVersion.MINECRAFT_1_19_4) >= 0) {
            return 1;
        }
        throw new IllegalArgumentException("This shouldn't happen");
    }
}

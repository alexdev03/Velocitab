package net.william278.velocitab.packet.tags.metadata;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

public class ByteMetaData extends EntityMetaData{

    private final byte value;

    public ByteMetaData(int index, byte value) {
        super(index);
        this.value = value;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        byteBuf.writeByte(index);
        ProtocolUtils.writeVarInt(byteBuf, getType(protocolVersion));
        byteBuf.writeByte(value);
    }

    @Override
    public int getType(ProtocolVersion version) {
        if (version.compareTo(ProtocolVersion.MINECRAFT_1_19_4) >= 0) {
            return 0;
        }
        throw new IllegalArgumentException("This shouldn't happen");
    }
}

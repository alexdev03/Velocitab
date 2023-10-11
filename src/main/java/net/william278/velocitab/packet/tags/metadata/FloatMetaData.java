package net.william278.velocitab.packet.tags.metadata;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

public class FloatMetaData extends EntityMetaData {

    private final float value;

    public FloatMetaData(int index, float value) {
        super(index);
        this.value = value;
    }


    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        byteBuf.writeByte(index);
        ProtocolUtils.writeVarInt(byteBuf, getType(protocolVersion));
        byteBuf.writeFloat(value);
    }

    @Override
    public int getType(ProtocolVersion version) {
        if (version.compareTo(ProtocolVersion.MINECRAFT_1_19_4) >= 0) {
            return 3;
        }
        throw new IllegalArgumentException("This shouldn't happen");
    }
}

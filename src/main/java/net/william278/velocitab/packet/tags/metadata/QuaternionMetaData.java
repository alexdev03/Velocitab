package net.william278.velocitab.packet.tags.metadata;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

public class QuaternionMetaData extends EntityMetaData {

    private final float x, y, z, w;

    public QuaternionMetaData(int index, float x, float y, float z, float w) {
        super(index);
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }


    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        byteBuf.writeByte(index);
        ProtocolUtils.writeVarInt(byteBuf, getType(protocolVersion));
        byteBuf.writeFloat(x);
        byteBuf.writeFloat(y);
        byteBuf.writeFloat(z);
        byteBuf.writeFloat(w);
    }

    @Override
    public int getType(ProtocolVersion version) {
        if (version.compareTo(ProtocolVersion.MINECRAFT_1_19_4) >= 0) {
            return 27;
        }
        throw new IllegalArgumentException("This shouldn't happen");
    }
}

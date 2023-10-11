package net.william278.velocitab.packet.tags.metadata;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

public class Vector3MetaData extends EntityMetaData {

    private final float x, y, z;

    public Vector3MetaData(int index, float x, float y, float z) {
        super(index);
        this.x = x;
        this.y = y;
        this.z = z;
    }


    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        byteBuf.writeByte(index);
        ProtocolUtils.writeVarInt(byteBuf, getType(protocolVersion));
        byteBuf.writeFloat(x);
        byteBuf.writeFloat(y);
        byteBuf.writeFloat(z);
    }

    @Override
    public int getType(ProtocolVersion version) {
        if (version.compareTo(ProtocolVersion.MINECRAFT_1_19_4) >= 0) {
            return 26;
        }
        throw new IllegalArgumentException("This shouldn't happen");
    }
}

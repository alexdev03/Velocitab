package net.william278.velocitab.packet.tags.metadata;

import com.velocitypowered.api.network.ProtocolVersion;
import io.netty.buffer.ByteBuf;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class EntityMetaData {

    protected final int index;

    public abstract void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion);
    public abstract int getType(ProtocolVersion version);

}

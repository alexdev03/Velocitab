package net.william278.velocitab.packet.tags.metadata;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChatMetaData extends EntityMetaData {

    private final Component value;

    public ChatMetaData(int index, @NotNull Component value) {
        super(index);
        this.value = value;
    }


    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        byteBuf.writeByte(index);
        ProtocolUtils.writeVarInt(byteBuf, getType(protocolVersion));
        ProtocolUtils.writeString(byteBuf, ProtocolUtils.getJsonChatSerializer(protocolVersion).serialize(this.value));
    }

    @Override
    public int getType(ProtocolVersion version) {
        if (version.compareTo(ProtocolVersion.MINECRAFT_1_19_4) >= 0) {
            return 5;
        }
        throw new IllegalArgumentException("This shouldn't happen");
    }
}

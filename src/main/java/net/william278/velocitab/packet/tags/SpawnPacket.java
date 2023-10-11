package net.william278.velocitab.packet.tags;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.william278.velocitab.Velocitab;

import java.util.UUID;

@RequiredArgsConstructor
@AllArgsConstructor
public class SpawnPacket implements MinecraftPacket {

    private final Velocitab plugin;
    private int id;

    /*
    Entity ID	VarInt	A unique integer ID mostly used in the protocol to identify the entity.
Entity UUID	UUID	A unique identifier that is mostly used in persistence and places where the uniqueness matters more.
Type	VarInt	The type of the entity (see "type" field of the list of Mob types).
X	Double
Y	Double
Z	Double
Pitch	Angle	To get the real pitch, you must divide this by (256.0F / 360.0F)
Yaw	Angle	To get the real yaw, you must divide this by (256.0F / 360.0F)
Head Yaw	Angle	Only used by living entities, where the head of the entity may differ from the general body rotation.
Data	VarInt	Meaning dependent on the value of the Type field, see Object Data for details.
Velocity X	Short	Same units as Set Entity Velocity.
Velocity Y	Short
Velocity Z	Short
     */

    public static void send(Velocitab plugin, Player player, int id) {
        ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
        connectedPlayer.getConnection().write(new SpawnPacket(plugin, id));
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
        //byteBuf.writeInt(id); // Entity ID
        ProtocolUtils.writeVarInt(byteBuf, id); // Entity ID
        UUID uuid = UUID.randomUUID();
        ProtocolUtils.writeUuid(byteBuf, uuid); // UUID
        ProtocolUtils.writeVarInt(byteBuf, 100); // Type (100 = Text Display)
//        byteBuf.writeInt(100); // Type (100 = Text Display)
        byteBuf.writeDouble(0D); // X
        byteBuf.writeDouble(100D); // Y
        byteBuf.writeDouble(0D); // Z

        byteBuf.writeByte((byte) floor(0 * 256.0F / 360.0F)); // xRot
        byteBuf.writeByte((byte) floor(0 * 256.0F / 360.0F)); // yRot
        byteBuf.writeByte((byte) floor(0 * 256.0D / 360.0D)); // Head Yaw

        ProtocolUtils.writeVarInt(byteBuf, 0); // Data

        byteBuf.writeShort(0); // Velocity X
        byteBuf.writeShort(0); // Velocity Y
        byteBuf.writeShort(0); // Velocity Z

    }

    public int floor(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }

    @Override
    public boolean handle(MinecraftSessionHandler minecraftSessionHandler) {
        return true;
    }
}

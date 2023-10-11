package net.william278.velocitab.packet.tags;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.EncoderException;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.Adventure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.william278.velocitab.Velocitab;
import net.william278.velocitab.packet.tags.metadata.*;

@RequiredArgsConstructor
@AllArgsConstructor
public class UnlimitedTagPacket implements MinecraftPacket {

    private final Velocitab plugin;
    private Player player;
    private Component text;
    private int id;

    public static void send(Velocitab plugin, Player player, Component text, int id) {
        ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
        connectedPlayer.getConnection().write(new UnlimitedTagPacket(plugin, player, text, id));
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {

    }

    /*
    0	Byte	Bit mask	Meaning	0
0x01	Is on fire
0x02	Is crouching
0x04	Unused (previously riding)
0x08	Is sprinting
0x10	Is swimming
0x20	Is invisible
0x40	has glowing effect
0x80	Is flying with an elytra
1	VarInt	Air ticks	300
2	OptChat	Custom name	empty
3	Boolean	Is custom name visible	false
4	Boolean	Is silent	false
5	Boolean	Has no gravity	false
6	Pose	Pose	STANDING
7	VarInt	Ticks frozen in powdered snow	0
8	VarInt	Interpolation delay	0
9	VarInt	Transformation interpolation duration	0
10	VarInt	Position/Rotation interpolation duration	0
11	Vector3	Translation	(0.0, 0.0, 0.0)
12	Vector3	Scale	(1.0, 1.0, 1.0)
13	Quaternion	Rotation left	(0.0, 0.0, 0.0, 1.0)
14	Quaternion	Rotation right	(0.0, 0.0, 0.0, 1.0)
15	Byte	Billboard Constraints (0 = FIXED, 1 = VERTICAL, 2 = HORIZONTAL, 3 = CENTER)	0
16	VarInt	Brightness override (blockLight << 4 | skyLight << 20)	-1
17	Float	View range	1.0
18	Float	Shadow radius	0.0
19	Float	Shadow strength	1.0
20	Float	Width	0.0
21	Float	Height	0.0
22	VarInt	Glow color override	-1
23	Chat	Text	Empty
24	VarInt	Line width	200
25	VarInt	Background color	1073741824 (0x40000000)
26	Byte	Text opacity	-1 (fully opaque)
27	Byte	Bit mask	Meaning	0
0x01	Has shadow
0x02	Is see through
0x04	Use default background color
0x08	Alignment:
0 = CENTER
1 or 3 = LEFT
2 = RIGHT
     */

    @Override
    public void encode(ByteBuf byteBuf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
        ProtocolUtils.writeVarInt(byteBuf, id); //Entity ID

        new ByteMetaData(0, (byte) 0x40).encode(byteBuf, protocolVersion); //Bit mask

//        new OptionalChatMetaData(2, Component.text("Hello!")).encode(byteBuf, protocolVersion); //Custom name
        new BooleanMetaData(5, true).encode(byteBuf, protocolVersion); //No gravity

        //end entity

        new Vector3MetaData(10, 0.0F, 1.0F, 0.0F).encode(byteBuf, protocolVersion); //Translation

        new ByteMetaData(14, (byte) 1).encode(byteBuf, protocolVersion); //Billboard Constraints VERTICAL

        new FloatMetaData(16, 1.0F).encode(byteBuf, protocolVersion); //View range

        new VarIntMetaData(21, -1).encode(byteBuf, protocolVersion); //Glow color override

        //end display

        new ChatMetaData(22, this.text).encode(byteBuf, protocolVersion); //Text
        //end text

        byteBuf.writeByte(255); //at the end of pack method has writeByte(255) which is 0xFF
    }


    public void encode2(ByteBuf byteBuf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
        ProtocolUtils.writeVarInt(byteBuf, id); //Entity ID
        byteBuf.writeByte(0); //Do nothing
        ProtocolUtils.writeVarInt(byteBuf, 300); //Air ticks
        byteBuf.writeBoolean(false); //Is custom name visible
        byteBuf.writeBoolean(false); //Has custom name (OptChat)
        byteBuf.writeBoolean(false); //Is silent
        byteBuf.writeBoolean(true); //Has no gravity
        ProtocolUtils.writeVarInt(byteBuf, 0); //POSE Standing
        ProtocolUtils.writeVarInt(byteBuf, 0); //Frozen

        //end entity

        ProtocolUtils.writeVarInt(byteBuf, 0); //POS Interpolation duration
        ProtocolUtils.writeVarInt(byteBuf, 0); //POS Transformation start delta
        ProtocolUtils.writeVarInt(byteBuf, 0); //POS Transformation duration

        byteBuf.writeFloat(0.0F); //X Translation
        byteBuf.writeFloat(1.0F); //Y Translation
        byteBuf.writeFloat(0.0F); //Z Translation

        byteBuf.writeFloat(1.0F); //X Scale
        byteBuf.writeFloat(1.0F); //Y Scale
        byteBuf.writeFloat(1.0F); //Z Scale

        byteBuf.writeFloat(0.0F); //X Rotation Right
        byteBuf.writeFloat(0.0F); //Y Rotation Right
        byteBuf.writeFloat(0.0F); //Z Rotation Right
        byteBuf.writeFloat(1.0F); //W Rotation Right

        byteBuf.writeFloat(0.0F); //X Rotation Left
        byteBuf.writeFloat(0.0F); //Y Rotation Left
        byteBuf.writeFloat(0.0F); //Z Rotation Left
        byteBuf.writeFloat(1.0F); //W Rotation Left

        byteBuf.writeByte(1); //Billboard Constraints VERTICAL
        ProtocolUtils.writeVarInt(byteBuf, -1); //Brightness override
        byteBuf.writeFloat(1.0F); //View range
        byteBuf.writeFloat(0.0F); //Shadow radius
        byteBuf.writeFloat(1.0F); //Shadow strength
        byteBuf.writeFloat(0.0F); //Width
        byteBuf.writeFloat(0.0F); //Height

        ProtocolUtils.writeVarInt(byteBuf, -1); //Glow color override

        //end display

        ProtocolUtils.writeString(byteBuf, ProtocolUtils.getJsonChatSerializer(protocolVersion).serialize(this.text));
        ProtocolUtils.writeVarInt(byteBuf, 200); //Line width
        ProtocolUtils.writeVarInt(byteBuf, 1073741824); //Background color
        byteBuf.writeByte((byte) -1); //Text opacity
        byteBuf.writeByte((byte) 0); //Has shadow


        byteBuf.writeByte(0xFF);
        //ClientboundSetEntityDataPacket at the end of pack method has writeByte(255) which is 0xFF
    }

    @Override
    public boolean handle(MinecraftSessionHandler minecraftSessionHandler) {
        return true;
    }
}

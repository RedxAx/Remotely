package redxax.oxy.remotely.resync.bridge;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
//#if MC >= 1.21.1
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import restudio.rescreen.util.Notification;
//#if MC >= 1.21.11
import net.minecraft.resources.Identifier;
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//#endif
//#else
//$$ import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
//$$ import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
//$$ import net.minecraft.resources.ResourceLocation;
//#endif

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ReSyncVanillaPacketAdapter {
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;
    //#if MC >= 1.21.11
    private static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("resync", "bridge");
    private static final Identifier REGISTER_CHANNEL = Identifier.fromNamespaceAndPath("minecraft", "register");
    //#else
    //#if MC >= 1.21.1
    //$$ private static final ResourceLocation CHANNEL = ResourceLocation.fromNamespaceAndPath("resync", "bridge");
    //$$ private static final ResourceLocation REGISTER_CHANNEL = ResourceLocation.fromNamespaceAndPath("minecraft", "register");
    //#else
    //$$ private static final ResourceLocation CHANNEL = new ResourceLocation("resync", "bridge");
    //$$ private static final ResourceLocation REGISTER_CHANNEL = new ResourceLocation("minecraft", "register");
    //#endif
    //#endif
    private static final byte[] REGISTER_PAYLOAD = "resync:bridge".getBytes(StandardCharsets.UTF_8);

    public static boolean isBridgeChannel(Object id) {
        return CHANNEL.equals(id);
    }

    public boolean registerBridgeChannel() {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            return false;
        }
        try {
            //#if MC >= 1.21.1
            connection.send(new ServerboundCustomPayloadPacket(createRegisterPayload()));
            //#else
            //$$ FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            //$$ buffer.writeBytes(REGISTER_PAYLOAD);
            //$$ connection.send(new ServerboundCustomPayloadPacket(REGISTER_CHANNEL, buffer));
            //#endif
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public boolean send(byte[] payload) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection = client.getConnection();
        if (connection == null || payload == null || payload.length > MAX_PAYLOAD_BYTES) {
            return false;
        }
        try {
            //#if MC >= 1.21.1
            connection.send(new ServerboundCustomPayloadPacket(new BridgePayload(payload)));
            //#else
            //$$ FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            //$$ buffer.writeBytes(payload);
            //$$ connection.send(new ServerboundCustomPayloadPacket(CHANNEL, buffer));
            //#endif
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public byte[] read(ClientboundCustomPayloadPacket packet) {
        if (packet == null) {
            return null;
        }
        //#if MC >= 1.21.1
        return read(packet.payload());
        //#else
        //$$ if (!CHANNEL.equals(packet.getIdentifier())) {
        //$$     return null;
        //$$ }
        //$$ FriendlyByteBuf buffer = packet.getData();
        //$$ int length = buffer.readableBytes();
        //$$ if (length > MAX_PAYLOAD_BYTES) {
        //$$     return null;
        //$$ }
        //$$ byte[] data = new byte[length];
        //$$ buffer.readBytes(data);
        //$$ return data;
        //#endif
    }

    //#if MC >= 1.21.1
    private CustomPacketPayload createRegisterPayload() {
        try {
            Class<?> payloadClass = Class.forName("net.fabricmc.fabric.impl.networking.RegistrationPayload");
            Constructor<?> constructor = payloadClass.getConstructor(CustomPacketPayload.Type.class, List.class);
            Object registerType = payloadClass.getField("REGISTER").get(null);
            return (CustomPacketPayload) constructor.newInstance(registerType, List.of(CHANNEL));
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return new RegisterPayload();
        }
    }

    public byte[] read(CustomPacketPayload payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof BridgePayload bridgePayload) {
            return bridgePayload.data();
        }
        if (!CHANNEL.equals(payload.type().id())) {
            return null;
        }
        new Notification("ReSync", "Bridge Payload Discarded", Notification.Type.WARN);
        return null;
    }
    //#endif

    //#if MC >= 1.21.1
    public record BridgePayload(byte[] data) implements CustomPacketPayload {
        //#if MC >= 1.21.11
        public static final Type<BridgePayload> TYPE = new Type<>(CHANNEL);
        //#else
        //$$ public static final Type<BridgePayload> TYPE = new Type<>(CHANNEL);
        //#endif
        public static final StreamCodec<FriendlyByteBuf, BridgePayload> STREAM_CODEC = CustomPacketPayload.codec(BridgePayload::write, BridgePayload::read);
        public static final StreamCodec<RegistryFriendlyByteBuf, BridgePayload> REGISTRY_STREAM_CODEC = CustomPacketPayload.codec(BridgePayload::write, BridgePayload::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeBytes(data == null ? new byte[0] : data);
        }

        private static BridgePayload read(FriendlyByteBuf buffer) {
            int length = buffer.readableBytes();
            if (length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Bridge payload too large");
            }
            byte[] data = new byte[length];
            buffer.readBytes(data);
            return new BridgePayload(data);
        }
    }

    public record RegisterPayload() implements CustomPacketPayload {
        //#if MC >= 1.21.11
        public static final Type<RegisterPayload> TYPE = new Type<>(REGISTER_CHANNEL);
        //#else
        //$$ public static final Type<RegisterPayload> TYPE = new Type<>(REGISTER_CHANNEL);
        //#endif
        public static final StreamCodec<FriendlyByteBuf, RegisterPayload> STREAM_CODEC = CustomPacketPayload.codec(RegisterPayload::write, RegisterPayload::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeBytes(REGISTER_PAYLOAD);
        }

        private static RegisterPayload read(FriendlyByteBuf buffer) {
            buffer.skipBytes(buffer.readableBytes());
            return new RegisterPayload();
        }
    }

    //#endif
}

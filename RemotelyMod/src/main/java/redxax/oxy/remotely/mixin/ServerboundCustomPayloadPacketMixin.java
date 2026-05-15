package redxax.oxy.remotely.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
//#if MC >= 1.21.1
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.TypeAndCodec;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import redxax.oxy.remotely.resync.bridge.ReSyncVanillaPacketAdapter;

import java.util.ArrayList;
import java.util.List;
//#endif

//#if MC >= 1.21.1
@Mixin(ServerboundCustomPayloadPacket.class)
//#else
//$$ @Pseudo
//$$ @Mixin(targets = "net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket")
//#endif
public class ServerboundCustomPayloadPacketMixin {
    //#if MC >= 1.21.1
    //#if NEOFORGE
    //$$ @ModifyArg(
    //$$     method = "<clinit>",
    //$$     at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/protocol/PacketFlow;)Lnet/minecraft/network/codec/StreamCodec;", ordinal = 0),
    //$$     index = 1
    //$$ )
    //$$ private static List<TypeAndCodec<? super FriendlyByteBuf, ?>> remotely$addNeoForgeBridgePayload(List<TypeAndCodec<? super FriendlyByteBuf, ?>> codecs) {
    //$$     List<TypeAndCodec<? super FriendlyByteBuf, ?>> updated = new ArrayList<>(codecs);
    //$$     updated.add(new CustomPacketPayload.TypeAndCodec<>(ReSyncVanillaPacketAdapter.BridgePayload.TYPE, ReSyncVanillaPacketAdapter.BridgePayload.STREAM_CODEC));
    //$$     updated.add(new CustomPacketPayload.TypeAndCodec<>(ReSyncVanillaPacketAdapter.RegisterPayload.TYPE, ReSyncVanillaPacketAdapter.RegisterPayload.STREAM_CODEC));
    //$$     return updated;
    //$$ }
    //#else
    @ModifyArg(
        method = "<clinit>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;"),
        index = 1,
        require = 1
    )
    private static List<TypeAndCodec<? super FriendlyByteBuf, ?>> remotely$addFabricBridgePayload(List<TypeAndCodec<? super FriendlyByteBuf, ?>> codecs) {
        List<TypeAndCodec<? super FriendlyByteBuf, ?>> updated = new ArrayList<>(codecs);
        updated.add(new CustomPacketPayload.TypeAndCodec<>(ReSyncVanillaPacketAdapter.BridgePayload.TYPE, ReSyncVanillaPacketAdapter.BridgePayload.STREAM_CODEC));
        updated.add(new CustomPacketPayload.TypeAndCodec<>(ReSyncVanillaPacketAdapter.RegisterPayload.TYPE, ReSyncVanillaPacketAdapter.RegisterPayload.STREAM_CODEC));
        return updated;
    }
    //#endif
    //#endif
}

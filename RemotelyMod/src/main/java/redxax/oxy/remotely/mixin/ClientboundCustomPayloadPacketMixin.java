package redxax.oxy.remotely.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
//#if MC >= 1.21.1
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.FallbackProvider;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import redxax.oxy.remotely.resync.bridge.ReSyncVanillaPacketAdapter;

import java.util.ArrayList;
import java.util.List;
//#endif

//#if MC >= 1.21.1
@Mixin(ClientboundCustomPayloadPacket.class)
//#else
//$$ @Pseudo
//$$ @Mixin(targets = "net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket")
//#endif
public class ClientboundCustomPayloadPacketMixin {
    //#if MC >= 1.21.1
    //#if NEOFORGE
    //$$ @ModifyArg(
    //$$     method = "<clinit>",
    //$$     at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/protocol/PacketFlow;)Lnet/minecraft/network/codec/StreamCodec;", ordinal = 0),
    //$$     index = 0
    //$$ )
    //$$ private static FallbackProvider<RegistryFriendlyByteBuf> remotely$wrapNeoForgeGameplayBridgeFallback(FallbackProvider<RegistryFriendlyByteBuf> fallback) {
    //$$     return id -> ReSyncVanillaPacketAdapter.isBridgeChannel(id) ? ReSyncVanillaPacketAdapter.BridgePayload.REGISTRY_STREAM_CODEC : fallback.create(id);
    //$$ }
    //$$
    //$$ @ModifyArg(
    //$$     method = "<clinit>",
    //$$     at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/protocol/PacketFlow;)Lnet/minecraft/network/codec/StreamCodec;", ordinal = 0),
    //$$     index = 1
    //$$ )
    //$$ private static List remotely$addNeoForgeGameplayBridgePayload(List codecs) {
    //$$     List updated = new ArrayList(codecs);
    //$$     updated.add(new CustomPacketPayload.TypeAndCodec<>(ReSyncVanillaPacketAdapter.BridgePayload.TYPE, ReSyncVanillaPacketAdapter.BridgePayload.REGISTRY_STREAM_CODEC));
    //$$     return updated;
    //$$ }
    //#else
    @ModifyArg(
        method = "<clinit>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;", ordinal = 0),
        index = 0
    )
    private static FallbackProvider<RegistryFriendlyByteBuf> remotely$wrapFabricGameplayBridgeFallback(FallbackProvider<RegistryFriendlyByteBuf> fallback) {
        return id -> ReSyncVanillaPacketAdapter.isBridgeChannel(id) ? ReSyncVanillaPacketAdapter.BridgePayload.REGISTRY_STREAM_CODEC : fallback.create(id);
    }

    @ModifyArg(
        method = "<clinit>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;", ordinal = 0),
        index = 1
    )
    private static List remotely$addFabricGameplayBridgePayload(List codecs) {
        List updated = new ArrayList(codecs);
        updated.add(new CustomPacketPayload.TypeAndCodec<>(ReSyncVanillaPacketAdapter.BridgePayload.TYPE, ReSyncVanillaPacketAdapter.BridgePayload.REGISTRY_STREAM_CODEC));
        return updated;
    }
    //#endif
    //#endif
}

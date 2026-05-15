package redxax.oxy.remotely.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
//#if MC >= 1.21.1
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#else
//$$ import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.resync.bridge.ReSyncVanillaBridgeManager;
import redxax.oxy.remotely.resync.bridge.ReSyncVanillaPacketAdapter;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    private final ReSyncVanillaPacketAdapter remotely$adapter = new ReSyncVanillaPacketAdapter();

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    //#if MC >= 1.21.1
    private void remotely$handleCustomPayload(CustomPacketPayload packet, CallbackInfo ci) {
    //#else
    //$$ private void remotely$handleCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
    //#endif
        byte[] payload = remotely$adapter.read(packet);
        if (payload == null) {
            return;
        }
        ReSyncVanillaBridgeManager.getInstance().handlePayload(payload);
        ci.cancel();
    }
}

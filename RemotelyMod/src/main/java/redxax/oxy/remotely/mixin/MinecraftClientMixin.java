package redxax.oxy.remotely.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.servers.ReverseProxyManager;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        RemotelyClient.INSTANCE.shutdownAllTerminals();
        ReverseProxyManager.shutdownAll();
    }
}

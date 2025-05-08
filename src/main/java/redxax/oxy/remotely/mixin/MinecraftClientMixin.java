package redxax.oxy.remotely.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.RemotelyClient;

// Example using Mixin
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        RemotelyClient.INSTANCE.shutdownAllTerminals();
    }
}
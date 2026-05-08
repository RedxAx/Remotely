package redxax.oxy.remotely.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.rematrix.mc.RematrixScreen;
import redxax.oxy.remotely.servers.ReProxyManager;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        RemotelyClient.INSTANCE.shutdownAllTerminals();
        ReProxyManager.stopAll();
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        //#if MC >= 26.2
        //$$ if (client.gui.screen() instanceof RematrixScreen && !(screen instanceof RematrixScreen)) {
        //$$     if (RematrixScreen.shouldBlockMinecraftClose()) {
        //$$         RematrixScreen.rememberMinecraftScreen(screen);
        //$$         ci.cancel();
        //$$     }
        //$$ }
        //#else
        if (client.screen instanceof RematrixScreen && !(screen instanceof RematrixScreen)) {
            if (RematrixScreen.shouldBlockMinecraftClose()) {
                RematrixScreen.rememberMinecraftScreen(screen);
                ci.cancel();
            }
        }
        //#endif
    }
}

package redxax.oxy.remotely.mixin;

import net.minecraft.client.gui.Gui;
//#if MC >= 26.2
//$$ import net.minecraft.client.gui.screens.Screen;
//#endif
import org.spongepowered.asm.mixin.Mixin;
//#if MC >= 26.2
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$ import redxax.oxy.remotely.rematrix.mc.RematrixScreen;
//#endif

@Mixin(Gui.class)
public class GuiScreenMixin {
    //#if MC >= 26.2
//$$     @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
//$$     private void onSetScreen(Screen screen, CallbackInfo ci) {
//$$         Gui gui = (Gui) (Object) this;
//$$         if (gui.screen() instanceof RematrixScreen && !(screen instanceof RematrixScreen)) {
//$$             if (RematrixScreen.shouldBlockMinecraftClose()) {
//$$                 RematrixScreen.rememberMinecraftScreen(screen);
//$$                 ci.cancel();
//$$             }
//$$         }
//$$     }
    //#endif
}

package redxax.oxy.remotely.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import restudio.rescreen.ui.core.ScreenManager;

@Mixin(value = KeyboardHandler.class)
public class KeyboardMixin {

    @Unique
    private final Minecraft client = Minecraft.getInstance();

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKey(long l, int i, KeyEvent keyEvent, CallbackInfo ci) {
        if (client.hasControlDown() && keyEvent.key() == GLFW.GLFW_KEY_B) {
            if (client.screen == null) return;
            client.screen.keyPressed(keyEvent);
            ci.cancel();
        }
    }
}

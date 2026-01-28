package redxax.oxy.remotely.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
//#if MC >= 1.21.9
import net.minecraft.client.input.KeyEvent;
//#endif
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.adapters.ReScreenWrapper;

@Mixin(value = KeyboardHandler.class)
public class KeyboardMixin {

    @Unique
    private final Minecraft client = Minecraft.getInstance();

    @Unique
    private boolean remotely$toggleDown;

    @Unique
    private boolean remotely$shouldToggle(int key, int modifiers) {
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        return alt && !shift && !ctrl && key == GLFW.GLFW_KEY_X;
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    //#if MC >= 1.21.9
    private void onKey(long l, int i, KeyEvent keyEvent, CallbackInfo ci) {
        if (remotely$shouldToggle(keyEvent.key(), keyEvent.modifiers())) {
            if (i == GLFW.GLFW_PRESS) {
                if (ReScreenWrapper.toggleScreen()) {
                    remotely$toggleDown = true;
                    ci.cancel();
                    return;
                }
            } else if (i == GLFW.GLFW_RELEASE) {
                remotely$toggleDown = false;
            } else if (i == GLFW.GLFW_REPEAT && remotely$toggleDown) {
                ci.cancel();
                return;
            }
        }
        if (client.hasControlDown() && keyEvent.key() == GLFW.GLFW_KEY_B) {
            if (client.screen == null) return;
            client.screen.keyPressed(keyEvent);
            ci.cancel();
        }
    }
    //#else
    //$$ private void onKey(long l, int i, int j, int k, int m, CallbackInfo ci) {
    //$$     if (remotely$shouldToggle(i, m)) {
    //$$         if (k == GLFW.GLFW_PRESS) {
    //$$             if (ReScreenWrapper.toggleScreen()) {
    //$$                 remotely$toggleDown = true;
    //$$                 ci.cancel();
    //$$                 return;
    //$$             }
    //$$         } else if (k == GLFW.GLFW_RELEASE) {
    //$$             remotely$toggleDown = false;
    //$$         } else if (k == GLFW.GLFW_REPEAT && remotely$toggleDown) {
    //$$             ci.cancel();
    //$$             return;
    //$$         }
    //$$     }
    //$$     if (((m & GLFW.GLFW_MOD_CONTROL) != 0) && i == GLFW.GLFW_KEY_B && k == GLFW.GLFW_PRESS) {
    //$$         if (client.screen == null) return;
    //$$         client.screen.keyPressed(i, j, k);
    //$$         ci.cancel();
    //$$     }
    //$$ }
    //#endif

}

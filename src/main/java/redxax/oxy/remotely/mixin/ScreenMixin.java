package redxax.oxy.remotely.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.util.CursorUtils;
import redxax.oxy.remotely.util.Notification;
import static redxax.oxy.remotely.config.Config.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

@Mixin(value = Screen.class, targets = "net.minecraft.client.gui.screens.Screen")
public class ScreenMixin {


    @Inject(method = "render", at = @At("TAIL"))
    private void render(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Config.tickTime();
        Config.globalCursorAnimatedColor = CursorUtils.blendColor();
        for (Notification notification : Notification.getActiveNotifications()) {
            notification.update();
            context.pose().pushPose();
            context.pose().translate(0, 0, 499);
            notification.render(context, mouseX, mouseY);
            context.pose().popPose();
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        boolean all = alt && shift && ctrl;
        if (keyCode == GLFW.GLFW_KEY_D && all) {
            enableDebugTools = !enableDebugTools;
            new Notification("Toggled Debug Tools To " + enableDebugTools, Notification.Type.INFO);
        }
        if (!enableDebugTools) return;
        if (keyCode == GLFW.GLFW_KEY_E && all) {
            new Notification("Testing Error Notification", Notification.Type.ERROR);
            cir.setReturnValue(true);
        } else if (keyCode == GLFW.GLFW_KEY_W && all) {
            new Notification("Testing Warning Notification", Notification.Type.WARN);
            cir.setReturnValue(true);
        } else if (keyCode == GLFW.GLFW_KEY_I && all) {
            new Notification("Testing Info Notification", Notification.Type.INFO);
            cir.setReturnValue(true);
        }
    }
}
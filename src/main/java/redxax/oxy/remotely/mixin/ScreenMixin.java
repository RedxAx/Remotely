package redxax.oxy.remotely.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.servers.ReverseProxyManager;
import redxax.oxy.remotely.ui.tests.ContainerTestingScreen;
import redxax.oxy.remotely.ui.tests.WidgetsTestingScreen;
import redxax.oxy.remotely.util.CursorUtils;
import static redxax.oxy.remotely.config.Config.*;

import net.minecraft.client.gui.screens.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

@Mixin(value = Screen.class)
public class ScreenMixin {

    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "render", at = @At("TAIL"))
    private void render(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        restudio.rescreen.config.Config.tickTime();
        CursorUtils.tick();
        Config.globalCursorAnimatedColor = CursorUtils.blendColor();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void keyPressed(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        boolean ctrl = (keyEvent.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (keyEvent.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean alt = (keyEvent.modifiers() & GLFW.GLFW_MOD_ALT) != 0;
        boolean all = alt && shift && ctrl;
        if (keyEvent.key() == GLFW.GLFW_KEY_D && all) {
            enableDebugTools = !enableDebugTools;
            new Notification("Toggled Debug Tools To " + enableDebugTools, Notification.Type.INFO);
        }
        if (!enableDebugTools) return;
        if (keyEvent.key() == GLFW.GLFW_KEY_T && ctrl) {
            ScreenManager.getInstance().setScreen(new WidgetsTestingScreen());
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_C && ctrl) {
            ScreenManager.getInstance().setScreen(new ContainerTestingScreen());
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_P && all) {
            ReverseProxyManager.listActivePorts();
        }
    }
}

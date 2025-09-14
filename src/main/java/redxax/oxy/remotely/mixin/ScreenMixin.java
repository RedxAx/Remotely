package redxax.oxy.remotely.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.servers.ReverseProxyManager;
import redxax.oxy.remotely.ui.tests.ContainerTestingScreen;
import redxax.oxy.remotely.ui.tests.WidgetsTestingScreen;
import redxax.oxy.remotely.util.CursorUtils;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.config.SettingsScreen.loopOfThemes;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import restudio.rescreen.Main;
import restudio.rescreen.ui.MouseCursor;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

@Mixin(value = Screen.class)
public class ScreenMixin {

    @Shadow public int width;
    @Shadow public int height;


    @Inject(method = "render", at = @At("TAIL"))
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!((Object)this instanceof TitleScreen)) {
            Config.tickTime();
            restudio.rescreen.config.Config.tickTime();
        }
        Config.globalCursorAnimatedColor = CursorUtils.blendColor();
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 500);
//        LoadingAnimation.render(context, width, height, mouseX, mouseY);
        context.getMatrices().pop();
        for (Notification notification : Notification.getActiveNotifications()) {
            notification.update();
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 499);
//            notification.render(context, mouseX, mouseY);
            context.getMatrices().pop();
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
        if (keyCode == GLFW.GLFW_KEY_RIGHT && alt) {
            loopOfThemes(true);
            cir.setReturnValue(true);
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT && alt) {
            loopOfThemes(false);
            cir.setReturnValue(true);
        }
        if (!enableDebugTools) return;
        if (keyCode == GLFW.GLFW_KEY_T && ctrl) {
            ScreenManager.getInstance().setScreen(new WidgetsTestingScreen());
        }
        if (keyCode == GLFW.GLFW_KEY_C && ctrl) {
            ScreenManager.getInstance().setScreen(new ContainerTestingScreen());
        }
        if (keyCode == GLFW.GLFW_KEY_P && all) {
            ReverseProxyManager.listActivePorts();
        }
    }

    @Inject(method = "init*", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        MouseCursor.reset(false);
        RemotelyClient.INSTANCE.ensureTextRenderer();
        Main.setWindow(MinecraftClient.getInstance().getWindow().getHandle());
    }
}
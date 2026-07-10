package redxax.oxy.remotely.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
//#if MC >= 26.1
//$$ import net.minecraft.client.input.MouseButtonInfo;
//#endif
//#if MC >= 1.21.10 && MC < 26.1
import net.minecraft.client.input.MouseButtonInfo;
//#endif
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.rematrix.mc.RematrixScale;
import restudio.rescreen.platform.input.ReInputEventFactory;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.ui.core.ScreenManager;

@Mixin(value = MouseHandler.class)
public class MouseHandlerMixin {

    @Unique
    private int remotely$pinnedActiveButton = -1;

    @Unique
    private double remotely$lastMouseX;

    @Unique
    private double remotely$lastMouseY;

    @Unique
    private boolean remotely$hasLastMouse;

    //#if MC >= 26.1
    //$$ @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    //$$ private void buttonPinnedInGame(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
    //$$     if (remotely$handlePinnedButton(window, buttonInfo.button(), action)) {
    //$$         ci.cancel();
    //$$     }
    //$$ }
    //$$
    //$$ @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    //$$ private void scrollPinnedInGame(long window, double horizontalAmount, double verticalAmount, CallbackInfo ci) {
    //$$     if (remotely$handlePinnedScroll(window, horizontalAmount, verticalAmount)) {
    //$$         ci.cancel();
    //$$     }
    //$$ }
    //$$
    //$$ @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    //$$ private void dragPinnedInGame(long window, double x, double y, CallbackInfo ci) {
    //$$     if (remotely$handlePinnedDrag(window, x, y)) {
    //$$         ci.cancel();
    //$$     }
    //$$ }
    //#elseif MC >= 1.21.10
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void buttonPinnedInGame(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (remotely$handlePinnedButton(window, buttonInfo.button(), action)) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void scrollPinnedInGame(long window, double horizontalAmount, double verticalAmount, CallbackInfo ci) {
        if (remotely$handlePinnedScroll(window, horizontalAmount, verticalAmount)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void dragPinnedInGame(long window, double x, double y, CallbackInfo ci) {
        if (remotely$handlePinnedDrag(window, x, y)) {
            ci.cancel();
        }
    }
    //#else
    //$$ @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    //$$ private void buttonPinnedInGame(long window, int button, int action, int modifiers, CallbackInfo ci) {
    //$$     if (remotely$handlePinnedButton(window, button, action)) {
    //$$         ci.cancel();
    //$$     }
    //$$ }
    //$$
    //$$ @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    //$$ private void scrollPinnedInGame(long window, double horizontalAmount, double verticalAmount, CallbackInfo ci) {
    //$$     if (remotely$handlePinnedScroll(window, horizontalAmount, verticalAmount)) {
    //$$         ci.cancel();
    //$$     }
    //$$ }
    //$$
    //$$ @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    //$$ private void dragPinnedInGame(long window, double x, double y, CallbackInfo ci) {
    //$$     if (remotely$handlePinnedDrag(window, x, y)) {
    //$$         ci.cancel();
    //$$     }
    //$$ }
    //#endif

    @Unique
    private boolean remotely$handlePinnedButton(long window, int button, int action) {
        RematrixScale.ensureConfigured(Minecraft.getInstance());
        double[] cursor = remotely$getCursor(window);
        remotely$rememberMouse(cursor[0], cursor[1]);
        ScreenManager manager = ScreenManager.getInstance();
        if (action == GLFW.GLFW_PRESS) {
            if (manager.mouseClickedPinnedInGame(ReInputEventFactory.mouseEvent(this, manager.getDesktopWindowsOverlay(), ReMouseEvent.Action.PRESSED, cursor[0], cursor[1], button, remotely$currentModifiers(window), 0, 0))) {
                remotely$pinnedActiveButton = button;
                return true;
            }
            return false;
        }
        if (action == GLFW.GLFW_RELEASE) {
            boolean handled = manager.mouseReleasedPinnedInGame(ReInputEventFactory.mouseEvent(this, manager.getDesktopWindowsOverlay(), ReMouseEvent.Action.RELEASED, cursor[0], cursor[1], button, remotely$currentModifiers(window), 0, 0));
            if (button == remotely$pinnedActiveButton) {
                remotely$pinnedActiveButton = -1;
            }
            return handled;
        }
        return false;
    }

    @Unique
    private boolean remotely$handlePinnedScroll(long window, double horizontalAmount, double verticalAmount) {
        RematrixScale.ensureConfigured(Minecraft.getInstance());
        double[] cursor = remotely$getCursor(window);
        ScreenManager manager = ScreenManager.getInstance();
        return manager.mouseScrolledPinnedInGame(ReInputEventFactory.scrollEvent(this, manager.getDesktopWindowsOverlay(), cursor[0], cursor[1], horizontalAmount, verticalAmount, remotely$currentModifiers(window)));
    }

    @Unique
    private boolean remotely$handlePinnedDrag(long window, double x, double y) {
        RematrixScale.ensureConfigured(Minecraft.getInstance());
        if (remotely$pinnedActiveButton < 0) {
            remotely$rememberMouse(x, y);
            return false;
        }
        double dx = remotely$hasLastMouse ? x - remotely$lastMouseX : 0.0;
        double dy = remotely$hasLastMouse ? y - remotely$lastMouseY : 0.0;
        remotely$rememberMouse(x, y);
        ScreenManager manager = ScreenManager.getInstance();
        return manager.mouseDraggedPinnedInGame(ReInputEventFactory.mouseEvent(this, manager.getDesktopWindowsOverlay(), ReMouseEvent.Action.DRAGGED, x, y, remotely$pinnedActiveButton, remotely$currentModifiers(window), dx, dy));
    }

    @Unique
    private int remotely$currentModifiers(long window) {
        int modifiers = 0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) {
            modifiers |= GLFW.GLFW_MOD_ALT;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS) {
            modifiers |= GLFW.GLFW_MOD_SUPER;
        }
        return modifiers;
    }

    @Unique
    private void remotely$rememberMouse(double x, double y) {
        remotely$lastMouseX = x;
        remotely$lastMouseY = y;
        remotely$hasLastMouse = true;
    }

    @Unique
    private double[] remotely$getCursor(long window) {
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);
        return new double[]{x[0], y[0]};
    }
}

package redxax.oxy.remotely.mixin;

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
    //$$     if (remotely$handlePinnedDrag(x, y)) {
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
        if (remotely$handlePinnedDrag(x, y)) {
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
    //$$     if (remotely$handlePinnedDrag(x, y)) {
    //$$         ci.cancel();
    //$$     }
    //$$ }
    //#endif

    @Unique
    private boolean remotely$handlePinnedButton(long window, int button, int action) {
        double[] cursor = remotely$getCursor(window);
        remotely$rememberMouse(cursor[0], cursor[1]);
        if (action == GLFW.GLFW_PRESS) {
            if (ScreenManager.getInstance().mouseClickedPinnedInGame(cursor[0], cursor[1], button)) {
                remotely$pinnedActiveButton = button;
                return true;
            }
            return false;
        }
        if (action == GLFW.GLFW_RELEASE) {
            boolean handled = ScreenManager.getInstance().mouseReleasedPinnedInGame(cursor[0], cursor[1], button);
            if (button == remotely$pinnedActiveButton) {
                remotely$pinnedActiveButton = -1;
            }
            return handled;
        }
        return false;
    }

    @Unique
    private boolean remotely$handlePinnedScroll(long window, double horizontalAmount, double verticalAmount) {
        double[] cursor = remotely$getCursor(window);
        return ScreenManager.getInstance().mouseScrolledPinnedInGame(cursor[0], cursor[1], horizontalAmount, verticalAmount);
    }

    @Unique
    private boolean remotely$handlePinnedDrag(double x, double y) {
        if (remotely$pinnedActiveButton < 0) {
            remotely$rememberMouse(x, y);
            return false;
        }
        double dx = remotely$hasLastMouse ? x - remotely$lastMouseX : 0.0;
        double dy = remotely$hasLastMouse ? y - remotely$lastMouseY : 0.0;
        remotely$rememberMouse(x, y);
        return ScreenManager.getInstance().mouseDraggedPinnedInGame(x, y, remotely$pinnedActiveButton, dx, dy);
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

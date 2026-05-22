package redxax.oxy.remotely.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
//#if MC >= 26.1
//$$ import net.minecraft.client.input.CharacterEvent;
//$$ import net.minecraft.client.input.KeyEvent;
//$$ import net.minecraft.client.input.MouseButtonEvent;
//#endif
//#if MC >= 1.21.9 && MC < 26.1
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import redxax.oxy.remotely.rematrix.mc.RematrixScale;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

@Mixin(value = ContainerEventHandler.class)
public interface ContainerEventHandlerMixin {

    @Unique
    private boolean remotely$isScreen() {
        return this instanceof Screen;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    //#if MC >= 1.21.9 || MC >= 26.1
    private void mouseClicked(MouseButtonEvent event, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (!remotely$isScreen()) {
            return;
        }
        double sf = Minecraft.getInstance().getWindow().getGuiScale();
        if (remotely$mouseClickedNotification(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (ScreenManager.getInstance().mouseClickedPinnedInGame(event.x() * sf, event.y() * sf, event.button())) {
            cir.setReturnValue(true);
        }
    }
    //#else
    //$$ private void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (!remotely$isScreen()) {
    //$$         return;
    //$$     }
    //$$     double sf = Minecraft.getInstance().getWindow().getGuiScale();
    //$$     if (remotely$mouseClickedNotification(mouseX, mouseY, button)) {
    //$$         cir.setReturnValue(true);
    //$$         return;
    //$$     }
    //$$     if (ScreenManager.getInstance().mouseClickedPinnedInGame(mouseX * sf, mouseY * sf, button)) {
    //$$         cir.setReturnValue(true);
    //$$     }
    //$$ }
    //#endif

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    //#if MC >= 1.21.9 || MC >= 26.1
    private void mouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!remotely$isScreen()) {
            return;
        }
        double sf = Minecraft.getInstance().getWindow().getGuiScale();
        if (remotely$mouseReleasedNotification(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (ScreenManager.getInstance().mouseReleasedPinnedInGame(event.x() * sf, event.y() * sf, event.button())) {
            cir.setReturnValue(true);
        }
    }
    //#else
    //$$ private void mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (!remotely$isScreen()) {
    //$$         return;
    //$$     }
    //$$     double sf = Minecraft.getInstance().getWindow().getGuiScale();
    //$$     if (remotely$mouseReleasedNotification(mouseX, mouseY, button)) {
    //$$         cir.setReturnValue(true);
    //$$         return;
    //$$     }
    //$$     if (ScreenManager.getInstance().mouseReleasedPinnedInGame(mouseX * sf, mouseY * sf, button)) {
    //$$         cir.setReturnValue(true);
    //$$     }
    //$$ }
    //#endif

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    //#if MC >= 1.21.9 || MC >= 26.1
    private void mouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!remotely$isScreen()) {
            return;
        }
        double sf = Minecraft.getInstance().getWindow().getGuiScale();
        if (remotely$mouseDraggedNotification(event.x(), event.y(), event.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
            return;
        }
        if (ScreenManager.getInstance().mouseDraggedPinnedInGame(event.x() * sf, event.y() * sf, event.button(), deltaX * sf, deltaY * sf)) {
            cir.setReturnValue(true);
        }
    }
    //#else
    //$$ private void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (!remotely$isScreen()) {
    //$$         return;
    //$$     }
    //$$     double sf = Minecraft.getInstance().getWindow().getGuiScale();
    //$$     if (remotely$mouseDraggedNotification(mouseX, mouseY, button, deltaX, deltaY)) {
    //$$         cir.setReturnValue(true);
    //$$         return;
    //$$     }
    //$$     if (ScreenManager.getInstance().mouseDraggedPinnedInGame(mouseX * sf, mouseY * sf, button, deltaX * sf, deltaY * sf)) {
    //$$         cir.setReturnValue(true);
    //$$     }
    //$$ }
    //#endif

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    //#if MC >= 1.20.3
    private void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (!remotely$isScreen()) {
            return;
        }
        double sf = Minecraft.getInstance().getWindow().getGuiScale();
        if (remotely$mouseScrolledNotification(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
            return;
        }
        if (ScreenManager.getInstance().mouseScrolledPinnedInGame(mouseX * sf, mouseY * sf, horizontalAmount, verticalAmount)) {
            cir.setReturnValue(true);
        }
    }
    //#else
    //$$ private void mouseScrolled(double mouseX, double mouseY, double amount, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (!remotely$isScreen()) {
    //$$         return;
    //$$     }
    //$$     double sf = Minecraft.getInstance().getWindow().getGuiScale();
    //$$     if (remotely$mouseScrolledNotification(mouseX, mouseY, amount)) {
    //$$         cir.setReturnValue(true);
    //$$         return;
    //$$     }
    //$$     if (ScreenManager.getInstance().mouseScrolledPinnedInGame(mouseX * sf, mouseY * sf, 0.0, amount)) {
    //$$         cir.setReturnValue(true);
    //$$     }
    //$$ }
    //#endif

    @Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true)
    //#if MC >= 1.21.9 || MC >= 26.1
    private void keyReleasedPinnedInGame(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (!remotely$isScreen()) {
            return;
        }
        if (ScreenManager.getInstance().keyReleasedPinnedInGame(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers())) {
            cir.setReturnValue(true);
        }
    }
    //#else
    //$$ private void keyReleasedPinnedInGame(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (!remotely$isScreen()) {
    //$$         return;
    //$$     }
    //$$     if (ScreenManager.getInstance().keyReleasedPinnedInGame(keyCode, scanCode, modifiers)) {
    //$$         cir.setReturnValue(true);
    //$$     }
    //$$ }
    //#endif

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    //#if MC >= 26.1
    //$$ private void charTypedPinnedInGame(CharacterEvent characterEvent, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (!remotely$isScreen()) {
    //$$         return;
    //$$     }
    //$$     int codepoint = characterEvent.codepoint();
    //$$     boolean handled = false;
    //$$     char[] chars = Character.toChars(codepoint);
    //$$     for (char chr : chars) {
    //$$         handled = ScreenManager.getInstance().charTypedPinnedInGame(chr, 0) || handled;
    //$$     }
    //$$     if (handled) {
    //$$         cir.setReturnValue(true);
    //$$     }
    //$$ }
    //#elseif MC >= 1.21.9
    private void charTypedPinnedInGame(CharacterEvent characterEvent, CallbackInfoReturnable<Boolean> cir) {
        if (!remotely$isScreen()) {
            return;
        }
        int codepoint = characterEvent.codepoint();
        boolean handled = false;
        char[] chars = Character.toChars(codepoint);
        for (char chr : chars) {
            handled = ScreenManager.getInstance().charTypedPinnedInGame(chr, characterEvent.modifiers()) || handled;
        }
        if (handled) {
            cir.setReturnValue(true);
        }
    }
    //#else
    //$$ private void charTypedPinnedInGame(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (!remotely$isScreen()) {
    //$$         return;
    //$$     }
    //$$     if (ScreenManager.getInstance().charTypedPinnedInGame(chr, modifiers)) {
    //$$         cir.setReturnValue(true);
    //$$     }
    //$$ }
    //#endif

    @Unique
    private double remotely$inputScale() {
        Minecraft minecraft = Minecraft.getInstance();
        RematrixScale.ensureConfigured(minecraft);
        double mcScale = minecraft.getWindow().getGuiScale();
        float reScale = ScreenManager.getInstance().getGuiScale();
        if (reScale == 0) {
            return 1.0;
        }
        return mcScale / reScale;
    }

    @Unique
    private boolean remotely$mouseClickedNotification(double mouseX, double mouseY, int button) {
        double scale = remotely$inputScale();
        double scaledX = mouseX * scale;
        double scaledY = mouseY * scale;
        for (Notification notification : Notification.getActiveNotifications()) {
            if (notification.mouseClicked(scaledX, scaledY, button)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean remotely$mouseReleasedNotification(double mouseX, double mouseY, int button) {
        double scale = remotely$inputScale();
        double scaledX = mouseX * scale;
        double scaledY = mouseY * scale;
        for (Notification notification : Notification.getActiveNotifications()) {
            if (notification.mouseReleased(scaledX, scaledY, button)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean remotely$mouseDraggedNotification(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        double scale = remotely$inputScale();
        double scaledX = mouseX * scale;
        double scaledY = mouseY * scale;
        double scaledDeltaX = deltaX * scale;
        double scaledDeltaY = deltaY * scale;
        for (Notification notification : Notification.getActiveNotifications()) {
            if (notification.mouseDragged(scaledX, scaledY, button, scaledDeltaX, scaledDeltaY)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean remotely$mouseScrolledNotification(double mouseX, double mouseY, double verticalAmount) {
        double scale = remotely$inputScale();
        int scaledX = (int) (mouseX * scale);
        int scaledY = (int) (mouseY * scale);
        for (Notification notification : Notification.getActiveNotifications()) {
            if (notification.mouseScrolled(scaledX, scaledY, verticalAmount)) {
                return true;
            }
        }
        return false;
    }
}

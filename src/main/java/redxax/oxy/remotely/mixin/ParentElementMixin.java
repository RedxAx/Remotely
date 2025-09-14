package redxax.oxy.remotely.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import restudio.rescreen.ui.LoadingAnimation;
import restudio.rescreen.ui.MouseCursor;
import restudio.rescreen.ui.widgets.ScrollSelectorWidget;
import restudio.rescreen.util.Notification;


@Mixin(value = ParentElement.class)
public interface ParentElementMixin {


    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        for (Notification notification : Notification.getActiveNotifications()) {
            if (notification.mouseClicked(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void mouseScrolled(double mouseX, double mouseY /*? !=1.20.1 {*/ , double horizontalAmount /*?}*/, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        MouseCursor.mouseScrolled(verticalAmount);
        for (Element child : MinecraftClient.getInstance().currentScreen.children()) {
            if (child instanceof ScrollSelectorWidget scrollSelect) {
                if (scrollSelect.scroll(mouseX, mouseY, verticalAmount)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }

}
package redxax.oxy.remotely.mixin;

import net.minecraft.client.gui.ParentElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import redxax.oxy.remotely.util.Notification;


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

}
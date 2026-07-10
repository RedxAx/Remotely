package redxax.oxy.remotely.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.rematrix.mc.RematrixScale;
import restudio.rescreen.platform.input.ReInputEventFactory;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.ui.core.ScreenManager;

@Mixin(value = GuiEventListener.class)
public interface GuiEventListenerMixin {

    @Unique
    private boolean remotely$isScreen() {
        return this instanceof Screen;
    }

    @Inject(method = "mouseMoved", at = @At("HEAD"))
    private void mouseMoved(double mouseX, double mouseY, CallbackInfo ci) {
        if (!remotely$isScreen()) {
            return;
        }
        double sf = RematrixScale.managerInputScale(Minecraft.getInstance());
        ScreenManager manager = ScreenManager.getInstance();
        manager.mouseMovedPinnedInGame(ReInputEventFactory.mouseEvent(this, manager.getDesktopWindowsOverlay(), ReMouseEvent.Action.MOVED, mouseX * sf, mouseY * sf, -1, 0, 0, 0));
    }
}

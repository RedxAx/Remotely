package redxax.oxy.remotely.mixin;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.adapters.ICustomWidgetHolder;
import redxax.oxy.remotely.util.InitializationManager;
import redxax.oxy.remotely.util.ScreenInitHelper;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends net.minecraft.client.gui.screens.Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        InitializationManager.ensureInitialized();
        ScreenInitHelper.resetTitleScreen(this);
        if (this instanceof ICustomWidgetHolder holder) {
            holder.remotely$clearWidgets();
        }
    }
}

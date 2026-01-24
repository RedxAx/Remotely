package redxax.oxy.remotely.mixin;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.adapters.ICustomWidgetHolder;
import redxax.oxy.remotely.util.InitializationManager;
import redxax.oxy.remotely.util.ScreenInitHelper;

@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends net.minecraft.client.gui.screens.Screen {

    protected GameMenuScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        InitializationManager.ensureInitialized();
        if (this instanceof ICustomWidgetHolder holder) {
            holder.remotely$clearWidgets();
        }
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addServerManagerButton(CallbackInfo ci) {
        String returnToMenuButtonText = I18n.get("menu.returnToMenu");
        String disconnectButtonText = I18n.get("menu.disconnect");
        AbstractButton optionsButton = this.children().stream()
            .filter(child -> child instanceof AbstractButton)
            .map(child -> (AbstractButton) child)
            .filter(button -> button.getMessage().getString().equals(returnToMenuButtonText) || button.getMessage().getString().equals(disconnectButtonText))
            .findFirst()
            .orElse(null);

        if (optionsButton == null) return;

        ScreenInitHelper.init(this, optionsButton, false);
    }
}

package redxax.oxy.remotely.mixin;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.adapters.ICustomWidgetHolder;
import redxax.oxy.remotely.util.InitializationManager;
import redxax.oxy.remotely.util.ScreenInitHelper;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends net.minecraft.client.gui.screens.Screen {
    @Unique
    private AbstractButton optionsButton;

    protected TitleScreenMixin(Component title) {
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
        String optionsButtonText = I18n.get("menu.options");
        optionsButton = this.children().stream()
            .filter(child -> child instanceof AbstractButton)
            .map(child -> (AbstractButton) child)
            .filter(button -> button.getMessage().getString().equals(optionsButtonText))
            .findFirst()
            .orElse(null);

        if (optionsButton == null) {
            System.err.println("[Remotely] Could not find Options button to attach additional buttons.");
            return;
        }

        ScreenInitHelper.init(this, optionsButton, true);
    }
}

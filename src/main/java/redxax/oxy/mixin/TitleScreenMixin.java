package redxax.oxy.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import redxax.oxy.RemotelyClient;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addMultiTerminalButton(CallbackInfo ci) {
        // Find the language button's position
        ButtonWidget languageButton = this.children().stream()
                .filter(child -> child instanceof ButtonWidget)
                .map(child -> (ButtonWidget) child)
                .filter(button -> button.getMessage().getString().contains("Options..."))
                .findFirst()
                .orElse(null);

        if (languageButton != null) {
            int buttonX = languageButton.getX();
            int buttonY = languageButton.getY() + languageButton.getHeight() + 5;
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("🗔"), // Button icon
                    button -> openMultiTerminalScreen()
            ).dimensions(
                    buttonX,
                    buttonY,
                    20,
                    20
            ).build());
        }
    }

    @Unique
    private void openMultiTerminalScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            RemotelyClient.INSTANCE.openMultiTerminalGUI(client);
        }
    }
}

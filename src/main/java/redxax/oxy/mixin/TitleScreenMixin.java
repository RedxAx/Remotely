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
    private void addServerManagerButton(CallbackInfo ci) {
        ButtonWidget optionsButton = this.children().stream()
                .filter(child -> child instanceof ButtonWidget)
                .map(child -> (ButtonWidget) child)
                .filter(button -> button.getMessage().getString().contains("Options"))
                .findFirst()
                .orElse(null);

        if (optionsButton != null) {
            int buttonX = optionsButton.getX();
            int buttonY = optionsButton.getY() + optionsButton.getHeight() + 5;

            ButtonWidget serverButton = ButtonWidget.builder(
                            Text.literal("Servers"),
                            btn -> openServerManagerScreen()
                    )
                    .dimensions(buttonX, buttonY, 80, 20)
                    .build();
            this.addDrawableChild(serverButton);

            ButtonWidget terminalButton = ButtonWidget.builder(
                            Text.literal("Terminal"),
                            btn -> openMultiTerminalScreen()
                    )
                    .dimensions(buttonX + 85, buttonY, 80, 20)
                    .build();
            this.addDrawableChild(terminalButton);
        }
    }

    @Unique
    private void openServerManagerScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            RemotelyClient.INSTANCE.openServerManagerGUI(client);
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
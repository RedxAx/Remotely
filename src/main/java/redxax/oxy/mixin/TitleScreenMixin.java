package redxax.oxy.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import redxax.oxy.RemotelyClient;
import redxax.oxy.explorer.FileExplorerScreen;
import redxax.oxy.servers.ServerInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addServerManagerButton(CallbackInfo ci) {
        String optionsButtonText = I18n.translate("menu.options");

        ButtonWidget optionsButton = this.children().stream()
                .filter(child -> child instanceof ButtonWidget)
                .map(child -> (ButtonWidget) child)
                .filter(button -> button.getMessage().getString().equals(optionsButtonText))
                .findFirst()
                .orElse(null);

        if (optionsButton != null) {
            int buttonX = optionsButton.getX();
            int buttonY = optionsButton.getY() + optionsButton.getHeight() + 5;

            int smallButtonWidth = 50;
            int largeButtonWidth = 100;
            int gap = 5;
            int totalWidth = smallButtonWidth * 2 + largeButtonWidth + gap * 2;

            if (totalWidth > 200) {
                int excessWidth = totalWidth - 200;
                largeButtonWidth -= excessWidth;
            }

            ButtonWidget serverButton = ButtonWidget.builder(
                            Text.literal("Servers"),
                            btn -> openServerManagerScreen()
                    )
                    .dimensions(buttonX, buttonY, smallButtonWidth, 20)
                    .build();
            this.addDrawableChild(serverButton);

            ButtonWidget fileExplorerButton = ButtonWidget.builder(
                            Text.literal("File Explorer"),
                            btn -> openFileExplorerScreen()
                    )
                    .dimensions(buttonX + smallButtonWidth + gap, buttonY, largeButtonWidth, 20)
                    .build();
            this.addDrawableChild(fileExplorerButton);

            ButtonWidget terminalButton = ButtonWidget.builder(
                            Text.literal("Terminal"),
                            btn -> openMultiTerminalScreen()
                    )
                    .dimensions(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY, smallButtonWidth, 20)
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

    @Unique
    private void openFileExplorerScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(new FileExplorerScreen(client, this, new ServerInfo("C:/")));
        }
    }
}
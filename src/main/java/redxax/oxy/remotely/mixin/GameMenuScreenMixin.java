package redxax.oxy.remotely.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.ui.ReWidgetWrapper;
import redxax.oxy.remotely.util.InitializationManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.SquareButtonWidget;

import static redxax.oxy.remotely.config.Config.mainMenuStyle;
import static redxax.oxy.remotely.config.Config.remotelyDir;

@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends net.minecraft.client.gui.screens.Screen {

    protected GameMenuScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        InitializationManager.ensureInitialized();
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addServerManagerButton(CallbackInfo ci) {
        String returnToMenuButtonText = I18n.get("menu.returnToMenu");
        String disconnectButtonText = I18n.get("menu.disconnect");
        AbstractButton optionsButton = this.children().stream().filter(child -> child instanceof AbstractButton).map(child -> (AbstractButton) child).filter(button -> button.getMessage().getString().equals(returnToMenuButtonText) || button.getMessage().getString().equals(disconnectButtonText)).findFirst().orElse(null);

        if (optionsButton == null) return;

        switch (mainMenuStyle) {
            case "Vanilla" -> {
                int buttonX = optionsButton.getX();
                int buttonY = optionsButton.getY() + optionsButton.getHeight() + 5;
                int smallButtonWidth = 50;
                int largeButtonWidth = 100;
                int gap = 5;
                int totalWidth = smallButtonWidth * 2 + largeButtonWidth + gap * 2;
                int excessWidth = totalWidth - 200;
                largeButtonWidth -= excessWidth;
                AbstractButton serverButton = Button.builder(Component.literal("Servers"), btn -> openServerManagerScreen()).bounds(buttonX, buttonY, smallButtonWidth, 20).build();
                this.addRenderableWidget(serverButton);
                AbstractButton fileExplorerButton = Button.builder(Component.literal("File Explorer"), btn -> openFileExplorerScreen()).bounds(buttonX + smallButtonWidth + gap, buttonY, largeButtonWidth, 20).build();
                this.addRenderableWidget(fileExplorerButton);
                AbstractButton terminalButton = Button.builder(Component.literal("Terminal"), btn -> openMultiTerminalScreen()).bounds(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY, smallButtonWidth, 20).build();
                this.addRenderableWidget(terminalButton);
            }
            case "Minimal" -> {
                int spacing = 8;
                int buttonSize = 18;
                int startX = optionsButton.getX();
                int buttonY = optionsButton.getY() + optionsButton.getHeight() + 5;

                SquareButtonWidget serverBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("manager.png").onClick(this::openServerManagerScreen).hint("Servers").build();
                serverBtn.setPosition(startX, buttonY);
                this.addRenderableWidget(new ReWidgetWrapper(serverBtn));

                SquareButtonWidget terminalBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("terminal.png").onClick(this::openMultiTerminalScreen).hint("Terminal").build();
                terminalBtn.setPosition(startX + (buttonSize + spacing), buttonY);
                this.addRenderableWidget(new ReWidgetWrapper(terminalBtn));

                SquareButtonWidget explorerBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("explorer.png").onClick(this::openFileExplorerScreen).hint("File Explorer").build();
                explorerBtn.setPosition(startX + 2 * (buttonSize + spacing), buttonY);
                this.addRenderableWidget(new ReWidgetWrapper(explorerBtn));
            }
            case "Normal" -> {
                int buttonX = optionsButton.getX();
                int buttonY = optionsButton.getY() + optionsButton.getHeight() + 5;
                int smallButtonWidth = 50;
                int largeButtonWidth = 100;
                int gap = 5;
                int totalWidth = smallButtonWidth * 2 + largeButtonWidth + gap * 2;
                int excessWidth = totalWidth - 200;
                largeButtonWidth -= excessWidth;

                AnimatedButton serverBtn = new AnimatedButton.Builder().label("Servers").onClick(this::openServerManagerScreen).size(smallButtonWidth, 18).entranceAnimation(false).build();
                serverBtn.setPosition(buttonX, buttonY);
                this.addRenderableWidget(new ReWidgetWrapper(serverBtn));

                AnimatedButton explorerBtn = new AnimatedButton.Builder().label("File Explorer").onClick(this::openFileExplorerScreen).size(largeButtonWidth, 18).entranceAnimation(false).build();
                explorerBtn.setPosition(buttonX + smallButtonWidth + gap, buttonY);
                this.addRenderableWidget(new ReWidgetWrapper(explorerBtn));

                AnimatedButton terminalBtn = new AnimatedButton.Builder().label("Terminal").onClick(this::openMultiTerminalScreen).size(smallButtonWidth, 18).entranceAnimation(false).build();
                terminalBtn.setPosition(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY);
                this.addRenderableWidget(new ReWidgetWrapper(terminalBtn));
            }
        }
    }

    @Unique
    private void openServerManagerScreen() {
        RemotelyClient.INSTANCE.openServerManager(this);
    }

    @Unique
    private void openMultiTerminalScreen() {
        RemotelyClient.INSTANCE.openMultiTerminal(this);
    }

    @Unique
    private void openFileExplorerScreen() {
        RemotelyClient.INSTANCE.openFileExplorer(null, remotelyDir);
    }
}

package redxax.oxy.remotely.mixin;

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
import redxax.oxy.remotely.adapters.ICustomWidgetHolder;
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
        if (this instanceof ICustomWidgetHolder holder) {
            holder.remotely$clearWidgets();
        }
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addServerManagerButton(CallbackInfo ci) {
        String returnToMenuButtonText = I18n.get("menu.returnToMenu");
        String disconnectButtonText = I18n.get("menu.disconnect");
        AbstractButton optionsButton = this.children().stream().filter(child -> child instanceof AbstractButton).map(child -> (AbstractButton) child).filter(button -> button.getMessage().getString().equals(returnToMenuButtonText) || button.getMessage().getString().equals(disconnectButtonText)).findFirst().orElse(null);

        if (optionsButton == null) return;

        ICustomWidgetHolder widgetHolder = (ICustomWidgetHolder) this;

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
                int startX = optionsButton.getX() + 1;
                int buttonY = optionsButton.getY() + optionsButton.getHeight() + buttonSize;

                SquareButtonWidget serverBtn = new SquareButtonWidget.Builder().imagePath("manager.png").onClick(this::openServerManagerScreen).build();
                serverBtn.setPosition(startX, buttonY);
                widgetHolder.remotely$addWidget(serverBtn);

                SquareButtonWidget terminalBtn = new SquareButtonWidget.Builder().imagePath("terminal.png").onClick(this::openMultiTerminalScreen).build();
                terminalBtn.setPosition(startX + (buttonSize + spacing), buttonY);
                widgetHolder.remotely$addWidget(terminalBtn);

                SquareButtonWidget explorerBtn = new SquareButtonWidget.Builder().imagePath("explorer.png").onClick(this::openFileExplorerScreen).build();
                explorerBtn.setPosition(startX + 2 * (buttonSize + spacing), buttonY);
                widgetHolder.remotely$addWidget(explorerBtn);
            }
            case "Normal" -> {
                int buttonX = optionsButton.getX() + 1;
                int buttonY = optionsButton.getY() + optionsButton.getHeight() + 18;
                int smallButtonWidth = 50;
                int largeButtonWidth = 100;
                int gap = 5;
                int totalWidth = smallButtonWidth * 2 + largeButtonWidth + gap * 2;
                int excessWidth = totalWidth - 200;
                largeButtonWidth -= excessWidth;

                AnimatedButton serverBtn = new AnimatedButton.Builder().label("Servers").onClick(this::openServerManagerScreen).size(smallButtonWidth, 18).build();
                serverBtn.setPosition(buttonX, buttonY);
                widgetHolder.remotely$addWidget(serverBtn);

                AnimatedButton explorerBtn = new AnimatedButton.Builder().label("File Explorer").onClick(this::openFileExplorerScreen).size(largeButtonWidth, 18).build();
                explorerBtn.setPosition(buttonX + smallButtonWidth + gap, buttonY);
                widgetHolder.remotely$addWidget(explorerBtn);

                AnimatedButton terminalBtn = new AnimatedButton.Builder().label("Terminal").onClick(this::openMultiTerminalScreen).size(smallButtonWidth, 18).build();
                terminalBtn.setPosition(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY);
                widgetHolder.remotely$addWidget(terminalBtn);
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

package redxax.oxy.remotely.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
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
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addServerManagerButton(CallbackInfo ci) {
        String optionsButtonText = I18n.get("menu.options");
        optionsButton = this.children().stream().filter(child -> child instanceof AbstractButton).map(child -> (AbstractButton) child).filter(button -> button.getMessage().getString().equals(optionsButtonText)).findFirst().orElse(null);

        if (optionsButton == null) {
            System.err.println("[Remotely] Could not find Options button to attach additional buttons.");
            return;
        } else {
            System.out.println("[Remotely] Found Options button at: (" + optionsButton.getX() + ", " + optionsButton.getY() + ")");
        }

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
                this.addWidget(serverButton);
                AbstractButton fileExplorerButton = Button.builder(Component.literal("File Explorer"), btn -> openFileExplorerScreen()).bounds(buttonX + smallButtonWidth + gap, buttonY, largeButtonWidth, 20).build();
                this.addWidget(fileExplorerButton);
                AbstractButton terminalButton = Button.builder(Component.literal("Terminal"), btn -> openMultiTerminalScreen()).bounds(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY, smallButtonWidth, 20).build();
                this.addWidget(terminalButton);
            }
            case "Minimal" -> {
                int spacing = 8;
                int buttonSize = 18;
                int startX = optionsButton.getX();
                int buttonY = optionsButton.getY() + optionsButton.getHeight() + 5;

                SquareButtonWidget serverBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("manager.png").onClick(this::openServerManagerScreen).hint("Servers").build();
                serverBtn.setPosition(startX, buttonY);
                this.addWidget(new ReWidgetWrapper(serverBtn));

                SquareButtonWidget terminalBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("terminal.png").onClick(this::openMultiTerminalScreen).hint("Terminal").build();
                terminalBtn.setPosition(startX + (buttonSize + spacing), buttonY);
                this.addWidget(new ReWidgetWrapper(terminalBtn));

                SquareButtonWidget explorerBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("explorer.png").onClick(this::openFileExplorerScreen).hint("File Explorer").build();
                explorerBtn.setPosition(startX + 2 * (buttonSize + spacing), buttonY);
                this.addWidget(new ReWidgetWrapper(explorerBtn));
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

                AnimatedButton serverBtn = new AnimatedButton.Builder().entranceAnimation(false).label("Servers").onClick(this::openServerManagerScreen).size(smallButtonWidth, 18).build();
                serverBtn.setPosition(buttonX, buttonY);
                this.addWidget(new ReWidgetWrapper(serverBtn));

                AnimatedButton explorerBtn = new AnimatedButton.Builder().entranceAnimation(false).label("File Explorer").onClick(this::openFileExplorerScreen).size(largeButtonWidth, 18).build();
                explorerBtn.setPosition(buttonX + smallButtonWidth + gap, buttonY);
                this.addWidget(new ReWidgetWrapper(explorerBtn));

                AnimatedButton terminalBtn = new AnimatedButton.Builder().entranceAnimation(false).label("Terminal").onClick(this::openMultiTerminalScreen).size(smallButtonWidth, 18).build();
                terminalBtn.setPosition(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY);
                this.addWidget(new ReWidgetWrapper(terminalBtn));
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        for (var widget : this.children()) {
            if (widget instanceof ReWidgetWrapper wrapper) {
                wrapper.render(guiGraphics, i, j, f);
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

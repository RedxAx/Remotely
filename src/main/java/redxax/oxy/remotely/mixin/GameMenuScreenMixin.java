package redxax.oxy.remotely.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.adapters.MinecraftDrawContextAdapter;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.SquareButtonWidget;

import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.config.Config.mainMenuStyle;
import static redxax.oxy.remotely.config.Config.remotelyDir;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends net.minecraft.client.gui.screen.Screen {
    @Unique private ButtonWidget optionsButton;
    @Unique private final List<SquareButtonWidget> minimalButtons = new ArrayList<>();
    @Unique private final List<AnimatedButton> normalButtons = new ArrayList<>();
    @Unique private boolean wasMousePressed = false;

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addServerManagerButton(CallbackInfo ci) {
        String returnToMenuButtonText = I18n.translate("menu.returnToMenu");
        String disconnectButtonText = I18n.translate("menu.disconnect");
        optionsButton = this.children().stream().filter(child -> child instanceof ButtonWidget).map(child -> (ButtonWidget) child).filter(button -> button.getMessage().getString().equals(returnToMenuButtonText) || button.getMessage().getString().equals(disconnectButtonText)).findFirst().orElse(null);
        if (optionsButton != null && mainMenuStyle.equals("Vanilla")) {
            int buttonX = optionsButton.getX();
            int buttonY = optionsButton.getY() + optionsButton.getHeight() + 5;
            int smallButtonWidth = 50;
            int largeButtonWidth = 100;
            int gap = 5;
            int totalWidth = smallButtonWidth * 2 + largeButtonWidth + gap * 2;
            int excessWidth = totalWidth - 200;
            largeButtonWidth -= excessWidth;
            ButtonWidget serverButton = ButtonWidget.builder(Text.literal("Servers"), btn -> openServerManagerScreen()).dimensions(buttonX, buttonY, smallButtonWidth, 20).build();
            this.addDrawableChild(serverButton);
            ButtonWidget fileExplorerButton = ButtonWidget.builder(Text.literal("File Explorer"), btn -> openFileExplorerScreen()).dimensions(buttonX + smallButtonWidth + gap, buttonY, largeButtonWidth, 20).build();
            this.addDrawableChild(fileExplorerButton);
            ButtonWidget terminalButton = ButtonWidget.builder(Text.literal("Terminal"), btn -> openMultiTerminalScreen()).dimensions(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY, smallButtonWidth, 20).build();
            this.addDrawableChild(terminalButton);
        }
        if (optionsButton != null && mainMenuStyle.equals("Minimal")) {
            minimalButtons.clear();
            minimalButtons.add(new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("manager.png").onClick(this::openServerManagerScreen).hint("Servers").build());
            minimalButtons.add(new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("terminal.png").onClick(this::openMultiTerminalScreen).hint("Terminal").build());
            minimalButtons.add(new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("explorer.png").onClick(this::openFileExplorerScreen).hint("File Explorer").build());
        }
        if (optionsButton != null && mainMenuStyle.equals("Normal")) {
            normalButtons.clear();
            int smallButtonWidth = 50;
            int largeButtonWidth = 100;
            int gap = 5;
            int totalWidth = smallButtonWidth * 2 + largeButtonWidth + gap * 2;
            int excessWidth = totalWidth - 200;
            largeButtonWidth -= excessWidth;
            normalButtons.add(new AnimatedButton.Builder().label("Servers").onClick(this::openServerManagerScreen).size(smallButtonWidth, 18).entranceAnimation(false).build());
            normalButtons.add(new AnimatedButton.Builder().label("File Explorer").onClick(this::openFileExplorerScreen).size(largeButtonWidth, 18).entranceAnimation(false).build());
            normalButtons.add(new AnimatedButton.Builder().label("Terminal").onClick(this::openMultiTerminalScreen).size(smallButtonWidth, 18).entranceAnimation(false).build());
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        IDrawContext libCtx = new MinecraftDrawContextAdapter(context);
        if (mainMenuStyle.equals("Minimal") && optionsButton != null) {
            int spacing = 8;
            int buttonSize = 18;
            int totalWidth = minimalButtons.size() * buttonSize + spacing * (minimalButtons.size() - 1);
            int startX = ((this.width - totalWidth) / 2) - 51;
            int buttonY = optionsButton.getY() + optionsButton.getHeight() + 5;
            for (int i = 0; i < minimalButtons.size(); i++) {
                SquareButtonWidget b = minimalButtons.get(i);
                b.setPosition(startX + i * (buttonSize + spacing), buttonY);
                b.renderWidget(libCtx, mouseX, mouseY, delta);
            }
        }
        if (mainMenuStyle.equals("Normal") && optionsButton != null) {
            int buttonX = optionsButton.getX();
            int buttonY = optionsButton.getY() + optionsButton.getHeight() + 5;
            int smallButtonWidth = 50;
            int largeButtonWidth = 100;
            int gap = 5;
            int totalWidth = smallButtonWidth * 2 + largeButtonWidth + gap * 2;
            int excessWidth = totalWidth - 200;
            largeButtonWidth -= excessWidth;
            if (normalButtons.size() == 3) {
                normalButtons.get(0).setPosition(buttonX, buttonY);
                normalButtons.get(1).setPosition(buttonX + smallButtonWidth + gap, buttonY);
                normalButtons.get(2).setPosition(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY);
            }
            for (AnimatedButton btn : normalButtons) {
                btn.renderWidget(libCtx, mouseX, mouseY, delta);
            }
        }
        checkClicks(mouseX, mouseY);
    }

    @Unique
    private void checkClicks(double mouseX, double mouseY) {
        boolean mousePressed = GLFW.glfwGetMouseButton(this.client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (mousePressed && !this.wasMousePressed) {
            if (mainMenuStyle.equals("Minimal")) {
                for (SquareButtonWidget b : minimalButtons) {
                    b.mouseClicked(mouseX, mouseY, 0);
                }
            }
            if (mainMenuStyle.equals("Normal")) {
                for (AnimatedButton btn : normalButtons) {
                    btn.mouseClicked(mouseX, mouseY, 0);
                }
            }
        }
        this.wasMousePressed = mousePressed;
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
package redxax.oxy.common.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import redxax.oxy.common.RemotelyClient;
import redxax.oxy.common.Style1Button;
import redxax.oxy.common.explorer.FileExplorerScreen;
import redxax.oxy.common.servers.BrowserScreen;
import redxax.oxy.common.servers.ServerInfo;
import redxax.oxy.common.servers.ServerManagerScreen;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.common.Render.drawSquareButton;
import static redxax.oxy.common.config.Config.mainMenuStyle;
import static redxax.oxy.common.util.DevUtil.devPrint;
import static redxax.oxy.common.util.ImageUtil.drawPixelArt;
import static redxax.oxy.common.util.ImageUtil.loadResourceIcon;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private ButtonWidget optionsButton;
    private BufferedImage remotelyIcon;
    private BufferedImage fileExplorerIcon;
    private BufferedImage terminalIcon;
    private BufferedImage browserIcon;

    @Unique
    private final List<Style1Button> style1Buttons = new ArrayList<>();

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addServerManagerButton(CallbackInfo ci) {
        String optionsButtonText = I18n.translate("menu.options");
        optionsButton = this.children().stream().filter(child -> child instanceof ButtonWidget).map(child -> (ButtonWidget) child).filter(button -> button.getMessage().getString().equals(optionsButtonText)).findFirst().orElse(null);
        if (optionsButton != null && mainMenuStyle.equals("Vanilla")) {
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
            ButtonWidget serverButton = ButtonWidget.builder(Text.literal("Servers"), btn -> openServerManagerScreen()).dimensions(buttonX, buttonY, smallButtonWidth, 20).build();
            this.addDrawableChild(serverButton);
            ButtonWidget fileExplorerButton = ButtonWidget.builder(Text.literal("File Explorer"), btn -> openFileExplorerScreen()).dimensions(buttonX + smallButtonWidth + gap, buttonY, largeButtonWidth, 20).build();
            this.addDrawableChild(fileExplorerButton);
            ButtonWidget terminalButton = ButtonWidget.builder(Text.literal("Terminal"), btn -> openMultiTerminalScreen()).dimensions(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY, smallButtonWidth, 20).build();
            this.addDrawableChild(terminalButton);
        }
        if (optionsButton != null && mainMenuStyle.equals("Minimal")) {
            style1Buttons.clear();
            style1Buttons.add(new Style1Button("Servers", this::openServerManagerScreen));
            style1Buttons.add(new Style1Button("Terminal", this::openMultiTerminalScreen));
            style1Buttons.add(new Style1Button("File Explorer", this::openFileExplorerScreen));
            style1Buttons.add(new Style1Button("Internet Browser", () -> this.client.setScreen(new BrowserScreen(this.client, this, "google.com"))));
            try {
                remotelyIcon = loadResourceIcon("/assets/remotely/icons/manager.png");
                fileExplorerIcon = loadResourceIcon("/assets/remotely/icons/explorer.png");
                terminalIcon = loadResourceIcon("/assets/remotely/icons/terminal.png");
                browserIcon = loadResourceIcon("/assets/remotely/icons/minibrowser.png");
            } catch (Exception e) {
                devPrint("Failed to load TitleScreen icons: " + e.getMessage());
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (mainMenuStyle.equals("Minimal") && optionsButton != null) {
            int spacing = 8;
            int totalWidth = style1Buttons.size() * style1Buttons.get(0).size + spacing * (style1Buttons.size() - 1);
            int startX = ((this.width - totalWidth) / 2) - 51;
            int buttonY = optionsButton.getY() + optionsButton.getHeight() + 5;
            for (int i = 0; i < style1Buttons.size(); i++) {
                Style1Button b = style1Buttons.get(i);
                b.x = startX + i * (b.size + spacing);
                b.y = buttonY;
                boolean hovered = mouseX >= b.x && mouseX < b.x + b.size && mouseY >= b.y && mouseY < b.y + b.size;
                drawSquareButton(context, b.x, b.y, this.client, hovered, mouseX, mouseY, b.label, switch (b.label) { case "Servers" -> remotelyIcon; case "File Explorer" -> fileExplorerIcon; case "Terminal" -> terminalIcon; case "Internet Browser" -> browserIcon; default -> null; });
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double d, double e, int i, CallbackInfoReturnable<Boolean> cir) {
        if (mainMenuStyle.equals("Minimal")) {
            for (Style1Button b : style1Buttons) {
                if (d >= b.x && d < b.x + b.size && e >= b.y && e < b.y + b.size) {
                    b.action.run();
                    cir.cancel();
                    return;
                }
            }
        }
    }

    @Unique
    private void openServerManagerScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(new ServerManagerScreen(client, RemotelyClient.INSTANCE, RemotelyClient.INSTANCE.servers));
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

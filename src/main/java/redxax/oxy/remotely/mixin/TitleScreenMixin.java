package redxax.oxy.remotely.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import redxax.oxy.remotely.NormalButton;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.Style1Button;
import redxax.oxy.remotely.explorer.FileExplorerScreen;
import redxax.oxy.remotely.servers.BrowserScreen;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.servers.ServerManagerScreen;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import static redxax.oxy.remotely.Render.drawSquareButton;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.servers.BrowserScreen.checkIfMcefExist;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private Button optionsButton;
    private BufferedImage remotelyIcon;
    private BufferedImage fileExplorerIcon;
    private BufferedImage terminalIcon;
    private BufferedImage browserIcon;
    @Unique
    private final List<Style1Button> style1Buttons = new ArrayList<>();
    @Unique
    private final List<NormalButton> normalButtons = new ArrayList<>();

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addServerManagerButton(CallbackInfo ci) {
        String optionsButtonText = I18n.get("menu.options");
        optionsButton = this.children().stream().filter(child -> child instanceof Button).map(child -> (Button) child).filter(button -> button.getMessage().getString().equals(optionsButtonText)).findFirst().orElse(null);
        if (optionsButton != null && mainMenuStyle.equals("Vanilla")) {
            int buttonX = optionsButton.getX();
            int buttonY = optionsButton.getY() + optionsButton.getHeight() + 5;
            int smallButtonWidth = 50;
            int largeButtonWidth = 100;
            int gap = 5;
            int totalWidth = smallButtonWidth * 2 + largeButtonWidth + gap * 2;
            int excessWidth = totalWidth - 200;
            largeButtonWidth -= excessWidth;
            Button serverButton = Button.builder(Component.literal("Servers"), btn -> openServerManagerScreen()).bounds(buttonX, buttonY, smallButtonWidth, 20).build();
            this.addRenderableWidget(serverButton);
            Button fileExplorerButton = Button.builder(Component.literal("File Explorer"), btn -> openFileExplorerScreen()).bounds(buttonX + smallButtonWidth + gap, buttonY, largeButtonWidth, 20).build();
            this.addRenderableWidget(fileExplorerButton);
            Button terminalButton = Button.builder(Component.literal("Terminal"), btn -> openMultiTerminalScreen()).bounds(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY, smallButtonWidth, 20).build();
            this.addRenderableWidget(terminalButton);
        }
        if (optionsButton != null && mainMenuStyle.equals("Minimal")) {
            style1Buttons.clear();
            style1Buttons.add(new Style1Button("Servers", this::openServerManagerScreen));
            style1Buttons.add(new Style1Button("Terminal", this::openMultiTerminalScreen));
            style1Buttons.add(new Style1Button("File Explorer", this::openFileExplorerScreen));
            style1Buttons.add(new Style1Button("Internet Browser", () ->  {
                if (checkIfMcefExist())
                    this.minecraft.setScreen(new BrowserScreen(this.minecraft, this, "google.com"));
            }));
            try {
                remotelyIcon = loadResourceIcon("/assets/remotely/icons/manager.png");
                fileExplorerIcon = loadResourceIcon("/assets/remotely/icons/explorer.png");
                terminalIcon = loadResourceIcon("/assets/remotely/icons/terminal.png");
                browserIcon = loadResourceIcon("/assets/remotely/icons/minibrowser.png");
            } catch (Exception e) {
                devPrint("Failed to load TitleScreen icons: " + e.getMessage());
            }
        }
        if (optionsButton != null && mainMenuStyle.equals("Normal")) {
            normalButtons.clear();
            int smallButtonWidth = 50;
            int largeButtonWidth = 100;
            int gap = 5;
            int totalWidth = smallButtonWidth * 2 + largeButtonWidth + gap * 2;
            int excessWidth = totalWidth - 200;
            largeButtonWidth -= excessWidth;
            normalButtons.add(new NormalButton("Servers", this::openServerManagerScreen, smallButtonWidth, 20));
            normalButtons.add(new NormalButton("File Explorer", this::openFileExplorerScreen, largeButtonWidth, 20));
            normalButtons.add(new NormalButton("Terminal", this::openMultiTerminalScreen, smallButtonWidth, 20));
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
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
                drawSquareButton(context, b.x, b.y, this.minecraft, hovered, mouseX, mouseY, b.label, switch (b.label) {
                    case "Servers" -> remotelyIcon;
                    case "File Explorer" -> fileExplorerIcon;
                    case "Terminal" -> terminalIcon;
                    case "Internet Browser" -> browserIcon;
                    default -> null;
                });
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
            if(normalButtons.size() == 3) {
                normalButtons.get(0).x = buttonX;
                normalButtons.get(0).y = buttonY;
                normalButtons.get(0).width = smallButtonWidth;
                normalButtons.get(0).height = 18;
                normalButtons.get(1).x = buttonX + smallButtonWidth + gap;
                normalButtons.get(1).y = buttonY;
                normalButtons.get(1).width = largeButtonWidth;
                normalButtons.get(1).height = 18;
                normalButtons.get(2).x = buttonX + smallButtonWidth + largeButtonWidth + gap * 2;
                normalButtons.get(2).y = buttonY;
                normalButtons.get(2).width = smallButtonWidth;
                normalButtons.get(2).height = 18;
            }
            for (NormalButton btn : normalButtons) {
                boolean hovered = mouseX >= btn.x && mouseX < btn.x + btn.width && mouseY >= btn.y && mouseY < btn.y + btn.height;
                redxax.oxy.remotely.Render.drawCustomButton(context, btn.x, btn.y, btn.label, this.minecraft, hovered, false, true, false, true, btn.width, btn.height, globalTextColor, niceAccentHoverColor, mouseX, mouseY, "");
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
        if (mainMenuStyle.equals("Normal")) {
            for (NormalButton btn : normalButtons) {
                if (d >= btn.x && d < btn.x + btn.width && e >= btn.y && e < btn.y + btn.height) {
                    btn.action.run();
                    cir.cancel();
                    return;
                }
            }
        }
    }

    @Unique
    private void openServerManagerScreen() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new ServerManagerScreen(client, RemotelyClient.INSTANCE, RemotelyClient.INSTANCE.servers));
    }

    @Unique
    private void openMultiTerminalScreen() {
        Minecraft client = Minecraft.getInstance();
        RemotelyClient.INSTANCE.openMultiTerminalGUI(client);
    }

    @Unique
    private void openFileExplorerScreen() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new FileExplorerScreen(client, this, new ServerInfo(remotelyDir.toString())));
    }
}

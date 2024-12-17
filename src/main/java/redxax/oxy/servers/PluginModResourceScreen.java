package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class PluginModResourceScreen extends Screen {
    private final MinecraftClient mc;
    private final Screen parent;
    private final ModrinthResource resource;
    private final ServerInfo serverInfo;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;

    public PluginModResourceScreen(MinecraftClient mc, Screen parent, ModrinthResource resource, ServerInfo info) {
        super(Text.literal(resource.name));
        this.mc = mc;
        this.parent = parent;
        this.resource = resource;
        this.serverInfo = info;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int installButtonX = this.width - 60;
        int installButtonY = 5;
        int btnW = 50;
        int btnH = 20;
        boolean hoveredInstall = mouseX >= installButtonX && mouseX <= installButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH;
        if (hoveredInstall && button == 0) {
            installResource();
            return true;
        }
        int backButtonX = installButtonX - (btnW + 10);
        boolean hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH;
        if (hoveredBack && button == 0) {
            mc.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void installResource() {
        try {
            Path dest;
            if (serverInfo.isModServer()) {
                dest = Path.of(serverInfo.path, "mods", resource.fileName);
            } else {
                dest = Path.of(serverInfo.path, "plugins", resource.fileName);
            }
            Files.createDirectories(dest.getParent());
            Files.copy(resource.downloadUrl, dest, StandardCopyOption.REPLACE_EXISTING);
            if (serverInfo.terminal != null) {
                serverInfo.terminal.appendOutput("Installed: " + resource.name + " to " + dest + "\n");
            }
        } catch (Exception e) {
            if (serverInfo.terminal != null) {
                serverInfo.terminal.appendOutput("Install failed: " + e.getMessage() + "\n");
            }
        }
        mc.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, 30, lighterColor);
        drawInnerBorder(context, 0, 0, this.width, 30, borderColor);
        context.drawText(this.textRenderer, Text.literal(resource.name + " - " + resource.version), 10, 10, textColor, false);

        int installButtonX = this.width - 60;
        int installButtonY = 5;
        int btnW = 50;
        int btnH = 20;
        boolean hoveredInstall = mouseX >= installButtonX && mouseX <= installButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH;
        int bgInstall = hoveredInstall ? highlightColor : lighterColor;
        context.fill(installButtonX, installButtonY, installButtonX + btnW, installButtonY + btnH, bgInstall);
        drawInnerBorder(context, installButtonX, installButtonY, btnW, btnH, borderColor);
        int tw = this.textRenderer.getWidth("Install");
        int tx = installButtonX + (btnW - tw) / 2;
        int ty = installButtonY + (btnH - this.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, Text.literal("Install"), tx, ty, textColor, false);

        int backButtonX = installButtonX - (btnW + 10);
        boolean hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= installButtonY && mouseY <= installButtonY + btnH;
        int bgBack = hoveredBack ? highlightColor : lighterColor;
        context.fill(backButtonX, installButtonY, backButtonX + btnW, installButtonY + btnH, bgBack);
        drawInnerBorder(context, backButtonX, installButtonY, btnW, btnH, borderColor);
        int twb = this.textRenderer.getWidth("Back");
        int txb = backButtonX + (btnW - twb) / 2;
        context.drawText(this.textRenderer, Text.literal("Back"), txb, ty, textColor, false);

        context.drawText(this.textRenderer, Text.literal("Description: " + resource.description), 10, 40, 0xA0A0A0, false);
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }
}

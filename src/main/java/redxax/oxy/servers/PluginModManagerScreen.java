package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.gui.widget.TextFieldWidget;
import java.util.ArrayList;
import java.util.List;

public class PluginModManagerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private final List<ModrinthResource> resources = new ArrayList<>();
    private int offset = 0;
    private int entryHeight = 16;
    private boolean isLoading = false;
    private String currentSearch = "";
    private TextFieldWidget searchField;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;

    public PluginModManagerScreen(MinecraftClient mc, Screen parent, ServerInfo info) {
        super(Text.literal("Plugin / Mod Manager"));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
    }

    @Override
    protected void init() {
        super.init();
        searchField = new TextFieldWidget(this.textRenderer, 10, 35, this.width - 80, 20, Text.literal(""));
        this.addDrawableChild(searchField);
        loadResources("");
    }

    private void loadResources(String query) {
        isLoading = true;
        resources.clear();
        new Thread(() -> {
            try {
                List<ModrinthResource> results = ModrinthAPI.searchResources(query, serverInfo);
                synchronized (resources) {
                    resources.addAll(results);
                }
            } catch (Exception ignored) {}
            isLoading = false;
        }).start();
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        if (searchField.charTyped(chr, keyCode)) {
            currentSearch = searchField.getText();
            loadResources(currentSearch);
            return true;
        }
        return super.charTyped(chr, keyCode);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField.keyPressed(keyCode, scanCode, modifiers)) {
            currentSearch = searchField.getText();
            loadResources(currentSearch);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount > 0 && offset > 0) {
            offset--;
        } else if (verticalAmount < 0 && offset < resources.size() - 1) {
            offset++;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int backButtonX = this.width - 60;
        int backButtonY = 5;
        int btnW = 50;
        int btnH = 20;
        boolean hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH;
        if (hoveredBack && button == 0) {
            this.minecraftClient.setScreen(parent);
            return true;
        }
        int yStart = 60;
        int x = 10;
        for (int i = offset; i < resources.size(); i++) {
            if (yStart + entryHeight > this.height - 10) break;
            ModrinthResource r = resources.get(i);
            if (mouseY >= yStart && mouseY < yStart + entryHeight && mouseX >= x && mouseX < x + this.width - 20) {
                minecraftClient.setScreen(new PluginModResourceScreen(minecraftClient, this, r, serverInfo));
                break;
            }
            yStart += entryHeight;
        }
        if (searchField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, 30, lighterColor);
        drawInnerBorder(context, 0, 0, this.width, 30, borderColor);
        context.drawText(this.textRenderer, Text.literal("Plugin / Mod Manager"), 10, 10, textColor, false);

        int backButtonX = this.width - 60;
        int backButtonY = 5;
        int btnW = 50;
        int btnH = 20;
        boolean hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH;
        int bgBack = hoveredBack ? highlightColor : lighterColor;
        context.fill(backButtonX, backButtonY, backButtonX + btnW, backButtonY + btnH, bgBack);
        drawInnerBorder(context, backButtonX, backButtonY, btnW, btnH, borderColor);
        int txbWidth = this.textRenderer.getWidth("Back");
        int txb = backButtonX + (btnW - txbWidth) / 2;
        int txy = backButtonY + (btnH - this.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, Text.literal("Back"), txb, txy, textColor, false);

        if (isLoading) {
            context.drawText(this.textRenderer, Text.literal("Loading..."), 10, 60, 0xFF00FF, false);
            return;
        }
        int yStart = 60 + entryHeight;
        int x = 10;
        synchronized (resources) {
            for (int i = offset; i < resources.size(); i++) {
                if (yStart + entryHeight > this.height - 10) break;
                ModrinthResource r = resources.get(i);
                String displayLine = r.name + " - " + r.version;
                context.drawText(this.textRenderer, Text.literal(displayLine), x, yStart, 0xFFFFFF, false);
                yStart += entryHeight;
            }
        }
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }
}

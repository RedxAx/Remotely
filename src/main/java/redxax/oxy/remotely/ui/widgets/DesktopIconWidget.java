package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.servers.ServerInfo;

import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.globalTextColor;
import static redxax.oxy.remotely.util.ImageUtil.drawPixelArt;

public class DesktopIconWidget extends AnimatedWidget {
    private final ServerInfo serverInfo;
    private final BufferedImage icon;
    private final boolean isCreateButton;

    private BiConsumer<DesktopIconWidget, Integer> onClick;

    public static class Builder extends AnimatedWidget.Builder<DesktopIconWidget, Builder> {
        public Builder(ServerInfo serverInfo, boolean isCreateButton, BufferedImage icon) {
            super(new DesktopIconWidget(0, 0, 34, 34, serverInfo, isCreateButton, icon));
        }

        public Builder onClick(BiConsumer<DesktopIconWidget, Integer> consumer) {
            widget.onClick = consumer;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

    public DesktopIconWidget(int x, int y, int width, int height, ServerInfo serverInfo, boolean isCreateButton, BufferedImage icon) {
        super(x, y, width, height, Text.literal(isCreateButton ? "New Server" : serverInfo.name));
        this.serverInfo = serverInfo;
        this.isCreateButton = isCreateButton;
        this.icon = icon;
        this.animateLayout = true;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        int iconSize = 32;
        int iconX = getX() + (getWidth() - iconSize) / 2;
        int iconY = getY() + 1;

        drawPixelArt(ctx, iconX, iconY, iconSize, iconSize, icon);

        String name = getMessage().getString();
        String trimmed = trimTextToWidthWithEllipsis(name, getWidth() + 4);
        int textWidth = tr.getWidth(trimmed);
        int textX = getX() + (getWidth() - textWidth) / 2;
        int textY = iconY + iconSize + 4;

        ctx.drawText(tr, Text.literal(trimmed), textX, textY, globalTextColor, Config.shadow);
        if (hint.isEmpty()) {
            setHint(name);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && this.visible && this.isMouseOver(mouseX, mouseY)) {
            if (this.onClick != null) {
                this.onClick.accept(this, button);
            }
            return true;
        } else {
            return false;
        }
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public boolean isCreateButton() {
        return isCreateButton;
    }
}
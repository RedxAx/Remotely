package redxax.oxy.remotely.ui.widgets;

import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.WidgetCleanup;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.ResourceManager;

import java.util.function.BiConsumer;

import static redxax.oxy.remotely.RemotelyClient.tr;

public class DesktopIconWidget extends AnimatedWidget implements WidgetCleanup {
    private Instance serverInfo;
    private Identifier iconId;
    private boolean ownsIconId;
    private final boolean isCreateButton;

    private BiConsumer<DesktopIconWidget, Integer> onClick;

    public static class Builder extends AnimatedWidget.Builder<DesktopIconWidget, Builder> {
        public Builder(Instance serverInfo, boolean isCreateButton, Identifier iconId) {
            super(new DesktopIconWidget(0, 0, 34, 34, serverInfo, isCreateButton, iconId));
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

    public DesktopIconWidget(int x, int y, int width, int height, Instance serverInfo, boolean isCreateButton, Identifier iconId) {
        super(x, y, width, height,(isCreateButton ? "New Server" : serverInfo.getName()));
        setCursorHoverReactive(true);
        this.serverInfo = serverInfo;
        this.isCreateButton = isCreateButton;
        setIcon(iconId);
        this.animateLayout = true;
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        int iconSize = 32;
        int iconX = getX() + (getWidth() - iconSize) / 2;
        int iconY = getY() + 1;

        if (iconId != null) {
            ctx.drawPixelArt(iconId, iconX, iconY, iconSize, iconSize);
        }

        String name = getMessage();
        String trimmed = tr.trimToWidth(name, getWidth() + 4);
        if (!name.equals(trimmed)) {
            trimmed = trimmed + "..";
        }
        int textWidth = tr.getWidth(trimmed);
        int textX = getX() + (getWidth() - textWidth) / 2;
        int textY = iconY + iconSize + 4;

        Accent niceAccent = ThemeManager.getAccent("nice");
        Accent dangerAccent = ThemeManager.getAccent("danger");
        Accent defaultAccent = ThemeManager.getDefaultAccent();

        ctx.drawText((trimmed), textX, textY, textColor, Config.shadow);
        if (hint.isEmpty()) {
            setHint(name);
        }
        if (serverInfo != null) {
            if (serverInfo.getState() == InstanceState.RUNNING || serverInfo.getState() == InstanceState.STARTING) {
                accentType = niceAccent;
            } else if (serverInfo.getState() == InstanceState.CRASHED) {
                accentType = dangerAccent;
            } else {
                accentType = defaultAccent;
            }
        } else if (isCreateButton) {
            accentType = defaultAccent;
        }

        if (accentType == niceAccent) {
            ctx.drawAnimatedCornerGradient(x, y, width, height, niceAccent.getAccentColor());
        }
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        if (isMouseOver(event.x(), event.y())) {
            if (onClick != null) {
                onClick.accept(this, event.nativeButton());
            }
            return true;
        }
        return false;
    }

    public Instance getInstance() {
        return serverInfo;
    }

    public void setInstance(Instance serverInfo) {
        this.serverInfo = serverInfo;
        if (serverInfo != null && !isCreateButton) {
            setMessage(serverInfo.getName());
            setHint(serverInfo.getName());
        }
    }

    public boolean isCreateButton() {
        return isCreateButton;
    }

    public void setIcon(Identifier iconId) {
        releaseOwnedIcon();
        this.iconId = iconId;
        this.ownsIconId = false;
    }

    public void setGeneratedIcon(Identifier iconId) {
        releaseOwnedIcon();
        this.iconId = iconId;
        this.ownsIconId = iconId != null && iconId.type() == Identifier.Type.GENERATED_IMAGE;
        if (ownsIconId) {
            ResourceManager.getInstance().retainImage(iconId);
        }
    }

    public Identifier getIconId() {
        return iconId;
    }

    @Override
    public void cleanup() {
        releaseOwnedIcon();
        iconId = null;
    }

    private void releaseOwnedIcon() {
        if (ownsIconId && iconId != null) {
            ResourceManager.getInstance().releaseImage(iconId);
        }
        ownsIconId = false;
    }
}

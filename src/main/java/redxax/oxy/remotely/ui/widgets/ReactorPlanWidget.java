package redxax.oxy.remotely.ui.widgets;

import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.util.ImageUtils;

import java.awt.image.BufferedImage;

public class ReactorPlanWidget extends AnimatedWidget {
    private static final int PADDING_X = 12;
    private static final int PADDING_Y = 4;
    private static final int CONTENT_Y_OFFSET = 4;
    private static final int ICON_SIZE = 20;
    private static final int TOP_GAP = 4;
    private static final int SUBTITLE_GAP = 1;

    private final BufferedImage reactorIcon;
    private String title;
    private String subtitle;
    private String specs;
    private String price;
    private Runnable onAction;

    public ReactorPlanWidget(int x, int y, int width, int height, Runnable onSelect) {
        super(x, y, width, height, "");
        this.entranceAnimationEnabled = false;
        this.accentType = ThemeManager.getDefaultAccent();
        this.reactorIcon = ImageUtils.loadIcon("Reactor.png");
        this.onAction = onSelect;
        this.title = "Reactor Plan";
        this.subtitle = "Choose A Plan";
        this.specs = "";
        this.price = "";
    }

    public void setContent(String title, String subtitle, String specs, String price) {
        this.title = title == null || title.isBlank() ? "Reactor Plan" : title;
        this.subtitle = subtitle == null || subtitle.isBlank() ? "Choose A Plan" : subtitle;
        this.specs = specs == null ? "" : specs;
        this.price = price == null ? "" : price;
    }

    public void setOnAction(Runnable onAction) {
        this.onAction = onAction;
    }

    public int getPreferredHeight() {
        int specsHeight = ITextRenderer.fontHeight;
        return (PADDING_Y * 2) + CONTENT_Y_OFFSET + ICON_SIZE + 1 + TOP_GAP + specsHeight;
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        int primaryColor = ThemeManager.getColor(ThemeColor.textHover);
        int dimText = ThemeManager.getColor(ThemeColor.textDark);
        int accent = accentType != null ? accentType.getAccentColor() : ThemeManager.getDefaultAccent().getAccentColor();
        LayoutMetrics metrics = computeLayout();

        if (reactorIcon != null) {
            ctx.drawPixelArt(reactorIcon, metrics.iconX(), metrics.iconY(), ICON_SIZE, ICON_SIZE);
        }

        String displayTitle = title == null ? "" : title.trim();
        ctx.drawText(fitText(displayTitle, metrics.titleMaxWidth()), metrics.textLeftX(), metrics.titleY(), primaryColor, Config.shadow);

        String displaySubtitle = subtitle == null ? "" : subtitle.trim();
        ctx.drawText(displaySubtitle, metrics.textLeftX(), metrics.subtitleY(), dimText, Config.shadow);

        String rawPrice = price == null ? "" : price.trim();
        String displayPrice = fitText(rawPrice, metrics.priceMaxWidth());
        if (!displayPrice.isBlank()) {
            int priceX = metrics.priceRightX() - TextRenderer.tr.getWidth(displayPrice);
            ctx.drawText(displayPrice, priceX, metrics.priceY(), accent, Config.shadow);
        }

        ctx.fill(metrics.dividerX1(), metrics.dividerY(), metrics.dividerX2(), metrics.dividerY() + 1, borderColor);

        String displaySpecs = fitText(specs, metrics.specsMaxWidth());
        int specsX = metrics.centerX() - (TextRenderer.tr.getWidth(displaySpecs) / 2);
        ctx.drawText(displaySpecs, specsX, metrics.specsY(), dimText, Config.shadow);
    }

    private LayoutMetrics computeLayout() {
        int iconX = getX() + PADDING_X;
        int priceRightX = getX() + getWidth() - PADDING_X;
        int centerX = getX() + (getWidth() / 2);
        int textLeftX = iconX + ICON_SIZE + 8;
        int titleHeight = ITextRenderer.fontHeight;
        int topRowHeight = 20;
        int specsHeight = ITextRenderer.fontHeight;
        int totalHeight = topRowHeight + 1 + TOP_GAP + specsHeight;
        int requiredHeight = totalHeight + (PADDING_Y * 2) + CONTENT_Y_OFFSET;
        int startY = getY() + PADDING_Y + CONTENT_Y_OFFSET;
        if (requiredHeight > getHeight()) {
            startY = getY() + Math.max(0, (getHeight() - totalHeight) / 2);
        }

        int iconY = startY;
        int priceZoneWidth = Math.clamp((int) Math.round(getWidth() * 0.24), 66, 96);
        int titleMaxWidth = Math.max(42, priceRightX - textLeftX - priceZoneWidth - 18);
        int titleY = startY;
        int subtitleY = titleY + titleHeight + SUBTITLE_GAP;
        int priceY = titleY;
        int dividerY = startY + topRowHeight + 1;
        int specsY = dividerY + TOP_GAP;

        return new LayoutMetrics(centerX, iconX, iconY, textLeftX, titleY, subtitleY, priceRightX, priceY, priceZoneWidth, titleMaxWidth, dividerY, iconX, priceRightX, specsY, priceRightX - iconX);
    }

    private record LayoutMetrics(int centerX, int iconX, int iconY, int textLeftX, int titleY, int subtitleY, int priceRightX, int priceY, int priceMaxWidth, int titleMaxWidth, int dividerY, int dividerX1, int dividerX2, int specsY, int specsMaxWidth) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isActive()) {
            return false;
        }
        if (button == 0 && isMouseOver(mouseX, mouseY)) {
            if (onAction != null) {
                onAction.run();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private String fitText(String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String fitted = TextRenderer.tr.trimToWidth(text.trim(), Math.max(40, maxWidth));
        return fitted == null ? "" : fitted.trim();
    }
}

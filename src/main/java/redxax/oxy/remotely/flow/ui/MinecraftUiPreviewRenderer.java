package redxax.oxy.remotely.flow.ui;

import restudio.rescreen.game.MinecraftAssetReference;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.render.Render;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.util.ResourceManager;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class MinecraftUiPreviewRenderer {
    static final int TEXT_LINE_HEIGHT = 9;
    static final int BUTTON_HEIGHT = 20;
    private static final Map<String, BufferedImage> IMAGE_SLICES = new ConcurrentHashMap<>();

    private MinecraftUiPreviewRenderer() {
    }

    static void drawButton(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y, int width, int height, String label, boolean hovered) {
        String sprite = hovered ? "widget/button_highlighted" : "widget/button";
        if (!drawSprite(context, gameAssets, sprite, x, y, width, height)) {
            Render.drawLayeredInnerBorder(context, x, y, width, height, hovered ? 0xFF7F7F7F : 0xFF606060, 0xFF000000);
        }
        context.enableScissor(x + 4, y, x + width - 4, y + height);
        drawCenteredRichText(context, label, x, y + (height - TEXT_LINE_HEIGHT) / 2 + 1, width, 0xFFFFFFFF, true);
        context.disableScissor();
    }

    static void drawTextField(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y, int width, int height, String value) {
        if (!drawSprite(context, gameAssets, "widget/text_field", x, y, width, height)) {
            context.fill(x, y, x + width, y + height, 0xFF000000);
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF303030);
        }
        if (value != null && !value.isBlank()) {
            context.enableScissor(x + 4, y + 2, x + width - 4, y + height - 2);
            context.drawRichText(value, x + 4, y + Math.max(2, (height - TEXT_LINE_HEIGHT) / 2 + 1), 0xFFFFFFFF, true);
            context.disableScissor();
        }
    }

    static void drawCheckbox(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y, int size, boolean checked) {
        String sprite = checked ? "widget/checkbox_selected" : "widget/checkbox";
        if (!drawSprite(context, gameAssets, sprite, x, y, size, size)) {
            context.fill(x, y, x + size, y + size, 0xFF000000);
            context.fill(x + 1, y + 1, x + size - 1, y + size - 1, checked ? 0xFF55AA55 : 0xFF303030);
            if (checked) {
                context.drawText("x", x + 5, y + 4, 0xFFFFFFFF, true);
            }
        }
    }

    static void drawSlider(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y, int width, int height, float percent, String label, boolean hovered) {
        if (!drawSprite(context, gameAssets, hovered ? "widget/slider_highlighted" : "widget/slider", x, y, width, height)) {
            context.fill(x, y + height / 2 - 1, x + width, y + height / 2 + 1, 0xFF808080);
        }
        int knobWidth = 8;
        int knobX = x + Math.round((width - knobWidth) * Math.clamp(percent, 0f, 1f));
        if (!drawSprite(context, gameAssets, hovered ? "widget/slider_handle_highlighted" : "widget/slider_handle", knobX, y, knobWidth, height)) {
            drawButton(context, gameAssets, knobX, y, knobWidth, height, "", hovered);
        }
        context.enableScissor(x + 4, y, x + width - 4, y + height);
        drawCenteredRichText(context, label, x, y + (height - TEXT_LINE_HEIGHT) / 2 + 1, width, 0xFFFFFFFF, true);
        context.disableScissor();
    }

    static boolean drawSprite(IDrawContext context, MinecraftGameAssets gameAssets, String sprite, int x, int y, int width, int height) {
        MinecraftAssetReference reference = gameAssets.asset("minecraft", "textures/gui/sprites/" + sprite + ".png");
        BufferedImage image = gameAssets.getImage(reference);
        int sourceWidth = image != null && image != ResourceManager.getInstance().getMissingTexture() ? image.getWidth() : width;
        int sourceHeight = image != null && image != ResourceManager.getInstance().getMissingTexture() ? image.getHeight() : height;
        return drawAssetRegion(context, gameAssets, reference, x, y, width, height, 0, 0, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
    }

    static boolean drawAssetRegion(IDrawContext context, MinecraftGameAssets gameAssets, MinecraftAssetReference reference, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || regionWidth <= 0 || regionHeight <= 0) {
            return true;
        }
        Object nativeIdentifier = gameAssets.getNativeIdentifier(reference);
        if (nativeIdentifier != null && context.drawNativeTexture(nativeIdentifier, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight)) {
            return true;
        }
        BufferedImage image = gameAssets.getImage(reference);
        if (image == null || image == ResourceManager.getInstance().getMissingTexture()) {
            return false;
        }
        int safeU = Math.clamp(u, 0, Math.max(0, image.getWidth() - 1));
        int safeV = Math.clamp(v, 0, Math.max(0, image.getHeight() - 1));
        int safeWidth = Math.clamp(regionWidth, 1, image.getWidth() - safeU);
        int safeHeight = Math.clamp(regionHeight, 1, image.getHeight() - safeV);
        String key = reference.namespacedPath() + ":" + safeU + ":" + safeV + ":" + safeWidth + ":" + safeHeight;
        BufferedImage slice = IMAGE_SLICES.computeIfAbsent(key, ignored -> image.getSubimage(safeU, safeV, safeWidth, safeHeight));
        context.drawPixelArt(slice, x, y, width, height);
        return true;
    }

    static void drawCenteredRichText(IDrawContext context, String text, int x, int y, int width, int color, boolean shadow) {
        context.drawRichText(text, x + (width - richTextWidth(text)) / 2, y, color, shadow);
    }

    static int richTextWidth(String text) {
        return TextRenderer.getWidth(plainPreviewText(text));
    }

    static String plainPreviewText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder plain = new StringBuilder();
        boolean inTag = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inTag) {
                if (c == '>') {
                    inTag = false;
                }
                continue;
            }
            if (c == '<') {
                inTag = true;
                continue;
            }
            if ((c == '&' || c == '§') && i + 1 < text.length() && isLegacyFormatCode(text.charAt(i + 1))) {
                i++;
                continue;
            }
            plain.append(c);
        }
        return plain.toString();
    }

    private static boolean isLegacyFormatCode(char value) {
        char code = Character.toLowerCase(value);
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'f' || code >= 'k' && code <= 'o' || code == 'r' || code == 'x';
    }
}

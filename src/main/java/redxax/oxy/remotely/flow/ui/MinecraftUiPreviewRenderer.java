package redxax.oxy.remotely.flow.ui;

import restudio.rescreen.game.MinecraftAssetReference;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.render.Render;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.util.Identifier;

public final class MinecraftUiPreviewRenderer {
    static final int TEXT_LINE_HEIGHT = 9;
    static final int BUTTON_HEIGHT = 20;

    private MinecraftUiPreviewRenderer() {
    }

    static void clearImageSlices() {
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
        MinecraftGameAssets.ImageDimensions dimensions = gameAssets.getImageDimensions(reference);
        int sourceWidth = dimensions != null ? dimensions.width() : width;
        int sourceHeight = dimensions != null ? dimensions.height() : height;
        return drawAssetRegion(context, gameAssets, reference, x, y, width, height, 0, 0, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
    }

    public static boolean drawAssetRegion(IDrawContext context, MinecraftGameAssets gameAssets, MinecraftAssetReference reference, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || regionWidth <= 0 || regionHeight <= 0) {
            return true;
        }
        if (gameAssets == null) {
            return false;
        }
        Object nativeIdentifier = gameAssets.getNativeIdentifier(reference);
        if (nativeIdentifier != null && context.drawNativeTexture(nativeIdentifier, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight)) {
            return true;
        }
        Identifier imageId = gameAssets.getImageId(reference);
        if (imageId != null && u == 0 && v == 0 && regionWidth == textureWidth && regionHeight == textureHeight) {
            context.drawPixelArt(imageId, x, y, width, height);
            return true;
        }
        Identifier regionId = gameAssets.getImageRegionId(reference, u, v, regionWidth, regionHeight);
        if (regionId == null) {
            return false;
        }
        context.drawPixelArt(regionId, x, y, width, height);
        return true;
    }

    public static boolean drawImage(IDrawContext context, Identifier id, int x, int y, int width, int height) {
        if (id == null) {
            return false;
        }
        context.drawPixelArt(id, x, y, width, height);
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

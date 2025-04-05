package redxax.oxy.common.util;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static redxax.oxy.common.util.DevUtil.devPrint;

public class ImageUtil {
    static final Map<BufferedImage, Identifier> textureCache = new ConcurrentHashMap<>();

    public static class IconWithTooltip {
        private BufferedImage image;
        private final String tooltip;

        public IconWithTooltip(String imagePath, String tooltip) {
            try {
                this.image = loadResourceIcon(imagePath);
            } catch (Exception e) {
                devPrint("Failed to load icon: " + e.getMessage());
            }
            this.tooltip = tooltip;
        }

        public BufferedImage getImage() {
            return image;
        }

        public String getTooltip() {
            return tooltip;
        }
    }

    public static BufferedImage loadResourceIcon(String path) throws Exception {
        InputStream tmp = ImageUtil.class.getResourceAsStream(path);
        final InputStream is = tmp != null ? tmp : ImageUtil.class.getResourceAsStream("assets/remotely/icons/missing.png");
        try (is) {
            BufferedImage original = ImageIO.read(is);
            BufferedImage scaled = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaled.createGraphics();
            g2d.drawImage(original, 0, 0, 40, 40, null);
            g2d.dispose();
            return scaled;
        } catch (Exception e) {
            throw new Exception("Failed to load icon: " + e.getMessage());
        }
    }

    public static BufferedImage loadSpriteSheet(String path) throws Exception {
        try (InputStream is = ImageUtil.class.getResourceAsStream(path)) {
            if (is == null) throw new Exception("Resource not found: " + path);
            return ImageIO.read(is);
        }
    }

    public static void drawBufferedImage(DrawContext context, BufferedImage image, int x, int y, int width, int height) {
        Identifier textureId = textureCache.get(image);
        if (textureId == null) {
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            NativeImage nativeImage = new NativeImage(imgWidth, imgHeight, true);
            for (int i = 0; i < imgWidth; i++) {
                for (int j = 0; j < imgHeight; j++) {
                    int argb = image.getRGB(i, j);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                    nativeImage.setColor(i, j, abgr);
                }
            }
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            textureId = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("image_" + image.hashCode(), texture);
            textureCache.put(image, textureId);
        }
        context.drawTexture(textureId, x, y, 0, 0, width, height, width, height);
    }

    public static void drawPixelArt(DrawContext context, int x, int y, int width, int height, BufferedImage image) {
        Identifier textureId = textureCache.get(image);
        if (textureId == null) {
            BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaledImage.createGraphics();
            g2d.drawImage(image, 0, 0, width, height, null);
            g2d.dispose();
            NativeImage nativeImage = new NativeImage(width, height, true);
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    int argb = scaledImage.getRGB(i, j);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                    nativeImage.setColor(i, j, abgr);
                }
            }
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            textureId = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("image_" + image.hashCode(), texture);
            textureCache.put(image, textureId);
        }
        context.drawTexture(textureId, x, y, 0, 0, width, height, width, height);
    }

    public static void drawBrowser(MCEFBrowser currentBrowser, boolean fullscreen, int width, int height, int TOP_OFFSET, int BROWSER_DRAW_OFFSET) {
        if (fullscreen) {
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, currentBrowser.getRenderer().getTextureID());
            Tessellator t = Tessellator.getInstance();
            BufferBuilder buffer = t.getBuffer();
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            buffer.vertex(0, height, 0).texture(0.0f, 1.0f).color(255,255,255,255).next();
            buffer.vertex(width, height, 0).texture(1.0f, 1.0f).color(255,255,255,255).next();
            buffer.vertex(width, 0, 0).texture(1.0f, 0.0f).color(255,255,255,255).next();
            buffer.vertex(0, 0, 0).texture(0.0f, 0.0f).color(255,255,255,255).next();
            t.draw();
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, currentBrowser.getRenderer().getTextureID());
            Tessellator t = Tessellator.getInstance();
            BufferBuilder buffer = t.getBuffer();
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            buffer.vertex(BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0).texture(0.0f, 1.0f).color(255, 255, 255, 255).next();
            buffer.vertex(width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0).texture(1.0f, 1.0f).color(255, 255, 255, 255).next();
            buffer.vertex(width - BROWSER_DRAW_OFFSET, TOP_OFFSET, 0).texture(1.0f, 0.0f).color(255, 255, 255, 255).next();
            buffer.vertex(BROWSER_DRAW_OFFSET, TOP_OFFSET, 0).texture(0.0f, 0.0f).color(255, 255, 255, 255).next();
            t.draw();
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableDepthTest();
        }
    }
}


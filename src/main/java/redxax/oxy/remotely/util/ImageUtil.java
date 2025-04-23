package redxax.oxy.remotely.util;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
//? if !=1.20.1
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class ImageUtil {
    static final Map<BufferedImage, ResourceLocation> textureCache = new ConcurrentHashMap<>();

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
            if (is == null)
                throw new Exception("Resource not found: " + path);
            return ImageIO.read(is);
        }
    }

    public static void drawBufferedImage(GuiGraphics context, BufferedImage image, int x, int y, int width, int height) {
        //? if <=1.21.1 {
        /*ResourceLocation textureId = textureCache.get(image);
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
                    nativeImage.setPixelRGBA(i, j, abgr);
                }
            }
            DynamicTexture texture = new DynamicTexture(nativeImage);
            textureId = Minecraft.getInstance().getTextureManager()
                    .register("image_" + image.hashCode(), texture);
            textureCache.put(image, textureId);
        }
        context.blit(textureId, x, y, 0, 0, width, height, width, height);
        *///?} elif <=1.21.5 {
        ResourceLocation textureId = textureCache.get(image);
        if (textureId == null) {
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            NativeImage nativeImage = new NativeImage(imgWidth, imgHeight, true);
            for (int i = 0; i < imgWidth; i++) {
                for (int j = 0; j < imgHeight; j++) {
                    int argb = image.getRGB(i, j);
                    nativeImage.setPixel(i, j, argb);
                }
            }
            //? if =1.21.5 {
            /*Supplier<String> textureName = () -> "redxax.oxy:image_" + image.hashCode();
            DynamicTexture texture = new DynamicTexture(textureName, nativeImage);
            *///?} else {
             DynamicTexture texture = new DynamicTexture(nativeImage);
            //?}
            textureId = ResourceLocation.tryParse("redxax.oxy:image_" + image.hashCode());
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
            textureCache.put(image, textureId);
        }
        context.blit(RenderType::guiTextured, textureId, x, y, 0F, 0F,
                width, height, width, height);
        //?}
    }

    public static void drawPixelArt(GuiGraphics context, int x, int y, int width, int height, BufferedImage image) {
        //? if <=1.21.1 {
        /*ResourceLocation textureId = textureCache.get(image);
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
                    nativeImage.setPixelRGBA(i, j, abgr);
                }
            }
            DynamicTexture texture = new DynamicTexture(nativeImage);
            textureId = Minecraft.getInstance().getTextureManager().register("image_" + image.hashCode(), texture);
            textureCache.put(image, textureId);
        }
        context.blit(textureId, x, y, 0, 0, width, height, width, height);
        *///?} elif <=1.21.4 {
        ResourceLocation textureId = textureCache.get(image);
        if (textureId == null) {
            BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaledImage.createGraphics();
            g2d.drawImage(image, 0, 0, width, height, null);
            g2d.dispose();
            NativeImage nativeImage = new NativeImage(width, height, true);
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    int argb = scaledImage.getRGB(i, j);
                    nativeImage.setPixel(i, j, argb);
                }
            }
            DynamicTexture texture = new DynamicTexture(nativeImage);
            textureId = ResourceLocation.tryParse("redxax.oxy:image_" + image.hashCode());
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
            textureCache.put(image, textureId);
        }
        context.blit(RenderType::guiTextured, textureId, x, y, 0F, 0F, width, height, width, height);
        //?}
    }

    public static void warpedDrawGuiTexture(GuiGraphics context, int x, int y, ResourceLocation icon, int iconWidth, int iconHeight) {
        //? if <=1.21.1 && !=1.20.1 {
        /*context.blitSprite(icon, x, y, iconWidth, iconHeight);
         *///?} elif >1.21.1 {
        context.blitSprite(RenderType::guiTextured, icon, x, y, iconWidth, iconHeight);
        //?}
    }

    public static void drawBrowser(MCEFBrowser currentBrowser, boolean fullscreen,
                                   int width, int height, int TOP_OFFSET, int BROWSER_DRAW_OFFSET) {
        //? if <=1.21.1 {
        /*if (fullscreen) {
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0,
                    currentBrowser.getRenderer().getTextureID());
            Tesselator t = Tesselator.getInstance();
            BufferBuilder buffer = t.getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(0, height, 0).uv(0.0f, 1.0f).color(255, 255, 255, 255).endVertex();
            buffer.vertex(width, height, 0).uv(1.0f, 1.0f).color(255, 255, 255, 255).endVertex();
            buffer.vertex(width, 0, 0).uv(1.0f, 0.0f).color(255, 255, 255, 255).endVertex();
            buffer.vertex(0, 0, 0).uv(0.0f, 0.0f).color(255, 255, 255, 255).endVertex();
            t.end();
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, currentBrowser.getRenderer().getTextureID());
            Tesselator t = Tesselator.getInstance();
            BufferBuilder buffer = t.getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0).uv(0.0f, 1.0f).color(255, 255, 255, 255).endVertex();
            buffer.vertex(width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0).uv(1.0f, 1.0f).color(255, 255, 255, 255).endVertex();
            buffer.vertex(width - BROWSER_DRAW_OFFSET, TOP_OFFSET, 0).uv(1.0f, 0.0f).color(255, 255, 255, 255).endVertex();
            buffer.vertex(BROWSER_DRAW_OFFSET, TOP_OFFSET, 0).uv(0.0f, 0.0f).color(255, 255, 255, 255).endVertex();
            t.end();
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableDepthTest();
        }
        *///?} elif <=1.21.4 {
        if (fullscreen) {
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, currentBrowser.getRenderer().getTextureID());
            Tesselator t = Tesselator.getInstance();
            BufferBuilder buffer = t.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.addVertex(0, height, 0).setUv(0.0f, 1.0f).setColor(255, 255, 255, 255);
            buffer.addVertex(width, height, 0).setUv(1.0f, 1.0f).setColor(255, 255, 255, 255);
            buffer.addVertex(width, 0, 0).setUv(1.0f, 0.0f).setColor(255, 255, 255, 255);
            buffer.addVertex(0, 0, 0).setUv(0.0f, 0.0f).setColor(255, 255, 255, 255);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, currentBrowser.getRenderer().getTextureID());
            Tesselator t = Tesselator.getInstance();
            BufferBuilder buffer = t.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.addVertex(BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0).setUv(0.0f, 1.0f).setColor(255, 255, 255, 255);
            buffer.addVertex(width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0).setUv(1.0f, 1.0f).setColor(255, 255, 255, 255);
            buffer.addVertex(width - BROWSER_DRAW_OFFSET, TOP_OFFSET, 0).setUv(1.0f, 0.0f).setColor(255, 255, 255, 255);
            buffer.addVertex(BROWSER_DRAW_OFFSET, TOP_OFFSET, 0).setUv(0.0f, 0.0f).setColor(255, 255, 255, 255);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableDepthTest();
        }
        //?}
    }
}

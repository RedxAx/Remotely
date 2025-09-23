package redxax.oxy.remotely.util;

import com.mojang.blaze3d.systems.RenderSystem;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
//? if >1.21.1 && <1.21.5
/*import net.minecraft.client.gl.ShaderProgramKeys;*/
//? if >=1.21.6
/*import net.minecraft.client.gl.RenderPipelines;*/
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static redxax.oxy.remotely.util.DevUtil.devPrint;

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

    public static BufferedImage loadResourceIcon(String path) {
        return restudio.rescreen.util.ImageUtils.loadResourceIcon(path);
    }

    public static BufferedImage loadSpriteSheet(String path) throws Exception {
        try (InputStream is = ImageUtil.class.getResourceAsStream(path)) {
            if (is == null)
                throw new Exception("Resource not found: " + path);
            return ImageIO.read(is);
        }
    }

    public static void drawBufferedImage(DrawContext context, BufferedImage image, int x, int y, int width, int height) {
        //? if <=1.21.1 {
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
        //?} elif <=1.21.5 {
        /*Identifier textureId = textureCache.get(image);
        if (textureId == null) {
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            NativeImage nativeImage = new NativeImage(imgWidth, imgHeight, true);
            for (int i = 0; i < imgWidth; i++) {
                for (int j = 0; j < imgHeight; j++) {
                    int argb = image.getRGB(i, j);
                    nativeImage.setColorArgb(i, j, argb);
                }
            }
            //? if =1.21.5 {
            /^Supplier<String> textureName = () -> "redxax.oxy:image_" + image.hashCode();
            NativeImageBackedTexture texture = new NativeImageBackedTexture(textureName, nativeImage);
            ^///?} else {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            //?}
            textureId = Identifier.tryParse("redxax.oxy:image_" + image.hashCode());
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
            textureCache.put(image, textureId);
        }
        context.drawTexture(RenderLayer::getGuiTextured, textureId, x, y, 0F, 0F, width, height, width, height);
        *///?}
    }

    public static void drawPixelArt(DrawContext context, int x, int y, int width, int height, BufferedImage image) {
        //? if <=1.21.1 {
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
        //?} elif >=1.21.4 {
        /*Identifier textureId = textureCache.get(image);
        if (textureId == null) {
            BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaledImage.createGraphics();
            g2d.drawImage(image, 0, 0, width, height, null);
            g2d.dispose();
            NativeImage nativeImage = new NativeImage(width, height, true);
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    int argb = scaledImage.getRGB(i, j);
                    nativeImage.setColorArgb(i, j, argb);
                }
            }
            //? if >=1.21.5 {
            /^Supplier<String> textureName = () -> "redxax.oxy:image_" + image.hashCode();
            NativeImageBackedTexture texture = new NativeImageBackedTexture(textureName, nativeImage);
            textureId = Identifier.tryParse("redxax.oxy:image_" + image.hashCode());
            ^///?} else {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            textureId = Identifier.tryParse("redxax.oxy:image_" + image.hashCode());
            //?}
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
            textureCache.put(image, textureId);
        }
        //? if >=1.21.6 {
        /^context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, textureId, x, y, 0, 0, width, height, width, height);
        ^///?} else {
        context.drawTexture(RenderPipeline::getGuiTextured, textureId, x, y, 0F, 0F, width, height, width, height);
        //?}
        *///?}
    }
}
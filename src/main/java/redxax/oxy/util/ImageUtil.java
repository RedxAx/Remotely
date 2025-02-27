package redxax.oxy.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImageUtil {
    static final Map<BufferedImage, Identifier> textureCache = new ConcurrentHashMap<>();

    public static BufferedImage loadResourceIcon(String path) throws Exception {
        try (InputStream is = ImageUtil.class.getResourceAsStream(path)) {
            if (is == null) throw new Exception("Resource not found: " + path);
            BufferedImage original = ImageIO.read(is);
            BufferedImage scaled = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaled.createGraphics();
            g2d.drawImage(original, 0, 0, 40, 40, null);
            g2d.dispose();
            return scaled;
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
}

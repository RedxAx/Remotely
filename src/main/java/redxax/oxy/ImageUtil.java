package redxax.oxy;

import net.minecraft.client.gui.DrawContext;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImageUtil {

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
        final Map<BufferedImage, BufferedImage> scaledCache = new ConcurrentHashMap<>();
        BufferedImage scaledImage = scaledCache.get(image);
        if (scaledImage == null) {
            scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaledImage.createGraphics();
            g2d.drawImage(image, 0, 0, width, height, null);
            g2d.dispose();
            scaledCache.put(image, scaledImage);
        }
        int[] pixels = scaledImage.getRGB(0, 0, width, height, null, 0, width);
        for (int py = 0; py < height; py++) {
            int rowStart = py * width;
            int lastCol = -1;
            int lastColor = 0;
            for (int px = 0; px < width; px++) {
                int color = pixels[rowStart + px];
                if (px == 0) {
                    lastCol = 0;
                    lastColor = color;
                } else if (color != lastColor) {
                    if ((lastColor >>> 24) != 0) {
                        context.fill(x + lastCol, y + py, x + px, y + py + 1, lastColor);
                    }
                    lastCol = px;
                    lastColor = color;
                }
            }
            if ((lastColor >>> 24) != 0) {
                context.fill(x + lastCol, y + py, x + width, y + py + 1, lastColor);
            }
        }
    }
}

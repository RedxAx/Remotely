package redxax.oxy.remotely.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class ImageUtil {

    public static BufferedImage loadResourceIcon(String path) {
        return restudio.rescreen.util.ImageUtils.loadIcon(path);
    }

    public static BufferedImage loadSpriteSheet(String path) throws Exception {
        try (InputStream is = ImageUtil.class.getResourceAsStream(path)) {
            if (is == null) throw new Exception("Resource not found: " + path);
            return ImageIO.read(is);
        }
    }
}

package redxax.oxy.remotely.packcontent;

import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.ResourceManager;

import java.awt.image.BufferedImage;

final class DesktopGlyphPreviewFrame {
    private DesktopGlyphPreviewFrame() {
    }

    static GlyphPreviewFrame of(BufferedImage image, int delayMs) {
        if (image == null) {
            return null;
        }
        Identifier id = ResourceManager.getInstance().registerImage(image);
        return new GlyphPreviewFrame(id, image.getWidth(), image.getHeight(), delayMs);
    }
}

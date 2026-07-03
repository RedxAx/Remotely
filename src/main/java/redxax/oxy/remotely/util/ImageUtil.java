package redxax.oxy.remotely.util;

import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.ImageUtils;

public class ImageUtil {

    public static Identifier loadResourceIcon(String path) {
        return ImageUtils.loadIconId(path);
    }
}

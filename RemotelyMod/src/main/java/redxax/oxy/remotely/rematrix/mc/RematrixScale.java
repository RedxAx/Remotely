package redxax.oxy.remotely.rematrix.mc;

import net.minecraft.client.Minecraft;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.ScreenManager;

public final class RematrixScale {
    private static boolean initialized;

    private RematrixScale() {
    }

    public static void ensureConfigured(Minecraft minecraft) {
        if (initialized) {
            return;
        }
        float scale = Config.configManager != null
            ? Config.configManager.getGuiScale()
            : (float) minecraft.getWindow().getGuiScale();
        ScreenManager.getInstance().setGuiScale(scale);
        Config.animScaleFactor = scale;
        Config.targetScaleFactor = scale;
        initialized = Config.configManager != null;
    }
}

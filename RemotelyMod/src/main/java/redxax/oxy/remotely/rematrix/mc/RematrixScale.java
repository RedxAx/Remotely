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

    public static double managerInputScale(Minecraft minecraft) {
        ensureConfigured(minecraft);
        return minecraft.getWindow().getGuiScale();
    }

    public static double renderInputScale(Minecraft minecraft) {
        ensureConfigured(minecraft);
        double mcScale = minecraft.getWindow().getGuiScale();
        float reScale = ScreenManager.getInstance().getGuiScale();
        if (reScale == 0) {
            return 1.0;
        }
        return mcScale / reScale;
    }
}

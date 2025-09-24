package redxax.oxy.remotely.host;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import redxax.oxy.remotely.adapters.MinecraftTextRendererAdapter;
import redxax.oxy.remotely.adapters.ReScreenWrapper;
import restudio.rescreen.ui.core.Screen;

public class MinecraftApplicationHost implements ApplicationHost {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void setScreen(Screen screen) {
        if (screen == null) {
            mc.setScreen(null);
        } else {
            mc.setScreen(new ReScreenWrapper(screen));
        }
    }

    @Override
    public Screen getCurrentScreen() {
        net.minecraft.client.gui.screen.Screen current = mc.currentScreen;
        if (current instanceof ReScreenWrapper) {
            return ((ReScreenWrapper) current).getScreen();
        }
        return null;
    }

    @Override
    public void ensureTextRenderer() {
        if (redxax.oxy.remotely.RemotelyClient.tr != null) return;
        restudio.rescreen.render.TextRenderer.setTextRendererAdapter(new MinecraftTextRendererAdapter());
        redxax.oxy.remotely.RemotelyClient.tr = restudio.rescreen.render.TextRenderer.getTr();
    }

    @Override
    public Object getFontIdentifier(String namespace, String path) {
        return Identifier.of(namespace, path);
    }

    @Override
    public void openParentScreen(Screen currentScreen, Object parent) {
        if (parent instanceof net.minecraft.client.gui.screen.Screen) {
            mc.setScreen((net.minecraft.client.gui.screen.Screen) parent);
        } else if (parent instanceof Screen) {
            setScreen((Screen) parent);
        } else {
            setScreen(null);
        }
    }

    @Override
    public String getGameVersion() {
        return MinecraftClient.getInstance().getGameVersion();
    }

    @Override
    public void setClipboard(String text) {
        MinecraftClient.getInstance().keyboard.setClipboard(text);
    }
}
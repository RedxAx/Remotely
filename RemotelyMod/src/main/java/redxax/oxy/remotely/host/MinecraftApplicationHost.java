package redxax.oxy.remotely.host;

import dev.deftu.omnicore.api.OmniResourceLocation;
import dev.deftu.omnicore.api.client.screen.OmniScreens;
import net.minecraft.client.Minecraft;
import redxax.oxy.remotely.adapters.MinecraftTextRendererAdapter;
import redxax.oxy.remotely.adapters.ReScreenWrapper;
import restudio.rescreen.platform.ClipboardHandler;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;

public class MinecraftApplicationHost implements ApplicationHost {
    private final Minecraft mc = Minecraft.getInstance();

    public MinecraftApplicationHost() {
        ScreenManager.getInstance().setClipboardHandler(new ClipboardHandler() {
            @Override
            public void setClipboard(String text) {
                mc.keyboardHandler.setClipboard(text);
            }

            @Override
            public String getClipboard() {
                return mc.keyboardHandler.getClipboard();
            }
        });
    }

    @Override
    public void setScreen(Screen screen) {
        if (screen == null) {
            mc.setScreen(null);
            return;
        }
        OmniScreens.setCurrentScreen(new ReScreenWrapper(screen));
    }

    @Override
    public Screen getCurrentScreen() {
        var omni = OmniScreens.getCurrentScreen();
        if (omni instanceof ReScreenWrapper wrapper) {
            return wrapper.getScreen();
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
        return OmniResourceLocation.createOrThrow(namespace, path);
    }

    @Override
    public void openParentScreen(Screen currentScreen, Object parent) {
        if (parent instanceof net.minecraft.client.gui.screens.Screen) {
            mc.setScreen((net.minecraft.client.gui.screens.Screen) parent);
        } else if (parent instanceof Screen) {
            setScreen((Screen) parent);
        } else {
            setScreen(null);
        }
    }

    @Override
    public String getGameVersion() {
        return Minecraft.getInstance().getLaunchedVersion();
    }

    @Override
    public void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    @Override
    public String getGameUserName() {
        return mc.getUser().getName();
    }

    @Override
    public String getGameUUID() {
        return mc.getUser().getProfileId().toString();
    }
}

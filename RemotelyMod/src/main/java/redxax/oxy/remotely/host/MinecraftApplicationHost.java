package redxax.oxy.remotely.host;

import net.minecraft.client.Minecraft;
//#if MC >= 1.21.11 || MC >= 26.1
import net.minecraft.resources.Identifier;
//#endif
//#if MC < 1.21.11 && MC < 26.1
//$$ import net.minecraft.resources.ResourceLocation;
//#endif
import redxax.oxy.remotely.adapters.MinecraftTextRendererAdapter;
import redxax.oxy.remotely.adapters.ReScreenWrapper;
import redxax.oxy.remotely.rematrix.mc.RematrixScreen;
import restudio.rescreen.config.Config;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.platform.ClipboardHandler;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;

import java.lang.reflect.Field;

public class MinecraftApplicationHost implements ApplicationHost {
    private final Minecraft mc = Minecraft.getInstance();
    private final MinecraftGameAssets gameAssets = new MinecraftNativeGameAssets();

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
        //#if MC >= 26.2
        //$$ if (screen == null) {
        //$$     RematrixScreen.closeExplicitly();
        //$$     mc.gui.setScreen(null);
        //$$     return;
        //$$ }
        //$$ if (Config.desktopMode) {
        //$$     ScreenManager sm = ScreenManager.getInstance();
        //$$     long handle = Minecraft.getInstance().getWindow().handle();
        //$$     sm.setWindowHandle(handle);
        //$$     try {
        //$$         Field f = restudio.rescreen.Main.class.getDeclaredField("window");
        //$$         f.setAccessible(true);
        //$$         f.setLong(null, handle);
        //$$     } catch (Throwable ignored) {}
        //$$     if (mc.gui.screen() instanceof ReScreenWrapper) {
        //$$         sm.setScreen(screen);
        //$$         return;
        //$$     }
        //$$ }
        //$$ RematrixScreen.rememberMinecraftScreen(null);
        //$$ mc.gui.setScreen(new ReScreenWrapper(screen));
        //#else
        if (screen == null) {
            RematrixScreen.closeExplicitly();
            mc.setScreen(null);
            return;
        }
        if (Config.desktopMode) {
            ScreenManager sm = ScreenManager.getInstance();
            //#if NEOFORGE && MC < 1.21.10
            //$$ long handle = Minecraft.getInstance().getWindow().getWindow();
            //#else
            long handle = Minecraft.getInstance().getWindow().handle();
            //#endif
            sm.setWindowHandle(handle);
            try {
                Field f = restudio.rescreen.Main.class.getDeclaredField("window");
                f.setAccessible(true);
                f.setLong(null, handle);
            } catch (Throwable ignored) {}
            if (mc.screen instanceof ReScreenWrapper) {
                sm.setScreen(screen);
                return;
            }
        }
        RematrixScreen.rememberMinecraftScreen(null);
        mc.setScreen(new ReScreenWrapper(screen));
        //#endif
    }

    @Override
    public Screen getCurrentScreen() {
        //#if MC >= 26.2
        //$$ if (mc.gui.screen() instanceof ReScreenWrapper wrapper) {
        //$$     return wrapper.getScreen();
        //$$ }
        //$$ return null;
        //#else
        if (mc.screen instanceof ReScreenWrapper wrapper) {
            return wrapper.getScreen();
        }
        return null;
        //#endif
    }

    @Override
    public void ensureTextRenderer() {
        if (redxax.oxy.remotely.RemotelyClient.tr != null) return;
        restudio.rescreen.render.TextRenderer.setTextRendererAdapter(new MinecraftTextRendererAdapter());
        redxax.oxy.remotely.RemotelyClient.tr = restudio.rescreen.render.TextRenderer.getTr();
    }

    @Override
    public MinecraftGameAssets getGameAssets() {
        return gameAssets;
    }

    @Override
    public Object getFontIdentifier(String namespace, String path) {
        String resolvedNamespace = (namespace == null || namespace.isBlank()) ? "minecraft" : namespace;
        String resolvedPath = path == null ? "" : path;
        //#if MC >= 1.21.11 || MC >= 26.1
        return Identifier.fromNamespaceAndPath(resolvedNamespace, resolvedPath);
        //#endif
        //#if MC < 1.21.11 && MC >= 1.21.1
        //$$ return ResourceLocation.fromNamespaceAndPath(resolvedNamespace, resolvedPath);
        //#endif
        //#if MC < 1.21.1
        //$$ return new ResourceLocation(resolvedNamespace, resolvedPath);
        //#endif
    }

    @Override
    public void openParentScreen(Screen currentScreen, Object parent) {
        //#if MC >= 26.2
        //$$ if (parent instanceof net.minecraft.client.gui.screens.Screen) {
        //$$     RematrixScreen.closeExplicitly();
        //$$     mc.gui.setScreen(RematrixScreen.consumeRememberedMinecraftScreen((net.minecraft.client.gui.screens.Screen) parent));
        //$$ } else if (parent instanceof Screen) {
        //$$     setScreen((Screen) parent);
        //$$ } else {
        //$$     RematrixScreen.closeExplicitly();
        //$$     mc.gui.setScreen(RematrixScreen.consumeRememberedMinecraftScreen(null));
        //$$ }
        //#else
        if (parent instanceof net.minecraft.client.gui.screens.Screen) {
            RematrixScreen.closeExplicitly();
            mc.setScreen(RematrixScreen.consumeRememberedMinecraftScreen((net.minecraft.client.gui.screens.Screen) parent));
        } else if (parent instanceof Screen) {
            setScreen((Screen) parent);
        } else {
            RematrixScreen.closeExplicitly();
            mc.setScreen(RematrixScreen.consumeRememberedMinecraftScreen(null));
        }
        //#endif
    }

    @Override
    public boolean shouldCloseRootScreen() {
        return true;
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

package redxax.oxy.remotely;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.settings.server.ServerSettingsRegistry;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.ui.core.Screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopRemotelyCompositionTest {
    @Test
    void installsTypedDesktopSettingsStorage() {
        ServerSettingsRegistry registry = ServerSettingsRegistry.getInstance();
        registry.close();
        RemotelyComposition composition = DesktopRemotelyComposition.create(new TestHost()).build();

        assertEquals(RemotelyComposition.Environment.DESKTOP, composition.environment());
        assertTrue(registry.packs().stream().anyMatch(pack -> pack.id().equals("minecraft-server-properties")));
        registry.close();
    }

    @Test
    void rejectsCompositionWithoutSettingsStorage() {
        assertThrows(IllegalStateException.class, () -> RemotelyComposition.builder(new TestHost()).build());
    }

    private static final class TestHost implements ApplicationHost {
        private Screen screen;

        @Override public void setScreen(Screen screen) { this.screen = screen; }
        @Override public Screen getCurrentScreen() { return screen; }
        @Override public void ensureTextRenderer() { }
        @Override public MinecraftGameAssets getGameAssets() { return null; }
        @Override public Object getFontIdentifier(String namespace, String path) { return null; }
        @Override public void openParentScreen(Screen currentScreen, Object parent) { }
        @Override public void setClipboard(String text) { }
        @Override public boolean shouldCloseRootScreen() { return false; }
        @Override public String getGameVersion() { return ""; }
        @Override public String getGameUserName() { return ""; }
        @Override public String getGameUUID() { return ""; }
    }
}

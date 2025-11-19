package redxax.oxy.remotely.util;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.RemotelyInit;
import redxax.oxy.remotely.host.MinecraftApplicationHost;
import restudio.rescreen.ui.MouseCursor;

public class InitializationManager {
    private static boolean initialized = false;

    public static void ensureInitialized() {
        if (!initialized) {
            RemotelyInit.initCommon();
            if (RemotelyClient.INSTANCE == null) {
                RemotelyInit.initClient(new MinecraftApplicationHost());
            }
            initialized = true;
        }
        MouseCursor.reset(false);
        if (RemotelyClient.INSTANCE != null) {
            RemotelyClient.INSTANCE.getHost().ensureTextRenderer();
        }
    }
}

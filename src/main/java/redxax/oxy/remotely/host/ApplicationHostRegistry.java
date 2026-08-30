package redxax.oxy.remotely.host;

import restudio.rescreen.game.MinecraftGameAssets;

public final class ApplicationHostRegistry {
    private static volatile ApplicationHost current;

    private ApplicationHostRegistry() {
    }

    public static void install(ApplicationHost host) {
        current = host;
    }

    public static ApplicationHost current() {
        return current;
    }

    public static MinecraftGameAssets gameAssets() {
        ApplicationHost host = current;
        if (host == null) {
            return MinecraftGameAssets.EMPTY;
        }
        MinecraftGameAssets assets = host.getGameAssets();
        return assets == null ? MinecraftGameAssets.EMPTY : assets;
    }
}

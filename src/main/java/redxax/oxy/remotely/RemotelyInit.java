package redxax.oxy.remotely;

import redxax.oxy.remotely.host.ApplicationHost;
import restudio.rebase.Rebase;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.util.RebaseLogger;
import restudio.rescreen.Main;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInit
{
    public static ModPlatform PLATFORM = null;

    public static void initCommon(ModPlatform platform) {
        RemotelyInit.PLATFORM = platform;

        InstanceManager.initialize(remotelyDir);

        RemotelyManager remotelyManager = new RemotelyManager();
        Rebase.initialize(remotelyManager);
        RebaseLogger.setLogger(remotelyManager::log);
    }

    public static void initClient(ApplicationHost host) {
        new RemotelyClient(host).initialize();
    }

    public static void entrypoint(ModPlatform platform, ApplicationHost host) {
        initCommon(platform);
        initClient(host);
    }

    public static void main(String[] args) {
        Main.setEntryClass(RemotelyEntry.class);
        initCommon(new ModPlatform() {
            @Override
            public String getModloader() {
                return "standalone";
            }
            @Override
            public boolean isModLoaded(String modloader) {
                return true;
            }
        });
        Main.main(args);
    }
}
package redxax.oxy.remotely;

import restudio.rebase.Rebase;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.util.RebaseLogger;
import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInit
{
    public static ModPlatform PLATFORM = null;

    public static void entrypoint(ModPlatform platform) {
        RemotelyInit.PLATFORM = platform;

        InstanceManager.initialize(remotelyDir);

        RemotelyManager remotelyManager = new RemotelyManager();
        Rebase.initialize(remotelyManager);
        RebaseLogger.setLogger(remotelyManager::log);

        RemotelyClient remotelyClient = new RemotelyClient();
        remotelyClient.initialize();
    }
}
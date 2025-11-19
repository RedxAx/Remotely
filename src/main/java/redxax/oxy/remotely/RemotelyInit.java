package redxax.oxy.remotely;

import redxax.oxy.remotely.host.ApplicationHost;
import restudio.rebase.Rebase;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.util.RebaseLogger;
import restudio.rescreen.Main;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInit {
    public static void initCommon() {
        InstanceManager.initialize(remotelyDir);

        RemotelyManager remotelyManager = new RemotelyManager();
        Rebase.initialize(remotelyManager);
        RebaseLogger.setLogger(remotelyManager::log);
    }

    public static void initClient(ApplicationHost host) {
        new RemotelyClient(host).initialize();
    }

    public static void main(String[] args) {
        Main.setEntryClass(RemotelyEntry.class);
        initCommon();
        Main.main(args);
    }
}

package redxax.oxy.remotely;

import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rebase.Rebase;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.util.RebaseLogger;
import restudio.rescreen.Main;
import restudio.rescreen.config.Config;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyInit {
    public static void initCommon() {
        try {
            if (Rebase.get() != null) return;
        } catch (IllegalStateException ignored) {
            System.out.println("Rebase already initialized, skipping...");
        }

        InstanceManager.initialize(remotelyDir);

        Config.applicationDir = remotelyDir;
        Config.setConfigManager(new RemotelyConfigManager(remotelyDir));

        ReStudio.getInstance().init(remotelyDir);

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

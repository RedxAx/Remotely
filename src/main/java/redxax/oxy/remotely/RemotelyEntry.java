package redxax.oxy.remotely;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rescreen.ReStudioEntry;
import restudio.rescreen.config.Config;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyEntry extends ReStudioEntry {
    @Override
    public void init() {
        Config.applicationDir = remotelyDir;
        Config.setConfigManager(new RemotelyConfigManager(remotelyDir));
    }

    @Override
    public String getIconResourcePath() {
        return "/assets/restudio/logos/Reemotely.png";
    }
}
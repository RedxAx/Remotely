//? if fabric {
/*package redxax.oxy.remotely.platforms.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import redxax.oxy.remotely.servers.SettingsScreen;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class ModMenuIntegration implements ModMenuApi {

    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        SettingsScreen.loadClientConfiguration();
        return parent -> new SettingsScreen("config", parent, remotelyDir.toString(), SettingsScreen.settings);
    }
}
*///?}

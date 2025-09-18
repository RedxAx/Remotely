//? if fabric {
package redxax.oxy.remotely.platforms.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import redxax.oxy.remotely.adapters.ReScreenWrapper;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.config.SettingsScreenFactory;
import restudio.rebase.Rebase;

public class ModMenuIntegration implements ModMenuApi {

    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ReScreenWrapper(SettingsScreenFactory.createGlobalSettingsScreen(null, (RemotelyConfigManager) Rebase.get().getConfigManager()));
    }
}
//?}

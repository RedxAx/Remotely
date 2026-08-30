package redxax.oxy.remotely.ui.settings.data;

import redxax.oxy.remotely.settings.server.ServerSettingsSnapshot;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.instance.Instance;

public class ServerSettingsController extends DesktopServerSettingsDataController {
    public ServerSettingsController(Instance instance, ServerSettingsSnapshot snapshot) {
        super(instance, snapshot);
    }

    public ServerSettingsController(Instance instance, ServerSettingsSnapshot snapshot, RebaseAPI api) {
        super(instance, snapshot, api);
    }
}

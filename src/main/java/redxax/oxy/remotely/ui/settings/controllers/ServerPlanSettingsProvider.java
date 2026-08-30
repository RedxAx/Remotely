package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rescreen.platform.Async;
import restudio.rebase.restudio.api.models.ServerModels;

import java.util.List;

public interface ServerPlanSettingsProvider {
    Async<List<ServerModels.Plan>> plans();
}

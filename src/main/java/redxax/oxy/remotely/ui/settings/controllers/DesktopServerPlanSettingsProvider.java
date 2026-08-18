package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.util.DesktopAsyncTools;
import restudio.rebase.platform.Async;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.restudio.api.models.ServerModels;

import java.util.List;

public final class DesktopServerPlanSettingsProvider implements ServerPlanSettingsProvider {
    @Override public Async<List<ServerModels.Plan>> plans() {
        return DesktopAsyncTools.adapt(ReStudio.getInstance().getApi().getPlans());
    }
}

package redxax.oxy.remotely.ui.server.containers;

import redxax.oxy.remotely.ui.widgets.InstanceResourceWidget;
import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.rescreen.ReScreen;

public class ResourceContainer extends restudio.rebase.ui.screens.resources.ResourceContainer {
    public ResourceContainer(ReScreen host, Instance instance, int x, int y, int width, int height) {
        super(host, instance, x, y, width, height, InstanceResourceWidget::new, true);
    }
}

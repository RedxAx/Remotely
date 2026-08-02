package redxax.oxy.remotely.flow.ui.studio;

import restudio.rescreen.ui.widgets.ItemSelectorWidget;

public interface StudioSelectorView {
    boolean hasActiveStudioSelector();

    default ItemSelectorWidget activeStudioSelector() {
        return null;
    }
}

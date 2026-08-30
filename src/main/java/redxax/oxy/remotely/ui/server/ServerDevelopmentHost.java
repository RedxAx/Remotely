package redxax.oxy.remotely.ui.server;

import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.rescreen.ReScreen;

interface ServerDevelopmentHost {
    ReScreen screen();

    SidePanel createSidePanel(String id);

    int getWidth();

    int getHeight();

    <T extends Widget> T addDrawableChild(T widget);

    void remove(Widget widget);

    default ServerDevelopmentProvider developmentProvider() {
        return ServerDevelopmentProvider.unavailableProvider();
    }

    default boolean isActiveDevelopment(Object instance) {
        return false;
    }

    default void openDevelopmentTab(Object remote, Object local) {
    }

    default void closeInstanceTab(Object instance) {
    }

    default void addInstanceTab(Object instance) {
    }

    default String developmentInstanceId(Object instance) {
        return instance == null ? "" : instance.toString();
    }

    default String developmentInstanceName(Object instance) {
        return instance == null ? "" : instance.toString();
    }

    default String developmentConfigPath() {
        return "data";
    }
}

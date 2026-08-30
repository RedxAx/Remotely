package redxax.oxy.remotely.config;

import restudio.rescreen.ui.settings.SettingsScreen;

public interface SettingsScreenRuntime {
    default void opened() {
    }

    default void displayed(SettingsScreen screen) {
    }

    default void beforeSave() {
    }

    default void afterApply() {
    }

    default void cleanup() {
    }
}

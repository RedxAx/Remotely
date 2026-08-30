package redxax.oxy.remotely.ui.settings.controllers;

public interface CollaborationSettingsProvider {
    String CUSTOM_COLOR = "collaboration.custom-color";
    String COLOR = "collaboration.color";

    default boolean available(String control) {
        return true;
    }
}

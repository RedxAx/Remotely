package redxax.oxy.remotely.ui.server;

import restudio.rebase.backend.CapabilityDescriptor;
import restudio.rebase.ui.screens.resources.ResourceContainerProvider;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.RowWidget;

import java.util.Map;

public interface ResourceContainerAdapter {
    String CAPABILITY_GROUPING = ResourceContainerProvider.CAPABILITY_GROUPING;
    String CAPABILITY_NESTED = ResourceContainerProvider.CAPABILITY_NESTED;
    String CAPABILITY_DETACH = ResourceContainerProvider.CAPABILITY_DETACH;
    String CAPABILITY_UPDATE_SELECTION = ResourceContainerProvider.CAPABILITY_UPDATE_SELECTION;
    String CAPABILITY_BACKUP = ResourceContainerProvider.CAPABILITY_BACKUP;
    String CAPABILITY_UPLOAD = ResourceContainerProvider.CAPABILITY_UPLOAD;

    AnimatedWidget widget();

    RowWidget selectorsRow();

    default void setHost(ReScreen host) {
    }

    default void openInstanceResources() {
    }

    default void showUpdateAllDialog() {
    }

    default void search(String query) {
    }

    default void setSelectorsVisible(boolean visible) {
    }

    default void ensureSelectorsSynced() {
    }

    default void loadResources() {
    }

    default void loadResources(boolean force) {
        loadResources();
    }

    default void resetLoadingState() {
    }

    default Map<String, CapabilityDescriptor> capabilities() {
        return Map.of(
                CAPABILITY_GROUPING, CapabilityDescriptor.supported(CAPABILITY_GROUPING),
                CAPABILITY_NESTED, CapabilityDescriptor.supported(CAPABILITY_NESTED),
                CAPABILITY_DETACH, CapabilityDescriptor.supported(CAPABILITY_DETACH),
                CAPABILITY_UPDATE_SELECTION, CapabilityDescriptor.supported(CAPABILITY_UPDATE_SELECTION),
                CAPABILITY_BACKUP, CapabilityDescriptor.supported(CAPABILITY_BACKUP),
                CAPABILITY_UPLOAD, CapabilityDescriptor.supported(CAPABILITY_UPLOAD));
    }

    default CapabilityDescriptor capability(String id) {
        if (id == null || id.isBlank()) {
            return CapabilityDescriptor.unavailable("resources.unknown", "Resource Capability Is Unavailable");
        }
        return capabilities().getOrDefault(id, CapabilityDescriptor.unavailable(id, "Resource Capability Is Unavailable"));
    }

    default void clearSelectionOutsideResource(double mouseX, double mouseY) {
    }

    default void cleanup() {
    }
}

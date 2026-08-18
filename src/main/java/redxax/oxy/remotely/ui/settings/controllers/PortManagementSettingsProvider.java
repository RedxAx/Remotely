package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.platform.Async;
import restudio.rebase.restudio.api.models.ServerModels;

import java.util.List;

public interface PortManagementSettingsProvider {
    Async<List<ServerModels.Allocation>> getAllocations();
    Async<ServerModels.Allocation> createAllocation();
    Async<ServerModels.Allocation> updateAllocation(Integer allocationId, String notes, boolean primary);
    Async<Void> deleteAllocation(Integer allocationId);
    default boolean available() { return true; }
    default String unavailableReason() { return "Network Feature Is Unavailable"; }

    static PortManagementSettingsProvider unavailable(String reason) {
        return new PortManagementSettingsProvider() {
            private final String message = reason == null || reason.isBlank() ? "Network Feature Is Unavailable" : reason;
            public Async<List<ServerModels.Allocation>> getAllocations() { return failed(); }
            public Async<ServerModels.Allocation> createAllocation() { return failed(); }
            public Async<ServerModels.Allocation> updateAllocation(Integer allocationId, String notes, boolean primary) { return failed(); }
            public Async<Void> deleteAllocation(Integer allocationId) { return failed(); }
            public boolean available() { return false; }
            public String unavailableReason() { return message; }
            private <T> Async<T> failed() { return Async.failed(new UnsupportedOperationException(message)); }
        };
    }
}

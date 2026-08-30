package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.flow.ui.ReSyncProvisioningService;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class NetworkReSyncSetup {
    private NetworkReSyncSetup() {
    }

    static boolean isInstalled(Object instance) {
        return new ReSyncProvisioningService().isInstalled(instance);
    }

    static SetupResult installLatest(Collection<?> instances) {
        ReSyncProvisioningService provisioning = new ReSyncProvisioningService();
        Map<String, Object> unique = new LinkedHashMap<>();
        if (instances != null) {
            for (Object instance : instances) {
                if (instance != null) {
                    unique.putIfAbsent(provisioning.instanceId(instance), instance);
                }
            }
        }
        List<Object> targets = new ArrayList<>(unique.values());
        Map<String, String> failures = new LinkedHashMap<>();
        for (Object instance : targets) {
            ReSyncProvisioningService.OperationResult result = provisioning.installLatest(instance);
            if (!result.success()) {
                failures.put(provisioning.instanceName(instance), result.failureMessage().isBlank() ? "Installation Failed" : result.failureMessage());
            }
        }
        return new SetupResult(targets.size() - failures.size(), targets.size(), failures);
    }

    record SetupResult(int installed, int total, Map<String, String> failures) {
        SetupResult {
            failures = failures == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(failures));
        }

        boolean successful() {
            return failures.isEmpty();
        }

        String failureMessage() {
            return failures.entrySet().stream().map(entry -> entry.getKey() + ": " + entry.getValue()).findFirst().orElse("Installation Failed");
        }
    }
}

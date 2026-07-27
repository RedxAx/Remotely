package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.flow.ui.ReSyncProvisioningService;
import restudio.rebase.instance.Instance;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class NetworkReSyncSetup {
    private NetworkReSyncSetup() {
    }

    static boolean isInstalled(Instance instance) {
        return new ReSyncProvisioningService().isInstalled(instance);
    }

    static SetupResult installLatest(Collection<Instance> instances) {
        List<Instance> targets = instances == null ? List.of() : instances.stream().filter(Objects::nonNull)
            .collect(Collectors.toMap(Instance::getInstanceId, instance -> instance, (first, second) -> first, LinkedHashMap::new))
            .values().stream().toList();
        Map<String, String> failures = new LinkedHashMap<>();
        ReSyncProvisioningService provisioning = new ReSyncProvisioningService();
        for (Instance instance : targets) {
            ReSyncProvisioningService.OperationResult result = provisioning.installLatest(instance);
            if (!result.success()) {
                failures.put(instance.getName(), result.failureMessage().isBlank() ? "Installation Failed" : result.failureMessage());
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

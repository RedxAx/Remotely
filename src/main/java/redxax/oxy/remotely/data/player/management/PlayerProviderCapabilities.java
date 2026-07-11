package redxax.oxy.remotely.data.player.management;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record PlayerProviderCapabilities(String providerId, Set<PlayerSection> readableSections, Set<PlayerOperation.Type> operations) {
    public PlayerProviderCapabilities {
        readableSections = readableSections == null || readableSections.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(readableSections));
        operations = operations == null || operations.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(operations));
    }

    public boolean supports(PlayerSection section) {
        return readableSections.contains(section);
    }

    public boolean supports(PlayerOperation.Type operation) {
        return operations.contains(operation);
    }
}

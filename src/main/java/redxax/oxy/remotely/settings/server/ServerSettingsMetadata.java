package redxax.oxy.remotely.settings.server;

import java.util.List;
import java.util.Objects;

public final class ServerSettingsMetadata {
    private final String providerId;
    private final int priority;
    private final List<ServerSettingsPack> packs;

    public ServerSettingsMetadata(String providerId, int priority, List<ServerSettingsPack> packs) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("A provider ID is required");
        }
        this.providerId = providerId.trim();
        this.priority = priority;
        this.packs = packs == null ? List.of() : List.copyOf(packs);
        if (this.packs.isEmpty()) {
            throw new IllegalArgumentException("Metadata needs at least one settings pack");
        }
    }

    public String providerId() {
        return providerId;
    }

    public String provider() {
        return providerId;
    }

    public int priority() {
        return priority;
    }

    public List<ServerSettingsPack> packs() {
        return packs;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerSettingsMetadata that)) return false;
        return priority == that.priority && providerId.equals(that.providerId) && packs.equals(that.packs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, priority, packs);
    }
}

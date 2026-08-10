package redxax.oxy.remotely.settings.server;

import java.util.List;

public record ServerSettingsSnapshot(List<ServerSettingsPack> packs) {
    public ServerSettingsSnapshot {
        packs = packs == null ? List.of() : List.copyOf(packs);
    }

    public List<ServerSettingsPack> getPacks() {
        return packs;
    }

    public boolean isEmpty() {
        return packs.isEmpty();
    }
}

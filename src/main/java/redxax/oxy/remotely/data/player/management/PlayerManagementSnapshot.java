package redxax.oxy.remotely.data.player.management;

import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.data.flow.player.PlayerFacetState;
import redxax.oxy.remotely.data.managed.PlayerSession;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.data.playerdata.PlayerData;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record PlayerManagementSnapshot(UnifiedPlayer player, PlayerData playerData, PlayerDossier dossier, List<PlayerSession> localSessions,
                                       Map<String, PlayerFacetState> extensionFacets, Map<PlayerSection, PlayerSectionState<?>> sections,
                                       long generation) {
    public PlayerManagementSnapshot {
        localSessions = localSessions == null ? List.of() : List.copyOf(localSessions);
        extensionFacets = extensionFacets == null ? Map.of() : Collections.unmodifiableMap(extensionFacets);
        EnumMap<PlayerSection, PlayerSectionState<?>> states = new EnumMap<>(PlayerSection.class);
        if (sections != null) states.putAll(sections);
        for (PlayerSection section : PlayerSection.values()) states.putIfAbsent(section, PlayerSectionState.unsupported());
        sections = Collections.unmodifiableMap(states);
    }

    @SuppressWarnings("unchecked")
    public <T> PlayerSectionState<T> section(PlayerSection section) {
        return (PlayerSectionState<T>) sections.getOrDefault(section, PlayerSectionState.unsupported());
    }

    public boolean supports(PlayerSection section) {
        return section(section).supported();
    }
}

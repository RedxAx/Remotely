package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.ui.collaboration.CollaborationVisuals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SlotCollaborationAuthority {
    private List<CollaborativeSlotView.RemoteSlotSelection> remote = List.of();
    private boolean localOverride;

    void apply(List<CollaborativeSlotView.RemoteSlotSelection> selections) {
        remote = selections != null ? List.copyOf(selections) : List.of();
    }

    void markLocalInteraction() {
        localOverride = true;
    }

    Integer followedSlot() {
        if (localOverride) {
            return null;
        }
        return remote.stream().filter(selection -> !selection.slots().isEmpty()).max((left, right) -> Long.compare(left.updatedAt(), right.updatedAt()))
            .map(selection -> selection.slots().getFirst()).orElse(null);
    }

    boolean isFollowing() {
        return !localOverride && followedSlot() != null;
    }

    Map<Integer, Integer> remoteColors() {
        Map<Integer, List<Integer>> colors = new LinkedHashMap<>();
        for (CollaborativeSlotView.RemoteSlotSelection selection : remote) {
            for (int slot : selection.slots()) {
                colors.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(selection.color());
            }
        }
        Map<Integer, Integer> resolved = new LinkedHashMap<>();
        colors.forEach((slot, values) -> resolved.put(slot, CollaborationVisuals.blend(values, 0xFF4E8CFF)));
        return resolved;
    }
}

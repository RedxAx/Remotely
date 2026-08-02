package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonArray;

import java.util.List;

interface CollaborativeSlotView {
    JsonArray collaborationSlots();

    void applyCollaborationSlots(List<RemoteSlotSelection> selections);

    record RemoteSlotSelection(String sessionId, List<Integer> slots, int color, long updatedAt) {
        public RemoteSlotSelection {
            slots = slots != null ? List.copyOf(slots) : List.of();
        }
    }
}

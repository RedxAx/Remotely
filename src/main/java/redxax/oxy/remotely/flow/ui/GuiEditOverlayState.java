package redxax.oxy.remotely.flow.ui;

public final class GuiEditOverlayState {
    private static final Object LOCK = new Object();
    private static boolean editable;
    private static String serverId;
    private static String guiId;
    private static String flowId;
    private static int revision;

    private GuiEditOverlayState() {}

    public static void update(String serverId, String guiId, String flowId, boolean editable) {
        synchronized (LOCK) {
            GuiEditOverlayState.serverId = serverId;
            GuiEditOverlayState.guiId = guiId;
            GuiEditOverlayState.flowId = flowId;
            GuiEditOverlayState.editable = editable;
            revision++;
        }
    }

    public static void clear() {
        update(null, null, null, false);
    }

    public static OverlaySnapshot snapshot() {
        synchronized (LOCK) {
            return new OverlaySnapshot(editable, serverId, guiId, flowId, revision);
        }
    }

    public record OverlaySnapshot(boolean editable, String serverId, String guiId, String flowId, int revision) {}
}

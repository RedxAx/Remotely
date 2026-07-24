package redxax.oxy.remotely.flow.ui.studio;

public interface StudioResourceRenameAware {
    default void resourceRenamed(String type, String oldId, String newId) {
    }
}

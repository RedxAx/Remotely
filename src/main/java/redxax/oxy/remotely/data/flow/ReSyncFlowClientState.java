package redxax.oxy.remotely.data.flow;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.player.PlayerTrackingUpdate;
import redxax.oxy.remotely.data.flow.world.WorldChannelMessage;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.sync.OptionCatalogSnapshot;
import restudio.resync.flow.contract.EditorError;

import java.util.List;
import java.util.Map;

public interface ReSyncFlowClientState {
    default void onPlayerTrackingUpdate(String serverId, PlayerTrackingUpdate update) {
    }

    default void onWorldManagementMessage(String serverId, WorldChannelMessage message) {
    }

    default void onTraceSnapshot(String serverId, String json) {
    }

    default void onTraceEvent(String serverId, String json) {
    }

    default void onDebugSnapshot(String serverId, String json) {
    }

    default void onMessageLogPage(String serverId, JsonObject page) {
    }

    default void onServerCapabilities(String serverId, JsonObject capabilities) {
    }

    default JsonObject serverCapabilities(String serverId) {
        return null;
    }

    default FlowGraph graph(String serverId, ReSyncResourceType type, String id) {
        return null;
    }

    default void onResourceData(String serverId, ReSyncResourceType type, Object item, boolean openWhenReceived) {
    }

    default void onResourceList(String serverId, ReSyncResourceType type, List<String> ids) {
    }

    default void onResourceDeleted(String serverId, ReSyncResourceType type, String id) {
    }

    default void onResourceActivation(String serverId, ReSyncResourceType type, String resourceId, boolean enabled,
                                      String requestId, boolean success, String message, boolean editorError) {
    }

    default void onQuickEditOpen(String serverId, String sessionId, CustomContentDefinition definition) {
    }

    default void onQuickEditResult(String serverId, boolean applied, String message) {
    }

    default void onOpenCustomContent(String serverId, CustomContentDefinition content) {
    }

    default void onGuiState(String serverId, boolean editable, String guiId, String flowId) {
    }

    default void onEditTargetState(String serverId, boolean editable, String resourceType, String resourceId, String flowId) {
    }

    default void onFlowError(String serverId, EditorError editorError, List<Map<String, Object>> attributeErrors, String message) {
    }

    default void onResourceSaveFailed(String serverId, ReSyncResourceType type, String id, String message) {
    }

    default void onResourceSaveSucceeded(String serverId, ReSyncResourceType type, String id, long sequence, long revision, String hash) {
    }

    default boolean isBackingFlowAcknowledgement(String serverId, ReSyncResourceType type, String id) {
        return false;
    }

    default void onOptionCatalog(String serverId, OptionCatalogSnapshot payload) {
    }

    default void onNodeRegistryUpdated(String serverId) {
    }

    default void onPlaceholderPreview(String serverId, String rendered) {
    }

    default void onNotification(String title, String message, ReSyncNotificationLevel level) {
    }

    default Integer collaborationColorOverride() {
        return null;
    }

    default Object luckPerms(ReSyncFlowClient client) {
        return null;
    }

    default void closeIntegrations(ReSyncFlowClient client) {
    }

    default void onResourceStateFailed(String serverId, ReSyncResourceType type, String id) {
    }

    default boolean onWorldGenerationSaveFailed(String serverId, String requestId, String message) {
        return false;
    }

    default void attachSaveRequest(String serverId, ReSyncResourceType type, String id, String requestId) {
    }

    default ReSyncSaveTarget failAnySave(String serverId, String title, String message) {
        return null;
    }

    default ReSyncSaveTarget failSaveRequest(String serverId, String requestId, String message) {
        return null;
    }

    default ReSyncSaveTarget failResourceSave(String serverId, ReSyncResourceType type, String id, String message) {
        return null;
    }

    default ReSyncSaveTarget completeSave(String serverId, ReSyncResourceType type, String id, String requestId) {
        return null;
    }

    default boolean consumeAutomaticNotificationSuppression(String requestId) {
        return false;
    }

    default boolean consumeRecentError(String serverId, String message) {
        return false;
    }

    static ReSyncFlowClientState noop() {
        return new ReSyncFlowClientState() {
        };
    }
}

package redxax.oxy.remotely.data.flow;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.player.PlayerTrackingUpdate;
import redxax.oxy.remotely.data.flow.world.WorldChannelMessage;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.sync.OptionCatalogSnapshot;
import redxax.oxy.remotely.flow.ui.AdvancementDesignerScreen;
import redxax.oxy.remotely.flow.ui.ContentDesignerScreen;
import redxax.oxy.remotely.flow.ui.DialogDesignerScreen;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.flow.ui.FocusedJsonResourceDesignerScreen;
import redxax.oxy.remotely.flow.ui.GraphEditorScreen;
import redxax.oxy.remotely.flow.ui.GuiDesignerScreen;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.registry.WorldGenNodeDefinition;
import restudio.resync.flow.contract.EditorError;

import java.util.List;
import java.util.Map;

public final class ReSyncFlowManagerState implements ReSyncFlowClientState, ReSyncWorldGenerationState {
    private final FlowManager manager;

    public ReSyncFlowManagerState(FlowManager manager) {
        this.manager = manager;
    }

    @Override
    public void onPlayerTrackingUpdate(String serverId, PlayerTrackingUpdate update) {
        manager.applyPlayerTrackingUpdate(serverId, update);
    }

    @Override
    public void onWorldManagementMessage(String serverId, WorldChannelMessage message) {
        manager.applyWorldManagementMessage(serverId, message);
    }

    @Override
    public void onTraceSnapshot(String serverId, String json) {
        manager.getDebugController().applyTraceSnapshot(serverId, json);
    }

    @Override
    public void onTraceEvent(String serverId, String json) {
        manager.getDebugController().applyTraceEvent(serverId, json);
    }

    @Override
    public void onDebugSnapshot(String serverId, String json) {
        manager.getDebugController().applyDebugSnapshot(serverId, json);
    }

    @Override
    public void onMessageLogPage(String serverId, JsonObject page) {
        manager.cacheMessageLogPage(serverId, page);
    }

    @Override
    public void onServerCapabilities(String serverId, JsonObject capabilities) {
        manager.cacheServerCapabilities(serverId, capabilities);
    }

    @Override
    public JsonObject serverCapabilities(String serverId) {
        return manager.getServerCapabilities(serverId);
    }

    @Override
    public FlowGraph graph(String serverId, ReSyncResourceType type, String id) {
        return manager.getGraph(serverId, type, id);
    }

    @Override
    public void onResourceData(String serverId, ReSyncResourceType type, Object item, boolean openWhenReceived) {
        if (item == null) return;
        cache(serverId, type, item);
        handleData(serverId, type, item);
        if (openWhenReceived) {
            String id = type.extractId(item);
            if (id != null && !id.isBlank()) {
                manager.openStudioDocument(serverId, ReSyncProjectMetadata.resourceKey(type.typeId(), id),
                    screen -> screen.openWorkspaceDesigner(type.typeId(), id, false));
            }
        }
    }

    @Override
    public void onResourceList(String serverId, ReSyncResourceType type, List<String> ids) {
        if (type.isGraph()) manager.applyServerGraphList(serverId, type, ids);
        else if (type == ReSyncResourceType.GUI) manager.applyServerGuiList(serverId, ids);
        else if (type == ReSyncResourceType.SCOREBOARD) manager.applyServerScoreboardList(serverId, ids);
        else if (type == ReSyncResourceType.TAB) manager.applyServerTabList(serverId, ids);
        else if (type == ReSyncResourceType.CUSTOM_CONTENT) manager.applyServerCustomContentList(serverId, ids);
        else if (type == ReSyncResourceType.PROJECT_METADATA) manager.applyServerProjectMetadataList(serverId, ids);
        else manager.applyServerJsonResourceList(serverId, type, ids);
    }

    @Override
    public void onResourceDeleted(String serverId, ReSyncResourceType type, String id) {
        manager.confirmResourceDeleted(serverId, type, id);
    }

    @Override
    public void onResourceActivation(String serverId, ReSyncResourceType type, String resourceId, boolean enabled,
                                     String requestId, boolean success, String message, boolean editorError) {
        manager.completeResourceActivation(serverId, type, resourceId, enabled, requestId, success, message, !editorError);
    }

    @Override
    public void onQuickEditOpen(String serverId, String sessionId, CustomContentDefinition definition) {
        manager.openStudioDocument(serverId, "quick_edit:" + sessionId, studioScreen -> {
            ContentDesignerScreen screen = ContentDesignerScreen.quickEdit(serverId, sessionId, definition, studioScreen);
            String documentId = definition != null && definition.getId() != null && !definition.getId().isBlank()
                ? definition.getId() : "quickedit_" + (sessionId != null && !sessionId.isBlank() ? sessionId : "item");
            studioScreen.openWorkspaceContentDesigner(documentId, "Quick Edit", screen.getContentGraph(), screen, false);
        });
    }

    @Override
    public void onQuickEditResult(String serverId, boolean applied, String message) {
        onNotification(applied ? "Quick Edit" : "Quick Edit Failed", message,
            applied ? ReSyncNotificationLevel.SUCCESS : ReSyncNotificationLevel.ERROR);
    }

    @Override
    public void onOpenCustomContent(String serverId, CustomContentDefinition content) {
        manager.cacheCustomContent(serverId, content);
        if (content != null && content.getGraph() != null) {
            manager.openStudioDocument(serverId, "custom_content:" + content.getId(),
                screen -> screen.openWorkspaceContentDesigner(content.getId(), content.getDisplayName(), content.getGraph()));
        }
    }

    @Override
    public void onGuiState(String serverId, boolean editable, String guiId, String flowId) {
        manager.handleGuiStatePacket(serverId, editable, guiId, flowId);
    }

    @Override
    public void onEditTargetState(String serverId, boolean editable, String resourceType, String resourceId, String flowId) {
        manager.handleEditTargetStatePacket(serverId, editable, resourceType, resourceId, flowId);
    }

    @Override
    public void onFlowError(String serverId, EditorError editorError, List<Map<String, Object>> attributeErrors, String message) {
        if (editorError != null) GraphEditorScreen.handleEditorErrorForServer(serverId, editorError);
        else if (attributeErrors != null && !attributeErrors.isEmpty()) ContentDesignerScreen.handleAttributeValidationErrorsForServer(serverId, attributeErrors);
        if (message != null && !message.isBlank()) onNotification("ReSync", message, ReSyncNotificationLevel.ERROR);
    }

    @Override
    public void onResourceSaveFailed(String serverId, ReSyncResourceType type, String id, String message) {
        manager.markResourceSaveFailed(serverId, type, id);
    }

    @Override
    public void onResourceSaveSucceeded(String serverId, ReSyncResourceType type, String id, long sequence, long revision, String hash) {
        if (type.isGraph() && revision > 0L) manager.markFlowSaved(serverId, type, id, revision, hash);
        else if (type == ReSyncResourceType.GUI) manager.markGuiSaved(serverId, id);
        else if (type == ReSyncResourceType.SCOREBOARD) manager.markScoreboardSaved(serverId, id);
        else if (type == ReSyncResourceType.TAB) manager.markTabSaved(serverId, id);
        else if (type == ReSyncResourceType.CUSTOM_CONTENT) manager.markCustomContentSaved(serverId, id);
        else if (type == ReSyncResourceType.PROJECT_METADATA) manager.markProjectMetadataSaved(serverId);
        else manager.markJsonResourceSaved(serverId, type, id);
    }

    @Override
    public boolean isBackingFlowAcknowledgement(String serverId, ReSyncResourceType type, String id) {
        if (!type.isGraph() || id == null) return false;
        FlowGraph graph = manager.getGraph(serverId, type, id);
        if (CustomContentGraphAdapter.isContentGraph(graph) || manager.getCommandBinding(serverId, id) != null) return true;
        return manager.getCustomContentForServer(serverId).values().stream()
            .anyMatch(content -> content != null && id.equals(content.getFlowId()));
    }

    @Override
    public void onOptionCatalog(String serverId, OptionCatalogSnapshot payload) {
        FlowEditorScreen.refreshCatalogForServer(serverId, payload.getSourceId());
        GuiDesignerScreen.refreshCatalogForServer(serverId);
        AdvancementDesignerScreen.refreshCatalogForServer(serverId);
        DialogDesignerScreen.refreshCatalogForServer(serverId);
        FocusedJsonResourceDesignerScreen.refreshCatalogForServer(serverId);
        manager.refreshStudioWorkspace(serverId, true);
    }

    @Override
    public void onNodeRegistryUpdated(String serverId) {
        manager.refreshStudioWorkspace(serverId, true);
    }

    @Override
    public void onNotification(String title, String message, ReSyncNotificationLevel level) {
        manager.notify(title, message, level);
    }

    @Override
    public void onResourceStateFailed(String serverId, ReSyncResourceType type, String id) {
        manager.markResourceSaveFailed(serverId, type, id);
    }

    @Override
    public boolean onWorldGenerationSaveFailed(String serverId, String requestId, String message) {
        return WorldGenManager.getInstance().failProjectSaveFromRequestId(serverId, requestId, message);
    }

    @Override
    public void attachSaveRequest(String serverId, ReSyncResourceType type, String id, String requestId) {
        DesignerSaveNotifications.attachRequestId(serverId, type, id, requestId);
    }

    @Override
    public ReSyncSaveTarget failAnySave(String serverId, String title, String message) {
        return target(DesignerSaveNotifications.failAnyForServer(serverId, title, message));
    }

    @Override
    public ReSyncSaveTarget failSaveRequest(String serverId, String requestId, String message) {
        return target(DesignerSaveNotifications.failRequest(serverId, requestId, message));
    }

    @Override
    public ReSyncSaveTarget failResourceSave(String serverId, ReSyncResourceType type, String id, String message) {
        return target(DesignerSaveNotifications.failResource(serverId, type, id, message));
    }

    @Override
    public ReSyncSaveTarget completeSave(String serverId, ReSyncResourceType type, String id, String requestId) {
        return target(DesignerSaveNotifications.complete(serverId, type, id, requestId));
    }

    @Override
    public boolean consumeAutomaticNotificationSuppression(String requestId) {
        return DesignerSaveNotifications.consumeAutomaticNotificationSuppression(requestId);
    }

    @Override
    public boolean consumeRecentError(String serverId, String message) {
        return DesignerSaveNotifications.consumeRecentError(serverId, message);
    }

    @Override
    public void onPreviewStatus(String serverId, String previewId, String state, String message) {
        WorldGenManager.getInstance().handlePreviewStatus(serverId, previewId, state, message);
    }

    @Override
    public void onRegistrySnapshot(String serverId, List<WorldGenNodeDefinition> definitions, Object capabilities) {
        WorldGenManager.getInstance().applyRegistrySnapshot(serverId, definitions, capabilities);
    }

    @Override
    public void onProjectData(String serverId, WorldGenProject project) {
        WorldGenManager.getInstance().handleProjectData(serverId, project);
    }

    @Override
    public void onProjectList(String serverId, List<String> ids) {
        WorldGenManager.getInstance().handleProjectList(serverId, ids);
    }

    @Override
    public void onProjectSaveAcknowledged(String serverId, String payload) {
        WorldGenManager.getInstance().handleProjectSaved(serverId, payload);
    }

    @Override
    public void onCompileDiagnostics(String serverId, String payload) {
        WorldGenManager.getInstance().handleCompileDiagnostics(serverId, payload);
    }

    private void cache(String serverId, ReSyncResourceType type, Object item) {
        if (type.isGraph()) manager.cacheFlow(serverId, (FlowGraph) item);
        else if (type == ReSyncResourceType.GUI) manager.cacheGui(serverId, (GuiDefinition) item);
        else if (type == ReSyncResourceType.SCOREBOARD) manager.cacheScoreboard(serverId, (ScoreboardDefinition) item);
        else if (type == ReSyncResourceType.TAB) manager.cacheTab(serverId, (TabDefinition) item);
        else if (type == ReSyncResourceType.CUSTOM_CONTENT) manager.cacheCustomContent(serverId, (CustomContentDefinition) item);
        else if (type == ReSyncResourceType.PROJECT_METADATA) manager.cacheProjectMetadata(serverId, (ReSyncProjectMetadata) item);
        else if (item instanceof JsonObject json) manager.cacheJsonResource(serverId, type, json);
    }

    private void handleData(String serverId, ReSyncResourceType type, Object item) {
        if (type == ReSyncResourceType.GUI) manager.handleGuiDataReceived(serverId, (GuiDefinition) item);
        else if (type == ReSyncResourceType.SCOREBOARD) manager.handleScoreboardDataReceived(serverId, (ScoreboardDefinition) item);
        else if (type == ReSyncResourceType.TAB) manager.handleTabDataReceived(serverId, (TabDefinition) item);
        else if (type == ReSyncResourceType.ADVANCEMENT_TREE && item instanceof JsonObject value) manager.handleAdvancementTreeDataReceived(serverId, value);
        else if (type == ReSyncResourceType.DIALOG && item instanceof JsonObject value) manager.handleDialogDataReceived(serverId, value);
        else if ((type == ReSyncResourceType.TRADE_PROFILE || type == ReSyncResourceType.NPC_DEFINITION || type == ReSyncResourceType.LOOT_TABLE) && item instanceof JsonObject value) {
            manager.handleFocusedJsonResourceDataReceived(serverId, type, value);
        }
    }

    private ReSyncSaveTarget target(DesignerSaveNotifications.SaveTarget target) {
        return target == null ? null : new ReSyncSaveTarget(target.type(), target.id(), target.shouldUpdateResourceState(), target.sequence());
    }
}

package redxax.oxy.remotely.flow.ui.studio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import restudio.rescreen.platform.IDrawContext;
import restudio.resync.flow.workspace.WorkspacePatch;

import java.util.List;

public class ScoreboardStudioPreviewView implements ReSyncStudioView, ReSyncCollaborativeView {
    private final String serverId;
    private final String scoreboardId;

    public ScoreboardStudioPreviewView(String serverId, String scoreboardId) {
        this.serverId = serverId;
        this.scoreboardId = scoreboardId;
    }

    @Override
    public JsonObject collaborationDocument() {
        FlowManager manager = FlowManager.getInstance();
        return ReSyncCollaborationDocuments.from(manager != null ? manager.getScoreboardsForServer(serverId).get(scoreboardId) : null);
    }

    @Override
    public void applyCollaborationDocument(JsonObject document, List<WorkspacePatch<JsonElement>> patches) {
        FlowManager manager = FlowManager.getInstance();
        ScoreboardDefinition target = manager != null ? manager.getScoreboardsForServer(serverId).get(scoreboardId) : null;
        ReSyncCollaborationDocuments.copy(target, ReSyncCollaborationDocuments.to(document, ScoreboardDefinition.class));
    }

    @Override
    public void renderPreview(IDrawContext context, int x, int y, int width, int height) {
        FlowManager manager = FlowManager.getInstance();
        ScoreboardDefinition scoreboard = manager != null ? manager.getScoreboardsForServer(serverId).get(scoreboardId) : null;
        if (scoreboard == null) {
            return;
        }
        List<String> lines = scoreboard.getLines() == null ? List.of() : scoreboard.getLines();
        int maxLines = Math.min(15, lines.size());
        int panelWidth = Math.clamp(width / 3, 120, width - 24);
        int rowHeight = 12;
        int panelHeight = Math.min(height - 24, (maxLines + 1) * rowHeight + 8);
        int startX = x + Math.max(0, (width - panelWidth) / 2);
        int startY = y + Math.max(0, (height - panelHeight) / 2);
        context.fill(startX, startY, startX + panelWidth, startY + panelHeight, 0x7F101010);
        context.drawText(scoreboard.getTitle() == null || scoreboard.getTitle().isBlank() ? scoreboard.getId() : scoreboard.getTitle(), startX + 6, startY + 4, 0xFFFFFFFF, true);
        for (int i = 0; i < maxLines; i++) {
            context.drawText(lines.get(i), startX + 6, startY + 18 + i * rowHeight, 0xFFFFFFFF, true);
        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
    }
}

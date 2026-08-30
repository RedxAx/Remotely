package redxax.oxy.remotely.flow.ui.studio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import restudio.resync.flow.workspace.WorkspacePatch;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.render.Render;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;

import java.util.List;

public class GuiStudioPreviewView implements ReSyncStudioView, ReSyncCollaborativeView {
    private final String serverId;
    private final String guiId;

    public GuiStudioPreviewView(String serverId, String guiId) {
        this.serverId = serverId;
        this.guiId = guiId;
    }

    @Override
    public JsonObject collaborationDocument() {
        FlowManager manager = FlowManager.getInstance();
        return ReSyncCollaborationDocuments.from(manager != null ? manager.getGuisForServer(serverId).get(guiId) : null);
    }

    @Override
    public void applyCollaborationDocument(JsonObject document, List<WorkspacePatch<JsonElement>> patches) {
        FlowManager manager = FlowManager.getInstance();
        GuiDefinition target = manager != null ? manager.getGuisForServer(serverId).get(guiId) : null;
        ReSyncCollaborationDocuments.copy(target, ReSyncCollaborationDocuments.toGui(document));
    }

    @Override
    public void renderPreview(IDrawContext context, int x, int y, int width, int height) {
        FlowManager manager = FlowManager.getInstance();
        GuiDefinition gui = manager != null ? manager.getGuisForServer(serverId).get(guiId) : null;
        if (gui == null) {
            return;
        }
        int rows = Math.clamp(gui.getRows(), 1, 6);
        int slot = Math.clamp(Math.min((width - 40) / 9, (height - 40) / rows), 12, 26);
        int gridWidth = slot * 9;
        int gridHeight = slot * rows;
        int startX = x + Math.max(0, (width - gridWidth) / 2);
        int startY = y + Math.max(0, (height - gridHeight) / 2);
        int border = ThemeManager.getColor(ThemeColor.innerBorder);
        int background = ThemeManager.getColor(ThemeColor.innerBackground);
        Render.drawLayeredInnerBorder(context, startX - 6, startY - 18, gridWidth + 12, gridHeight + 24, background, border);
        context.drawText(gui.getTitle() == null || gui.getTitle().isBlank() ? gui.getId() : gui.getTitle(), startX, startY - 12, ThemeManager.getColor(ThemeColor.text), false);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = startX + col * slot;
                int slotY = startY + row * slot;
                Render.drawLayeredInnerBorder(context, slotX, slotY, slot - 1, slot - 1, ThemeManager.getColor(ThemeColor.background), border);
            }
        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
    }
}

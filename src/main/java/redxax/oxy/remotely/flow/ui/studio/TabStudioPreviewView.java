package redxax.oxy.remotely.flow.ui.studio;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.TabDefinition;
import restudio.rescreen.platform.IDrawContext;

public class TabStudioPreviewView implements ReSyncStudioView {
    private final String serverId;
    private final String tabId;

    public TabStudioPreviewView(String serverId, String tabId) {
        this.serverId = serverId;
        this.tabId = tabId;
    }

    @Override
    public void renderPreview(IDrawContext context, int x, int y, int width, int height) {
        FlowManager manager = FlowManager.getInstance();
        TabDefinition tab = manager != null ? manager.getTabsForServer(serverId).get(tabId) : null;
        if (tab == null) {
            return;
        }
        int panelWidth = Math.clamp(width / 3, 160, width - 24);
        int panelHeight = Math.min(120, height - 24);
        int startX = x + Math.max(0, (width - panelWidth) / 2);
        int startY = y + Math.max(0, (height - panelHeight) / 2);
        context.fill(startX, startY, startX + panelWidth, startY + panelHeight, 0x7F101010);
        int text = 0xFFFFFFFF;
        context.drawText(tab.getHeader() == null || tab.getHeader().isBlank() ? tab.getId() : tab.getHeader(), startX + 6, startY + 6, text, true);
        context.drawText(tab.getEntryFormat() == null || tab.getEntryFormat().isBlank() ? "%player%" : tab.getEntryFormat(), startX + 6, startY + 48, text, true);
        context.drawText(tab.getFooter() == null ? "" : tab.getFooter(), startX + 6, startY + panelHeight - 18, text, true);
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
    }
}

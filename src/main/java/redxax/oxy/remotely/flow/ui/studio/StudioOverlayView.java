package redxax.oxy.remotely.flow.ui.studio;

import restudio.rescreen.platform.IDrawContext;

public interface StudioOverlayView {
    void renderStudioOverlay(IDrawContext context, int mouseX, int mouseY, float delta);
}

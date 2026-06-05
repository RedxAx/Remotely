package redxax.oxy.remotely.flow.ui.studio;

import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.widgets.AnimatedWidget;

import java.util.List;

public interface ReSyncStudioView {
    default void init() {}
    default void selected() {}
    default void closed() {}
    default void resize(int width, int height) {}
    default List<AnimatedWidget> headerButtons() { return List.of(); }
    default StudioPanel.Placement preferredPanelPlacement() { return StudioPanel.Placement.RIGHT; }
    default boolean hasPanel() { return false; }
    default void configurePanel(StudioPanel panel) {}
    default void renderPreview(IDrawContext context, int x, int y, int width, int height) {}
    void render(IDrawContext context, int mouseX, int mouseY, float delta);
    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
    default boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) { return false; }
    default void mouseMoved(double mouseX, double mouseY) {}
    default boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { return false; }
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    default boolean charTyped(char chr, int modifiers) { return false; }
}

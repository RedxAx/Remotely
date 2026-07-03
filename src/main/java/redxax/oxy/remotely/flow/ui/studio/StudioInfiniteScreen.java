package redxax.oxy.remotely.flow.ui.studio;

import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rebase.ui.widgets.editor.TextAreaWidget;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReKey;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReModifierState;
import restudio.rescreen.ui.core.InfiniteScreen;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class StudioInfiniteScreen extends InfiniteScreen {
    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private final List<StudioScreen.History<?>> histories = new ArrayList<>();

    protected <T> StudioScreen.History<T> history(Supplier<T> snapshotSupplier, Consumer<T> restoreConsumer) {
        return history(snapshotSupplier, restoreConsumer, DEFAULT_HISTORY_LIMIT);
    }

    protected <T> StudioScreen.History<T> history(Supplier<T> snapshotSupplier, Consumer<T> restoreConsumer, int limit) {
        StudioScreen.History<T> history = new StudioScreen.History<>(snapshotSupplier, restoreConsumer, limit);
        histories.add(history);
        return history;
    }

    protected StudioPanel studioPanel(String id) {
        return new StudioPanel(this, id);
    }

    protected StudioPanel rightStudioPanel(String id) {
        return studioPanel(id).right();
    }

    protected StudioPanel leftStudioPanel(String id) {
        return studioPanel(id).left();
    }

    protected int studioPanelTop() {
        return 54;
    }

    protected int studioPanelBottomReserve() {
        return 8;
    }

    protected boolean handleStudioHistoryShortcut(ReKeyEvent event) {
        if (!isStudioHistoryShortcutAllowed(event.modifiers())) {
            return false;
        }
        if (event.key() == ReKey.Z) {
            if (event.modifiers().shift()) {
                return redoActiveHistory();
            }
            return undoActiveHistory();
        }
        if (event.key() == ReKey.Y) {
            return redoActiveHistory();
        }
        return false;
    }

    protected boolean isStudioHistoryShortcutAllowed(ReModifierState modifiers) {
        return modifiers.control() && !isStudioKeyboardInputFocused();
    }

    protected boolean isStudioKeyboardInputFocused() {
        Widget focused = getFocusedWidget();
        return focused instanceof TextInputWidget
            || focused instanceof TextAreaWidget
            || focused instanceof CodeEditorWidget
            || focused instanceof ItemSelectorWidget;
    }

    protected boolean undoActiveHistory() {
        StudioScreen.History<?> history = activeHistory();
        return history != null && history.undo();
    }

    protected boolean redoActiveHistory() {
        StudioScreen.History<?> history = activeHistory();
        return history != null && history.redo();
    }

    protected StudioScreen.History<?> activeHistory() {
        if (histories.isEmpty()) {
            return null;
        }
        return histories.getLast();
    }

    protected void renderStudioPanel(SidePanel panel, IDrawContext context, int mouseX, int mouseY, float delta) {
        if (panel == null) {
            return;
        }
        panel.update();
        panel.container().render(context, mouseX, mouseY, delta);
        panel.renderHeader(context, mouseX, mouseY);
    }

    protected void renderStudioPanel(StudioPanel panel, IDrawContext context, int mouseX, int mouseY, float delta) {
        if (panel == null) {
            return;
        }
        panel.layout();
        renderStudioPanel(panel.sidePanel(), context, mouseX, mouseY, delta);
        panel.renderHintOverlay(context);
    }
}

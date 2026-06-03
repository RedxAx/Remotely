package redxax.oxy.remotely.flow.ui.studio;

import org.lwjgl.glfw.GLFW;
import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rebase.ui.widgets.editor.TextAreaWidget;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class StudioScreen extends ReScreen {
    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private final List<History<?>> histories = new ArrayList<>();

    protected <T> History<T> history(Supplier<T> snapshotSupplier, Consumer<T> restoreConsumer) {
        return history(snapshotSupplier, restoreConsumer, DEFAULT_HISTORY_LIMIT);
    }

    protected <T> History<T> history(Supplier<T> snapshotSupplier, Consumer<T> restoreConsumer, int limit) {
        History<T> history = new History<>(snapshotSupplier, restoreConsumer, limit);
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (!isStudioHistoryShortcutAllowed(modifiers)) {
            return false;
        }
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (keyCode == GLFW.GLFW_KEY_Z) {
            if (shift) {
                return redoActiveHistory();
            }
            return undoActiveHistory();
        }
        if (keyCode == GLFW.GLFW_KEY_Y) {
            return redoActiveHistory();
        }
        return false;
    }

    protected boolean isStudioHistoryShortcutAllowed(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && !isStudioKeyboardInputFocused();
    }

    protected boolean isStudioKeyboardInputFocused() {
        Widget focused = getFocusedWidget();
        return focused instanceof TextInputWidget
            || focused instanceof TextAreaWidget
            || focused instanceof CodeEditorWidget
            || focused instanceof ItemSelectorWidget;
    }

    protected boolean undoActiveHistory() {
        History<?> history = activeHistory();
        return history != null && history.undo();
    }

    protected boolean redoActiveHistory() {
        History<?> history = activeHistory();
        return history != null && history.redo();
    }

    protected History<?> activeHistory() {
        if (histories.isEmpty()) {
            return null;
        }
        return histories.getLast();
    }

    public static class History<T> {
        private final Supplier<T> snapshotSupplier;
        private final Consumer<T> restoreConsumer;
        private final int limit;
        private final List<T> undoStack = new ArrayList<>();
        private final List<T> redoStack = new ArrayList<>();
        private boolean restoring;

        public History(Supplier<T> snapshotSupplier, Consumer<T> restoreConsumer, int limit) {
            this.snapshotSupplier = snapshotSupplier;
            this.restoreConsumer = restoreConsumer;
            this.limit = Math.max(1, limit);
        }

        public void capture() {
            if (restoring) {
                return;
            }
            undoStack.add(snapshotSupplier.get());
            if (undoStack.size() > limit) {
                undoStack.removeFirst();
            }
            redoStack.clear();
        }

        public void clear() {
            undoStack.clear();
            redoStack.clear();
        }

        public boolean undo() {
            if (undoStack.isEmpty()) {
                return false;
            }
            restoring = true;
            try {
                redoStack.add(snapshotSupplier.get());
                if (redoStack.size() > limit) {
                    redoStack.removeFirst();
                }
                restoreConsumer.accept(undoStack.removeLast());
                return true;
            } finally {
                restoring = false;
            }
        }

        public boolean redo() {
            if (redoStack.isEmpty()) {
                return false;
            }
            restoring = true;
            try {
                undoStack.add(snapshotSupplier.get());
                if (undoStack.size() > limit) {
                    undoStack.removeFirst();
                }
                restoreConsumer.accept(redoStack.removeLast());
                return true;
            } finally {
                restoring = false;
            }
        }

        public boolean isRestoring() {
            return restoring;
        }

        public boolean canUndo() {
            return !undoStack.isEmpty();
        }

        public boolean canRedo() {
            return !redoStack.isEmpty();
        }
    }
}

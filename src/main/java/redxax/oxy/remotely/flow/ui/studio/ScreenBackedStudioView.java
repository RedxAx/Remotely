package redxax.oxy.remotely.flow.ui.studio;

import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReScrollEvent;
import restudio.rescreen.platform.input.ReTextInputEvent;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.AnimatedWidget;

import java.util.ArrayList;
import java.util.List;

public class ScreenBackedStudioView implements ReSyncStudioView, StudioSelectorView, WorldStudioDocumentView {
    private final Screen host;
    private final Screen screen;
    private final boolean fullEditor;
    private boolean initialized;
    private boolean exposingHeaderButtons;

    public ScreenBackedStudioView(Screen host, Screen screen) {
        this(host, screen, false);
    }

    public ScreenBackedStudioView(Screen host, Screen screen, boolean fullEditor) {
        this.host = host;
        this.screen = screen;
        this.fullEditor = fullEditor;
    }

    public Screen screen() {
        return screen;
    }

    public boolean fullEditor() {
        return fullEditor;
    }

    public boolean initialized() {
        return initialized;
    }

    @Override
    public List<AnimatedWidget> headerButtons() {
        init();
        exposingHeaderButtons = true;
        if (screen instanceof StudioHeaderProvider provider) {
            return provider.getStudioHeaderButtons();
        }
        if (screen instanceof ReScreen reScreen) {
            reScreen.header().build();
            List<AnimatedWidget> buttons = new ArrayList<>();
            buttons.addAll(reScreen.header().leftButtons);
            buttons.addAll(reScreen.header().rightButtons);
            return buttons;
        }
        return ReSyncStudioView.super.headerButtons();
    }

    @Override
    public void init() {
        if (initialized) {
            return;
        }
        screen.resize(host.width, host.height);
        screen.init();
        if (screen instanceof ReScreen reScreen) {
            reScreen.header().visible(false);
        }
        initialized = true;
    }

    @Override
    public void selected() {
        init();
        screen.resize(host.width, host.height);
        if (screen instanceof StudioDocumentLifecycleScreen lifecycleScreen) {
            lifecycleScreen.studioDocumentSelected();
        }
        if (screen instanceof ReSyncStudioView view) {
            view.selected();
        }
    }

    @Override
    public void deselected() {
        if (screen instanceof StudioDocumentLifecycleScreen lifecycleScreen) {
            lifecycleScreen.studioDocumentDeselected();
        }
        if (screen instanceof ReSyncStudioView view) {
            view.deselected();
        }
    }

    @Override
    public void closed() {
        if (screen instanceof StudioDocumentLifecycleScreen lifecycleScreen) {
            lifecycleScreen.studioDocumentClosed();
        }
        screen.removed();
    }

    @Override
    public void resourceRenamed(String type, String oldId, String newId) {
        if (screen instanceof StudioResourceRenameAware view) {
            view.resourceRenamed(type, oldId, newId);
        }
    }

    @Override
    public void resize(int width, int height) {
        if (initialized) {
            screen.resize(width, height);
        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        init();
        List<AnimatedWidget> exposedHeaders = exposingHeaderButtons ? headerButtons() : List.of();
        List<Boolean> visibility = new ArrayList<>();
        for (AnimatedWidget widget : exposedHeaders) {
            visibility.add(widget.visible);
            widget.visible = false;
        }
        screen.renderHandler(context, mouseX, mouseY, delta);
        for (int i = 0; i < exposedHeaders.size(); i++) {
            exposedHeaders.get(i).visible = visibility.get(i);
        }
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        init();
        if (exposingHeaderButtons) {
            for (AnimatedWidget headerButton : headerButtons()) {
                if (headerButton != null && headerButton.visible && headerButton.isMouseOver(event.x(), event.y())) {
                    return false;
                }
            }
        }
        return Screen.dispatchMouseClicked(screen, event.retarget(screen, event.x(), event.y()));
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        return initialized && Screen.dispatchMouseReleased(screen, event.retarget(screen, event.x(), event.y()));
    }

    @Override
    public boolean mouseDragged(ReMouseEvent event) {
        return initialized && Screen.dispatchMouseDragged(screen, event.retarget(screen, event.x(), event.y(), event.deltaX(), event.deltaY()));
    }

    @Override
    public void mouseMoved(ReMouseEvent event) {
        if (initialized) {
            Screen.dispatchMouseMoved(screen, event.retarget(screen, event.x(), event.y()));
        }
    }

    @Override
    public boolean mouseScrolled(ReScrollEvent event) {
        return initialized && Screen.dispatchMouseScrolled(screen, event.retarget(screen, event.x(), event.y()));
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        return initialized && Screen.dispatchKeyPressed(screen, event.retarget(screen));
    }

    @Override
    public boolean textInput(ReTextInputEvent event) {
        return initialized && Screen.dispatchTextInput(screen, event.retarget(screen));
    }

    @Override
    public boolean hasActiveStudioSelector() {
        return screen instanceof StudioSelectorView view && view.hasActiveStudioSelector();
    }

    @Override
    public void refreshWorlds() {
        if (screen instanceof WorldStudioDocumentView view) {
            view.refreshWorlds();
        }
    }

    @Override
    public void handleWorldOperationResult(WorldOperationResult result) {
        if (screen instanceof WorldStudioDocumentView view) {
            view.handleWorldOperationResult(result);
        }
    }
}

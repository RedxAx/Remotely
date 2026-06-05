package redxax.oxy.remotely.flow.ui.studio;

import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.AnimatedWidget;

import java.util.ArrayList;
import java.util.List;

public class ScreenBackedStudioView implements ReSyncStudioView, StudioSelectorView, WorldStudioDocumentView {
    private final Screen host;
    private final Screen screen;
    private boolean initialized;
    private boolean exposingHeaderButtons;

    public ScreenBackedStudioView(Screen host, Screen screen) {
        this.host = host;
        this.screen = screen;
    }

    public Screen screen() {
        return screen;
    }

    @Override
    public List<AnimatedWidget> headerButtons() {
        init();
        exposingHeaderButtons = true;
        if (screen instanceof StudioHeaderProvider provider) {
            return provider.getStudioHeaderButtons();
        }
        if (screen instanceof ReScreen reScreen) {
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
        if (screen instanceof ReSyncStudioView view) {
            view.selected();
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        init();
        if (exposingHeaderButtons) {
            for (AnimatedWidget headerButton : headerButtons()) {
                if (headerButton != null && headerButton.visible && headerButton.isMouseOver(mouseX, mouseY)) {
                    return false;
                }
            }
        }
        return screen.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return initialized && screen.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return initialized && screen.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (initialized) {
            screen.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return initialized && screen.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return initialized && screen.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return initialized && screen.charTyped(chr, modifiers);
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

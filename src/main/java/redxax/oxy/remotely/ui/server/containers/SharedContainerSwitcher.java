package redxax.oxy.remotely.ui.server.containers;

import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.TabSwitchWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class SharedContainerSwitcher {
    private final List<String> iconOptions = new ArrayList<>();
    private final List<AnimatedWidget> registeredViews = new ArrayList<>();
    private final Container hostMainContainer;
    private final ReScreen hostScreen;
    private TabSwitchWidget widget;
    private IntConsumer onChange;

    public SharedContainerSwitcher(ReScreen hostScreen, Container mainContainer) {
        this.hostScreen = hostScreen;
        this.hostMainContainer = mainContainer;
    }

    public void register(String iconPath, AnimatedWidget view) {
        iconOptions.add(iconPath);
        registeredViews.add(view);
    }

    public void build() {
        int size = iconOptions.size() * 19;
        widget = new TabSwitchWidget.Builder().entranceCorner(AnimatedWidget.EntranceCorner.TOP_RIGHT).size(size, 18).iconMode(true).options(iconOptions).onChange(this::handleChange).build();
        hostScreen.addDrawableChild(widget);
    }

    private void handleChange(int i) {
        if (i < 0 || i >= registeredViews.size()) i = 0;
        AnimatedWidget target = registeredViews.get(i);
        hostMainContainer.scrollToWidget(target);
        if (onChange != null) onChange.accept(i);
    }

    public void setOnChange(IntConsumer consumer) {
        this.onChange = consumer;
    }

    public TabSwitchWidget getWidget() {
        return widget;
    }

    public void setPosition(int x, int y) {
        if (widget != null) widget.setPosition(x, y);
    }

    public void recreateButtons() {
        if (widget != null) widget.recreateButtons();
    }

    public void setActiveIndex(int index) {
        if (widget != null) widget.handleTabClick(index);
    }
}

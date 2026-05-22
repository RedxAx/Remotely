package redxax.oxy.remotely.adapters;

import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.widgets.AnimatedWidget;

import java.util.List;

public interface ICustomWidgetHolder {
    List<Widget> remotely$getWidgets();

    void remotely$addWidget(Widget widget);

    void remotely$clearWidgets();

    default void remotely$renderWidgets(IDrawContext context, int mouseX, int mouseY, float delta) {
        List<Widget> widgets = remotely$getWidgets();
        for (Widget widget : widgets) {
            widget.render(context, mouseX, mouseY, delta);
        }
        for (Widget widget : widgets) {
            if (widget instanceof AnimatedWidget animatedWidget) {
                animatedWidget.renderHintOverlay(context);
            }
        }
    }
}

package redxax.oxy.remotely.adapters;

import restudio.rescreen.ui.core.Widget;

public interface ICustomWidgetHolder {
    void remotely$addWidget(Widget widget);
    void remotely$clearWidgets();
}

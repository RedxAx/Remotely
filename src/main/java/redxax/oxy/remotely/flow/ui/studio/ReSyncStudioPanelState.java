package redxax.oxy.remotely.flow.ui.studio;

import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;

import java.util.function.Consumer;

public class ReSyncStudioPanelState {
    public static final int DEFAULT_WIDTH = 150;
    public static final int MIN_WIDTH = 150;
    public static final int MIN_ROW_WIDTH = 120;
    public static final int DEFAULT_PADDING = 6;
    public static final int FIELD_HEIGHT = 18;
    public static final int ROW_HEIGHT = 34;

    private int width = DEFAULT_WIDTH;
    private int padding = DEFAULT_PADDING;

    public ReSyncStudioPanelState width(int width) {
        this.width = Math.max(MIN_WIDTH, width);
        return this;
    }

    public ReSyncStudioPanelState padding(int padding) {
        this.padding = Math.max(0, padding);
        return this;
    }

    public int width() {
        return width;
    }

    public int padding() {
        return padding;
    }

    public int rowWidth() {
        return Math.max(MIN_ROW_WIDTH, width - padding * 2);
    }

    public int rowWidth(SidePanel panel) {
        if (panel == null) {
            return rowWidth();
        }
        return Math.max(MIN_ROW_WIDTH, panel.getDesiredWidth() - padding * 2);
    }

    public AnimatedButton hint(String label, int width) {
        AnimatedButton button = new AnimatedButton.Builder()
            .label(label)
            .size(width, 18)
            .centered(false)
            .active(false)
            .flat(true)
            .transparent(true)
            .animateElevation(false)
            .enableHoverColors(false)
            .entranceAnimation(false)
            .build();
        disableEntrance(button);
        return button;
    }

    public TextInputWidget input(String label, String value, Consumer<String> onChange) {
        TextInputWidget.Builder builder = new TextInputWidget.Builder()
            .text(value != null ? value : "")
            .placeholder(label)
            .forcePlaceholder(false)
            .size(rowWidth(), FIELD_HEIGHT);
        if (onChange != null) {
            builder.onChange(onChange);
        }
        TextInputWidget input = builder.build();
        disableEntrance(input);
        return input;
    }

    public TitledRowWidget row(String label, Widget widget, int width) {
        return row(label, widget, width, "");
    }

    public TitledRowWidget row(String label, Widget widget, int width, String description) {
        disableEntrance(widget);
        TitledRowWidget row = new TitledRowWidget.Builder()
            .title(label)
            .description(description)
            .size(width, ROW_HEIGHT)
            .gap(4)
            .addWidget(widget)
            .build();
        disableEntrance(row);
        return row;
    }

    public TitledRowWidget row(String label, Widget widget) {
        return row(label, widget, rowWidth());
    }

    public TitledRowWidget codeRow(String label, CodeEditorWidget editor, int width, int height) {
        return codeRow(label, editor, width, height, "");
    }

    public TitledRowWidget codeRow(String label, CodeEditorWidget editor, int width, int height, String description) {
        disableEntrance(editor);
        TitledRowWidget row = new TitledRowWidget.Builder()
            .title(label)
            .description(description)
            .size(width, height)
            .gap(4)
            .addWidget(editor)
            .build();
        disableEntrance(row);
        return row;
    }

    public AnimatedButton action(String label, int width, Runnable action) {
        AnimatedButton button = new AnimatedButton.Builder()
            .label(label)
            .size(width, 18)
            .accentType(ThemeManager.getAccent("nice"))
            .entranceAnimation(false)
            .onClick(action)
            .build();
        disableEntrance(button);
        return button;
    }

    public static void disableEntrance(Widget widget) {
        if (widget instanceof AnimatedWidget animated) {
            animated.entranceAnimationEnabled = false;
        }
    }
}

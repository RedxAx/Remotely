package redxax.oxy.remotely.ui.server.management;

import redxax.oxy.remotely.data.playerdata.PlayerEffect;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class PlayerEffectsSection {
    private final Supplier<AnimatedButton> labelFactory;
    private final BiFunction<String, String, MountableButtonWidget> emptyRowFactory;
    private final Function<PlayerEffect, MountableButtonWidget> effectRowFactory;

    public PlayerEffectsSection(Supplier<AnimatedButton> labelFactory, BiFunction<String, String, MountableButtonWidget> emptyRowFactory, Function<PlayerEffect, MountableButtonWidget> effectRowFactory) {
        this.labelFactory = labelFactory;
        this.emptyRowFactory = emptyRowFactory;
        this.effectRowFactory = effectRowFactory;
    }

    public void render(Container container, List<PlayerEffect> effects, String searchQuery, boolean dataAvailable, BooleanSupplier sectionFeedback) {
        float scroll = container.getScrollOffset();
        container.layout(new ManagedLayout()).columns(1).padding(4).verticalSpacing(4).enableSelecting(false).scrolling(true).backgroundDrawing(true);
        container.clearWidgets();
        container.addWidget(labelFactory.get());
        if (sectionFeedback.getAsBoolean()) {
            finish(container, scroll);
            return;
        }
        if (!dataAvailable) {
            container.addWidget(emptyRowFactory.apply("No Data", "Waiting for effect data."));
        } else if (effects.isEmpty()) {
            container.addWidget(emptyRowFactory.apply(searchQuery.isBlank() ? "No Effects" : "No Match", searchQuery.isBlank() ? "This player has no active effects." : "No effects match the current search."));
        } else {
            effects.stream().map(effectRowFactory).forEach(container::addWidget);
        }
        finish(container, scroll);
    }

    private void finish(Container container, float scroll) {
        container.updateWidgetPositions();
        container.setScrollOffset(scroll);
    }
}

package redxax.oxy.remotely.ui.server.management;

import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.ui.widgets.management.InventoryWidget;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.MountableButtonWidget;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongConsumer;

public final class PlayerInventorySections {
    private final UnifiedPlayer player;
    private final PlayerManagerController controller;
    private final BooleanSupplier canManipulate;
    private final Runnable interactionListener;
    private final LongConsumer refreshScheduler;
    private final Function<String, MountableButtonWidget> emptyRowFactory;

    public PlayerInventorySections(UnifiedPlayer player, PlayerManagerController controller, BooleanSupplier canManipulate, Runnable interactionListener, LongConsumer refreshScheduler, Function<String, MountableButtonWidget> emptyRowFactory) {
        this.player = player;
        this.controller = controller;
        this.canManipulate = canManipulate;
        this.interactionListener = interactionListener;
        this.refreshScheduler = refreshScheduler;
        this.emptyRowFactory = emptyRowFactory;
    }

    public void render(Container container, PlayerData data, InventoryWidget.Mode mode, String searchQuery, BooleanSupplier sectionFeedback) {
        container.layout(new ManagedLayout()).columns(1).padding(0).verticalSpacing(0).enableSelecting(false).scrolling(false).backgroundDrawing(false);
        container.clearWidgets();
        if (sectionFeedback.getAsBoolean()) {
            container.updateWidgetPositions();
            return;
        }
        if (data == null) {
            container.layout(new ManagedLayout()).columns(1).padding(4).verticalSpacing(4).enableSelecting(false).scrolling(true).backgroundDrawing(true);
            container.addWidget(emptyRowFactory.apply(mode == InventoryWidget.Mode.INVENTORY ? "Waiting for inventory data." : "Waiting for ender chest data."));
        } else {
            InventoryWidget widget = new InventoryWidget(0, 0, container.getEffectiveWidth(), container.getHeight(), player, controller, data, mode, canManipulate, interactionListener, refreshScheduler);
            widget.setSearchQuery(searchQuery);
            container.addWidget(widget);
        }
        container.updateWidgetPositions();
    }
}

package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.player.management.PlayerManagementService;
import redxax.oxy.remotely.data.player.management.PlayerManagementSnapshot;
import redxax.oxy.remotely.data.player.management.PlayerSection;
import redxax.oxy.remotely.data.player.management.PlayerSectionState;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.ui.server.PlayerManagementScreen;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.util.TimeUtils;

import java.util.Locale;

public class PlayerDataPopup extends PopupWidget {
    private final UnifiedPlayer player;
    private final PlayerManagerController controller;
    private final Screen parentScreen;
    private final Container summaryContainer;
    private PlayerManagementService.Subscription subscription;
    private AutoCloseable reSyncWatch;
    private boolean disposed;

    public PlayerDataPopup(Screen parent, UnifiedPlayer player, PlayerManagerController controller) {
        super(0, 0, 500, 300, player.getName() != null ? player.getName() : "Player");
        setLayer(500);
        this.player = player;
        this.controller = controller;
        this.parentScreen = parent;

        Builder builder = new Builder(getTitle()).size(500, 300).setResizable(true).setAntiOutOfBound(true);
        summaryContainer = new Container(0, 0, 460, 196);
        summaryContainer.layout(new ManagedLayout()).columns(2).padding(4).verticalSpacing(4).enableSelecting(false).scrolling(true).backgroundDrawing(true);
        summaryContainer.entranceAnimationEnabled = false;

        IconButton refresh = new IconButton.Builder().imagePath("reload.png").label("Refresh").onClick(() -> controller.getPlayerManagementService().refresh(player, true)).accentType(ThemeManager.getAccent("calm")).autoWidthOnTextChange(true).build();
        IconButton manager = new IconButton.Builder().imagePath("external.png").label("Player Manager").onClick(this::openPlayerManagementScreen).accentType(ThemeManager.getAccent("nice")).autoWidthOnTextChange(true).build();
        builder.addRow("", false, 20, refresh, manager);
        builder.addRow("", true, 196, summaryContainer);
        builder.onClose(this::closePopup);

        PopupWidget configured = builder.build();
        rows.addAll(configured.rows);
        setSize(configured.getWidth(), configured.getHeight());
        setPosition(configured.getX(), configured.getY());
        parent.addDrawableChild(this);
        show();

        showLoading();
        reSyncWatch = controller.watchPlayer(player.getUuid());
        subscription = controller.getPlayerManagementService().subscribe(player, this::renderSnapshot);
        onClose = this::closePopup;
    }

    private void renderSnapshot(PlayerManagementSnapshot snapshot) {
        if (disposed || snapshot == null) return;
        float scroll = summaryContainer.getScrollOffset();
        summaryContainer.clearWidgets();
        summaryContainer.columns(2);
        PlayerSectionState<PlayerData> overview = snapshot.section(PlayerSection.OVERVIEW);
        PlayerData data = snapshot.playerData();
        summaryContainer.addWidget(row("Status", player.isOnline() ? "Online" : "Offline", player.isOnline() ? "Live Player" : "Saved Player"));
        summaryContainer.addWidget(row("Source", sourceName(overview.source()), freshness(overview)));
        if (overview.status() == PlayerSectionState.Status.STALE || overview.status() == PlayerSectionState.Status.FAILED) {
            summaryContainer.addWidget(empty(overview.status() == PlayerSectionState.Status.STALE ? "Stale Data" : "Load Failed", overview.message().isBlank() ? "Refresh to try again." : overview.message()));
        }
        summaryContainer.addWidget(row("UUID", player.getUuid().toString(), player.isOp() ? "Operator" : "Player"));
        summaryContainer.addWidget(row("Access", player.getBan().getValue() != null ? "Banned" : "Allowed", player.isOp() ? "Operator Enabled" : "Standard Access"));
        if (data != null) {
            summaryContainer.addWidget(row("Health", data.health() >= 0 ? number(data.health()) + " HP" : "Unknown", data.food() >= 0 ? data.food() + " Food" : "No Food Data"));
            summaryContainer.addWidget(row("Experience", data.experienceLevel() >= 0 ? "Level " + data.experienceLevel() : "Unknown", data.totalExperience() >= 0 ? data.totalExperience() + " Total" : "No XP Data"));
            summaryContainer.addWidget(row("Game Mode", data.gameMode() != null ? title(data.gameMode()) : "Unknown", data.flying() ? "Flying" : data.fallFlying() ? "Gliding" : "Grounded"));
            summaryContainer.addWidget(row("Inventory", data.inventory().size() + " Items", snapshot.supports(PlayerSection.INVENTORY) ? controller.canEditPlayerInventory(player) ? "Editable" : "Read Only" : "Unavailable"));
            if (data.location() != null) summaryContainer.addWidget(row("Location", number(data.location().x()) + ", " + number(data.location().y()) + ", " + number(data.location().z()), title(data.location().dimension())));
        } else {
            summaryContainer.addWidget(empty(overview.status() == PlayerSectionState.Status.FAILED ? "Load Failed" : "No Player Data", overview.message().isBlank() ? "Refresh to try again." : overview.message()));
        }
        summaryContainer.updateWidgetPositions();
        summaryContainer.setScrollOffset(scroll);
    }

    private void showLoading() {
        summaryContainer.clearWidgets();
        summaryContainer.columns(1);
        summaryContainer.addWidget(empty("Loading Player", "Collecting available player data."));
        summaryContainer.updateWidgetPositions();
    }

    private MountableButtonWidget row(String title, String value, String detail) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title).description(value).hiddenText(detail).build();
        row.setHeight(34);
        row.setActive(false);
        row.entranceAnimationEnabled = false;
        return row;
    }

    private MountableButtonWidget empty(String title, String detail) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title).description(detail).build();
        row.setHeight(42);
        row.setActive(false);
        row.entranceAnimationEnabled = false;
        return row;
    }

    private String freshness(PlayerSectionState<?> state) {
        if (state.updatedAt() <= 0L) return state.status().name().replace('_', ' ');
        return TimeUtils.timeSense(state.updatedAt());
    }

    private String sourceName(String source) {
        if (source == null || source.isBlank()) return "Unavailable";
        return title(source);
    }

    private String title(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String text = value.replace("minecraft:", "").replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void openPlayerManagementScreen() {
        closePopup();
        PlayerManagementScreen screen = new PlayerManagementScreen(player, controller, parentScreen);
        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
            RemotelyClient.INSTANCE.getHost().setScreen(screen);
        } else {
            ScreenManager.getInstance().setScreen(screen);
        }
    }

    private void closePopup() {
        if (disposed) return;
        disposed = true;
        if (subscription != null) subscription.close();
        if (reSyncWatch != null) {
            try {
                reSyncWatch.close();
            } catch (Exception ignored) {
            }
        }
        hide();
    }
}

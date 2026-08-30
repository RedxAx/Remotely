package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.RemotelyCapabilityException;
import redxax.oxy.remotely.util.AsyncTools;
import redxax.oxy.remotely.util.TaskSchedulers;

import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.ScreenWindowWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.Duration;
import java.util.Locale;


import static restudio.rescreen.util.SoundUtils.playSound;

public class ServerNetworkSettingsController {
    private static final String NETWORK_TAB = "Network";
    private static final long LOAD_TIMEOUT_MS = 30000L;

    private final ReScreen parentScreen;
    private final PortManagementSettingsProvider portFeature;
    private final List<ServerModels.Allocation> allocationCache = new ArrayList<>();
    private volatile boolean allocationsLoaded;
    private volatile boolean loadingAllocations;
    private volatile boolean loadingAction;
    private volatile String loadError;
    private volatile long loadStartedAt;
    private volatile long loadRequestId;

    public ServerNetworkSettingsController(ReScreen parentScreen, PortManagementSettingsProvider portFeature) {
        this.parentScreen = parentScreen;
        this.portFeature = portFeature == null ? PortManagementSettingsProvider.unavailable("Network Feature Is Unavailable") : portFeature;
    }

    public List<Setting> getSettings() {
        if (!portFeature.available()) {
            Setting.Builder unavailable = new Setting.Builder("Server Network");
            unavailable.addRow("", new AnimatedButton.Builder().label("Network feature unavailable").active(false).hint(portFeature.unavailableReason()).build());
            return List.of(unavailable.build());
        }

        ensureAllocationsLoaded();

        Setting.Builder builder = new Setting.Builder("Server Network");
        AnimatedButton createPortButton = new AnimatedButton.Builder()
                .label("Add Port")
                .accentType(ThemeManager.getAccent("nice"))
                .onClick(this::createAllocation)
                .active(allocationsLoaded && !loadingAction)
                .build();
        builder.addRow("", createPortButton);

        if (!allocationsLoaded) {
            if (loadingAllocations) {
                builder.addRow("", new AnimatedButton.Builder().label("Loading Ports").active(false).build());
            } else if (!safe(loadError).isBlank()) {
                builder.addRow("", new AnimatedButton.Builder().label("Load Failed").active(false).build());
                builder.addRow("", new AnimatedButton.Builder()
                        .label("Retry Load")
                        .accentType(ThemeManager.getDefaultAccent())
                        .onClick(this::loadAllocations)
                        .build());
            } else {
                builder.addRow("", new AnimatedButton.Builder().label("Ports Unavailable").active(false).build());
            }
            return List.of(builder.build());
        }

        renderAllocations(builder, new ArrayList<>(allocationCache));
        return List.of(builder.build());
    }

    private void ensureAllocationsLoaded() {
        if (loadingAllocations && hasLoadTimedOut()) {
            loadingAllocations = false;
            allocationsLoaded = false;
            loadError = "Load timed out";
            updateLoadingState();
            ScreenManager.getInstance().execute(() -> new Notification("Load Failed", loadError, Notification.Type.ERROR));
            refreshNetwork();
        }
        if (!allocationsLoaded && !loadingAllocations && safe(loadError).isBlank()) {
            loadAllocations();
        }
    }

    private void loadAllocations() {
        if (loadingAllocations) {
            return;
        }
        long requestId = ++loadRequestId;
        loadError = null;
        loadingAllocations = true;
        loadStartedAt = System.currentTimeMillis();
        updateLoadingState();
        AsyncTools.withTimeout(portFeature.getAllocations(), TaskSchedulers.current(), Duration.ofSeconds(20))
                .whenComplete((allocations, error) -> ScreenManager.getInstance().execute(() -> {
                    if (requestId != loadRequestId) {
                        return;
                    }
                    if (error == null) {
                        allocationCache.clear();
                        if (allocations != null) {
                            allocationCache.addAll(allocations);
                        }
                        allocationsLoaded = true;
                        loadError = null;
                    } else {
                        allocationsLoaded = false;
                        loadError = sanitizeError(error);
                        new Notification("Load Failed", loadError, Notification.Type.ERROR);
                    }
                    loadingAllocations = false;
                    loadStartedAt = 0L;
                    updateLoadingState();
                    refreshNetwork();
                }));
    }

    private void renderAllocations(Setting.Builder builder, List<ServerModels.Allocation> allocations) {
        if (allocations.isEmpty()) {
            builder.addRow("", new AnimatedButton.Builder().label("No ports found").active(false).build());
            return;
        }

        allocations.sort(Comparator
                .comparing((ServerModels.Allocation allocation) -> !allocation.isDefault)
                .thenComparing(allocation -> allocation.port == null ? Integer.MAX_VALUE : allocation.port));

        for (ServerModels.Allocation allocation : allocations) {
            builder.addRow("", createAllocationWidget(allocation));
        }
    }

    private MountableButtonWidget createAllocationWidget(ServerModels.Allocation allocation) {
        SquareButtonWidget editButton = new SquareButtonWidget.Builder()
                .imagePath("edit.png")
                .onClick(() -> showEditPopup(allocation))
                .hint("Edit Notes")
                .size(18, 18)
                .build();

        SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
                .imagePath("delete.png")
                .onClick(() -> deleteAllocation(allocation))
                .hint("Delete Port")
                .accentType(ThemeManager.getAccent("danger"))
                .size(18, 18)
                .build();

        String host = displayHost(allocation);
        String title = host + ":" + (allocation.port == null ? "Unknown" : allocation.port);
        String description = allocation.isDefault ? "Primary Port" : "Additional Port";
        String notes = safe(allocation.notes);
        String hiddenText = notes.isBlank() ? "No Notes" : notes;

        MountableButtonWidget.Builder builder = new MountableButtonWidget.Builder(title)
                .description(description)
                .hiddenText(hiddenText)
                .addButton(editButton);

        if (!allocation.isDefault) {
            builder.addButton(deleteButton);
        }

        return builder.build();
    }

    private void showEditPopup(ServerModels.Allocation allocation) {
        Screen currentScreen = ScreenManager.getInstance().getCurrentScreen();
        if (currentScreen == null) {
            return;
        }

        PopupWidget.Builder builder = new PopupWidget.Builder("Edit Port")
                .pos(50, currentScreen.height / 5)
                .width(300)
                .setResizable(false);

        TextInputWidget notesField = new TextInputWidget.Builder()
                .placeholder("Notes")
                .size(170, 20)
                .build();
        notesField.setText(safe(allocation.notes));
        builder.addRow("Notes", notesField);

        builder.addTitleAction("Save", () -> {
            playSound(Sound.CREATE);
            updateAllocation(allocation, notesField.getText(), false);
            builder.getWidget().setVisible(false);
        }, PopupWidget.TitleActionRole.PRIMARY);

        PopupWidget popup = builder.build();
        currentScreen.addDrawableChild(popup);
        popup.show();
    }

    private void createAllocation() {
        if (loadingAction) {
            return;
        }
        loadingAction = true;
        updateLoadingState();
        new Notification("Adding Port", "Requesting New Port", Notification.Type.INFO);
        portFeature.createAllocation()
                .thenAccept(allocation -> ScreenManager.getInstance().execute(() -> {
                    if (allocation != null) {
                        upsertAllocation(allocation);
                        new Notification("Port Added", formatAllocation(allocation), Notification.Type.SUCCESS);
                    } else {
                        new Notification("Add Failed", "Server returned an error", Notification.Type.ERROR);
                    }
                    loadingAction = false;
                    updateLoadingState();
                    refreshNetwork();
                    loadAllocations();
                }))
                .exceptionally(e -> {
                    ScreenManager.getInstance().execute(() -> {
                        new Notification("Add Failed", sanitizeAllocationError(e), Notification.Type.ERROR);
                        loadingAction = false;
                        updateLoadingState();
                        refreshNetwork();
                    });
                    return null;
                });
    }

    private void updateAllocation(ServerModels.Allocation allocation, String notes, boolean primary) {
        if (allocation.id == null || loadingAction) {
            return;
        }
        loadingAction = true;
        updateLoadingState();
        portFeature.updateAllocation(allocation.id, notes, primary)
                .thenAccept(updated -> ScreenManager.getInstance().execute(() -> {
                    if (updated != null) {
                        if (primary) {
                            allocationCache.forEach(item -> item.isDefault = false);
                        }
                        upsertAllocation(updated);
                        new Notification("Port Updated", formatAllocation(updated), Notification.Type.SUCCESS);
                    } else {
                        new Notification("Update Failed", "Server returned an error", Notification.Type.ERROR);
                    }
                    loadingAction = false;
                    updateLoadingState();
                    refreshNetwork();
                    loadAllocations();
                }))
                .exceptionally(e -> {
                    ScreenManager.getInstance().execute(() -> {
                        new Notification("Update Failed", sanitizeError(e), Notification.Type.ERROR);
                        loadingAction = false;
                        updateLoadingState();
                        refreshNetwork();
                    });
                    return null;
                });
    }

    private void deleteAllocation(ServerModels.Allocation allocation) {
        if (allocation.id == null || allocation.isDefault || loadingAction) {
            return;
        }
        playSound(Sound.DELETE);
        loadingAction = true;
        updateLoadingState();
        portFeature.deleteAllocation(allocation.id)
                .thenRun(() -> ScreenManager.getInstance().execute(() -> {
                    allocationCache.removeIf(item -> allocation.id.equals(item.id));
                    new Notification("Port Deleted", formatAllocation(allocation), Notification.Type.SUCCESS);
                    loadingAction = false;
                    updateLoadingState();
                    refreshNetwork();
                    loadAllocations();
                }))
                .exceptionally(e -> {
                    ScreenManager.getInstance().execute(() -> {
                        new Notification("Delete Failed", sanitizeError(e), Notification.Type.ERROR);
                        loadingAction = false;
                        updateLoadingState();
                        refreshNetwork();
                    });
                    return null;
                });
    }

    private void upsertAllocation(ServerModels.Allocation allocation) {
        if (allocation == null || allocation.id == null) {
            return;
        }
        allocationCache.removeIf(existing -> allocation.id.equals(existing.id));
        allocationCache.add(allocation);
    }

    private void refreshNetwork() {
        ScreenManager screenManager = ScreenManager.getInstance();
        Screen current = screenManager.getCurrentScreen();
        List<SettingsScreen> targets = new ArrayList<>();
        if (current instanceof SettingsScreen settingsScreen) {
            targets.add(settingsScreen);
        }
        if (screenManager.getDesktopWindowsOverlay() != null) {
            for (ScreenWindowWidget window : screenManager.getDesktopWindowsOverlay().getWindows()) {
                if (window.getScreen() instanceof SettingsScreen settingsScreen && !targets.contains(settingsScreen)) {
                    targets.add(settingsScreen);
                }
            }
        }
        for (SettingsScreen settingsScreen : targets) {
            settingsScreen.refreshTab(NETWORK_TAB);
        }
    }

    private void updateLoadingState() {
        if (parentScreen != null) {
            parentScreen.setLoading(loadingAllocations || loadingAction);
        }
    }

    private boolean hasLoadTimedOut() {
        return loadStartedAt > 0L && System.currentTimeMillis() - loadStartedAt > LOAD_TIMEOUT_MS;
    }

    private String displayHost(ServerModels.Allocation allocation) {
        if (!safe(allocation.ipAlias).isBlank()) {
            return allocation.ipAlias;
        }
        if (!safe(allocation.ip).isBlank()) {
            return allocation.ip;
        }
        return "Unknown";
    }

    private String formatAllocation(ServerModels.Allocation allocation) {
        return displayHost(allocation) + ":" + (allocation.port == null ? "Unknown" : allocation.port);
    }

    private String sanitizeError(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        return message.length() > 180 ? message.substring(0, 180) + "..." : message;
    }

    private String sanitizeAllocationError(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof RemotelyCapabilityException failure
                && "remotely_web_allocation_limit_reached".equals(failure.code())) {
            return failure.getMessage();
        }
        String message = sanitizeError(throwable);
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.equals("browser capability failed with status 400")
                || normalized.contains("allocation limit")
                || normalized.contains("maximum network port")) {
            return "Maximum Network Ports Reached";
        }
        return message;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

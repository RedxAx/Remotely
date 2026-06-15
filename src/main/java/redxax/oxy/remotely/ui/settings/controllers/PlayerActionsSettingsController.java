package redxax.oxy.remotely.ui.settings.controllers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static restudio.rescreen.render.TextRenderer.tr;

public class PlayerActionsSettingsController {

    private final RebaseAPI api;
    private final Instance instance;
    private final Path playerActionsPath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private List<PlayerAction> loadedActions = new ArrayList<>();
    private boolean actionsLoaded;
    private boolean actionsLoading;
    private CompletableFuture<Void> directoryReady;
    private CompletableFuture<Void> saveChain = CompletableFuture.completedFuture(null);

    public PlayerActionsSettingsController(Instance instance) {
        this.instance = instance;
        this.api = RebaseApiFactory.get(instance);
        this.playerActionsPath = Path.of(instance.getPath(), "Remotely", "player-actions.json");
        this.directoryReady = ensureDirectory();
    }

    private CompletableFuture<Void> ensureDirectory() {
        Path dir = playerActionsPath.getParent();
        return api.fileExists(dir)
                .handle((exists, error) -> Boolean.TRUE.equals(exists))
                .thenCompose(exists -> exists ? CompletableFuture.completedFuture(null) : api.createDirectory(dir));
    }

    private void loadActions() {
        if (actionsLoaded || actionsLoading) {
            return;
        }
        actionsLoading = true;
        api.readFile(playerActionsPath).thenAccept(content -> {
            List<PlayerAction> loaded = new ArrayList<>();
            try {
                if (content != null && !content.isEmpty()) {
                    List<PlayerAction> parsed = gson.fromJson(content, new TypeToken<List<PlayerAction>>(){}.getType());
                    if (parsed != null) {
                        loaded = new ArrayList<>(parsed);
                    }
                }
            } catch (Exception e) {
                loaded = new ArrayList<>();
            }
            completeLoad(loaded, true);
        }).exceptionally(e -> {
            completeLoad(new ArrayList<>(), true);
            return null;
        });
    }

    private void completeLoad(List<PlayerAction> actions, boolean refresh) {
        ScreenManager.getInstance().execute(() -> {
            loadedActions = sanitizeActions(actions);
            actionsLoaded = true;
            actionsLoading = false;
            if (refresh) {
                refreshActions();
            }
        });
    }

    private List<PlayerAction> sanitizeActions(List<PlayerAction> actions) {
        List<PlayerAction> sanitized = new ArrayList<>();
        if (actions == null) {
            return sanitized;
        }
        for (PlayerAction action : actions) {
            if (action == null || action.name == null || action.icon == null || action.command == null) {
                continue;
            }
            sanitized.add(copyAction(action));
        }
        return sanitized;
    }

    private PlayerAction copyAction(PlayerAction action) {
        PlayerAction copy = new PlayerAction(action.name, action.icon, action.command);
        copy.uuid = action.uuid == null || action.uuid.isBlank() ? UUID.randomUUID().toString() : action.uuid;
        return copy;
    }

    private void saveActions(List<PlayerAction> actions, String successMessage) {
        List<PlayerAction> snapshot = sanitizeActions(actions);
        String json = gson.toJson(snapshot);
        loadedActions = new ArrayList<>(snapshot);
        actionsLoaded = true;
        refreshActions();

        synchronized (this) {
            saveChain = saveChain.handle((ignored, previousError) -> null)
                    .thenCompose(ignored -> directoryReady)
                    .thenCompose(ignored -> api.writeFile(playerActionsPath, json));
            saveChain.thenRun(() -> {
                PlayerManagerController.getOrCreate(instance).refreshPlayerActions();
                ScreenManager.getInstance().execute(() -> new Notification("Success", successMessage, Notification.Type.SUCCESS));
            }).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> new Notification("Error", "Failed To Save Actions: " + e.getMessage(), Notification.Type.ERROR));
                return null;
            });
        }
    }

    public List<Setting> getSettings() {
        loadActions();
        Setting.Builder builder = new Setting.Builder("Player Actions");

        IconButton createButton = new IconButton.Builder()
                .label("Create New")
                .imagePath("create.png")
                .onClick(() -> showPlayerActionPopup(null))
                .size(24 + tr.getWidth("Create New"), 20).accentType(ThemeManager.getAccent("nice")).build();
        createButton.setActive(actionsLoaded);
        builder.addRow("", false, false, 20, createButton);

        if (!actionsLoaded) {
            MountableButtonWidget loading = new MountableButtonWidget.Builder("Loading Actions")
                    .description("Reading Player Actions")
                    .iconPath("reload.png")
                    .build();
            loading.setActive(false);
            builder.addRow("", true, false, 30, loading);
            return List.of(builder.build());
        }

        for (int i = 0; i < loadedActions.size(); i++) {
            PlayerAction action = loadedActions.get(i);
            String actionUuid = action.uuid;

            SquareButtonWidget editButton = new SquareButtonWidget.Builder()
                    .imagePath("edit.png")
                    .onClick(() -> showPlayerActionPopup(actionUuid))
                    .build();

            SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
                    .imagePath("delete.png")
                    .onClick(() -> {
                        List<PlayerAction> currentActions = new ArrayList<>(loadedActions);
                        currentActions.removeIf(current -> actionUuid.equals(current.uuid));
                        saveActions(currentActions, "Action '" + action.name + "' Deleted.");
                    })
                    .accentType(ThemeManager.getAccent("danger"))
                    .build();

            MountableButtonWidget widget = new MountableButtonWidget.Builder(action.name)
                    .description(action.command)
                    .hiddenText(action.icon)
                    .addButton(editButton)
                    .addButton(deleteButton)
                    .build();
            builder.addRow("", true, false, 30, widget);
        }

        return List.of(builder.build());
    }

    private void refreshActions() {
        Screen currentScreen = ScreenManager.getInstance().getCurrentScreen();
        if (currentScreen instanceof SettingsScreen) {
            ((SettingsScreen) currentScreen).refreshTab("Player Actions");
        }
    }

    private void showPlayerActionPopup(String actionUuid) {
        PlayerAction actionToEdit = findAction(actionUuid);
        boolean isEditing = actionToEdit != null;
        
        String title = isEditing ? "Edit Player Action" : "Create Player Action";

        PopupWidget.Builder builder = new PopupWidget.Builder(title)
                .size(300, 150)
                .setResizable(false);

        TextInputWidget nameField = new TextInputWidget.Builder()
                .text(isEditing ? actionToEdit.name : "")
                .placeholder("Action Name")
                .build();

        TextInputWidget iconField = new TextInputWidget.Builder()
                .text(isEditing ? actionToEdit.icon : "minecraft.png")
                .placeholder("Icon Path (e.g., start.png)")
                .build();

        TextInputWidget commandField = new TextInputWidget.Builder()
                .text(isEditing ? actionToEdit.command : "")
                .placeholder("Command (you can reference $name and $uuid)")
                .build();

        builder.addTitleButton(() -> {
            String name = nameField.getText().trim();
            String icon = iconField.getText().trim();
            String command = commandField.getText().trim();

            if (name.isEmpty() || icon.isEmpty() || command.isEmpty()) {
                new Notification("Error", "All Fields Are Required.", Notification.Type.ERROR);
                return;
            }

            List<PlayerAction> currentActions = new ArrayList<>(loadedActions);
            if (isEditing) {
                if (!replaceAction(currentActions, actionUuid, name, icon, command)) {
                    new Notification("Error", "Action No Longer Exists.", Notification.Type.ERROR);
                    builder.getWidget().setVisible(false);
                    refreshActions();
                    return;
                }
            } else {
                currentActions.add(new PlayerAction(name, icon, command));
            }
            saveActions(currentActions, "Action '" + name + "' " + (isEditing ? "Updated." : "Created."));

            builder.getWidget().setVisible(false);
        }, isEditing ? "Save" : "Create", ThemeManager.getAccent("nice"));

        builder.addRow("Name", true, 20, nameField);
        builder.addRow("Icon", true, 20, iconField);
        builder.addRow("Command", true, 20, commandField);

        PopupWidget popup = builder.build();
        ScreenManager.getInstance().getCurrentScreen().addDrawableChild(popup);
        popup.show();
    }

    private boolean replaceAction(List<PlayerAction> actions, String actionUuid, String name, String icon, String command) {
        if (actionUuid == null || actions == null) {
            return false;
        }
        for (int i = 0; i < actions.size(); i++) {
            PlayerAction action = actions.get(i);
            if (action != null && actionUuid.equals(action.uuid)) {
                PlayerAction replacement = new PlayerAction(name, icon, command);
                replacement.uuid = actionUuid;
                actions.set(i, replacement);
                return true;
            }
        }
        return false;
    }

    private PlayerAction findAction(String actionUuid) {
        return findAction(loadedActions, actionUuid);
    }

    private PlayerAction findAction(List<PlayerAction> actions, String actionUuid) {
        if (actionUuid == null || actions == null) {
            return null;
        }
        for (PlayerAction action : actions) {
            if (action != null && actionUuid.equals(action.uuid)) {
                return action;
            }
        }
        return null;
    }
}

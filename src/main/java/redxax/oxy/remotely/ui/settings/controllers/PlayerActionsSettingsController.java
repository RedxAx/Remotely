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

import static restudio.rescreen.render.TextRenderer.tr;

public class PlayerActionsSettingsController {

    private final RebaseAPI api;
    private final Instance instance;
    private final Path playerActionsPath;
    private final Gson gson = new Gson();
    private List<PlayerAction> loadedActions = new ArrayList<>();

    public PlayerActionsSettingsController(Instance instance) {
        this.instance = instance;
        this.api = RebaseApiFactory.get(instance);
        this.playerActionsPath = Path.of(instance.getPath(), "Remotely", "player-actions.json");

        ensureDirectory();
    }

    private void ensureDirectory() {
        Path dir = playerActionsPath.getParent();
        api.fileExists(dir).thenAccept(exists -> {
           if (!exists) api.createDirectory(dir); 
        });
    }

    private void loadActions() {
        api.readFile(playerActionsPath).thenAccept(content -> {
            try {
                if (content != null && !content.isEmpty()) {
                    List<PlayerAction> loaded = gson.fromJson(content, new TypeToken<List<PlayerAction>>(){}.getType());
                    this.loadedActions = loaded != null ? loaded : new ArrayList<>();
                } else {
                    this.loadedActions = new ArrayList<>();
                }
            } catch (Exception e) {
                this.loadedActions = new ArrayList<>();
            }
            refreshActions();
        }).exceptionally(e -> {
            this.loadedActions = new ArrayList<>();
            return null;
        });
    }

    private void saveActions(List<PlayerAction> actions) {
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(actions);
        this.loadedActions = actions;
        api.writeFile(playerActionsPath, json).thenRun(() -> {
            PlayerManagerController.getOrCreate(instance).refreshPlayerActions();
        }).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> new Notification("Error", "Failed to save actions: " + e.getMessage(), Notification.Type.ERROR));
            return null;
        });
    }

    public List<Setting> getSettings() {
        loadActions();
        Setting.Builder builder = new Setting.Builder("Player Actions");

        IconButton createButton = new IconButton.Builder()
                .label("Create New")
                .imagePath("create.png")
                .onClick(() -> showPlayerActionPopup(-1))
                .size(24 + tr.getWidth("Create New"), 20).accentType(ThemeManager.getAccent("nice")).build();
        builder.addRow("", false, false, 20, createButton);

        for (int i = 0; i < loadedActions.size(); i++) {
            PlayerAction action = loadedActions.get(i);
            int index = i;

            SquareButtonWidget editButton = new SquareButtonWidget.Builder()
                    .imagePath("edit.png")
                    .onClick(() -> showPlayerActionPopup(index))
                    .build();

            SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
                    .imagePath("delete.png")
                    .onClick(() -> {
                        List<PlayerAction> currentActions = new ArrayList<>(loadedActions);
                        currentActions.remove(index);
                        saveActions(currentActions);
                        refreshActions();
                        new Notification("Success", "Action '" + action.name + "' deleted.", Notification.Type.SUCCESS);
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

    private void showPlayerActionPopup(int indexToEdit) {
        boolean isEditing = indexToEdit >= 0 && indexToEdit < loadedActions.size();
        PlayerAction actionToEdit = isEditing ? loadedActions.get(indexToEdit) : null;
        
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
                new Notification("Error", "All fields are required.", Notification.Type.ERROR);
                return;
            }

            List<PlayerAction> currentActions = new ArrayList<>(loadedActions);
            if (isEditing) {
                PlayerAction edited = currentActions.get(indexToEdit);
                edited.name = name;
                edited.icon = icon;
                edited.command = command;
                new Notification("Success", "Action '" + name + "' updated.", Notification.Type.SUCCESS);
            } else {
                currentActions.add(new PlayerAction(name, icon, command));
                new Notification("Success", "Action '" + name + "' created.", Notification.Type.SUCCESS);
            }
            saveActions(currentActions);

            builder.getWidget().setVisible(false);
            refreshActions();
        }, isEditing ? "Save" : "Create", ThemeManager.getAccent("nice"));

        builder.addRow("Name", true, 20, nameField);
        builder.addRow("Icon", true, 20, iconField);
        builder.addRow("Command", true, 20, commandField);

        PopupWidget popup = builder.build();
        ScreenManager.getInstance().getCurrentScreen().addDrawableChild(popup);
        popup.show();
    }
}

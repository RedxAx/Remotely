package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.data.managed.PlayerAction;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;

import java.util.List;

import static restudio.rescreen.render.TextRenderer.tr;

public class PlayerActionsSettingsController {

    private final RemotelyConfigManager configManager;

    public PlayerActionsSettingsController(RemotelyConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Custom Player Actions");

        IconButton createButton = new IconButton.Builder()
                .label("Create New")
                .imagePath("create.png")
                .onClick(() -> showPlayerActionPopup(null))
                .size(24 + tr.getWidth("Create New"), 20).accentType(ThemeManager.getAccent("nice")).build();
        builder.addRow("", false, false, 20, createButton);

        for (PlayerAction action : configManager.getPlayerActions()) {
            SquareButtonWidget editButton = new SquareButtonWidget.Builder()
                    .imagePath("edit.png")
                    .onClick(() -> showPlayerActionPopup(action))
                    .build();

            SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
                    .imagePath("delete.png")
                    .onClick(() -> {
                        List<PlayerAction> actions = configManager.getPlayerActions();
                        actions.remove(action);
                        configManager.savePlayerActions(actions);
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

    private void showPlayerActionPopup(PlayerAction actionToEdit) {
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
                new Notification("Error", "All fields are required.", Notification.Type.ERROR);
                return;
            }

            List<PlayerAction> actions = configManager.getPlayerActions();
            if (isEditing) {
                actionToEdit.name = name;
                actionToEdit.icon = icon;
                actionToEdit.command = command;
                new Notification("Success", "Action '" + name + "' updated.", Notification.Type.SUCCESS);
            } else {
                actions.add(new PlayerAction(name, icon, command));
                new Notification("Success", "Action '" + name + "' created.", Notification.Type.SUCCESS);
            }
            configManager.savePlayerActions(actions);

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
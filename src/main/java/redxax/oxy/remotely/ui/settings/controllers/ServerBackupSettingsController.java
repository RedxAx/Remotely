package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.Rebase;
import restudio.rebase.backup.BackupInfo;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;
import restudio.rescreen.util.TimeUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static restudio.rescreen.util.SoundUtils.playSound;

public class ServerBackupSettingsController {
    private final ReScreen parentScreen;
    private final Instance instance;

    public ServerBackupSettingsController(ReScreen parentScreen, Instance instance) {
        this.parentScreen = parentScreen;
        this.instance = instance;
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Server Backups");

        AnimatedButton createBackupButton = new AnimatedButton.Builder()
                .label("Create Server Backup")
                .accentType(restudio.rescreen.theme.ThemeManager.getAccent("nice"))
                .onClick(this::showCreateBackupPopup)
                .build();

        builder.addRow("", true, 20, createBackupButton);

        Rebase.get().getBackupManager().loadBackups();
        List<BackupInfo> allBackups = Rebase.get().getBackupManager().getAllBackups();
        List<BackupInfo> serverBackups = allBackups.stream().filter(b -> b.getInstanceId() != null && b.getInstanceId().equals(instance.getInstanceId())).sorted(Comparator.comparing(BackupInfo::getCreationTimestamp).reversed()).toList();

        for (BackupInfo backup : serverBackups) {
            builder.addRow("", true, false, 30, createBackupWidget(backup));
        }

        return List.of(builder.build());
    }

    private MountableButtonWidget createBackupWidget(BackupInfo backup) {
        SquareButtonWidget restoreButton = new SquareButtonWidget.Builder()
                .imagePath("reload.png")
                .onClick(() -> restoreBackup(backup))
                .hint("Restore this backup")
                .accentType(restudio.rescreen.theme.ThemeManager.getAccent("nice"))
                .size(18, 18).build();

        SquareButtonWidget extendButton = new SquareButtonWidget.Builder()
                .imagePath("history.png")
                .onClick(() -> showExtendPopup(backup))
                .hint("Extend the expiration time")
                .size(18, 18).build();

        SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
                .imagePath("delete.png")
                .onClick(() -> deleteBackup(backup))
                .hint("Permanently delete the backup")
                .accentType(restudio.rescreen.theme.ThemeManager.getAccent("danger"))
                .size(18, 18).build();

        String description = backup.getDescription();
        String created = "Created: " + TimeUtils.timeSense(backup.getCreationTimestamp());
        String expires = "Expires: " + TimeUtils.timeSense(backup.getExpiryTimestamp());

        return new MountableButtonWidget.Builder(description)
                .description(expires)
                .hiddenText(created)
                .addButton(restoreButton)
                .addButton(extendButton)
                .addButton(deleteButton)
                .build();
    }

    private void showCreateBackupPopup() {
        Screen currentScreen = ScreenManager.getInstance().getCurrentScreen();

        PopupWidget.Builder builder = new PopupWidget.Builder("Create Server Backup")
                .pos(50, currentScreen != null ? currentScreen.height / 5 : 150)
                .size(200, 300)
                .setResizable(false);

        TextInputWidget descriptionField = new TextInputWidget.Builder()
                .placeholder("e.g. 'Before major update'")
                .size(160, 20)
                .build();

        List<String> backupOptions = getBackupOptions(instance.getModLoader());
        TabSwitchWidget optionsSelector = new TabSwitchWidget.Builder()
                .options(backupOptions)
                .multiSelect(true)
                .selectedIndices(IntStream.range(0, backupOptions.size()).boxed().collect(Collectors.toList()))
                .size(160, 18)
                .build();

        TextInputWidget retentionField = new TextInputWidget.Builder()
                .text("7")
                .size(160, 20)
                .build();

        TextInputWidget customPathsField = new TextInputWidget.Builder()
                .placeholder("Additional paths")
                .size(160, 20)
                .hint("e.g. custom_worlds, scripts")
                .build();

        TextInputWidget backupPathField = new TextInputWidget.Builder()
                .placeholder("Override The Default Path")
                .size(160, 20)
                .hint("(Optional) E.g. \"C:\\Backups\\myServer\"")
                .build();

        builder.addRow("Description", true, 20, descriptionField);
        builder.addRow("Components", true, 18, optionsSelector);
        builder.addRow("Retention (Days)", true, 20, retentionField);
        builder.addRow("Custom Paths", true, 20, customPathsField);
        builder.addRow("Backup Location/Path", true, 20, backupPathField);

        builder.addTitleButton(() -> {
            playSound(Sound.CREATE);
            createServerBackup(optionsSelector, descriptionField, retentionField, customPathsField, backupPathField);
            builder.getWidget().setVisible(false);
        }, "Create Backup", restudio.rescreen.theme.ThemeManager.getAccent("nice"));

        PopupWidget popup = builder.build();
        if (currentScreen != null) {
            currentScreen.addDrawableChild(popup);
            popup.show();
        }
    }

    private List<String> getBackupOptions(ModLoader serverType) {
        List<String> options = new ArrayList<>();

        if (ModLoader.isBukkitBased(serverType)) {
            options.add("Plugins");
        }

        if (!ModLoader.isProxy(serverType)) {
            options.add("Worlds");
        }

        if (!ModLoader.isBukkitBased(serverType)) {
            options.add("Mods");
        }

        options.add("Configs");

        return options;
    }

    private void createServerBackup(TabSwitchWidget optionsSelector, TextInputWidget descriptionField,
                                  TextInputWidget retentionField, TextInputWidget customPathsField, TextInputWidget backupPathField) {
        String descriptionInput = descriptionField.getText();
        final String description = descriptionInput.isEmpty()
            ? "Server Backup | " + TimeUtils.formatDateTime(System.currentTimeMillis())
            : descriptionInput;

        int retentionDays;
        try {
            retentionDays = Integer.parseInt(retentionField.getText());
            if (retentionDays <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            new Notification("Error", "Invalid retention days", Notification.Type.ERROR);
            return;
        }

        boolean isRemote = instance.getBackendConfig() != null &&
                          !"LOCAL".equalsIgnoreCase(instance.getBackendConfig().type);

        if (instance.getState() == InstanceState.RUNNING || instance.getState() == InstanceState.STARTING) {
            new Notification("Server Running", "Server must be stopped to create backup", Notification.Type.WARN);
            return;
        }

        List<Path> pathsToBackup = new ArrayList<>();
        Path instancePath = Paths.get(instance.getPath());

        backupByOptions(optionsSelector, instancePath, pathsToBackup);

        String customPaths = customPathsField.getText();
        if (!customPaths.trim().isEmpty()) {
            addCustomPaths(customPaths, instancePath, pathsToBackup);
        }

        if (pathsToBackup.isEmpty()) {
            new Notification("Error", "Nothing selected to backup", Notification.Type.ERROR);
            return;
        }

        restudio.rescreen.config.Config.loading = true;

        String backupPath = backupPathField.getText().trim();

        Rebase.get().getBackupManager().createBackup(instance, description, pathsToBackup, Duration.ofDays(retentionDays), backupPath)
                .thenAccept(backup -> {
                    refreshBackups();
                    new Notification("Backup Created", description, Notification.Type.SUCCESS);
                })
                .exceptionally(e -> {
                    new Notification("Backup Failed", e.getMessage(), Notification.Type.ERROR);
                    return null;
                })
                .whenComplete((v, e) -> restudio.rescreen.config.Config.loading = false);
    }

    private void backupByOptions(TabSwitchWidget optionsSelector, Path instancePath,
                                List<Path> pathsToBackup) {
        List<String> options = getBackupOptions(instance.getModLoader());
        List<Integer> selected = optionsSelector.getSelectedIndices();

        for (int index : selected) {
            String option = options.get(index);
            switch (option) {
                case "Worlds":
                    addWorldDirectories(instancePath, pathsToBackup);
                    break;
                case "Plugins":
                    pathsToBackup.add(instancePath.resolve("plugins"));
                    addPluginConfigs(instancePath, pathsToBackup);
                    addPluginData(instancePath, pathsToBackup);
                    addSQLiteDatabases(instancePath, pathsToBackup);
                    break;
                case "Plugin Configs":
                    addPluginConfigs(instancePath, pathsToBackup);
                    break;
                case "Plugin Data":
                    addPluginData(instancePath, pathsToBackup);
                    break;
                case "SQLite Databases":
                    addSQLiteDatabases(instancePath, pathsToBackup);
                    break;
                case "Mods":
                    pathsToBackup.add(instancePath.resolve("mods"));
                    pathsToBackup.add(instancePath.resolve("config"));
                    break;
                case "Mod Configs":
                    pathsToBackup.add(instancePath.resolve("config"));
                    break;
                case "Server Configs":
                    addServerConfigs(instancePath, pathsToBackup);
                    break;
                case "server.properties":
                    pathsToBackup.add(instancePath.resolve("server.properties"));
                    break;
                case "Proxy Configs":
                    addProxyConfigs(instancePath, pathsToBackup);
                    break;
            }
        }
    }

    private void addWorldDirectories(Path instancePath, List<Path> pathsToBackup) {
        try {
            try (Stream<Path> stream = Files.list(instancePath)) {
                List<Path> worldDirs = stream.filter(Files::isDirectory).filter(this::isValidWorldDirectory).toList();
                pathsToBackup.addAll(worldDirs);
            }
        } catch (IOException ignored) {}
    }

    private boolean isValidWorldDirectory(Path dir) {
        try {
            Path levelDat = dir.resolve("level.dat");
            if (Files.exists(levelDat)) {
                return true;
            }
            return dir.getFileName().toString().toLowerCase().contains("world");
        } catch (Exception e) {
            return dir.getFileName().toString().toLowerCase().contains("world");
        }
    }

    private void addPluginConfigs(Path instancePath, List<Path> pathsToBackup) {
        Path pluginsDir = instancePath.resolve("plugins");
        if (Files.exists(pluginsDir)) {
            try (Stream<Path> stream = Files.list(pluginsDir)) {
                stream.filter(Files::isDirectory)
                        .map(p -> p.resolve("config.yml"))
                        .filter(Files::exists)
                        .forEach(pathsToBackup::add);
            } catch (IOException ignored) {}
        }
    }

    private void addPluginData(Path instancePath, List<Path> pathsToBackup) {
        Path pluginsDir = instancePath.resolve("plugins");
        if (Files.exists(pluginsDir)) {
            try (Stream<Path> stream = Files.list(pluginsDir)) {
                stream.filter(Files::isDirectory)
                        .flatMap(p -> {
                            try {
                                return Files.list(p).toList().stream().filter(this::isPluginDataFile);
                            } catch (IOException e) {
                                return Stream.empty();
                            }
                        })
                        .forEach(pathsToBackup::add);
            } catch (IOException ignored) {}
        }
    }

    private boolean isPluginDataFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".db") || name.endsWith(".sqlite") || name.endsWith(".json") ||
               name.endsWith(".yml") || name.equals("data") || name.equals("userdata");
    }

    private void addSQLiteDatabases(Path instancePath, List<Path> pathsToBackup) {
        try {
            try (Stream<Path> stream = Files.walk(instancePath)) {
                stream.filter(p -> !Files.isDirectory(p))
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".db"))
                        .forEach(pathsToBackup::add);
            }
        } catch (IOException ignored) {}
    }

    private void addServerConfigs(Path instancePath, List<Path> pathsToBackup) {
        ModLoader type = instance.getModLoader();

        if (type == ModLoader.PAPER || type == ModLoader.SPIGOT || type == ModLoader.BUKKIT) {
            pathsToBackup.add(instancePath.resolve("spigot.yml"));
            pathsToBackup.add(instancePath.resolve("bukkit.yml"));
        }
        if (type == ModLoader.PAPER) {
            pathsToBackup.add(instancePath.resolve("paper.yml"));
        }
        if (type == ModLoader.PURPUR) {
            pathsToBackup.add(instancePath.resolve("purpur.yml"));
        }
        if (type == ModLoader.LEAF) {
            pathsToBackup.add(instancePath.resolve("leaf.yml"));
        }
        if (type == ModLoader.VELOCITY) {
            pathsToBackup.add(instancePath.resolve("velocity.toml"));
        } else if (type == ModLoader.WATERFALL || type == ModLoader.BUNGEECORD) {
            pathsToBackup.add(instancePath.resolve("config.yml"));
            pathsToBackup.add(instancePath.resolve("server.yml"));
        }
    }

    private void addProxyConfigs(Path instancePath, List<Path> pathsToBackup) {
        ModLoader type = instance.getModLoader();

        if (type == ModLoader.VELOCITY) {
            pathsToBackup.add(instancePath.resolve("velocity.toml"));
        } else if (type == ModLoader.WATERFALL || type == ModLoader.BUNGEECORD) {
            pathsToBackup.add(instancePath.resolve("config.yml"));
            pathsToBackup.add(instancePath.resolve("server.yml"));
        }
    }

    private void addCustomPaths(String customPaths, Path instancePath, List<Path> pathsToBackup) {
        String[] paths = customPaths.split(",");
        for (String pathStr : paths) {
            pathStr = pathStr.trim();
            if (!pathStr.isEmpty()) {
                Path customPath = instancePath.resolve(pathStr);
                if (Files.exists(customPath)) {
                    pathsToBackup.add(customPath);
                }
            }
        }
    }

    private void refreshBackups() {
        Screen currentScreen = ScreenManager.getInstance().getCurrentScreen();
        if (currentScreen instanceof restudio.rescreen.ui.settings.SettingsScreen) {
            ((restudio.rescreen.ui.settings.SettingsScreen) currentScreen).refreshTab("Backups");
        }
    }

    private void restoreBackup(BackupInfo backupInfo) {
        if (instance.getState() == InstanceState.RUNNING || instance.getState() == InstanceState.STARTING) {
            new Notification("Server Running", "Server must be stopped to restore backup", Notification.Type.WARN);
            return;
        }

        Rebase.get().getBackupManager().restoreBackup(backupInfo)
                .thenRun(() -> ScreenManager.getInstance().execute(() -> {
                    new Notification("Backup Restored", backupInfo.getDescription() + " restored successfully", Notification.Type.SUCCESS);
                    refreshBackups();
                }))
                .exceptionally(e -> {
                    Throwable cause = e;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    Throwable finalCause = cause;
                    ScreenManager.getInstance().execute(() ->
                        new Notification("Restore Failed", finalCause.getMessage(), Notification.Type.ERROR));
                    return null;
                });
    }

    private void deleteBackup(BackupInfo backupInfo) {
        Rebase.get().getBackupManager().deleteBackup(backupInfo);
        new Notification("Backup Deleted", backupInfo.getDescription() + " has been deleted", Notification.Type.INFO);
        refreshBackups();
    }

    private void showExtendPopup(BackupInfo backupInfo) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Extend Backup Expiry").size(250, 80);
        TextInputWidget daysField = new TextInputWidget.Builder().placeholder("Days").size(100, 20).build();
        AnimatedButton extendButton = new AnimatedButton.Builder()
                .label("Extend")
                .onClick(() -> {
                    try {
                        int days = Integer.parseInt(daysField.getText());
                        Rebase.get().getBackupManager().extendBackup(backupInfo, Duration.ofDays(days));
                        new Notification("Backup Extended", "Expiry extended by " + days + " days", Notification.Type.SUCCESS);
                        builder.getWidget().setVisible(false);
                        refreshBackups();
                    } catch (NumberFormatException e) {
                        new Notification("Invalid Number", "Please enter a valid number of days", Notification.Type.ERROR);
                    }
                })
                .build();
        builder.addRow("Additional Days", false, 20, daysField, extendButton);
        PopupWidget popup = builder.build();
        Screen currentScreen = ScreenManager.getInstance().getCurrentScreen();
        if (currentScreen != null) {
            currentScreen.addDrawableChild(popup);
            popup.show();
        }
    }
}

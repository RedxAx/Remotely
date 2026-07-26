package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.Rebase;
import restudio.rebase.backup.BackupPathResolver;
import restudio.rebase.backup.BackupInfo;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.util.FileTransferProgress;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;
import restudio.rebase.settings.controllers.ReStudioBackupSettingsController;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.FileUtils;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;
import restudio.rescreen.util.TimeUtils;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static restudio.rescreen.util.SoundUtils.playSound;

public class ServerBackupSettingsController {
    private final ReScreen parentScreen;
    private final Instance instance;
    private ReStudioBackupSettingsController reStudioBackupController;
    private final Runnable backupRefreshListener = () -> ScreenManager.getInstance().execute(this::refreshBackups);
    private boolean backupRefreshListenerRegistered;

    public ServerBackupSettingsController(ReScreen parentScreen, Instance instance) {
        this.parentScreen = parentScreen;
        this.instance = instance;
    }

    public List<Setting> getSettings() {
        ensureRefreshListenerRegistered();
        boolean isReStudioBackend = instance.getBackendConfig() != null &&
                "RESTUDIO".equalsIgnoreCase(instance.getBackendConfig().type);

        if (isReStudioBackend) {
            if (reStudioBackupController == null) {
                reStudioBackupController = new ReStudioBackupSettingsController(parentScreen, instance);
            }
            return reStudioBackupController.getSettings();
        }

        Setting.Builder builder = new Setting.Builder("Server Backups");
        ConfigOption<Boolean> autoBackups = ConfigOption.<Boolean>builder("Auto Backups")
                .description("Create backups automatically for this server.")
                .bind(instance::isAutoBackupEnabled, instance::setAutoBackupEnabled)
                .defaultValue(false)
                .build();

        builder.addOption(autoBackups);
        builder.addOption(ConfigOption.<Boolean>builder("Allow Running Backups")
                .description("Allow manual and auto backups while this server is running.")
                .bind(instance::isAutoBackupWhileRunningEnabled, instance::setAutoBackupWhileRunningEnabled)
                .defaultValue(false)
                .build());
        builder.addOption(ConfigOption.<Integer>builder("Auto Backup Interval Minutes")
                .description("Set how often this server should auto backup.")
                .bind(instance::getAutoBackupIntervalMinutes, instance::setAutoBackupIntervalMinutes)
                .defaultValue(1440)
                .dependsOn(autoBackups)
                .build());
        builder.addOption(ConfigOption.<Integer>builder("Auto Backup Retention Days")
                .description("Set how long this server auto backups should be kept.")
                .bind(instance::getAutoBackupRetentionDays, instance::setAutoBackupRetentionDays)
                .defaultValue(3)
                .dependsOn(autoBackups)
                .build());

        AnimatedButton createBackupButton = new AnimatedButton.Builder()
                .label("Create Server Backup")
                .accentType(ThemeManager.getAccent("nice"))
                .onClick(this::showCreateBackupPopup)
                .build();

        builder.addRow("", createAutoBackupStatusWidget());
        builder.addRow("", createBackupButton);

        List<BackupInfo> allBackups = Rebase.get().getBackupManager().getAllBackups();
        List<BackupInfo> serverBackups = allBackups.stream().filter(b -> b.getInstanceId() != null && b.getInstanceId().equals(instance.getInstanceId())).sorted(Comparator.comparing(BackupInfo::getCreationTimestamp).reversed()).toList();
        for (BackupInfo backup : serverBackups) {
            builder.addRow("", createBackupWidget(backup));
        }

        return List.of(builder.build());
    }

    private MountableButtonWidget createBackupWidget(BackupInfo backup) {
        SquareButtonWidget restoreButton = new SquareButtonWidget.Builder()
                .imagePath("reload.png")
                .onClick(() -> restoreBackup(backup))
                .hint("Restore this backup")
                .accentType(ThemeManager.getAccent("nice"))
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
                .accentType(ThemeManager.getAccent("danger"))
                .size(18, 18).build();

        String description = backup.getDescription();
        String created = "Created: " + TimeUtils.timeSense(backup.getCreationTimestamp());
        String expires = "Expires: " + TimeUtils.timeSense(backup.getExpiryTimestamp());

        MountableButtonWidget.Builder builder = new MountableButtonWidget.Builder(description)
                .description(expires)
                .hiddenText(created)
                .addButton(restoreButton);
        if (backup.isStoredRemotely()) {
            builder.addButton(new SquareButtonWidget.Builder()
                    .imagePath("download.png")
                    .onClick(() -> downloadBackup(backup))
                    .hint("Download this backup file")
                    .size(18, 18).build());
        }
        return builder.addButton(extendButton).addButton(deleteButton).build();
    }

    private void showCreateBackupPopup() {
        Screen currentScreen = ScreenManager.getInstance().getCurrentScreen();

        PopupWidget.Builder builder = new PopupWidget.Builder("Create Server Backup")
                .pos(50, currentScreen != null ? currentScreen.height / 5 : 150)
                .width(200)
                .setResizable(false);

        TextInputWidget descriptionField = new TextInputWidget.Builder()
                .placeholder("e.g. 'Before major update'")
                .size(160, 20)
                .build();

        List<String> backupOptions = getBackupOptions();
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
                .hint(instance.getBackendConfig() != null && "SSH".equalsIgnoreCase(instance.getBackendConfig().type)
                        ? "(Optional) Remote path, e.g. \"~/.remotely/backups/myServer\""
                        : "(Optional) E.g. \"C:\\Backups\\myServer\"")
                .build();

        builder.addRow("Description", descriptionField);
        builder.addRow("Components", optionsSelector);
        builder.addRow("Retention (Days)", retentionField);
        builder.addRow("Custom Paths", customPathsField);
        builder.addRow("Backup Location/Path", backupPathField);

        builder.addTitleAction("Create", () -> {
            playSound(Sound.CREATE);
            createServerBackup(optionsSelector, descriptionField, retentionField, customPathsField, backupPathField);
            builder.getWidget().setVisible(false);
        }, PopupWidget.TitleActionRole.PRIMARY);

        PopupWidget popup = builder.build();
        if (currentScreen != null) {
            currentScreen.addDrawableChild(popup);
            popup.show();
        }
    }

    private List<String> getBackupOptions() {
        return BackupPathResolver.getServerOptions(instance);
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

        if ((instance.getState() == InstanceState.RUNNING && !instance.isAutoBackupWhileRunningEnabled()) || instance.getState() == InstanceState.STARTING) {
            new Notification("Server Running", "Server must be stopped to create backup", Notification.Type.WARN);
            return;
        }

        Set<Path> pathsToBackup = new LinkedHashSet<>();

        backupByOptions(optionsSelector, pathsToBackup);

        String customPaths = customPathsField.getText();
        if (!customPaths.trim().isEmpty()) {
            addCustomPaths(customPaths, pathsToBackup);
        }

        if (pathsToBackup.isEmpty()) {
            new Notification("Error", "Nothing selected to backup", Notification.Type.ERROR);
            return;
        }

        parentScreen.setLoading(true);

        String backupPath = backupPathField.getText().trim();
        boolean remoteStorage = instance.getBackendConfig() != null && "SSH".equalsIgnoreCase(instance.getBackendConfig().type);
        String initialStatus = remoteStorage ? "Preparing Remote Backup" : "Preparing Backup";
        String notificationMessage = remoteStorage ? "Creating Remote Backup" : "Creating Backup";
        FileTransferProgress progress = new FileTransferProgress(0L);
        AtomicReference<String> status = new AtomicReference<>(initialStatus);
        Notification notification = new Notification.Builder()
                .message(notificationMessage)
                .description(formatTransferDescription(initialStatus, progress, false))
                .type(Notification.Type.INFO)
                .loading(true)
                .autoSlideOut(false)
                .build();

        Rebase.get().getBackupManager().createBackup(
                        instance,
                        description,
                        List.copyOf(pathsToBackup),
                        Duration.ofDays(retentionDays),
                        backupPath,
                        newStatus -> {
                            status.set(newStatus);
                            ScreenManager.getInstance().execute(() -> notification.update()
                                    .description(formatTransferDescription(status.get(), progress, false))
                                    .progress(progress.getPercentage(), 100)
                                    .commit());
                        },
                        (transferred, total) -> {
                            progress.update(transferred, total);
                            ScreenManager.getInstance().execute(() -> notification.update()
                                    .description(formatTransferDescription(status.get(), progress, false))
                                    .progress(progress.getPercentage(), 100)
                                    .commit());
                        }
                )
                .thenAccept(backup -> ScreenManager.getInstance().execute(() -> {
                    refreshBackups();
                    notification.update()
                            .message("Backup Created")
                            .description(backup.isStoredRemotely() ? backup.getFilePath() : backup.getDescription())
                            .type(Notification.Type.SUCCESS)
                            .loading(false)
                            .autoSlideOut(true)
                            .action(null)
                            .image(null)
                            .commit();
                }))
                .exceptionally(e -> {
                    Throwable cause = unwrapThrowable(e);
                    ScreenManager.getInstance().execute(() -> notification.update()
                            .message("Backup Failed")
                            .description(resolveErrorMessage(cause))
                            .type(Notification.Type.ERROR)
                            .loading(false)
                            .autoSlideOut(true)
                            .action(null)
                            .image(null)
                            .commit());
                    return null;
                })
                .whenComplete((v, e) -> ScreenManager.getInstance().execute(() -> parentScreen.setLoading(false)));
    }

    private void backupByOptions(TabSwitchWidget optionsSelector, Set<Path> pathsToBackup) {
        List<String> options = getBackupOptions();
        List<Integer> selected = optionsSelector.getSelectedIndices();
        List<String> selectedOptions = new ArrayList<>();

        for (int index : selected) {
            if (index >= 0 && index < options.size()) {
                selectedOptions.add(options.get(index));
            }
        }
        pathsToBackup.addAll(BackupPathResolver.resolveServerPaths(instance, selectedOptions));
    }

    private void addCustomPaths(String customPaths, Set<Path> pathsToBackup) {
        pathsToBackup.addAll(BackupPathResolver.resolveCustomPaths(instance, customPaths));
    }

    private void refreshBackups() {
        Set<SettingsScreen> settingsScreens = new LinkedHashSet<>();
        ScreenManager screenManager = ScreenManager.getInstance();
        Screen currentScreen = screenManager.getCurrentScreen();
        if (currentScreen instanceof SettingsScreen settingsScreen) {
            settingsScreens.add(settingsScreen);
        }
        if (screenManager.getDesktopWindowsOverlay() != null) {
            for (ScreenWindowWidget window : screenManager.getDesktopWindowsOverlay().getWindows()) {
                if (window.getScreen() instanceof SettingsScreen settingsScreen) {
                    settingsScreens.add(settingsScreen);
                }
            }
        }
        for (SettingsScreen settingsScreen : settingsScreens) {
            settingsScreen.refreshTab("Backups");
        }
    }

    private void ensureRefreshListenerRegistered() {
        if (!backupRefreshListenerRegistered) {
            Rebase.get().getBackupManager().addChangeListener(backupRefreshListener);
            backupRefreshListenerRegistered = true;
        }
    }

    public void cleanup() {
        if (reStudioBackupController != null) {
            reStudioBackupController.cleanup();
        }
        if (backupRefreshListenerRegistered) {
            Rebase.get().getBackupManager().removeChangeListener(backupRefreshListener);
            backupRefreshListenerRegistered = false;
        }
    }

    private MountableButtonWidget createAutoBackupStatusWidget() {
        AutoBackupStatusWidget widget = new AutoBackupStatusWidget();
        widget.setActive(false);
        return widget;
    }

    private AutoBackupStatus getAutoBackupStatus() {
        if (!instance.isAutoBackupEnabled()) {
            return new AutoBackupStatus("Auto Backups Off", "Enable Auto Backups To Schedule Backups", null);
        }
        if (Rebase.get().getBackupManager().isAutoBackupActive(instance)) {
            long startedAt = Rebase.get().getBackupManager().getAutoBackupStartedAt(instance);
            String description = startedAt > 0L ? "Started " + TimeUtils.timeSense(startedAt) : "Creating Backup";
            String hiddenText = startedAt > 0L ? "At " + TimeUtils.formatDateTime(startedAt) : null;
            return new AutoBackupStatus("Auto Backup Running", description, hiddenText);
        }
        if (instance.getState() == InstanceState.STARTING || instance.getState() == InstanceState.INSTALLING) {
            return new AutoBackupStatus("Waiting For Server Ready", "Backups Resume After Startup Finishes", null);
        }
        if (instance.getState() == InstanceState.RUNNING && !instance.isAutoBackupWhileRunningEnabled()) {
            long nextRunAt = Rebase.get().getBackupManager().getNextAutoBackupRunAt(instance);
            String hiddenText = nextRunAt > 0L ? "Due " + TimeUtils.formatDateTime(nextRunAt) : null;
            return new AutoBackupStatus("Waiting For Server Stop", "Enable Allow Running Backups To Run While Online", hiddenText);
        }
        long nextRunAt = Rebase.get().getBackupManager().getNextAutoBackupRunAt(instance);
        if (nextRunAt <= 0L || nextRunAt <= System.currentTimeMillis()) {
            return new AutoBackupStatus("Next Backup Due", "Scheduled Now", null);
        }
        return new AutoBackupStatus(
                "Next Backup " + TimeUtils.timeSense(nextRunAt),
                "At " + TimeUtils.formatDateTime(nextRunAt),
                "State " + instance.getState().name()
        );
    }

    private record AutoBackupStatus(String title, String description, String hiddenText) {
    }

    private final class AutoBackupStatusWidget extends MountableButtonWidget {
        private AutoBackupStatusWidget() {
            super("", null, null, new CopyOnWriteArrayList<>(), null);
        }

        @Override
        public void renderWidget(IDrawContext context, int mouseX, int mouseY, float delta) {
            AutoBackupStatus status = getAutoBackupStatus();
            setName(status.title());
            setDescription(status.description());
            setHiddenText(status.hiddenText());
            super.renderWidget(context, mouseX, mouseY, delta);
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

    private void downloadBackup(BackupInfo backupInfo) {
        if (!backupInfo.isStoredRemotely()) {
            new Notification("Download Unavailable", "Only Remote Backups Can Be Downloaded", Notification.Type.WARN);
            return;
        }
        Path destination = resolveDownloadDestination(backupInfo);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        FileTransferProgress progress = new FileTransferProgress(0L);
        Notification notification = new Notification.Builder()
                .message("Downloading Backup")
                .description(formatTransferDescription("Preparing Download", progress, true))
                .type(Notification.Type.INFO)
                .loading(true)
                .autoSlideOut(false)
                .image(Identifier.icon("download.png"))
                .action(() -> cancelled.set(true))
                .build();

        Rebase.get().getBackupManager().downloadBackup(backupInfo, destination, (transferred, total) -> {
            progress.update(transferred, total);
            ScreenManager.getInstance().execute(() -> notification.update()
                    .description(formatTransferDescription("Downloading Backup", progress, true))
                    .progress(progress.getPercentage(), 100)
                    .commit());
        }, cancelled::get).thenAccept(path -> ScreenManager.getInstance().execute(() -> notification.update()
                .message("Backup Downloaded")
                .description(path.getFileName() + "\nClick To Open")
                .type(Notification.Type.SUCCESS)
                .loading(false)
                .autoSlideOut(false)
                .image(Identifier.icon("download.png"))
                .action(() -> openDownloadedBackup(path))
                .commit())).exceptionally(e -> {
            Throwable cause = unwrapThrowable(e);
            ScreenManager.getInstance().execute(() -> notification.update()
                    .message(cancelled.get() || cause instanceof CancellationException ? "Download Cancelled" : "Download Failed")
                    .description(cancelled.get() || cause instanceof CancellationException ? backupInfo.getDescription() : resolveErrorMessage(cause))
                    .type(cancelled.get() || cause instanceof CancellationException ? Notification.Type.WARN : Notification.Type.ERROR)
                    .loading(false)
                    .autoSlideOut(true)
                    .image(cancelled.get() || cause instanceof CancellationException ? Identifier.icon("download.png") : null)
                    .action(null)
                    .commit());
            return null;
        });
    }

    private Path resolveDownloadDestination(BackupInfo backupInfo) {
        return normalizeDownloadDestination(resolveDownloadsDir().resolve(getSuggestedBackupFileName(backupInfo)), backupInfo);
    }

    private Path resolveDownloadsDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Paths.get(System.getProperty("user.home"));
        if (os.contains("win")) {
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile != null && !userProfile.isBlank()) {
                Path path = Paths.get(userProfile).resolve("Downloads");
                if (Files.isDirectory(path)) {
                    return path;
                }
            }
            Path path = home.resolve("Downloads");
            if (Files.isDirectory(path)) {
                return path;
            }
            return home;
        }
        if (os.contains("mac")) {
            Path path = home.resolve("Downloads");
            return Files.isDirectory(path) ? path : home;
        }
        try {
            Path xdg = home.resolve(".config").resolve("user-dirs.dirs");
            if (Files.isRegularFile(xdg)) {
                for (String line : Files.readAllLines(xdg)) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("XDG_DOWNLOAD_DIR")) {
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String value = trimmed.substring(eq + 1).trim().replace("\"", "").replace("$HOME", home.toString());
                    Path path = Paths.get(value);
                    if (Files.isDirectory(path)) {
                        return path;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        Path path = home.resolve("Downloads");
        return Files.isDirectory(path) ? path : home;
    }

    private Path normalizeDownloadDestination(Path selectedPath, BackupInfo backupInfo) {
        String fileName = selectedPath.getFileName() == null ? "backup" : selectedPath.getFileName().toString();
        String suffix = getPreferredBackupSuffix(backupInfo);
        if (!suffix.isEmpty() && !fileName.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
            selectedPath = selectedPath.resolveSibling(fileName + suffix);
        }
        return selectedPath.toAbsolutePath().normalize();
    }

    private String getSuggestedBackupFileName(BackupInfo backupInfo) {
        String path = backupInfo.getFilePath();
        if (path == null || path.isBlank()) {
            return backupInfo.getDescription() + getPreferredBackupSuffix(backupInfo);
        }
        String normalized = path.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    private String getPreferredBackupSuffix(BackupInfo backupInfo) {
        String fileName = getSuggestedBackupFileName(backupInfo).toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".tar.gz")) {
            return ".tar.gz";
        }
        if (fileName.endsWith(".zip")) {
            return ".zip";
        }
        return backupInfo.isStoredRemotely() ? ".tar.gz" : ".zip";
    }

    private void openDownloadedBackup(Path path) {
        try {
            FileUtils.openAssociated(path);
        } catch (Exception e) {
            new Notification("Open Failed", resolveErrorMessage(e), Notification.Type.ERROR);
        }
    }

    private String formatTransferDescription(String status, FileTransferProgress progress, boolean cancellable) {
        List<String> lines = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            lines.add(status);
        }
        if (progress != null && (progress.getTotalBytes() > 0 || progress.getTransferredBytes() > 0)) {
            lines.add(progress.formatProgress());
        }
        if (cancellable) {
            lines.add("Click To Cancel");
        }
        return String.join("\n", lines);
    }

    private Throwable unwrapThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String resolveErrorMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank() ? "Unexpected Error" : message;
    }

    private void showExtendPopup(BackupInfo backupInfo) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Extend Backup Expiry").width(250);
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
        builder.addRow(new PopupWidget.PopupRow.Builder("Additional Days", daysField).contentWidth().build());
        builder.addTitleAction("Extend", () -> extendButton.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        PopupWidget popup = builder.build();
        Screen currentScreen = ScreenManager.getInstance().getCurrentScreen();
        if (currentScreen != null) {
            currentScreen.addDrawableChild(popup);
            popup.show();
        }
    }
}

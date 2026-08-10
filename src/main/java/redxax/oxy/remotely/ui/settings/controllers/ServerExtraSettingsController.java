package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerExtraSettingsController {

    private final Instance instance;
    private final RebaseAPI api;
    private final List<String> existingFiles;
    private final List<String> configurationFiles;

    private static final List<String> CONFIG_FILES = List.of(
        "server.properties",
        "velocity.toml",
        "config.yml",
        "bukkit.yml",
        "spigot.yml",
        "config/paper-global.yml",
        "config/paper-world-defaults.yml",
        "purpur.yml"
    );

    public ServerExtraSettingsController(Instance instance, List<String> existingFiles) {
        this(instance, existingFiles, CONFIG_FILES);
    }

    public ServerExtraSettingsController(Instance instance, List<String> existingFiles, Collection<String> configurationFiles) {
        this.instance = instance;
        this.api = RebaseApiFactory.get(instance);
        this.existingFiles = existingFiles != null ? existingFiles : new ArrayList<>();
        LinkedHashSet<String> files = new LinkedHashSet<>(CONFIG_FILES);
        if (configurationFiles != null) {
            files.addAll(configurationFiles);
        }
        this.configurationFiles = List.copyOf(files);
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Configuration Files");

        List<MountableButtonWidget> fileButtons = new ArrayList<>();
        for (String fileName : configurationFiles) {
            if (existingFiles.contains(fileName) || existingFiles.contains(Path.of(fileName).getFileName().toString())) {
                fileButtons.add(createFileEditButton(fileName));
            }
        }

        if (fileButtons.isEmpty()) {
            builder.addRow("No extra configuration files found.");
        } else {
            for(MountableButtonWidget button : fileButtons) {
                builder.addRow("", button);
            }
        }

        return List.of(builder.build());
    }

    private MountableButtonWidget createFileEditButton(String fileName) {
        return new MountableButtonWidget(fileName, "Edit " + fileName, null, new CopyOnWriteArrayList<>(), () -> openEditorPopupFor(fileName));
    }

    private void openEditorPopupFor(String fileName) {
        Path filePath = Path.of(instance.getPath()).resolve(fileName);

        api.readFile(filePath).exceptionally(t -> "Error loading file: " + t.getMessage())
            .thenAccept(content -> ScreenManager.getInstance().execute(() -> {

                int padding = 20;
                int popupWidth = ScreenManager.currentScreen.width - (padding * 2);
                int popupHeight = ScreenManager.currentScreen.height - (padding * 2);

                int editorWidth = popupWidth - 12;
                int editorHeight = popupHeight - 16 - 18;

                CodeEditorWidget editor = new CodeEditorWidget(0, 0, editorWidth, editorHeight);
                editor.setLanguage(detectLanguage(fileName));
                editor.setText(content);
                editor.setMonospace(true);

                PopupWidget popup = new PopupWidget(padding, padding, popupWidth, popupHeight, "Editing: " + fileName) {
                    @Override
                    public void tick() {
                        super.tick();
                        if (isResizing && !rows.isEmpty() && !rows.getFirst().getWidgets().isEmpty()) {
                            Widget w = rows.getFirst().getWidgets().getFirst();
                            int newEditorHeight = this.getHeight() - 16 - 6 * 2 - (rows.getFirst().id.isEmpty() ? 0 : 12) - 8;
                            int newEditorWidth = this.getWidth() - 6 * 2;
                            if (w.getWidth() != newEditorWidth) w.setWidth(newEditorWidth);
                            if (w.getHeight() != newEditorHeight) w.setHeight(newEditorHeight);
                        }
                    }
                };

                popup.resizable = true;
                popup.addRow(new PopupWidget.PopupRow.Builder("", editor).minHeight(editorHeight - 8).build());

                popup.addTitleAction("Save", () -> {
                        String newContent = editor.getText();
                        api.writeFile(filePath, newContent).thenRun(() ->
                            ScreenManager.getInstance().execute(() -> new Notification("File Saved", fileName + " has been saved.", Notification.Type.SUCCESS))
                        ).exceptionally(ex -> {
                            ScreenManager.getInstance().execute(() -> new Notification("Save Failed", ex.getMessage(), Notification.Type.ERROR));
                            return null;
                        });
                    }, PopupWidget.TitleActionRole.PRIMARY);

                ScreenManager.currentScreen.addDrawableChild(popup);
                popup.show();
            }));
    }

    private String detectLanguage(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".yml") || n.endsWith(".yaml")) return "yaml";
        if (n.endsWith(".toml")) return "toml";
        if (n.endsWith(".properties")) return "properties";
        if (n.endsWith(".json")) return "json";
        if (n.endsWith(".sk")) return "skript";
        return "plain";
    }
}

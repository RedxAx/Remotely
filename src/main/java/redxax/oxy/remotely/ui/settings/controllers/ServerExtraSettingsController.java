package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ServerExtraSettingsController {

    private final Instance instance;
    private final RebaseAPI api;

    private static final List<String> CONFIG_FILES = List.of(
            "server.properties",
            "bukkit.yml",
            "spigot.yml",
            "paper-global.yml",
            "paper-world-defaults.yml",
            "purpur.yml"
    );

    public ServerExtraSettingsController(Instance instance) {
        this.instance = instance;
        this.api = RebaseApiFactory.get(instance);
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Configuration Files");
        Path instancePath = Path.of(instance.getPath());

        List<String> existingFiles;
        try {
            existingFiles = api.listDirectory(instancePath).join().stream().map(RebaseAPI.FileEntry::toString).toList();
        } catch (Exception e) {
            builder.addRow("Could not list files: " + e.getMessage(), true, 20);
            return List.of(builder.build());
        }

        List<MountableButtonWidget> fileButtons = new ArrayList<>();
        for (String fileName : CONFIG_FILES) {
            if (existingFiles.contains(fileName)) {
                fileButtons.add(createFileEditButton(fileName));
            }
        }

        if (fileButtons.isEmpty()) {
            builder.addRow("No extra configuration files found.", true, 20);
        } else {
            for(MountableButtonWidget button : fileButtons) {
                builder.addRow("", true, false, 30, button);
            }
        }

        return List.of(builder.build());
    }

    private MountableButtonWidget createFileEditButton(String fileName) {
        return new MountableButtonWidget.Builder(fileName)
                .description("Edit " + fileName)
                .onClick(() -> openEditorPopupFor(fileName))
                .build();
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
                            if (isResizing && !rows.isEmpty() && !rows.getFirst().widgets.isEmpty()) {
                                Widget w = rows.getFirst().widgets.getFirst();
                                int newEditorHeight = this.getHeight() - 16 - 6 * 2 - (rows.getFirst().id.isEmpty() ? 0 : 12) - 8;
                                int newEditorWidth = this.getWidth() - 6 * 2;
                                if (w.getWidth() != newEditorWidth) w.setWidth(newEditorWidth);
                                if (w.getHeight() != newEditorHeight) w.setHeight(newEditorHeight);
                            }
                        }
                    };

                    popup.resizable = true;
                    popup.addRow("", List.of(editor), editorHeight - 8, true);

                    popup.titleButtons.add(new AnimatedButton.Builder()
                            .onClick(() -> {
                                String newContent = editor.getText();
                                api.writeFile(filePath, newContent).thenRun(() ->
                                        ScreenManager.getInstance().execute(() -> new Notification("File Saved", fileName + " has been saved.", Notification.Type.SUCCESS))
                                ).exceptionally(ex -> {
                                    ScreenManager.getInstance().execute(() -> new Notification("Save Failed", ex.getMessage(), Notification.Type.ERROR));
                                    return null;
                                });
                            })
                            .accentType(ThemeManager.getAccent("nice")).animateElevation(false).size(12, 8).hint("Save").build());

                    ScreenManager.currentScreen.addDrawableChild(popup);
                    popup.show();
                }));
    }

    private String detectLanguage(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".yml") || n.endsWith(".yaml")) return "yaml";
        if (n.endsWith(".properties")) return "properties";
        if (n.endsWith(".json")) return "json";
        return "plain";
    }
}
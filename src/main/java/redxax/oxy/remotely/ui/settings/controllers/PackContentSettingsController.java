package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.packcontent.GlyphPreviewMode;
import redxax.oxy.remotely.packcontent.PackContentDiagnostic;
import redxax.oxy.remotely.packcontent.PackContentRegistry;
import redxax.oxy.remotely.packcontent.RemotelyPackContentIntegration;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class PackContentSettingsController {
    private final RemotelyConfigManager configManager;

    public PackContentSettingsController(RemotelyConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Pack Content");
        builder.addOption(ConfigOption.<GlyphPreviewMode>builder("Glyph Previews")
                .description("Preview custom glyph tags in editors and terminals.")
                .options(Arrays.asList(GlyphPreviewMode.values()))
                .display(GlyphPreviewMode::displayName)
                .bind(configManager::getGlyphPreviewMode, configManager::setGlyphPreviewMode)
                .defaultValue(GlyphPreviewMode.INLINE_HOVER)
                .build());
        builder.addRow(new PopupWidget.PopupRow.Builder("Actions", new AnimatedButton.Builder()
                .label("Refresh")
                .onClick(this::refreshPackContent)
                .build()).contentWidth().build());
        List<PackContentRegistry.ProviderStatus> statuses = PackContentRegistry.get().statuses();
        if (statuses.isEmpty()) {
            builder.addRow("", new MountableButtonWidget.Builder("No Pack Providers")
                    .description("Open Server Workspace Then Refresh")
                    .build());
        } else {
            for (PackContentRegistry.ProviderStatus status : statuses) {
                builder.addRow("", providerWidget(status));
            }
        }
        List<PackContentDiagnostic> diagnostics = PackContentRegistry.get().diagnostics().stream().distinct().toList();
        for (PackContentDiagnostic diagnostic : diagnostics) {
            builder.addRow("", diagnosticWidget(diagnostic));
        }
        return List.of(builder.build());
    }

    private void refreshPackContent() {
        new Notification("Refreshing Pack Content", "Scanning Server Pack Providers", Notification.Type.INFO);
        RemotelyPackContentIntegration.refreshAllInstances().thenAccept(count ->
                ScreenManager.getInstance().execute(() -> new Notification("Pack Content Refreshed", count + " Workspaces", Notification.Type.SUCCESS))
        ).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> new Notification("Refresh Failed", e.getMessage(), Notification.Type.ERROR));
            return null;
        });
    }

    private MountableButtonWidget providerWidget(PackContentRegistry.ProviderStatus status) {
        String description = rootName(status.root()) + " - " + status.glyphCount() + " Glyphs";
        if (status.frameCount() > 0) {
            description += " - " + status.frameCount() + " Frames";
        }
        if (status.diagnosticCount() > 0) {
            description += " - " + status.diagnosticCount() + " Issues";
        }
        return new MountableButtonWidget.Builder(status.workspaceName())
                .hiddenText(status.providerName())
                .description(description)
                .build();
    }

    private MountableButtonWidget diagnosticWidget(PackContentDiagnostic diagnostic) {
        return new MountableButtonWidget.Builder("Issue")
                .hiddenText(diagnostic.providerId())
                .description(fileName(diagnostic.sourceFile()) + " - " + diagnostic.message())
                .build();
    }

    private String rootName(Path path) {
        if (path == null) {
            return "Not Detected";
        }
        return path.getFileName() != null ? path.getFileName().toString() : path.toString();
    }

    private String fileName(Path path) {
        return path != null && path.getFileName() != null ? path.getFileName().toString() : "Unknown";
    }
}

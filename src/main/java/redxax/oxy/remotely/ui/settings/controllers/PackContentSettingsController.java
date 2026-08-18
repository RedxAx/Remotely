package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigStore;
import redxax.oxy.remotely.packcontent.GlyphPreviewMode;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.util.Notification;

import java.util.Arrays;
import java.util.List;

public class PackContentSettingsController {
    private final RemotelyConfigStore configManager;
    private final PackContentSettingsProvider provider;

    public PackContentSettingsController(RemotelyConfigStore configManager, PackContentSettingsProvider provider) {
        this.configManager = configManager;
        this.provider = provider == null ? PackContentSettingsProvider.unavailable("Pack Content Refresh Is Unavailable") : provider;
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
        PackContentSettingsProvider.Availability refresh = provider.refreshAvailability();
        builder.addRow(new PopupWidget.PopupRow.Builder("Actions", new AnimatedButton.Builder()
                .label("Refresh")
                .hint(refresh.reason())
                .active(refresh.available())
                .onClick(this::refreshPackContent)
                .build()).contentWidth().build());
        List<PackContentSettingsProvider.ProviderStatus> statuses = provider.statuses();
        if (statuses.isEmpty()) {
            builder.addRow("", new MountableButtonWidget.Builder("No Pack Providers")
                    .description(refresh.available() ? "Open Server Workspace Then Refresh" : refresh.reason())
                    .build());
        } else {
            for (PackContentSettingsProvider.ProviderStatus status : statuses) {
                builder.addRow("", providerWidget(status));
            }
        }
        for (PackContentSettingsProvider.Diagnostic diagnostic : provider.diagnostics()) {
            builder.addRow("", diagnosticWidget(diagnostic));
        }
        return List.of(builder.build());
    }

    private void refreshPackContent() {
        PackContentSettingsProvider.Availability refresh = provider.refreshAvailability();
        if (!refresh.available()) {
            new Notification("Refresh Unavailable", refresh.reason(), Notification.Type.ERROR);
            return;
        }
        new Notification("Refreshing Pack Content", "Scanning Server Pack Providers", Notification.Type.INFO);
        provider.refresh().thenAccept(count ->
                ScreenManager.getInstance().execute(() -> new Notification("Pack Content Refreshed", count + " Workspaces", Notification.Type.SUCCESS))
        ).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> new Notification("Refresh Failed", e.getMessage(), Notification.Type.ERROR));
            return null;
        });
    }

    private MountableButtonWidget providerWidget(PackContentSettingsProvider.ProviderStatus status) {
        String description = status.rootName() + " - " + status.glyphCount() + " Glyphs";
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

    private MountableButtonWidget diagnosticWidget(PackContentSettingsProvider.Diagnostic diagnostic) {
        return new MountableButtonWidget.Builder("Issue")
                .hiddenText(diagnostic.providerId())
                .description(fileName(diagnostic.sourceFile()) + " - " + diagnostic.message())
                .build();
    }

    private String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "Unknown";
        }
        String normalized = path.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 && separator + 1 < normalized.length() ? normalized.substring(separator + 1) : normalized;
    }
}

package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.restudio.ReStudio;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.ScreenWindowWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.FileUtils;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;
import restudio.rescreen.util.TimeUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static restudio.rescreen.util.SoundUtils.playSound;

public class ReProxySettingsController {
    private static final String REPROXY_TAB = "ReProxy";
    private static final long LOAD_TIMEOUT_MS = 30000L;

    private final List<ServerModels.ReProxyDomain> domainCache = new ArrayList<>();
    private ServerModels.ReProxySummary summary;
    private volatile boolean dataLoaded;
    private volatile boolean loadingData;
    private volatile boolean loadingAction;
    private volatile String loadError;
    private volatile long loadStartedAt;
    private volatile long loadRequestId;

    public List<Setting> getSettings() {
        if (!ReStudio.getInstance().isAuthenticated()) {
            Setting.Builder builder = new Setting.Builder("ReProxy");
            builder.addRow("", true, 20, new AnimatedButton.Builder().label("ReStudio Login Required").active(false).build());
            return List.of(builder.build());
        }

        ensureDataLoaded();

        Setting.Builder statusBuilder = new Setting.Builder("ReProxy");
        statusBuilder.addRow("Status", true, false, 30, createOverviewWidget());

        if (!dataLoaded) {
            renderLoadingState(statusBuilder);
            return List.of(statusBuilder.build());
        }

        if (summary != null && summary.activeTunnel != null) {
            statusBuilder.addRow("Address", true, false, 30, createActiveTunnelWidget(summary.activeTunnel));
        } else {
            statusBuilder.addRow("Address", true, false, 30, new MountableButtonWidget.Builder("No Active Tunnel")
                    .description("Start ReProxy From A Server")
                    .hiddenText(tunnelLimitText())
                    .build());
        }
        statusBuilder.addRow("Limits", true, 20, new AnimatedButton.Builder().label(limitText()).active(false).build());

        Setting.Builder domainsBuilder = new Setting.Builder("Domains");
        renderDomains(domainsBuilder, new ArrayList<>(domainCache));
        return List.of(statusBuilder.build(), domainsBuilder.build());
    }

    private void ensureDataLoaded() {
        if (loadingData && hasLoadTimedOut()) {
            loadingData = false;
            dataLoaded = false;
            loadError = "Load timed out";
            loadStartedAt = 0L;
            ScreenManager.getInstance().execute(() -> new Notification("Load Failed", loadError, Notification.Type.ERROR));
            refreshReProxyTab();
        }
        if (!dataLoaded && !loadingData && safe(loadError).isBlank()) {
            loadData();
        }
    }

    private void loadData() {
        if (loadingData) {
            return;
        }
        long requestId = ++loadRequestId;
        loadingData = true;
        loadError = null;
        loadStartedAt = System.currentTimeMillis();
        ReStudio.getInstance().getApi().getReProxySummary()
                .orTimeout(20, TimeUnit.SECONDS)
                .whenComplete((value, error) -> ScreenManager.getInstance().execute(() -> {
                    if (requestId != loadRequestId) {
                        return;
                    }
                    if (error == null && value != null) {
                        summary = value;
                        domainCache.clear();
                        if (value.domains != null) {
                            domainCache.addAll(value.domains);
                        }
                        dataLoaded = true;
                        loadError = null;
                    } else {
                        dataLoaded = false;
                        loadError = sanitizeError(error);
                        new Notification("Load Failed", loadError, Notification.Type.ERROR);
                    }
                    loadingData = false;
                    loadStartedAt = 0L;
                    refreshReProxyTab();
                }));
    }

    private MountableButtonWidget createOverviewWidget() {
        String title = loadingData ? "Loading" : dataLoaded ? "Ready" : "Unavailable";
        if (!safe(loadError).isBlank()) {
            title = "Load Failed";
        }

        String description = domainCountText();
        if (summary != null && summary.activeTunnel != null && summary.activeTunnel.domain != null) {
            description = "Online | " + safeDomain(summary.activeTunnel.domain);
        }

        MountableButtonWidget.Builder builder = new MountableButtonWidget.Builder(title)
                .description(description)
                .hiddenText(loadingAction ? "Working" : "");

        builder.addButton(new SquareButtonWidget.Builder()
                .imagePath("create.png")
                .hint("Create Domain")
                .accentType(ThemeManager.getAccent("nice"))
                .active(dataLoaded && canCreateDomain() && !loadingAction)
                .onClick(this::showCreatePopup)
                .size(18, 18)
                .build());
        builder.addButton(new SquareButtonWidget.Builder()
                .imagePath("reload.png")
                .hint("Refresh")
                .active(!loadingData)
                .onClick(this::loadData)
                .size(18, 18)
                .build());

        return builder.build();
    }

    private void renderLoadingState(Setting.Builder builder) {
        if (loadingData) {
            builder.addRow("", true, 20, new AnimatedButton.Builder().label("Loading Domains").active(false).build());
        } else if (!safe(loadError).isBlank()) {
            builder.addRow("", true, 20, new AnimatedButton.Builder().label("Load Failed").active(false).build());
            builder.addRow("", true, 20, new AnimatedButton.Builder()
                    .label("Retry Load")
                    .accentType(ThemeManager.getAccent("warning"))
                    .onClick(this::loadData)
                    .build());
        } else {
            builder.addRow("", true, 20, new AnimatedButton.Builder().label("Domains Unavailable").active(false).build());
        }
    }

    private MountableButtonWidget createActiveTunnelWidget(ServerModels.ReProxyTunnel tunnel) {
        String domain = tunnel.domain == null ? "Unknown Domain" : safeDomain(tunnel.domain);
        String description = "Port " + tunnel.localPort + " | " + safeStatus(tunnel.status);
        String hidden = tunnel.connectionCount + " Connections | " + formatBytes(tunnel.bytesIn) + "/" + formatBytes(tunnel.bytesOut);

        return new MountableButtonWidget.Builder(domain)
                .description(description)
                .hiddenText(hidden)
                .addButton(new SquareButtonWidget.Builder()
                        .imagePath("clipboard.png")
                        .hint("Copy Address")
                        .onClick(() -> copyAddress(domain))
                        .size(18, 18)
                        .build())
                .addButton(new SquareButtonWidget.Builder()
                        .imagePath("closeReverse.png")
                        .hint("Stop ReProxy")
                        .onClick(() -> stopTunnel(tunnel))
                        .accentType(ThemeManager.getAccent("danger"))
                        .size(18, 18)
                        .build())
                .build();
    }

    private void renderDomains(Setting.Builder builder, List<ServerModels.ReProxyDomain> domains) {
        if (domains.isEmpty()) {
            builder.addRow("", true, false, 30, new MountableButtonWidget.Builder("No Domains")
                    .description("Create Domain To Start")
                    .hiddenText(domainLimitText())
                    .build());
            return;
        }

        domains.sort(Comparator
                .comparing((ServerModels.ReProxyDomain domain) -> !"ACTIVE".equalsIgnoreCase(safe(domain.status)))
                .thenComparing(domain -> safe(domain.subdomain).toLowerCase(Locale.ROOT)));

        for (ServerModels.ReProxyDomain domain : domains) {
            builder.addRow("", true, false, 30, createDomainWidget(domain));
        }
    }

    private MountableButtonWidget createDomainWidget(ServerModels.ReProxyDomain domain) {
        ServerModels.ReProxyTunnel activeTunnel = activeTunnelForDomain(domain);
        boolean online = activeTunnel != null;
        boolean active = "ACTIVE".equalsIgnoreCase(safe(domain.status));
        String description = online ? "Online | Port " + activeTunnel.localPort : safeStatus(domain.status) + " | " + safe(domain.subdomain);
        String hiddenText = online
                ? activeTunnel.connectionCount + " Connections | " + formatBytes(activeTunnel.bytesIn) + "/" + formatBytes(activeTunnel.bytesOut)
                : domainTimeline(domain);

        MountableButtonWidget.Builder builder = new MountableButtonWidget.Builder(safeDomain(domain))
                .description(description)
                .hiddenText(hiddenText)
                .onClick(() -> copyAddress(safeDomain(domain)))
                .addButton(new SquareButtonWidget.Builder()
                        .imagePath("clipboard.png")
                        .hint("Copy Address")
                        .onClick(() -> copyAddress(safeDomain(domain)))
                        .size(18, 18)
                        .build());

        if (online) {
            builder.addButton(new SquareButtonWidget.Builder()
                    .imagePath("closeReverse.png")
                    .hint("Stop ReProxy")
                    .onClick(() -> stopTunnel(activeTunnel))
                    .accentType(ThemeManager.getAccent("danger"))
                    .size(18, 18)
                    .build());
        } else if (active) {
            builder.addButton(new SquareButtonWidget.Builder()
                    .imagePath("delete.png")
                    .hint("Delete Domain")
                    .onClick(() -> showDeletePopup(domain))
                    .accentType(ThemeManager.getAccent("danger"))
                    .size(18, 18)
                    .build());
        }

        MountableButtonWidget widget = builder.build();
        if (!active) {
            widget.setActive(false);
        }
        return widget;
    }

    private ServerModels.ReProxyTunnel activeTunnelForDomain(ServerModels.ReProxyDomain domain) {
        if (summary == null || summary.activeTunnel == null || domain == null || summary.activeTunnel.domain == null) {
            return null;
        }
        ServerModels.ReProxyDomain tunnelDomain = summary.activeTunnel.domain;
        if (!safe(domain.id).isBlank() && domain.id.equals(tunnelDomain.id)) {
            return summary.activeTunnel;
        }
        if (!safe(domain.fullDomain).isBlank() && domain.fullDomain.equalsIgnoreCase(safe(tunnelDomain.fullDomain))) {
            return summary.activeTunnel;
        }
        return null;
    }

    private void showCreatePopup() {
        Screen currentScreen = ScreenManager.getInstance().getCurrentScreen();
        if (currentScreen == null || loadingAction) {
            return;
        }

        PopupWidget.Builder builder = new PopupWidget.Builder("Create Domain")
                .pos(50, currentScreen.height / 5)
                .size(280, 110)
                .setResizable(false);

        TextInputWidget subdomainInput = new TextInputWidget.Builder()
                .placeholder("Subdomain")
                .size(190, 20)
                .build();
        subdomainInput.setText(suggestSubdomain());
        builder.addRow("Subdomain", true, 20, subdomainInput);
        builder.addRow("Limit", true, 20, new AnimatedButton.Builder().label(domainLimitText()).active(false).build());
        builder.addTitleButton(() -> {
            String subdomain = normalizeSubdomain(subdomainInput.getText());
            if (subdomain.isBlank()) {
                new Notification("Create Failed", "Subdomain Required", Notification.Type.ERROR);
                return;
            }
            playSound(Sound.CREATE);
            createDomain(subdomain);
            builder.getWidget().setVisible(false);
        }, "Create Domain", ThemeManager.getAccent("nice"));

        PopupWidget popup = builder.build();
        currentScreen.addDrawableChild(popup);
        popup.show();
    }

    private void showDeletePopup(ServerModels.ReProxyDomain domain) {
        Screen currentScreen = ScreenManager.getInstance().getCurrentScreen();
        if (currentScreen == null || domain == null || loadingAction) {
            return;
        }

        PopupWidget.Builder builder = new PopupWidget.Builder("Delete Domain")
                .pos(50, currentScreen.height / 5)
                .size(300, 100)
                .setResizable(false);

        builder.addRow("Domain", true, 20, new AnimatedButton.Builder().label(safeDomain(domain)).active(false).build());
        builder.addRow("Status", true, 20, new AnimatedButton.Builder().label(safeStatus(domain.status)).active(false).build());
        builder.addTitleButton(() -> {
            playSound(Sound.DELETE);
            deleteDomain(domain);
            builder.getWidget().setVisible(false);
        }, "Delete Domain", ThemeManager.getAccent("danger"));

        PopupWidget popup = builder.build();
        currentScreen.addDrawableChild(popup);
        popup.show();
    }

    private void createDomain(String subdomain) {
        if (loadingAction) {
            return;
        }
        loadingAction = true;
        refreshReProxyTab();
        ReStudio.getInstance().getApi().createReProxyDomain(subdomain)
                .whenComplete((domain, error) -> ScreenManager.getInstance().execute(() -> {
                    loadingAction = false;
                    if (error == null && domain != null) {
                        upsertDomain(domain);
                        new Notification("Domain Created", safeDomain(domain), Notification.Type.SUCCESS);
                        loadData();
                    } else {
                        String message = sanitizeError(error);
                        new Notification(isLimitError(message) ? "Limit Reached" : "Domain Taken", message, Notification.Type.ERROR);
                    }
                    refreshReProxyTab();
                }));
    }

    private void deleteDomain(ServerModels.ReProxyDomain domain) {
        if (domain == null || safe(domain.id).isBlank() || loadingAction) {
            return;
        }
        loadingAction = true;
        refreshReProxyTab();
        ReStudio.getInstance().getApi().deleteReProxyDomain(domain.id)
                .whenComplete((ignored, error) -> ScreenManager.getInstance().execute(() -> {
                    loadingAction = false;
                    if (error == null) {
                        domain.status = "DISABLED";
                        new Notification("Domain Deleted", safeDomain(domain), Notification.Type.SUCCESS);
                        loadData();
                    } else {
                        new Notification("Delete Failed", sanitizeError(error), Notification.Type.ERROR);
                    }
                    refreshReProxyTab();
                }));
    }

    private void stopTunnel(ServerModels.ReProxyTunnel tunnel) {
        if (tunnel == null || safe(tunnel.id).isBlank() || loadingAction) {
            return;
        }
        loadingAction = true;
        refreshReProxyTab();
        ReStudio.getInstance().getApi().stopReProxyTunnel(tunnel.id)
                .whenComplete((ignored, error) -> ScreenManager.getInstance().execute(() -> {
                    loadingAction = false;
                    if (error == null) {
                        new Notification("ReProxy Stopped", safeDomain(tunnel.domain), Notification.Type.SUCCESS);
                        if (summary != null) {
                            summary.activeTunnel = null;
                        }
                        loadData();
                    } else {
                        new Notification("Stop Failed", sanitizeError(error), Notification.Type.ERROR);
                    }
                    refreshReProxyTab();
                }));
    }

    private void copyAddress(String address) {
        if (safe(address).isBlank()) {
            return;
        }
        FileUtils.setClipboard(address);
        new Notification("Address Copied", address, Notification.Type.SUCCESS);
    }

    private void upsertDomain(ServerModels.ReProxyDomain domain) {
        if (domain == null || safe(domain.id).isBlank()) {
            return;
        }
        domainCache.removeIf(existing -> domain.id.equals(existing.id));
        domainCache.add(domain);
    }

    private boolean canCreateDomain() {
        if (summary == null || summary.limits == null) {
            return true;
        }
        long activeDomains = domainCache.stream().filter(domain -> "ACTIVE".equalsIgnoreCase(safe(domain.status))).count();
        return activeDomains < summary.limits.maxDomainsPerUser;
    }

    private String domainCountText() {
        long active = domainCache.stream().filter(domain -> "ACTIVE".equalsIgnoreCase(safe(domain.status))).count();
        int total = domainCache.size();
        if (summary == null || summary.limits == null) {
            return active + "/" + total + " Domains";
        }
        return active + "/" + summary.limits.maxDomainsPerUser + " Domains";
    }

    private String limitText() {
        return domainLimitText() + " | " + tunnelLimitText();
    }

    private String domainLimitText() {
        if (summary == null || summary.limits == null || summary.usageWindow == null) {
            return "Domain Limit Unknown";
        }
        return summary.usageWindow.domainsCreatedToday + "/" + summary.limits.maxDomainCreatesPerDay + " Created Today";
    }

    private String tunnelLimitText() {
        if (summary == null || summary.limits == null || summary.usageWindow == null) {
            return "Tunnel Limit Unknown";
        }
        return summary.usageWindow.tunnelsStartedThisHour + "/" + summary.limits.maxTunnelStartsPerHour + " Started This Hour";
    }

    private String domainTimeline(ServerModels.ReProxyDomain domain) {
        List<String> parts = new ArrayList<>();
        String created = timeAgo(domain.createdAt);
        if (!created.isBlank()) {
            parts.add("Created " + created);
        }
        String lastUsed = timeAgo(domain.lastUsedAt);
        if (!lastUsed.isBlank()) {
            parts.add("Used " + lastUsed);
        }
        return parts.isEmpty() ? "Never Used" : String.join(" | ", parts);
    }

    private String timeAgo(String value) {
        if (safe(value).isBlank()) {
            return "";
        }
        try {
            return TimeUtils.timeSense(Instant.parse(value).toEpochMilli());
        } catch (DateTimeParseException ignored) {
            return "Unknown";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        while (value >= 1024D && unit < units.length - 1) {
            value /= 1024D;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private String suggestSubdomain() {
        long suffix = System.currentTimeMillis() % 100000L;
        return "server-" + Long.toString(suffix, 36);
    }

    private String normalizeSubdomain(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
    }

    private String safeDomain(ServerModels.ReProxyDomain domain) {
        if (domain == null) {
            return "Unknown Domain";
        }
        return safeDomain(domain.fullDomain);
    }

    private String safeDomain(String value) {
        return safe(value).isBlank() ? "Unknown Domain" : value;
    }

    private String safeStatus(String value) {
        return safe(value).isBlank() ? "Unknown" : value;
    }

    private boolean isLimitError(String message) {
        return safe(message).toLowerCase(Locale.ROOT).contains("limit");
    }

    private boolean hasLoadTimedOut() {
        return loadStartedAt > 0L && System.currentTimeMillis() - loadStartedAt > LOAD_TIMEOUT_MS;
    }

    private String sanitizeError(Throwable throwable) {
        if (throwable == null) {
            return "Request Failed";
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
            return "Request Failed";
        }
        return message.length() > 180 ? message.substring(0, 180) + "..." : message;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void refreshReProxyTab() {
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
            settingsScreen.refreshTab(REPROXY_TAB);
        }
    }
}

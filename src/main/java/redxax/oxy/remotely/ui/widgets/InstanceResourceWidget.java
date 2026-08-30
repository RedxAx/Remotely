package redxax.oxy.remotely.ui.widgets;

import restudio.rebase.resource.provider.OnlineResourceVersion;
import restudio.rebase.ui.screens.resources.ResourceContainerItem;
import restudio.rebase.ui.screens.resources.ResourceContainerProvider;
import restudio.rebase.ui.widgets.resources.ResourceWidget;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;
import restudio.rescreen.util.Identifier;

import static restudio.rescreen.util.SoundUtils.playSound;

public class InstanceResourceWidget extends ResourceWidget<ResourceContainerItem> {
    private final ResourceContainerProvider provider;
    private final Runnable refreshCallback;
    private final ReScreen parentScreen;
    private boolean updateBackup = true;
    private volatile boolean iconLoading = false;

    public InstanceResourceWidget(ReScreen parentScreen, ResourceContainerProvider provider, ResourceContainerItem resource, Runnable refreshCallback) {
        this(parentScreen, provider, resource, refreshCallback, new Adapter(provider));
    }

    private InstanceResourceWidget(ReScreen parentScreen, ResourceContainerProvider provider, ResourceContainerItem resource,
                                   Runnable refreshCallback, Adapter adapter) {
        super(resource, adapter);
        adapter.owner = this;
        this.parentScreen = parentScreen;
        this.provider = provider;
        this.refreshCallback = refreshCallback;
        ensureImage();
    }

    private void ensureImage() {
        if (iconLoading || resource.getIconId() != null) return;
        if (resource.getProjectId() == null || resource.getProviderName() == null) return;
        iconLoading = true;
        provider.resolveIcon(resource, icon -> ScreenManager.getInstance().execute(() -> {
            if (icon != null) resource.setIconId(icon);
            refresh();
            iconLoading = false;
        }));
    }

    private void showUpdateDialog() {
        if (resource.getProjectId() == null || resource.getProviderName() == null) {
            new Notification("Cannot Check Update", "Resource has no project ID", Notification.Type.WARN);
            return;
        }
        provider.checkUpdate(resource).whenComplete((update, failure) -> ScreenManager.getInstance().execute(() -> {
            if (failure != null) {
                new Notification("Check Failed", failure.getMessage(), Notification.Type.ERROR);
                return;
            }
            if (update == null) {
                resource.availableUpdate = null;
                refresh();
                return;
            }
            resource.availableUpdate = update;
            refresh();
            showUpdatePopup(update);
        }));
    }

    private void showUpdatePopup(OnlineResourceVersion newVersion) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Update " + resource.getName()).size(400, 300).setResizable(true);
        builder.addMarkdown("Version Info", String.format("Current: %s\nNew: %s", resource.getVersion(), newVersion.versionNumber));
        builder.addMarkdown("Changelog", newVersion.changelog != null ? newVersion.changelog : "No changelog provided.");
        ToggleWidget backupToggle = new ToggleWidget.Builder().toggled(updateBackup).onChange(() -> updateBackup = !updateBackup).build();
        var backupCapability = provider.capability(ResourceContainerProvider.CAPABILITY_BACKUP);
        backupToggle.setActive(backupCapability.available());
        backupToggle.setHint(backupCapability.available() ? "Backup Resource" : backupCapability.detail());
        SquareButtonWidget updateBtn = new SquareButtonWidget.Builder().imagePath("download.png").onClick(() -> {
            Notification progressNotification = new Notification.Builder()
                    .message("Updating " + resource.getName())
                    .description("Starting Download")
                    .type(Notification.Type.INFO)
                    .loading(true)
                    .autoSlideOut(false)
                    .progress(0, 100)
                    .build();
            builder.getWidget().setVisible(false);
            provider.update(resource, newVersion, (current, total) -> {
                if (total > 0) {
                    int percentage = (int) (current * 100 / total);
                    ScreenManager.getInstance().execute(() -> progressNotification.updateProgress(String.format("%d/%d KB", current / 1024, total / 1024), percentage, 100));
                }
            }, refreshCallback, updateBackup).thenRun(() -> ScreenManager.getInstance().execute(() -> {
                progressNotification.update().message("Update Complete").description(resource.getName() + " Updated").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).progress(100, 100).commit();
            })).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> progressNotification.update().message("Update Failed").description(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit());
                return null;
            });
        }).build();
        builder.addRow(new PopupWidget.PopupRow.Builder("Backup?", backupToggle).contentWidth().build());
        builder.addTitleAction("Update", () -> updateBtn.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        PopupWidget popup = builder.build();
        ScreenManager.getInstance().getCurrentScreen().addDrawableChild(popup);
        popup.show();
    }

    private void open(int button) {
        if (button == 0 && resource.getProjectId() != null && resource.getProviderName() != null) {
            playSound(Sound.CREATE);
            provider.openResource(parentScreen, resource, refreshCallback);
        }
    }

    @Override
    protected void refreshFromResource() {
        super.refreshFromResource();
        var toggleCapability = provider.capability(ResourceContainerProvider.CAPABILITY_TOGGLE);
        toggleButton.setActive(toggleCapability.available());
        toggleButton.setHint(toggleCapability.available() ? "Toggle Resource" : toggleCapability.detail());
        var updateCapability = provider.capability(ResourceContainerProvider.CAPABILITY_UPDATE_SELECTION);
        updateButton.setActive(updateCapability.available());
        updateButton.setHint(updateCapability.available() ? "Update Available" : updateCapability.detail());
    }

    private static class Adapter implements ResourceAdapter<ResourceContainerItem> {
        private final ResourceContainerProvider provider;
        private InstanceResourceWidget owner;

        private Adapter(ResourceContainerProvider provider) {
            this.provider = provider;
        }

        @Override
        public String name(ResourceContainerItem resource) {
            return resource.getName();
        }

        @Override
        public String description(ResourceContainerItem resource) {
            return resource.getDescription();
        }

        @Override
        public String version(ResourceContainerItem resource) {
            return resource.getVersion();
        }

        @Override
        public String author(ResourceContainerItem resource) {
            return resource.getAuthor();
        }

        @Override
        public Identifier iconId(ResourceContainerItem resource) {
            if (owner != null) owner.ensureImage();
            return resource.getIconId();
        }

        @Override
        public boolean enabled(ResourceContainerItem resource) {
            return resource.isEnabled();
        }

        @Override
        public boolean updateAvailable(ResourceContainerItem resource) {
            return resource.availableUpdate != null;
        }

        @Override
        public void toggle(ResourceContainerItem resource, ToggleWidget toggle, RenderingMode renderingMode) {
            if (renderingMode == RenderingMode.COMPACT_UPDATE || resource.isModpack()) {
                return;
            }
            boolean originalState = resource.isEnabled();
            if (!provider.available(ResourceContainerProvider.CAPABILITY_TOGGLE)) {
                toggle.setValue(originalState);
                return;
            }
            provider.toggle(resource, !originalState).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> {
                    new Notification("Failed to toggle resource", e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), Notification.Type.ERROR);
                    toggle.setValue(originalState);
                });
                return null;
            });
        }

        @Override
        public void update(ResourceContainerItem resource) {
            if (owner != null) {
                owner.showUpdateDialog();
            }
        }

        @Override
        public void open(ResourceContainerItem resource, int button) {
            if (owner != null) {
                owner.open(button);
            }
        }
    }
}

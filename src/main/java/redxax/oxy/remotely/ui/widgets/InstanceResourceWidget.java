package redxax.oxy.remotely.ui.widgets;

import restudio.rebase.Rebase;
import restudio.rebase.cache.CacheManager;
import restudio.rebase.instance.Instance;
import restudio.rebase.resource.InstanceResource;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.resource.provider.IResourceProvider;
import restudio.rebase.resource.provider.OnlineResource;
import restudio.rebase.resource.provider.OnlineResourceVersion;
import restudio.rebase.ui.screens.resources.ResourceOverviewScreen;
import restudio.rebase.ui.widgets.resources.ResourceWidget;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.ImageUtils;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.nio.file.Files;
import java.nio.file.Path;

import static restudio.rescreen.util.SoundUtils.playSound;

public class InstanceResourceWidget extends ResourceWidget<InstanceResource> {
    private final Instance instance;
    private final Runnable refreshCallback;
    private final ReScreen parentScreen;
    private boolean updateBackup = false;
    private volatile boolean iconLoading = false;

    public InstanceResourceWidget(ReScreen parentScreen, Instance instance, InstanceResource resource, Runnable refreshCallback) {
        this(parentScreen, instance, resource, refreshCallback, new Adapter(parentScreen, instance));
    }

    private InstanceResourceWidget(ReScreen parentScreen, Instance instance, InstanceResource resource, Runnable refreshCallback, Adapter adapter) {
        super(resource, adapter);
        adapter.owner = this;
        this.parentScreen = parentScreen;
        this.instance = instance;
        this.refreshCallback = refreshCallback;
        this.updateBackup = resource.isModpack();
        ensureImage();
    }

    private void ensureImage() {
        if (resource.getProjectId() == null || resource.getProviderName() == null) return;
        CacheManager cacheManager = Rebase.get().getCacheManager();
        Path imagePath = cacheManager.getIconPath(resource.getProviderName(), resource.getProjectId());
        Identifier currentIconId = resource.getIconId();
        if (currentIconId == null || currentIconId.equals(Identifier.icon("missing.png"))) {
            if (Files.exists(imagePath)) {
                resource.setGeneratedIcon(ImageUtils.loadImageId(imagePath));
            } else if (!iconLoading) {
                OnlineResource cachedDetails = cacheManager.get(resource.getProviderName(), resource.getProjectId());
                if (cachedDetails != null && cachedDetails.getIconUrl() != null && !cachedDetails.getIconUrl().isBlank()) {
                    iconLoading = true;
                    cacheManager.getOrFetchImageId(cachedDetails.getIconUrl(), imagePath).thenAccept(iconId -> {
                        if (iconId != null) {
                            resource.setGeneratedIcon(iconId);
                            ScreenManager.getInstance().execute(this::ensureImage);
                        }
                    }).whenComplete((v, e) -> iconLoading = false);
                } else {
                    IResourceProvider provider = Rebase.get().getResourceProvider(resource.getProviderName());
                    if (provider != null) {
                        iconLoading = true;
                        provider.getResourceDetails(resource.getProjectId()).thenAccept(resourceDetails -> {
                            if (resourceDetails != null) {
                                cacheManager.put(resource.getProviderName(), resource.getProjectId(), resourceDetails);
                                String iconUrl = resourceDetails.getIconUrl();
                                if (iconUrl != null && !iconUrl.isBlank()) {
                                    cacheManager.getOrFetchImageId(iconUrl, imagePath).thenAccept(iconId -> {
                                        if (iconId != null) {
                                            resource.setGeneratedIcon(iconId);
                                            ScreenManager.getInstance().execute(() -> setMessage(resourceDetails.getName()));
                                        }
                                    });
                                }
                            }
                        }).whenComplete((v, e) -> iconLoading = false);
                    }
                }
            }
        }
        imageColor = ImageUtils.getDominantColor(resource.getIconId());
    }

    private void showUpdateDialog() {
        if (resource.getProjectId() == null || resource.getProviderName() == null) {
            new Notification("Cannot Check Update", "Resource has no project ID", Notification.Type.WARN);
            return;
        }
        Rebase.get().getUpdateManager().checkSingleResourceUpdate(instance, resource).thenAcceptAsync(updateOpt -> {
            if (updateOpt.isEmpty()) {
                resource.availableUpdate = null;
                refresh();
                return;
            }
            resource.availableUpdate = updateOpt.get();
            refresh();
            showUpdatePopup(updateOpt.get());
        }, ScreenManager.getInstance()::execute).exceptionally(ex -> {
            new Notification("Check Failed", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage(), Notification.Type.ERROR);
            return null;
        });
    }

    private void showUpdatePopup(OnlineResourceVersion newVersion) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Update " + resource.getName()).size(400, 300).setResizable(true);
        builder.addMarkdown("Version Info", String.format("Current: %s\nNew: %s", resource.getVersion(), newVersion.versionNumber), 35);
        builder.addMarkdown("Changelog", newVersion.changelog != null ? newVersion.changelog : "No changelog provided.", 150);
        ToggleWidget backupToggle = new ToggleWidget.Builder().toggled(updateBackup).onChange(() -> updateBackup = !updateBackup).build();
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
            Rebase.get().getUpdateManager().performUpdate(instance, resource, newVersion, (current, total) -> {
                if (total > 0) {
                    int percentage = (int) (current * 100 / total);
                    ScreenManager.getInstance().execute(() -> progressNotification.updateProgress(String.format("%d/%d KB", current / 1024, total / 1024), percentage, 100));
                }
            }, refreshCallback, updateBackup, 7).thenRun(() -> ScreenManager.getInstance().execute(() -> {
                refreshCallback.run();
                progressNotification.update().message("Update Complete").description(resource.getName() + " Updated").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).progress(100, 100).commit();
            })).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> progressNotification.update().message("Update Failed").description(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit());
                return null;
            });
        }).build();
        builder.addRow("Backup?", false, 18, backupToggle);
        builder.addRow("Update", false, 18, updateBtn);
        PopupWidget popup = builder.build();
        ScreenManager.getInstance().getCurrentScreen().addDrawableChild(popup);
        popup.show();
    }

    private void open(int button) {
        if (button == 0 && resource.getProjectId() != null && resource.getProviderName() != null) {
            IResourceProvider provider = Rebase.get().getResourceProvider(resource.getProviderName());
            if (provider != null) {
                parentScreen.setLoading(true);
                playSound(Sound.CREATE);
                provider.getResourceDetails(resource.getProjectId()).thenAccept(onlineResource -> {
                    if (onlineResource != null) {
                        ResourceType activeResourceType = ResourceType.getTypeFromString(onlineResource.projectType);
                        if (activeResourceType == null) activeResourceType = resource.getType();
                        ResourceType finalActiveResourceType = activeResourceType;
                        ScreenManager.getInstance().execute(() -> ScreenManager.getInstance().setScreen(new ResourceOverviewScreen(parentScreen, provider, onlineResource, instance, finalActiveResourceType, true, null)));
                    }
                }).whenComplete((v, e) -> parentScreen.setLoading(false));
            }
        }
    }

    @Override
    protected void refreshFromResource() {
        super.refreshFromResource();
    }

    private static class Adapter implements ResourceAdapter<InstanceResource> {
        private final ReScreen parentScreen;
        private final Instance instance;
        private InstanceResourceWidget owner;

        private Adapter(ReScreen parentScreen, Instance instance) {
            this.parentScreen = parentScreen;
            this.instance = instance;
        }

        @Override
        public String name(InstanceResource resource) {
            return resource.getName();
        }

        @Override
        public String description(InstanceResource resource) {
            return resource.getDescription();
        }

        @Override
        public String version(InstanceResource resource) {
            return resource.getVersion();
        }

        @Override
        public String author(InstanceResource resource) {
            return resource.getAuthor();
        }

        @Override
        public Identifier iconId(InstanceResource resource) {
            return resource.getIconId();
        }

        @Override
        public boolean enabled(InstanceResource resource) {
            return resource.isEnabled();
        }

        @Override
        public boolean updateAvailable(InstanceResource resource) {
            return resource.availableUpdate != null;
        }

        @Override
        public void toggle(InstanceResource resource, ToggleWidget toggle, RenderingMode renderingMode) {
            if (renderingMode == RenderingMode.COMPACT_UPDATE || resource.isModpack()) {
                return;
            }
            boolean originalState = resource.isEnabled();
            Rebase.get().getResourceManager().setEnabled(instance, resource, !originalState).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> {
                    new Notification("Failed to toggle resource", e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), Notification.Type.ERROR);
                    toggle.setValue(originalState);
                });
                return null;
            });
        }

        @Override
        public void update(InstanceResource resource) {
            if (owner != null) {
                owner.showUpdateDialog();
            }
        }

        @Override
        public void open(InstanceResource resource, int button) {
            if (owner != null) {
                owner.open(button);
            }
        }
    }
}

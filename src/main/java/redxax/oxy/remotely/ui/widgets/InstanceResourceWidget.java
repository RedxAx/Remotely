package redxax.oxy.remotely.ui.widgets;

import restudio.rebase.Rebase;
import restudio.rebase.cache.CacheManager;
import restudio.rebase.instance.Instance;
import restudio.rebase.resource.InstanceResource;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.resource.provider.IResourceProvider;
import restudio.rebase.resource.provider.OnlineResource;
import restudio.rebase.ui.screens.resources.ResourceOverviewScreen;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.ImageUtils;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static restudio.rescreen.config.Config.*;
import static restudio.rescreen.render.TextRenderer.tr;
import static restudio.rescreen.util.SoundUtils.playSound;

public class InstanceResourceWidget extends MountableButtonWidget {
    private final Instance instance;
    private InstanceResource resource;
    private final Runnable refreshCallback;
    private final ReScreen parentScreen;
    private final ToggleWidget toggleButton;
    private final SquareButtonWidget updateButton;
    private boolean updateBackup = false;
    private volatile boolean iconLoading = false;
    private int imageColor;

    public enum RenderingMode {
        NORMAL,
        COMPACT_UPDATE
    }

    private RenderingMode renderingMode = RenderingMode.NORMAL;
    public boolean includedInUpdate = true;

    public InstanceResourceWidget(ReScreen parentScreen, Instance instance, InstanceResource resource, Runnable refreshCallback) {
        super(resource.getName(), null, null, new CopyOnWriteArrayList<>(), null);
        setCursorHoverReactive(true);
        this.parentScreen = parentScreen;
        this.instance = instance;
        this.resource = resource;
        this.refreshCallback = refreshCallback;
        this.animateElevation = false;
        this.imageColor = ImageUtils.getDominantColor(resource.getIcon());

        AtomicReference<ToggleWidget> toggleButtonRef = new AtomicReference<>();
        this.toggleButton = new ToggleWidget.Builder().animateElevation(false).entranceAnimation(false).onChange(() -> {
            ToggleWidget toggle = toggleButtonRef.get();
            if (renderingMode == RenderingMode.COMPACT_UPDATE) {
                includedInUpdate = !includedInUpdate;
                toggle.setValue(includedInUpdate);
            } else {
                boolean originalState = resource.isEnabled();
                Rebase.get().getResourceManager().setEnabled(instance, resource, !originalState).exceptionally(e -> {
                    ScreenManager.getInstance().execute(() -> {
                        new Notification("Failed to toggle resource", e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), Notification.Type.ERROR);
                        toggle.setValue(originalState);
                    });
                    return null;
                });
            }
        }).toggled(resource.isEnabled()).build();
        toggleButtonRef.set(this.toggleButton);

        this.updateButton = new SquareButtonWidget.Builder()
                .imagePath("download.png")
                .onClick(this::showUpdateDialog)
                .hint("Update Available!")
                .accentType(ThemeManager.getAccent("nice"))
                .size(18, 18)
                .visible(resource.availableUpdate != null)
                .entranceAnimation(false)
                .build();

        addMountedWidget(this.updateButton);
        addMountedWidget(this.toggleButton);
        ensureImage();
    }

    public void setRenderingMode(RenderingMode mode) {
        this.renderingMode = mode;
        if (mode == RenderingMode.COMPACT_UPDATE) {
            setHeight(18);
            this.toggleButton.setValue(this.includedInUpdate);
            this.toggleButton.setSize(16, 8);
            this.updateButton.setVisible(false);
        } else {
            refreshFromResource();
        }
    }

    private void ensureImage() {
        if (resource.getProjectId() == null || resource.getProviderName() == null) return;
        CacheManager cacheManager = Rebase.get().getCacheManager();
        Path imagePath = cacheManager.getIconPath(resource.getProviderName(), resource.getProjectId());
        if (ImageUtils.compareImages(resource.getIcon(), ImageUtils.loadIcon("missing.png"))) {
            if (Files.exists(imagePath)) {
                resource.setIcon(ImageUtils.loadImage(imagePath));
            } else if (!iconLoading) {
                OnlineResource cachedDetails = cacheManager.get(resource.getProviderName(), resource.getProjectId());
                if (cachedDetails != null && cachedDetails.getIconUrl() != null && !cachedDetails.getIconUrl().isBlank()) {
                    iconLoading = true;
                    cacheManager.getOrFetchImage(cachedDetails.getIconUrl(), imagePath).thenAccept(img -> {
                        if (img != null) {
                            this.resource.setIcon(img);
                            ScreenManager.getInstance().execute(this::ensureImage);
                        }
                    }).whenComplete((v, e) -> iconLoading = false);
                } else {
                    IResourceProvider provider = Rebase.get().getResourceProvider(resource.getProviderName());
                    if (provider != null) {
                        iconLoading = true;
                        provider.getResourceDetails(resource.getProjectId()).thenAccept(resourceDetails -> {
                            if (resourceDetails != null) {
                                cacheManager.put(this.resource.getProviderName(), this.resource.getProjectId(), resourceDetails);
                                String iconUrl = resourceDetails.getIconUrl();
                                if (iconUrl != null && !iconUrl.isBlank()) {
                                    cacheManager.getOrFetchImage(iconUrl, imagePath).thenAccept(img -> {
                                        if (img != null) {
                                            this.resource.setIcon(img);
                                            ScreenManager.getInstance().execute(() -> this.setMessage(resourceDetails.getName()));
                                        }
                                    });
                                }
                            }
                        }).whenComplete((v, e) -> iconLoading = false);
                    }
                }
            }
        }
        this.imageColor = ImageUtils.getDominantColor(this.resource.getIcon());
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

    private void showUpdatePopup(restudio.rebase.resource.provider.OnlineResourceVersion newVersion) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Update " + resource.getName()).size(400, 300).setResizable(true);

        String info = String.format("Current: %s\nNew: %s", resource.getVersion(), newVersion.versionNumber);
        builder.addMarkdown("Version Info", info, 35);
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
            }, this.refreshCallback, updateBackup, 7).thenRun(() -> {
                ScreenManager.getInstance().execute(() -> {
                    refreshCallback.run();
                    progressNotification.update()
                            .message("Update Complete")
                            .description(resource.getName() + " Updated")
                            .type(Notification.Type.SUCCESS)
                            .loading(false)
                            .autoSlideOut(true)
                            .progress(100, 100)
                            .commit();
                });
            }).exceptionally(e -> {
                ScreenManager.getInstance().execute(() -> progressNotification.update()
                        .message("Update Failed")
                        .description(e.getCause() != null ? e.getCause().getMessage() : e.getMessage())
                        .type(Notification.Type.ERROR)
                        .loading(false)
                        .autoSlideOut(true)
                        .commit());
                return null;
            });
        }).build();

        builder.addRow("Backup?", false, 18, backupToggle);
        builder.addRow("Update", false, 18, updateBtn);

        PopupWidget popup = builder.build();
        ScreenManager.getInstance().getCurrentScreen().addDrawableChild(popup);
        popup.show();
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        if (renderingMode == RenderingMode.COMPACT_UPDATE) {
            drawCompactUpdateContent(ctx, mouseX, mouseY);
        } else {
            drawNormalContent(ctx, mouseX, mouseY);
        }

        if (renderingMode == RenderingMode.COMPACT_UPDATE) {
            int toggleX = getX() + getWidth() - toggleButton.getWidth() - 5;
            toggleButton.setPosition(toggleX, getY() + (getHeight() - toggleButton.getHeight()) / 2);
            toggleButton.render(ctx, mouseX, mouseY, deltaTime);
        } else {
            int currentX = getX() + getWidth() - 5;

            currentX -= toggleButton.getWidth();
            toggleButton.setPosition(currentX, getY() + (getHeight() - toggleButton.getHeight()) / 2);
            toggleButton.render(ctx, mouseX, mouseY, deltaTime);

            if (updateButton.visible) {
                currentX -= (updateButton.getWidth() + 5);
                updateButton.setPosition(currentX, getY() + (getHeight() - updateButton.getHeight()) / 2);
                updateButton.render(ctx, mouseX, mouseY, deltaTime);
            }
        }
    }

    private void drawCompactUpdateContent(IDrawContext ctx, int mouseX, int mouseY) {
        int iconSize = 16;
        int textX = getX() + iconSize + 5;

        if (resource.getIcon() != null) {
            ctx.drawBufferedImage(resource.getIcon(), getX() + 1, getY() + (getHeight() - iconSize) / 2f, iconSize, iconSize);
        }

        String name = resource.getName();
        String desc = resource.getDescription();
        boolean hasDesc = desc != null && !desc.equals("No description available.") && !desc.trim().isEmpty();

        ctx.enableScissor(getX() + 1, getY() + 1, getX() + getWidth() - (toggleButton.getWidth() + 10), getY() + getHeight() - 1);
        ctx.drawText(name, textX, getY() + (getHeight() - ITextRenderer.fontHeight) / 2 + 1, ThemeManager.getColor(ThemeColor.text), shadow);

        if (hasDesc) {
            int nameWidth = tr.getWidth(name);
            int descColor = ThemeManager.getAnimatedColor(this.hashCode() + desc.hashCode() + 2, hovered ? ThemeManager.getColor(ThemeColor.textDark) : bgColor, 2);
            ctx.drawText(" | " + desc, textX + nameWidth + 2, getY() + (getHeight() - ITextRenderer.fontHeight) / 2 + 1, descColor, hovered);
        }
        ctx.disableScissor();
    }

    private void drawNormalContent(IDrawContext ctx, int mouseX, int mouseY) {
        int iconSize = 26;
        int textX = getX() + iconSize + 10;
        int globalTextColor = ThemeManager.getColor(ThemeColor.text);
        int expandedGradientWidth = getWidth() / 6;
        int animatedGradientWidth = ThemeManager.getAnimatedValue(this.hashCode() + 143, hovered ? expandedGradientWidth : 0, hovered ? 1 : 1.5);
        int animatedImageColor = ThemeManager.getAnimatedColor(this.hashCode() + resource.getName().hashCode() + 34, hovered ? imageColor : bgColor, 2);

        if (animatedGradientWidth > 1) ctx.fillGradient(getX() + 1, getY() + 1, getX() + animatedGradientWidth, getY() + getHeight() - 1, animatedImageColor, bgColor, true);

        if (resource.getIcon() != null) {
            ctx.drawBufferedImage(resource.getIcon(), getX() + 2, getY() + (float) (getHeight() - iconSize) / 2, iconSize, iconSize);
        }

        ctx.drawText(resource.getName(), textX, getY() + 6, globalTextColor, shadow);

        String details = String.format("v%s | By: %s", resource.getVersion(), resource.getAuthor());

        if (!details.isEmpty()) {
            ctx.enableScissor(getX() + 1, getY() + 1, getX() + getWidth() - 50, getY() + getHeight() - 1);
            ctx.drawText(details, textX, getY() + 18, borderColor, shadow);
            ctx.disableScissor();
        }
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
    public void tick() {
        super.tick();
        refreshFromResource();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (renderingMode == RenderingMode.COMPACT_UPDATE && toggleButton.isVisible()) {
            int toggleX = getX() + getWidth() - toggleButton.getWidth() - 5;
            int toggleY = getY() + (getHeight() - toggleButton.getHeight()) / 2;
            
            toggleButton.setPosition(toggleX, toggleY);
            
            if (mouseX >= toggleX && mouseX <= toggleX + toggleButton.getWidth() &&
                mouseY >= toggleY && mouseY <= toggleY + toggleButton.getHeight()) {
                return toggleButton.mouseClicked(mouseX, mouseY, button);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        open(button);
    }

    public InstanceResource getResource() {
        return resource;
    }

    public void setResource(InstanceResource resource) {
        this.resource = resource;
        this.setMessage(resource.getName());
    }

    public void refresh() {
        refreshFromResource();
    }

    private void refreshFromResource() {
        boolean enabled = resource.isEnabled();
        boolean hasUpdate = resource.availableUpdate != null;
        
        if (renderingMode == RenderingMode.NORMAL) {
            this.toggleButton.setValue(enabled);
        }
        
        this.updateButton.setVisible(hasUpdate);
    }
}

package redxax.oxy.remotely.ui.widgets;

import org.lwjgl.glfw.GLFW;
import restudio.rebase.Rebase;
import restudio.rebase.cache.CacheManager;
import restudio.rebase.instance.Instance;
import restudio.rebase.resource.InstanceResource;
import restudio.rebase.resource.ResourceType;
import restudio.rebase.resource.provider.IResourceProvider;
import restudio.rebase.ui.screens.resources.ResourceOverviewScreen;
import restudio.rebase.ui.widgets.DownloadProgressWidget;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.ImageUtils;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static restudio.rescreen.config.Config.*;
import static restudio.rescreen.render.TextRenderer.tr;
import static restudio.rescreen.util.SoundUtils.playSound;

public class InstanceResourceWidget extends AnimatedWidget {
    private final Instance instance;
    private final InstanceResource resource;
    private final Runnable refreshCallback;
    private final ReScreen parentScreen;
    private final ToggleWidget toggleButton;
    private final SquareButtonWidget updateButton;
    private boolean updateBackup = false;
    private int imageColor;
    private long lastClickTime = 0;

    public enum RenderingMode {
        NORMAL,
        COMPACT_UPDATE
    }

    private RenderingMode renderingMode = RenderingMode.NORMAL;
    public boolean includedInUpdate = true;

    public InstanceResourceWidget(ReScreen parentScreen, Instance instance, InstanceResource resource, Runnable refreshCallback) {
        super(0, 0, 100, 50, resource.getName());
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
        ensureImage();
    }

    public void setRenderingMode(RenderingMode mode) {
        this.renderingMode = mode;
        if (mode == RenderingMode.COMPACT_UPDATE) {
            setHeight(18);
            this.toggleButton.setValue(this.includedInUpdate);
        } else {
            this.toggleButton.setValue(resource.isEnabled());
        }
    }

    private void ensureImage() {
        if (resource.getProjectId() == null || resource.getProviderName() == null) return;
        CacheManager cacheManager = Rebase.get().getCacheManager();
        Path imagePath = cacheManager.getIconPath(resource.getProviderName(), resource.getProjectId());
        if (ImageUtils.compareImages(resource.getIcon(), ImageUtils.loadIcon("missing.png"))) {
            if (Files.exists(imagePath)) {
                resource.setIcon(ImageUtils.loadImage(imagePath));
            } else if (resource.getProjectId() != null && resource.getProviderName() != null) {
                Rebase.get().getResourceProvider(resource.getProviderName()).getResourceDetails(resource.getProjectId()).thenAccept(resourceDetails -> {
                    if (resourceDetails != null) {
                        cacheManager.cacheIcon(this.resource.getProviderName(), resourceDetails.getProjectId(), resourceDetails.getIconUrl());
                        cacheManager.getOrFetchImage(resourceDetails.getIconUrl(), imagePath).thenAccept(img -> {
                            if (img != null) {
                                this.resource.setIcon(img);
                                ScreenManager.getInstance().execute(() -> {
                                    this.setMessage(resourceDetails.getName());
                                    this.ensureImage();
                                });
                            }
                        });
                    }
                });
            }
        }
        this.imageColor = ImageUtils.getDominantColor(this.resource.getIcon());
    }

    private void showUpdateDialog() {
        if (resource.availableUpdate == null) return;

        PopupWidget.Builder builder = new PopupWidget.Builder("Update " + resource.getName()).size(400, 300).setResizable(true);

        String info = String.format("Current: %s\nNew: %s", resource.getVersion(), resource.availableUpdate.versionNumber);
        builder.addMarkdown("Version Info", info, 35);
        builder.addMarkdown("Changelog", resource.availableUpdate.changelog != null ? resource.availableUpdate.changelog : "No changelog provided.", 150);

        DownloadProgressWidget progress = new DownloadProgressWidget(0, 0, 200, 18);
        progress.setVisible(false);
        ToggleWidget backupToggle = new ToggleWidget.Builder().toggled(updateBackup).onChange(() -> updateBackup = !updateBackup).build();
        SquareButtonWidget updateBtn = new SquareButtonWidget.Builder().imagePath("download.png").onClick(() -> {
            progress.setVisible(true);
            Rebase.get().getUpdateManager().performUpdate(instance, resource, resource.availableUpdate, (current, total) -> {
                if (total > 0) {
                    progress.updateProgress(String.format("Downloading... %d/%d KB", current / 1024, total / 1024), (int) (current * 100 / total));
                }
            }, this.refreshCallback, updateBackup, 7).thenRun(() -> {
                builder.getWidget().setVisible(false);
                ScreenManager.getInstance().execute(refreshCallback);
            }).exceptionally(e -> {
                new Notification("Update Failed", e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), Notification.Type.ERROR);
                progress.setVisible(false);
                return null;
            });
        }).build();

        builder.addRow("Backup?", false, 18, backupToggle);
        builder.addRow("Update", false, 18, updateBtn);
        builder.addRow("Progress", true, 20, progress);

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

        toggleButton.setSize(16, 8);
        toggleButton.setPosition(getX() + getWidth() - toggleButton.getWidth() - 5, getY() + (getHeight() - toggleButton.getHeight()) / 2);
        toggleButton.render(ctx, mouseX, mouseY, deltaTime);
    }

    private void drawNormalContent(IDrawContext ctx, int mouseX, int mouseY) {
        int iconSize = 26;
        int textX = getX() + iconSize + 10;
        int globalTextColor = ThemeManager.getColor(ThemeColor.text);
        int globalDarkTextColor = ThemeManager.getColor(ThemeColor.textDark);
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
            int detailsTextColor = ThemeManager.getAnimatedColor(details.hashCode() + 124, hovered ? globalTextColor : globalDarkTextColor, 1.0);
            ctx.drawText(details, textX, getY() + 18, detailsTextColor, shadow);
            ctx.disableScissor();
        }

        int currentX = getX() + getWidth() - 5;
        currentX -= (toggleButton.getWidth());
        toggleButton.setPosition(currentX, getY() + 5);
        toggleButton.render(ctx, mouseX, mouseY, deltaTime);

        if (updateButton.visible) {
            currentX -= (updateButton.getWidth() + 5);
            updateButton.setPosition(currentX, getY() + 5);
            updateButton.render(ctx, mouseX, mouseY, deltaTime);
        }
    }

    private void onDoubleClick(int button) {
        if (button == 0 && resource.getProjectId() != null && resource.getProviderName() != null) {
            IResourceProvider provider = Rebase.get().getResourceProvider(resource.getProviderName());
            if (provider != null) {
                loading = true;
                playSound(Sound.CREATE);
                provider.getResourceDetails(resource.getProjectId()).thenAccept(onlineResource -> {
                    if (onlineResource != null) {
                        ResourceType activeResourceType = ResourceType.getTypeFromString(onlineResource.projectType);
                        if (activeResourceType == null) activeResourceType = resource.getType();
                        ResourceType finalActiveResourceType = activeResourceType;
                        ScreenManager.getInstance().execute(() -> ScreenManager.getInstance().setScreen(new ResourceOverviewScreen(parentScreen, provider, onlineResource, instance, finalActiveResourceType, true)));
                    }
                }).whenComplete((v, e) -> Config.loading = false);
            }
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (toggleButton.isHovered()) {
            toggleButton.onClick(mouseX, mouseY, button);
            return;
        }
        if (renderingMode == RenderingMode.COMPACT_UPDATE) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < 300) {
            onDoubleClick(button);
            lastClickTime = 0;
            return;
        }
        lastClickTime = currentTime;

        if (button == 0) {
            if (updateButton.visible && updateButton.isHovered()) {
                updateButton.onClick(mouseX, mouseY, button);
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // Context menu logic can be added here if needed
        }
    }

    public InstanceResource getResource() {
        return resource;
    }
}
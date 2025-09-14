package redxax.oxy.remotely.ui.widgets;

import restudio.rescreen.platform.IDrawContext;
import redxax.oxy.remotely.resources.IRemotelyResource;
import redxax.oxy.remotely.resources.ResourceManagerScreen;
import redxax.oxy.remotely.resources.ResourcePageScreen;
import redxax.oxy.remotely.servers.ServerInfo;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedWidget;

import java.awt.image.BufferedImage;
import java.net.URL;
import javax.imageio.ImageIO;
import java.util.concurrent.CompletableFuture;

import static redxax.oxy.remotely.Render.drawSnakeLoading;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;
import static redxax.oxy.remotely.RemotelyClient.tr;

public class ResourceWidget extends AnimatedWidget {
    private final IRemotelyResource resource;
    private final ServerInfo serverInfo;
    private BufferedImage icon;
    private BufferedImage banner;
    private final BufferedImage nullIcon;
    private static final int ICON_SIZE = 35;

    public ResourceWidget(IRemotelyResource resource, ServerInfo serverInfo) {
        super(0, 0, 400, 40, (resource.getName()));
        this.resource = resource;
        this.serverInfo = serverInfo;
        nullIcon = loadResourceIcon("unknown.png");
        icon = null;
        banner = null;
        CompletableFuture.runAsync(() -> {
            if (!resource.getIconUrl().isEmpty()) {
                try {
                    URL url = new URL(resource.getIconUrl());
                    BufferedImage loadedIcon = ImageIO.read(url);
                    this.icon = loadedIcon;
                } catch (Exception e) {
                    this.icon = nullIcon;
                }
            } else {
                this.icon = nullIcon;
            }
        });
        CompletableFuture.runAsync(() -> {
            String urlStr = resource.getBannerUrl();
            if (urlStr != null && !urlStr.isEmpty()) {
                try {
                    URL url = new URL(urlStr);
                    BufferedImage loadedBanner = ImageIO.read(url);
                    this.banner = loadedBanner;
                } catch (Exception e) {
                    this.banner = null;
                }
            } else {
                this.banner = null;
            }
        });
    }

    public void renderBanner(IDrawContext ctx) {
        if (banner != null) {
            int imgW = banner.getWidth();
            int imgH = banner.getHeight();
            if (imgW <= 0 || imgH <= 0) {
                return;
            }
            double scale = Math.max((double) getWidth() / imgW, (double) getHeight() / imgH);
            double subImageWidthPrecise = (double) getWidth() / scale;
            double subImageHeightPrecise = (double) getHeight() / scale;
            double subImageXPrecise = ((double) imgW - subImageWidthPrecise) / 2.0;
            double subImageYPrecise = ((double) imgH - subImageHeightPrecise) / 2.0;
            int srcX = (int) Math.round(subImageXPrecise);
            int srcY = (int) Math.round(subImageYPrecise);
            int srcWidth = (int) Math.round(subImageWidthPrecise);
            int srcHeight = (int) Math.round(subImageHeightPrecise);
            srcX = Math.max(0, srcX);
            srcY = Math.max(0, srcY);
            if (srcX + srcWidth > imgW) {
                srcWidth = imgW - srcX;
            }
            if (srcY + srcHeight > imgH) {
                srcHeight = imgH - srcY;
            }
            if (srcWidth <= 0 || srcHeight <= 0) {
                return;
            }
            try {
                BufferedImage subBanner = banner.getSubimage(srcX + 1, srcY + 1, srcWidth - 2, srcHeight - 2);
                ctx.drawBufferedImage(subBanner, getX(), getY(), getWidth(), getHeight());
            } catch (Exception ignored) {}
            ctx.fillGradient(getX(), getY(), getX() + getWidth(), getY() + getHeight() * 2, bgColor, 0x00000000);
        }
    }

    @Override
    protected void drawBackground(IDrawContext ctx) {
        renderBanner(ctx);
        if (banner == null) super.drawBackground(ctx);
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        if (icon != null) {
            ctx.drawBufferedImage(icon, getX() + 4, getY() + (getHeight() - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE);
        } else {
            drawSnakeLoading(ctx, getX() + 4, getY() + (getHeight() - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE);
        }
        ctx.drawText((resource.getName()), getX() + 42, getY() + 5, globalTextColor, shadow);
        String desc = resource.getDescription();
        if (tr.getWidth(desc) > getWidth() - 50) {
            while (tr.getWidth(desc + "...") > getWidth() - 50 && !desc.isEmpty()) {
                desc = desc.substring(0, desc.length() - 1);
            }
            desc += "...";
        }
        ctx.drawText((desc), getX() + 42, getY() + 15, globalDarkTextColor, shadow);
        String info;
        if (resource.getAverageRating() > 0) {
            info = formatDownloads(resource.getDownloads()) + " | " + resource.getAverageRating() + " Star Rating";
        } else {
            info = formatDownloads(resource.getDownloads()) + " | " + resource.getVersion() + " | " + resource.getFollowers() + " Followers";
        }
        ctx.drawText((info), getX() + 42, getY() + 28, globalDarkTextColor, shadow);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        ScreenManager.getInstance();
        ScreenManager.getInstance().setScreen(new ResourcePageScreen((ResourceManagerScreen) ScreenManager.currentScreen, resource, serverInfo));
    }

    private String formatDownloads(int n) {
        if (n >= 1_000_000) {
            return String.format("%.1fM", n / 1_000_000.0);
        } else if (n >= 1000) {
            return String.format("%.1fK", n / 1000.0);
        } else {
            return String.valueOf(n);
        }
    }
}
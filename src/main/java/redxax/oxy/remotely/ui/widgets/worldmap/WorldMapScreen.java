package redxax.oxy.remotely.ui.widgets.worldmap;

import restudio.rebase.instance.Instance;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.InfiniteScreen;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.LoadingAnimationWidget;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class WorldMapScreen extends InfiniteScreen {

    private final Screen parent;
    private final WorldMapRenderer mapRenderer;
    private final AssetManager assetManager;

    private boolean loadingAssets = false;
    private LoadingAnimationWidget loader;

    public WorldMapScreen(Screen parent, Instance instance) {
        super();
        this.parent = parent;

        Path worldFolder = Path.of(instance.getPath()).resolve("world");
        this.assetManager = new AssetManager();
        this.mapRenderer = new WorldMapRenderer(worldFolder, assetManager);

        this.targetZoomLevel = 1.0f;
        this.zoomLevel = 0.5f;
        this.minZoom = 0.1f;
        this.maxZoom = 8.0f;
        this.panSpeed = 15.0f;
        this.zoomSpeed = 10.0f;
        this.clampWidgets = false;
    }

    @Override
    public void init() {
        super.init();

        addHudWidget(new IconButton.Builder()
                .pos(10, 10)
                .size(100, 20)
                .label("Back to Server")
                .imagePath("arrow_left.png")
                .onClick(() -> client.setScreen(parent))
                .build());

        AtomicReference<IconButton> hdButtonRef = new AtomicReference<>();
        hdButtonRef.set(new IconButton.Builder()
                .pos(120, 10)
                .size(140, 20)
                .label("Extract Map Colors")
                .imagePath("download.png")
                .accentType(ThemeManager.getAccent("calm"))
                .hint("Extracts accurate colors from JAR")
                .onClick(() -> {
                    IconButton hdButton = hdButtonRef.get();
                    if (assetManager.isHighQualityLoaded()) return;
                    if (loadingAssets) return;

                    loadingAssets = true;
                    hdButton.active = false;
                    hdButton.setMessage("Extracting...");

                    loader = new LoadingAnimationWidget(width / 2 - 50, height / 2 - 50, 100, 100);
                    addHudWidget(loader);

                    assetManager.loadAssetsFromVersion().thenRun(() -> {
                        loadingAssets = false;
                        removeHudWidget(loader);
                        hdButton.setMessage("Colors Loaded");
                        hdButton.setAccent(ThemeManager.getAccent("nice"));
                        mapRenderer.clearCache();
                        new Notification("Assets Loaded", "High accuracy map colors enabled.", Notification.Type.SUCCESS);
                    }).exceptionally(e -> {
                        loadingAssets = false;
                        removeHudWidget(loader);
                        hdButton.active = true;
                        hdButton.setMessage("Retry Colors");
                        hdButton.setAccent(ThemeManager.getAccent("danger"));
                        new Notification("Error", "Failed to load assets: " + e.getMessage(), Notification.Type.ERROR);
                        return null;
                    });
                })
                .build());
        IconButton hdButton = hdButtonRef.get();


        if (assetManager.isHighQualityLoaded()) {
            hdButton.setMessage("Colors Loaded");
            hdButton.setAccent(ThemeManager.getAccent("nice"));
            hdButton.active = false;
        }

        addHudWidget(hdButton);
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF101010);

    }

    @Override
    public void renderHandler(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateTransforms(delta);

        if (framebuffer != null) framebuffer.bind();
        context.fill(0, 0, width, height, 0xFF050505);

        context.getMatrices().push();

        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(zoomLevel, zoomLevel, 1.0f);
        context.getMatrices().translate(-centerX + panX, -centerY + panY, 0);

        mapRenderer.render(context, panX, panY, zoomLevel, width, height);

        for (var w : worldWidgets) {
            w.render(context, 0, 0, delta);
        }

        context.getMatrices().pop();

        for (var w : hudWidgets) {
            w.render(context, mouseX, mouseY, delta);
        }

        double[] worldMouse = screenToWorld(mouseX, mouseY);
        double wxd = worldMouse[0];
        double wzd = worldMouse[1];
        int wx = (int) Math.floor(wxd);
        int wz = (int) Math.floor(wzd);
        String[] info = mapRenderer.getBlockAndBiomeAt(wxd, wzd);
        String blockName = info != null && info[0] != null ? prettyCase(info[0]) : "Unknown";
        String biomeName = info != null && info[1] != null ? prettyCase(info[1]) : "Unknown";

        String line1 = String.format("X: %d Z: %d", wx, wz);
        String line2 = "Block: " + blockName;
        String line3 = "Biome: " + biomeName;
        int tx = mouseX + 12;
        int ty = mouseY + 12;
        context.drawText(line1, tx, ty, 0xFFFFFFFF, true);
        context.drawText(line2, tx, ty + 12, 0xFFDDDDDD, true);
        context.drawText(line3, tx, ty + 24, 0xFFBBBBBB, true);

        if (framebuffer != null) {
            framebuffer.unbind();
        }
    }

    @Override
    public void close() {
        mapRenderer.close();
        client.setScreen(parent);
    }

    private String prettyCase(String id) {
        String s = id == null ? "" : id;
        int c = s.indexOf(':');
        if (c >= 0) s = s.substring(c + 1);
        String[] parts = s.split("_");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            String t = Character.toUpperCase(p.charAt(0)) + (p.length() > 1 ? p.substring(1) : "");
            if (i > 0) b.append(' ');
            b.append(t);
        }
        String res = b.toString();
        if (res.isEmpty()) return "Unknown";
        return res;
    }
}
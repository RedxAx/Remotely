package redxax.oxy.remotely.worldgen.ui;

import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;

import java.util.List;
import java.util.Locale;

public class WorldGenEditorScreen extends FlowEditorScreen {
    private final String actualServerId;
    private final Screen parentScreen;
    private final WorldGenManager manager = WorldGenManager.getInstance();
    private final String previewId;
    private String previewEnvironment = "NORMAL";
    private long previewSeed;
    private String previewPlayerUuid = "";

    public WorldGenEditorScreen(String serverId, ClientServerView server, Screen parent) {
        super(WorldGenManager.getInstance().getOrCreateEditorGraph(serverId), WorldGenManager.registryServerId(serverId), parent);
        this.actualServerId = serverId;
        this.parentScreen = parent;
        this.previewId = "worldgen_" + sanitizePreviewId(serverId);
    }

    public String getActualServerId() {
        return actualServerId;
    }

    @Override
    public void init() {
        super.init();
        manager.requestRegistry(actualServerId);
    }

    @Override
    public String getDesktopAppId() {
        return "worldgen-editor";
    }

    @Override
    public String getDesktopAppTitle() {
        return "World Generation";
    }

    @Override
    public String getDesktopAppIconPath() {
        return "node.png";
    }

    @Override
    protected boolean showExtractButton() {
        return false;
    }

    @Override
    protected void addCustomHeaderButtons() {
        IconButton previewButton = new IconButton.Builder()
            .size(18, 18)
            .imagePath("play.png")
            .onClick(this::showPreviewPopup)
            .build();
        addHeaderButton(previewButton);

        IconButton stopButton = new IconButton.Builder()
            .size(18, 18)
            .imagePath("close.png")
            .onClick(() -> manager.stopPreview(actualServerId, previewId))
            .build();
        addHeaderButton(stopButton);
    }

    @Override
    protected boolean useStrictTypeCompatibility() {
        return true;
    }

    @Override
    protected boolean allowFlowPins() {
        return false;
    }

    @Override
    protected void saveGraph() {
        manager.saveWorldGen(actualServerId, graph);
        previewCurrentGraph();
    }

    public void refreshStatus() {
    }

    private void showPreviewPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Preview").setResizable(false);
        TextInputWidget seedInput = new TextInputWidget.Builder()
            .placeholder("Seed")
            .size(220, 18)
            .build();
        seedInput.setText(String.valueOf(previewSeed));

        DropDownWidget<String> environmentSelect = new DropDownWidget.Builder<>(List.of("NORMAL", "NETHER", "THE_END", "CUSTOM"))
            .size(220, 18)
            .selectedItem(previewEnvironment)
            .build();

        TextInputWidget playerUuidInput = new TextInputWidget.Builder()
            .placeholder("Player UUID")
            .size(220, 18)
            .build();
        playerUuidInput.setText(previewPlayerUuid);

        builder.addRow("Seed", true, 20, seedInput);
        builder.addRow("Environment", true, 20, environmentSelect);
        builder.addRow("Player UUID", true, 20, playerUuidInput);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton startButton = new AnimatedButton.Builder()
            .label("Preview")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                previewEnvironment = safeText(environmentSelect.getSelectedItem()).toUpperCase(Locale.ROOT);
                previewSeed = parseLong(seedInput.getText(), 0);
                previewPlayerUuid = safeText(playerUuidInput.getText()).trim();
                previewCurrentGraph();
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, startButton);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void previewCurrentGraph() {
        syncNodePositions();
        manager.requestPreview(actualServerId, previewId, graph, previewEnvironment, previewSeed, previewPlayerUuid);
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String sanitizePreviewId(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return "local";
        }
        return serverId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    @Override
    public void close() {
        ScreenManager.getInstance().setScreen(parentScreen);
    }
}

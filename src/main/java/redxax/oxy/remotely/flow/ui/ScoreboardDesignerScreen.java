package redxax.oxy.remotely.flow.ui;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.flow.ui.studio.StudioPanel;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static restudio.rescreen.config.Config.desktopMode;

public class ScoreboardDesignerScreen extends StudioScreen implements DesktopWindowBehaviorProvider {
    private static final int PANEL_PADDING = 8;
    private static final int PREVIEW_ROW_BG = 0x7F101010;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SCORE_COLOR = 0xFFFF5555;
    private static final Pattern MINI_HEX_PATTERN = Pattern.compile("<#([0-9a-fA-F]{6})>");

    private final ScoreboardDefinition scoreboard;
    private final String serverId;
    private final Object parent;
    private final boolean forceSuperScreen;
    private final ReSyncStudioPanelState panelState = new ReSyncStudioPanelState();

    private StudioPanel inspectorStudioPanel;
    private SidePanel inspectorPanel;
    private TextInputWidget titleInput;
    private TextInputWidget objectiveInput;
    private CodeEditorWidget linesInput;
    private String previewTitle = "";
    private List<String> previewLines = new ArrayList<>();
    private int previewRequestRevision;

    public ScoreboardDesignerScreen(ScoreboardDefinition scoreboard) {
        this(scoreboard, null, null);
    }

    public ScoreboardDesignerScreen(ScoreboardDefinition scoreboard, String serverId, Object parent) {
        this(scoreboard, serverId, parent, !(parent instanceof Screen));
    }

    public ScoreboardDesignerScreen(ScoreboardDefinition scoreboard, String serverId, Object parent, boolean forceSuperScreen) {
        this.scoreboard = scoreboard;
        this.serverId = serverId;
        this.parent = parent;
        this.forceSuperScreen = forceSuperScreen;
        this.autoResizeContainers = false;
        ensureDefaults();
    }

    public String getDesktopAppId() {
        return "scoreboard-designer";
    }

    public String getDesktopAppTitle() {
        return "Scoreboard Designer";
    }

    public String getDesktopAppIconPath() {
        return "change.png";
    }

    @Override
    public DesktopWindowBehavior getDesktopWindowBehavior() {
        return DesktopWindowBehavior.SINGLETON;
    }

    @Override
    public boolean shouldForceSuperScreen() {
        return desktopMode && forceSuperScreen;
    }

    @Override
    public void init() {
        super.init();
        buildHeader();
        buildInspectorPanel();
        refreshPreviewText();
    }

    @Override
    public void close() {
        if (desktopMode && isDesktopWindow()) {
            var overlay = ScreenManager.getInstance().getDesktopWindowsOverlay();
            if (overlay != null) {
                overlay.requestCloseWindowForScreen(this);
                return;
            }
        }
        super.close();
        if (parent != null) {
            if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
                RemotelyClient.INSTANCE.getHost().openParentScreen(this, parent);
            } else if (parent instanceof Screen screen) {
                ScreenManager.getInstance().setScreen(screen);
            }
        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateLayout();
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        renderPreview(context);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    private void buildHeader() {
        header().reset();
        if (shouldShowBackButton()) {
            header().addRight("close.png", this::close, "Back");
        }
        header().addRight("save.png", this::saveScoreboard, "Save Scoreboard");
        header().build();
    }

    private boolean shouldShowBackButton() {
        return !desktopMode || shouldForceSuperScreen();
    }

    private void buildInspectorPanel() {
        if (inspectorPanel == null) {
            inspectorStudioPanel = rightStudioPanel("scoreboard_inspector")
                .show();
            inspectorPanel = inspectorStudioPanel.sidePanel();
        }
        Container container = inspectorPanel.container();
        inspectorStudioPanel.padding(panelState.padding());
        int rowWidth = inspectorStudioPanel.rowWidth();
        if (titleInput != null && objectiveInput != null && linesInput != null) {
            if (!titleInput.isFocused()) {
                titleInput.setText(scoreboard.getTitle() != null ? scoreboard.getTitle() : "");
            }
            if (!objectiveInput.isFocused()) {
                objectiveInput.setText(scoreboard.getObjectiveId() != null ? scoreboard.getObjectiveId() : "");
            }
            if (!linesInput.isFocused()) {
                linesInput.setText(String.join("\n", scoreboard.getLines()));
            }
            return;
        }

        titleInput = new TextInputWidget.Builder()
            .text(scoreboard.getTitle() != null ? scoreboard.getTitle() : "")
            .placeholder("Title")
            .forcePlaceholder(false)
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onChange(text -> {
                scoreboard.setTitle(text != null ? text : "");
                refreshPreviewText();
            })
            .build();
        ReSyncStudioPanelState.disableEntrance(titleInput);
        container.addWidget(panelState.row("Title", titleInput, rowWidth, scoreboardPanelDescription("Title")));

        objectiveInput = new TextInputWidget.Builder()
            .text(scoreboard.getObjectiveId() != null ? scoreboard.getObjectiveId() : "")
            .placeholder("Objective ID")
            .forcePlaceholder(false)
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onChange(this::updateObjective)
            .build();
        ReSyncStudioPanelState.disableEntrance(objectiveInput);
        container.addWidget(panelState.row("Objective", objectiveInput, rowWidth, scoreboardPanelDescription("Objective")));

        linesInput = new CodeEditorWidget(0, 0, rowWidth, 220);
        linesInput.setText(String.join("\n", scoreboard.getLines()));
        linesInput.onChange = t -> updateLines(linesInput.getText());
        container.addWidget(panelState.codeRow("Lines", linesInput, rowWidth, 238, scoreboardPanelDescription("Lines")));
    }

    private String scoreboardPanelDescription(String label) {
        return switch (label) {
            case "Title" -> "Sidebar display title.\nMinecraft renders this as the scoreboard objective display name.";
            case "Objective" -> "Internal scoreboard objective id.\nKeep it stable because live updates target this id.";
            case "Lines" -> "Sidebar lines under the title.\nMinecraft shows up to 15 visible rows.\nFirst line appears at the top.";
            default -> "";
        };
    }

    private void updateLayout() {
        if (inspectorPanel != null) {
            if (inspectorStudioPanel != null) {
                inspectorStudioPanel.layout();
            }
        }
    }

    private void renderPreview(IDrawContext context) {
        List<String> lines = previewLines;
        int maxLines = Math.min(15, lines.size());
        String titleText = previewTitle.isEmpty() ? formatPreviewText(scoreboard.getId()) : previewTitle;

        int titleWidth = textWidth(titleText);
        int maxRowWidth = 0;
        for (int i = 0; i < maxLines; i++) {
            String lineText = lines.get(i) != null ? lines.get(i) : "";
            int scoreWidth = textWidth(String.valueOf(maxLines - i));
            maxRowWidth = Math.max(maxRowWidth, textWidth(lineText) + scoreWidth + 8);
        }

        int panelWidth = Math.max(90, Math.max(titleWidth, maxRowWidth) + 10);
        int areaX = getPreviewAreaX();
        int areaTop = getPreviewAreaTop();
        int areaWidth = getPreviewAreaWidth();
        int areaHeight = getPreviewAreaHeight();
        panelWidth = Math.clamp(panelWidth, 90, areaWidth);
        int rowHeight = 11;
        int panelHeight = (maxLines + 1) * rowHeight + 6;
        panelHeight = Math.clamp(panelHeight, 10, areaHeight);
        int x = areaX + Math.max(0, (areaWidth - panelWidth) / 2);
        int y = areaTop + Math.max(0, (areaHeight - panelHeight) / 2);

        context.fill(x, y, x + panelWidth, y + rowHeight + 2, PREVIEW_ROW_BG);
        int titleTextMaxWidth = Math.max(0, panelWidth - 6);
        String titleDraw = fitLineToWidth(titleText, titleTextMaxWidth);
        int titleX = x + (panelWidth - textWidth(titleDraw)) / 2;
        context.drawText(titleDraw, titleX, y + 2, TITLE_COLOR, true);

        for (int i = 0; i < maxLines; i++) {
            int rowY = y + rowHeight + 2 + i * rowHeight;
            context.fill(x, rowY, x + panelWidth, rowY + rowHeight, PREVIEW_ROW_BG);
            String line = lines.get(i) != null ? lines.get(i) : "";
            String score = String.valueOf(maxLines - i);
            int scoreX = x + panelWidth - textWidth(score) - 3;
            int lineX = x + 3;
            int lineMaxWidth = Math.max(0, scoreX - lineX - 2);
            context.drawText(fitLineToWidth(line, lineMaxWidth), lineX, rowY + 1, TEXT_COLOR, true);
            context.drawText(score, scoreX, rowY + 1, SCORE_COLOR, true);
        }
    }

    private String fitLineToWidth(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        String value = text != null ? text : "";
        if (textWidth(value) <= maxWidth) {
            return value;
        }
        int end = value.length();
        while (end > 0 && textWidth(value.substring(0, end)) > maxWidth) {
            end--;
        }
        return value.substring(0, end);
    }

    private int getPreviewAreaX() {
        return PANEL_PADDING;
    }

    private int getPreviewAreaTop() {
        return header().headerSize + 5;
    }

    private int getPreviewAreaWidth() {
        int rightWidth = inspectorPanel != null ? (int) inspectorPanel.getAnimatedWidth() : 0;
        int availableWidth = width - rightWidth - PANEL_PADDING * 2;
        return Math.max(120, availableWidth);
    }

    private int getPreviewAreaHeight() {
        int contentTop = getPreviewAreaTop();
        return Math.max(120, height - contentTop - PANEL_PADDING);
    }

    private String formatPreviewText(String text) {
        return miniMessageToSection(text != null ? text : "").replace('&', '§');
    }

    private int textWidth(String text) {
        String clean = stripSectionCodes(text);
        if (RemotelyClient.tr != null) {
            return RemotelyClient.tr.getWidth(clean);
        }
        return clean.length() * 6;
    }

    private String stripSectionCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private String miniMessageToSection(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String withHex = replaceMiniHex(input);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < withHex.length(); i++) {
            char c = withHex.charAt(i);
            if (c != '<') {
                out.append(c);
                continue;
            }
            int end = withHex.indexOf('>', i);
            if (end <= i) {
                out.append(c);
                continue;
            }
            String tag = withHex.substring(i + 1, end).trim().toLowerCase();
            String mapped = mapMiniTag(tag);
            if (mapped != null) {
                out.append(mapped);
            }
            i = end;
        }
        return out.toString();
    }

    private String replaceMiniHex(String input) {
        Matcher matcher = MINI_HEX_PATTERN.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1).toUpperCase();
            String replacement = "§x§" + hex.charAt(0) + "§" + hex.charAt(1) + "§" + hex.charAt(2)
                + "§" + hex.charAt(3) + "§" + hex.charAt(4) + "§" + hex.charAt(5);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String mapMiniTag(String tag) {
        return switch (tag) {
            case "/black", "/dark_blue", "/dark_green", "/dark_aqua", "/dark_red", "/dark_purple", "/gold", "/gray", "/dark_gray",
                 "/blue", "/green", "/aqua", "/red", "/light_purple", "/yellow", "/white", "/bold", "/italic", "/underlined",
                 "/strikethrough", "/obfuscated", "/reset" -> "§r";
            case "black" -> "§0";
            case "dark_blue" -> "§1";
            case "dark_green" -> "§2";
            case "dark_aqua" -> "§3";
            case "dark_red" -> "§4";
            case "dark_purple" -> "§5";
            case "gold" -> "§6";
            case "gray" -> "§7";
            case "dark_gray" -> "§8";
            case "blue" -> "§9";
            case "green" -> "§a";
            case "aqua" -> "§b";
            case "red" -> "§c";
            case "light_purple" -> "§d";
            case "yellow" -> "§e";
            case "white" -> "§f";
            case "bold" -> "§l";
            case "italic" -> "§o";
            case "underlined" -> "§n";
            case "strikethrough" -> "§m";
            case "obfuscated" -> "§k";
            case "reset" -> "§r";
            default -> null;
        };
    }

    private void updateObjective(String value) {
        if (value == null) {
            scoreboard.setObjectiveId("");
            return;
        }
        String normalized = value.trim();
        if (normalized.length() > 16) {
            normalized = normalized.substring(0, 16);
            if (objectiveInput != null && !normalized.equals(objectiveInput.getText())) {
                objectiveInput.setText(normalized);
            }
        }
        scoreboard.setObjectiveId(normalized);
    }

    private void updateLines(String value) {
        List<String> lines = new ArrayList<>();
        if (value != null) {
            String normalized = value.replace("\r", "");
            if (!normalized.isEmpty()) {
                Collections.addAll(lines, normalized.split("\n", -1));
            }
        }
        scoreboard.setLines(lines);
        refreshPreviewText();
    }

    private void refreshPreviewText() {
        String rawTitle = scoreboard.getTitle() != null && !scoreboard.getTitle().isEmpty() ? scoreboard.getTitle() : scoreboard.getId();
        List<String> sourceLines = scoreboard.getLines() != null ? scoreboard.getLines() : List.of();
        String localTitle = formatPreviewText(rawTitle);
        List<String> localLines = new ArrayList<>();
        for (String line : sourceLines) {
            localLines.add(formatPreviewText(line));
        }
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager == null || serverId == null) {
            previewTitle = localTitle;
            previewLines = localLines;
            return;
        }
        if (previewTitle.isEmpty()) {
            previewTitle = localTitle;
        }
        if (previewLines.isEmpty()) {
            previewLines = localLines;
        }
        int requestRevision = ++previewRequestRevision;
        flowManager.resolvePlaceholderPreview(serverId, rawTitle, rendered -> {
            if (requestRevision == previewRequestRevision && rendered != null) {
                previewTitle = formatPreviewText(rendered);
            }
        });
        String joined = String.join("\u0001", sourceLines);
        int expectedLines = sourceLines.size();
        flowManager.resolvePlaceholderPreview(serverId, joined, rendered -> applyResolvedPreviewLines(rendered, requestRevision, expectedLines));
    }

    private void applyResolvedPreviewLines(String joined, int requestRevision, int expectedLines) {
        if (requestRevision != previewRequestRevision || joined == null) {
            return;
        }
        if (expectedLines <= 0) {
            previewLines = new ArrayList<>();
            return;
        }
        List<String> resolved = new ArrayList<>();
        for (String part : joined.split("\u0001", -1)) {
            resolved.add(formatPreviewText(part));
        }
        previewLines = resolved;
    }

    private void saveScoreboard() {
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager != null && serverId != null) {
            flowManager.saveScoreboard(serverId, scoreboard);
        } else {
            String title = scoreboard.getTitle() != null ? scoreboard.getTitle() : scoreboard.getId();
            new Notification("Scoreboard Saved", title != null ? title : "Scoreboard", Notification.Type.SUCCESS);
        }
    }

    private void ensureDefaults() {
        if (scoreboard.getTitle() == null || scoreboard.getTitle().isBlank()) {
            scoreboard.setTitle(scoreboard.getId() != null ? scoreboard.getId() : "Scoreboard");
        }
        if (scoreboard.getObjectiveId() == null || scoreboard.getObjectiveId().isBlank()) {
            scoreboard.setObjectiveId(scoreboard.getId() != null ? scoreboard.getId() : "scoreboard");
        }
        if (scoreboard.getDisplaySlot() == null || scoreboard.getDisplaySlot().isBlank()) {
            scoreboard.setDisplaySlot(ScoreboardDefinition.SLOT_SIDEBAR);
        }
        if (scoreboard.getLines() == null) {
            scoreboard.setLines(new ArrayList<>());
        }
    }
}

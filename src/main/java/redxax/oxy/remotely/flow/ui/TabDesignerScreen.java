package redxax.oxy.remotely.flow.ui;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.DesignerSaveNotifications;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.flow.ui.studio.StudioPanel;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static restudio.rescreen.config.Config.desktopMode;

public class TabDesignerScreen extends StudioScreen implements DesktopWindowBehaviorProvider, StudioCloseHandledScreen {
    private static final int PANEL_PADDING = 8;
    private static final int PREVIEW_BG = 0x7F101010;
    private static final int PREVIEW_TEXT = 0xFFFFFFFF;
    private static final Pattern MINI_HEX_PATTERN = Pattern.compile("<#([0-9a-fA-F]{6})>");

    private final TabDefinition tab;
    private final String serverId;
    private final Object parent;
    private final boolean forceSuperScreen;
    private final boolean animateTopHeader;
    private final ReSyncStudioPanelState panelState = new ReSyncStudioPanelState();

    private StudioPanel inspectorStudioPanel;
    private SidePanel inspectorPanel;
    private CodeEditorWidget headerInput;
    private TextInputWidget entryFormatInput;
    private CodeEditorWidget footerInput;
    private String previewHeader = "";
    private String previewEntry = "%player%";
    private String previewFooter = "";
    private Runnable studioCloseHandler;
    private boolean closingRequested;
    private boolean closeCompleted;
    private boolean studioCloseNotified;

    public TabDesignerScreen(TabDefinition tab) {
        this(tab, null, null);
    }

    public TabDesignerScreen(TabDefinition tab, String serverId, Object parent) {
        this(tab, serverId, parent, !(parent instanceof Screen));
    }

    public TabDesignerScreen(TabDefinition tab, String serverId, Object parent, boolean forceSuperScreen) {
        this(tab, serverId, parent, forceSuperScreen, false);
    }

    public TabDesignerScreen(TabDefinition tab, String serverId, Object parent, boolean forceSuperScreen, boolean animateTopHeader) {
        this.tab = tab;
        this.serverId = serverId;
        this.parent = parent;
        this.forceSuperScreen = forceSuperScreen;
        this.animateTopHeader = animateTopHeader;
        this.autoResizeContainers = false;
        ensureDefaults();
    }

    public String getDesktopAppId() {
        return "tab-designer";
    }

    public String getDesktopAppTitle() {
        return "Tab Designer";
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
        closingRequested = false;
        closeCompleted = false;
        studioCloseNotified = false;
        buildHeader();
        if (animateTopHeader) {
            startTopHeaderOpeningAnimation();
        }
        buildInspectorPanel();
        refreshPreviewText();
    }

    @Override
    public void close() {
        requestClose();
    }

    private void requestClose() {
        if (closeCompleted) {
            return;
        }
        if (isDesktopWindow()) {
            var overlay = ScreenManager.getInstance().getDesktopWindowsOverlay();
            if (overlay != null) {
                overlay.requestCloseWindowForScreen(this);
                return;
            }
        }
        if (!closingRequested) {
            closingRequested = true;
            if (animateTopHeader) {
                startTopHeaderClosingAnimation();
            }
            notifyStudioCloseStarted();
            if (inspectorPanel != null) {
                inspectorPanel.hide();
            }
        }
        updateCloseAnimation();
    }

    private void updateCloseAnimation() {
        if (!closingRequested || closeCompleted) {
            return;
        }
        if ((inspectorPanel == null || inspectorPanel.getAnimatedWidth() <= 1f) && (!animateTopHeader || isTopHeaderAnimationFinished())) {
            finishClose();
        }
    }

    private void notifyStudioCloseStarted() {
        if (studioCloseHandler != null && !studioCloseNotified) {
            studioCloseNotified = true;
            studioCloseHandler.run();
        }
    }

    private void finishClose() {
        if (closeCompleted) {
            return;
        }
        closeCompleted = true;
        if (studioCloseHandler != null) {
            if (!studioCloseNotified) {
                studioCloseNotified = true;
                studioCloseHandler.run();
            }
            return;
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
    public void renderHandler(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.renderHandler(context, mouseX, mouseY, delta);
        if (inspectorStudioPanel != null && inspectorPanel != null && inspectorPanel.isVisible()) {
            renderStudioPanel(inspectorStudioPanel, context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void setStudioCloseHandler(Runnable closeHandler) {
        this.studioCloseHandler = closeHandler;
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateTopHeaderAnimation();
        updateLayout();
        updateCloseAnimation();
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (!forceSuperScreen) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
        renderPreview(context);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inspectorPanel != null && inspectorPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (inspectorPanel != null && inspectorPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (inspectorPanel != null && inspectorPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inspectorPanel != null && inspectorPanel.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inspectorPanel != null && inspectorPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inspectorPanel != null && inspectorPanel.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void buildHeader() {
        header().reset();
        if (shouldShowBackButton()) {
            header().addRight("close.png", this::close, "Back");
        }
        header().addRight("save.png", this::saveTab, "Save Tab");
        header().build();
    }

    private boolean shouldShowBackButton() {
        return !desktopMode || shouldForceSuperScreen();
    }

    private void buildInspectorPanel() {
        if (inspectorPanel == null) {
            inspectorStudioPanel = rightStudioPanel("tab_inspector")
                .show();
            inspectorPanel = inspectorStudioPanel.sidePanel();
        }
        Container container = inspectorPanel.container();
        inspectorStudioPanel.padding(panelState.padding());
        int rowWidth = inspectorStudioPanel.rowWidth();
        if (headerInput != null && entryFormatInput != null && footerInput != null) {
            if (!headerInput.isFocused()) {
                headerInput.setText(tab.getHeader());
            }
            if (!entryFormatInput.isFocused()) {
                entryFormatInput.setText(tab.getEntryFormat() != null ? tab.getEntryFormat() : "%player%");
            }
            if (!footerInput.isFocused()) {
                footerInput.setText(tab.getFooter());
            }
            return;
        }

        headerInput = new CodeEditorWidget(0, 0, rowWidth, 100);
        headerInput.setText(tab.getHeader());
        headerInput.onChange = (t) -> {
            tab.setHeader(headerInput.getText());
            refreshPreviewText();
        };
        container.addWidget(panelState.codeRow("Header", headerInput, rowWidth, 118, tabPanelDescription("Header")));

        entryFormatInput = new TextInputWidget.Builder()
            .text(tab.getEntryFormat() != null ? tab.getEntryFormat() : "%player%")
            .placeholder("Entry")
            .forcePlaceholder(false)
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onChange(this::updateEntryFormat)
            .build();
        ReSyncStudioPanelState.disableEntrance(entryFormatInput);
        container.addWidget(panelState.row("Entry", entryFormatInput, rowWidth, tabPanelDescription("Entry")));

        footerInput = new CodeEditorWidget(0, 0, rowWidth, 100);
        footerInput.setText(tab.getFooter());
        footerInput.onChange = (t) -> {
            tab.setFooter(footerInput.getText());
            refreshPreviewText();
        };
        container.addWidget(panelState.codeRow("Footer", footerInput, rowWidth, 118, tabPanelDescription("Footer")));
    }

    private String tabPanelDescription(String label) {
        return switch (label) {
            case "Header" -> "Text above the player list in the tab overlay.\nSupports multiple lines.";
            case "Entry" -> "Format for each player row.\n%player% is replaced in the preview.\nRuntime placeholders depend on the synced tab renderer.";
            case "Footer" -> "Text below the player list in the tab overlay.\nSupports multiple lines.";
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
        List<String> headerLines = splitPreviewLines(previewHeader);
        List<String> footerLines = splitPreviewLines(previewFooter);
        List<String> playerLines = new ArrayList<>();
        String format = previewEntry != null ? previewEntry : "%player%";
        String[] players = {"RedxAx", "Steve", "Alex", "Player"};
        for (String player : players) {
            playerLines.add(format.replace("%player%", player));
        }

        List<String> renderLines = new ArrayList<>(headerLines);
        if (!headerLines.isEmpty()) {
            renderLines.add("");
        }
        renderLines.addAll(playerLines);
        if (!footerLines.isEmpty()) {
            renderLines.add("");
        }
        renderLines.addAll(footerLines);

        int maxTextWidth = 0;
        for (String line : renderLines) {
            maxTextWidth = Math.max(maxTextWidth, textWidth(line));
        }
        int horizontalPadding = 1;
        int verticalPadding = 1;
        int lineHeight = 10;
        int areaX = getPreviewAreaX();
        int areaTop = getPreviewAreaTop();
        int areaWidth = getPreviewAreaWidth();
        int areaHeight = getPreviewAreaHeight();

        int panelWidth = Math.max(2, maxTextWidth + horizontalPadding * 2);
        int panelHeight = Math.max(2, verticalPadding * 2 + renderLines.size() * lineHeight);
        panelWidth = Math.clamp(panelWidth, 2, areaWidth);
        panelHeight = Math.clamp(panelHeight, 2, areaHeight);

        int x = areaX + Math.max(0, (areaWidth - panelWidth) / 2);
        int y = areaTop + Math.max(0, (areaHeight - panelHeight) / 2);
        context.fill(x, y, x + panelWidth, y + panelHeight, PREVIEW_BG);

        int contentMaxWidth = Math.max(0, panelWidth - horizontalPadding * 2);
        List<String> drawLines = new ArrayList<>(renderLines.size());
        int maxDrawWidth = 0;
        for (String line : renderLines) {
            String drawLine = fitLineToWidth(line, contentMaxWidth);
            drawLines.add(drawLine);
            maxDrawWidth = Math.max(maxDrawWidth, textWidth(drawLine));
        }
        int drawX = x + (panelWidth - maxDrawWidth) / 2;
        int cursor = y + verticalPadding;
        for (String drawLine : drawLines) {
            context.drawText(drawLine, drawX, cursor, PREVIEW_TEXT, true);
            cursor += lineHeight;
        }
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
        return Math.max(2, availableWidth);
    }

    private int getPreviewAreaHeight() {
        int contentTop = getPreviewAreaTop();
        return Math.max(120, height - contentTop - PANEL_PADDING);
    }

    private void refreshPreviewText() {
        previewHeader = formatPreviewText(tab.getHeader());
        previewEntry = formatPreviewText(tab.getEntryFormat() != null ? tab.getEntryFormat() : "%player%");
        previewFooter = formatPreviewText(tab.getFooter());
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager == null || serverId == null) {
            return;
        }
        flowManager.resolvePlaceholderPreview(serverId, tab.getHeader(), rendered -> previewHeader = formatPreviewText(rendered));
        flowManager.resolvePlaceholderPreview(serverId, tab.getEntryFormat(), rendered -> previewEntry = formatPreviewText(rendered));
        flowManager.resolvePlaceholderPreview(serverId, tab.getFooter(), rendered -> previewFooter = formatPreviewText(rendered));
    }

    private void updateEntryFormat(String value) {
        String format = value != null && !value.isEmpty() ? value : "%player%";
        if (!format.contains("%player%")) {
            format = format + " %player%";
            if (entryFormatInput != null && !format.equals(entryFormatInput.getText())) {
                entryFormatInput.setText(format);
            }
        }
        tab.setEntryFormat(format);
        refreshPreviewText();
    }

    private String formatPreviewText(String text) {
        return miniMessageToSection(text != null ? text : "").replace('&', '§');
    }

    private List<String> splitPreviewLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String normalized = text.replace("\r", "");
        Collections.addAll(lines, normalized.split("\n", -1));
        return lines;
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

    private void saveTab() {
        FlowManager flowManager = FlowManager.getInstance();
        DesignerSaveNotifications.start(serverId, ReSyncResourceType.TAB, tab.getId(), tab.getId());
        if (flowManager != null && serverId != null) {
            flowManager.saveTab(serverId, tab);
        } else {
            DesignerSaveNotifications.failResource(serverId, ReSyncResourceType.TAB, tab.getId(), "ReSync Offline");
        }
    }

    private void ensureDefaults() {
        if (tab.getId() == null || tab.getId().isBlank()) {
            tab.setId("main");
        }
        if (tab.getEntryFormat() == null || tab.getEntryFormat().isEmpty()) {
            tab.setEntryFormat("%player%");
        }
    }
}

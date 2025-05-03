package redxax.oxy.remotely.terminal;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.Render.ContextMenu;
import redxax.oxy.remotely.Render.ScrollBar;
import redxax.oxy.remotely.Render.TabsBar;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.explorer.DeskSelectionScreen;
import redxax.oxy.remotely.explorer.FileEditorScreen;
import redxax.oxy.remotely.explorer.FileExplorerScreen;
import redxax.oxy.remotely.servers.*;
import redxax.oxy.remotely.ui.AISidePanel;
import redxax.oxy.remotely.util.ImageUtil.IconWithTooltip;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import redxax.oxy.remotely.util.Sound;

import static redxax.oxy.remotely.RemotelyClient.globalSnippets;
import static redxax.oxy.remotely.RemotelyClient.mcScreen;
import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.SoundUtils.playSound;


public class MultiTerminalScreen extends Screen {

    private final MinecraftClient minecraftClient;
    private final RemotelyClient remotelyClient;
    final List<TerminalInstance> terminals;
    final List<String> tabNames;
    public int activeTerminalIndex = 0;

    public static final int TAB_HEIGHT = 18;
    public static int ContentYStart;

    private float scale = 1.0f;
    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 2.0f;

    public static boolean isRenaming = false;
    int renamingTabIndex = -1;
    StringBuilder renameBuffer = new StringBuilder();
    long lastRenameInputTime = 0;
    int renameCursorPos = 0;

    private boolean closedViaEscape = false;
    private String warningMessage = "";

    private static final Path TERMINAL_LOG_DIR = Paths.get(System.getProperty("user.dir"), "remotely_terminal_logs");
    final List<String> commandHistory = new ArrayList<>();
    int historyIndex = -1;

    int snippetPanelWidth = 150;
    boolean creatingSnippet = false;
    boolean editingSnippet = false;
    int editingSnippetIndex = -1;
    StringBuilder snippetNameBuffer = new StringBuilder();
    StringBuilder snippetCommandsBuffer = new StringBuilder();
    StringBuilder snippetShortcutBuffer = new StringBuilder();
    boolean snippetPopupActive = false;
    int snippetPopupX;
    int snippetPopupY;
    int snippetPopupWidth = 250;
    int snippetPopupHeight = 150;
    boolean snippetNameFocused = true;
    long snippetLastBlinkTime = 0;
    long snippetLastInputTime = 0;
    int snippetNameCursorPos = 0;
    int snippetCommandsCursorPos = 0;
    boolean snippetCreationWarning = false;
    boolean snippetRecordingKeys = false;
    boolean showSnippetsPanel;
    float tabScrollOffset = 0;
    int tabPadding = 5;
    int verticalPadding = 2;
    int lastClickedSnippet = -1;
    long lastSnippetClickTime = 0;
    int snippetHoverIndex = -1;
    int selectedSnippetIndex = -1;
    int snippetCommandsScrollOffset = 0;
    int snippetMaxVisibleLines = 1;
    private boolean shortcutConsumed = false;
    public static boolean isResizingSnippetPanel = false;
    TabsBar<Void> tabsBar;

    public final Map<UUID, MergeGroup> mergeGroups = new LinkedHashMap<>();
    private int focusedPanelIndex = 0;
    private final List<Float> gridColumnWeights = new ArrayList<>();
    private final List<Float> gridRowWeights = new ArrayList<>();
    private int gridRows = 1;
    private int gridColumns = 1;
    private final int paneGap = 1;
    private int focusedPaneIndex = 0;
    private boolean draggingVerticalDivider = false;
    private boolean draggingHorizontalDivider = false;
    private int dividerIndex = -1;
    private int horizontalDividerIndex = -1;
    private float dragStartPos = 0;
    private List<Float> dragStartColWeights = new ArrayList<>();
    private List<Float> dragStartRowWeights = new ArrayList<>();
    private final List<Float> currentColumnWeights = new ArrayList<>();
    private final List<Float> currentRowWeights    = new ArrayList<>();

    public static class MergeGroup {
        List<TerminalInstance> members = new ArrayList<>();
        MergeGroup(TerminalInstance t) { members.add(t); }
    }

    int snippetNameScrollOffset = 0;

    private final int topBarHeight = 30;

    public static final Path THEMES_DIR = remotelyDir.resolve("themes");

    private final Screen parent;
    private IconWithTooltip closeIcon, startIcon, stopIcon, explorerIcon, resourcesIcon, snippetsIcon, aiIcon;
    private float targetSnippetListScrollOffset = 0;
    private float snippetListScrollOffset;
    private final Map<Integer, Float> snippetExpandProgress = new HashMap<>();
    private float animatedSnippetPanelWidth = 0;
    private int draggingSnippetIndex = -1;
    private boolean isDraggingSnippet = false;
    private float draggingStartY = 0;
    private float draggingCurrentY = 0;
    private final Map<RemotelyClient.CommandSnippet, Float> snippetAnimatedY = new HashMap<>();
    private final RemotelyClient.CommandSnippet CREATE_SNIPPET = new RemotelyClient.CommandSnippet("Create Snippet", "Snippets Executes Commands", "");
    AISidePanel aiSidePanel;

    public MultiTerminalScreen(MinecraftClient minecraftClient, Screen parent, RemotelyClient remotelyClient, List<TerminalInstance> terminals, List<String> tabNames) {
        super(Text.literal("Multi Terminal"));
        this.minecraftClient = minecraftClient;
        this.remotelyClient = remotelyClient;
        this.terminals = terminals;
        this.tabNames = tabNames;
        this.parent = parent;
        if (!(parent instanceof FileExplorerScreen || parent instanceof PluginModManagerScreen || parent instanceof ServerManagerScreen || parent instanceof FileEditorScreen || parent instanceof DeskSelectionScreen || parent instanceof BrowserScreen || parent instanceof MultiTerminalScreen)) {
            RemotelyClient.mcScreen = parent;
        }
        if (terminals.isEmpty()) {
            addNewTerminal();
        }
        this.activeTerminalIndex = remotelyClient.activeTerminalIndex;
        this.scale = remotelyClient.scale;
        this.snippetPanelWidth = remotelyClient.snippetPanelWidth;
        this.showSnippetsPanel = remotelyClient.showSnippetsPanel;
        this.snippetLastBlinkTime = System.currentTimeMillis();
        originalMCScale = minecraftClient.getWindow().getScaleFactor();
        targetScaleFactor = globalScaleFactor;
        minecraftClient.getWindow().setScaleFactor(globalScaleFactor);
        if (!globalSnippets.contains(CREATE_SNIPPET)) {
            for (RemotelyClient.CommandSnippet c : globalSnippets) {
                if (c.name.equals("Create Snippet")) {
                    globalSnippets.remove(c);
                    break;
                }
            }
            globalSnippets.add(CREATE_SNIPPET);
        }
        if (remotelyClient.multiMergeGroups != null && !remotelyClient.multiMergeGroups.isEmpty()) {
            mergeGroups.putAll(remotelyClient.multiMergeGroups);
        } else {
            for (TerminalInstance t : terminals) {
                mergeGroups.put(t.terminalId, new MergeGroup(t));
            }
        }
        initializeWeights();

        List<TabsBar.Tab<Void>> tabList = new ArrayList<>();
        for (String name : tabNames) {
            tabList.add(new TabsBar.Tab<>(name, false, null));
        }
        tabsBar = new TabsBar<>(tabList);

        tabsBar.setActiveTab(activeTerminalIndex);
        tabsBar.setHasPlus(true);
        tabsBar.setAllowClose(tabCloseButtons);
        tabsBar.setAllowRename(false);
        tabsBar.setAllowDrag(true);
        tabsBar.setAllowScroll(true);

        tabsBar.setOnTabOrderChanged(() -> {
            List<String> oldNames = new ArrayList<>(tabNames);
            List<TerminalInstance> oldTerms = new ArrayList<>(terminals);

            tabNames.clear();
            terminals.clear();
            for (TabsBar.Tab<Void> t : tabsBar.getTabs()) {
                tabNames.add(t.name);
                int idx = oldNames.indexOf(t.name);
                if (idx >= 0) {
                    terminals.add(oldTerms.get(idx));
                } else {
                    terminals.add(new TerminalInstance(minecraftClient, this, UUID.randomUUID()));
                }
            }
            remotelyClient.multiTabNames = new ArrayList<>(tabNames);
            remotelyClient.multiTerminals = new ArrayList<>(terminals);
        });

        tabsBar.setOnTabClosed(() -> closeTerminal(tabsBar.getActiveTab()));

        tabsBar.setOnTabSelected(() -> {
            activeTerminalIndex = tabsBar.getActiveTab();
            initializeWeights();
            playSound(Sound.SWITCHTAB);
        });

        tabsBar.setOnTabPlus(() -> {
            addNewTerminal();
            tabsBar.getTabs().add(new TabsBar.Tab<>("Tab " + terminals.size(), false, null));
            tabsBar.setActiveTab(terminals.size() - 1);
            playSound(Sound.CREATE);
        });

        tabsBar.setOnTabRenamed(() -> {
            int i = tabsBar.getRenamingTab();
            tabNames.set(i, tabsBar.getTabs().get(i).name);
            remotelyClient.multiTabNames = new ArrayList<>(tabNames);
        });
    }

    public MultiTerminalScreen(MinecraftClient minecraftClient, Screen parent, RemotelyClient remotelyClient) {
        this(minecraftClient, parent, remotelyClient, new ArrayList<>(remotelyClient.multiTerminals), new ArrayList<>(remotelyClient.multiTabNames));
    }

    public MultiTerminalScreen(MinecraftClient minecraftClient,Screen parent, RemotelyClient remotelyClient, ServerInfo serverInfo) {
        this(minecraftClient, parent, remotelyClient);
        addNewServerTab(serverInfo);
    }

    @Override
    protected void init() {
        super.init();
        try {
            if (!Files.exists(THEMES_DIR)) {
                Files.createDirectories(THEMES_DIR);
            }
            closeIcon = new IconWithTooltip("/assets/remotely/icons/close.png", "");
            startIcon = new IconWithTooltip("/assets/remotely/icons/start.png", "Start The Server");
            stopIcon = new IconWithTooltip("/assets/remotely/icons/stop.png", "Stop The Server");
            explorerIcon = new IconWithTooltip("/assets/remotely/icons/explorer.png", "Open File Explorer In The Current Path");
            resourcesIcon = new IconWithTooltip("/assets/remotely/icons/resources.png", "Open Resource Browser");
            snippetsIcon = new IconWithTooltip("/assets/remotely/icons/snippets.png", "Open Snippets Panel");
            aiIcon = new IconWithTooltip("/assets/remotely/icons/ReemotelyAI.png", "Open RemotelyAI Panel");
        } catch (Exception ignored) {}
        aiSidePanel = new AISidePanel();
        List<TabsBar.Tab<Void>> tabList = new ArrayList<>();
        for (String name : tabNames) {
            tabList.add(new TabsBar.Tab<>(name, false, null));
        }
        tabsBar = new TabsBar<>(tabList);

        tabsBar.setActiveTab(activeTerminalIndex);
        tabsBar.setHasPlus(true);
        tabsBar.setAllowClose(tabCloseButtons);
        tabsBar.setAllowRename(false);
        tabsBar.setAllowDrag(true);
        tabsBar.setAllowScroll(true);

        tabsBar.setOnTabOrderChanged(() -> {
            List<String> oldNames = new ArrayList<>(tabNames);
            List<TerminalInstance> oldTerms = new ArrayList<>(terminals);

            tabNames.clear();
            terminals.clear();
            for (TabsBar.Tab<Void> t : tabsBar.getTabs()) {
                tabNames.add(t.name);
                int idx = oldNames.indexOf(t.name);
                if (idx >= 0) {
                    terminals.add(oldTerms.get(idx));
                } else {
                    terminals.add(new TerminalInstance(minecraftClient, this, UUID.randomUUID()));
                }
            }
            remotelyClient.multiTabNames = new ArrayList<>(tabNames);
            remotelyClient.multiTerminals = new ArrayList<>(terminals);
        });

        tabsBar.setOnTabClosed(() -> closeTerminal(tabsBar.getActiveTab()));
        tabsBar.setOnTabSelected(() -> activeTerminalIndex = tabsBar.getActiveTab());
        tabsBar.setOnTabPlus(() -> {
            addNewTerminal();
            tabsBar.getTabs().add(new TabsBar.Tab<>("Tab " + terminals.size(), false, null));
            tabsBar.setActiveTab(terminals.size() - 1);
        });
        tabsBar.setOnTabRenamed(() -> {
            int i = tabsBar.getRenamingTab();
            tabNames.set(i, tabsBar.getTabs().get(i).name);
            remotelyClient.multiTabNames = new ArrayList<>(tabNames);
        });
    }

    private void addNewTerminal() {
        UUID terminalId = UUID.randomUUID();
        TerminalInstance newTerminal = new TerminalInstance(minecraftClient, this, terminalId);
        terminals.add(newTerminal);
        tabNames.add("Tab " + terminals.size());
        activeTerminalIndex = terminals.size() - 1;
        remotelyClient.multiTerminals = new ArrayList<>(terminals);
        remotelyClient.multiTabNames = new ArrayList<>(tabNames);
        mergeGroups.put(terminalId, new MergeGroup(newTerminal));
        initializeWeights();
    }

    private void addNewServerTab(ServerInfo serverInfo) {
        if (serverInfo.isRemote && serverInfo.remoteHost == null) {
            devPrint("[Terminal] Remote ServerInfo Has a Null RemoteHostInfo, Attempting to Remap...");
            for (TerminalInstance terminal : terminals) {
                if (terminal instanceof ServerTerminalInstance serverTerminal) {
                    ServerInfo sInfo = serverTerminal.getServerInfo();
                    if (sInfo.remoteHost != null) {
                        devPrint("[Terminal] Found a Remote Tab With a Null RemoteHostInfo, Remapping...");
                        serverInfo.remoteHost = sInfo.remoteHost;
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < terminals.size(); i++) {
            TerminalInstance terminal = terminals.get(i);
            if (terminal instanceof ServerTerminalInstance serverTerminal) {
                if (serverTerminal.getServerInfo().path.equals(serverInfo.path)) {
                    setActiveTerminal(i);
                    return;
                }
            }
        }
        UUID terminalId = UUID.randomUUID();
        ServerTerminalInstance newTerminal = new ServerTerminalInstance(minecraftClient, null, terminalId, serverInfo);
        newTerminal.isServerTerminal = true;
        newTerminal.serverName = serverInfo.name;
        newTerminal.serverJarPath = Paths.get(serverInfo.path, "server.jar").toString().replace("\\", "/");
        terminals.add(newTerminal);
        tabNames.add(serverInfo.name);
        activeTerminalIndex = terminals.size() - 1;
        remotelyClient.multiTerminals = new ArrayList<>(terminals);
        remotelyClient.multiTabNames = new ArrayList<>(tabNames);
        mergeGroups.put(terminalId, new MergeGroup(newTerminal));
        initializeWeights();
    }

    private void closeTerminal(int index) {
        if (terminals.size() <= 1) {
            if (parent != null) {
                minecraftClient.setScreen(parent);
            } else {
                this.close();
            }
        }
        TerminalInstance terminal = terminals.get(index);
        terminal.shutdown();
        terminals.remove(index);
        tabNames.remove(index);
        tabsBar.closeTab(index);
        if (activeTerminalIndex >= terminals.size()) {
            activeTerminalIndex = terminals.size() - 1;
        }
        remotelyClient.multiTerminals = new ArrayList<>(terminals);
        remotelyClient.multiTabNames = new ArrayList<>(tabNames);
    }

    private void setActiveTerminal(int index) {
        if (index >= 0 && index < terminals.size()) {
            activeTerminalIndex = index;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (!terminals.isEmpty()) {
            TerminalInstance safeTerminal = terminals.get(Math.min(activeTerminalIndex, terminals.size() - 1));
            MergeGroup mg = mergeGroups.get(safeTerminal.terminalId);
            TerminalInstance headerTerminal = safeTerminal;
            if (mg != null && !mg.members.isEmpty()) {
                if (mg.members.size() > 1) {
                    int safeFocusedPaneIndex = Math.min(focusedPaneIndex, mg.members.size() - 1);
                    headerTerminal = mg.members.get(safeFocusedPaneIndex);
                }
            }
            if (headerTerminal instanceof ServerTerminalInstance serverTerminal) {
                ServerInfo sInfo = serverTerminal.getServerInfo();
                boolean isProxy = List.of("velocity", "waterfall", "bungeecord").contains(sInfo.type.toLowerCase(Locale.getDefault()));
                ServerState st = sInfo.state;
                drawScreenHeader(context, width, height, width - 5, mouseX, mouseY, this, minecraftClient, closeIcon, explorerIcon, snippetsIcon, aiIcon, (st == ServerState.RUNNING || st == ServerState.STARTING) ? stopIcon : startIcon, isProxy ? null : resourcesIcon, null, null, null);
            } else {
                drawScreenHeader(context, width, height, width - 5, mouseX, mouseY, this, minecraftClient, closeIcon, explorerIcon, snippetsIcon, aiIcon, null, null, null, null, null);
            }
        }
        if (!warningMessage.isEmpty()) {
            context.drawText(minecraftClient.textRenderer, Text.literal(warningMessage), 5, TAB_HEIGHT + verticalPadding, 0xFFFF0000, shadow);
            warningMessage = "";
        }
        int tabOffsetY = topBarHeight + 5;
        int tabAreaHeight = TAB_HEIGHT;
        float targetPanelWidth = showSnippetsPanel ? snippetPanelWidth : 0;
        animatedSnippetPanelWidth += (targetPanelWidth - animatedSnippetPanelWidth) * Config.globalExpandSpeed * deltaTime;
        int animatedWidth = (int) animatedSnippetPanelWidth;
        tabsBar.setTabBarBounds(5, tabOffsetY, animatedWidth - 5, tabAreaHeight);
        tabsBar.renderTabsBar(context, minecraftClient.textRenderer, tabsBar, mouseX, mouseY, shadow);
        activeTerminalIndex = Math.min(tabsBar.getActiveTab(), terminals.size() - 1);
        if (terminals.isEmpty()) return;
        TerminalInstance activeTerminal = terminals.get(Math.min(activeTerminalIndex, terminals.size() - 1));
        int contentYStart = tabOffsetY + tabAreaHeight + verticalPadding;
        ContentYStart = contentYStart + 5;
        MergeGroup mergedGroup = mergeGroups.get(terminals.get(activeTerminalIndex).terminalId);

        if (mergedGroup != null && !mergedGroup.members.isEmpty() && mergedGroup.members.size() > 1) {
            int expectedCols = gridColumns;
            int expectedRows = gridRows;
            boolean needsInit = false;
            if (currentColumnWeights.size() != expectedCols || gridColumnWeights.size() != expectedCols) needsInit = true;
            if (currentRowWeights.size() != expectedRows || gridRowWeights.size() != expectedRows) needsInit = true;
            if (needsInit) {
                initializeWeights();
            }
            for (int i = 0; i < gridColumns; i++) {
                float curr = currentColumnWeights.get(i);
                float targ = gridColumnWeights.get(i);
                currentColumnWeights.set(i, curr + (targ - curr) * globalExpandSpeed * deltaTime);
            }
            for (int i = 0; i < gridRows; i++) {
                float curr = currentRowWeights.get(i);
                float targ = gridRowWeights.get(i);
                currentRowWeights.set(i, curr + (targ - curr) * globalExpandSpeed * deltaTime);
            }
        }
        if (mergedGroup == null || mergedGroup.members.isEmpty() || mergedGroup.members.size() == 1) {
            int terminalX = 5;
            int terminalY = ContentYStart;
            int terminalW = this.width - animatedWidth - 10;
            int terminalH = this.height - terminalY - 5;
            activeTerminal.renderer.render(context, terminalX, terminalY, terminalW, terminalH, this);
        } else {
            int contentH = this.height - ContentYStart - 5;
            renderSplitTerminals(context, this.width - animatedWidth - 10, contentH);
        }
        if (animatedWidth > 0) {
            int panelX = this.width - animatedWidth;
            int panelY = tabOffsetY + tabAreaHeight + 7;
            int panelHeight = this.height - panelY - 5;
            context.fill(panelX, panelY, panelX, panelY + panelHeight, innerBorderColor);
            context.fill(panelX, panelY, panelX + animatedWidth, panelY + panelHeight, innerBackgroundColor);
            drawInnerBorder(context, panelX, panelY, animatedWidth, panelHeight, innerBorderColor);
            drawOuterBorder(context, panelX, panelY, animatedWidth, panelHeight, globalOuterBorder);
            context.enableScissor(panelX, panelY + 1, panelX + animatedWidth, panelY + panelHeight - 1);
            if (aiMode) {
                aiSidePanel.render(context, panelX, panelY, animatedWidth, panelHeight, mouseX, mouseY);
            } else {
                renderSnippetsPanel(context, panelX, panelY, animatedWidth, panelHeight, mouseX, mouseY);
            }
            context.disableScissor();
        } else {
            int textAreaHeight = -activeTerminal.renderer.getInputFieldHeight() - activeTerminal.renderer.getStatusBarHeight();
            int scrollableRange = Math.max(0, activeTerminal.renderer.getTotalScrollHeight() - textAreaHeight);
            if (ScrollBar.isDragging()) activeTerminal.renderer.targetScrollOffset = (int) ScrollBar.getPendingOffset();
            ScrollBar.render(context, this, mouseX, mouseY, scrollableRange, activeTerminal.renderer.targetScrollOffset);
        }
        if (snippetPopupActive) {
            renderSnippetPopup(context, mouseX, mouseY);
        }
        if (ContextMenu.isOpen()) {
            ContextMenu.renderMenu(context, minecraftClient, mouseX, mouseY);
        }
        animatedScaling(this);
    }

    private void renderSnippetPopup(DrawContext context, int mouseX, int mouseY) {
        if (snippetPopupX + snippetPopupWidth > this.width) snippetPopupX = this.width - snippetPopupWidth - 5;
        if (snippetPopupY + snippetPopupHeight > this.height) snippetPopupY = this.height - snippetPopupHeight - 5;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 499);
        context.fill(snippetPopupX, snippetPopupY, snippetPopupX + snippetPopupWidth, snippetPopupY + snippetPopupHeight, backgroundColor);
        drawInnerBorder(context, snippetPopupX, snippetPopupY, snippetPopupWidth, snippetPopupHeight, elementBorderColor);
        drawOuterBorder(context, snippetPopupX, snippetPopupY, snippetPopupWidth, snippetPopupHeight, globalOuterBorder);
        int nameLabelY = snippetPopupY + 5;
        trimAndDrawText(context, "Name:", snippetPopupX + 5, nameLabelY, snippetPopupWidth - 10, globalTextColor);
        int nameBoxY = nameLabelY + 12;
        int nameBoxHeight = 12;
        int nameBoxWidth = snippetPopupWidth - 10;
        context.fill(snippetPopupX + 5, nameBoxY, snippetPopupX + 5 + nameBoxWidth, nameBoxY + nameBoxHeight, snippetNameFocused ? innerBackgroundSelectedColor : innerBackgroundColor);
        drawOuterBorder(context, snippetPopupX + 5, nameBoxY, nameBoxWidth, nameBoxHeight, globalOuterBorder);
        String fullName = snippetNameBuffer.toString();
        int wBeforeCursor = minecraftClient.textRenderer.getWidth(fullName.substring(0, Math.min(snippetNameCursorPos, fullName.length())));
        if (wBeforeCursor < snippetNameScrollOffset) snippetNameScrollOffset = wBeforeCursor;
        int maxVisibleWidth = nameBoxWidth - 6;
        if (wBeforeCursor - snippetNameScrollOffset > maxVisibleWidth) snippetNameScrollOffset = wBeforeCursor - maxVisibleWidth;
        if (snippetNameScrollOffset < 0) snippetNameScrollOffset = 0;
        int charStart = 0;
        while (charStart < fullName.length()) {
            int cw = minecraftClient.textRenderer.getWidth(fullName.substring(0, charStart));
            if (cw >= snippetNameScrollOffset) break;
            charStart++;
        }
        int visibleEnd = charStart;
        while (visibleEnd <= fullName.length()) {
            int cw = minecraftClient.textRenderer.getWidth(fullName.substring(charStart, visibleEnd));
            if (cw > maxVisibleWidth) break;
            visibleEnd++;
        }
        visibleEnd--;
        if (visibleEnd < charStart) visibleEnd = charStart;
        String visibleName = fullName.substring(charStart, visibleEnd);
        int nameTextX = snippetPopupX + 8;
        int nameTextY = nameBoxY + 2;
        context.drawText(minecraftClient.textRenderer, Text.literal(visibleName), nameTextX, nameTextY, globalTextColor, shadow);
        if (snippetNameFocused) {
            int cursorPosVisible = Math.min(snippetNameCursorPos - charStart, visibleName.length());
            if (cursorPosVisible < 0) cursorPosVisible = 0;
            int cX = nameTextX + minecraftClient.textRenderer.getWidth(visibleName.substring(0, Math.min(cursorPosVisible, visibleName.length())));
            context.fill(cX, nameTextY - 1, cX + 1, nameTextY + minecraftClient.textRenderer.fontHeight, globalCursorAnimatedColor);
        }
        int commandsLabelY = nameBoxY + nameBoxHeight + 8;
        trimAndDrawText(context, "Commands:", snippetPopupX + 5, commandsLabelY, snippetPopupWidth - 10, globalTextColor);
        int commandsBoxY = commandsLabelY + 12;
        int commandsBoxHeight = snippetPopupHeight - (commandsBoxY - snippetPopupY) - 60;
        if (commandsBoxHeight < 20) commandsBoxHeight = 20;
        int commandsBoxWidth = snippetPopupWidth - 10;
        context.fill(snippetPopupX + 5, commandsBoxY, snippetPopupX + 5 + commandsBoxWidth, commandsBoxY + commandsBoxHeight, !snippetNameFocused ? innerBackgroundSelectedColor : innerBackgroundColor);
        drawOuterBorder(context, snippetPopupX + 5, commandsBoxY, commandsBoxWidth, commandsBoxHeight, globalOuterBorder);
        String fullCommands = snippetCommandsBuffer.toString();
        fullCommands = ensureCursorBounds(fullCommands);
        String[] cmdLines = fullCommands.split("\n", -1);
        List<String> wrappedLines = wrapLines(cmdLines, commandsBoxWidth, minecraftClient.textRenderer);
        snippetMaxVisibleLines = Math.max(1, commandsBoxHeight / (minecraftClient.textRenderer.fontHeight + 2));
        if (snippetCommandsScrollOffset < 0) snippetCommandsScrollOffset = 0;
        if (snippetCommandsScrollOffset > Math.max(0, wrappedLines.size() - snippetMaxVisibleLines)) {
            snippetCommandsScrollOffset = Math.max(0, wrappedLines.size() - snippetMaxVisibleLines);
        }
        int firstVisibleLine = snippetCommandsScrollOffset;
        List<String> visibleCmdLines = getVisibleLines(wrappedLines, firstVisibleLine, snippetMaxVisibleLines);
        int commandsInnerX = snippetPopupX + 8;
        int commandsInnerY = commandsBoxY + 2;
        int cLineIndex = findCursorLine(wrappedLines, snippetCommandsCursorPos);
        if (cLineIndex < firstVisibleLine) {
            snippetCommandsScrollOffset = cLineIndex;
            firstVisibleLine = snippetCommandsScrollOffset;
            visibleCmdLines = getVisibleLines(wrappedLines, firstVisibleLine, snippetMaxVisibleLines);
        }
        if (cLineIndex >= firstVisibleLine + snippetMaxVisibleLines) {
            snippetCommandsScrollOffset = cLineIndex - (snippetMaxVisibleLines - 1);
            firstVisibleLine = snippetCommandsScrollOffset;
            visibleCmdLines = getVisibleLines(wrappedLines, firstVisibleLine, snippetMaxVisibleLines);
        }
        for (int i = 0; i < visibleCmdLines.size(); i++) {
            context.drawText(minecraftClient.textRenderer, Text.literal(visibleCmdLines.get(i)), commandsInnerX, commandsInnerY + i * (minecraftClient.textRenderer.fontHeight + 2), globalTextColor, shadow);
        }
        if (!snippetNameFocused) {
            String cursorLine = cLineIndex >= 0 && cLineIndex < wrappedLines.size() ? wrappedLines.get(cLineIndex) : "";
            int cPosInLine = cursorPosInLine(snippetCommandsCursorPos, wrappedLines, cLineIndex);
            if (cPosInLine < 0) cPosInLine = 0;
            if (cPosInLine > cursorLine.length()) cPosInLine = cursorLine.length();
            String beforeCursor = cursorLine.substring(0, cPosInLine);
            int cursorX = commandsInnerX + minecraftClient.textRenderer.getWidth(beforeCursor);
            int relativeLine = cLineIndex - firstVisibleLine;
            if (relativeLine < 0) relativeLine = 0;
            if (relativeLine >= snippetMaxVisibleLines) relativeLine = snippetMaxVisibleLines - 1;
            int cursorY = commandsInnerY + relativeLine * (minecraftClient.textRenderer.fontHeight + 2);
            context.fill(cursorX, cursorY - 2, cursorX + 1, cursorY + minecraftClient.textRenderer.fontHeight, globalCursorAnimatedColor);
        }
        int shortcutLabelY = commandsBoxY + commandsBoxHeight + 8;
        trimAndDrawText(context, snippetRecordingKeys ? "Shortcut (recording):" : "Shortcut:", snippetPopupX + 5, shortcutLabelY, snippetPopupWidth - 10, globalTextColor);
        int shortcutBoxY = shortcutLabelY + 12;
        int shortcutBoxHight = 12;
        int shortcutBoxWidth = snippetPopupWidth - 10;
        context.fill(snippetPopupX + 5, shortcutBoxY, snippetPopupX + 5 + shortcutBoxWidth, shortcutBoxY + shortcutBoxHight, innerBackgroundColor);
        drawOuterBorder(context, snippetPopupX + 5, shortcutBoxY, shortcutBoxWidth, shortcutBoxHight, globalOuterBorder);
        String shortcutText = snippetShortcutBuffer.isEmpty() ? "No Shortcut" : snippetShortcutBuffer.toString();
        shortcutText = trimTextToWidthWithEllipsis(shortcutText, shortcutBoxWidth - 2);
        context.drawText(minecraftClient.textRenderer, Text.literal(shortcutText), snippetPopupX + 8, shortcutBoxY + 2, globalTextColor, shadow);
        int recordX = snippetPopupX + snippetPopupWidth - shortcutBoxHight - 5;
        boolean recordHover = mouseX >= recordX && mouseX <= recordX + shortcutBoxHight && mouseY >= shortcutBoxY && mouseY <= shortcutBoxY + shortcutBoxHight;
        context.fill(recordX + 1, shortcutBoxY + 1, recordX + shortcutBoxHight - 1, shortcutBoxY + shortcutBoxHight - 1, recordHover ? 0xFF666666 : 0xFF555555);
        context.fill(recordX, shortcutBoxY, recordX + shortcutBoxHight, shortcutBoxY + 1, 0xFFAAAAAA);
        context.fill(recordX, shortcutBoxY, recordX + 1, shortcutBoxY + shortcutBoxHight, 0xFFAAAAAA);
        context.fill(recordX, shortcutBoxY + shortcutBoxHight - 1, recordX + shortcutBoxHight, shortcutBoxY + shortcutBoxHight, 0xFF333333);
        context.fill(recordX + shortcutBoxHight - 1, shortcutBoxY, recordX + shortcutBoxHight, shortcutBoxY + shortcutBoxHight, 0xFF333333);
        String recordText = "⏺";
        int rw = minecraftClient.textRenderer.getWidth(recordText);
        int rtx = recordX + (shortcutBoxHight - rw) / 2;
        int rty = shortcutBoxY + (shortcutBoxHight - minecraftClient.textRenderer.fontHeight) / 2;
        trimAndDrawText(context, recordText, rtx, rty, shortcutBoxHight, globalTextColor);
        int ButtonY = snippetPopupY + snippetPopupHeight - 22;
        String okText = "OK";
        int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
        int confirmButtonX = snippetPopupX + 5;
        boolean okHover = mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && mouseY >= ButtonY && mouseY <= ButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawCustomButton(context, confirmButtonX, ButtonY, okText, minecraftClient, okHover, true, true, false, true, 60, 20, globalTextColor, globalHoverTextColor, mouseX, mouseY, "");

        String cancelText = "Cancel";
        int cancelW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
        int cancelButtonX = snippetPopupX + snippetPopupWidth - (cancelW + 5);
        boolean cancelHover = mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelW && mouseY >= ButtonY && mouseY <= ButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawCustomButton(context, cancelButtonX, ButtonY, cancelText, minecraftClient, cancelHover, true, true, false, true, 60, 20, globalTextColor, dangerLightAccentColor, mouseX, mouseY, "");
        if (editingSnippet) {
            String deleteText = "Delete";
            int dw = minecraftClient.textRenderer.getWidth(deleteText) + 10;
            int deleteX = snippetPopupX + (snippetPopupWidth - dw) / 2;
            boolean delHover = mouseX >= deleteX && mouseX <= deleteX + dw && mouseY >= ButtonY && mouseY <= ButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawCustomButton(context, deleteX, ButtonY, deleteText, minecraftClient, delHover, true, true, false, true, 60, 20, dangerLightAccentColor, dangerDarkAccentColor, mouseX, mouseY, "");
        }
        if (snippetCreationWarning) {
            String warning = "Name/Code cannot be empty";
            int ww = minecraftClient.textRenderer.getWidth(warning);
            trimAndDrawText(context, warning, snippetPopupX + (snippetPopupWidth - ww) / 2, snippetPopupY + snippetPopupHeight - 30, snippetPopupWidth - 10, 0xFFFF0000);
        }
    }

    private void renderSnippetsPanel(DrawContext context, int panelX, int startY, int panelWidth, int panelHeight, int mouseX, int mouseY) {
        snippetListScrollOffset += (targetSnippetListScrollOffset - snippetListScrollOffset) * globalScrollSpeed * deltaTime;
        int currentSnippetMaxWidth = Math.max(0, panelWidth - 10);
        snippetHoverIndex = -1;
        Map<Integer, Float> targetPositions = new HashMap<>();
        float runningTarget;
        if (isDraggingSnippet && draggingSnippetIndex >= 0 && draggingSnippetIndex < globalSnippets.size()) {
            RemotelyClient.CommandSnippet draggedSnippet = globalSnippets.get(draggingSnippetIndex);
            int draggedHeight = (selectedSnippetIndex == draggingSnippetIndex ? calculateSnippetHeight(draggedSnippet.commands) : 35);
            float dropY = draggingCurrentY - snippetListScrollOffset;
            float cumulative = startY + 5;
            int dropIndex = -1;
            int order = 0;
            for (int i = 0; i < globalSnippets.size(); i++) {
                if (i == draggingSnippetIndex) continue;
                RemotelyClient.CommandSnippet s = globalSnippets.get(i);
                int h = (selectedSnippetIndex == i ? calculateSnippetHeight(s.commands) : 35);
                float midY = cumulative + h / 2.0f;
                if (dropY < midY) {
                    dropIndex = order;
                    break;
                }
                cumulative += h + 5;
                order++;
            }
            if (dropIndex == -1) {
                dropIndex = order;
            }
            runningTarget = startY + 5 - snippetListScrollOffset;
            order = 0;
            for (int i = 0; i < globalSnippets.size(); i++) {
                if (order == dropIndex) {
                    runningTarget += draggedHeight + 5;
                }
                if (i == draggingSnippetIndex) continue;
                RemotelyClient.CommandSnippet s = globalSnippets.get(i);
                int h = (selectedSnippetIndex == i ? calculateSnippetHeight(s.commands) : 35);
                targetPositions.put(i, runningTarget);
                runningTarget += h + 5;
                order++;
            }
        }
        float openFactor = panelWidth / (float) snippetPanelWidth;
        float delayPerSnippet = 0.05f;
        runningTarget = startY + 5 - snippetListScrollOffset;
        for (int i = 0; i < globalSnippets.size(); i++) {
            if (isDraggingSnippet && i == draggingSnippetIndex) {
                continue;
            }
            RemotelyClient.CommandSnippet snippet = globalSnippets.get(i);
            boolean selected = selectedSnippetIndex == i;
            float expandProgress = snippetExpandProgress.getOrDefault(i, selected ? 1.0f : 0.0f);
            int baseHeight = 35;
            int fullExpandedHeight = calculateSnippetHeight(snippet.commands);
            int snippetHeight = baseHeight + (int) ((fullExpandedHeight - baseHeight) * expandProgress);
            float effectiveSlide = (openFactor - i * delayPerSnippet) / (1.0f - i * delayPerSnippet);
            effectiveSlide = Math.min(Math.max(effectiveSlide, 0), 1);
            int targetX = panelX + 5;
            int startX = this.width + 5;
            int snippetX = (int) (startX + (targetX - startX) * effectiveSlide);
            float targetY;
            if (isDraggingSnippet) {
                targetY = targetPositions.getOrDefault(i, startY + 5f - snippetListScrollOffset);
            } else {
                targetY = runningTarget;
                runningTarget += snippetHeight + 5;
            }
            float currentY = snippetAnimatedY.getOrDefault(snippet, targetY);
            float newY = currentY + (targetY - currentY) * snippetAnimationSpeed * deltaTime;
            snippetAnimatedY.put(snippet, newY);
            int snippetY = (int) newY;
            if (snippetY + snippetHeight < startY + 5 || snippetY > startY + panelHeight - 5) {
                continue;
            }
            int boxTop = Math.max(snippetY, startY + 5);
            int boxBottom = Math.min(snippetY + snippetHeight, startY + panelHeight - 5);
            boolean hovered = mouseX >= snippetX && mouseX <= snippetX + currentSnippetMaxWidth && mouseY >= boxTop && mouseY <= boxBottom;
            if (hovered) {
                snippetHoverIndex = i;
            }
            renderSnippetBox(context, snippetX, snippetY, currentSnippetMaxWidth, snippetHeight, snippet, hovered, selected, minecraftClient);
        }
        if (isDraggingSnippet && draggingSnippetIndex >= 0 && draggingSnippetIndex < globalSnippets.size()) {
            RemotelyClient.CommandSnippet draggedSnippet = globalSnippets.get(draggingSnippetIndex);
            int draggedHeight = (selectedSnippetIndex == draggingSnippetIndex ? calculateSnippetHeight(draggedSnippet.commands) : 35);
            int drawY = (int) (draggingCurrentY - draggedHeight / 2.0f - snippetListScrollOffset);
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 499);
            renderSnippetBox(context, panelX + 5, drawY, currentSnippetMaxWidth, draggedHeight, draggedSnippet, false, false, minecraftClient);
            context.getMatrices().pop();
        }
        for (int j = 0; j < globalSnippets.size(); j++) {
            float targetExpand = (selectedSnippetIndex == j) ? 1.0f : 0.0f;
            float currentExpand = snippetExpandProgress.getOrDefault(j, targetExpand);
            if (Math.abs(currentExpand - targetExpand) > 0.01f) {
                currentExpand += (targetExpand - currentExpand) * globalExpandSpeed * deltaTime;
                snippetExpandProgress.put(j, currentExpand);
            } else {
                snippetExpandProgress.put(j, targetExpand);
            }
        }
    }

    private int calculateSnippetHeight(String commands) {
        String[] lines = commands.split("\n", -1);
        int fontHeight = minecraftClient.textRenderer.fontHeight;
        return 15 + fontHeight + lines.length * (fontHeight + 2);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ContextMenu.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        ContextMenu.hide();
        if (tabsBar.handleTabsBarMouse((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            MergeGroup mg = mergeGroups.get(terminals.get(activeTerminalIndex).terminalId);
            TerminalInstance headerTerminal = mg.members.size() > 1 ? mg.members.get(focusedPaneIndex) : activeTerminal;
            int textAreaHeight = -activeTerminal.renderer.getInputFieldHeight() - activeTerminal.renderer.getStatusBarHeight();
            int scrollableRange = Math.max(0, activeTerminal.renderer.getTotalScrollHeight() - textAreaHeight);
            if (aiMode && aiSidePanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (!showSnippetsPanel && ScrollBar.handleMousePressed(this, (int) mouseX, (int) mouseY, scrollableRange + 3, activeTerminal.renderer.getScrollOffset())) {
                return true;
            }
            if (button == 0 && mouseX >= width - 23 && mouseX <= width - 6 && mouseY >= 6 && mouseY <= 24) {
                if (parent != null) {
                    minecraftClient.setScreen(parent);
                } else {
                    this.close();
                    playSound(Sound.SCREEN);
                    closedViaEscape = true;
                }
                return true;
            }
            if (mouseX >= width - 69 && mouseX <= width - 52 && mouseY >= 6 && mouseY <= 24 && button == 0) {
                playSound(Sound.PANEL);
                if (aiMode && showSnippetsPanel) aiMode = false;
                else {
                    showSnippetsPanel = !showSnippetsPanel;
                    aiMode = false;
                }
                return true;
            }
            if (mouseX >= width - 92 && mouseX <= width - 75 && mouseY >= 6 && mouseY <= 24 && button == 0) {
                playSound(Sound.PANEL);
                if (aiMode && showSnippetsPanel) showSnippetsPanel = false;
                else {
                    aiMode = true;
                    showSnippetsPanel = true;
                }
                return true;
            }
            if (headerTerminal instanceof ServerTerminalInstance serverTerminal) {
                if (button == 0) {
                    if (mouseX >= 5 && mouseX <= 22 && mouseY >= 6 && mouseY <= 24) {
                        ServerInfo sInfo = serverTerminal.getServerInfo();
                        if (sInfo.state == ServerState.RUNNING || sInfo.state == ServerState.STARTING) {
                            playSound(Sound.STOP);
                            try {
                                if (sInfo.isRemote && sInfo.remoteHost != null && sInfo.remoteSSHManager != null && sInfo.remoteSSHManager.getSshWriter() != null) {
                                    sInfo.remoteSSHManager.getSshWriter().write("stop\n");
                                    sInfo.remoteSSHManager.getSshWriter().flush();
                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(2000);
                                            String sessionName = "server_" + Integer.toHexString(sInfo.path.hashCode());
                                            devPrint(sInfo.remoteSSHManager.runRemoteCommandWithOutput("tmux kill-session -t " + sessionName + " 2>/dev/null"));
                                        } catch (InterruptedException e) {
                                            devPrint("Error while stopping server: " + e.getMessage());
                                        }
                                    }).start();
                                } else if (serverTerminal.processManager != null && serverTerminal.processManager.writer != null) {
                                    serverTerminal.processManager.writer.write("stop\n");
                                    serverTerminal.processManager.writer.flush();
                                }
                            } catch (IOException ignored) {}
                            sInfo.state = ServerState.STOPPED;
                        } else {
                            playSound(Sound.START);
                            if (sInfo.state == ServerState.STOPPED || sInfo.state == ServerState.CRASHED) {
                                if (!serverTerminal.isServerTerminal) {
                                    serverTerminal.isServerTerminal = true;
                                }
                                if (serverTerminal.serverName == null) {
                                    serverTerminal.serverName = sInfo.name;
                                }
                                serverTerminal.clearOutput();
                                serverTerminal.launchServerProcess();
                                sInfo.state = ServerState.STARTING;
                            }
                        }
                        return true;
                    }
                    if (mouseX >= width - 46 && mouseX <= width - 29 && mouseY >= 6 && mouseY <= 24) {
                        minecraftClient.setScreen(new FileExplorerScreen(minecraftClient, this, serverTerminal.getServerInfo()));
                        return true;
                    }
                    if (mouseX >= 28 && mouseX <= 45 && mouseY >= 6 && mouseY <= 24) {
                        minecraftClient.setScreen(new PluginModManagerScreen(minecraftClient, this, serverTerminal.getServerInfo()));
                        return true;
                    }
                }
            } else {
                if (button == 0) {
                    if (mouseX >= width - 46 && mouseX <= width - 29 && mouseY >= 6 && mouseY <= 24) {
                        minecraftClient.setScreen(new FileExplorerScreen(minecraftClient, this, new ServerInfo(headerTerminal.getCurrentDir())));
                        return true;
                    }
                }
            }
        }
        if (snippetPopupActive) {
            int confirmButtonY = snippetPopupY + snippetPopupHeight - 15;
            String okText = "OK";
            int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
            int confirmButtonX = snippetPopupX + 5;
            int cancelButtonX = snippetPopupX + snippetPopupWidth - (minecraftClient.textRenderer.getWidth("Cancel") + 10 + 5);
            String deleteText = "Delete";
            int dw = minecraftClient.textRenderer.getWidth(deleteText) + 10;
            int deleteX = snippetPopupX + (snippetPopupWidth - dw) / 2;
            int commandsLabelY = snippetPopupY + 5 + 12 + 8;
            int commandsBoxY = commandsLabelY + 12;
            int commandsBoxHeight = snippetPopupHeight - (commandsBoxY - snippetPopupY) - 60;
            if (commandsBoxHeight < 20) commandsBoxHeight = 20;
            int shortcutLabelY = commandsBoxY + commandsBoxHeight + 8;
            int shortcutBoxY = shortcutLabelY + 12;
            int rbSize = 12;
            int recordX = snippetPopupX + snippetPopupWidth - rbSize - 5;
            if (mouseX >= recordX && mouseX <= recordX + rbSize && mouseY >= shortcutBoxY && mouseY <= shortcutBoxY + rbSize && button == 0) {
                playSound(Sound.CLICK);
                snippetRecordingKeys = !snippetRecordingKeys;
                snippetShortcutBuffer.setLength(0);
                return true;
            }
            if (editingSnippet && button == 0 && mouseX >= deleteX && mouseX <= deleteX + dw && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight) {
                playSound(Sound.CREATE);
                if (editingSnippetIndex >= 0 && editingSnippetIndex < globalSnippets.size()) {
                    globalSnippets.remove(editingSnippetIndex);
                    remotelyClient.saveSnippets();
                }
                creatingSnippet = false;
                editingSnippet = false;
                editingSnippetIndex = -1;
                snippetPopupActive = false;
                snippetNameBuffer.setLength(0);
                snippetCommandsBuffer.setLength(0);
                snippetShortcutBuffer.setLength(0);
                snippetNameCursorPos = 0;
                snippetCommandsCursorPos = 0;
                snippetCreationWarning = false;
                snippetRecordingKeys = false;
                return true;
            }
            if (mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight) {
                playSound(Sound.CREATE);
                if (mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && button == 0) {
                    if (snippetNameBuffer.toString().trim().isEmpty() || snippetCommandsBuffer.toString().trim().isEmpty()) {
                        return true;
                    }
                    if (creatingSnippet) {
                        globalSnippets.add(new RemotelyClient.CommandSnippet(snippetNameBuffer.toString().trim(), snippetCommandsBuffer.toString().trim(), snippetShortcutBuffer.toString().trim()));
                        remotelyClient.saveSnippets();
                    }
                    if (editingSnippet && editingSnippetIndex >= 0 && editingSnippetIndex < globalSnippets.size()) {
                        RemotelyClient.CommandSnippet s = globalSnippets.get(editingSnippetIndex);
                        s.name = snippetNameBuffer.toString().trim();
                        s.commands = snippetCommandsBuffer.toString().trim();
                        s.shortcut = snippetShortcutBuffer.toString().trim();
                        remotelyClient.saveSnippets();
                    }
                    creatingSnippet = false;
                    editingSnippet = false;
                    editingSnippetIndex = -1;
                    snippetPopupActive = false;
                    snippetNameBuffer.setLength(0);
                    snippetCommandsBuffer.setLength(0);
                    snippetShortcutBuffer.setLength(0);
                    snippetNameCursorPos = 0;
                    snippetCommandsCursorPos = 0;
                    snippetCreationWarning = false;
                    snippetRecordingKeys = false;
                    return true;
                }
                if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + (minecraftClient.textRenderer.getWidth("Cancel") + 10) && button == 0) {
                    playSound(Sound.CLICK);
                    creatingSnippet = false;
                    editingSnippet = false;
                    editingSnippetIndex = -1;
                    snippetPopupActive = false;
                    snippetNameBuffer.setLength(0);
                    snippetCommandsBuffer.setLength(0);
                    snippetShortcutBuffer.setLength(0);
                    snippetNameCursorPos = 0;
                    snippetCommandsCursorPos = 0;
                    snippetCreationWarning = false;
                    snippetRecordingKeys = false;
                    return true;
                }
            }
            int nameBoxY = snippetPopupY + 5 + 12;
            int nameBoxHeight = 12;
            commandsBoxY = commandsLabelY + 12;
            commandsBoxHeight = snippetPopupHeight - (commandsBoxY - snippetPopupY) - 60;
            if (commandsBoxHeight < 20) commandsBoxHeight = 20;
            if (mouseX >= snippetPopupX + 5 && mouseX <= snippetPopupX + snippetPopupWidth - 5 && mouseY >= nameBoxY && mouseY <= nameBoxY + nameBoxHeight && button == 0) {
                playSound(Sound.CLICK);
                snippetNameFocused = true;
                snippetCommandsCursorPos = Math.min(snippetCommandsCursorPos, snippetCommandsBuffer.length());
                snippetNameCursorPos = Math.min(snippetNameCursorPos, snippetNameBuffer.length());
                int cx = (int) (mouseX - (snippetPopupX + 8));
                snippetNameCursorPos = getCursorFromMouseX(snippetNameBuffer.toString(), cx);
                return true;
            }
            if (mouseX >= snippetPopupX + 5 && mouseX <= snippetPopupX + snippetPopupWidth - 5 && mouseY >= commandsBoxY && mouseY <= commandsBoxY + commandsBoxHeight && button == 0) {
                playSound(Sound.CLICK);
                snippetNameFocused = false;
                snippetCommandsCursorPos = 0;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!terminals.isEmpty()) {
            for (int i = 0; i < terminals.size(); i++) {
                minecraftClient.textRenderer.getWidth(tabNames.get(i));
            }
            int plusW = 20;
            int tabOffsetY = topBarHeight + 5;
            int tabAreaHeight = TAB_HEIGHT;
            float renderX = 5 - tabScrollOffset;
            for (int i = 0; i < terminals.size(); i++) {
                String tName = tabNames.get(i);
                int tabW = textRenderer.getWidth(tName) + 2 * tabPadding;
                float renderX2 = renderX + tabW;
                if (mouseX >= renderX && mouseX <= renderX2 && mouseY >= tabOffsetY && mouseY <= tabOffsetY + tabAreaHeight) {
                    if (button == 1) {
                        playSound(Sound.RIGHTCLICK);
                        int finalI = i;
                        ContextMenu.addItem("Close", () -> closeTerminal(finalI), false, false, false, "Close Terminal");
                        if (finalI != activeTerminalIndex) ContextMenu.addItem("Merge", () -> mergeTerminal(finalI), false, false, false, "Merge Terminal / Split Screen");
                        ContextMenu.addItem("Rename", () -> tabsBar.renameTab(finalI), false, false, false, "Rename Terminal");
                        ContextMenu.show((int) mouseX, (int) mouseY, 60, this.width, this.height);
                        return true;
                    } else if (button == 2) {
                        playSound(Sound.CLOSETAB);
                        closeTerminal(i);
                        return true;
                    } else if (button == 0) {
                        if (!isRenaming) {
                            playSound(Sound.SWITCHTAB);
                            setActiveTerminal(i);
                            return true;
                        } else {
                            playSound(Sound.CLICK);
                            isRenaming = false;
                        }
                    }
                }
                renderX += tabW + tabPadding;
            }
            if (mouseX >= renderX && mouseX <= renderX + plusW && mouseY >= tabOffsetY && mouseY <= tabOffsetY + tabAreaHeight && button == 0) {
                playSound(Sound.CREATE);
                addNewTerminal();
                return true;
            }
        }
        if (showSnippetsPanel) {
            int panelX = this.width - snippetPanelWidth;
            int tabOffsetY = topBarHeight + 5;
            int panelY = tabOffsetY + TAB_HEIGHT + verticalPadding;
            if (mouseX < panelX || mouseX > this.width || mouseY < panelY || mouseY > panelY + (this.height - panelY - 5)) {
                selectedSnippetIndex = -1;
            }
            if (Math.abs(mouseX - (panelX - 1)) < 5 && mouseY >= panelY && mouseY <= panelY + (this.height - panelY - 5) && button == 0) {
                isResizingSnippetPanel = true;
                return true;
            }
            if (!terminals.isEmpty()) {
                TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                if (mouseX < panelX - 1) {
                    if (activeTerminal.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
            float yOffset = panelY + 5 - snippetListScrollOffset;
            int snippetMaxWidth = snippetPanelWidth - 10;
            for (int i = 0; i < globalSnippets.size(); i++) {
                RemotelyClient.CommandSnippet snippet = globalSnippets.get(i);
                int snippetHeight = selectedSnippetIndex == i ? calculateSnippetHeight(snippet.commands) : 35;
                int snippetX = panelX + 5;
                float snippetY = yOffset;
                if (snippetY + snippetHeight < panelY + 5 || snippetY > panelY + (this.height - panelY - 5)) {
                    yOffset += snippetHeight + 5;
                    continue;
                }
                boolean hovered = mouseX >= snippetX && mouseX <= snippetX + snippetMaxWidth && mouseY >= Math.max(snippetY, panelY + 5) && mouseY <= Math.min(snippetY + snippetHeight, panelY + (this.height - panelY - 5));
                if (hovered && !aiMode) {
                    if (snippet.equals(CREATE_SNIPPET)) {
                        if (button == 0) {
                            selectedSnippetIndex = i;
                            long currentTime = System.currentTimeMillis();
                            if (lastClickedSnippet == i && (currentTime - lastSnippetClickTime) < 500) {
                                playSound(Sound.CLICK);
                                creatingSnippet = true;
                                snippetNameBuffer.setLength(0);
                                snippetCommandsBuffer.setLength(0);
                                snippetShortcutBuffer.setLength(0);
                                snippetPopupActive = true;
                                snippetPopupX = this.width / 2 - snippetPopupWidth / 2;
                                snippetPopupY = this.height / 2 - snippetPopupHeight / 2;
                                snippetNameFocused = true;
                                snippetNameCursorPos = 0;
                                snippetCommandsCursorPos = 0;
                                snippetCreationWarning = false;
                                snippetRecordingKeys = false;
                                snippetCommandsScrollOffset = 0;
                                return true;
                            } else {
                                lastClickedSnippet = i;
                                lastSnippetClickTime = currentTime;
                            }
                            return true;
                        }
                    } else {
                        if (button == 1) {
                            playSound(Sound.CLICK);
                            editingSnippet = true;
                            editingSnippetIndex = i;
                            RemotelyClient.CommandSnippet s = globalSnippets.get(i);
                            snippetNameBuffer.setLength(0);
                            snippetNameBuffer.append(s.name);
                            snippetCommandsBuffer.setLength(0);
                            snippetCommandsBuffer.append(s.commands);
                            snippetShortcutBuffer.setLength(0);
                            snippetShortcutBuffer.append(s.shortcut == null ? "" : s.shortcut);
                            snippetPopupActive = true;
                            snippetPopupX = this.width / 2 - snippetPopupWidth / 2;
                            snippetPopupY = this.height / 2 - snippetPopupHeight / 2;
                            snippetNameFocused = true;
                            snippetNameCursorPos = snippetNameBuffer.length();
                            snippetCommandsCursorPos = snippetCommandsBuffer.length();
                            snippetCreationWarning = false;
                            snippetRecordingKeys = false;
                            snippetCommandsScrollOffset = 0;
                            return true;
                        }
                        if (button == 0) {
                            if (!snippet.equals(CREATE_SNIPPET)) {
                                draggingSnippetIndex = i;
                                draggingStartY = (float) mouseY;
                                draggingCurrentY = (float) mouseY;
                                isDraggingSnippet = false;
                                if (selectedSnippetIndex == i) {
                                    selectedSnippetIndex = -1;
                                }
                                return true;
                            }
                        }
                    }
                }
                yOffset += snippetHeight + 5;
            }
        }
        MergeGroup mg = mergeGroups.get(terminals.get(activeTerminalIndex).terminalId);
        if (mg.members.size() > 1) {
            int gap = paneGap;
            int availW = (int) (this.width - animatedSnippetPanelWidth - 10);
            int availH = this.height - ContentYStart - 5;
            int baseX = 5;
            int baseY = ContentYStart;
            if (button == 0) {
                int xAcc = baseX;
                for (int c = 0; c < gridColumns - 1; c++) {
                    int cw = Math.round(currentColumnWeights.get(c) * availW);
                    xAcc += cw;
                    if (Math.abs(mouseX - xAcc) < 5 && mouseY >= baseY && mouseY <= baseY + availH + (gridRows - 1) * gap + 3) {
                        draggingVerticalDivider = true;
                        dividerIndex = c;
                        dragStartPos = (float) mouseX;
                        dragStartColWeights = new ArrayList<>(gridColumnWeights);
                        return true;
                    }
                    xAcc += gap;
                }
                int yAcc = baseY;
                for (int r = 0; r < gridRows - 1; r++) {
                    int rh = Math.round(currentRowWeights.get(r) * availH);
                    yAcc += rh;
                    if (Math.abs(mouseY - yAcc) < 5 && mouseX >= baseX && mouseX <= baseX + availW + (gridColumns - 1) * gap + 3) {
                        draggingHorizontalDivider = true;
                        horizontalDividerIndex = r;
                        dragStartPos = (float) mouseY;
                        dragStartRowWeights = new ArrayList<>(gridRowWeights);
                        return true;
                    }
                    yAcc += gap;
                }
            }
            int idx2   = 0;
            int yOff2  = baseY;
            for (int r = 0; r < gridRows; r++) {
                int rowH = Math.round(currentRowWeights.get(r) * availH);
                int xOff2 = baseX;
                for (int c = 0; c < gridColumns; c++) {
                    if (idx2 >= mg.members.size()) break;
                    int colW = Math.round(currentColumnWeights.get(c) * availW);
                    if (mouseX >= xOff2 && mouseX <= xOff2 + colW && mouseY >= yOff2 && mouseY <= yOff2 + rowH) {
                        TerminalInstance ti = mg.members.get(idx2);
                        if (button == 1) {
                            final int p = idx2;
                            ContextMenu.addItem("Unmerge", () -> unmergePanel(p), false, false, false, "Unmerge Pane");
                            ContextMenu.addItem("Close",   () -> closePanel(p), false, false, false, "Close Pane");
                            ContextMenu.show((int)mouseX,(int)mouseY,100,this.width,this.height);
                            return true;
                        } else if (button == 0) {
                            playSound(Sound.SELECT);
                            focusedPaneIndex = idx2;
                            return ti.mouseClicked(mouseX, mouseY, button);
                        }
                    }
                    xOff2 += colW + gap;
                    idx2++;
                }
                yOff2 += rowH + gap;
            }
        } else terminals.get(activeTerminalIndex).mouseClicked(mouseX, mouseY, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tabsBar != null && tabsBar.handleTabsBarRelease(button)) {
            return true;
        }
        if (button == 0 && (draggingVerticalDivider || draggingHorizontalDivider)) {
            draggingVerticalDivider = false;
            draggingHorizontalDivider = false;
            return true;
        }
        MergeGroup mg = mergeGroups.get(terminals.get(activeTerminalIndex).terminalId);
        if (mg.members.size() > 1) {
            TerminalInstance ti = mg.members.get(focusedPaneIndex);
            if (ti.mouseReleased(mouseX, mouseY, button)) return true;
        }
        if (button == 0 && draggingSnippetIndex != -1) {
            if (isDraggingSnippet) {
                RemotelyClient.CommandSnippet dragged = globalSnippets.get(draggingSnippetIndex);
                int draggedHeightCalc = (selectedSnippetIndex == draggingSnippetIndex ? calculateSnippetHeight(dragged.commands) : 35);
                float dropY = draggingCurrentY - snippetListScrollOffset;
                float draggedDrawY = dropY - draggedHeightCalc / 2.0f;
                snippetAnimatedY.put(dragged, draggedDrawY);
                int tabOffsetY = topBarHeight + 5;
                int panelY = tabOffsetY + TAB_HEIGHT + verticalPadding;
                float cumulative = panelY + 5 - snippetListScrollOffset;
                int newIndex = 0;
                for (int j = 0; j < globalSnippets.size(); j++) {
                    if (j == draggingSnippetIndex) continue;
                    RemotelyClient.CommandSnippet snip = globalSnippets.get(j);
                    int h = (selectedSnippetIndex == j ? calculateSnippetHeight(snip.commands) : 35);
                    float midY = cumulative + h / 2.0f;
                    if (dropY < midY) break;
                    cumulative += h + 5;
                    newIndex++;
                }
                if (draggingSnippetIndex < newIndex) {
                    newIndex--;
                }
                RemotelyClient.CommandSnippet draggedSnippet = globalSnippets.remove(draggingSnippetIndex);
                globalSnippets.add(newIndex, draggedSnippet);
                remotelyClient.saveSnippets();
            } else {
                long currentTime = System.currentTimeMillis();
                if (lastClickedSnippet == draggingSnippetIndex && (currentTime - lastSnippetClickTime) < 500) {
                    TerminalInstance t = terminals.get(activeTerminalIndex);
                    String[] lines = globalSnippets.get(draggingSnippetIndex).commands.split("\n");
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            try {
                                t.getInputHandler().commandExecutor.executeCommand(line.trim(), new StringBuilder(line.trim()));
                            } catch (IOException e) {
                                t.appendOutput("ERROR: " + e.getMessage() + "\n");
                            }
                        }
                    }
                } else {
                    lastClickedSnippet = draggingSnippetIndex;
                    lastSnippetClickTime = currentTime;
                    selectedSnippetIndex = draggingSnippetIndex;
                }
            }
            draggingSnippetIndex = -1;
            isDraggingSnippet = false;
            return true;
        }
        if (ScrollBar.handleMouseReleased()) {
            return true;
        }
        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            if (activeTerminal.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (button == 0 && isResizingSnippetPanel) {
            isResizingSnippetPanel = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isResizingSnippetPanel && button == 0) {
            int newWidth = this.width - (int) mouseX;
            snippetPanelWidth = Math.max(80, Math.min(newWidth, this.width - 50));
            return true;
        }
        if (draggingVerticalDivider && button == 0) {
            int availableWidth = (int) (this.width - animatedSnippetPanelWidth - 10);
            float delta = (float) mouseX - dragStartPos;
            float total = dragStartColWeights.get(dividerIndex) + dragStartColWeights.get(dividerIndex + 1);
            float newVal1 = dragStartColWeights.get(dividerIndex) + delta / availableWidth;
            float newVal2 = dragStartColWeights.get(dividerIndex + 1) - delta / availableWidth;
            if (newVal1 >= 0.05f && newVal2 >= 0.05f) {
                gridColumnWeights.set(dividerIndex, newVal1);
                gridColumnWeights.set(dividerIndex + 1, newVal2);
                float sum = gridColumnWeights.get(dividerIndex) + gridColumnWeights.get(dividerIndex + 1);
                gridColumnWeights.set(dividerIndex, gridColumnWeights.get(dividerIndex) / sum * total);
                gridColumnWeights.set(dividerIndex + 1, gridColumnWeights.get(dividerIndex + 1) / sum * total);
            }
            return true;
        }
        if (draggingHorizontalDivider && button == 0) {
            int availableHeight = this.height - ContentYStart - 5;
            float delta = (float) mouseY - dragStartPos;
            float total = dragStartRowWeights.get(horizontalDividerIndex) + dragStartRowWeights.get(horizontalDividerIndex + 1);
            float newVal1 = dragStartRowWeights.get(horizontalDividerIndex) + delta / availableHeight;
            float newVal2 = dragStartRowWeights.get(horizontalDividerIndex + 1) - delta / availableHeight;
            if (newVal1 >= 0.05f && newVal2 >= 0.05f) {
                gridRowWeights.set(horizontalDividerIndex, newVal1);
                gridRowWeights.set(horizontalDividerIndex + 1, newVal2);
                float sum = gridRowWeights.get(horizontalDividerIndex) + gridRowWeights.get(horizontalDividerIndex + 1);
                gridRowWeights.set(horizontalDividerIndex, gridRowWeights.get(horizontalDividerIndex) / sum * total);
                gridRowWeights.set(horizontalDividerIndex + 1, gridRowWeights.get(horizontalDividerIndex + 1) / sum * total);
            }
            return true;
        }
        if (tabsBar.handleTabsBarDrag(button, deltaX)) {
            return true;
        }
        MergeGroup mg = mergeGroups.get(terminals.get(activeTerminalIndex).terminalId);
        if (mg.members.size() > 1) {
            if (!draggingVerticalDivider && !draggingHorizontalDivider) {
                TerminalInstance ti = mg.members.get(focusedPaneIndex);
                return ti.mouseDragged(mouseX, mouseY, button);
            }
            return true;
        }
        if (draggingSnippetIndex != -1 && button == 0) {
            draggingCurrentY += (float) deltaY;
            if (!isDraggingSnippet && Math.abs(draggingCurrentY - draggingStartY) > 5) {
                isDraggingSnippet = true;
            }
            return true;
        }
        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            int textAreaHeight = -activeTerminal.renderer.getInputFieldHeight() - activeTerminal.renderer.getStatusBarHeight();
            int scrollableRange = Math.max(0, activeTerminal.renderer.getTotalScrollHeight() - textAreaHeight);
            if (ScrollBar.handleMouseDragged(this, (int) mouseY, scrollableRange + 3)) {
                return true;
            }
            if (activeTerminal.mouseDragged(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,/*? !=1.20.1 {*/ double horizontalAmount, /*?}*/ double verticalAmount) {
        if (aiMode && aiSidePanel.mouseScrolled(mouseX, mouseY, verticalAmount, this.width - (int) animatedSnippetPanelWidth - 5, ContentYStart, (int) animatedSnippetPanelWidth, this.height - ContentYStart - 5)) {
            return true;
        }
        if (tabsBar.handleTabsBarScroll(verticalAmount, mouseX, mouseY)) {
            return true;
        }
        boolean ctrlHeld = InputUtil.isKeyPressed(this.minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(this.minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL);
        scaleScroll(verticalAmount);
        MergeGroup mg = mergeGroups.get(terminals.get(activeTerminalIndex).terminalId);
        if (mg.members.size() > 1) {
            return mg.members.get(focusedPaneIndex).mouseScrolled(mouseX, mouseY, verticalAmount);
        }
        if (ctrlHeld) {
            scale += verticalAmount > 0 ? 0.1f : -0.1f;
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
            return true;
        } else {
            if (snippetPopupActive) {
                int nameBoxY = snippetPopupY + 5 + 12;
                int nameBoxHeight = 12;
                int commandsLabelY = nameBoxY + nameBoxHeight + 8;
                int commandsBoxY = commandsLabelY + 12;
                int commandsBoxHeight = snippetPopupHeight - (commandsBoxY - snippetPopupY) - 60;
                if (commandsBoxHeight < 20) commandsBoxHeight = 20;
                if (mouseY >= commandsBoxY && mouseY <= commandsBoxY + commandsBoxHeight) {
                    int scrollDir = verticalAmount >= 0 ? (int) Math.ceil(verticalAmount) : (int) Math.floor(verticalAmount);
                    snippetCommandsScrollOffset -= scrollDir;
                    return true;
                }
                return true;
            }
            int currentPanelWidth = (int) animatedSnippetPanelWidth;
            if (currentPanelWidth > 0) {
                int panelX = this.width - currentPanelWidth;
                int tabOffsetY = topBarHeight + 5;
                int panelY = tabOffsetY + TAB_HEIGHT + 7;
                int panelHeight = this.height - panelY - 5;
                if (mouseX >= panelX && mouseX <= this.width && mouseY >= panelY && mouseY <= panelY + panelHeight) {
                    int scrollDir = verticalAmount >= 0 ? (int) Math.ceil(verticalAmount * 20) : (int) Math.floor(verticalAmount * 20);
                    targetSnippetListScrollOffset -= scrollDir;
                    int totalHeight = 0;
                    for (int i = 0; i < globalSnippets.size(); i++) {
                        RemotelyClient.CommandSnippet snippet = globalSnippets.get(i);
                        int h = (selectedSnippetIndex == i ? calculateSnippetHeight(snippet.commands) : 35) + 5;
                        totalHeight += h;
                    }
                    int maxScroll = Math.max(0, totalHeight - (panelHeight - 10));
                    targetSnippetListScrollOffset = MathHelper.clamp(targetSnippetListScrollOffset, 0, maxScroll);
                    return true;
                }
            }
            if (!terminals.isEmpty()) {
                TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                int padding = 2;
                int textAreaHeight = activeTerminal.renderer.terminalHeight - 2 * padding - activeTerminal.renderer.getInputFieldHeight() - activeTerminal.renderer.getStatusBarHeight();
                int scrollDir = verticalAmount >= 0 ? (int) Math.ceil(verticalAmount) : (int) Math.floor(verticalAmount);
                activeTerminal.renderer.scroll(scrollDir, textAreaHeight);
                ScrollBar.setPendingOffset(activeTerminal.renderer.getScrollOffset());
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tabsBar.handleTabsBarKey(keyCode, scanCode, modifiers)) return true;
        if (aiMode && aiSidePanel.fieldFocused) {
            aiSidePanel.setExtraContext(mergeGroups.get(terminals.get(activeTerminalIndex).terminalId)
                    .members.get(focusedPaneIndex).getRenderer().getTerminalContext());
            if (aiSidePanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (snippetRecordingKeys) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                snippetShortcutBuffer.setLength(0);
                snippetRecordingKeys = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!snippetShortcutBuffer.isEmpty()) {
                    int i = snippetShortcutBuffer.lastIndexOf("+");
                    if (i >= 0) snippetShortcutBuffer.delete(i, snippetShortcutBuffer.length());
                    else snippetShortcutBuffer.setLength(0);
                } else snippetRecordingKeys = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                snippetRecordingKeys = false;
                return true;
            }
            String name = InputUtil.fromKeyCode(keyCode, scanCode).getTranslationKey();
            String hr = humanReadableKey(name);
            List<String> parts = new ArrayList<>(Arrays.asList(snippetShortcutBuffer.toString().split("\\+")));
            if (parts.size() == 1 && parts.get(0).isEmpty()) parts.clear();
            if (!parts.contains(hr)) {
                parts.add(hr);
                snippetShortcutBuffer.setLength(0);
                snippetShortcutBuffer.append(parts.stream().filter(s -> !s.isEmpty())
                        .collect(Collectors.joining("+")));
            }
            return true;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (snippetPopupActive) {
            snippetLastInputTime = System.currentTimeMillis();
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                if (!snippetNameFocused) {
                    snippetCommandsBuffer.insert(snippetCommandsCursorPos, '\n');
                    snippetCommandsCursorPos++;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                creatingSnippet = false;
                editingSnippet = false;
                editingSnippetIndex = -1;
                snippetPopupActive = false;
                snippetNameBuffer.setLength(0);
                snippetCommandsBuffer.setLength(0);
                snippetShortcutBuffer.setLength(0);
                snippetNameCursorPos = 0;
                snippetCommandsCursorPos = 0;
                snippetCreationWarning = false;
                snippetRecordingKeys = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (snippetNameFocused) {
                    if (snippetNameCursorPos > 0) {
                        snippetNameBuffer.deleteCharAt(snippetNameCursorPos - 1);
                        snippetNameCursorPos--;
                    }
                } else {
                    if (snippetCommandsCursorPos > 0) {
                        if (ctrl) {
                            int p = findPreviousWord(snippetCommandsBuffer.toString(), snippetCommandsCursorPos);
                            snippetCommandsBuffer.delete(p, snippetCommandsCursorPos);
                            snippetCommandsCursorPos = p;
                        } else {
                            snippetCommandsBuffer.deleteCharAt(snippetCommandsCursorPos - 1);
                            snippetCommandsCursorPos--;
                        }
                    }
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                if (!snippetNameFocused) moveCursorVertically(-1, snippetCommandsBuffer.toString());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                if (!snippetNameFocused) moveCursorVertically(1, snippetCommandsBuffer.toString());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
                if (snippetNameFocused) {
                    if (keyCode == GLFW.GLFW_KEY_LEFT && snippetNameCursorPos > 0) snippetNameCursorPos--;
                    if (keyCode == GLFW.GLFW_KEY_RIGHT && snippetNameCursorPos < snippetNameBuffer.length()) snippetNameCursorPos++;
                } else {
                    if (ctrl) {
                        if (keyCode == GLFW.GLFW_KEY_LEFT) snippetCommandsCursorPos = findPreviousWord(snippetCommandsBuffer.toString(), snippetCommandsCursorPos);
                        else snippetCommandsCursorPos = findNextWord(snippetCommandsBuffer.toString(), snippetCommandsCursorPos);
                    } else {
                        if (keyCode == GLFW.GLFW_KEY_LEFT && snippetCommandsCursorPos > 0) snippetCommandsCursorPos--;
                        if (keyCode == GLFW.GLFW_KEY_RIGHT && snippetCommandsCursorPos < snippetCommandsBuffer.length()) snippetCommandsCursorPos++;
                    }
                }
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                String clip = minecraftClient.keyboard.getClipboard();
                if (!clip.isEmpty()) {
                    if (snippetNameFocused) {
                        snippetNameBuffer.insert(snippetNameCursorPos, clip);
                        snippetNameCursorPos += clip.length();
                    } else {
                        snippetCommandsBuffer.insert(snippetCommandsCursorPos, clip);
                        snippetCommandsCursorPos += clip.length();
                    }
                }
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (parent != null) minecraftClient.setScreen(parent);
            else { this.close(); closedViaEscape = true; }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            if (!terminals.isEmpty()) {
                TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                int adjustedHeight = this.height - (topBarHeight + TAB_HEIGHT + verticalPadding) - 5;
                activeTerminal.scrollToTop(adjustedHeight);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            if (!terminals.isEmpty()) {
                TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                activeTerminal.scrollToBottom();
            }
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_TAB) {
            int next = (activeTerminalIndex + 1) % terminals.size();
            tabsBar.setActiveTab(next);
            return true;
        }
        MergeGroup mg = mergeGroups.get(terminals.get(activeTerminalIndex).terminalId);
        if (mg.members.size() > 1) {
            TerminalInstance ti = mg.members.get(focusedPaneIndex);
            if (ti.keyPressed(keyCode, modifiers)) return true;
        }
        if (!terminals.isEmpty()) {
            for (RemotelyClient.CommandSnippet sn : globalSnippets) {
                if (sn.shortcut != null && !sn.shortcut.isEmpty() && checkShortcut(sn.shortcut)) {
                    TerminalInstance tgt = mg.members.size() > 1
                            ? mg.members.get(focusedPaneIndex)
                            : terminals.get(activeTerminalIndex);
                    for (String ln : sn.commands.split("\n")) {
                        if (!ln.trim().isEmpty()) {
                            try {
                                tgt.getInputHandler().commandExecutor.executeCommand(ln.trim(), new StringBuilder(ln.trim()));
                            } catch (IOException e) {
                                tgt.appendOutput("ERROR: " + e.getMessage() + "\n");
                            }
                        }
                    }
                    shortcutConsumed = true;
                    return true;
                }
            }
        }
        TerminalInstance finalTarget = mg.members.size() > 1
                ? mg.members.get(focusedPaneIndex)
                : terminals.get(activeTerminalIndex);
        return finalTarget.keyPressed(keyCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        if (tabsBar.handleTabsBarChar(chr)) return true;
        if (aiSidePanel.fieldFocused && aiMode && aiSidePanel.charTyped(chr, keyCode)) return true;
        if (shortcutConsumed) {
            shortcutConsumed = false;
            return true;
        }
        if (snippetRecordingKeys) return true;
        if (snippetPopupActive) {
            snippetLastInputTime = System.currentTimeMillis();
            if (chr == '\r' || chr == '\b') return true;
            if (chr == '\n') {
                if (!snippetNameFocused) {
                    snippetCommandsBuffer.insert(snippetCommandsCursorPos, '\n');
                    snippetCommandsCursorPos++;
                }
                return true;
            }
            if (chr >= 32 && chr != 127) {
                if (snippetNameFocused) {
                    snippetNameBuffer.insert(snippetNameCursorPos, chr);
                    snippetNameCursorPos++;
                } else {
                    snippetCommandsBuffer.insert(snippetCommandsCursorPos, chr);
                    snippetCommandsCursorPos++;
                }
            }
            return true;
        }
        if (isRenaming && renamingTabIndex != -1) {
            lastRenameInputTime = System.currentTimeMillis();
            if (chr == '\r') {
                String nn = renameBuffer.toString().trim();
                if (!nn.isEmpty()) tabNames.set(renamingTabIndex, nn);
                isRenaming = false;
                renamingTabIndex = -1;
                remotelyClient.multiTabNames = new ArrayList<>(tabNames);
                return true;
            }
            if (chr == '\b') {
                if (renameCursorPos > 0 && renameCursorPos <= renameBuffer.length()) {
                    renameBuffer.deleteCharAt(renameCursorPos - 1);
                    renameCursorPos--;
                    tabNames.set(renamingTabIndex, renameBuffer.toString());
                }
                return true;
            }
            if (chr >= 32 && chr != 127) {
                renameBuffer.insert(renameCursorPos, chr);
                renameCursorPos++;
                tabNames.set(renamingTabIndex, renameBuffer.toString());
                remotelyClient.multiTabNames = new ArrayList<>(tabNames);
                return true;
            }
            return true;
        }
        MergeGroup mg = mergeGroups.get(terminals.get(activeTerminalIndex).terminalId);
        if (mg.members.size() > 1) {
            TerminalInstance ti = mg.members.get(focusedPaneIndex);
            if (ti.charTyped(chr)) return true;
        }
        if (!terminals.isEmpty()) {
            TerminalInstance tgt = mg.members.size() > 1
                    ? mg.members.get(focusedPaneIndex)
                    : terminals.get(activeTerminalIndex);
            return tgt.charTyped(chr) || super.charTyped(chr, keyCode);
        }
        return super.charTyped(chr, keyCode);
    }


    @Override
    public void close() {
        super.close();
        if (closedViaEscape) {
            saveAllTerminals();
            remotelyClient.onMultiTerminalScreenClosed();
        }
        remotelyClient.activeTerminalIndex = this.activeTerminalIndex;
        remotelyClient.scale = this.scale;
        remotelyClient.snippetPanelWidth = this.snippetPanelWidth;
        remotelyClient.showSnippetsPanel = this.showSnippetsPanel;
        remotelyClient.multiTerminals = new ArrayList<>(terminals);
        remotelyClient.multiTabNames = new ArrayList<>(tabNames);
    }

    public void shutdownAllTerminals() {
        if (terminals.isEmpty()) {
            return;
        }
        for (TerminalInstance terminal : terminals) {
            terminal.shutdown();
        }
        terminals.clear();
        tabNames.clear();
    }

    private void saveAllTerminals() {
        try {
            if (!Files.exists(TERMINAL_LOG_DIR)) {
                Files.createDirectories(TERMINAL_LOG_DIR);
            }
            for (int i = 0; i < terminals.size(); i++) {
                TerminalInstance terminal = terminals.get(i);
                String tabName = tabNames.get(i);
                terminal.saveTerminalOutput(TERMINAL_LOG_DIR.resolve(tabName + ".log"));
            }
        } catch (IOException e) {
            if (minecraftClient.player != null) {
                minecraftClient.player.sendMessage(Text.literal("Failed to save terminal logs."), false);
            }
        }
    }

    private void trimAndDrawText(DrawContext context, String text, int x, int y, int maxWidth, int color) {
        String t = trimTextToWidthWithEllipsis(text, maxWidth);
        context.drawText(minecraftClient.textRenderer, Text.literal(t), x, y, color, shadow);
    }

    private String trimTextToWidthWithEllipsis(String text, int maxWidth) {
        if (minecraftClient.textRenderer.getWidth(text) <= maxWidth) return text;
        while (minecraftClient.textRenderer.getWidth(text + "..") > maxWidth && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }

    private String ensureCursorBounds(String text) {
        return text;
    }

    private List<String> wrapLines(String[] lines, int width, TextRenderer renderer) {
        List<String> wrapped = new ArrayList<>();
        for (String l : lines) {
            if (l.isEmpty()) {
                wrapped.add("");
                continue;
            }
            String temp = l;
            while (renderer.getWidth(temp) > width && !temp.isEmpty()) {
                int cut = temp.length();
                while (cut > 0 && renderer.getWidth(temp.substring(0, cut)) > width) {
                    cut--;
                }
                if (cut == 0) cut = 1;
                wrapped.add(temp.substring(0, cut));
                temp = temp.substring(cut);
            }
            wrapped.add(temp);
        }
        return wrapped;
    }

    private int findCursorLine(List<String> wrappedLines, int cursorPos) {
        int count = 0;
        int total = 0;
        for (String l : wrappedLines) {
            int len = l.length();
            if (cursorPos <= total + len) {
                return count;
            }
            total += len;
            count++;
        }
        return wrappedLines.size() - 1;
    }

    private int cursorPosInLine(int cursorPos, List<String> wrappedLines, int lineIndex) {
        int total = 0;
        for (int i = 0; i < lineIndex; i++) {
            total += wrappedLines.get(i).length();
        }
        return cursorPos - total;
    }

    private List<String> getVisibleLines(List<String> wrappedLines, int start, int maxCount) {
        List<String> res = new ArrayList<>();
        for (int i = start; i < start + maxCount && i < wrappedLines.size(); i++) {
            res.add(wrappedLines.get(i));
        }
        return res;
    }

    private int getCursorFromMouseX(String line, int mouseX) {
        int pos = 0;
        while (pos < line.length()) {
            int w = minecraftClient.textRenderer.getWidth(line.substring(0, pos + 1));
            if (w > mouseX) break;
            pos++;
        }
        return pos;
    }

    private void moveCursorVertically(int direction, String full) {
        int commandsBoxWidth = snippetPopupWidth - 10;
        List<String> wrappedLines = wrapLines(full.split("\n", -1), commandsBoxWidth, minecraftClient.textRenderer);
        int cLineIndex = findCursorLine(wrappedLines, snippetCommandsCursorPos);
        int cPosInLine = cursorPosInLine(snippetCommandsCursorPos, wrappedLines, cLineIndex);
        cLineIndex += direction;
        if (cLineIndex < 0) cLineIndex = 0;
        if (cLineIndex >= wrappedLines.size()) cLineIndex = wrappedLines.size() - 1;
        int newPos = 0;
        for (int i = 0; i < cLineIndex; i++) {
            newPos += wrappedLines.get(i).length();
        }
        newPos += Math.min(cPosInLine, wrappedLines.get(cLineIndex).length());
        snippetCommandsCursorPos = Math.min(newPos, full.length());
    }

    private String humanReadableKey(String keyName) {
        if (keyName == null) return "";
        if (keyName.startsWith("key.keyboard.")) {
            String k = keyName.substring("key.keyboard.".length());
            return switch (k) {
                case "left.control", "right.control" -> "CTRL";
                case "left.shift", "right.shift" -> "SHIFT";
                case "left.alt", "right.alt" -> "ALT";
                default -> k.toUpperCase();
            };
        }
        return keyName.toUpperCase();
    }

    private boolean checkShortcut(String shortcut) {
        if (shortcut.isEmpty()) return false;
        String[] keys = shortcut.split("\\+");
        for (String k : keys) {
            if (!isKeyHeld(k)) return false;
        }
        return true;
    }

    private boolean isKeyHeld(String k) {
        k = k.toLowerCase();
        switch (k) {
            case "ctrl" -> {
                return InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL)
                        || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL);
            }
            case "shift" -> {
                return InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT)
                        || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT);
            }
            case "alt" -> {
                return InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_ALT)
                        || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_ALT);
            }
        }
        for (int code = 0; code < 400; code++) {
            InputUtil.Key testKey = InputUtil.fromKeyCode(code, 0);
            if (testKey != InputUtil.UNKNOWN_KEY && testKey.getTranslationKey().toLowerCase().endsWith("." + k.toLowerCase())) {
                if (InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), code)) return true;
            }
        }
        return false;
    }

    private int findPreviousWord(String text, int pos) {
        if (pos <= 0) return 0;
        int idx = pos - 1;
        while (idx > 0 && Character.isWhitespace(text.charAt(idx))) idx--;
        while (idx > 0 && !Character.isWhitespace(text.charAt(idx))) idx--;
        if (idx > 0 && Character.isWhitespace(text.charAt(idx))) idx++;
        return idx;
    }

    private int findNextWord(String text, int pos) {
        if (pos >= text.length()) return text.length();
        int idx = pos;
        while (idx < text.length() && !Character.isWhitespace(text.charAt(idx))) idx++;
        while (idx < text.length() && Character.isWhitespace(text.charAt(idx))) idx++;
        return idx;
    }

    public static class Theme {
        public String name;
        public Map<String, Integer> colors = new HashMap<>();
    }

    public static class TabInfo {
        public String name;
        public int width;
        TabInfo(String n, int w) {
            name = n;
            width = w;
        }
    }

    private void initializeWeights() {
        MergeGroup group = mergeGroups.get(terminals.get(activeTerminalIndex).terminalId);
        int count = group.members.size();
        if (count <= 1) {
            gridRows = 1;
            gridColumns = 1;
            gridColumnWeights.clear();
            gridRowWeights.clear();
            currentColumnWeights.clear();
            currentRowWeights.clear();
            return;
        }
        gridRows = (int) Math.floor(Math.sqrt(count));
        if (gridRows < 1) gridRows = 1;
        gridColumns = (int) Math.ceil((double) count / gridRows);

        gridColumnWeights.clear();
        currentColumnWeights.clear();
        for (int i = 0; i < gridColumns; i++) {
            float w = 1.0f / gridColumns;
            gridColumnWeights.add(w);
            currentColumnWeights.add(w);
        }

        gridRowWeights.clear();
        currentRowWeights.clear();
        for (int i = 0; i < gridRows; i++) {
            float h = 1.0f / gridRows;
            gridRowWeights.add(h);
            currentRowWeights.add(h);
        }
    }

    private void mergeTerminal(int index) {
        if (index == activeTerminalIndex) return;
        UUID targetId = terminals.get(activeTerminalIndex).terminalId;
        MergeGroup targetGroup = mergeGroups.get(targetId);
        TerminalInstance toMerge = terminals.get(index);
        MergeGroup sourceGroup = mergeGroups.get(toMerge.terminalId);
        for (TerminalInstance t : sourceGroup.members) {
            targetGroup.members.add(t);
            mergeGroups.put(t.terminalId, targetGroup);
        }
        mergeGroups.remove(toMerge.terminalId);
        terminals.remove(index);
        tabNames.remove(index);
        tabsBar.getTabs().remove(index);
        if (activeTerminalIndex > index) activeTerminalIndex--;
        tabsBar.setActiveTab(activeTerminalIndex);
        initializeWeights();
    }

    private void unmergePanel(int panelIndex) {
        UUID groupId = terminals.get(activeTerminalIndex).terminalId;
        MergeGroup group = mergeGroups.get(groupId);
        if (panelIndex < 0 || panelIndex >= group.members.size()) return;
        TerminalInstance removed = group.members.remove(panelIndex);
        mergeGroups.remove(removed.terminalId);
        terminals.add(activeTerminalIndex + 1, removed);
        tabNames.add(activeTerminalIndex + 1, "Tab " + (terminals.size()));
        tabsBar.getTabs().add(activeTerminalIndex + 1, new TabsBar.Tab<>(tabNames.get(activeTerminalIndex + 1), false, null));
        mergeGroups.put(removed.terminalId, new MergeGroup(removed));
        initializeWeights();
    }

    private void closePanel(int panelIndex) {
        UUID groupId = terminals.get(activeTerminalIndex).terminalId;
        MergeGroup group = mergeGroups.get(groupId);
        if (group.members.size() <= 1) return;
        TerminalInstance removed = group.members.remove(panelIndex);
        mergeGroups.remove(removed.terminalId);
        removed.shutdown();
        if (focusedPanelIndex >= group.members.size()) focusedPanelIndex = group.members.size() - 1;
        initializeWeights();
    }

    private void renderSplitTerminals(DrawContext context, int totalW, int totalH) {
        MergeGroup mg = mergeGroups.get(terminals.get(activeTerminalIndex).terminalId);
        int count = mg.members.size();
        if (count <= 1) return;
        int gap = paneGap;
        int availW = totalW - (gridColumns - 1) * gap;
        int availH = totalH - (gridRows - 1) * gap;
        int baseX  = 5;
        int baseY  = ContentYStart;
        int idx    = 0;
        for (int r = 0; r < gridRows; r++) {
            int rowH = Math.round(currentRowWeights.get(r) * availH);
            int xOff = baseX;
            for (int c = 0; c < gridColumns; c++) {
                if (idx >= count) break;
                int colW = Math.round(currentColumnWeights.get(c) * availW);
                TerminalInstance ti = mg.members.get(idx);
                ti.renderer.isActive = (idx == focusedPaneIndex);
                ti.renderer.render(context, xOff, baseY, colW, rowH, this);
                xOff += colW + gap;
                idx++;
            }
            baseY += rowH + gap;
        }
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.TERMINAL);
    }

    @Override
    public void removed() {
        minecraftClient.getWindow().setScaleFactor(originalMCScale);
        targetScaleFactor = globalScaleFactor = animScaleFactor;
        remotelyClient.activeTerminalIndex = this.activeTerminalIndex;
        remotelyClient.scale = this.scale;
        remotelyClient.snippetPanelWidth = this.snippetPanelWidth;
        remotelyClient.showSnippetsPanel = this.showSnippetsPanel;
        remotelyClient.multiTerminals = new ArrayList<>(terminals);
        remotelyClient.multiTabNames = new ArrayList<>(tabNames);
        remotelyClient.multiMergeGroups = new LinkedHashMap<>(mergeGroups);
        if (parent.equals(mcScreen)) playSound(Sound.SCREEN);
    }
}


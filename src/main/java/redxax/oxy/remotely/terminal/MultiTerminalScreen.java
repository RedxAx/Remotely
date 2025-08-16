package redxax.oxy.remotely.terminal;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.api.RemotelyAPI;
import redxax.oxy.remotely.api.RemotelyApiFactory;
import redxax.oxy.remotely.explorer.FileExplorerScreen;
import redxax.oxy.remotely.resources.ResourceManagerScreen;
import redxax.oxy.remotely.servers.ReverseProxyManager;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.servers.ServerState;
import redxax.oxy.remotely.ui.ReScreen;
import redxax.oxy.remotely.ui.widgets.AnimatedButton;
import redxax.oxy.remotely.util.Notification;
import redxax.oxy.remotely.util.Sound;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

public class MultiTerminalScreen extends ReScreen {

    private final RemotelyClient remotelyClient;
    private final Screen parent;
    private SidePanel snippetsPanel;
    private SidePanel aiPanel;
    private final ServerInfo serverToOpen;
    private TerminalWidget activeTerminal;
    private ServerState lastKnownState;

    public static final Path THEMES_DIR = remotelyDir.resolve("themes");

    public static class Theme {
        public String name;
        public Map<String, Integer> colors = new HashMap<>();
    }

    public MultiTerminalScreen(MinecraftClient client, Screen parent, RemotelyClient remotelyClient) {
        this(client, parent, remotelyClient, null);
    }

    public MultiTerminalScreen(MinecraftClient client, Screen parent, RemotelyClient remotelyClient, ServerInfo serverToOpen) {
        super(Text.literal("Multi Terminal"));
        this.client = client;
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.serverToOpen = serverToOpen;
    }

    @Override
    protected void init() {
        super.init();
        this.activeTerminal = null;

        setupPanels();
        setupHeader();
        setupTabs();

        if (tabsManager.getTabs().isEmpty() && serverToOpen == null) {
            addNewTerminalTab("Terminal");
        }

        if (serverToOpen != null) {
            int existingTabIndex = -1;
            for (int i = 0; i < tabsManager.getTabs().size(); i++) {
                TabsManager.Tab tab = tabsManager.getTabs().get(i);
                if (tab.getData() instanceof TerminalWidget widget) {
                    ServerInfo info = widget.getServerInfo();
                    if (info != null && info.path.equals(serverToOpen.path)) {
                        existingTabIndex = i;
                        break;
                    }
                }
            }

            if (existingTabIndex != -1) {
                tabsManager.setActiveTab(existingTabIndex);
            } else {
                addNewTerminalTab(serverToOpen.name, serverToOpen);
            }
        } else {
            int index = remotelyClient.activeTerminalIndex;
            if (index >= 0 && index < tabsManager.getTabs().size()) {
                tabsManager.setActiveTab(index);
            } else if (!tabsManager.getTabs().isEmpty()) {
                tabsManager.setActiveTab(0);
            }
        }
        targetScaleFactor = 2;
        globalScaleFactor = 2;
        animScaleFactor = 2;
    }

    private void launchActiveTerminal(boolean start) {
        if (activeTerminal == null) {
            return;
        }

        ServerInfo sInfo = activeTerminal.getServerInfo();
        if (sInfo == null) {
            return;
        }

        if (!start) {
            activeTerminal.executeCommand("stop");
            header().setButtonVisible("start.png", true);
            header().setButtonVisible("stop.png", false);
        } else {
            try {
                RemotelyAPI api = RemotelyApiFactory.get(sInfo);
                api.launchServer(sInfo, command -> {
                    if (command != null && !command.isEmpty()) {
                        activeTerminal.executeCommand(command);
                    }
                });
                header().setButtonVisible("stop.png", true);
                header().setButtonVisible("start.png", false);
            } catch (Exception e) {
                new Notification("Failed To Start Server", e.getMessage(), Notification.Type.ERROR);
            }
        }
    }

    private void exploreActiveTerminalFiles() {
        if (activeTerminal == null) return;
        ServerInfo sInfo = activeTerminal.getServerInfo();
        if (sInfo != null) {
            client.setScreen(new FileExplorerScreen(this, sInfo));
        } else {
            RemotelyAPI api = RemotelyApiFactory.get(null);
            String path = api.getInitialDirectory(null);
            client.setScreen(new FileExplorerScreen(this, Path.of(path)));
        }
    }

    private void openActiveTerminalResources() {
        if (activeTerminal == null) return;
        ServerInfo sInfo = activeTerminal.getServerInfo();
        if (sInfo != null) {
            client.setScreen(new ResourceManagerScreen(client, this, sInfo));
        }
    }

    private void setupHeader() {
        header().reset();
        header().addRight("close.png", this::close, "Close");

        header().addLeft("start.png", () -> launchActiveTerminal(true), "Start Server");
        header().addLeft("stop.png", () -> launchActiveTerminal(false), "Stop Server");
        header().addLeft("explorer.png", this::exploreActiveTerminalFiles, "File Explorer");
        header().addLeft("resources.png", this::openActiveTerminalResources, "Resources");
        header().addLeft("reverse.png", () -> ReverseProxyManager.reverse(activeTerminal.getServerInfo()), "Open Server To The Public");
        header().addLeft("closeReverse.png", () -> ReverseProxyManager.reverse(activeTerminal.getServerInfo()), "Close Reverse Proxy");

        header().addRight("snippets.png", () -> snippetsPanel.toggle(), "Snippets");
        header().addRight("RemotelyAI.png", () -> aiPanel.toggle(), "RemotelyAI");

        header().build();
        updateHeaderButtons();
    }

    private void updateHeaderButtons() {
        if (activeTerminal == null) {
            header().setButtonVisible("start.png", false);
            header().setButtonVisible("stop.png", false);
            header().setButtonVisible("explorer.png", false);
            header().setButtonVisible("resources.png", false);
            return;
        }

        ServerInfo sInfo = activeTerminal.getServerInfo();
        if (sInfo != null) {
            header().setButtonVisible("explorer.png", true);
            boolean isProxy = List.of("velocity", "waterfall", "bungeecord").contains(sInfo.type.toLowerCase(Locale.getDefault()));
            boolean isReversed = ReverseProxyManager.isPortForwarded(activeTerminal.getServerInfo().getPort());
            header().setButtonVisible("resources.png", !isProxy);
            header().setButtonVisible("reverse.png", !isReversed);
            header().setButtonVisible("closeReverse.png", isReversed);
            updateStartStopButtonState(sInfo);
        } else {
            header().setButtonVisible("start.png", false);
            header().setButtonVisible("stop.png", false);
            header().setButtonVisible("resources.png", false);
            header().setButtonVisible("reverse.png", false);
            header().setButtonVisible("closeReverse.png", false);
            header().setButtonVisible("explorer.png", true);
        }
    }

    private void updateStartStopButtonState(ServerInfo sInfo) {
        if (sInfo == null) return;

        ServerState st = sInfo.state;
        boolean isRunning = st == ServerState.RUNNING || st == ServerState.STARTING;
        header().setButtonVisible("start.png", !isRunning);
        header().setButtonVisible("stop.png", isRunning);
    }


    private void setupTabs() {
        tabs().builder()
                .position(5, 35)
                .size(width - 10, 18)
                .onTabSelected(this::onTabSelected)
                .onTabClosed(this::onTabClosed)
                .onPlusButtonClicked(() -> addNewTerminalTab("Terminal " + (tabsManager.getTabs().size() + 1)))
                .build();

        if (!remotelyClient.multiTerminalTabs.isEmpty()) {
            for (TabsManager.Tab tab : remotelyClient.multiTerminalTabs) {
                tabsManager.addTabRaw(tab);
            }
            remotelyClient.multiTerminalTabs.clear();
        }
    }

    private void onTabSelected(TabsManager.Tab tab) {
        if (this.activeTerminal != null) {
            this.remove(this.activeTerminal);
        }

        if (tab != null && tab.getData() instanceof TerminalWidget terminal) {
            this.activeTerminal = terminal;
            addDrawableChild(this.activeTerminal);
            this.setFocused(this.activeTerminal);
            if (terminal.getServerInfo() != null) {
                this.lastKnownState = terminal.getServerInfo().state;
            } else {
                this.lastKnownState = null;
            }
        } else {
            this.activeTerminal = null;
            this.lastKnownState = null;
        }

        updateHeaderButtons();

        remotelyClient.activeTerminalIndex = tabsManager.getActiveTabIndex();
        playSound(Sound.SWITCHTAB);
    }

    private void onTabClosed(TabsManager.Tab tab) {
        if (tab.getData() instanceof TerminalWidget terminal) {
            terminal.shutdown();
            if (terminal == activeTerminal) {
                remove(activeTerminal);
                activeTerminal = null;
            }
        }
        if (tabsManager.getTabs().isEmpty()) {
            this.close();
        }
        playSound(Sound.CLOSETAB);
    }

    private void addTerminalTab(String name, ServerInfo info) {
        TerminalWidget terminal = new TerminalWidget.Builder()
                .server(info).animateLayout(false)
                .size(width - 10, height - 5)
                .build();
        terminal.setPosition(5, 60);
        terminal.start();

        TabsManager.Tab tab = tabsManager.addTab(name, null);
        tab.setData(terminal);
    }

    private void addNewTerminalTab(String name) {
        addTerminalTab(name, null);
        tabsManager.setActiveTab(tabsManager.getTabs().size() - 1);
        playSound(Sound.CREATE);
    }

    private void addNewTerminalTab(String name, ServerInfo info) {
        addTerminalTab(name, info);
        tabsManager.setActiveTab(tabsManager.getTabs().size() - 1);
        playSound(Sound.CREATE);
    }

    private void setupPanels() {
        snippetsPanel = createSidePanel("snippets").width(200).y(60).height(height - 5);
        aiPanel = createSidePanel("ai").width(300).y(60).height(height - 5);

        if (remotelyClient.showSnippetsPanel) {
            snippetsPanel.show();
        }

        populateSnippetsPanel();
    }

    @Override
    public void tick() {
        super.tick();
        if (activeTerminal != null && activeTerminal.getServerInfo() != null) {
            ServerInfo sInfo = activeTerminal.getServerInfo();
            if (sInfo.state != lastKnownState) {
                updateStartStopButtonState(sInfo);
                lastKnownState = sInfo.state;
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (activeTerminal == null) return;

        int termX = 5;
        int termY = 60;
        int termWidth = this.width - 10;
        int termHeight = this.height - 5 - termY;

        if (snippetsPanel.isVisible()) {
            termWidth -= (int) snippetsPanel.getAnimatedWidth();
        }
        if (aiPanel.isVisible()) {
            termWidth -= (int) aiPanel.getAnimatedWidth();
        }

        activeTerminal.setPosition(termX, termY);
        activeTerminal.setWidth(termWidth);
        activeTerminal.setHeight(termHeight);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && activeTerminal != null) {
            activeTerminal.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_B && hasControlDown()) {
            if (activeTerminal != null) activeTerminal.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_TAB && activeTerminal != null) {
            return activeTerminal.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void populateSnippetsPanel() {
        Container snippetContainer = snippetsPanel.container();
        snippetContainer.clearWidgets();
        snippetContainer.padding(5).columns(1);

        for (RemotelyClient.CommandSnippet snippet : RemotelyClient.globalSnippets) {
            AnimatedButton button = new AnimatedButton.Builder().label(Text.literal(snippet.name)).onClick(() -> executeSnippet(snippet)).build();
            snippetContainer.addWidget(button);
        }
    }

    private void executeSnippet(RemotelyClient.CommandSnippet snippet) {
        TabsManager.Tab activeTab = tabsManager.getActiveTab();
        if (activeTab != null && activeTab.getData() instanceof TerminalWidget terminal) {
            for (String command : snippet.commands.split("\n")) {
                if (!command.trim().isEmpty()) {
                    terminal.executeCommand(command.trim());
                }
            }
        }
    }

    @Override
    public void close() {
        assert this.client != null;
        this.client.setScreen(parent);
    }

    @Override
    public void removed() {
        if (this.activeTerminal != null) {
            this.setFocused(null);
        }

        remotelyClient.showSnippetsPanel = snippetsPanel.isVisible();
        remotelyClient.activeTerminalIndex = tabsManager.getActiveTabIndex();

        remotelyClient.multiTerminalTabs.clear();
        remotelyClient.multiTerminalTabs.addAll(tabsManager.getTabs());

    }
}
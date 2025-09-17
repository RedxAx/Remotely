package redxax.oxy.remotely.terminal;

import net.minecraft.client.MinecraftClient;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rescreen.platform.IDrawContext;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.explorer.FileExplorerScreen;
import redxax.oxy.remotely.resources.ResourceManagerScreen;
import redxax.oxy.remotely.servers.ReverseProxyManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static restudio.rescreen.util.SoundUtils.playSound;

public class MultiTerminalScreen extends ReScreen {

    private final RemotelyClient remotelyClient;
    private Screen parent;
    private net.minecraft.client.gui.screen.Screen mcParent;
    private SidePanel snippetsPanel;
    private SidePanel aiPanel;
    private final Instance serverToOpen;
    private TerminalWidget activeTerminal;
    private InstanceState lastKnownState;

    public static final Path THEMES_DIR = remotelyDir.resolve("themes");

    public static class Theme {
        public String name;
        public Map<String, Integer> colors = new HashMap<>();
    }

    public MultiTerminalScreen(Screen parent, RemotelyClient remotelyClient) {
        this(parent, remotelyClient, null);
    }

    public MultiTerminalScreen(net.minecraft.client.gui.screen.Screen parent, RemotelyClient remotelyClient) {
        super();
        this.parent = null;
        this.mcParent = parent;
        this.remotelyClient = remotelyClient;
        this.serverToOpen = null;
    }

    public MultiTerminalScreen(Screen parent, RemotelyClient remotelyClient, Instance serverToOpen) {
        super();
        this.parent = parent;
        this.mcParent = null;
        this.remotelyClient = remotelyClient;
        this.serverToOpen = serverToOpen;
    }

    @Override
    public void init() {
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
                    Instance info = widget.getServerInfo();
                    if (info != null && info.getPath().equals(serverToOpen.getPath())) {
                        existingTabIndex = i;
                        break;
                    }
                }
            }

            if (existingTabIndex != -1) {
                tabsManager.setActiveTab(existingTabIndex);
            } else {
                addNewTerminalTab(serverToOpen.getName(), serverToOpen);
            }
        } else {
            int index = remotelyClient.activeTerminalIndex;
            if (index >= 0 && index < tabsManager.getTabs().size()) {
                tabsManager.setActiveTab(index);
            } else if (!tabsManager.getTabs().isEmpty()) {
                tabsManager.setActiveTab(0);
            }
        }
    }

    private void launchActiveTerminal(boolean start) {
        if (activeTerminal == null) {
            return;
        }

        Instance sInfo = activeTerminal.getServerInfo();
        if (sInfo == null) {
            return;
        }

        if (!start) {
            activeTerminal.executeCommand("stop");
            header().setButtonVisible("start.png", true);
            header().setButtonVisible("stop.png", false);
        } else {
            try {
                RebaseAPI api = RebaseApiFactory.get(sInfo);
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
        Instance sInfo = activeTerminal.getServerInfo();
        if (sInfo != null) {
            ScreenManager.getInstance().setScreen(new FileExplorerScreen(this, sInfo, false));
        } else {
            RebaseAPI api = RebaseApiFactory.get(null);
            String path = api.getInitialDirectory(null);
            RemotelyClient.INSTANCE.openFileExplorer(this, Path.of(path));
        }
    }

    private void openActiveTerminalResources() {
        if (activeTerminal == null) return;
        Instance sInfo = activeTerminal.getServerInfo();
        if (sInfo != null) {
            ScreenManager.getInstance().setScreen(new ResourceManagerScreen(this, sInfo));
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

        Instance sInfo = activeTerminal.getServerInfo();
        if (sInfo != null) {
            header().setButtonVisible("explorer.png", true);
            boolean isProxy = List.of("velocity", "waterfall", "bungeecord").contains(sInfo.getModLoader().name().toLowerCase(Locale.getDefault()));
            boolean isReversed = ReverseProxyManager.isPortForwarded(sInfo);
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

    private void updateStartStopButtonState(Instance sInfo) {
        if (sInfo == null) return;

        InstanceState st = sInfo.getState();
        boolean isRunning = st == InstanceState.RUNNING || st == InstanceState.STARTING;
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
            if (terminal.getServerInfo() != null) {
                this.lastKnownState = terminal.getServerInfo().getState();
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

    private void addTerminalTab(String name, Instance info) {
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

    private void addNewTerminalTab(String name, Instance info) {
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
            Instance sInfo = activeTerminal.getServerInfo();
            if (sInfo.getState() != lastKnownState) {
                updateStartStopButtonState(sInfo);
                lastKnownState = sInfo.getState();
            }
        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
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
            AnimatedButton button = new AnimatedButton.Builder().label((snippet.name)).onClick(() -> executeSnippet(snippet)).build();
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
        if (parent != null) {
            ScreenManager.getInstance().setScreen(parent);
        } else if (mcParent != null) {
            MinecraftClient.getInstance().setScreen(mcParent);
        }
    }

    @Override
    public void removed() {
        remotelyClient.showSnippetsPanel = snippetsPanel.isVisible();
        remotelyClient.activeTerminalIndex = tabsManager.getActiveTabIndex();

        remotelyClient.multiTerminalTabs.clear();
        remotelyClient.multiTerminalTabs.addAll(tabsManager.getTabs());

    }
}
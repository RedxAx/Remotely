package redxax.oxy.remotely.servers;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.config.Config.enableDebugTools;
import static redxax.oxy.remotely.util.ImageUtil.drawBrowser;
import static restudio.rescreen.config.Config.shadow;
import static restudio.rescreen.render.Render.animatedScaling;
import static restudio.rescreen.util.SoundUtils.playSound;

public class BrowserScreen extends ReScreen {
    private static final List<Tab> tabs = new ArrayList<>();
    private final Screen parent;
    private final String startUrl;
    private Tab activeTab;
    private TextInputWidget urlBar;
    private boolean fullScreenMode = false;
    private int previousBrowserWidth = -1;
    private int previousBrowserHeight = -1;

    public BrowserScreen(Screen parent, String url) {
        super();
        this.parent = parent;
        this.startUrl = url;
        checkIfMcefExist();
    }

    public static class Tab {
        public String url;
        public String title;
        public MCEFBrowser browser;

        public Tab(String url, MCEFBrowser browser) {
            this.url = url;
            this.browser = browser;
            this.title = trimUrl(url);
        }

        private static String trimUrl(String url) {
            try {
                String withoutProtocol = url.replaceFirst("^(http://|https://)", "");
                withoutProtocol = withoutProtocol.replaceFirst("^www\\.", "");
                int queryIndex = withoutProtocol.indexOf('?');
                if (queryIndex != -1) {
                    withoutProtocol = withoutProtocol.substring(0, queryIndex);
                }
                if (withoutProtocol.endsWith("/")) {
                    withoutProtocol = withoutProtocol.substring(0, withoutProtocol.length() - 1);
                }
                return withoutProtocol;
            } catch (Exception e) {
                return url;
            }
        }
    }

    @Override
    public void init() {
        super.init();

        urlBar = new TextInputWidget.Builder()
                .size(400, 18)
                .onChange(this::loadUrl)
                .build();
        addDrawableChild(urlBar);

        header().addLeft("/assets/remotely/icons/goback.png", () -> activeTab.browser.goBack(), "Go Back")
                .addLeft("/assets/remotely/icons/goforward.png", () -> activeTab.browser.goForward(), "Go Forward")
                .addLeft("/assets/remotely/icons/reload.png", () -> activeTab.browser.reload(), "Reload")
                .addRight("/assets/remotely/icons/close.png", this::close, "Close")
                .addRight("/assets/remotely/icons/fullscreen.png", this::toggleFullscreen, "Toggle Fullscreen")
                .build();

        tabs().builder()
                .position(5, 35)
                .size(width - 10, 18)
                .allowAdd(true)
                .allowClose(true)
                .allowReorder(true)
                .onTabSelected(this::onTabSelected)
                .onTabClosed(this::onTabClosed)
                .onPlusButtonClicked(this::addNewTab)
                .onTabsReordered(this::onTabsReordered)
                .build();

        if (tabs.isEmpty()) {
            addNewTab(startUrl);
        } else {
            for (Tab tab : tabs) {
                TabsManager.Tab uiTab = tabs().addTab(tab.title, null);
                uiTab.setData(tab);
            }
            tabs().setActiveTab(0);
            onTabSelected(tabs().getActiveTab());
        }
    }

    private void onTabSelected(TabsManager.Tab uiTab) {
        if (uiTab == null) {
            activeTab = null;
            return;
        }
        activeTab = (Tab) uiTab.getData();
        if (activeTab != null) {
            urlBar.setText(activeTab.url);
        }
    }

    private void onTabClosed(TabsManager.Tab uiTab) {
        Tab tab = (Tab) uiTab.getData();
        if (tab != null) {
            tab.browser.close();
            tabs.remove(tab);
        }
        if (tabs.isEmpty()) {
            close();
        }
    }

    private void onTabsReordered(List<TabsManager.Tab> uiTabs) {
        List<Tab> newOrder = new ArrayList<>();
        for (TabsManager.Tab uiTab : uiTabs) {
            newOrder.add((Tab) uiTab.getData());
        }
        tabs.clear();
        tabs.addAll(newOrder);
    }

    private void addNewTab() {
        addNewTab("https://www.google.com");
    }

    private void addNewTab(String url) {
        MCEFBrowser newBrowser = MCEF.createBrowser(url, true);
        Tab newTab = new Tab(url, newBrowser);
        tabs.add(newTab);

        TabsManager.Tab uiTab = tabs().addTab(newTab.title, null);
        uiTab.setData(newTab);
        tabs().setActiveTab(tabs().getTabs().size() - 1);
        resizeBrowser(newBrowser);
    }

    private void loadUrl(String url) {
        String finalUrl = url;
        try {
            new URL(finalUrl).toURI();
        } catch (Exception e) {
            try {
                finalUrl = "https://www.google.com/search?q=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        activeTab.url = finalUrl;
        activeTab.browser.loadURL(finalUrl);
        activeTab.title = Tab.trimUrl(finalUrl);
        tabs().getActiveTab().setName(activeTab.title);
    }

    private void toggleFullscreen() {
        fullScreenMode = !fullScreenMode;
        header().visible(!fullScreenMode);
        tabs().builder().visible(!fullScreenMode);
    }

    private void resizeBrowser(MCEFBrowser browser) {
        if (width <= 0 || height <= 0 || browser == null) return;

        int browserWidth, browserHeight;
        if (fullScreenMode) {
            browserWidth = width;
            browserHeight = height;
        } else {
            browserWidth = width - 10;
            browserHeight = height - 65;
        }

        if (browserWidth <= 0 || browserHeight <= 0) return;

        int scaledWidth = browserWidth * client.getScaledWidth();
        int scaledHeight = browserHeight * client.getScaledHeight();

        if (scaledWidth != previousBrowserWidth || scaledHeight != previousBrowserHeight) {
            browser.resize(scaledWidth, scaledHeight);
            previousBrowserWidth = scaledWidth;
            previousBrowserHeight = scaledHeight;
        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (!fullScreenMode) {
            super.render(context, mouseX, mouseY, delta);
            urlBar.setPosition((width - urlBar.getWidth()) / 2, (header().headerSize - urlBar.getHeight()) / 2);
        } else {
            renderBackground(context, mouseX, mouseY, delta);
        }

        if (activeTab != null && activeTab.browser != null) {
            if (fullScreenMode) {
                drawBrowser(activeTab.browser, true, width, height, 0, 0);
            } else {
                drawBrowser(activeTab.browser, false, width - 10, height - 65, 60, 5);
            }
        }
        animatedScaling();
    }

    private int convertMouseX(double x, boolean isFullscreen) {
        double scale = client.getGuiScale();
        if (isFullscreen) return (int) (x * scale);
        return (int) ((x - 5) * scale);
    }

    private int convertMouseY(double y, boolean isFullscreen) {
        double scale = client.getGuiScale();
        if (isFullscreen) return (int) (y * scale);
        return (int) ((y - 60) * scale);
    }

    private boolean isMouseInBrowserArea(double mouseX, double mouseY) {
        if (fullScreenMode) {
            return true;
        }
        return mouseY > 60;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (isMouseInBrowserArea(mouseX, mouseY) && activeTab != null) {
            activeTab.browser.sendMousePress(convertMouseX(mouseX, fullScreenMode), convertMouseY(mouseY, fullScreenMode), button);
            activeTab.browser.setFocus(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (super.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (isMouseInBrowserArea(mouseX, mouseY) && activeTab != null) {
            activeTab.browser.sendMouseRelease(convertMouseX(mouseX, fullScreenMode), convertMouseY(mouseY, fullScreenMode), button);
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        if (isMouseInBrowserArea(mouseX, mouseY) && activeTab != null) {
            activeTab.browser.sendMouseMove(convertMouseX(mouseX, fullScreenMode), convertMouseY(mouseY, fullScreenMode));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (isMouseInBrowserArea(mouseX, mouseY) && activeTab != null) {
            activeTab.browser.sendMouseWheel(convertMouseX(mouseX, fullScreenMode), convertMouseY(mouseY, fullScreenMode), verticalAmount, 0);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (isMouseInBrowserArea(mouseX, mouseY) && activeTab != null) {
            activeTab.browser.sendMouseMove(convertMouseX(mouseX, fullScreenMode), convertMouseY(mouseY, fullScreenMode));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (fullScreenMode) {
                toggleFullscreen();
                return true;
            } else if (urlBar.isFocused()) {
                setFocusedWidget(null);
                return true;
            }
        }

        if (hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_T) {
                addNewTab();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_W && tabs.size() > 0) {
                tabs().removeTab(tabs().getActiveTabIndex());
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_F5 && !urlBar.isFocused()) {
            if (activeTab != null) activeTab.browser.reload();
            return true;
        }

        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (!urlBar.isFocused() && activeTab != null) {
            activeTab.browser.sendKeyPress(keyCode, scanCode, modifiers);
            return true;
        }

        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (super.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (!urlBar.isFocused() && activeTab != null) {
            activeTab.browser.sendKeyRelease(keyCode, scanCode, modifiers);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (super.charTyped(chr, modifiers)) {
            return true;
        }
        if (!urlBar.isFocused() && activeTab != null) {
            activeTab.browser.sendKeyTyped(chr, modifiers);
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (activeTab == null) return;

        String currentBrowserUrl = activeTab.browser.getURL();
        if (currentBrowserUrl != null && !currentBrowserUrl.equals(activeTab.url)) {
            activeTab.url = currentBrowserUrl;
            activeTab.title = Tab.trimUrl(currentBrowserUrl);
            tabs().getActiveTab().setName(activeTab.title);
            if (!urlBar.isFocused()) {
                urlBar.setText(currentBrowserUrl);
            }
        }
        resizeBrowser(activeTab.browser);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }


    public static void closeAll() {
        for (Tab tab : tabs) {
            tab.browser.close();
        }
        tabs.clear();
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.SCREEN);
    }

    public static boolean checkIfMcefExist() {
        if (enableDebugTools) return true;
        try {
            Class.forName("com.cinemamod.mcef.MCEF");
        } catch (ClassNotFoundException e) {
            new Notification("MCEF is not installed.", "Browser functionality is disabled.", Notification.Type.ERROR);
            return false;
        }
        return true;
    }
}
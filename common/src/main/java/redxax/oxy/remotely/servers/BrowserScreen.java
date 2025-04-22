package redxax.oxy.remotely.servers;

import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.MCEF;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.util.Notification;
import redxax.oxy.remotely.util.TextAnimator;
import redxax.oxy.remotely.Render;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.terminal.MultiTerminalScreen.TAB_HEIGHT;
import static redxax.oxy.remotely.util.ImageUtil.*;
import static redxax.oxy.remotely.util.SoundUtils.playClick;

public class BrowserScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final String startUrl;
    private static final int BROWSER_DRAW_OFFSET = 5;
    private final int TOP_OFFSET = 60;
    private static final List<Tab> tabs = new ArrayList<>();
    private static int currentTabIndex = 0;
    private final StringBuilder urlFieldText = new StringBuilder();
    private boolean urlFieldFocused = false;
    private int urlCursorPosition = 0;
    private int urlSelectionStart = -1;
    private int urlSelectionEnd = -1;
    private final float urlScrollOffset = 0;
    private final float urlTargetScrollOffset = 0;
    private boolean urlShowCursor = true;
    private long urlLastBlinkTime = 0;
    private static final int SEARCH_BAR_WIDTH = 200;
    private static final int SEARCH_BAR_HEIGHT = 20;
    private boolean fullScreenMode = false;
    private int previousBrowserWidth = -1;
    private int previousBrowserHeight = -1;
    private final Screen parent;
    private IconWithTooltip fullscreenIcon;
    private IconWithTooltip closeIcon;
    private IconWithTooltip reloadIcon;
    private IconWithTooltip goBackIcon;
    private IconWithTooltip goForwardIcon;
    private Render.TabsBar<Tab> tabsBar;

    public BrowserScreen(MinecraftClient client, Screen parent, String url) {
        super(Text.literal("Browser"));
        this.minecraftClient = client;
        this.parent = parent;
        checkIfMcefExist();
        this.startUrl = url;
        originalMCScale = minecraftClient.getWindow().getScaleFactor();
        targetScaleFactor = globalScaleFactor;
        minecraftClient.getWindow().setScaleFactor(globalScaleFactor);
    }

    public class Tab {
        public String url;
        public MCEFBrowser browser;
        public TextAnimator textAnimator;
        public Tab(String url, MCEFBrowser browser) {
            this.url = url;
            String title = trimUrl(url);
            this.browser = browser;
            this.textAnimator = new TextAnimator(title, 0, 30);
            this.textAnimator.start();
        }
        public String getAnimatedText() {
            return textAnimator.getCurrentText();
        }
        @Override
        public String toString() {
            return getAnimatedText();
        }
    }

    private String trimUrl(String url) {
        try {
            String withoutProtocol = url.replaceFirst("^(http://|https://)", "");
            withoutProtocol = withoutProtocol.replaceFirst("^www\\.", "");
            int queryIndex = withoutProtocol.indexOf("?");
            if(queryIndex != -1) {
                withoutProtocol = withoutProtocol.substring(0, queryIndex);
            }
            if (withoutProtocol.charAt(withoutProtocol.length() - 1) == '/') withoutProtocol = withoutProtocol.substring(0, withoutProtocol.length() - 1);
            return withoutProtocol;
        } catch(Exception e) {
            return url;
        }
    }

    private boolean hasSelection() {
        return urlSelectionStart != -1 && urlSelectionEnd != -1 && urlSelectionStart != urlSelectionEnd;
    }

    private void clearSelection() {
        urlSelectionStart = -1;
        urlSelectionEnd = -1;
    }

    @Override
    protected void init() {
        super.init();
        if (tabs.isEmpty()) {
            MCEFBrowser newBrowser = MCEF.createBrowser(startUrl, true);
            tabs.add(new Tab(startUrl, newBrowser));
            urlFieldText.setLength(0);
            urlFieldText.append(startUrl);
            urlCursorPosition = urlFieldText.length();
            resizeBrowser(newBrowser);
        }
        try {
            fullscreenIcon = new IconWithTooltip("/assets/remotely/icons/fullscreen.png", "Toggle Fullscreen Mode");
            closeIcon = new IconWithTooltip("/assets/remotely/icons/close.png", "");
            reloadIcon = new IconWithTooltip("/assets/remotely/icons/reload.png", "Reload Current Page");
            goBackIcon = new IconWithTooltip("/assets/remotely/icons/goback.png", "");
            goForwardIcon = new IconWithTooltip("/assets/remotely/icons/goforward.png", "");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        List<Render.TabsBar.Tab<Tab>> tabList = new ArrayList<>();
        for (Tab t : tabs) {
            tabList.add(new Render.TabsBar.Tab<>(t.getAnimatedText(), false, t));
        }
        tabsBar = new Render.TabsBar<>(tabList);
        tabsBar.setActiveTab(currentTabIndex);
        tabsBar.setHasPlus(true);
        tabsBar.setAllowClose(tabCloseButtons);
        tabsBar.setAllowRename(false);
        tabsBar.setAllowDrag(true);
        tabsBar.setAllowScroll(true);
        tabsBar.setTabBarBounds(5, 35, this.width - 5, TAB_HEIGHT);
        tabsBar.setOnTabOrderChanged(() -> {
            List<Tab> newTabs = tabsBar.getTabs().stream().map(tab -> tab.data).toList();
            tabs.clear();
            tabs.addAll(newTabs);
            currentTabIndex = tabsBar.getActiveTab();
        });
        tabsBar.setOnTabClosed(() -> {
            int idx = tabsBar.getActiveTab();
            if (idx >= 0 && idx < tabs.size()) {
                Tab removed = tabs.get(idx);
                removed.browser.close();
                tabs.remove(idx);
                tabsBar.getTabs().remove(idx);
                if (tabs.isEmpty()) {
                    minecraftClient.setScreen(parent);
                } else {
                    if (currentTabIndex >= tabs.size()) currentTabIndex = tabs.size() - 1;
                    tabsBar.setActiveTab(currentTabIndex);
                }
            }
        });
        tabsBar.setOnTabSelected(() -> {
            currentTabIndex = tabsBar.getActiveTab();
            if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
                urlFieldText.setLength(0);
                urlFieldText.append(tabs.get(currentTabIndex).url);
                urlCursorPosition = urlFieldText.length();
            }
        });
        tabsBar.setOnTabPlus(() -> {
            MCEFBrowser newBrowser = MCEF.createBrowser("https://www.google.com", true);
            Tab newTab = new Tab("https://www.google.com", newBrowser);
            tabs.add(newTab);
            tabsBar.getTabs().add(new Render.TabsBar.Tab<>(newTab.getAnimatedText(), false, newTab));
            currentTabIndex = tabs.size() - 1;
            tabsBar.setActiveTab(currentTabIndex);
            urlFieldText.setLength(0);
            urlFieldText.append("https://www.google.com");
            urlCursorPosition = urlFieldText.length();
            resizeBrowser(newBrowser);
        });
    }

    public static boolean checkIfMcefExist() {
        try {
            if (!FabricLoader.getInstance().isModLoaded("mcef")) {
                new Notification("MCEF Isn't Installed. Click Here To Download (Coming Soon)", Notification.Type.ERROR);
                return false;
            }
        } catch (Exception e) {
            new Notification("Error checking for MCEF mod: " + e.getMessage(), Notification.Type.ERROR);
            return false;
        }
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        MCEFBrowser currentBrowser = tabs.get(currentTabIndex).browser;
        if (fullScreenMode) {
            drawBrowser(currentBrowser, fullScreenMode, width, height, 0, 0);
            return;
        }
        drawHeader(context, width, height, mouseX, mouseY);
        drawBrowser(currentBrowser, fullScreenMode, width, height, TOP_OFFSET, BROWSER_DRAW_OFFSET);
        drawInnerBorder(context, 5, 60, width - 5 * 2, height - 60 - 5, innerBorderColor);
        animatedScaling(context, this, minecraftClient);
    }

    private void drawHeader(DrawContext context, int width, int height, int mouseX, int mouseY) {
        drawScreenHeader(context, width, height, width - 5, mouseX, mouseY, this, minecraftClient, closeIcon, fullscreenIcon, null, null, goBackIcon, goForwardIcon, null, null, reloadIcon);
        tabsBar.setTabBarBounds(5, 35, width - 5, TAB_HEIGHT);
        tabsBar.renderTabsBar(context, minecraftClient.textRenderer, tabsBar, mouseX, mouseY, shadow);
        tabsBar.setActiveTabName(tabs.get(Math.min(currentTabIndex, tabs.size()-1)).getAnimatedText());
        String displayUrl = urlFieldFocused ? urlFieldText.toString() : trimUrl(urlFieldText.toString());
        drawSearchBar(context, minecraftClient.textRenderer, new StringBuilder(displayUrl), urlFieldFocused, urlCursorPosition, urlSelectionStart, urlSelectionEnd, urlScrollOffset, urlTargetScrollOffset, false, "BrowserScreen", mouseX, mouseY, "Search In Google or Enter a URL");
    }

    private int convertMouseX(double x) {
        if(fullScreenMode) return (int)(x * minecraftClient.getWindow().getScaleFactor());
        return (int)((x - BROWSER_DRAW_OFFSET) * minecraftClient.getWindow().getScaleFactor());
    }

    private int convertMouseY(double y) {
        if(fullScreenMode) return (int)(y * minecraftClient.getWindow().getScaleFactor());
        return (int)((y - TOP_OFFSET) * minecraftClient.getWindow().getScaleFactor());
    }

    private void resizeBrowser(MCEFBrowser browser) {
        if (width > 100 && height > 100) {
            int browserWidth;
            int browserHeight;
            if(fullScreenMode) {
                browserWidth = width;
                browserHeight = height;
            } else {
                browserWidth = width - (BROWSER_DRAW_OFFSET * 2);
                browserHeight = height - TOP_OFFSET - BROWSER_DRAW_OFFSET;
            }

            int scaledWidth = (int)(browserWidth * minecraftClient.getWindow().getScaleFactor());
            int scaledHeight = (int)(browserHeight * minecraftClient.getWindow().getScaleFactor());

            if (scaledWidth != previousBrowserWidth || scaledHeight != previousBrowserHeight) {
                browser.resize(scaledWidth, scaledHeight);
                previousBrowserWidth = scaledWidth;
                previousBrowserHeight = scaledHeight;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabsBar.handleTabsBarMouse((int)mouseX, (int)mouseY, button)) return true;
        int searchBarX = (width - SEARCH_BAR_WIDTH) / 2;
        int searchBarY = 5;
        if(mouseX >= searchBarX && mouseX <= searchBarX + SEARCH_BAR_WIDTH && mouseY >= searchBarY && mouseY <= searchBarY + SEARCH_BAR_HEIGHT) {
            if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                playClick();
                urlFieldFocused = true;
                int clickX = (int) mouseX - searchBarX - 5;
                int pos = 0;
                int cumulativeWidth = 0;
                for (int i = 0; i < urlFieldText.length(); i++) {
                    int charWidth = minecraftClient.textRenderer.getWidth(urlFieldText.substring(i, i+1));
                    if(cumulativeWidth + charWidth/2 > clickX) {
                        pos = i;
                        break;
                    }
                    cumulativeWidth += charWidth;
                    pos = i + 1;
                }
                urlCursorPosition = pos;
                urlSelectionStart = pos;
                urlSelectionEnd = pos;
                return true;
            }
        } else {
            urlFieldFocused = false;
        }

        if(button == 3 || button == 4) {
            playClick();
            if(button == 3) {
                tabs.get(currentTabIndex).browser.goBack();
            } else {
                tabs.get(currentTabIndex).browser.goForward();
            }
            return true;
        }

        if(fullScreenMode) {
            tabs.get(currentTabIndex).browser.sendMousePress(convertMouseX(mouseX), convertMouseY(mouseY), button);
            tabs.get(currentTabIndex).browser.setFocus(true);
            return true;
        }

        boolean yArea = mouseY >= 6 && mouseY <= 24;
        if(yArea) {
            if(mouseX >= 5 && mouseX <= 22) {
                playClick();
                tabs.get(currentTabIndex).browser.goBack();
                return true;
            }
            if(mouseX >= 28 && mouseX <= 45) {
                playClick();
                tabs.get(currentTabIndex).browser.goForward();
                return true;
            }
            int specialIconX = (width - SEARCH_BAR_WIDTH) / 2 - 23;
            if(mouseX >= specialIconX && mouseX <= specialIconX + 17) {
                playClick();
                tabs.get(currentTabIndex).browser.reload();
                resizeBrowser(tabs.get(currentTabIndex).browser);
                return true;
            }
            if(mouseX >= width - 46 && mouseX <= width - 29) {
                playClick();
                fullScreenMode = true;
                resizeBrowser(tabs.get(currentTabIndex).browser);
                return true;
            }
            if(mouseX >= width - 23 && mouseX <= width - 6) {
                playClick();
                minecraftClient.setScreen(null);
                return true;
            }
        }

        int titleBarHeight = 30;
        int tabBarHeight = 18;
        if(mouseY <= titleBarHeight + tabBarHeight + 10) {
            int tabBarX = 5;
            int tabBarY = 35;
            int tabBarEndY = tabBarY + tabBarHeight;
            boolean tabClicked = false;
            int x = tabBarX;
            int tabGap = 5;
            int tabPadding = 5;
            for (int i = 0; i < tabs.size(); i++) {
                Tab tab = tabs.get(i);
                int tabWidth = minecraftClient.textRenderer.getWidth(tab.getAnimatedText()) + 2 * tabPadding;
                if(mouseX >= x && mouseX <= x + tabWidth && mouseY >= tabBarY && mouseY <= tabBarEndY) {
                    if(button == 2) {
                        playClick();
                        if(tabs.size() > 1) {
                            tabs.get(i).browser.close();
                            tabs.remove(i);
                            if (i < tabsBar.getTabs().size()) {
                                tabsBar.getTabs().remove(i);
                            }
                            if(currentTabIndex >= tabs.size()) {
                                currentTabIndex = tabs.size() - 1;
                            }
                            tabsBar.setActiveTab(currentTabIndex);
                        } else {
                            tabs.get(i).browser.close();
                            tabs.clear();
                            tabsBar.getTabs().clear();
                            minecraftClient.setScreen(parent);
                        }
                        return true;
                    } else {
                        playClick();
                        currentTabIndex = i;
                        urlFieldText.setLength(0);
                        urlFieldText.append(tabs.get(currentTabIndex).url);
                        urlCursorPosition = urlFieldText.length();
                        tabClicked = true;
                        break;
                    }
                }
                x += tabWidth + tabGap;
            }
            if(!tabClicked && mouseX >= x && mouseX <= x + 18 && mouseY >= tabBarY && mouseY <= tabBarEndY) {
                playClick();
                MCEFBrowser newBrowser = MCEF.createBrowser("www.google.com", true);
                Tab newTab = new Tab("www.google.com", newBrowser);
                tabs.add(newTab);
                tabsBar.getTabs().add(new Render.TabsBar.Tab<>(newTab.getAnimatedText(), false, newTab));
                currentTabIndex = tabs.size() - 1;
                tabsBar.setActiveTab(currentTabIndex);
                urlFieldText.setLength(0);
                urlFieldText.append("www.google.com");
                urlCursorPosition = urlFieldText.length();
                resizeBrowser(newBrowser);
                return true;
            }
        } else {
            Tab currentTab = tabs.get(currentTabIndex);
            currentTab.browser.sendMousePress(convertMouseX(mouseX), convertMouseY(mouseY), button);
            currentTab.browser.setFocus(true);
        }

        if(Render.ContextMenu.isOpen()){
            if(Render.ContextMenu.mouseClicked(mouseX, mouseY, button)){
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tabsBar.handleTabsBarRelease(button)) return true;
        Tab currentTab = tabs.get(currentTabIndex);
        currentTab.browser.sendMouseRelease(convertMouseX(mouseX), convertMouseY(mouseY), button);
        currentTab.browser.setFocus(true);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        Tab currentTab = tabs.get(currentTabIndex);
        currentTab.browser.sendMouseMove(convertMouseX(mouseX), convertMouseY(mouseY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tabsBar.handleTabsBarScroll(verticalAmount, mouseX, mouseY)) return true;
        scaleScroll(verticalAmount);
        Tab currentTab = tabs.get(currentTabIndex);
        currentTab.browser.sendMouseWheel(convertMouseX(mouseX), convertMouseY(mouseY), verticalAmount - horizontalAmount, 0);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (tabsBar.handleTabsBarDrag(button, deltaX)) return true;
        int searchBarX = (width - SEARCH_BAR_WIDTH) / 2;
        int searchBarY = 5;
        if(urlFieldFocused && mouseX >= searchBarX && mouseX <= searchBarX + SEARCH_BAR_WIDTH && mouseY >= searchBarY && mouseY <= searchBarY + SEARCH_BAR_HEIGHT) {
            int clickX = (int) mouseX - searchBarX - 5;
            int pos = 0;
            int cumulativeWidth = 0;
            for (int i = 0; i < urlFieldText.length(); i++) {
                int charWidth = minecraftClient.textRenderer.getWidth(urlFieldText.substring(i, i+1));
                if(cumulativeWidth + charWidth/2 > clickX) {
                    pos = i;
                    break;
                }
                cumulativeWidth += charWidth;
                pos = i + 1;
            }
            urlSelectionEnd = pos;
            urlCursorPosition = pos;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tabsBar.handleTabsBarKey(keyCode, scanCode, modifiers)) return true;
        if(keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (fullScreenMode) {
                fullScreenMode = false;
                resizeBrowser(tabs.get(currentTabIndex).browser);
                return true;
            } else if (urlFieldFocused) {
                urlFieldFocused = false;
                return true;
            } else {
                minecraftClient.setScreen(parent);
                return true;
            }
        }
        if((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if(urlFieldFocused) {
                if(keyCode == GLFW.GLFW_KEY_A) {
                    urlSelectionStart = 0;
                    urlSelectionEnd = urlFieldText.length();
                    urlCursorPosition = urlFieldText.length();
                    return true;
                }
                if(keyCode == GLFW.GLFW_KEY_C) {
                    if(hasSelection()){
                        int start = Math.min(urlSelectionStart, urlSelectionEnd);
                        int end = Math.max(urlSelectionStart, urlSelectionEnd);
                        String copyText = urlFieldText.substring(start, end);
                        try {
                            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(copyText);
                            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                        } catch(Exception ignored) {}
                    }
                    return true;
                }
                if(keyCode == GLFW.GLFW_KEY_V) {
                    String data = "";
                    try {
                        java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                        data = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                    } catch(Exception ignored) {}
                    if(data != null) {
                        if(hasSelection()){
                            int start = Math.min(urlSelectionStart, urlSelectionEnd);
                            int end = Math.max(urlSelectionStart, urlSelectionEnd);
                            urlFieldText.delete(start, end);
                            urlCursorPosition = start;
                            clearSelection();
                        }
                        urlFieldText.insert(urlCursorPosition, data);
                        urlCursorPosition += data.length();
                    }
                    return true;
                }
                if(keyCode == GLFW.GLFW_KEY_LEFT) {
                    if(hasSelection()){
                        urlCursorPosition = Math.min(urlSelectionStart, urlSelectionEnd);
                        clearSelection();
                        return true;
                    } else if(urlCursorPosition > 0) {
                        int newPos = urlFieldText.lastIndexOf(" ", urlCursorPosition - 1);
                        if(newPos == -1) newPos = 0;
                        urlCursorPosition = newPos;
                        return true;
                    }
                }
                if(keyCode == GLFW.GLFW_KEY_RIGHT) {
                    if(hasSelection()){
                        urlCursorPosition = Math.max(urlSelectionStart, urlSelectionEnd);
                        clearSelection();
                        return true;
                    } else if(urlCursorPosition < urlFieldText.length()){
                        int newPos = urlFieldText.indexOf(" ", urlCursorPosition);
                        if(newPos == -1) newPos = urlFieldText.length();
                        urlCursorPosition = newPos;
                        return true;
                    }
                }
                if(keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if(hasSelection()){
                        int start = Math.min(urlSelectionStart, urlSelectionEnd);
                        int end = Math.max(urlSelectionStart, urlSelectionEnd);
                        urlFieldText.delete(start, end);
                        urlCursorPosition = start;
                        clearSelection();
                        return true;
                    }
                }
            }
            if(keyCode == GLFW.GLFW_KEY_T) {
                MCEFBrowser newBrowser = MCEF.createBrowser("google.com", true);
                tabs.add(new Tab("google.com", newBrowser));
                currentTabIndex = tabs.size() - 1;
                urlFieldText.setLength(0);
                urlFieldText.append("google.com");
                urlCursorPosition = urlFieldText.length();
                resizeBrowser(newBrowser);
                return true;
            }
            if(keyCode == GLFW.GLFW_KEY_W) {
                if(tabs.size() > 1) {
                    tabs.remove(currentTabIndex);
                    if(currentTabIndex >= tabs.size()) {
                        currentTabIndex = tabs.size() - 1;
                    }
                } else {
                    minecraftClient.setScreen(null);
                }
                return true;
            }
            if(keyCode == GLFW.GLFW_KEY_TAB) {
                if((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                    currentTabIndex = (currentTabIndex - 1 + tabs.size()) % tabs.size();
                } else {
                    currentTabIndex = (currentTabIndex + 1) % tabs.size();
                }
                urlFieldText.setLength(0);
                urlFieldText.append(tabs.get(currentTabIndex).url);
                urlCursorPosition = urlFieldText.length();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_F) {
                urlFieldFocused = !urlFieldFocused;
                return true;
            }
        }
        if(keyCode == GLFW.GLFW_KEY_F5) {
            tabs.get(currentTabIndex).browser.reload();
            return true;
        }
        if(urlFieldFocused) {
            if(keyCode == GLFW.GLFW_KEY_ENTER) {
                String url = urlFieldText.toString();
                try {
                    new URL(url);
                } catch(Exception e) {
                    try {
                        url = "https://www.google.com/search?q=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
                    } catch(Exception ignored) {}
                }
                tabs.get(currentTabIndex).url = url;
                tabs.get(currentTabIndex).textAnimator.updateText(trimUrl(url));
                tabs.get(currentTabIndex).browser.loadURL(url);
                urlFieldFocused = false;
                return true;
            }
            if(keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if(hasSelection()){
                    int start = Math.min(urlSelectionStart, urlSelectionEnd);
                    int end = Math.max(urlSelectionStart, urlSelectionEnd);
                    urlFieldText.delete(start, end);
                    urlCursorPosition = start;
                    clearSelection();
                } else if(urlCursorPosition > 0) {
                    urlFieldText.deleteCharAt(urlCursorPosition - 1);
                    urlCursorPosition--;
                }
                return true;
            }
            if(keyCode == GLFW.GLFW_KEY_DELETE) {
                if(hasSelection()){
                    int start = Math.min(urlSelectionStart, urlSelectionEnd);
                    int end = Math.max(urlSelectionStart, urlSelectionEnd);
                    urlFieldText.delete(start, end);
                    urlCursorPosition = start;
                    clearSelection();
                } else if(urlCursorPosition < urlFieldText.length()){
                    urlFieldText.deleteCharAt(urlCursorPosition);
                }
                return true;
            }
            if(keyCode == GLFW.GLFW_KEY_LEFT) {
                if(urlCursorPosition > 0) {
                    urlCursorPosition--;
                }
                clearSelection();
                return true;
            }
            if(keyCode == GLFW.GLFW_KEY_RIGHT) {
                if(urlCursorPosition < urlFieldText.length()){
                    urlCursorPosition++;
                }
                clearSelection();
                return true;
            }
        }
        tabs.get(currentTabIndex).browser.sendKeyPress(keyCode, scanCode, modifiers);
        tabs.get(currentTabIndex).browser.setFocus(true);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        tabs.get(currentTabIndex).browser.sendKeyRelease(keyCode, scanCode, modifiers);
        tabs.get(currentTabIndex).browser.setFocus(true);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (tabsBar.handleTabsBarChar(chr)) return true;
        if(urlFieldFocused) {
            if(hasSelection()){
                int start = Math.min(urlSelectionStart, urlSelectionEnd);
                int end = Math.max(urlSelectionStart, urlSelectionEnd);
                urlFieldText.delete(start, end);
                urlCursorPosition = start;
                clearSelection();
            }
            if(chr != '\n' && chr != '\r' && chr != '\b'){
                urlFieldText.insert(urlCursorPosition, chr);
                urlCursorPosition++;
                return true;
            }
        }
        if(chr == (char)0) return false;
        tabs.get(currentTabIndex).browser.sendKeyTyped(chr, modifiers);
        tabs.get(currentTabIndex).browser.setFocus(true);
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (urlFieldFocused) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - urlLastBlinkTime >= 500) {
                urlShowCursor = !urlShowCursor;
                urlLastBlinkTime = currentTime;
            }
        }
        Tab currentTab = tabs.get(currentTabIndex);
        String currentBrowserUrl = currentTab.browser.getURL();
        if (currentBrowserUrl != null && !currentBrowserUrl.equals(currentTab.url)) {
            currentTab.url = currentBrowserUrl;
            currentTab.textAnimator.updateText(currentBrowserUrl.replaceFirst("^(http://|https://|www\\.)", "").replaceAll("/.*", ""));
            if (!urlFieldFocused) {
                urlFieldText.setLength(0);
                urlFieldText.append(currentBrowserUrl);
                urlCursorPosition = urlFieldText.length();
            }
        }

        resizeBrowser(tabs.get(currentTabIndex).browser);
    }

    public static void closeAll() {
        for (Tab tab : tabs) {
            tab.browser.close();
        }
        tabs.clear();
    }

    @Override
    public void removed() {
        minecraftClient.getWindow().setScaleFactor(originalMCScale);
        targetScaleFactor = globalScaleFactor = animScaleFactor;
    }
}

package redxax.oxy.common.servers;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import redxax.oxy.common.util.TabTextAnimator;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static net.minecraft.client.render.BufferRenderer.draw;
import static redxax.oxy.common.Render.*;
import static redxax.oxy.common.config.Config.*;
import static redxax.oxy.common.util.ImageUtil.drawPixelArt;
import static redxax.oxy.common.util.ImageUtil.loadResourceIcon;

public class BrowserScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final String startUrl;
    private static final int BROWSER_DRAW_OFFSET = 5;
    private final int TOP_OFFSET = 60;
    private static List<Tab> tabs = new ArrayList<>();
    private static int currentTabIndex = 0;
    private final StringBuilder urlFieldText = new StringBuilder();
    private boolean urlFieldFocused = false;
    private int urlCursorPosition = 0;
    private int urlSelectionStart = -1;
    private int urlSelectionEnd = -1;
    private float urlScrollOffset = 0;
    private float urlTargetScrollOffset = 0;
    private boolean urlShowCursor = true;
    private long urlLastBlinkTime = 0;
    private static final int SEARCH_BAR_WIDTH = 200;
    private static final int SEARCH_BAR_HEIGHT = 20;
    private boolean fullScreenMode = false;
    private int previousBrowserWidth = -1;
    private int previousBrowserHeight = -1;
    private Screen parent;
    private BufferedImage fullscreenIcon, closeIcon, reloadIcon, goBackIcon, goForwardIcon;

    public BrowserScreen(MinecraftClient client, Screen parent, String url) {
        super(Text.literal("Browser"));
        this.minecraftClient = client;
        this.parent = parent;
        this.startUrl = url;
    }

    public class Tab {
        public String url;
        public MCEFBrowser browser;
        public TabTextAnimator textAnimator;
        public Tab(String url, MCEFBrowser browser) {
            this.url = url;
            String title = trimUrl(url);
            this.browser = browser;
            this.textAnimator = new TabTextAnimator(title, 0, 30);
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
        if(tabs.isEmpty()){
            MCEFBrowser newBrowser = MCEF.createBrowser(startUrl, true);
            tabs.add(new Tab(startUrl, newBrowser));
            urlFieldText.setLength(0);
            urlFieldText.append(startUrl);
            urlCursorPosition = urlFieldText.length();
            resizeBrowser(newBrowser);
        }
        try {
            fullscreenIcon = loadResourceIcon("/assets/remotely/icons/fullscreen.png");
            closeIcon = loadResourceIcon("/assets/remotely/icons/close.png");
            reloadIcon = loadResourceIcon("/assets/remotely/icons/reload.png");
            goBackIcon = loadResourceIcon("/assets/remotely/icons/goback.png");
            goForwardIcon = loadResourceIcon("/assets/remotely/icons/goforward.png");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if(fullScreenMode) {
            MCEFBrowser currentBrowser = tabs.get(currentTabIndex).browser;
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentBrowser.getRenderer().getTextureID());
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0f, 1.0f);
            GL11.glVertex3f(0, height, 0);
            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex3f(width, height, 0);
            GL11.glTexCoord2f(1.0f, 0.0f);
            GL11.glVertex3f(width, 0, 0);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex3f(0, 0, 0);
            GL11.glEnd();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            return;
        }
        context.fill(0, 0, width, height, 0xFF202020);
        drawHeader(context, width, height, mouseX, mouseY);
        MCEFBrowser currentBrowser = tabs.get(currentTabIndex).browser;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentBrowser.getRenderer().getTextureID());
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 1.0f);
        GL11.glVertex3f(BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0);
        GL11.glTexCoord2f(1.0f, 1.0f);
        GL11.glVertex3f(width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0);
        GL11.glTexCoord2f(1.0f, 0.0f);
        GL11.glVertex3f(width - BROWSER_DRAW_OFFSET, TOP_OFFSET, 0);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex3f(BROWSER_DRAW_OFFSET, TOP_OFFSET, 0);
        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        drawInnerBorder(context, BROWSER_DRAW_OFFSET, TOP_OFFSET, width - BROWSER_DRAW_OFFSET * 2, height - TOP_OFFSET - BROWSER_DRAW_OFFSET, editorInnerBackgroundColor);
        drawOuterBorder(context, BROWSER_DRAW_OFFSET, TOP_OFFSET, width - BROWSER_DRAW_OFFSET * 2, height - TOP_OFFSET - BROWSER_DRAW_OFFSET, globalBottomBorder);
    }

    private void drawHeader(DrawContext context, int width, int height, int mouseX, int mouseY) {
        context.fill(0, 0, this.width, 30, headerBackgroundColor);
        drawInnerBorder(context, 0, 0, this.width, 30, headerBorderColor);
        drawOuterBorder(context, 0, 0, this.width, 30, globalBottomBorder);
        drawTabs(context, minecraftClient.textRenderer, tabs, currentTabIndex, mouseX, mouseY, true, false);
        String displayUrl = urlFieldFocused ? urlFieldText.toString() : trimUrl(urlFieldText.toString());
        drawSearchBar(context, minecraftClient.textRenderer, new StringBuilder(displayUrl), urlFieldFocused, urlCursorPosition, urlSelectionStart, urlSelectionEnd, urlScrollOffset, urlTargetScrollOffset, urlShowCursor, false, "BrowserScreen");

        boolean isBackButtonHovered = mouseX >= BROWSER_DRAW_OFFSET && mouseX <= BROWSER_DRAW_OFFSET + 18 && mouseY >= 5 && mouseY <= 23;
        boolean isForwardButtonHovered = mouseX >= BROWSER_DRAW_OFFSET + 23 && mouseX <= BROWSER_DRAW_OFFSET + 41 && mouseY >= 5 && mouseY <= 23;
        drawSquareButton(context, BROWSER_DRAW_OFFSET, 5, minecraftClient, "", isBackButtonHovered, buttonTextColor, buttonTextHoverColor);
        drawPixelArt(context, goBackIcon, BROWSER_DRAW_OFFSET + 2-1, 6, 16, 16);
        drawSquareButton(context, BROWSER_DRAW_OFFSET + 23, 5, minecraftClient, "", isForwardButtonHovered, buttonTextColor, buttonTextHoverColor);
        drawPixelArt(context, goForwardIcon, BROWSER_DRAW_OFFSET + 25-1, 6, 16, 16);

        boolean isReloadButtonHovered = mouseX >= (width - SEARCH_BAR_WIDTH) / 2 - 23 && mouseX <= (width - SEARCH_BAR_WIDTH) / 2 - 5 && mouseY >= 5 && mouseY <= 23;
        drawSquareButton(context, (width - SEARCH_BAR_WIDTH) / 2 - 23, 5, minecraftClient, "", isReloadButtonHovered, buttonTextColor, buttonTextHoverColor);
        drawPixelArt(context, reloadIcon, (width - SEARCH_BAR_WIDTH) / 2 - 21, 6, 16, 16);

        boolean isCloseButtonHovered = mouseX >= width - 23 && mouseX <= width - 5 && mouseY >= 5 && mouseY <= 23;
        drawSquareButton(context, width - 23, 5, minecraftClient, "", isCloseButtonHovered, buttonTextColor, buttonTextCancelColor);
        drawPixelArt(context, closeIcon, width - 21 -1, 6, 16, 16);
        boolean isFullScreenButtonHovered = mouseX >= width - 46 && mouseX <= width - 26 && mouseY >= 5 && mouseY <= 23;
        drawSquareButton(context, width - 46, 5, minecraftClient, "", isFullScreenButtonHovered, buttonTextColor, buttonTextHoverColor);
        drawPixelArt(context, fullscreenIcon, width - 44 -1, 6, 16, 16);
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
        int searchBarX = (width - SEARCH_BAR_WIDTH) / 2;
        int searchBarY = 5;
        if(mouseX >= searchBarX && mouseX <= searchBarX + SEARCH_BAR_WIDTH && mouseY >= searchBarY && mouseY <= searchBarY + SEARCH_BAR_HEIGHT) { // Search bar
            if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                urlFieldFocused = true;
                int clickX = (int)mouseX - searchBarX - 5 + (int)urlScrollOffset;
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
            } else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                ContextMenu.addItem("Cut", () -> {
                    if(urlFieldFocused && urlFieldText.length() > 0 && hasSelection()) {
                        int start = Math.min(urlSelectionStart, urlSelectionEnd);
                        int end = Math.max(urlSelectionStart, urlSelectionEnd);
                        urlFieldText.delete(start, end);
                        urlCursorPosition = start;
                        clearSelection();
                    }
                }, buttonTextColor);
                ContextMenu.addItem("Copy", () -> {
                    if(urlFieldFocused && urlFieldText.length() > 0 && hasSelection()) {
                        int start = Math.min(urlSelectionStart, urlSelectionEnd);
                        int end = Math.max(urlSelectionStart, urlSelectionEnd);
                        String copyText = urlFieldText.substring(start, end);
                        try {
                            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(copyText);
                            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                        } catch(Exception e) {}
                    }
                }, buttonTextColor);
                ContextMenu.addItem("Paste", () -> {
                    try {
                        java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                        String data = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
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
                    } catch(Exception e) {}
                }, buttonTextColor);
                ContextMenu.addItem("Select All", () -> {
                    urlSelectionStart = 0;
                    urlSelectionEnd = urlFieldText.length();
                    urlCursorPosition = urlFieldText.length();
                }, buttonTextColor);
                ContextMenu.show((int)mouseX, (int)mouseY, 100, width, height);
                return true;
            }
        } else {
            urlFieldFocused = false;
        }
        if(button == 3) {
            tabs.get(currentTabIndex).browser.goBack();
            return true;
        }
        if(button == 4) {
            tabs.get(currentTabIndex).browser.goForward();
            return true;
        }
        if(fullScreenMode) {
            tabs.get(currentTabIndex).browser.sendMousePress(convertMouseX(mouseX), convertMouseY(mouseY), button);
            tabs.get(currentTabIndex).browser.setFocus(true);
            return true;
        }
        boolean backButton = mouseY >= 5 && mouseY <= 23;
        if(backButton) {
            if(mouseX >= BROWSER_DRAW_OFFSET && mouseX <= BROWSER_DRAW_OFFSET + 18) {
                tabs.get(currentTabIndex).browser.goBack();
                return true;
            }
            boolean forwardButton = mouseX >= BROWSER_DRAW_OFFSET + 23 && mouseX <= BROWSER_DRAW_OFFSET + 41;
            if(forwardButton) {
                tabs.get(currentTabIndex).browser.goForward();
                return true;
            }
            int reloadXStart = (width - SEARCH_BAR_WIDTH) / 2 - 23;
            int reloadXEnd = (width - SEARCH_BAR_WIDTH) / 2 - 5;
            boolean reloadButton = mouseX >= reloadXStart && mouseX <= reloadXEnd;
            if(reloadButton) {
                tabs.get(currentTabIndex).browser.reload();
                resizeBrowser(tabs.get(currentTabIndex).browser);
                return true;
            }
            boolean fullscreenButton = mouseX >= width - 46 && mouseX <= width - 26;
            if(fullscreenButton) {
                fullScreenMode = true;
                resizeBrowser(tabs.get(currentTabIndex).browser);
                return true;
            }
            boolean closeButton = mouseX >= width - 23 && mouseX <= width - 5;
            if(closeButton) {
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
                        if(tabs.size() > 1) {
                            tabs.get(i).browser.close();
                            tabs.remove(i);
                            if(currentTabIndex >= tabs.size()) {
                                currentTabIndex = tabs.size() - 1;
                            }
                        } else {
                            tabs.get(i).browser.close();
                            minecraftClient.setScreen(parent);
                        }
                        return true;
                    } else {
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
            if(!tabClicked) {
                boolean plusButton = mouseX >= x && mouseX <= x + 18 && mouseY >= tabBarY && mouseY <= tabBarEndY;
                if(plusButton) {
                    MCEFBrowser newBrowser = MCEF.createBrowser("www.google.com", true);
                    tabs.add(new Tab("www.google.com", newBrowser));
                    currentTabIndex = tabs.size() - 1;
                    urlFieldText.setLength(0);
                    urlFieldText.append("www.google.com");
                    urlCursorPosition = urlFieldText.length();
                    resizeBrowser(newBrowser);
                    return true;
                }
            }
        } else {
            Tab currentTab = tabs.get(currentTabIndex);
            currentTab.browser.sendMousePress(convertMouseX(mouseX), convertMouseY(mouseY), button);
            currentTab.browser.setFocus(true);
        }
        if(ContextMenu.isOpen()){
            if(ContextMenu.mouseClicked(mouseX, mouseY, button)){
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
        Tab currentTab = tabs.get(currentTabIndex);
        currentTab.browser.sendMouseWheel(convertMouseX(mouseX), convertMouseY(mouseY), verticalAmount - horizontalAmount, 0);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int searchBarX = (width - SEARCH_BAR_WIDTH) / 2;
        int searchBarY = 5;
        if(urlFieldFocused && mouseX >= searchBarX && mouseX <= searchBarX + SEARCH_BAR_WIDTH && mouseY >= searchBarY && mouseY <= searchBarY + SEARCH_BAR_HEIGHT) {
            int clickX = (int)mouseX - searchBarX - 5 + (int)urlScrollOffset;
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
                        } catch(Exception e) {}
                    }
                    return true;
                }
                if(keyCode == GLFW.GLFW_KEY_V) {
                    String data = "";
                    try {
                        java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                        data = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                    } catch(Exception e) {}
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
                        int start = Math.min(urlSelectionStart, urlSelectionEnd);
                        urlCursorPosition = start;
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
                        int end = Math.max(urlSelectionStart, urlSelectionEnd);
                        urlCursorPosition = end;
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
                        url = "https://www.google.com/search?q=" + URLEncoder.encode(url, StandardCharsets.UTF_8.toString());
                    } catch(Exception ex) {}
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
}

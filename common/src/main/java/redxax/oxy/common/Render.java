package redxax.oxy.common;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.common.config.Config;
import redxax.oxy.common.servers.SettingsScreen;
import redxax.oxy.common.terminal.MultiTerminalScreen;
import redxax.oxy.common.explorer.FileExplorerScreen;
import redxax.oxy.common.explorer.FileEditorScreen;
import redxax.oxy.common.servers.PluginModManagerScreen;
import redxax.oxy.common.util.TextAnimator;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static redxax.oxy.common.config.Config.*;
import static redxax.oxy.common.explorer.FileExplorerScreen.getIconForFile;
import static redxax.oxy.common.util.DevUtil.devPrint;
import static redxax.oxy.common.util.ImageUtil.*;
import static redxax.oxy.common.util.SoundUtils.playClick;

public class Render {

    public static int buttonW = 60;
    public static int buttonH = 18;
    private static TextAnimator searchTextAnimator = new TextAnimator("", 2, 10);
    private static String previousFieldText = "";
    private static boolean isAnimating = false;
    private static BufferedImage loadingAnim;
    private static final List<BufferedImage> loadingFrames = new ArrayList<>();
    private static int currentLoadingFrame = 0;
    private static long lastFrameTime = 0;
    private static float scrollInterpolation = 0.15f;
    private static float currentScrollOffset = 0f;
    private static float animatedOffset = 0;
    private static float scrollSelectorIndexFloat = 0f;
    private static int previousScrollSelectorIndex = -1;
    private static Map<String, Float> tabWidths;
    private static int previousTabCount = 0;

    public static class CustomTooltip {
        private static String tooltipText = "";
        private static int targetX, targetY;
        private static boolean visible = false;

        public static void show(String text, int mouseX, int mouseY, int screenWidth, int screenHeight, TextRenderer tr, boolean hover) {
            tooltipText = text;
            if(text == null || text.isEmpty() || !hover) {
                hide();
                return;
            }
            visible = true;
            int padding = 3;
            int textWidth = tr.getWidth(text);
            int textHeight = tr.fontHeight;
            int boxWidth = textWidth + padding * 2;
            int boxHeight = textHeight + padding * 2;
            targetX = mouseX + 10;
            targetY = mouseY + 10;
            if(targetX + boxWidth > screenWidth) {
                targetX = screenWidth - boxWidth;
            }
            if(targetY + boxHeight > screenHeight) {
                targetY = screenHeight - boxHeight;
            }
        }

        public static void hide() {
            visible = false;
        }

        public static void renderTooltip(DrawContext context, TextRenderer tr, int screenWidth, int screenHeight) {
            if(!visible) return;
            float scaleFactor = 1.0f;
            int padding = 3;
            int textWidth = (int) (tr.getWidth(tooltipText) / scaleFactor);
            int textHeight = (int) (tr.fontHeight / scaleFactor);
            int boxWidth = textWidth + padding * 2;
            int boxHeight = textHeight + padding * 2;
            int drawX = targetX;
            int drawY = targetY;
            context.getMatrices().push();
            context.getMatrices().scale(scaleFactor, scaleFactor, 1.0f);
            context.getMatrices().translate(0, 0, 500);
            int scaledX = (int)(drawX / scaleFactor);
            int scaledY = (int)(drawY / scaleFactor);
            context.fill(scaledX, scaledY, scaledX + boxWidth, scaledY + boxHeight, elementBackgroundColor);
            drawInnerBorder(context, scaledX, scaledY, boxWidth, boxHeight, elementBorderColor);
            drawOuterBorder(context, scaledX, scaledY, boxWidth, boxHeight, globalOuterBorder);
            context.drawText(tr, Text.literal(tooltipText), scaledX + padding, scaledY + padding, globalTextColor, Config.shadow);
            context.getMatrices().pop();
        }
    }

    public static class ContextMenu {
        private static int MenuHoverColor = 0xFFd6f264;

        private static class MenuItem {
            String label;
            Runnable action;
            String tooltipText;
            MenuItem(String label, Runnable action, String tooltipText) {
                this.label = label;
                this.action = action;
                this.tooltipText = tooltipText;
            }
        }

        private static final List<MenuItem> items = new ArrayList<>();
        private static boolean open;
        private static int menuX;
        private static int menuY;
        private static int itemWidth;
        private static final int itemHeight = 18;
        private static final int gap = 1;

        public static void show(int x, int y, int width, int screenWidth, int screenHeight) {
            open = true;
            menuX = x;
            menuY = y;
            itemWidth = width;
            int margin = 60;
            if (menuX + itemWidth > screenWidth) {
                menuX = screenWidth - itemWidth;
            }
            int totalMenuHeight = items.size() * (itemHeight + gap) - gap;
            if (menuY + totalMenuHeight > screenHeight - margin) {
                menuY = screenHeight - totalMenuHeight - margin;
            }
        }

        public static void hide() {
            open = false;
            items.clear();
        }

        public static void addItem(String label, Runnable action, int HoverColor, String tooltipText) {
            items.add(new MenuItem(label, action, tooltipText));
            MenuHoverColor = HoverColor;
        }

        public static boolean isOpen() {
            return open;
        }

        public static void renderMenu(DrawContext context, MinecraftClient mc, int mouseX, int mouseY) {
            if (!open) return;
            int currentY = menuY;
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 499);
            for (MenuItem item : items) {
                boolean hovered = mouseX >= menuX && mouseX <= menuX + itemWidth && mouseY >= currentY && mouseY < currentY + itemHeight;
                drawCustomButton(context, menuX, currentY, item.label, mc, hovered, false, false, globalTextColor, MenuHoverColor, mouseX, mouseY, item.tooltipText);
                currentY += itemHeight + gap;
            }
            context.getMatrices().pop();
        }

        public static boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!open) return false;
            int currentY = menuY;
            for (MenuItem item : items) {
                boolean hovered = mouseX >= menuX && mouseX <= menuX + itemWidth && mouseY >= currentY && mouseY < currentY + itemHeight;
                if (hovered && button == GLFW.GLFW_MOUSE_BUTTON_1) {
                    item.action.run();
                    hide();
                    return true;
                }
                currentY += itemHeight + gap;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
                hide();
            }
            return false;
        }
    }

    public static class ScrollBar {
        private static boolean dragging = false;
        private static int dragStartY = 0;
        private static float initialOffset = 0;
        private static boolean lineHovered = false;
        private static float pendingOffset = 0;

        public static void render(DrawContext context, Screen parent, int mouseX, int mouseY, int totalHeight, float scrollOffset) {
            int explorerY = 60;
            int explorerHeight = parent.height - 65;
            int screenWidth = parent.width;
            if (totalHeight <= explorerHeight) {
                return;
            }
            int scrollbarX = screenWidth - 3;
            int scrollbarWidth = 2;
            int lineWidth = 4;
            int lineHeight = Math.max(10, (int)((float)explorerHeight * explorerHeight / totalHeight));
            float targetOffset = dragging ? pendingOffset : scrollOffset;
            animatedOffset += (targetOffset - animatedOffset) * scrollInterpolation;
            float effectiveOffset = animatedOffset;
            float scrollRatio = effectiveOffset / (float)(totalHeight - explorerHeight);
            int lineY = explorerY + (int)((explorerHeight - lineHeight) * scrollRatio);
            int lineX = (scrollbarX - (lineWidth - scrollbarWidth) / 2);
            context.fill(scrollbarX, explorerY, scrollbarX + scrollbarWidth, lineY + lineHeight / 2, Config.getElementBackgroundColor(3000, true, dragging, false, false, false));
            context.fill(scrollbarX, lineY + lineHeight / 2, scrollbarX + scrollbarWidth, explorerY + explorerHeight, Config.getElementBackgroundColor(3000, dragging, false, false, false, false));
            drawOuterBorder(context, scrollbarX, explorerY, scrollbarWidth, explorerHeight, globalOuterBorder);
            lineHovered = mouseX >= lineX && mouseX <= lineX + lineWidth && mouseY >= lineY && mouseY <= lineY + lineHeight;
            int lineColor = Config.getElementBackgroundColor(3000, lineHovered, dragging, false, false, false);
            context.fill(lineX, lineY, lineX + lineWidth, lineY + lineHeight, lineColor);
            drawInnerBorder(context, lineX, lineY, lineWidth, lineHeight, Config.getElementBorderColor(3000, lineHovered, dragging, false, false, false));
        }

        public static boolean handleMousePressed(Screen parent, int mouseX, int mouseY, int totalHeight, float scrollOffset) {
            playClick();
            int explorerY = 60;
            int explorerHeight = parent.height - 65;
            int screenWidth = parent.width;
            if (totalHeight <= explorerHeight) {
                return false;
            }
            int scrollbarX = screenWidth - 3;
            int scrollbarWidth = 2;
            int lineWidth = 4;
            int lineHeight = Math.max(10, (int)((float)explorerHeight * explorerHeight / totalHeight));
            int lineX = (scrollbarX - (lineWidth - scrollbarWidth) / 2);
            if (lineHovered) {
                dragging = true;
                dragStartY = mouseY;
                initialOffset = scrollOffset;
                return true;
            }
            if (mouseX >= lineX && mouseX <= lineX + lineWidth && mouseY >= explorerY && mouseY <= explorerY + explorerHeight) {
                float newLineY = mouseY - lineHeight / 2.0f;
                float newScrollRatio = (newLineY - explorerY) / (explorerHeight - lineHeight);
                newScrollRatio = Math.max(0, Math.min(newScrollRatio, 1));
                int maxOffset = Math.max(0, totalHeight - explorerHeight);
                pendingOffset = newScrollRatio * maxOffset;
                dragging = true;
                dragStartY = mouseY;
                initialOffset = pendingOffset;
                return true;
            }
            return false;
        }

        public static boolean handleMouseDragged(Screen parent, int mouseY, int totalHeight) {
            int explorerHeight = parent.height - 65;
            if (!dragging) return false;
            int lineHeight = Math.max(10, (int)((float)explorerHeight * explorerHeight / totalHeight));
            float deltaY = mouseY - dragStartY;
            float scrollableHeight = explorerHeight - lineHeight;
            float scrollRatio = scrollableHeight > 0 ? deltaY / scrollableHeight : 0;
            int maxOffset = Math.max(0, totalHeight - explorerHeight);
            pendingOffset = initialOffset + scrollRatio * maxOffset;
            pendingOffset = Math.max(0, Math.min(pendingOffset, maxOffset));
            return true;
        }

        public static boolean handleMouseReleased() {
            if (dragging) {
                dragging = false;
                return true;
            }
            return false;
        }

        public static float getPendingOffset() {
            return pendingOffset;
        }

        public static boolean isDragging() {
            return dragging;
        }

        public static void setPendingOffset(float value) {
            pendingOffset = value;
        }
    }

    public static void drawTabs(DrawContext context, TextRenderer textRenderer, List<?> tabs, int currentTabIndex, int mouseX, int mouseY, boolean hasPlus, boolean isUnsaved) {
        int tabBarX = 5;
        int tabBarY = 35;
        int tabBarHeight = 18;
        boolean shadow = Config.shadow;
        int tabPadding = 5;
        int tabGap = 5;
        int plusTabWidth = 18;
        String plusSign = "+";
        int x = tabBarX;
        if (tabWidths == null) {
            tabWidths = new HashMap<>();
        }
        if (previousTabCount != tabs.size()) {
            tabWidths.clear();
            previousTabCount = tabs.size();
        }
        for (int i = 0; i < tabs.size(); i++) {
            Object tab = tabs.get(i);
            String name;
            if (tab instanceof FileExplorerScreen.Tab) {
                name = ((FileExplorerScreen.Tab) tab).getAnimatedText();
            } else if (tab instanceof FileEditorScreen.Tab t) {
                name = t.unsaved ? t.name + "*" : t.name;
            } else if (tab instanceof PluginModManagerScreen.Tab) {
                name = ((PluginModManagerScreen.Tab) tab).name;
            } else if (tab instanceof MultiTerminalScreen.TabInfo) {
                name = ((MultiTerminalScreen.TabInfo) tab).name;
            } else if (tab instanceof MultiTerminalScreen.Theme) {
                name = ((MultiTerminalScreen.Theme) tab).name;
            } else {
                try {
                    name = tab.toString();
                } catch (Exception e) {
                    name = "Tab";
                }
            }

            int targetWidth = textRenderer.getWidth(name) + 2 * tabPadding;
            String tabId = "tab_" + i + "_" + tab.hashCode();
            float currentWidth = tabWidths.getOrDefault(tabId, (float)targetWidth);
            float animatedWidth = currentWidth + (targetWidth - currentWidth) * 0.15f;
            tabWidths.put(tabId, animatedWidth);
            int tabWidth = (int)animatedWidth;
            boolean isActive = (i == currentTabIndex);
            int x2 = x + tabWidth;
            boolean isHovered = mouseX >= x && mouseX <= x2 && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight;
            int bgColor = Config.getElementBackgroundColor(1000 + i, isHovered, isActive, isUnsaved, false, false);
            context.fill(x, tabBarY, x2, tabBarY + tabBarHeight, bgColor);
            drawInnerBorder(context, x, tabBarY, tabWidth, tabBarHeight, Config.getElementBorderColor(1000 + i, isHovered, isActive, isUnsaved, false, false));
            drawOuterBorder(context, x, tabBarY, tabWidth, tabBarHeight, globalOuterBorder);

            context.enableScissor(x + 1, tabBarY, x2 - 1, tabBarY + tabBarHeight);
            context.drawText(textRenderer, Text.literal(name), x + tabPadding, tabBarY + 5, getTextColor(isHovered, false), shadow);
            context.disableScissor();

            x += tabWidth + tabGap;
        }

        if (hasPlus) {
            boolean isPlusTabHovered = mouseX >= x && mouseX <= x + plusTabWidth && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight;
            int bgColor = Config.getElementBackgroundColor(1000 + tabs.size(), isPlusTabHovered, false, isUnsaved, false, false);
            context.fill(x, tabBarY, x + plusTabWidth, tabBarY + tabBarHeight, bgColor);
            drawInnerBorder(context, x, tabBarY, plusTabWidth, tabBarHeight, Config.getElementBorderColor(1000 + tabs.size(), isPlusTabHovered, false, false, false, false));
            drawOuterBorder(context, x, tabBarY, plusTabWidth, tabBarHeight, globalOuterBorder);
            context.drawText(textRenderer, Text.literal(plusSign), x + plusTabWidth / 2 - textRenderer.getWidth(plusSign) / 2, tabBarY + 5, getTextColor(isPlusTabHovered, false), shadow);
        }
    }

    public static void drawSearchBar(DrawContext context, TextRenderer textRenderer, StringBuilder fieldText, boolean fieldFocused, int cursorPosition, int selectionStart, int selectionEnd, float pathScrollOffset, float pathTargetScrollOffset, boolean showCursor, boolean isSpecialMode, String caller, int mouseX, int mouseY, String tooltipText) {
        if (!fieldText.toString().equals(previousFieldText)) {
            if (!fieldFocused) {
                searchTextAnimator.updateText(fieldText.toString());
                isAnimating = true;
            } else {
                searchTextAnimator = new TextAnimator(fieldText.toString(), 0, 10);
                isAnimating = false;
            }
            previousFieldText = fieldText.toString();
        }
        String displayText = fieldFocused ? fieldText.toString() : searchTextAnimator.getCurrentText();
        int searchBarWidth = 200;
        int searchBarHeight = 18;
        int searchBarX = (context.getScaledWindowWidth() - searchBarWidth) / 2;
        int searchBarY = 5;
        boolean hovered = mouseX >= searchBarX && mouseX <= searchBarX + searchBarWidth && mouseY >= searchBarY && mouseY <= searchBarY + searchBarHeight;
        boolean shadow = Config.shadow;
        String hint = caller.equals("FileExplorerScreen") ? "Search..." : "Ask Remotely AI...";
        int id = 2000;
        context.fill(searchBarX, searchBarY, searchBarX + searchBarWidth, searchBarY + searchBarHeight, Config.getElementBackgroundColor(id, hovered, fieldFocused, false, false, false));
        drawInnerBorder(context, searchBarX, searchBarY, searchBarWidth, searchBarHeight, Config.getElementBorderColor(id, hovered, fieldFocused, false, false, false));
        drawOuterBorder(context, searchBarX, searchBarY, searchBarWidth, searchBarHeight, Config.globalOuterBorder);
        if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
            int selStart = Math.max(0, Math.min(selectionStart, selectionEnd));
            int selEnd = Math.min(displayText.length(), Math.max(selectionStart, selectionEnd));
            if (selEnd > displayText.length()) selEnd = displayText.length();
            String beforeSel = displayText.substring(0, selStart);
            String selectedText = displayText.substring(selStart, selEnd);
            int selX = searchBarX + 5 + textRenderer.getWidth(beforeSel);
            int selW = textRenderer.getWidth(selectedText);
            context.fill(selX, searchBarY + 4, selX + selW, searchBarY + 4 + textRenderer.fontHeight, 0x80FFFFFF);
        }
        if (fieldFocused && isSpecialMode && displayText.isEmpty()) {
            context.drawText(textRenderer, Text.literal(hint), searchBarX + 5, searchBarY + 5, Config.getTextColor(hovered, true), false);
        }
        int displayWidth = searchBarWidth - 10;
        int textWidth = textRenderer.getWidth(displayText);
        String beforeCursor = cursorPosition <= displayText.length() ? displayText.substring(0, cursorPosition) : displayText;
        int cursorX = searchBarX + 5 + textRenderer.getWidth(beforeCursor);
        float cursorMargin = 20f;
        float targetScrollOffset = pathTargetScrollOffset;
        if (cursorX - currentScrollOffset > searchBarX + displayWidth - cursorMargin) {
            targetScrollOffset = cursorX - (searchBarX + displayWidth - cursorMargin);
        } else if (cursorX - currentScrollOffset < searchBarX + cursorMargin) {
            targetScrollOffset = Math.max(0, cursorX - (searchBarX + cursorMargin));
        }
        if (textWidth > displayWidth) {
            targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, textWidth - displayWidth));
        } else {
            targetScrollOffset = 0;
        }
        currentScrollOffset += (targetScrollOffset - currentScrollOffset) * scrollInterpolation;
        if (!fieldFocused && isAnimating && searchTextAnimator.hasCompleted()) {
            isAnimating = false;
        }
        context.enableScissor(searchBarX, searchBarY, searchBarX + searchBarWidth, searchBarY + searchBarHeight);
        context.drawText(textRenderer, Text.literal(displayText), searchBarX + 5 - (int) currentScrollOffset, searchBarY + 5, Config.getTextColor(hovered, fieldFocused), shadow);
        if (fieldFocused && showCursor) {
            int cursorPosX = searchBarX + 5 + textRenderer.getWidth(beforeCursor) - (int) currentScrollOffset;
            context.fill(cursorPosX, searchBarY + 5, cursorPosX + 1, searchBarY + 5 + textRenderer.fontHeight, 0xFFFFFFFF);
        }
        context.disableScissor();
        CustomTooltip.show(tooltipText, mouseX, mouseY, context.getScaledWindowWidth(), context.getScaledWindowHeight(), textRenderer, hovered);
        CustomTooltip.renderTooltip(context, textRenderer, context.getScaledWindowWidth(), context.getScaledWindowHeight());
    }

    public static void drawExplorerElements(DrawContext context, boolean hovered, boolean isSelected, boolean isFavorite, FileExplorerScreen.EntryData entry, int explorerX, int entryY, int explorerWidth, int entryHeight) {
        int bg = Config.getElementBackgroundColor(entry.hashCode(), hovered, isSelected, isFavorite, false, false);
        int borderWithOpacity = Config.getElementBorderColor(entry.hashCode(), hovered, isSelected, isFavorite, false, false);
        drawOuterBorder(context, explorerX, entryY, explorerWidth, entryHeight, globalOuterBorder);
        context.fill(explorerX, entryY, explorerX + explorerWidth, entryY + entryHeight, bg);
        drawInnerBorder(context, explorerX, entryY, explorerWidth, entryHeight, borderWithOpacity);
        context.fill(explorerX, entryY + entryHeight - 1, explorerX + explorerWidth, entryY + entryHeight, borderWithOpacity);
        BufferedImage icon = entry.isDirectory ? FileExplorerScreen.folderIcon : getIconForFile(entry.path);
        drawPixelArt(context, icon, explorerX + 10, entryY + 2, 16, 16);
        if (isFavorite) {
            drawPixelArt(context, FileExplorerScreen.pinIcon, entry.isDirectory ? explorerX + 5 : explorerX + 7, entryY + 2, 16, 16);
        }
    }

    public static void renderSnippetBox(DrawContext context, int snippetX, int snippetY, int snippetMaxWidth, int snippetHeight, RemotelyClient.CommandSnippet snippet, boolean hovered, boolean selected, MinecraftClient minecraftClient) {
        int bgColor = Config.getElementBackgroundColor(snippet.hashCode(), hovered, selected, false, false, false);
        context.fill(snippetX, snippetY, snippetX + snippetMaxWidth, snippetY + snippetHeight, bgColor);
        drawInnerBorder(context, snippetX, snippetY, snippetMaxWidth, snippetHeight, Config.getElementBorderColor(snippet.hashCode(), hovered, selected, false, false, false));
        drawOuterBorder(context, snippetX, snippetY, snippetMaxWidth, snippetHeight, globalOuterBorder);
        String displayName = trimTextToWidthWithEllipsis(snippet.name, snippetMaxWidth - 10);
        context.drawText(minecraftClient.textRenderer, Text.literal(displayName), snippetX + 5, snippetY + 5, globalTextColor, shadow);
        int lineSeparatorY = snippetY + 5 + minecraftClient.textRenderer.fontHeight + 2;
        context.fill(snippetX + 5, lineSeparatorY, snippetX + snippetMaxWidth - 5, lineSeparatorY + 1, elementHoverBorderColor);
        int contentY = lineSeparatorY + 4;
        context.enableScissor(snippetX + 5, contentY, snippetX + snippetMaxWidth - 5, snippetY + snippetHeight - 4);
        String[] allLines = snippet.commands.split("\n");
        int lineY = contentY;
        for (String line : allLines) {
            String trimmed = trimTextToWidthWithEllipsis(line, snippetMaxWidth - 10);
            context.drawText(minecraftClient.textRenderer, Text.literal(trimmed), snippetX + 5, lineY, globalDarkTextColor, shadow);
            lineY += minecraftClient.textRenderer.fontHeight + 2;
        }
        context.disableScissor();
    }

    public static void drawCustomButton(DrawContext context, int x, int y, String text, MinecraftClient mc, boolean hovered, boolean dynamic, boolean centered, int txColor, int hoverColor, int mouseX, int mouseY, String tooltipText) {
        if (dynamic) {
            buttonW = mc.textRenderer.getWidth(text) + 10;
        } else {
            buttonW = 60;
        }
        int id = (text.hashCode() * 31 + x) * 31 + y;
        context.fill(x, y, x + buttonW, y + buttonH, Config.getElementBackgroundColor(id, hovered, false, false, false, false));
        drawInnerBorder(context, x, y, buttonW, buttonH, Config.getElementBorderColor(id, hovered, false, false, false, false));
        drawOuterBorder(context, x, y, buttonW, buttonH, globalOuterBorder);
        int tw = mc.textRenderer.getWidth(text);
        int tx = centered ? x + (buttonW - tw) / 2 : x + 5;
        int ty = y + 5;
        context.drawText(mc.textRenderer, Text.literal(text), tx, ty, hovered ? hoverColor : txColor, Config.shadow);
        CustomTooltip.show(tooltipText, mouseX, mouseY, context.getScaledWindowWidth(), context.getScaledWindowHeight(), mc.textRenderer, hovered);
        CustomTooltip.renderTooltip(context, mc.textRenderer, context.getScaledWindowWidth(), context.getScaledWindowHeight());
    }

    public static void drawSquareButton(DrawContext context, int x, int y, MinecraftClient mc, boolean hovered, int mouseX, int mouseY, String tooltipText) {
        int w = 18;
        int h = 18;
        int id = ("square" + x + y).hashCode();
        context.fill(x, y, x + w, y + h, Config.getElementBackgroundColor(id, hovered, false, false, false, false));
        drawInnerBorder(context, x, y, w, h, Config.getElementBorderColor(id, hovered, false, false, false, false));
        drawOuterBorder(context, x, y, w, h, globalOuterBorder);
        CustomTooltip.show(tooltipText, mouseX, mouseY, context.getScaledWindowWidth(), context.getScaledWindowHeight(), mc.textRenderer, hovered);
        CustomTooltip.renderTooltip(context, mc.textRenderer, context.getScaledWindowWidth(), context.getScaledWindowHeight());
    }

    public static void drawLoading(DrawContext context, int height, int width) {
        try {
            loadingAnim = loadSpriteSheet("/assets/remotely/icons/loadinganim.png");
        } catch (Exception e) {
            devPrint("Failed to load loading animation");
        }
        int frameWidth = 16;
        int frameHeight = 16;
        int rows = loadingAnim.getHeight() / frameHeight;
        for (int i = 0; i < rows; i++) {
            BufferedImage frame = loadingAnim.getSubimage(0, i * frameHeight, frameWidth, frameHeight);
            loadingFrames.add(frame);
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameTime >= 40) {
            currentLoadingFrame = (currentLoadingFrame + 1) % loadingFrames.size();
            lastFrameTime = currentTime;
        }
        BufferedImage currentFrame = loadingFrames.get(currentLoadingFrame);
        int scale = 8;
        int imgWidth = currentFrame.getWidth() * scale;
        int imgHeight = currentFrame.getHeight() * scale;
        int centerX = (width - imgWidth) / 2;
        int centerY = (height - imgHeight) / 2;
        drawPixelArt(context, currentFrame, centerX, centerY, imgWidth, imgHeight);
    }

    public static void drawScreenHeader(DrawContext context, int width, int height, int mouseX, int mouseY, Screen parent, MinecraftClient minecraftClient, IconWithTooltip icon1, IconWithTooltip icon2, IconWithTooltip icon3, IconWithTooltip icon4, IconWithTooltip icon5, IconWithTooltip icon6, IconWithTooltip icon7, IconWithTooltip icon8, IconWithTooltip specialIcon) {
        if (wallpaper && windowsBackground != null) {
            drawBufferedImage(context, windowsBackground, 0, 0, parent.width, parent.height);
        } else if (!background) {
            context.fill(0, 0, width, height, backgroundColor);
        }
        context.fill(0, 0, width, 30, innerBackgroundColor);
        drawInnerBorder(context, 0, 0, parent.width, 30, innerBorderColor);
        drawOuterBorder(context, 0, 0, parent.width, 30, globalOuterBorder);
        if (!(parent instanceof MultiTerminalScreen)) {
            if (!(parent instanceof FileExplorerScreen && !(((FileExplorerScreen) parent).isCanScroll()))) {
                if (!(parent instanceof PluginModManagerScreen)) {
                    if (!(parent instanceof SettingsScreen)) {
                        context.fill(5, 60, width - 5, height - 5, innerBackgroundColor);
                        drawInnerBorder(context, 5, 60, width - 10, height - 65, innerBorderColor);
                        drawOuterBorder(context, 5, 60, width - 10, height - 65, globalOuterBorder);
                    }
                }
            }
        }
        if (icon1 != null) {
            boolean isIcon1Hovered = mouseX >= width - 23 && mouseX <= width - 6 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 23, 5, minecraftClient, isIcon1Hovered, mouseX, mouseY, icon1.getTooltip());
            drawPixelArt(context, icon1.getImage(), width - 22, 6, 16, 16);
        }
        if (icon2 != null) {
            boolean isIcon2Hovered = mouseX >= width - 46 && mouseX <= width - 29 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 46, 5, minecraftClient, isIcon2Hovered, mouseX, mouseY, icon2.getTooltip());
            drawPixelArt(context, icon2.getImage(), width - 45, 6, 16, 16);
        }
        if (icon3 != null) {
            boolean isIcon3Hovered = mouseX >= width - 69 && mouseX <= width - 52 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 69, 5, minecraftClient, isIcon3Hovered, mouseX, mouseY, icon3.getTooltip());
            drawPixelArt(context, icon3.getImage(), width - 68, 6, 16, 16);
        }
        if (icon4 != null) {
            boolean isIcon4Hovered = mouseX >= width - 92 && mouseX <= width - 75 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 92, 5, minecraftClient, isIcon4Hovered, mouseX, mouseY, icon4.getTooltip());
            drawPixelArt(context, icon4.getImage(), width - 91, 6, 16, 16);
        }
        if (icon5 != null) {
            boolean isIcon5Hovered = mouseX >= 5 && mouseX <= 22 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 5, 5, minecraftClient, isIcon5Hovered, mouseX, mouseY, icon5.getTooltip());
            drawPixelArt(context, icon5.getImage(), 6, 6, 16, 16);
        }
        if (icon6 != null) {
            boolean isIcon6Hovered = mouseX >= 28 && mouseX <= 45 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 28, 5, minecraftClient, isIcon6Hovered, mouseX, mouseY, icon6.getTooltip());
            drawPixelArt(context, icon6.getImage(), 29, 6, 16, 16);
        }
        if (icon7 != null) {
            boolean isIcon7Hovered = mouseX >= 51 && mouseX <= 68 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 51, 5, minecraftClient, isIcon7Hovered, mouseX, mouseY, icon7.getTooltip());
            drawPixelArt(context, icon7.getImage(), 52, 6, 16, 16);
        }
        if (icon8 != null) {
            boolean isIcon8Hovered = mouseX >= 74 && mouseX <= 91 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 74, 5, minecraftClient, isIcon8Hovered, mouseX, mouseY, icon8.getTooltip());
            drawPixelArt(context, icon8.getImage(), 75, 6, 16, 16);
        }
        if (specialIcon != null) {
            int searchBarWidth = 200;
            int specialIconX = (width - searchBarWidth) / 2 - 23;
            boolean isSpecialIconHovered = mouseX >= specialIconX && mouseX <= specialIconX + 17 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, specialIconX, 5, minecraftClient, isSpecialIconHovered, mouseX, mouseY, specialIcon.getTooltip());
            drawPixelArt(context, specialIcon.getImage(), specialIconX + 1, 6, 16, 16);
        }
    }

    public static void drawToggle(DrawContext context, MinecraftClient mc, int x, int y, String label, boolean value, boolean hovered) {
        int trackWidth = 40;
        int trackHeight = 20;
        int id = ("toggle" + label ).hashCode() + 143;
        int trackColor = getElementBackgroundColor(id, hovered, value, false, false, false);
        context.fill(x, y, x + trackWidth, y + trackHeight, trackColor);
        context.fill(x, y + (int)(trackHeight * 0.75), x + trackWidth, y + trackHeight, 0x20000000);
        drawInnerBorder(context, x, y, trackWidth, trackHeight, getElementBorderColor(id, hovered, value, false, false, false));
        drawOuterBorder(context, x, y, trackWidth, trackHeight, globalOuterBorder);
        int knobDiameter = trackHeight - 4;
        int knobX = value ? x + trackWidth - knobDiameter - 2 : x + 2;
        int knobY = y + 2;
        context.fill(knobX, knobY, knobX + knobDiameter, knobY + knobDiameter, getElementBackgroundColor(id, hovered, false, false, false, false));
        drawInnerBorder(context, knobX, knobY, knobDiameter, knobDiameter, getElementBorderColor(id, hovered, false, false, false, false));
    }

    public static void drawSlider(DrawContext context, MinecraftClient mc, int x, int y, String label, int currentValue, int minValue, int maxValue, boolean hovered, int mouseX, int mouseY, String toolTipText) {
        int sliderWidth = 180;
        int sliderHeight = 18;
        int id = ("slider" + label).hashCode();
        int bg = Config.getElementBackgroundColor(id, hovered, false, false, false, false);
        context.fill(x, y, x + sliderWidth, y + sliderHeight, bg);

        float ratio = (float)(currentValue - minValue) / (float)(maxValue - minValue);
        int fillWidth = (int)(ratio * (sliderWidth - 2));
        context.fill(x + 1, y + 1, x + 1 + fillWidth, y + sliderHeight - 1, getElementBorderColor(id, hovered, true, false, false, false));
        drawInnerBorder(context, x, y, sliderWidth, sliderHeight, getElementBorderColor(id, hovered, false, false, false, false));
        drawOuterBorder(context, x, y, sliderWidth, sliderHeight, globalOuterBorder);
        context.fill(x + 1, y + sliderHeight - (int)(sliderHeight * 0.25), x + 1 + fillWidth, y + sliderHeight - 1, 0x20000000);
        String text = String.valueOf(currentValue);
        int tw = mc.textRenderer.getWidth(text);
        int tx = x + (sliderWidth - tw) / 2;
        int ty = y + 5;
        context.drawText(mc.textRenderer, Text.literal(text), tx, ty, globalTextColor, Config.shadow);
        CustomTooltip.show(toolTipText, mouseX, mouseY, sliderWidth, sliderHeight, mc.textRenderer, hovered);
        CustomTooltip.renderTooltip(context, mc.textRenderer, context.getScaledWindowWidth(), context.getScaledWindowHeight());
    }

    public static void drawScrollSelector(DrawContext context, MinecraftClient mc, int x, int y, List<String> options, int selectedIndex, boolean hovered) {
        selectedIndex = (selectedIndex + options.size()) % options.size();
        if (previousScrollSelectorIndex == -1) {
            scrollSelectorIndexFloat = selectedIndex;
            previousScrollSelectorIndex = selectedIndex;
        }
        if (previousScrollSelectorIndex != selectedIndex) {
            previousScrollSelectorIndex = selectedIndex;
        }
        scrollSelectorIndexFloat += (selectedIndex - scrollSelectorIndexFloat) * 0.15f;
        int w = 180;
        int h = 18;
        int id = ("scrollSelector" + options).hashCode();
        int bg = Config.getElementBackgroundColor(id, hovered, false, false, false, false);
        context.fill(x, y, x + w, y + h, bg);
        drawInnerBorder(context, x, y, w, h, Config.getElementBorderColor(id, hovered, false, false, false, false));
        drawOuterBorder(context, x, y, w, h, globalOuterBorder);
        int contentX = x + 1;
        int contentWidth = x + w - contentX - 1;
        context.enableScissor(contentX, y, contentX + contentWidth, y + h);
        float centerSlot = contentX + contentWidth / 2f;
        float slotSpacing = 60f;
        for (int i = 0; i < options.size(); i++) {
            float ringIndex = i - scrollSelectorIndexFloat;
            if (ringIndex < -options.size() / 2) ringIndex += options.size();
            if (ringIndex > options.size() / 2) ringIndex -= options.size();
            float offsetX = centerSlot + ringIndex * slotSpacing;
            String s = options.get(i);
            int textW = mc.textRenderer.getWidth(s);
            float textX = offsetX - textW / 2f -2;
            float textY = (y + (h - mc.textRenderer.fontHeight) / 2f) +1;
            context.drawText(mc.textRenderer, Text.literal(s), (int) textX, (int) textY, globalTextColor, Config.shadow);
        }
        context.disableScissor();
        int shadowSize = 32;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 499);
        for (int i = 0; i < shadowSize; i++) {
            int alpha = (int) (0x60 * ((float)(shadowSize - i) / shadowSize));
            int color = alpha << 24;
            context.fill(x + i + 1, y + 2, x + i + 2, y + h - 1, color);
            context.fill(x + w - i - 2, y + 1, x + w - i - 1, y + h - 2, color);
        }
        context.getMatrices().pop();
    }

    public static void drawTabSwitch(DrawContext context, MinecraftClient mc, int x, int y, String label, List<String> options, int currentIndex, int mouseX, int mouseY) {
        int barWidth = 180;
        int barHeight = 18;
        context.fill(x, y, x + barWidth, y + barHeight, elementBackgroundColor);
        drawInnerBorder(context, x, y, barWidth, barHeight, elementBorderColor);
        drawOuterBorder(context, x, y, barWidth, barHeight, globalOuterBorder);
        int segmentCount = options.size();
        int segmentWidth = barWidth / segmentCount;
        boolean hovered;
        for (int i = 0; i < segmentCount; i++) {
            int segX = x + i * segmentWidth;
            int segY = y;
            int segW = (i == segmentCount - 1) ? (x + barWidth - segX) : segmentWidth;
            hovered = mouseX >= segX && mouseX < segX + segW && mouseY >= segY && mouseY < segY + barHeight;
            boolean selected = i == currentIndex;
            int id = ("tabSwitch" + label + options.get(i)).hashCode();
            int color = getElementBackgroundColor(id, hovered, selected, false, false, false);
            context.fill(segX, segY, segX + segW, segY + barHeight, color);
            drawInnerBorder(context, segX, segY, segW, barHeight, getElementBorderColor(id, hovered, selected, false, false, false));
            int textWidth = mc.textRenderer.getWidth(options.get(i));
            int textX = segX + (segW - textWidth) / 2;
            int textY = (segY + (barHeight - mc.textRenderer.fontHeight) / 2) + 1;
            context.drawText(mc.textRenderer, Text.literal(options.get(i)), textX, textY, globalTextColor, shadow);
        }
    }

    public static void drawTextInput(DrawContext context, MinecraftClient mc, int x, int y, String label, String textValue, boolean focused, int cursorPos, int selectionStart, int selectionEnd, boolean hovered, float currentScrollOffset, float targetScrollOffset) {
        int inputWidth = 180;
        int inputHeight = 18;
        context.drawText(mc.textRenderer, Text.literal(label), x, y + 3, globalTextColor, Config.shadow);
        int id = ("textInput" + label + textValue + cursorPos + selectionStart + selectionEnd).hashCode();
        int bg = Config.getElementBackgroundColor(id, hovered, focused, false, false, false);
        context.fill(x, y, x + inputWidth, y + inputHeight, bg);
        drawInnerBorder(context, x, y, inputWidth, inputHeight, Config.getElementBorderColor(id, hovered, focused, false, false, false));
        drawOuterBorder(context, x, y, inputWidth, inputHeight, globalOuterBorder);
        int displayWidth = inputWidth - 10;
        int textWidth = mc.textRenderer.getWidth(textValue);
        String beforeCursor = cursorPos <= textValue.length() ? textValue.substring(0, cursorPos) : textValue;
        int cursorX = x + 5 + mc.textRenderer.getWidth(beforeCursor);
        float scrollOffset = 0;
        if (textWidth > displayWidth) {
            if (cursorX > x + displayWidth) {
                scrollOffset = cursorX - (x + displayWidth);
            }
        }
        context.enableScissor(x, y, x + inputWidth, y + inputHeight);
        if (textValue.isEmpty()) {
            context.drawText(mc.textRenderer, Text.literal("Type..."), x + 5, y + 5, Config.globalDarkTextColor, Config.shadow);
        } else {
            int selStart = Math.min(selectionStart, selectionEnd);
            int selEnd = Math.max(selectionStart, selectionEnd);
            if (focused && selStart != selEnd) {
                String textBeforeSel = selStart <= textValue.length() ? textValue.substring(0, selStart) : textValue;
                int selStartX = x + 5 + mc.textRenderer.getWidth(textBeforeSel) - (int)scrollOffset;
                String selectedText = selEnd <= textValue.length() ? textValue.substring(selStart, selEnd) : "";
                int selWidth = mc.textRenderer.getWidth(selectedText);
                context.fill(selStartX, y + 4, selStartX + selWidth, y + 4 + mc.textRenderer.fontHeight, globalSelectionColor);
            }
            context.drawText(mc.textRenderer, Text.literal(textValue), x + 5 - (int)scrollOffset, y + 5, globalTextColor, Config.shadow);
        }
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorPosX = x + 5 + mc.textRenderer.getWidth(beforeCursor) - (int)scrollOffset;
            context.fill(cursorPosX, y + 4, cursorPosX + 1, y + 4 + mc.textRenderer.fontHeight, globalTextColor);
        }
        context.disableScissor();
    }

    public static void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int i) {
        context.fill(x, y, x + w, y + 1, i);
        context.fill(x, y + h - 1, x + w, y + h, i);
        context.fill(x, y, x + 1, y + h, i);
        context.fill(x + w - 1, y, x + w, y + h, i);
    }

    public static void drawOuterBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x - 1, y - 1, x + w + 1, y, color);
        context.fill(x - 1, y + h, x + w + 1, y + h + 3, color);
        context.fill(x - 1, y, x, y + h, color);
        context.fill(x + w, y, x + w + 1, y + h, color);
        context.fill(x, y + h, x + w, y + h + 2, elementBackgroundColor);
        context.fill(x, y + h, x + w, y + h + 2, 0x40000000);
        context.fillGradient(x, y + h + 2, x + w, y + h + 4, 0x00000000, 0x60000000);
    }

    public static String trimTextToWidthWithEllipsis(String text, int maxWidth) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer.getWidth(text) <= maxWidth) return text;
        while (mc.textRenderer.getWidth(text + "..") > maxWidth && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }
}

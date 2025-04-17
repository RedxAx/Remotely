package redxax.oxy.common;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.common.config.Config;
import redxax.oxy.common.servers.SettingsScreen;
import redxax.oxy.common.terminal.MultiTerminalScreen;
import redxax.oxy.common.explorer.FileExplorerScreen;
import redxax.oxy.common.explorer.FileEditorScreen;
import redxax.oxy.common.servers.PluginModManagerScreen;
import redxax.oxy.common.util.TextAnimator;

import java.awt.image.BufferedImage;
import java.util.*;

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
    private static float currentScrollOffset = 0f;
    private static float animatedOffset = 0;
    private static Map<String, Float> tabWidths;
    private static int previousTabCount = 0;
    private static Map<Integer, Float> scrollSelectorIndexFloatMap = new HashMap<>();
    private static Map<Integer, Integer> previousScrollSelectorIndexMap = new HashMap<>();
    public static final Map<Integer, Float> elevationOffsets = new HashMap<>();

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
                drawCustomButton(context, menuX, currentY, item.label, mc, hovered, false, false, false, true, 60, 18, globalTextColor,  MenuHoverColor, mouseX, mouseY, item.tooltipText);
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
            animatedOffset += (targetOffset - animatedOffset) * globalScrollSpeed * deltaTime;
            float effectiveOffset = animatedOffset;
            float scrollRatio = effectiveOffset / (float)(totalHeight - explorerHeight);
            int lineY = explorerY + (int)((explorerHeight - lineHeight) * scrollRatio);
            int lineX = (scrollbarX - (lineWidth - scrollbarWidth) / 2);
            context.fill(scrollbarX, explorerY, scrollbarX + scrollbarWidth, lineY + lineHeight / 2, Config.getElementBackgroundColor(3000 + "topScroll".hashCode(), true, dragging, true, false, false, false));
            context.fill(scrollbarX, lineY + lineHeight / 2, scrollbarX + scrollbarWidth, explorerY + explorerHeight, Config.getElementBackgroundColor(3000 + "bottomScroll".hashCode(), dragging, false, true, false, false, false));
            drawOuterBorder(context, scrollbarX, explorerY, scrollbarWidth, explorerHeight, globalOuterBorder);
            lineHovered = mouseX >= lineX && mouseX <= lineX + lineWidth && mouseY >= lineY && mouseY <= lineY + lineHeight;
            int lineColor = Config.getElementBackgroundColor(3000 + "scrollLine".hashCode(), lineHovered, dragging, true, false, false, false);
            context.fill(lineX, lineY, lineX + lineWidth, lineY + lineHeight, lineColor);
            drawInnerBorder(context, lineX, lineY, lineWidth, lineHeight, Config.getElementBorderColor(3000 + "scrollLineBr".hashCode(), lineHovered, dragging, true, false, false, false));
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
            switch (tab) {
                case FileExplorerScreen.Tab tab1 -> name = tab1.getAnimatedText();
                case FileEditorScreen.Tab t -> name = t.unsaved ? t.name + "*" : t.name;
                case PluginModManagerScreen.Tab tab1 -> name = tab1.name;
                case MultiTerminalScreen.TabInfo tabInfo -> name = tabInfo.name;
                case MultiTerminalScreen.Theme theme -> name = theme.name;
                case null, default -> {
                    try {
                        assert tab != null;
                        name = tab.toString();
                    } catch (Exception e) {
                        name = "Tab";
                    }
                }
            }
            int targetWidth = textRenderer.getWidth(name) + 2 * tabPadding;
            String tabId = "tab_" + i + "_" + tab.hashCode();
            float currentWidth = tabWidths.getOrDefault(tabId, (float) targetWidth);
            float animatedWidth = currentWidth + (targetWidth - currentWidth) * globalExpandSpeed * deltaTime;
            tabWidths.put(tabId, animatedWidth);
            int tabWidth = (int) animatedWidth;
            boolean isActive = (i == currentTabIndex);
            int x2 = x + tabWidth;
            boolean isHovered = mouseX >= x && mouseX <= x2 && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight;
            int elevId = tabId.hashCode();
            float targetOffset = isHovered ? -2f : 0f;
            float currentOffset = elevationOffsets.getOrDefault(elevId, 0f);
            currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
            elevationOffsets.put(elevId, currentOffset);
            context.getMatrices().push();
            context.getMatrices().translate(0, currentOffset, 0);
            int bgColor = Config.getElementBackgroundColor(1000 + i, isHovered, isActive, true, isUnsaved, false, false);
            context.fill(x, tabBarY, x2, tabBarY + tabBarHeight, bgColor);
            drawInnerBorder(context, x, tabBarY, tabWidth, tabBarHeight, Config.getElementBorderColor(1000 + i, isHovered, isActive, true, isUnsaved, false, false));
            drawOuterBorder(context, x, tabBarY, tabWidth, tabBarHeight, globalOuterBorder);
            context.enableScissor(x + 1, tabBarY, x2 - 1, tabBarY + tabBarHeight);
            context.drawText(textRenderer, Text.literal(name), x + tabPadding, tabBarY + 5, getTextColor(isHovered, false), shadow);
            context.disableScissor();
            context.getMatrices().pop();
            x += tabWidth + tabGap;
        }
        if (hasPlus) {
            boolean isPlusTabHovered = mouseX >= x && mouseX <= x + plusTabWidth && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight;
            int plusId = ("plus_tab").hashCode();
            float targetOffset = isPlusTabHovered ? -2f : 0f;
            float currentOffset = elevationOffsets.getOrDefault(plusId, 0f);
            currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
            elevationOffsets.put(plusId, currentOffset);
            context.getMatrices().push();
            context.getMatrices().translate(0, currentOffset, 0);
            int bgColor = Config.getElementBackgroundColor(1000 + tabs.size(), isPlusTabHovered, false, true, isUnsaved, false, false);
            context.fill(x, tabBarY, x + plusTabWidth, tabBarY + tabBarHeight, bgColor);
            drawInnerBorder(context, x, tabBarY, plusTabWidth, tabBarHeight, Config.getElementBorderColor(1000 + tabs.size(), isPlusTabHovered, false, true, false, false, false));
            drawOuterBorder(context, x, tabBarY, plusTabWidth, tabBarHeight, globalOuterBorder);
            context.drawText(textRenderer, Text.literal(plusSign), x + plusTabWidth / 2 - textRenderer.getWidth(plusSign) / 2, tabBarY + 5, getTextColor(isPlusTabHovered, false), shadow);
            context.getMatrices().pop();
        }
    }

    public static void drawSearchBar(DrawContext context, TextRenderer textRenderer, StringBuilder fieldText, boolean fieldFocused, int cursorPosition, int selectionStart, int selectionEnd, float pathScrollOffset, float pathTargetScrollOffset, boolean isSpecialMode, String caller, int mouseX, int mouseY, String tooltipText) {
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
        int elevId = "searchbar".hashCode();
        float targetOffset = hovered ? -2f : 0f;
        float currentOffset = elevationOffsets.getOrDefault(elevId, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(elevId, currentOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        int id = 2000;
        int bgColor = getElementBackgroundColor(id, hovered, fieldFocused, true, false, caller.equals("FileExplorerScreen") && isSpecialMode, caller.equals("FileEditorScreen") && isSpecialMode);
        context.fill(searchBarX, searchBarY, searchBarX + searchBarWidth, searchBarY + searchBarHeight, bgColor);
        drawInnerBorder(context, searchBarX, searchBarY, searchBarWidth, searchBarHeight, getElementBorderColor(id, hovered, fieldFocused, true, false, caller.equals("FileExplorerScreen") && isSpecialMode, caller.equals("FileEditorScreen") && isSpecialMode));
        drawOuterBorder(context, searchBarX, searchBarY, searchBarWidth, searchBarHeight, globalOuterBorder);
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
            context.drawText(textRenderer, Text.literal("Search..."), searchBarX + 5, searchBarY + 5, getTextColor(hovered, true), shadow);
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
        currentScrollOffset += (targetScrollOffset - currentScrollOffset) * globalScrollSpeed * deltaTime;
        if (!fieldFocused && isAnimating && searchTextAnimator.hasCompleted()) {
            isAnimating = false;
        }
        context.enableScissor(searchBarX, searchBarY, searchBarX + searchBarWidth, searchBarY + searchBarHeight);
        context.drawText(textRenderer, Text.literal(displayText), searchBarX + 5 - (int) currentScrollOffset, searchBarY + 5, Config.getTextColor(hovered, fieldFocused), Config.shadow);
        if (fieldFocused) {
            int cursorPosX = searchBarX + 5 + textRenderer.getWidth(beforeCursor) - (int) currentScrollOffset;
            context.fill(cursorPosX, searchBarY + 5, cursorPosX + 1, searchBarY + 5 + textRenderer.fontHeight, Config.globalCursorAnimatedColor);
        }
        context.disableScissor();
        CustomTooltip.show(tooltipText, mouseX, mouseY, context.getScaledWindowWidth(), context.getScaledWindowHeight(), textRenderer, hovered);
        CustomTooltip.renderTooltip(context, textRenderer, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        context.getMatrices().pop();
    }

    public static void drawExplorerElements(DrawContext context, boolean hovered, boolean isSelected, boolean isFavorite, FileExplorerScreen.EntryData entry, int explorerX, int entryY, int explorerWidth, int entryHeight, TextRenderer textRenderer, boolean isRemote, String renamePath, StringBuilder renameBuffer, int renameCursorPos) {
        int id = ("explorer" + entry.hashCode()).hashCode();
        float targetOffset = hovered ? -2f : 0f;
        float currentOffset = elevationOffsets.getOrDefault(id, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, currentOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        int bg = Config.getElementBackgroundColor(entry.hashCode(), hovered, isSelected, true, isFavorite, entry.isMatched, false);
        int borderWithOpacity = Config.getElementBorderColor(entry.hashCode(), hovered, isSelected, true, isFavorite, entry.isMatched, false);
        drawOuterBorder(context, explorerX, entryY, explorerWidth, entryHeight, globalOuterBorder);
        context.fill(explorerX, entryY, explorerX + explorerWidth, entryY + entryHeight, bg);
        drawInnerBorder(context, explorerX, entryY, explorerWidth, entryHeight, borderWithOpacity);
        context.fill(explorerX, entryY + entryHeight - 1, explorerX + explorerWidth, entryY + entryHeight, borderWithOpacity);
        BufferedImage icon = entry.isDirectory ? FileExplorerScreen.folderIcon : getIconForFile(entry.path);
        drawPixelArt(context, explorerX + 10, entryY + 2, 16, 16, icon);
        if (isFavorite) {
            drawPixelArt(context, entry.isDirectory ? explorerX + 5 : explorerX + 7, entryY + 2, 16, 16, FileExplorerScreen.pinIcon);
        }
        if (!isRemote) {
            int createdX = explorerX + explorerWidth - 100;
            int sizeX = createdX - 100;
            context.drawText(textRenderer, Text.literal(entry.displayName), explorerX + 30, entryY + 6, globalTextColor, Config.shadow);
            context.drawText(textRenderer, Text.literal(entry.created), createdX, entryY + 6, globalTextColor, Config.shadow);
            context.drawText(textRenderer, Text.literal(entry.size), sizeX, entryY + 6, globalTextColor, Config.shadow);
        } else {
            context.drawText(textRenderer, Text.literal(entry.displayName), explorerX + 30, entryY + 5, globalTextColor, Config.shadow);
        }
        context.getMatrices().pop();
    }

    public static void renderSnippetBox(DrawContext context, int snippetX, int snippetY, int snippetMaxWidth, int snippetHeight, RemotelyClient.CommandSnippet snippet, boolean hovered, boolean selected, MinecraftClient minecraftClient) {
        int id = ("snippet" + snippet.hashCode()).hashCode();
        float targetOffset = hovered ? -3f : 0f;
        float currentOffset = elevationOffsets.getOrDefault(id, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, currentOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        int bgColor = Config.getElementBackgroundColor(snippet.hashCode(), hovered, selected, true, false, false, false);
        context.fill(snippetX, snippetY, snippetX + snippetMaxWidth, snippetY + snippetHeight, bgColor);
        drawInnerBorder(context, snippetX, snippetY, snippetMaxWidth, snippetHeight, Config.getElementBorderColor(snippet.hashCode(), hovered, selected, true, false, false, false));
        drawOuterBorder(context, snippetX, snippetY, snippetMaxWidth, snippetHeight, globalOuterBorder);
        String displayName = trimTextToWidthWithEllipsis(snippet.name, snippetMaxWidth - 10);
        context.drawText(minecraftClient.textRenderer, Text.literal(displayName), snippetX + 5, snippetY + 5, globalTextColor, shadow);
        int lineSeparatorY = snippetY + 5 + minecraftClient.textRenderer.fontHeight + 2;
        context.fill(snippetX + 5, lineSeparatorY, snippetX + snippetMaxWidth - 5, lineSeparatorY + 1, elementHoverBorderColor);
        int contentY = lineSeparatorY + 4;
        context.enableScissor(snippetX + 5, (int)(contentY + currentOffset), snippetX + snippetMaxWidth - 5, (int)(snippetY + snippetHeight - 4 + currentOffset));
        String[] allLines = snippet.commands.split("\n");
        int lineY = contentY;
        for (String line : allLines) {
            String trimmed = trimTextToWidthWithEllipsis(line, snippetMaxWidth - 10);
            context.drawText(minecraftClient.textRenderer, Text.literal(trimmed), snippetX + 5, lineY, globalDarkTextColor, shadow);
            lineY += minecraftClient.textRenderer.fontHeight + 2;
        }
        context.disableScissor();
        context.getMatrices().pop();
    }
    public static void drawCustomButton(DrawContext context, int x, int y, String text, MinecraftClient mc, boolean hovered, boolean dynamic, boolean centered, boolean selected, boolean clickable, int bW, int bH, int txColor, int hoverColor, int mouseX, int mouseY, String tooltipText) {
        if (dynamic) {
            bW = mc.textRenderer.getWidth(text) + 10;
        }
        int id = (text.hashCode() * 31 + bW) * 31;
        float targetOffset = hovered ? -3f : 0f;
        float currentOffset = elevationOffsets.getOrDefault(id, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, currentOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        context.fill(x, y, x + bW, y + bH, Config.getElementBackgroundColor(id, hovered, selected, clickable, false, false, false));
        drawInnerBorder(context, x, y, bW, bH, Config.getElementBorderColor(id, hovered, selected, clickable, false, false, false));
        drawOuterBorder(context, x, y, bW, bH, globalOuterBorder);
        int tw = mc.textRenderer.getWidth(text);
        int tx = centered ? x + (bW - tw) / 2 : x + 5;
        int ty = y + 5;
        context.drawText(mc.textRenderer, Text.literal(text), tx, ty, hovered ? hoverColor : txColor, Config.shadow);
        CustomTooltip.show(tooltipText, mouseX, mouseY, context.getScaledWindowWidth(), context.getScaledWindowHeight(), mc.textRenderer, hovered);
        CustomTooltip.renderTooltip(context, mc.textRenderer, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        context.getMatrices().pop();
    }


    public static void drawSquareButton(DrawContext context, int x, int y, MinecraftClient mc, boolean hovered, int mouseX, int mouseY, String tooltipText, BufferedImage icon) {
        int w = 18;
        int h = 18;
        int id = ("square" + x + y).hashCode();
        float targetOffset = hovered ? -3f : 0f;
        float currentOffset = elevationOffsets.getOrDefault(id, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, currentOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        context.fill(x, y, x + w, y + h, Config.getElementBackgroundColor(id, hovered, false, true, false, false, false));
        drawInnerBorder(context, x, y, w, h, Config.getElementBorderColor(id, hovered, false, true, false, false, false));
        drawOuterBorder(context, x, y, w, h, globalOuterBorder);
        if (icon != null) {
            drawPixelArt(context, x + 1, y + 1, 16, 16, icon);
        }
        CustomTooltip.show(tooltipText, mouseX, mouseY, context.getScaledWindowWidth(), context.getScaledWindowHeight(), mc.textRenderer, hovered);
        CustomTooltip.renderTooltip(context, mc.textRenderer, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        context.getMatrices().pop();
    }

    public static void drawSquareButton(DrawContext context, int x, int y, MinecraftClient mc, boolean hovered, int mouseX, int mouseY, String tooltipText, Identifier icon, int iconWidth, int iconHeight) {
        int w = 18;
        int h = 18;
        int id = ("square" + x + y).hashCode();
        float targetOffset = hovered ? -3f : 0f;
        float currentOffset = elevationOffsets.getOrDefault(id, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, currentOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        context.fill(x, y, x + w, y + h, Config.getElementBackgroundColor(id, hovered, false, true, false, false, false));
        drawInnerBorder(context, x, y, w, h, Config.getElementBorderColor(id, hovered, false, true, false, false, false));
        drawOuterBorder(context, x, y, w, h, globalOuterBorder);
        if (icon != null) {
            context.drawGuiTexture(icon, x + 2, y + 2,iconWidth, iconHeight);
        }

        CustomTooltip.show(tooltipText, mouseX, mouseY, context.getScaledWindowWidth(), context.getScaledWindowHeight(), mc.textRenderer, hovered);
        CustomTooltip.renderTooltip(context, mc.textRenderer, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        context.getMatrices().pop();
    }

    public static void drawLoading(DrawContext context, int height, int width) {
        try {
            loadingAnim = loadSpriteSheet("/assets/remotely/icons/loading.png");
        } catch (Exception e) {
            devPrint("Failed to load loading animation");
        }
        int frameWidth = 20;
        int frameHeight = 20;
        int rows = loadingAnim.getHeight() / frameHeight;
        for (int i = rows - 1; i >= 0; i--) {
            BufferedImage frame = loadingAnim.getSubimage(0, i * frameHeight, frameWidth, frameHeight);
            loadingFrames.add(frame);
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameTime >= 80) {
            currentLoadingFrame = (currentLoadingFrame + 1) % loadingFrames.size();
            lastFrameTime = currentTime;
        }
        BufferedImage currentFrame = loadingFrames.get(currentLoadingFrame);
        int scale = 8;
        int imgWidth = currentFrame.getWidth() * scale;
        int imgHeight = currentFrame.getHeight() * scale;
        int centerX = (width - imgWidth) / 2;
        int centerY = (height - imgHeight) / 2;
        drawPixelArt(context, centerX, centerY, imgWidth, imgHeight, currentFrame);
    }

    public static void drawScreenHeader(DrawContext context, int width, int height, int backgroundWidth, int mouseX, int mouseY, Screen parent, MinecraftClient minecraftClient, IconWithTooltip icon1, IconWithTooltip icon2, IconWithTooltip icon3, IconWithTooltip icon4, IconWithTooltip icon5, IconWithTooltip icon6, IconWithTooltip icon7, IconWithTooltip icon8, IconWithTooltip specialIcon) {
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
                        context.fill(5, 60, backgroundWidth, height - 5, innerBackgroundColor);
                        drawInnerBorder(context, 5, 60, backgroundWidth - 5, height - 65, innerBorderColor);
                        drawOuterBorder(context, 5, 60, backgroundWidth - 5, height - 65, globalOuterBorder);
                    }
                }
            }
        }
        if (icon1 != null) {
            boolean isIcon1Hovered = mouseX >= width - 23 && mouseX <= width - 6 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 23, 5, minecraftClient, isIcon1Hovered, mouseX, mouseY, icon1.getTooltip(), icon1.getImage());
        }
        if (icon2 != null) {
            boolean isIcon2Hovered = mouseX >= width - 46 && mouseX <= width - 29 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 46, 5, minecraftClient, isIcon2Hovered, mouseX, mouseY, icon2.getTooltip(), icon2.getImage());
        }
        if (icon3 != null) {
            boolean isIcon3Hovered = mouseX >= width - 69 && mouseX <= width - 52 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 69, 5, minecraftClient, isIcon3Hovered, mouseX, mouseY, icon3.getTooltip(), icon3.getImage());
        }
        if (icon4 != null) {
            boolean isIcon4Hovered = mouseX >= width - 92 && mouseX <= width - 75 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 92, 5, minecraftClient, isIcon4Hovered, mouseX, mouseY, icon4.getTooltip(), icon4.getImage());
        }
        if (icon5 != null) {
            boolean isIcon5Hovered = mouseX >= 5 && mouseX <= 22 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 5, 5, minecraftClient, isIcon5Hovered, mouseX, mouseY, icon5.getTooltip(), icon5.getImage());
        }
        if (icon6 != null) {
            boolean isIcon6Hovered = mouseX >= 28 && mouseX <= 45 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 28, 5, minecraftClient, isIcon6Hovered, mouseX, mouseY, icon6.getTooltip(), icon6.getImage());
        }
        if (icon7 != null) {
            boolean isIcon7Hovered = mouseX >= 51 && mouseX <= 68 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 51, 5, minecraftClient, isIcon7Hovered, mouseX, mouseY, icon7.getTooltip(), icon7.getImage());
        }
        if (icon8 != null) {
            boolean isIcon8Hovered = mouseX >= 74 && mouseX <= 91 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 74, 5, minecraftClient, isIcon8Hovered, mouseX, mouseY, icon8.getTooltip(), icon8.getImage());
        }
        if (specialIcon != null) {
            int searchBarWidth = 200;
            int specialIconX = (width - searchBarWidth) / 2 - 23;
            boolean isSpecialIconHovered = mouseX >= specialIconX && mouseX <= specialIconX + 17 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, specialIconX, 5, minecraftClient, isSpecialIconHovered, mouseX, mouseY, specialIcon.getTooltip(), specialIcon.getImage());
        }
    }

    public static void drawToggle(DrawContext context, int x, int y, String label, boolean value, boolean hovered, int trackWidth, int trackHeight) {
        int id = ("toggle" + label + x + 143).hashCode();
        float targetOffset = hovered ? -2f : 0f;
        float currentOffset = elevationOffsets.getOrDefault(id, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, currentOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        int trackColor = Config.getElementBackgroundColor(id, hovered, value, true, false, false, false);
        context.fill(x, y, x + trackWidth, y + trackHeight, trackColor);
        context.fill(x, y + (int)(trackHeight * 0.75), x + trackWidth, y + trackHeight, 0x20000000);
        drawInnerBorder(context, x, y, trackWidth, trackHeight, Config.getElementBorderColor(id, hovered, value, true, false, false, false));
        drawOuterBorder(context, x, y, trackWidth, trackHeight, globalOuterBorder);
        int knobId = (id + "knob").hashCode();
        float knobTargetX = value ? x + trackWidth - (trackHeight - 4) - 2 : x + 2;
        float currentKnobX = elevationOffsets.getOrDefault(knobId, knobTargetX);
        if (Math.abs(knobTargetX - currentKnobX) > trackWidth/2f) {
            currentKnobX = knobTargetX;
        }
        currentKnobX += (knobTargetX - currentKnobX) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(knobId, currentKnobX);
        int knobDiameter = trackHeight - 4;
        int knobX = (int) currentKnobX;
        int knobY = y + 2;
        context.fill(knobX, knobY, knobX + knobDiameter, knobY + knobDiameter, Config.getElementBackgroundColor(knobId, hovered, false, true, false, false, false));
        drawInnerBorder(context, knobX, knobY, knobDiameter, knobDiameter, Config.getElementBorderColor(knobId, hovered, false, true, false, false, false));

        context.getMatrices().pop();
    }

    public static void drawSlider(DrawContext context, MinecraftClient mc, int x, int y, String label, int currentValue, int minValue, int maxValue, boolean hovered, int mouseX, int mouseY, String toolTipText, int sliderWidth, int sliderHeight) {
        int id = ("slider" + label).hashCode();
        float targetOffset = hovered ? -2f : 0f;
        float currentOffset = elevationOffsets.getOrDefault(id, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, currentOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        int bg = Config.getElementBackgroundColor(id, hovered, false, true, false, false, false);
        context.fill(x, y, x + sliderWidth, y + sliderHeight, bg);

        float ratio = (float)(currentValue - minValue) / (float)(maxValue - minValue);
        int fillWidth = (int)(ratio * (sliderWidth - 2));
        context.fill(x + 1, y + 1, x + 1 + fillWidth, y + sliderHeight - 1, Config.getElementBorderColor(id + "Inner".hashCode(), hovered, true, true, false, false, false));
        drawInnerBorder(context, x, y, sliderWidth, sliderHeight, Config.getElementBorderColor(id, hovered, false, true, false, false, false));
        drawOuterBorder(context, x, y, sliderWidth, sliderHeight, globalOuterBorder);
        context.fill(x + 1, y + sliderHeight - (int)(sliderHeight * 0.25), x + 1 + fillWidth, y + sliderHeight - 1, 0x20000000);

        String text = String.valueOf(currentValue);
        int tw = mc.textRenderer.getWidth(text);
        int tx = x + (sliderWidth - tw) / 2;
        int ty = y + 5;
        context.drawText(mc.textRenderer, Text.literal(text), tx, ty, Config.globalTextColor, Config.shadow);
        CustomTooltip.show(toolTipText, mouseX, mouseY, sliderWidth, sliderHeight, mc.textRenderer, hovered);
        CustomTooltip.renderTooltip(context, mc.textRenderer, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        context.getMatrices().pop();
    }

    public static void drawSlider(DrawContext context, MinecraftClient mc, int x, int y, String label, double currentValue, boolean hovered, boolean selected, int mouseX, int mouseY, String toolTipText, int sliderWidth, int sliderHeight) {
        int id = ("slider" + label).hashCode();
        float targetOffset = hovered ? -2f : 0f;
        float currentOffset = elevationOffsets.getOrDefault(id, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, currentOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        int bg = Config.getElementBackgroundColor(id, hovered, selected, true, false, false, false);
        context.fill(x, y, x + sliderWidth, y + sliderHeight, bg);
        drawInnerBorder(context, x, y, sliderWidth, sliderHeight, Config.getElementBorderColor(id, hovered, selected, true, false, false, false));
        drawOuterBorder(context, x, y, sliderWidth, sliderHeight, globalOuterBorder);
        float clampedValue = (float) MathHelper.clamp(currentValue, 0f, 1f);
        int knobDiameter = sliderHeight - 4;
        int availableWidth = sliderWidth - knobDiameter - 4;
        int knobId = ("slider" + label + "knob").hashCode();
        float targetKnobX = x + 2 + availableWidth * clampedValue;
        float currentKnobX;
        currentKnobX = elevationOffsets.getOrDefault(knobId, targetKnobX);
        currentKnobX += (targetKnobX - currentKnobX) * globalMovementSpeed * deltaTime;
        int knobX = (int) currentKnobX;
        int knobY = y + 2;
        int knobColor = Config.getElementBackgroundColor(knobId, hovered, selected, true, false, false, false);
        context.fill(knobX, knobY, knobX + knobDiameter, knobY + knobDiameter, knobColor);
        drawInnerBorder(context, knobX, knobY, knobDiameter, knobDiameter, Config.getElementBorderColor(knobId, hovered, selected, true, false, false, false));
        int tw = mc.textRenderer.getWidth(label);
        int tx = x + (sliderWidth - tw) / 2;
        int ty = y + (sliderHeight - mc.textRenderer.fontHeight) / 2;
        context.drawText(mc.textRenderer, Text.literal(label), tx, ty, Config.globalTextColor, Config.shadow);
        CustomTooltip.show(toolTipText, mouseX, mouseY, context.getScaledWindowWidth(), context.getScaledWindowHeight(), mc.textRenderer, hovered);
        CustomTooltip.renderTooltip(context, mc.textRenderer, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        context.getMatrices().pop();
    }

    public static void drawScrollSelector(DrawContext context, MinecraftClient mc, int x, int y, List<String> options, int selectedIndex, boolean hovered, int w, int h) {
        int id = ("scrollSelector" + options).hashCode();
        Float scrollIndex = scrollSelectorIndexFloatMap.get(id);
        Integer prevIndex = previousScrollSelectorIndexMap.get(id);
        if (scrollIndex == null || prevIndex == null) {
            scrollIndex = (float) selectedIndex;
            prevIndex = selectedIndex;
        }
        if (prevIndex != selectedIndex) {
            prevIndex = selectedIndex;
        }
        scrollIndex += (selectedIndex - scrollIndex) * globalScrollSpeed * deltaTime;
        while (scrollIndex - selectedIndex > options.size() / 2f) {
            scrollIndex -= options.size();
        }
        while (scrollIndex - selectedIndex < -options.size() / 2f) {
            scrollIndex += options.size();
        }
        scrollSelectorIndexFloatMap.put(id, scrollIndex);
        previousScrollSelectorIndexMap.put(id, prevIndex);
        float targetOffset = hovered ? -2f : 0f;
        float currentOffset = elevationOffsets.getOrDefault(id, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, currentOffset);

        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        int bg = Config.getElementBackgroundColor(id, hovered, false, true, false, false, false);
        context.fill(x, y, x + w, y + h, bg);
        drawInnerBorder(context, x, y, w, h, Config.getElementBorderColor(id, hovered, false, true, false, false, false));
        drawOuterBorder(context, x, y, w, h, globalOuterBorder);
        int contentX = x + 1;
        int contentWidth = x + w - contentX - 1;
        context.enableScissor(contentX, y, contentX + contentWidth, y + h);
        float centerSlot = contentX + contentWidth / 2f;
        int maxTextW = 0;
        for (String s : options) {
            int textW = mc.textRenderer.getWidth(s);
            if (textW > maxTextW) {
                maxTextW = textW;
            }
        }
        float slotSpacing = maxTextW + 15f;
        for (int i = 0; i < options.size(); i++) {
            float ringIndex = i - scrollIndex;
            if (ringIndex < -options.size() / 2f) ringIndex += options.size();
            if (ringIndex > options.size() / 2f) ringIndex -= options.size();
            float offsetX = centerSlot + ringIndex * slotSpacing;
            String s = options.get(i);
            int textW = mc.textRenderer.getWidth(s);
            float textX = offsetX - textW / 2f;
            float textY = (y + (h - mc.textRenderer.fontHeight) / 2f) + 1;
            context.drawText(mc.textRenderer, Text.literal(s), (int) textX, (int) textY, Config.globalTextColor, Config.shadow);
        }
        context.disableScissor();
        context.getMatrices().pop();
    }

    public static void drawTabSwitch(DrawContext context, MinecraftClient mc, int x, int y, String label, List<String> options, int currentIndex, int mouseX, int mouseY, int barWidth, int barHeight) {
        context.fill(x, y, x + barWidth, y + barHeight, Config.elementBackgroundColor);
        drawInnerBorder(context, x, y, barWidth, barHeight, Config.elementBorderColor);
        drawOuterBorder(context, x, y, barWidth, barHeight, globalOuterBorder);
        int segmentCount = options.size();
        int segmentWidth = barWidth / segmentCount;
        for (int i = 0; i < segmentCount; i++) {
            int segX = x + i * segmentWidth;
            int segW = (i == segmentCount - 1) ? (x + barWidth - segX) : segmentWidth;
            boolean segmentedHovered = mouseX >= segX && mouseX < segX + segW && mouseY >= y && mouseY < y + barHeight;
            boolean selected = i == currentIndex;
            int segId = ("tabSwitch" + label + options.get(i)).hashCode();
            float targetOffset = segmentedHovered ? -2f : 0f;
            float currentOffset = elevationOffsets.getOrDefault(segId, 0f);
            currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
            elevationOffsets.put(segId, currentOffset);
            context.getMatrices().push();
            context.getMatrices().translate(0, currentOffset, 0);
            int color = Config.getElementBackgroundColor(segId, segmentedHovered, selected, true, false, false, false);
            context.fill(segX, y, segX + segW, y + barHeight, color);
            drawInnerBorder(context, segX, y, segW, barHeight, Config.getElementBorderColor(segId, segmentedHovered, selected, true, false, false, false));
            int textWidth = mc.textRenderer.getWidth(options.get(i));
            int textX = segX + (segW - textWidth) / 2;
            int textY = y + ((barHeight - mc.textRenderer.fontHeight) / 2) + 1;
            context.drawText(mc.textRenderer, Text.literal(options.get(i)), textX, textY, Config.globalTextColor, Config.shadow);
            context.getMatrices().pop();
        }
    }

    public static void drawTextInput(DrawContext context, MinecraftClient mc, int x, int y, String label, String textValue, boolean focused, int cursorPos, int selectionStart, int selectionEnd, boolean hovered, int inputWidth, int inputHeight, String placeholder) {
        int id = ("textInput" + label + textValue + cursorPos + selectionStart + selectionEnd).hashCode();
        float elevationTarget = hovered ? -2f : 0f;
        float elevationCurrent = elevationOffsets.getOrDefault(id, 0f);
        elevationCurrent += (elevationTarget - elevationCurrent) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, elevationCurrent);
        context.getMatrices().push();
        context.getMatrices().translate(0, elevationCurrent, 0);
        int bg = Config.getElementBackgroundColor(id, hovered, focused, true, false, false, false);
        context.fill(x, y, x + inputWidth, y + inputHeight, bg);
        drawInnerBorder(context, x, y, inputWidth, inputHeight, Config.getElementBorderColor(id, hovered, focused, true, false, false, false));
        drawOuterBorder(context, x, y, inputWidth, inputHeight, globalOuterBorder);
        int displayWidth = inputWidth - 10;
        int textWidth = mc.textRenderer.getWidth(textValue);
        int textY = y + (inputHeight - mc.textRenderer.fontHeight) / 2 + 1;
        String beforeCursor = cursorPos <= textValue.length() ? textValue.substring(0, cursorPos) : textValue;
        int cursorX = x + 5 + mc.textRenderer.getWidth(beforeCursor);
        float scrollOffsetLocal = 0;
        if (textWidth > displayWidth) {
            if (cursorX > x + displayWidth) {
                scrollOffsetLocal = cursorX - (x + displayWidth);
            }
        }
        context.enableScissor(x, y, x + inputWidth, y + inputHeight);
        if (textValue.isEmpty()) {
            context.drawText(mc.textRenderer, Text.literal(placeholder), x + 5, textY, Config.globalDarkTextColor, Config.shadow);
        } else {
            int selStart = Math.min(selectionStart, selectionEnd);
            int selEnd = Math.max(selectionStart, selectionEnd);
            if (focused && selStart != selEnd) {
                String textBeforeSel = selStart <= textValue.length() ? textValue.substring(0, selStart) : textValue;
                int selStartX = x + 5 + mc.textRenderer.getWidth(textBeforeSel) - (int)scrollOffsetLocal;
                String selectedText = selEnd <= textValue.length() ? textValue.substring(selStart, selEnd) : "";
                int selWidth = mc.textRenderer.getWidth(selectedText);
                context.fill(selStartX, textY, selStartX + selWidth, y + 4 + mc.textRenderer.fontHeight, Config.globalSelectionColor);
            }
            context.drawText(mc.textRenderer, Text.literal(textValue), x + 5 - (int)scrollOffsetLocal, textY, Config.globalTextColor, Config.shadow);
        }
        if (focused) {
            int cursorPosX = x + 5 + mc.textRenderer.getWidth(beforeCursor) - (int)scrollOffsetLocal;
            context.fill(cursorPosX, textY, cursorPosX + 1, textY + mc.textRenderer.fontHeight, Config.globalCursorAnimatedColor);
        }
        context.disableScissor();
        context.getMatrices().pop();
    }

    public static void animatedScaling(DrawContext context, Screen parent, MinecraftClient minecraftClient) {
        animScaleFactor += (targetScaleFactor - animScaleFactor) * scaleAnimationSpeed * deltaTime;
        if (Math.abs(animScaleFactor - targetScaleFactor) < 0.005f) {
            animScaleFactor = targetScaleFactor;
        }
        if (targetScaleFactor != animScaleFactor) {
            minecraftClient.getWindow().setScaleFactor(animScaleFactor);
            parent.width = minecraftClient.getWindow().getScaledWidth();
            parent.height = minecraftClient.getWindow().getScaledHeight();
            globalScaleFactor = animScaleFactor;
            lastRounding = true;
        } else if (lastRounding) {
            minecraftClient.getWindow().setScaleFactor(animScaleFactor);
            parent.width = minecraftClient.getWindow().getScaledWidth();
            parent.height = minecraftClient.getWindow().getScaledHeight();
            globalScaleFactor = animScaleFactor;
            lastRounding = false;
        }
        if (isDev & enableDebugTools) {
            String animatedAndTargetScaleFactorVisulization = "Animated Scale: " + animScaleFactor + " | Target Scale: " + targetScaleFactor;
            String isRounded = "Rounded?: " + (lastRounding ? "No" : "Yes");
            context.drawTextWithShadow(minecraftClient.textRenderer, animatedAndTargetScaleFactorVisulization, 5, 5, globalHoverTextColor);
            context.drawTextWithShadow(minecraftClient.textRenderer, isRounded, 5, 15, globalHoverTextColor);
        }
    }

    public static void scaleScroll(double vertAmount) {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        boolean ctrlHeld = InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean altHeld = InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_ALT) || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_ALT);
        if (ctrlHeld && altHeld) {
            targetScaleFactor = Math.max(1f, Math.min(4f, targetScaleFactor + (vertAmount > 0 ? 1f : -1f)));
            globalScaleFactor = targetScaleFactor;
        }
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

    public static class TabsBar<T> {
        public static class Tab<T> {
            public String name;
            public boolean unsaved;
            public T data;
            public final int id;
            public Tab(String name, boolean unsaved, T data) {
                this.name = name;
                this.unsaved = unsaved;
                this.data = data;
                this.id = System.identityHashCode(this);
            }
        }
        private final List<Tab<T>> tabs;
        private int activeTab = 0;
        private float scrollOffset = 0;
        private float targetScrollOffset = 0;
        private int draggingTab = -1;
        private boolean isDragging = false;
        private float dragStartX = 0;
        private float dragCurrentX = 0;
        private int dragOverTab = -1;
        public int tabBarX, tabBarY, tabBarW, tabBarH;
        public int tabBarHeight = 18;
        private int tabPadding = 6;
        private int tabGap = 5;
        private final int plusTabWidth = 18;
        private boolean hasPlus;
        private boolean allowRename = false;
        private boolean allowClose = false;
        private boolean allowDrag = false;
        private boolean allowScroll = false;
        private int renamingTab = -1;
        private StringBuilder renameBuffer = new StringBuilder();
        private int renameCursor = 0;
        private long lastRenameInput = 0;
        private int hoverTab = -1;
        private int hoverClose = -1;
        private int hoverPlus = -1;
        private Map<Integer, Float> tabWidths = new HashMap<>();
        private Map<Integer, Float> tabOffsets = new HashMap<>();
        public Map<Integer, Float> dragAnimatedX = new HashMap<>();
        private Runnable onTabOrderChanged = null;
        private Runnable onTabClosed = null;
        private Runnable onTabSelected = null;
        private Runnable onTabPlus = null;
        private Runnable onTabRenamed = null;

        public TabsBar(List<Tab<T>> tabs) {
            this.tabs = tabs;
        }
        public void setActiveTab(int idx) { activeTab = idx; }
        public int getActiveTab() { return activeTab; }
        public void setActiveTabName(String name) { tabs.get(activeTab).name = name; }
        public void setHasPlus(boolean b) { hasPlus = b; }
        public void setAllowRename(boolean b) { allowRename = b; }
        public void setAllowClose(boolean b) { allowClose = b; }
        public void setAllowDrag(boolean b) { allowDrag = b; }
        public void setAllowScroll(boolean b) { allowScroll = b; }
        public void setTabBarBounds(int x, int y, int w, int h) { tabBarX = x; tabBarY = y; tabBarW = w; tabBarH = h; }
        public void setOnTabOrderChanged(Runnable r) { onTabOrderChanged = r; }
        public void setOnTabClosed(Runnable r) { onTabClosed = r; }
        public void setOnTabSelected(Runnable r) { onTabSelected = r; }
        public void setOnTabPlus(Runnable r) { onTabPlus = r; }
        public void setOnTabRenamed(Runnable r) { onTabRenamed = r; }
        public List<Tab<T>> getTabs() { return tabs; }
        public int getTabCount() { return tabs.size(); }
        public int getRenamingTab() { return renamingTab; }
        public int getDraggingTab() { return draggingTab; }
        public void setDraggingTab(int idx) { draggingTab = idx; }
        public boolean isDragging() { return isDragging; }
        public void setIsDragging(boolean b) { isDragging = b; }
        public float getDragStartX() { return dragStartX; }
        public float getDragCurrentX() { return dragCurrentX; }
        public int getDragOverTab() { return dragOverTab; }
        public Map<Integer, Float> getTabWidths() { return tabWidths; }
        public Map<Integer, Float> getTabOffsets() { return tabOffsets; }

        public void renderTabsBar(DrawContext context, TextRenderer textRenderer, TabsBar<T> tabsBar, int mouseX, int mouseY, boolean shadow) {
            List<Tab<T>> tabs = tabsBar.getTabs();
            int x = (int) (tabBarX - scrollOffset);
            int totalTabsWidth = 0;
            for (Tab<T> tab : tabs) {
                String name = tab.name + (tab.unsaved ? "*" : "");
                int targetWidth = textRenderer.getWidth(name) + 2 * tabPadding;
                float currentWidth = tabWidths.getOrDefault(tab.id, (float) targetWidth);
                float animatedWidth = currentWidth + (targetWidth - currentWidth) * globalExpandSpeed * deltaTime;
                tabWidths.put(tab.id, animatedWidth);
                int tabWidth = (int) animatedWidth;
                totalTabsWidth += tabWidth + tabGap;
            }
            if (hasPlus) totalTabsWidth += plusTabWidth + tabGap;
            float maxScroll = Math.max(0, totalTabsWidth - tabBarW);
            targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, maxScroll));
            scrollOffset += (targetScrollOffset - scrollOffset) * globalScrollSpeed * deltaTime;

            float[] basePositions = new float[tabs.size()];
            float baseX = x;
            for (int i = 0; i < tabs.size(); i++) {
                basePositions[i] = baseX;
                baseX += tabWidths.getOrDefault(tabs.get(i).id, 60f) + tabGap;
            }

            float[] targetPositions = new float[tabs.size()];
            for (int i = 0; i < tabs.size(); i++) {
                targetPositions[i] = basePositions[i];
                if (isDragging) {
                    if (i == draggingTab) {
                        targetPositions[i] = dragCurrentX - tabWidths.getOrDefault(tabs.get(i).id, 60f) / 2f;
                    } else if ((draggingTab < dragOverTab && i > draggingTab && i <= dragOverTab) ||
                            (draggingTab > dragOverTab && i < draggingTab && i >= dragOverTab)) {
                        float tabAndGapWidth = tabWidths.getOrDefault(tabs.get(draggingTab).id, 60f) + tabGap;
                        targetPositions[i] += (draggingTab < dragOverTab) ? -tabAndGapWidth : tabAndGapWidth;
                    }
                }
            }

            for (int i = 0; i < tabs.size(); i++) {
                Tab<T> tab = tabs.get(i);
                String name = tab.name + (tab.unsaved ? "*" : "");
                float tabWidth = tabWidths.getOrDefault(tab.id, (float)textRenderer.getWidth(name) + 2 * tabPadding);

                float currentPos = dragAnimatedX.getOrDefault(i, basePositions[i]);
                float newPos = currentPos + (targetPositions[i] - currentPos) * globalMovementSpeed * deltaTime;
                dragAnimatedX.put(i, newPos);
                tabOffsets.put(tab.id, newPos);

                boolean isActive = (i == activeTab);
                boolean isHovered = mouseX >= newPos && mouseX <= newPos + tabWidth && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight;
                boolean isDragged = isDragging && (draggingTab == i);
                float targetOffset = isHovered ? -2f : 0f;
                int elevId = tab.id;
                float currentOffset = Render.elevationOffsets.getOrDefault(elevId, 0f);
                currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
                Render.elevationOffsets.put(elevId, currentOffset);
                context.getMatrices().push();
                context.getMatrices().translate(0, currentOffset, isDragged ? 499 : isActive || isHovered ? 498 : 497);
                int bgColor = Config.getElementBackgroundColor(1000 + i, isHovered, isActive, true, tab.unsaved, false, false);
                context.fill((int) newPos, tabBarY, (int) newPos + (int) tabWidth, tabBarY + tabBarHeight, bgColor);
                drawInnerBorder(context, (int) newPos, tabBarY, (int) tabWidth, tabBarHeight, Config.getElementBorderColor(1000 + i, isHovered, isActive, true, tab.unsaved, false, false));
                drawOuterBorder(context, (int) newPos, tabBarY, (int) tabWidth, tabBarHeight, globalOuterBorder);
                context.enableScissor((int) newPos + 1, (int) (tabBarY + currentOffset), (int) newPos + (int) tabWidth - 1, tabBarY + tabBarHeight);
                if (renamingTab == i) {
                    int textX = (int) newPos + tabPadding;
                    int textY = tabBarY + 5;
                    String beforeCursor = renameBuffer.substring(0, Math.min(renameCursor, renameBuffer.length()));
                    int cursorX = textX + textRenderer.getWidth(beforeCursor);
                    context.drawText(textRenderer, Text.literal(renameBuffer.toString()), textX, textY, Config.getTextColor(isHovered, false), shadow);
                    context.fill(cursorX, textY, cursorX + 1, textY + textRenderer.fontHeight, globalCursorAnimatedColor);
                } else {
                    int textX = (int) newPos + tabPadding;
                    int textY = tabBarY + 5;
                    context.drawText(textRenderer, Text.literal(name), textX, textY, Config.getTextColor(isHovered, false), shadow);
                }
                if (allowClose) {
                    int closeX = (int) (newPos + tabWidth - 5 - 1);
                    int closeY = tabBarY + 1;
                    boolean closeHovered = mouseX >= closeX && mouseX <= closeX + 5 && mouseY >= closeY && mouseY <= closeY + 5;
                    int closeColor = Config.getElementBorderColor(1000 + "x".hashCode(), false, false, true, closeHovered, false, false);
                    context.drawText(textRenderer, Text.literal("×"), closeX, closeY - 1, closeColor, true);
                }
                context.getMatrices().pop();
                context.disableScissor();
            }

            if (hasPlus) {
                int plusId = "plus_tab_pos".hashCode();
                float currentPlusX = dragAnimatedX.getOrDefault(plusId, baseX);
                float newPlusX = currentPlusX + (baseX - currentPlusX) * globalMovementSpeed * deltaTime;
                dragAnimatedX.put(plusId, newPlusX);
                int drawX = (int)newPlusX;
                boolean isPlusHovered = mouseX >= drawX && mouseX <= drawX + plusTabWidth && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight;
                int plusElevId = ("plus_tab").hashCode();
                float targetOffset = isPlusHovered ? -2f : 0f;
                float currentOffset = Render.elevationOffsets.getOrDefault(plusElevId, 0f);
                currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
                Render.elevationOffsets.put(plusElevId, currentOffset);
                context.getMatrices().push();
                context.getMatrices().translate(0, currentOffset, 0);
                int bgColor = Config.getElementBackgroundColor(1000 + tabs.size(), isPlusHovered, false, true, false, false, false);
                context.fill(drawX, tabBarY, drawX + plusTabWidth, tabBarY + tabBarHeight, bgColor);
                drawInnerBorder(context, drawX, tabBarY, plusTabWidth, tabBarHeight, Config.getElementBorderColor(1000 + tabs.size(), isPlusHovered, false, true, false, false, false));
                drawOuterBorder(context, drawX, tabBarY, plusTabWidth, tabBarHeight, globalOuterBorder);
                context.drawText(textRenderer, Text.literal("+"), drawX + plusTabWidth / 2 - (textRenderer.getWidth("+") / 2), tabBarY + 4, Config.getTextColor(isPlusHovered, false), shadow);
                context.getMatrices().pop();
            }
        }
        public boolean handleTabsBarMouse(int mouseX, int mouseY, int button) {
            float x = tabBarX - scrollOffset;
            for (int i = 0; i < tabs.size(); i++) {
                Tab<T> tab = tabs.get(i);
                float tabWidth = tabWidths.getOrDefault(tab.id, 60f);
                float drawX = tabOffsets.getOrDefault(tab.id, x);
                boolean isHovered = mouseX >= drawX && mouseX <= drawX + tabWidth && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight;
                int closeX = (int) (drawX + tabWidth - 5 - 1);
                int closeY = tabBarY + 1;
                boolean closeHovered = mouseX >= closeX && mouseX <= closeX + 5 && mouseY >= closeY && mouseY <= closeY + 5;
                if (closeHovered && allowClose && button == 0) {
                    tabs.remove(i);
                    if (onTabClosed != null) onTabClosed.run();
                    if (activeTab >= tabs.size()) activeTab = tabs.size() - 1;
                    return true;
                }
                if (isHovered) {
                    if (button == 1 && allowRename) {
                        renamingTab = i;
                        renameBuffer.setLength(0);
                        renameBuffer.append(tab.name);
                        renameCursor = tab.name.length();
                        lastRenameInput = System.currentTimeMillis();
                        return true;
                    }
                    if (button == 2 && allowClose) {
                        tabs.remove(i);
                        if (onTabClosed != null) onTabClosed.run();
                        if (activeTab >= tabs.size()) activeTab = tabs.size() - 1;
                        return true;
                    }
                    if (button == 0) {
                        if (allowDrag) {
                            draggingTab = i;
                            dragStartX = mouseX;
                            dragCurrentX = mouseX;
                            isDragging = false;
                        }
                        if (!allowRename || renamingTab == -1) {
                            activeTab = i;
                            if (onTabSelected != null) onTabSelected.run();
                        }
                        return true;
                    }
                }
                x += tabWidth + tabGap;
            }
            if (hasPlus) {
                if (mouseX >= x && mouseX <= x + plusTabWidth && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight) {
                    if (button == 0 && onTabPlus != null) { onTabPlus.run(); return true; }
                }
            }
            return false;
        }
        public boolean handleTabsBarDrag(int button, double deltaX) {
            if (draggingTab != -1 && button == 0) {
                dragCurrentX += (float) deltaX;
                if (!isDragging && Math.abs(dragCurrentX - dragStartX) > 5) {
                    isDragging = true;
                }
                if (isDragging) {
                    int n = tabs.size();
                    float[] base = new float[n];
                    float x = tabBarX - scrollOffset;
                    for (int i = 0; i < n; i++) {
                        base[i] = x;
                        float w = tabWidths.getOrDefault(tabs.get(i).id, 60f);
                        x += w + tabGap;
                    }
                    float dragCenter = dragCurrentX;
                    List<Float> centers = new ArrayList<>(n - 1);
                    for (int i = 0; i < n; i++) {
                        if (i == draggingTab) continue;
                        float w = tabWidths.getOrDefault(tabs.get(i).id, 60f);
                        centers.add(base[i] + w * 0.5f);
                    }
                    Collections.sort(centers);
                    int count = 0;
                    for (float c : centers) {
                        if (dragCenter > c) count++;
                        else break;
                    }
                    dragOverTab = count;
                }
                return true;
            }
            return false;
        }
        public boolean handleTabsBarRelease(int button) {
            if (button == 0 && draggingTab != -1) {
                int prevActiveTabId = tabs.get(activeTab).id;
                if (isDragging && dragOverTab != -1 && dragOverTab != draggingTab) {
                    Map<Integer, Float> positionsByTabId = new HashMap<>();
                    for (int i = 0; i < tabs.size(); i++) {
                        Tab<T> tab = tabs.get(i);
                        positionsByTabId.put(tab.id, dragAnimatedX.getOrDefault(i, tabOffsets.getOrDefault(tab.id, 0f)));
                    }
                    Tab<T> moved = tabs.remove(draggingTab);
                    tabs.add(dragOverTab, moved);
                    for (int i = 0; i < tabs.size(); i++) {
                        if (tabs.get(i).id == prevActiveTabId) {
                            activeTab = i;
                            break;
                        }
                    }
                    Map<Integer, Float> newPositions = new HashMap<>();
                    for (int i = 0; i < tabs.size(); i++) {
                        Tab<T> tab = tabs.get(i);
                        if (positionsByTabId.containsKey(tab.id)) {
                            newPositions.put(i, positionsByTabId.get(tab.id));
                        }
                    }
                    dragAnimatedX = newPositions;

                    if (onTabOrderChanged != null) onTabOrderChanged.run();
                }
                draggingTab = -1;
                isDragging = false;
                dragOverTab = -1;
                return true;
            }
            return false;
        }
        public boolean handleTabsBarKey(int keyCode, int scanCode, int modifiers) {
            if (renamingTab != -1) {
                StringBuilder buf = renameBuffer;
                int cur = renameCursor;
                if (keyCode == GLFW.GLFW_KEY_ENTER) {
                    renamingTab = -1;
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if (cur > 0 && cur <= buf.length()) { buf.deleteCharAt(cur - 1); renameCursor = cur - 1; }
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
                    if (cur > 0) { renameCursor = cur - 1; } return true;
                } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                    if (cur < buf.length()) { renameCursor = cur + 1; } return true;
                } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    renamingTab = -1; return true;
                }
                return true;
            }
            return false;
        }
        public boolean handleTabsBarChar(char chr) {
            if (renamingTab != -1) {
                StringBuilder buf = renameBuffer;
                int cur = renameCursor;
                if (chr == '\r' || chr == '\n' || chr == '\b') return true;
                buf.insert(cur, chr); renameCursor = cur + 1;
                int idx = renamingTab;
                if (idx >= 0 && idx < tabs.size()) {
                    tabs.get(idx).name = buf.toString();
                    if (onTabRenamed != null) onTabRenamed.run();
                }
                return true;
            }
            return false;
        }
        public boolean handleTabsBarScroll(double verticalAmount, double mouseX, double mouseY) {
            if (!allowScroll) return false;
            if (mouseX < tabBarX || mouseX > tabBarX + tabBarW || mouseY < tabBarY || mouseY > tabBarY + tabBarHeight) return false;
            int totalTabsWidth = 0;
            for (Tab<T> tab : tabs) {
                totalTabsWidth += MinecraftClient.getInstance().textRenderer.getWidth(tab.name) + 2 * tabPadding + tabGap;
            }
            if (hasPlus) totalTabsWidth += plusTabWidth + tabGap;
            int availableWidth = tabBarW;
            float maxScroll = Math.max(0, totalTabsWidth - availableWidth);
            targetScrollOffset += (float) (-verticalAmount * 30);
            targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, maxScroll));
            return true;
        }

        public void renameTab(int i) {
            if (i >= 0 && i < tabs.size()) {
                renamingTab = i;
                renameBuffer.setLength(0);
                renameBuffer.append(tabs.get(i).name);
                renameCursor = renameBuffer.length();
                lastRenameInput = System.currentTimeMillis();
            }
        }

        public void closeTab(int i) {
            if (i >= 0 && i < tabs.size()) {
                tabs.remove(i);
                if (activeTab >= tabs.size()) activeTab = tabs.size() - 1;
            }
        }
    }
}


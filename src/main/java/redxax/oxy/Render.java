package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.config.Config;
import redxax.oxy.terminal.MultiTerminalScreen;
import redxax.oxy.explorer.FileExplorerScreen;
import redxax.oxy.explorer.FileEditorScreen;
import redxax.oxy.servers.PluginModManagerScreen;
import redxax.oxy.util.TabTextAnimator;

import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.config.Config.*;

public class Render {

    public static int buttonW = 60;
    public static int buttonH = 18;
    private static TabTextAnimator searchTextAnimator = new TabTextAnimator("", 2, 30);
    private static String previousFieldText = "";
    private static boolean isAnimating = false;

    public static void drawTabs(DrawContext context, TextRenderer textRenderer, List<?> tabs, int currentTabIndex, int mouseX, int mouseY) {
        int tabBarX = 5;
        int tabBarY = 35;
        int tabBarHeight = 18;
        boolean shadow = Config.shadow;
        int tabPadding = 5;
        int tabGap = 5;
        int plusTabWidth = 0;
        String plusSign = "";
        boolean isPlusTab = false;
        int x = tabBarX;
        for (int i = 0; i < tabs.size(); i++) {
            Object tab = tabs.get(i);
            String name;
            if (tab instanceof FileExplorerScreen.Tab) {
                name = ((FileExplorerScreen.Tab) tab).getAnimatedText();
            } else if (tab instanceof FileEditorScreen.Tab) {
                FileEditorScreen.Tab t = (FileEditorScreen.Tab) tab;
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
            int tabWidth;
            if (tab instanceof FileExplorerScreen.Tab) {
                tabWidth = textRenderer.getWidth(name) + 2 * tabPadding;
            } else if (tab instanceof FileEditorScreen.Tab) {
                tabWidth = textRenderer.getWidth(name) + 2 * tabPadding;
            } else if (tab instanceof PluginModManagerScreen.Tab) {
                tabWidth = textRenderer.getWidth(name) + 2 * tabPadding;
            } else if (tab instanceof MultiTerminalScreen.TabInfo) {
                tabWidth = ((MultiTerminalScreen.TabInfo) tab).width;
            } else {
                tabWidth = textRenderer.getWidth(name) + 2 * tabPadding;
            }
            boolean isActive = (i == currentTabIndex);
            int x2 = x + tabWidth;
            boolean isHovered = mouseX >= x && mouseX <= x2 && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight;
            int bgColor = isActive ? tabSelectedBackgroundColor : (isHovered ? tabBackgroundHoverColor : tabBackgroundColor);
            context.fill(x, tabBarY, x2, tabBarY + tabBarHeight, bgColor);
            drawInnerBorder(context, x, tabBarY, tabWidth, tabBarHeight, isActive ? tabSelectedBorderColor : isHovered ? tabBorderHoverColor : tabBorderColor);
            drawOuterBorder(context, x, tabBarY, tabWidth, tabBarHeight, globalBottomBorder);
            context.drawText(textRenderer, Text.literal(name), x + tabPadding, tabBarY + (tabBarHeight - textRenderer.fontHeight) / 2, isHovered ? tabTextHoverColor : tabTextColor, shadow);
            x += tabWidth + tabGap;
        }
        if (isPlusTab) {
            boolean isPlusTabHovered = mouseX >= x && mouseX <= x + plusTabWidth && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight;
            context.fill(x, tabBarY, x + plusTabWidth, tabBarY + tabBarHeight, isPlusTabHovered ? tabBackgroundHoverColor : tabBackgroundColor);
            drawInnerBorder(context, x, tabBarY, plusTabWidth, tabBarHeight, isPlusTabHovered ? tabBorderHoverColor : tabBorderColor);
            drawOuterBorder(context, x, tabBarY, plusTabWidth, tabBarHeight, globalBottomBorder);
            context.drawText(textRenderer, Text.literal(plusSign), x + plusTabWidth / 2 - textRenderer.getWidth(plusSign) / 2, tabBarY + (tabBarHeight - textRenderer.fontHeight) / 2, isPlusTabHovered ? tabTextHoverColor : tabTextColor, shadow);
        }
    }

    public static void drawSearchBar(DrawContext context, TextRenderer textRenderer, StringBuilder fieldText, boolean fieldFocused, int cursorPosition, int selectionStart, int selectionEnd, float pathScrollOffset, float pathTargetScrollOffset, boolean showCursor, boolean isSpecialMode, String caller) {
        if (!fieldText.toString().equals(previousFieldText)) {
            if (!fieldFocused) {
                searchTextAnimator.updateText(fieldText.toString());
                isAnimating = true;
            } else {
                searchTextAnimator = new TabTextAnimator(fieldText.toString(), 0, 10);
                isAnimating = false;
            }
            previousFieldText = fieldText.toString();
        }
        String displayText = fieldFocused ? fieldText.toString() : searchTextAnimator.getCurrentText();
        int searchBarWidth = 200;
        int searchBarHeight = 20;
        int searchBarX = (context.getScaledWindowWidth() - searchBarWidth) / 2;
        int searchBarY = 5;
        int baseColor = searchBarBackgroundColor;
        int activeColor = isSpecialMode ? (caller.equals("FileExplorerScreen") ? searchBarExplorerActiveBackgroundColor : airBarBackgroundColor) : searchBarActiveBackgroundColor;
        int activeBorderColor = isSpecialMode ? (caller.equals("FileExplorerScreen") ? searchBarExplorerActiveBorderColor : airBarBorderColor) : searchBarActiveBorderColor;
        int borderColor = searchBarBorderColor;
        int textColor = screensTitleTextColor;
        boolean shadow = Config.shadow;
        String hint = caller.equals("FileExplorerScreen") ? "Search..." : "Ask Remotely AI...";
        context.fill(searchBarX, searchBarY, searchBarX + searchBarWidth, searchBarY + searchBarHeight, fieldFocused ? activeColor : baseColor);
        drawInnerBorder(context, searchBarX, searchBarY, searchBarWidth, searchBarHeight, fieldFocused ? activeBorderColor : borderColor);
        drawOuterBorder(context, searchBarX, searchBarY, searchBarWidth, searchBarHeight, Config.globalBottomBorder);
        if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
            int selStart = Math.max(0, Math.min(selectionStart, selectionEnd));
            int selEnd = Math.min(displayText.length(), Math.max(selectionStart, selectionEnd));
            if (selStart < 0) selStart = 0;
            if (selEnd > displayText.length()) selEnd = displayText.length();
            String beforeSel = displayText.substring(0, selStart);
            String selectedText = displayText.substring(selStart, selEnd);
            int selX = searchBarX + 5 + textRenderer.getWidth(beforeSel);
            int selW = textRenderer.getWidth(selectedText);
            context.fill(selX, searchBarY + 4, selX + selW, searchBarY + 4 + textRenderer.fontHeight, 0x80FFFFFF);
        }
        if (fieldFocused && isSpecialMode && displayText.isEmpty()) {
            context.drawText(textRenderer, Text.literal(hint), searchBarX + 5, searchBarY + 5, textColor, false);
        }
        int displayWidth = searchBarWidth - 10;
        int textWidth = textRenderer.getWidth(displayText);
        int cursorX = searchBarX + 5 + textRenderer.getWidth(displayText.substring(0, Math.min(cursorPosition, displayText.length())));
        float cursorMargin = 50f;
        float localPathScrollOffset = pathScrollOffset;
        float localPathTargetScrollOffset = pathTargetScrollOffset;
        if (cursorX - localPathScrollOffset > searchBarX + displayWidth - 5 - cursorMargin) {
            localPathTargetScrollOffset = cursorX - (searchBarX + displayWidth - 5 - cursorMargin);
        } else if (cursorX - localPathScrollOffset < searchBarX + 5 + cursorMargin) {
            localPathTargetScrollOffset = cursorX - (searchBarX + 5 + cursorMargin);
        }
        localPathTargetScrollOffset = Math.max(0, Math.min(localPathTargetScrollOffset, textWidth - displayWidth));
        localPathScrollOffset += (localPathTargetScrollOffset - localPathScrollOffset);
        if (!fieldFocused && isAnimating && searchTextAnimator.hasCompleted()) {
            isAnimating = false;
        }
        context.enableScissor(searchBarX, searchBarY, searchBarX + searchBarWidth, searchBarY + searchBarHeight);
        context.drawText(textRenderer, Text.literal(displayText), searchBarX + 5 - (int) localPathScrollOffset, searchBarY + 5, textColor, shadow);
        if (fieldFocused && showCursor) {
            String beforeCursor = cursorPosition <= displayText.length() ? displayText.substring(0, cursorPosition) : displayText;
            int cursorPosX = searchBarX + 5 + textRenderer.getWidth(beforeCursor) - (int) localPathScrollOffset;
            context.fill(cursorPosX, searchBarY + 5, cursorPosX + 1, searchBarY + 5 + textRenderer.fontHeight, 0xFFFFFFFF);
        }
        context.disableScissor();
    }

    public static void drawCustomButton(DrawContext context, int x, int y, String text, MinecraftClient mc, boolean hovered, boolean dynamic, boolean centered, int txColor, int hoverColor) {
        int bg = hovered ? buttonBackgroundHoverColor : buttonBackgroundColor;
        if (dynamic) {
            buttonW = mc.textRenderer.getWidth(text) + 10;
        } else {
            buttonW = 60;
        }
        context.fill(x, y, x + buttonW, y + buttonH, bg);
        drawInnerBorder(context, x, y, buttonW, buttonH, hovered ? buttonBorderHoverColor : buttonBorderColor);
        drawOuterBorder(context, x, y, buttonW, buttonH, globalBottomBorder);
        int tw = mc.textRenderer.getWidth(text);
        int tx = centered ? x + (buttonW - tw) / 2 : x + 5;
        int ty = y + (buttonH - mc.textRenderer.fontHeight) / 2;
        context.drawText(mc.textRenderer, Text.literal(text), tx, ty, hovered ? hoverColor : txColor, Config.shadow);
    }

    public static void drawInnerBorder(DrawContext context, int x, int y, int buttonW, int buttonH, int i) {
        context.fill(x, y, x + buttonW, y + 1, i);
        context.fill(x, y + buttonH - 1, x + buttonW, y + buttonH, i);
        context.fill(x, y, x + 1, y + buttonH, i);
        context.fill(x + buttonW - 1, y, x + buttonW, y + buttonH, i);
    }

    public static void drawOuterBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x - 1, y - 1, x + width + 1, y, color);
        context.fill(x - 1, y + height, x + width + 1, y + height + 2, color);
        context.fill(x - 1, y, x, y + height, color);
        context.fill(x + width, y, x + width + 1, y + height, color);
    }

    public static class ContextMenu {
        private static int MenuHoverColor = 0xFFd6f264;

        private static class MenuItem {
            String label;
            Runnable action;

            MenuItem(String label, Runnable action) {
                this.label = label;
                this.action = action;
            }
        }

        private static final List<MenuItem> items = new ArrayList<>();
        private static boolean open;
        private static int menuX;
        private static int menuY;
        private static int itemWidth;
        private static int itemHeight = 18;
        private static int gap = 1;

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

        public static void addItem(String label, Runnable action, int HoverColor) {
            items.add(new MenuItem(label, action));
            MenuHoverColor = HoverColor;
        }

        public static boolean isOpen() {
            return open;
        }

        public static void renderMenu(DrawContext context, MinecraftClient mc, int mouseX, int mouseY) {
            if (!open) return;
            int currentY = menuY;
            for (int i = 0; i < items.size(); i++) {
                boolean hovered = mouseX >= menuX && mouseX <= menuX + itemWidth && mouseY >= currentY && mouseY < currentY + itemHeight;
                drawCustomButton(context, menuX, currentY, items.get(i).label, mc, hovered, false, false, buttonTextColor, MenuHoverColor);
                currentY += itemHeight + gap;
            }
        }

        public static boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!open) return false;
            int currentY = menuY;
            for (int i = 0; i < items.size(); i++) {
                boolean hovered = mouseX >= menuX && mouseX <= menuX + itemWidth && mouseY >= currentY && mouseY < currentY + itemHeight;
                if (hovered && button == GLFW.GLFW_MOUSE_BUTTON_1) {
                    items.get(i).action.run();
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
}

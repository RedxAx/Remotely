package redxax.oxy.common;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.common.config.Config;
import redxax.oxy.common.terminal.MultiTerminalScreen;
import redxax.oxy.common.explorer.FileExplorerScreen;
import redxax.oxy.common.explorer.FileEditorScreen;
import redxax.oxy.common.servers.PluginModManagerScreen;
import redxax.oxy.common.util.TabTextAnimator;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import static redxax.oxy.common.config.Config.*;
import static redxax.oxy.common.util.DevUtil.devPrint;
import static redxax.oxy.common.util.ImageUtil.*;

public class Render {

    public static int buttonW = 60;
    public static int buttonH = 18;
    private static TabTextAnimator searchTextAnimator = new TabTextAnimator("", 2, 3);
    private static String previousFieldText = "";
    private static boolean isAnimating = false;
    private static BufferedImage loadingAnim;
    private static final List<BufferedImage> loadingFrames = new ArrayList<>();
    private static int currentLoadingFrame = 0;
    private static long lastFrameTime = 0;
    private static float scrollInterpolation = 0.15f;
    private static float currentScrollOffset = 0f;

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
            int tabWidth;
            if (tab instanceof FileExplorerScreen.Tab) {
                tabWidth = textRenderer.getWidth(name) + 2 * tabPadding;
            } else if (tab instanceof FileEditorScreen.Tab) {
                tabWidth = textRenderer.getWidth(name) + 2 * tabPadding;
            } else if (tab instanceof PluginModManagerScreen.Tab) {
                tabWidth = textRenderer.getWidth(name) + 2 * tabPadding;
            } else if (tab instanceof MultiTerminalScreen.TabInfo) {
                tabWidth = textRenderer.getWidth(name) + 2 * tabPadding;
            } else {
                tabWidth = textRenderer.getWidth(name) + 2 * tabPadding;
            }
            boolean isActive = (i == currentTabIndex);
            int x2 = x + tabWidth;
            boolean isHovered = mouseX >= x && mouseX <= x2 && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight;
            int bgColor = isActive ? isUnsaved ? tabUnsavedBackgroundColor : tabSelectedBackgroundColor : (isHovered ? tabBackgroundHoverColor : tabBackgroundColor);
            context.fill(x, tabBarY, x2, tabBarY + tabBarHeight, bgColor);
            drawInnerBorder(context, x, tabBarY, tabWidth, tabBarHeight, isActive ? isUnsaved ? tabUnsavedBorderColor : tabSelectedBorderColor :  isHovered ? tabBorderHoverColor : tabBorderColor);
            drawOuterBorder(context, x, tabBarY, tabWidth, tabBarHeight, globalBottomBorder);
            context.drawText(textRenderer, Text.literal(name), x + tabPadding, tabBarY + 5, isHovered ? tabTextHoverColor : tabTextColor, shadow);
            x += tabWidth + tabGap;
        }
        if (hasPlus) {
            boolean isPlusTabHovered = mouseX >= x && mouseX <= x + plusTabWidth && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight;
            context.fill(x, tabBarY, x + plusTabWidth, tabBarY + tabBarHeight, isPlusTabHovered ? tabBackgroundHoverColor : tabBackgroundColor);
            drawInnerBorder(context, x, tabBarY, plusTabWidth, tabBarHeight, isPlusTabHovered ? tabBorderHoverColor : tabBorderColor);
            drawOuterBorder(context, x, tabBarY, plusTabWidth, tabBarHeight, globalBottomBorder);
            context.drawText(textRenderer, Text.literal(plusSign), x + plusTabWidth / 2 - textRenderer.getWidth(plusSign) / 2, tabBarY + 5, isPlusTabHovered ? tabTextHoverColor : tabTextColor, shadow);
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
        int searchBarHeight = 18;
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
        context.drawText(textRenderer, Text.literal(displayText), searchBarX + 5 - (int) currentScrollOffset, searchBarY + 5, textColor, shadow);
        if (fieldFocused && showCursor) {
            int cursorPosX = searchBarX + 5 + textRenderer.getWidth(beforeCursor) - (int) currentScrollOffset;
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
        int ty = y + 5;
        context.drawText(mc.textRenderer, Text.literal(text), tx, ty, hovered ? hoverColor : txColor, Config.shadow);
    }

    public static void drawSquareButton(DrawContext context, int x, int y, MinecraftClient mc, String text, boolean hovered, int txColor, int hoverColor) {
        int bg = hovered ? buttonBackgroundHoverColor : buttonBackgroundColor;
        int w = 18;
        int h = 18;
        context.fill(x, y, x + w, y + h, bg);
        drawInnerBorder(context, x, y, w, h, hovered ? buttonBorderHoverColor : buttonBorderColor);
        drawOuterBorder(context, x, y, w, h, globalBottomBorder);
        int tw = mc.textRenderer.getWidth(text);
        int tx = x + (w - tw) / 2;
        int ty = y + 5;
        context.drawText(mc.textRenderer, Text.literal(text), tx, ty, hovered ? hoverColor : txColor, Config.shadow);
    }

    public static void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int i) {
        context.fill(x, y, x + w, y + 1, i);
        context.fill(x, y + h - 1, x + w, y + h, i);
        context.fill(x, y, x + 1, y + h, i);
        context.fill(x + w - 1, y, x + w, y + h, i);
    }

    public static void drawOuterBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x - 1, y - 1, x + w + 1, y, color);
        context.fill(x - 1, y + h, x + w + 1, y + h + 2, color);
        context.fill(x - 1, y, x, y + h, color);
        context.fill(x + w, y, x + w + 1, y + h, color);
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
            for (MenuItem item : items) {
                boolean hovered = mouseX >= menuX && mouseX <= menuX + itemWidth && mouseY >= currentY && mouseY < currentY + itemHeight;
                drawCustomButton(context, menuX, currentY, item.label, mc, hovered, false, false, buttonTextColor, MenuHoverColor);
                currentY += itemHeight + gap;
            }
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

    public static void drawScreenHeader(DrawContext context, int width, int height, int mouseX, int mouseY, Screen parent, MinecraftClient minecraftClient, BufferedImage icon1, BufferedImage icon2, BufferedImage icon3, BufferedImage icon4, BufferedImage icon5, BufferedImage icon6, BufferedImage icon7, BufferedImage icon8) {
        context.fill(0, 0, parent.width, 30, headerBackgroundColor);
        drawInnerBorder(context, 0, 0, parent.width, 30, headerBorderColor);
        drawOuterBorder(context, 0, 0, parent.width, 30, globalBottomBorder);

        if (icon1 != null) {
            boolean isIcon1Hovered = mouseX >= width - 23 && mouseX <= width - 6 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 23, 5, minecraftClient, "", isIcon1Hovered, buttonTextColor, buttonTextCancelColor);
            drawPixelArt(context, icon1, width - 22, 6, 16, 16);
        }
        if (icon2 != null) {
            boolean isIcon2Hovered = mouseX >= width - 46 && mouseX <= width - 29 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 46, 5, minecraftClient, "", isIcon2Hovered, buttonTextColor, buttonTextHoverColor);
            drawPixelArt(context, icon2, width - 45, 6, 16, 16);
        }
        if (icon3 != null) {
            boolean isIcon3Hovered = mouseX >= width - 69 && mouseX <= width - 52 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 69, 5, minecraftClient, "", isIcon3Hovered, buttonTextColor, buttonTextHoverColor);
            drawPixelArt(context, icon3, width - 68, 6, 16, 16);
        }
        if (icon4 != null) {
            boolean isIcon4Hovered = mouseX >= width - 92 && mouseX <= width - 75 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, width - 92, 5, minecraftClient, "", isIcon4Hovered, buttonTextColor, buttonTextHoverColor);
            drawPixelArt(context, icon4, width - 91, 6, 16, 16);
        }

        if (icon5 != null) {
            boolean isIcon5Hovered = mouseX >= 5 && mouseX <= 22 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 5, 5, minecraftClient, "", isIcon5Hovered, buttonTextColor, buttonTextHoverColor);
            drawPixelArt(context, icon5, 6, 6, 16, 16);
        }
        if (icon6 != null) {
            boolean isIcon6Hovered = mouseX >= 28 && mouseX <= 45 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 28, 5, minecraftClient, "", isIcon6Hovered, buttonTextColor, buttonTextHoverColor);
            drawPixelArt(context, icon6, 29, 6, 16, 16);
        }
        if (icon7 != null) {
            boolean isIcon7Hovered = mouseX >= 51 && mouseX <= 68 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 51, 5, minecraftClient, "", isIcon7Hovered, buttonTextColor, buttonTextHoverColor);
            drawPixelArt(context, icon7, 52, 6, 16, 16);
        }
        if (icon8 != null) {
            boolean isIcon8Hovered = mouseX >= 74 && mouseX <= 91 && mouseY >= 6 && mouseY <= 24;
            drawSquareButton(context, 74, 5, minecraftClient, "", isIcon8Hovered, buttonTextColor, buttonTextHoverColor);
            drawPixelArt(context, icon8, 75, 6, 16, 16);
        }
    }

    public static void drawToggle(DrawContext context, MinecraftClient mc, int x, int y, String label, boolean value, boolean hovered) {
        int trackWidth = 40;
        int trackHeight = 20;
        int trackColor = value ? buttonTextHoverColor : hovered ? buttonBackgroundHoverColor : buttonBackgroundColor;
        context.fill(x, y, x + trackWidth, y + trackHeight, trackColor);
        context.fill(x, y + (int)(trackHeight * 0.75), x + trackWidth, y + trackHeight, 0x20000000);
        drawInnerBorder(context, x, y, trackWidth, trackHeight, hovered ? buttonBorderHoverColor : buttonBorderColor);
        drawOuterBorder(context, x, y, trackWidth, trackHeight, globalBottomBorder);
        int knobDiameter = trackHeight - 4;
        int knobX = value ? x + trackWidth - knobDiameter - 2 : x + 2;
        int knobY = y + 2;
        context.fill(knobX, knobY, knobX + knobDiameter, knobY + knobDiameter, serverElementBackgroundColor);
        drawInnerBorder(context, knobX, knobY, knobDiameter, knobDiameter, serverElementBorderColor);
    }

    public static void drawSlider(DrawContext context, MinecraftClient mc, int x, int y, String label, int currentValue, int minValue, int maxValue, boolean hovered) {
        int sliderWidth = 180;
        int sliderHeight = 18;
        context.drawText(mc.textRenderer, Text.literal(label), x, y + 3, screensTitleTextColor, false);
        int bg = hovered ? buttonBackgroundHoverColor : buttonBackgroundColor;
        context.fill(x, y, x + sliderWidth, y + sliderHeight, bg);
        drawInnerBorder(context, x, y, sliderWidth, sliderHeight, hovered ? buttonBorderHoverColor : buttonBorderColor);
        drawOuterBorder(context, x, y, sliderWidth, sliderHeight, globalBottomBorder);
        float ratio = (float)(currentValue - minValue) / (float)(maxValue - minValue);
        int fillWidth = (int)(ratio * (sliderWidth - 2));
        context.fill(x + 1, y + 1, x + 1 + fillWidth, y + sliderHeight - 1, buttonTextHoverColor);
        context.fill(x + 1, y + sliderHeight - (int)(sliderHeight * 0.25), x + 1 + fillWidth, y + sliderHeight - 1, 0x20000000);
        String text = String.valueOf(currentValue);
        int tw = mc.textRenderer.getWidth(text);
        int tx = x + (sliderWidth - tw) / 2;
        int ty = y + 5;
        context.drawText(mc.textRenderer, Text.literal(text), tx, ty, buttonTextColor, Config.shadow);
    }

    public static void drawDropDown(DrawContext context, MinecraftClient mc, int x, int y, String label, List<String> options, int selectedIndex, boolean expanded, boolean hovered) {
        int ddWidth = 180;
        int ddHeight = 18;
        context.drawText(mc.textRenderer, Text.literal(label), x, y + 3, screensTitleTextColor, false);
        int bg = hovered ? buttonBackgroundHoverColor : buttonBackgroundColor;
        context.fill(x, y, x + ddWidth, y + ddHeight, bg);
        drawInnerBorder(context, x, y, ddWidth, ddHeight, hovered ? buttonBorderHoverColor : buttonBorderColor);
        drawOuterBorder(context, x, y, ddWidth, ddHeight, globalBottomBorder);
        int tx = x + 3;
        int ty = y + 5;
        if (selectedIndex >= 0 && selectedIndex < options.size()) {
            String text = options.get(selectedIndex);
            context.drawText(mc.textRenderer, Text.literal(text), tx, ty, buttonTextColor, shadow);
        }
        int arrowX = x + ddWidth - 10;
        context.drawText(mc.textRenderer, Text.literal("v"), arrowX, ty, buttonTextColor, shadow);
        if (expanded) {
            int expandHeight = options.size() * ddHeight;
            context.fill(x, y + ddHeight, x + ddWidth, y + ddHeight + expandHeight, bg);
            for (int i = 0; i < options.size(); i++) {
                int optY = y + ddHeight + i * ddHeight;
                context.drawText(mc.textRenderer, Text.literal(options.get(i)), x + 3, optY + 3, buttonTextColor, shadow);
            }
            drawInnerBorder(context, x, y + ddHeight, ddWidth, expandHeight, hovered ? buttonBorderHoverColor : buttonBorderColor);
            drawOuterBorder(context, x, y + ddHeight, ddWidth, expandHeight, globalBottomBorder);
        }
    }

    public static void drawTabSwitch(DrawContext context, MinecraftClient mc, int x, int y, String label, List<String> options, int currentIndex, int mouseX, int mouseY) {
        context.drawText(mc.textRenderer, Text.literal(label), x, y + 3, screensTitleTextColor, false);
        int barWidth = 180;
        int barHeight = 18;
        context.fill(x, y, x + barWidth, y + barHeight, tabBackgroundColor);
        drawInnerBorder(context, x, y, barWidth, barHeight, tabBorderColor);
        drawOuterBorder(context, x, y, barWidth, barHeight, globalBottomBorder);
        int segmentCount = options.size();
        int segmentWidth = barWidth / segmentCount;
        for (int i = 0; i < segmentCount; i++) {
            int segX = x + i * segmentWidth;
            int segY = y;
            int segW = (i == segmentCount - 1) ? (x + barWidth - segX) : segmentWidth;
            boolean hovered = mouseX >= segX && mouseX < segX + segW && mouseY >= segY && mouseY < segY + barHeight;
            boolean selected = i == currentIndex;
            int color = selected ? tabSelectedBackgroundColor : hovered ? tabBackgroundHoverColor : tabBackgroundColor;
            context.fill(segX, segY, segX + segW, segY + barHeight, color);
            drawInnerBorder(context, segX, segY, segW, barHeight, selected ? tabSelectedBorderColor : hovered ? tabBorderHoverColor : tabBorderColor);
            int textWidth = mc.textRenderer.getWidth(options.get(i));
            int textX = segX + (segW - textWidth) / 2;
            int textY = (segY + (barHeight - mc.textRenderer.fontHeight) / 2) +1;
            context.drawText(mc.textRenderer, Text.literal(options.get(i)), textX, textY, tabTextColor, shadow);
        }
    }

    public static void drawTextInput(DrawContext context, MinecraftClient mc, int x, int y, String label, String textValue, boolean focused, int cursorPos, int selectionStart, int selectionEnd, boolean hovered, float currentScrollOffset, float targetScrollOffset) {
        int inputWidth = 180;
        int inputHeight = 18;
        context.drawText(mc.textRenderer, Text.literal(label), x, y + 3, screensTitleTextColor, false);
        int bg = hovered ? buttonBackgroundHoverColor : buttonBackgroundColor;
        context.fill(x, y, x + inputWidth, y + inputHeight, bg);
        drawInnerBorder(context, x, y, inputWidth, inputHeight, hovered ? buttonBorderHoverColor : buttonBorderColor);
        drawOuterBorder(context, x, y, inputWidth, inputHeight, globalBottomBorder);

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
            context.drawText(mc.textRenderer, Text.literal("Type..."), x + 5, y + 5, serverElementTextDimColor, false);
        } else {
            int selStart = Math.min(selectionStart, selectionEnd);
            int selEnd = Math.max(selectionStart, selectionEnd);
            if (focused && selStart != selEnd) {
                String textBeforeSel = selStart <= textValue.length() ? textValue.substring(0, selStart) : textValue;
                int selStartX = x + 5 + mc.textRenderer.getWidth(textBeforeSel) - (int)scrollOffset;
                String selectedText = selEnd <= textValue.length() ? textValue.substring(selStart, selEnd) : "";
                int selWidth = mc.textRenderer.getWidth(selectedText);
                context.fill(selStartX, y + 4, selStartX + selWidth, y + 4 + mc.textRenderer.fontHeight, terminalSelectionColor);
            }
            context.drawText(mc.textRenderer, Text.literal(textValue), x + 5 - (int)scrollOffset, y + 5, buttonTextColor, false);
        }

        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorPosX = x + 5 + mc.textRenderer.getWidth(beforeCursor) - (int)scrollOffset;
            context.fill(cursorPosX, y + 4, cursorPosX + 1, y + 4 + mc.textRenderer.fontHeight, buttonTextColor);
        }
        context.disableScissor();
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

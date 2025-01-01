package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class MultiTerminalScreen extends Screen {

    private final MinecraftClient minecraftClient;
    private final RemotelyClient remotelyClient;
    final List<TerminalInstance> terminals;
    final List<String> tabNames;
    int activeTerminalIndex = 0;

    public static final int TAB_HEIGHT = 25;

    private float scale = 1.0f;
    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 2.0f;

    boolean isRenaming = false;
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
    boolean isResizingSnippetPanel = false;
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
    boolean snippetCursorVisible = true;
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
    boolean hideButtonHovered = false;
    boolean plusButtonHovered = false;
    int hoveredTabIndex = -1;
    boolean createSnippetButtonHovered = false;
    int selectedSnippetIndex = -1;
    int snippetCommandsScrollOffset = 0;
    int snippetMaxVisibleLines = 1;
    private boolean shortcutConsumed = false;

    int baseColor = 0xFF181818;
    int lighterColor = 0xFF222222;
    int borderColor = 0xFF333333;
    int highlightColor = 0xFF444444;
    int textColor = 0xFFFFFFFF;
    int dimTextColor = 0xFFBBBBBB;

    int renameScrollOffset = 0;
    int snippetNameScrollOffset = 0;

    public MultiTerminalScreen(MinecraftClient minecraftClient, RemotelyClient remotelyClient, List<TerminalInstance> terminals, List<String> tabNames) {
        super(Text.literal("Multi Terminal"));
        this.minecraftClient = minecraftClient;
        this.remotelyClient = remotelyClient;
        this.terminals = terminals;
        this.tabNames = tabNames;
        if (terminals.isEmpty()) {
            addNewTerminal();
        }
        this.activeTerminalIndex = remotelyClient.activeTerminalIndex;
        this.scale = remotelyClient.scale;
        this.snippetPanelWidth = remotelyClient.snippetPanelWidth;
        this.showSnippetsPanel = remotelyClient.showSnippetsPanel;
        this.snippetLastBlinkTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
    }

    private void addNewTerminal() {
        UUID terminalId = UUID.randomUUID();
        TerminalInstance newTerminal = new TerminalInstance(minecraftClient, this, terminalId);
        terminals.add(newTerminal);
        tabNames.add("Tab " + terminals.size());
        activeTerminalIndex = terminals.size() - 1;
    }

    private void closeTerminal(int index) {
        if (terminals.size() <= 1) {
            this.close();
        }
        TerminalInstance terminal = terminals.get(index);
        terminal.shutdown();
        terminals.remove(index);
        tabNames.remove(index);
        if (activeTerminalIndex >= terminals.size()) {
            activeTerminalIndex = terminals.size() - 1;
        }
    }

    private void setActiveTerminal(int index) {
        if (index >= 0 && index < terminals.size()) {
            activeTerminalIndex = index;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - snippetLastBlinkTime > 500) {
            snippetCursorVisible = !snippetCursorVisible;
            snippetLastBlinkTime = currentTime;
        }

        super.render(context, mouseX, mouseY, delta);

        if (!warningMessage.isEmpty()) {
            context.drawText(minecraftClient.textRenderer, Text.literal(warningMessage), 5, TAB_HEIGHT + verticalPadding, 0xFFFF0000, false);
            warningMessage = "";
        }

        int hideButtonX = this.width - 15 - 5;
        int hideButtonY = 5;
        hideButtonHovered = mouseX >= hideButtonX && mouseX <= hideButtonX + 15 && mouseY >= hideButtonY && mouseY <= hideButtonY + 15;
        drawHoverableButton(context, hideButtonX, hideButtonY, "≡", hideButtonHovered, textColor);

        int availableTabWidth = this.width - (showSnippetsPanel ? snippetPanelWidth : 0) - 15 - 20;
        int tabStartX = 5;
        int tabY = 5;
        int tabAreaHeight = TAB_HEIGHT;
        int contentYStart = tabY + tabAreaHeight + verticalPadding;
        int contentXStart = 2;
        int effectiveWidth = this.width - (showSnippetsPanel ? snippetPanelWidth : 0) - contentXStart - 2;

        List<TabInfo> tabInfos = new ArrayList<>();
        for (int i = 0; i < terminals.size(); i++) {
            String tName = tabNames.get(i);
            int tw = minecraftClient.textRenderer.getWidth(tName);
            int paddingH = 10;
            int tabW = Math.max(tw + paddingH * 2, 60);
            tabInfos.add(new TabInfo(tName, tabW));
        }

        int totalTabsWidth = 0;
        for (TabInfo ti : tabInfos) totalTabsWidth += ti.width + tabPadding;
        if (totalTabsWidth < availableTabWidth) totalTabsWidth = availableTabWidth;
        tabScrollOffset = MathHelper.clamp(tabScrollOffset, 0, Math.max(0, totalTabsWidth - availableTabWidth));

        int plusW = 20;
        float renderX = tabStartX - tabScrollOffset;
        hoveredTabIndex = -1;
        for (int i = 0; i < tabInfos.size(); i++) {
            TabInfo ti = tabInfos.get(i);
            int baseC = (i == activeTerminalIndex) ? highlightColor : lighterColor;
            boolean tabHovered = mouseX >= renderX && mouseX <= renderX + ti.width && mouseY >= tabY && mouseY <= tabY + tabAreaHeight;
            if (tabHovered) hoveredTabIndex = i;
            int bgColor = tabHovered ? 0xFF555555 : baseC;
            context.fill((int) renderX, tabY, (int) renderX + ti.width, tabY + tabAreaHeight, bgColor);
            drawInnerBorder(context, (int) renderX, tabY, ti.width, tabAreaHeight, borderColor);
            String displayName = ti.name;
            int tx;
            int ty = tabY + (tabAreaHeight - minecraftClient.textRenderer.fontHeight) / 2;
            if (isRenaming && renamingTabIndex == i) {
                String fullText = renameBuffer.toString();
                int wBeforeCursor = minecraftClient.textRenderer.getWidth(fullText.substring(0, Math.min(renameCursorPos, fullText.length())));
                if (wBeforeCursor < renameScrollOffset) renameScrollOffset = wBeforeCursor;
                int maxTabTextWidth = ti.width - 10;
                if (wBeforeCursor - renameScrollOffset > maxTabTextWidth) renameScrollOffset = wBeforeCursor - maxTabTextWidth;
                if (renameScrollOffset < 0) renameScrollOffset = 0;
                int charStart = 0;
                while (charStart < fullText.length()) {
                    int cw = minecraftClient.textRenderer.getWidth(fullText.substring(0, charStart));
                    if (cw >= renameScrollOffset) break;
                    charStart++;
                }
                int visibleEnd = charStart;
                while (visibleEnd <= fullText.length()) {
                    int cw = minecraftClient.textRenderer.getWidth(fullText.substring(charStart, visibleEnd));
                    if (cw > maxTabTextWidth) break;
                    visibleEnd++;
                }
                visibleEnd--;
                if (visibleEnd < charStart) visibleEnd = charStart;
                displayName = fullText.substring(charStart, visibleEnd);
                tx = (int) renderX + 5;
                context.drawText(minecraftClient.textRenderer, Text.literal(displayName), tx, ty, textColor, false);
                long cTime = System.currentTimeMillis();
                boolean cursorShow = ((cTime - lastRenameInputTime) < 500) || (((cTime - lastRenameInputTime) > 1000) && ((cTime - lastRenameInputTime) < 1500));
                if ((cTime - lastRenameInputTime) > 1000) {
                    if ((cTime - lastRenameInputTime) > 1500) {
                        lastRenameInputTime = cTime;
                    }
                }
                if (cursorShow) {
                    int cursorPosVisible = Math.min(renameCursorPos - charStart, displayName.length());
                    if (cursorPosVisible < 0) cursorPosVisible = 0;
                    int renameCursorX = tx + minecraftClient.textRenderer.getWidth(displayName.substring(0, Math.min(cursorPosVisible, displayName.length())));
                    context.fill(renameCursorX, ty - 1, renameCursorX + 1, ty + minecraftClient.textRenderer.fontHeight, textColor);
                }
            } else {
                int textW = minecraftClient.textRenderer.getWidth(displayName);
                tx = (int) renderX + (ti.width - textW) / 2;
                context.drawText(minecraftClient.textRenderer, Text.literal(displayName), tx, ty, textColor, false);
            }
            renderX += ti.width + tabPadding;
        }

        plusButtonHovered = mouseX >= renderX && mouseX <= renderX + plusW && mouseY >= tabY && mouseY <= tabY + tabAreaHeight;
        int plusBg = plusButtonHovered ? 0xFF666666 : 0xFF555555;
        context.fill((int) renderX, tabY, (int) renderX + plusW, tabY + tabAreaHeight, plusBg);
        drawInnerBorder(context, (int) renderX, tabY, plusW, tabAreaHeight, borderColor);
        String plus = "+";
        int pw = minecraftClient.textRenderer.getWidth(plus);
        int ptx = (int) renderX + (plusW - pw) / 2;
        int pty = tabY + (tabAreaHeight - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(minecraftClient.textRenderer, Text.literal(plus), ptx, pty, textColor, false);

        if (tabScrollOffset > 0) {
            drawFade(context, 5, tabY, 15, tabY + tabAreaHeight, true);
        }
        if (totalTabsWidth - tabScrollOffset > availableTabWidth) {
            drawFade(context, this.width - (showSnippetsPanel ? snippetPanelWidth : 0) - 15 - 15, tabY, this.width - (showSnippetsPanel ? snippetPanelWidth : 0) - 15 - 5, tabY + tabAreaHeight, false);
        }

        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            activeTerminal.render(context, Math.max(effectiveWidth, 50), this.height, scale);
        }

        if (showSnippetsPanel) {
            int panelX = this.width - snippetPanelWidth;
            int panelHeight = this.height - contentYStart - 5;
            context.fill(panelX - 1, contentYStart, panelX, contentYStart + panelHeight, borderColor);
            context.fill(panelX, contentYStart, panelX + snippetPanelWidth, contentYStart + panelHeight, lighterColor);
            drawInnerBorder(context, panelX, contentYStart, snippetPanelWidth, panelHeight, borderColor);
            int yOffset = contentYStart + 5;
            int snippetMaxWidth = snippetPanelWidth - 10;
            snippetHoverIndex = -1;
            for (int i = 0; i < RemotelyClient.globalSnippets.size(); i++) {
                RemotelyClient.CommandSnippet snippet = RemotelyClient.globalSnippets.get(i);
                boolean hovered = (mouseX >= panelX + 5 && mouseX <= panelX + snippetMaxWidth && mouseY >= yOffset && mouseY <= yOffset + 35);
                if (hovered) snippetHoverIndex = i;
                boolean selected = selectedSnippetIndex == i;
                renderSnippetBox(context, panelX + 5, yOffset, snippetMaxWidth, snippet, hovered, selected, i);
                yOffset += selected ? calculateSnippetHeight(snippet.commands) : 35;
                yOffset += 5;
            }
            String createText = "+ Snippet";
            int ctw = minecraftClient.textRenderer.getWidth(createText) + 10;
            int createButtonWidth = Math.min(ctw, Math.max(50, snippetPanelWidth - 10));
            int createButtonX = panelX + (snippetPanelWidth - createButtonWidth) / 2;
            int createButtonY = this.height - 5 - 20;
            createSnippetButtonHovered = mouseX >= createButtonX && mouseX <= createButtonX + createButtonWidth && mouseY >= createButtonY && mouseY <= createButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawHoverableButton(context, createButtonX, createButtonY, createText, createSnippetButtonHovered, textColor);
        }

        if (snippetPopupActive) {
            renderSnippetPopup(context, mouseX, mouseY, delta);
        }
    }

    private void renderSnippetPopup(DrawContext context, int mouseX, int mouseY, float delta) {
        if (snippetPopupX + snippetPopupWidth > this.width) snippetPopupX = this.width - snippetPopupWidth - 5;
        if (snippetPopupY + snippetPopupHeight > this.height) snippetPopupY = this.height - snippetPopupHeight - 5;
        context.fill(snippetPopupX, snippetPopupY, snippetPopupX + snippetPopupWidth, snippetPopupY + snippetPopupHeight, baseColor);
        drawInnerBorder(context, snippetPopupX, snippetPopupY, snippetPopupWidth, snippetPopupHeight, borderColor);

        int nameLabelY = snippetPopupY + 5;
        trimAndDrawText(context, "Name:", snippetPopupX + 5, nameLabelY, snippetPopupWidth - 10, textColor);
        int nameBoxY = nameLabelY + 12;
        int nameBoxHeight = 12;
        int nameBoxWidth = snippetPopupWidth - 10;
        context.fill(snippetPopupX + 5, nameBoxY, snippetPopupX + 5 + nameBoxWidth, nameBoxY + nameBoxHeight, snippetNameFocused ? 0xFF444466 : 0xFF333333);
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
        context.drawText(minecraftClient.textRenderer, Text.literal(visibleName), nameTextX, nameTextY, textColor, false);
        if (snippetNameFocused && snippetCursorVisible) {
            int cursorPosVisible = Math.min(snippetNameCursorPos - charStart, visibleName.length());
            if (cursorPosVisible < 0) cursorPosVisible = 0;
            int cX = nameTextX + minecraftClient.textRenderer.getWidth(visibleName.substring(0, Math.min(cursorPosVisible, visibleName.length())));
            context.fill(cX, nameTextY - 1, cX + 1, nameTextY + minecraftClient.textRenderer.fontHeight, textColor);
        }

        int commandsLabelY = nameBoxY + nameBoxHeight + 8;
        trimAndDrawText(context, "Commands:", snippetPopupX + 5, commandsLabelY, snippetPopupWidth - 10, textColor);
        int commandsBoxY = commandsLabelY + 12;
        int commandsBoxHeight = snippetPopupHeight - (commandsBoxY - snippetPopupY) - 60;
        if (commandsBoxHeight < 20) commandsBoxHeight = 20;
        int commandsBoxWidth = snippetPopupWidth - 10;
        context.fill(snippetPopupX + 5, commandsBoxY, snippetPopupX + 5 + commandsBoxWidth, commandsBoxY + commandsBoxHeight, !snippetNameFocused ? 0xFF444466 : 0xFF333333);
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
            context.drawText(minecraftClient.textRenderer, Text.literal(visibleCmdLines.get(i)), commandsInnerX, commandsInnerY + i * (minecraftClient.textRenderer.fontHeight + 2), textColor, false);
        }
        if (!snippetNameFocused && snippetCursorVisible) {
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
            context.fill(cursorX, cursorY - 1, cursorX + 1, cursorY + minecraftClient.textRenderer.fontHeight, textColor);
        }

        int shortcutLabelY = commandsBoxY + commandsBoxHeight + 8;
        trimAndDrawText(context, snippetRecordingKeys ? "Shortcut (recording):" : "Shortcut:", snippetPopupX + 5, shortcutLabelY, snippetPopupWidth - 10, textColor);
        int shortcutBoxY = shortcutLabelY + 12;
        int shortcutBoxHight = 12;
        int shortcutBoxWidth = snippetPopupWidth - 10;
        context.fill(snippetPopupX + 5, shortcutBoxY, snippetPopupX + 5 + shortcutBoxWidth, shortcutBoxY + shortcutBoxHight, 0xFF333333);
        String shortcutText = snippetShortcutBuffer.isEmpty() ? "No Shortcut" : snippetShortcutBuffer.toString();
        shortcutText = trimTextToWidthWithEllipsis(shortcutText, shortcutBoxWidth - 2);
        context.drawText(minecraftClient.textRenderer, Text.literal(shortcutText), snippetPopupX + 8, shortcutBoxY + 2, textColor, false);

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
        trimAndDrawText(context, recordText, rtx, rty, shortcutBoxHight, textColor);

        int confirmButtonY = snippetPopupY + snippetPopupHeight - 15;
        String okText = "OK";
        int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
        int confirmButtonX = snippetPopupX + 5;
        boolean okHover = mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHoverableButton(context, confirmButtonX, confirmButtonY, okText, okHover, textColor);

        String cancelText = "Cancel";
        int cancelW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
        int cancelButtonX = snippetPopupX + snippetPopupWidth - (cancelW + 5);
        boolean cancelHover = mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHoverableButton(context, cancelButtonX, confirmButtonY, cancelText, cancelHover, textColor);

        if (editingSnippet) {
            String deleteText = "Delete";
            int dw = minecraftClient.textRenderer.getWidth(deleteText) + 10;
            int deleteX = snippetPopupX + (snippetPopupWidth - dw) / 2;
            boolean delHover = mouseX >= deleteX && mouseX <= deleteX + dw && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawHoverableButton(context, deleteX, confirmButtonY, deleteText, delHover, 0xFFFF0000);
        }

        if (snippetCreationWarning) {
            String warning = "Name/Code cannot be empty";
            int ww = minecraftClient.textRenderer.getWidth(warning);
            trimAndDrawText(context, warning, snippetPopupX + (snippetPopupWidth - ww) / 2, snippetPopupY + snippetPopupHeight - 30, snippetPopupWidth - 10, 0xFFFF0000);
        }
    }


    private void renderSnippetBox(DrawContext context, int snippetX, int snippetY, int snippetMaxWidth, RemotelyClient.CommandSnippet snippet, boolean hovered, boolean selected, int index) {
        int snippetHeight = selected ? calculateSnippetHeight(snippet.commands) : 30;
        int bgColor = hovered ? highlightColor : 0xFF444444;
        if (lastClickedSnippet == index && (System.currentTimeMillis() - lastSnippetClickTime) < 500) {
            bgColor = 0xFF666666;
        }
        context.fill(snippetX, snippetY, snippetX + snippetMaxWidth, snippetY + snippetHeight, bgColor);
        drawInnerBorder(context, snippetX, snippetY, snippetMaxWidth, snippetHeight, borderColor);

        String displayName = trimTextToWidthWithEllipsis(snippet.name, snippetMaxWidth - 10);
        context.drawText(minecraftClient.textRenderer, Text.literal(displayName), snippetX + 5, snippetY + 5, textColor, false);

        context.fill(snippetX + 5, snippetY + 5 + minecraftClient.textRenderer.fontHeight + 1, snippetX + snippetMaxWidth - 5, snippetY + 5 + minecraftClient.textRenderer.fontHeight + 2, borderColor);

        if (selected) {
            String[] allLines = snippet.commands.split("\n");
            int lineY = snippetY + 5 + minecraftClient.textRenderer.fontHeight + 5;
            for (String line : allLines) {
                if (!line.trim().isEmpty()) {
                    String tl = trimTextToWidthWithEllipsis(line, snippetMaxWidth - 10);
                    context.drawText(minecraftClient.textRenderer, Text.literal(tl), snippetX + 5, lineY, dimTextColor, false);
                    lineY += minecraftClient.textRenderer.fontHeight + 2;
                }
            }
        } else {
            String firstLine = snippet.commands.split("\n")[0];
            firstLine = trimTextToWidthWithEllipsis(firstLine, snippetMaxWidth - 10);
            context.drawText(minecraftClient.textRenderer, Text.literal(firstLine), snippetX + 5, snippetY + 20, dimTextColor, false);
        }
    }

    private int calculateSnippetHeight(String commands) {
        String[] lines = commands.split("\n");
        int nonEmptyLines = 0;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                nonEmptyLines++;
            }
        }
        return 1 + (minecraftClient.textRenderer.fontHeight * 2) + (nonEmptyLines * (minecraftClient.textRenderer.fontHeight + 2));
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int hideButtonX = this.width - 15 - 5;
        int hideButtonY = 5;
        if (mouseX >= hideButtonX && mouseX <= hideButtonX+15 && mouseY >= hideButtonY && mouseY <= hideButtonY+15 && button==0) {
            showSnippetsPanel = !showSnippetsPanel;
            return true;
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
            if (mouseX >= recordX && mouseX <= recordX+rbSize && mouseY>= shortcutBoxY && mouseY<= shortcutBoxY +rbSize && button==0) {
                snippetRecordingKeys = !snippetRecordingKeys;
                snippetShortcutBuffer.setLength(0);
                return true;
            }
            if (editingSnippet && button == 0 && mouseX >= deleteX && mouseX <= deleteX + dw && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight) {
                if (editingSnippetIndex >= 0 && editingSnippetIndex < RemotelyClient.globalSnippets.size()) {
                    RemotelyClient.globalSnippets.remove(editingSnippetIndex);
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
                if (mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && button==0) {
                    if (snippetNameBuffer.toString().trim().isEmpty() || snippetCommandsBuffer.toString().trim().isEmpty()) {
                        snippetCreationWarning = true;
                        return true;
                    }
                    if (creatingSnippet) {
                        RemotelyClient.globalSnippets.add(new RemotelyClient.CommandSnippet(snippetNameBuffer.toString().trim(), snippetCommandsBuffer.toString().trim(), snippetShortcutBuffer.toString().trim()));
                        remotelyClient.saveSnippets();
                    }
                    if (editingSnippet && editingSnippetIndex >= 0 && editingSnippetIndex < RemotelyClient.globalSnippets.size()) {
                        RemotelyClient.CommandSnippet s = RemotelyClient.globalSnippets.get(editingSnippetIndex);
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
                if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + (minecraftClient.textRenderer.getWidth("Cancel")+10) && button==0) {
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
            if (mouseX >= snippetPopupX + 5 && mouseX <= snippetPopupX + snippetPopupWidth - 5 && mouseY >= nameBoxY && mouseY <= nameBoxY + nameBoxHeight && button==0) {
                snippetNameFocused = true;
                snippetCommandsCursorPos = Math.min(snippetCommandsCursorPos, snippetCommandsBuffer.length());
                snippetNameCursorPos = Math.min(snippetNameCursorPos, snippetNameBuffer.length());
                int cx = (int)(mouseX-(snippetPopupX+8));
                snippetNameCursorPos = getCursorFromMouseX(snippetNameBuffer.toString(), cx);
                return true;
            }
            if (mouseX >= snippetPopupX + 5 && mouseX <= snippetPopupX + snippetPopupWidth - 5 && mouseY >= commandsBoxY && mouseY <= commandsBoxY + commandsBoxHeight && button==0) {
                snippetNameFocused = false;
                snippetCommandsCursorPos = 0;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        for (int i = 0; i < terminals.size(); i++) {
            String tName = tabNames.get(i);
            minecraftClient.textRenderer.getWidth(tName);
        }
        int plusW = 20;
        int tabY = 5;
        int tabAreaHeight = TAB_HEIGHT;
        float renderX = 5 - tabScrollOffset;
        for (int i = 0; i < terminals.size(); i++) {
            String tName = tabNames.get(i);
            int tw = minecraftClient.textRenderer.getWidth(tName);
            int paddingH = 10;
            int tabW = Math.max(tw + paddingH * 2, 60);
            if (mouseX >= renderX && mouseX <= renderX + tabW && mouseY >= tabY && mouseY <= tabY + tabAreaHeight) {
                if (button == 1) {
                    isRenaming = true;
                    renamingTabIndex = i;
                    renameBuffer.setLength(0);
                    renameBuffer.append(tabNames.get(i));
                    renameCursorPos = renameBuffer.length();
                    lastRenameInputTime = System.currentTimeMillis();
                    return true;
                } else if (button == 2) {
                    closeTerminal(i);
                    return true;
                } else if (button == 0) {
                    if (!isRenaming) {
                        setActiveTerminal(i);
                        return true;
                    }
                }
            }
            renderX += tabW + tabPadding;
        }
        if (mouseX >= renderX && mouseX <= renderX + plusW && mouseY >= tabY && mouseY <= tabY + tabAreaHeight && button == 0) {
            addNewTerminal();
            return true;
        }
        if (showSnippetsPanel) {
            int panelX = this.width - snippetPanelWidth;
            int panelY = tabY + tabAreaHeight + verticalPadding;
            int panelHeight = this.height - panelY - 5;
            if (Math.abs(mouseX - (panelX - 1)) < 5 && mouseY >= panelY && mouseY <= panelY + panelHeight && button == 0) {
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
            int yOffset = panelY + 5;
            if (selectedSnippetIndex == -1) {
                yOffset += minecraftClient.textRenderer.fontHeight + 5;
            }
            int snippetMaxWidth = snippetPanelWidth - 10;
            for (int i = 0; i < RemotelyClient.globalSnippets.size(); i++) {
                RemotelyClient.CommandSnippet snippet = RemotelyClient.globalSnippets.get(i);
                int snippetHeight = selectedSnippetIndex == i ? calculateSnippetHeight(snippet.commands) : 35;
                int snippetX = panelX + 5;
                int snippetY = yOffset;
                boolean hovered = (mouseX >= snippetX && mouseX <= snippetX + snippetMaxWidth && mouseY >= snippetY && mouseY <= snippetY + snippetHeight);
                if (hovered) {
                    if (button == 1) {
                        editingSnippet = true;
                        editingSnippetIndex = i;
                        RemotelyClient.CommandSnippet s = RemotelyClient.globalSnippets.get(i);
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
                        if (lastClickedSnippet == i && (System.currentTimeMillis() - lastSnippetClickTime) < 500) {
                            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                            String[] lines = RemotelyClient.globalSnippets.get(i).commands.split("\n");
                            for (String line : lines) {
                                if (!line.trim().isEmpty()) {
                                    try {
                                        activeTerminal.getInputHandler().commandExecutor.executeCommand(line.trim(), new StringBuilder(line.trim()));
                                    } catch (IOException e) {
                                        activeTerminal.appendOutput("ERROR: " + e.getMessage() + "\n");
                                    }
                                }
                            }
                            lastClickedSnippet = -1;
                            return true;
                        } else {
                            lastClickedSnippet = i;
                            lastSnippetClickTime = System.currentTimeMillis();
                            selectedSnippetIndex = i;
                            return true;
                        }
                    }
                }
                if (selectedSnippetIndex == i) {
                    yOffset += calculateSnippetHeight(snippet.commands) + 5;
                } else {
                    yOffset += 35 + 5;
                }
            }
            String createText = "+ Snippet";
            int ctw = minecraftClient.textRenderer.getWidth(createText) + 10;
            int createButtonWidth = Math.min(ctw, Math.max(50, snippetPanelWidth - 10));
            int createButtonX = panelX + (snippetPanelWidth - createButtonWidth) / 2;
            int createButtonY = this.height - 5 - 20;
            if (mouseX >= createButtonX && mouseX <= createButtonX + createButtonWidth && mouseY >= createButtonY && mouseY <= createButtonY + 10 + minecraftClient.textRenderer.fontHeight && button == 0) {
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
            }
        } else {
            if (!terminals.isEmpty()) {
                TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                if (activeTerminal.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            if (activeTerminal.mouseDragged(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (isResizingSnippetPanel && button == 0) {
            int newWidth = this.width - (int)mouseX;
            snippetPanelWidth = Math.max(80, Math.min(newWidth, this.width - 50));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean ctrlHeld = InputUtil.isKeyPressed(
                this.minecraftClient.getWindow().getHandle(),
                GLFW.GLFW_KEY_LEFT_CONTROL) ||
                InputUtil.isKeyPressed(this.minecraftClient.getWindow().getHandle(),
                        GLFW.GLFW_KEY_RIGHT_CONTROL);
        int availableTabWidth = this.width - (showSnippetsPanel ? snippetPanelWidth : 0) - 15 - 20;
        int totalTabsWidth=0;
        for (int i=0;i<terminals.size();i++) {
            String tName = tabNames.get(i);
            int tw = minecraftClient.textRenderer.getWidth(tName);
            int paddingH = 10;
            int tabW = Math.max(tw+paddingH*2,60);
            totalTabsWidth += tabW + tabPadding;
        }
        if (totalTabsWidth < availableTabWidth) totalTabsWidth=availableTabWidth;
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
                if (mouseY >= commandsBoxY && mouseY <= commandsBoxY+commandsBoxHeight) {
                    snippetCommandsScrollOffset -= (int)verticalAmount;
                    return true;
                }
                return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
            if (mouseY <= TAB_HEIGHT+10) {
                tabScrollOffset -= (float) (verticalAmount*20);
                tabScrollOffset = MathHelper.clamp(tabScrollOffset,0,Math.max(0,totalTabsWidth-availableTabWidth));
                return true;
            }
            if (!terminals.isEmpty()) {
                TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                activeTerminal.scroll((int) verticalAmount, this.height - (TAB_HEIGHT + verticalPadding) - 5);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (snippetRecordingKeys) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                snippetShortcutBuffer.setLength(0);
                snippetRecordingKeys = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!snippetShortcutBuffer.isEmpty()) {
                    int idx = snippetShortcutBuffer.lastIndexOf("+");
                    if (idx >= 0) {
                        snippetShortcutBuffer.delete(idx, snippetShortcutBuffer.length());
                    } else {
                        snippetShortcutBuffer.setLength(0);
                    }
                } else {
                    snippetRecordingKeys = false;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                snippetRecordingKeys = false;
                return true;
            }
            String keyName = InputUtil.fromKeyCode(keyCode, scanCode).getTranslationKey();
            String hrName = humanReadableKey(keyName);
            List<String> parts = new ArrayList<>(Arrays.asList(snippetShortcutBuffer.toString().split("\\+")));
            if (parts.size() == 1 && parts.get(0).isEmpty()) {
                parts.clear();
            }
            if (!parts.contains(hrName)) {
                parts.add(hrName);
                snippetShortcutBuffer.setLength(0);
                snippetShortcutBuffer.append(parts.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining("+")));
            }
            return true;
        }

        boolean ctrlHeld = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
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
                        if (ctrlHeld) {
                            int prev = findPreviousWord(snippetCommandsBuffer.toString(), snippetCommandsCursorPos);
                            snippetCommandsBuffer.delete(prev, snippetCommandsCursorPos);
                            snippetCommandsCursorPos = prev;
                        } else {
                            snippetCommandsBuffer.deleteCharAt(snippetCommandsCursorPos - 1);
                            snippetCommandsCursorPos--;
                        }
                    }
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                if (!snippetNameFocused) {
                    moveCursorVertically(-1, snippetCommandsBuffer.toString());
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                if (!snippetNameFocused) {
                    moveCursorVertically(1, snippetCommandsBuffer.toString());
                }
                return true;
            }
            if ((keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
                if (snippetNameFocused) {
                    if (keyCode == GLFW.GLFW_KEY_LEFT && snippetNameCursorPos > 0) snippetNameCursorPos--;
                    if (keyCode == GLFW.GLFW_KEY_RIGHT && snippetNameCursorPos < snippetNameBuffer.length()) snippetNameCursorPos++;
                } else {
                    if (ctrlHeld) {
                        if (keyCode == GLFW.GLFW_KEY_LEFT) {
                            snippetCommandsCursorPos = findPreviousWord(snippetCommandsBuffer.toString(), snippetCommandsCursorPos);
                        } else {
                            snippetCommandsCursorPos = findNextWord(snippetCommandsBuffer.toString(), snippetCommandsCursorPos);
                        }
                    } else {
                        if (keyCode == GLFW.GLFW_KEY_LEFT && snippetCommandsCursorPos > 0) snippetCommandsCursorPos--;
                        if (keyCode == GLFW.GLFW_KEY_RIGHT && snippetCommandsCursorPos < snippetCommandsBuffer.length()) snippetCommandsCursorPos++;
                    }
                }
                return true;
            }
            if (ctrlHeld && keyCode == GLFW.GLFW_KEY_V) {
                String clip = minecraftClient.keyboard.getClipboard();
                if (clip != null && !clip.isEmpty()) {
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

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closedViaEscape = true;
            this.close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            if (!terminals.isEmpty()) {
                TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                activeTerminal.scrollToTop(this.height - (TAB_HEIGHT + verticalPadding) - 5);
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
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            closeTerminal(activeTerminalIndex);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT) {
            if (!terminals.isEmpty()) {
                int nextIndex = (activeTerminalIndex + 1) % terminals.size();
                setActiveTerminal(nextIndex);
            }
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_EQUAL) {
            scale += 0.1f;
            scale = Math.min(MAX_SCALE, scale);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_MINUS) {
            scale -= 0.1f;
            scale = Math.max(MIN_SCALE, scale);
            return true;
        }
        if (isRenaming && renamingTabIndex != -1) {
            lastRenameInputTime = System.currentTimeMillis();
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                String newName = renameBuffer.toString().trim();
                if (!newName.isEmpty()) {
                    tabNames.set(renamingTabIndex, newName);
                }
                isRenaming = false;
                renamingTabIndex = -1;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (renameCursorPos > 0 && renameCursorPos <= renameBuffer.length()) {
                    renameBuffer.deleteCharAt(renameCursorPos - 1);
                    renameCursorPos--;
                    tabNames.set(renamingTabIndex, renameBuffer.toString());
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
                if (renameCursorPos > 0) renameCursorPos--;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                if (renameCursorPos < renameBuffer.length()) renameCursorPos++;
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!terminals.isEmpty()) {
            for (RemotelyClient.CommandSnippet snippet : RemotelyClient.globalSnippets) {
                if (snippet.shortcut != null && !snippet.shortcut.isEmpty()) {
                    if (checkShortcut(snippet.shortcut)) {
                        TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                        String[] lines = snippet.commands.split("\n");
                        for (String line : lines) {
                            if (!line.trim().isEmpty()) {
                                try {
                                    activeTerminal.getInputHandler().commandExecutor.executeCommand(line.trim(), new StringBuilder(line.trim()));
                                } catch (IOException e) {
                                    activeTerminal.appendOutput("ERROR: " + e.getMessage() + "\n");
                                }
                            }
                        }
                        shortcutConsumed = true;
                        return true;
                    }
                }
            }
        }
        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            return activeTerminal.keyPressed(keyCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override
    public boolean charTyped(char chr, int keyCode) {
        if (shortcutConsumed) {
            shortcutConsumed = false;
            return true;
        }
        if (snippetRecordingKeys) return true;
        if (snippetPopupActive) {
            snippetLastInputTime = System.currentTimeMillis();
            if (chr == '\r' || chr == '\b') {
                return true;
            }
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
                String newName = renameBuffer.toString().trim();
                if (!newName.isEmpty()) {
                    tabNames.set(renamingTabIndex, newName);
                }
                isRenaming = false;
                renamingTabIndex = -1;
                return true;
            } else if (chr == '\b') {
                if (renameCursorPos > 0 && renameCursorPos <= renameBuffer.length()) {
                    renameBuffer.deleteCharAt(renameCursorPos - 1);
                    renameCursorPos--;
                    tabNames.set(renamingTabIndex, renameBuffer.toString());
                }
                return true;
            } else if (chr >= 32 && chr != 127) {
                renameBuffer.insert(renameCursorPos, chr);
                renameCursorPos++;
                tabNames.set(renamingTabIndex, renameBuffer.toString());
                return true;
            }
            return true;
        }
        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            return activeTerminal.charTyped(chr) || super.charTyped(chr, keyCode);
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
            assert minecraftClient.player != null;
            minecraftClient.player.sendMessage(Text.literal("Failed to save terminal logs."), false);
        }
    }

    private void drawHoverableButton(DrawContext context, int x, int y, String text, boolean hovered, int textColor) {
        int w = minecraftClient.textRenderer.getWidth(text) + 10;
        int h = 10 + minecraftClient.textRenderer.fontHeight;
        int bgColor = hovered ? 0xFF666666 : 0xFF555555;
        context.fill(x+1, y+1, x+w-1, y+h-1, bgColor);
        context.fill(x, y, x+w, y+1, 0xFFAAAAAA);
        context.fill(x, y, x+1, y+h, 0xFFAAAAAA);
        context.fill(x, y+h-1, x+w, y+h, 0xFF333333);
        context.fill(x+w-1, y, x+w, y+h, 0xFF333333);
        int tx = x + (w - minecraftClient.textRenderer.getWidth(text)) / 2;
        int ty = y + (h - minecraftClient.textRenderer.fontHeight) / 2;
        trimAndDrawText(context, text, tx, ty, w, textColor);
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x+w, y+1, c);
        context.fill(x, y+h-1, x+w, y+h, c);
        context.fill(x, y, x+1, y+h, c);
        context.fill(x+w-1, y, x+w, y+h, c);
    }

    private void trimAndDrawText(DrawContext context, String text, int x, int y, int maxWidth, int color) {
        String t = trimTextToWidthWithEllipsis(text, maxWidth);
        context.drawText(minecraftClient.textRenderer, Text.literal(t), x, y, color, false);
    }

    private String trimTextToWidthWithEllipsis(String text, int maxWidth) {
        if (minecraftClient.textRenderer.getWidth(text) <= maxWidth) return text;
        while (minecraftClient.textRenderer.getWidth(text+"..")>maxWidth && text.length()>1) {
            text = text.substring(0,text.length()-1);
        }
        return text+"..";
    }

    private String ensureCursorBounds(String text) {
        return text;
    }

    private List<String> wrapLines(String[] lines, int width, net.minecraft.client.font.TextRenderer renderer) {
        List<String> wrapped = new ArrayList<>();
        for (String l : lines) {
            if (l.isEmpty()) {
                wrapped.add("");
                continue;
            }
            String temp = l;
            while (renderer.getWidth(temp) > width && !temp.isEmpty()) {
                int cut = temp.length();
                while (cut>0 && renderer.getWidth(temp.substring(0,cut))>width) {
                    cut--;
                }
                if (cut==0) cut = 1;
                wrapped.add(temp.substring(0,cut));
                temp = temp.substring(cut);
            }
            wrapped.add(temp);
        }
        return wrapped;
    }

    private int findCursorLine(List<String> wrappedLines, int cursorPos) {
        int count=0;
        int total=0;
        for (String l : wrappedLines) {
            int len = l.length();
            if (cursorPos <= total+len) {
                return count;
            }
            total += len;
            count++;
        }
        return wrappedLines.size()-1;
    }

    private int cursorPosInLine(int cursorPos, List<String> wrappedLines, int lineIndex) {
        int total=0;
        for (int i=0;i<lineIndex;i++) {
            total+=wrappedLines.get(i).length();
        }
        return cursorPos - total;
    }

    private List<String> getVisibleLines(List<String> wrappedLines, int start, int maxCount) {
        List<String> res = new ArrayList<>();
        for (int i=start; i<start+maxCount && i<wrappedLines.size(); i++) {
            res.add(wrappedLines.get(i));
        }
        return res;
    }

    private void drawFade(DrawContext context, int x1, int y1, int x2, int y2, boolean left) {
        int steps = 10;
        for (int i=0;i<steps;i++) {
            float alpha = (float)i/(steps-1);
            int a = (int)(alpha*255);
            int c = (0x333333 & 0x00FFFFFF) | (a<<24);
            if (left) {
                context.fill(x1+i, y1, x1+i+1, y2, c);
            } else {
                context.fill(x2-i-1, y1, x2-i, y2, c);
            }
        }
    }

    private int getCursorFromMouseX(String line, int mouseX) {
        int pos=0;
        while(pos<line.length()) {
            int w=minecraftClient.textRenderer.getWidth(line.substring(0,pos+1));
            if (w>mouseX) break;
            pos++;
        }
        return pos;
    }

    private void moveCursorVertically(int direction, String full) {
        int commandsBoxWidth = snippetPopupWidth - 10;
        List<String> wrappedLines = wrapLines(full.split("\n",-1), commandsBoxWidth, minecraftClient.textRenderer);
        int cLineIndex = findCursorLine(wrappedLines, snippetCommandsCursorPos);
        int cPosInLine = cursorPosInLine(snippetCommandsCursorPos, wrappedLines, cLineIndex);
        cLineIndex += direction;
        if (cLineIndex < 0) cLineIndex = 0;
        if (cLineIndex >= wrappedLines.size()) cLineIndex = wrappedLines.size() - 1;
        int newPos = 0;
        for (int i=0; i<cLineIndex; i++) {
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
                case "key.mouse." -> "MOUSE";
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
                return InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL);
            }
            case "shift" -> {
                return InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT);
            }
            case "alt" -> {
                return InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_ALT) || InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_ALT);
            }
        }
        for (int code=0;code<400;code++) {
            InputUtil.Key testKey = InputUtil.fromKeyCode(code, 0);
            if (testKey!=InputUtil.UNKNOWN_KEY && testKey.getTranslationKey().toLowerCase().endsWith("."+k.toLowerCase())) {
                if (InputUtil.isKeyPressed(minecraftClient.getWindow().getHandle(), code)) return true;
            }
        }
        return false;
    }

    private int findPreviousWord(String text, int pos) {
        if (pos<=0) return 0;
        int idx=pos-1;
        while (idx>0 && Character.isWhitespace(text.charAt(idx))) idx--;
        while (idx>0 && !Character.isWhitespace(text.charAt(idx))) idx--;
        if (idx>0 && Character.isWhitespace(text.charAt(idx))) idx++;
        return idx;
    }

    private int findNextWord(String text, int pos) {
        if (pos>=text.length()) return text.length();
        int idx=pos;
        while (idx<text.length() && !Character.isWhitespace(text.charAt(idx))) idx++;
        while (idx<text.length() && Character.isWhitespace(text.charAt(idx))) idx++;
        return idx;
    }

    static class TabInfo {
        String name;
        int width;
        TabInfo(String n, int w) {
            name=n; width=w;
        }
    }

    @Override
    public void removed() {
        remotelyClient.activeTerminalIndex = this.activeTerminalIndex;
        remotelyClient.scale = this.scale;
        remotelyClient.snippetPanelWidth = this.snippetPanelWidth;
        remotelyClient.showSnippetsPanel = this.showSnippetsPanel;
    }
}

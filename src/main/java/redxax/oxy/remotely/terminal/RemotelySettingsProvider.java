package redxax.oxy.remotely.terminal;

import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.emulator.ColorPaletteImpl;
import com.jediterm.terminal.model.TerminalTypeAheadSettings;
import com.jediterm.terminal.ui.TerminalActionPresentation;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redxax.oxy.remotely.config.Config;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;

import static redxax.oxy.remotely.RemotelyClient.os;

public class RemotelySettingsProvider implements SettingsProvider {

    private boolean isMacOS() {
        return os != null && os.toLowerCase().contains("mac");
    }

    private boolean isWindows() {
        return os != null && os.toLowerCase().contains("win");
    }

    @Override
    public @NotNull TerminalActionPresentation getNewSessionActionPresentation() {
        return new TerminalActionPresentation("New Session", isMacOS()
                ? KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.META_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    }

    @Override
    public @NotNull TerminalActionPresentation getOpenUrlActionPresentation() {
        KeyStroke keyStroke = isMacOS()
                ? KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.META_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        return new TerminalActionPresentation("Open URL", keyStroke);
    }

    @Override
    public @NotNull TerminalActionPresentation getCopyActionPresentation() {
        KeyStroke keyStroke = isMacOS()
                ? KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        return new TerminalActionPresentation("Copy", keyStroke);
    }

    @Override
    public @NotNull TerminalActionPresentation getPasteActionPresentation() {
        KeyStroke keyStroke = isMacOS()
                ? KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        return new TerminalActionPresentation("Paste", keyStroke);
    }

    @Override
    public @NotNull TerminalActionPresentation getClearBufferActionPresentation() {
        return new TerminalActionPresentation("Clear Buffer", isMacOS()
                ? KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.META_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
    }

    @Override
    public @NotNull TerminalActionPresentation getPageUpActionPresentation() {
        return new TerminalActionPresentation("Page Up", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.SHIFT_DOWN_MASK));
    }

    @Override
    public @NotNull TerminalActionPresentation getPageDownActionPresentation() {
        return new TerminalActionPresentation("Page Down", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.SHIFT_DOWN_MASK));
    }

    @Override
    public @NotNull TerminalActionPresentation getLineUpActionPresentation() {
        return new TerminalActionPresentation("Line Up", isMacOS()
                ? KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.META_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK));
    }

    @Override
    public @NotNull TerminalActionPresentation getLineDownActionPresentation() {
        return new TerminalActionPresentation("Line Down", isMacOS()
                ? KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.META_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK));
    }

    @Override
    public @NotNull TerminalActionPresentation getCloseSessionActionPresentation() {
        return new TerminalActionPresentation("Close Session", isMacOS()
                ? KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.META_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    }

    @Override
    public @NotNull TerminalActionPresentation getFindActionPresentation() {
        return new TerminalActionPresentation("Find", isMacOS()
                ? KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.META_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
    }

    @Override
    public @NotNull TerminalActionPresentation getSelectAllActionPresentation() {
        return new TerminalActionPresentation("Select All", Collections.emptyList());
    }

    @Override
    public ColorPalette getTerminalColorPalette() {
        return ColorPaletteImpl.XTERM_PALETTE;
    }

    @Override
    public Font getTerminalFont() {
        String fontName;
        if (isWindows()) {
            fontName = "Consolas";
        } else if (isMacOS()) {
            fontName = "Menlo";
        } else {
            fontName = "Monospaced";
        }
        return new Font(fontName, Font.PLAIN, (int) getTerminalFontSize());
    }

    @Override
    public float getTerminalFontSize() {
        return 14;
    }

    @Override
    public @NotNull TextStyle getDefaultStyle() {
        return new TextStyle(
                fromAwtToTerminalColor(new java.awt.Color(Config.terminalTextColor)),
                fromAwtToTerminalColor(new java.awt.Color(Config.elementBackgroundColor))
        );
    }

    @Override
    public @NotNull TextStyle getSelectionColor() {
        return new TextStyle(
                fromAwtToTerminalColor(new java.awt.Color(Config.terminalTextColor)),
                fromAwtToTerminalColor(new java.awt.Color(Config.globalSelectionColor))
        );
    }

    private @Nullable TerminalColor fromAwtToTerminalColor(Color color) {
        if (color == null) {
            return null;
        }
        return TerminalColor.rgb(color.getRed(), color.getGreen(), color.getBlue());
    }

    @Override
    public @NotNull TextStyle getFoundPatternColor() {
        return new TextStyle(TerminalColor.BLACK, TerminalColor.rgb(255, 255, 0));
    }

    @Override
    public TextStyle getHyperlinkColor() {
        return new TextStyle(TerminalColor.rgb(50, 50, 255), null);
    }

    @Override
    public HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode() {
        return HyperlinkStyle.HighlightMode.HOVER;
    }

    @Override
    public boolean useInverseSelectionColor() {
        return true;
    }

    @Override
    public boolean copyOnSelect() {
        return false;
    }

    @Override
    public boolean pasteOnMiddleMouseClick() {
        return false;
    }

    @Override
    public boolean emulateX11CopyPaste() {
        return false;
    }

    @Override
    public boolean useAntialiasing() {
        return true;
    }

    @Override
    public int maxRefreshRate() {
        return 50;
    }

    @Override
    public boolean audibleBell() {
        return true;
    }

    @Override
    public boolean enableMouseReporting() {
        return true;
    }

    @Override
    public int caretBlinkingMs() {
        return 500;
    }

    @Override
    public boolean scrollToBottomOnTyping() {
        return true;
    }

    @Override
    public boolean DECCompatibilityMode() {
        return true;
    }

    @Override
    public boolean forceActionOnMouseReporting() {
        return false;
    }

    @Override
    public int getBufferMaxLinesCount() {
        return 5000;
    }

    @Override
    public boolean altSendsEscape() {
        return true;
    }

    @Override
    public boolean ambiguousCharsAreDoubleWidth() {
        return false;
    }

    @Override
    public @NotNull TerminalTypeAheadSettings getTypeAheadSettings() {
        return TerminalTypeAheadSettings.DEFAULT;
    }
}
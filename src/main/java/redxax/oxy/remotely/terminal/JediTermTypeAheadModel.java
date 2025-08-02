package redxax.oxy.remotely.terminal;

import com.jediterm.core.typeahead.TypeAheadTerminalModel;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.*;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class JediTermTypeAheadModel implements TypeAheadTerminalModel {
    private final @NotNull Terminal myTerminal;
    private final @NotNull TerminalTextBuffer myTerminalTextBuffer;
    private final @NotNull SettingsProvider mySettingsProvider;
    private @NotNull TypeAheadTerminalModel.ShellType myShellType = ShellType.Unknown;
    private final List<TerminalModelListener> myTypeAheadListeners = new CopyOnWriteArrayList<>();

    private boolean isPredictionsApplied = false;
    private static Field typeAheadLineField;

    static {
        try {
            typeAheadLineField = TerminalLine.class.getDeclaredField("myTypeAheadLine");
            typeAheadLineField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            typeAheadLineField = null;
        }
    }

    public JediTermTypeAheadModel(@NotNull Terminal terminal, @NotNull TerminalTextBuffer textBuffer, @NotNull SettingsProvider settingsProvider) {
        myTerminal = terminal;
        myTerminalTextBuffer = textBuffer;
        mySettingsProvider = settingsProvider;
    }

    @Override
    public void insertCharacter(char ch, int index) {
        isPredictionsApplied = true;
        TerminalLine typeAheadLine = getTypeAheadLine();

        TextStyle typeAheadStyle = mySettingsProvider.getTypeAheadSettings().getTypeAheadStyle();
        typeAheadLine.insertString(index, new CharBuffer(ch, 1), typeAheadStyle);

        setTypeAheadLine(typeAheadLine);
    }

    @Override
    public void removeCharacters(int from, int count) {
        isPredictionsApplied = true;
        TerminalLine typeAheadLine = getTypeAheadLine();

        typeAheadLine.deleteCharacters(from, count, TextStyle.EMPTY);

        setTypeAheadLine(typeAheadLine);
    }

    public void forceRedraw() {
        fireTypeAheadModelChangeEvent();
    }

    @Override
    public void moveCursor(int index) {}

    @Override
    public void clearPredictions() {
        if (isPredictionsApplied) {
            clearTypeAheadPredictionsWithReflection(myTerminalTextBuffer);
        }
        isPredictionsApplied = false;
    }

    private void clearTypeAheadPredictionsWithReflection(@NotNull TerminalTextBuffer textBuffer) {
        textBuffer.lock();
        try {
            clearTypeAheadInLines(textBuffer.getScreenLinesStorage());
            clearTypeAheadInLines(textBuffer.getHistoryLinesStorage());
        } finally {
            textBuffer.unlock();
        }
        fireTypeAheadModelChangeEvent();
    }

    private void clearTypeAheadInLines(@NotNull LinesStorage lines) {
        if (typeAheadLineField == null) return;
        try {
            for (TerminalLine line : lines) {
                typeAheadLineField.set(line, null);
            }
        } catch (IllegalAccessException e) {
            // ignore
        }
    }

    @Override
    public void lock() {
        myTerminalTextBuffer.lock();
    }

    @Override
    public void unlock() {
        myTerminalTextBuffer.unlock();
    }

    @Override
    public boolean isUsingAlternateBuffer() {
        return myTerminalTextBuffer.isUsingAlternateBuffer();
    }

    @Override
    public boolean isTypeAheadEnabled() {
        return mySettingsProvider.getTypeAheadSettings().isEnabled();
    }

    @Override
    public long getLatencyThreshold() {
        return mySettingsProvider.getTypeAheadSettings().getLatencyThreshold();
    }

    @Override
    public @NotNull ShellType getShellType() {
        return myShellType;
    }

    public void setShellType(ShellType shellType) {
        myShellType = shellType;
    }

    @Override
    public @NotNull TypeAheadTerminalModel.LineWithCursorX getCurrentLineWithCursor() {
        TerminalLine terminalLine = myTerminalTextBuffer.getLine(myTerminal.getCursorY() - 1);
        return new LineWithCursorX(new StringBuffer(terminalLine.getText()), myTerminal.getCursorX() - 1);
    }

    @Override
    public int getTerminalWidth() {
        return myTerminal.getTerminalWidth();
    }

    private @NotNull TerminalLine getTypeAheadLine() {
        TerminalLine terminalLine = myTerminalTextBuffer.getLine(myTerminal.getCursorY() - 1);
        TerminalLine typeAheadLine = getTypeAheadLineField(terminalLine);
        if (typeAheadLine != null) {
            terminalLine = typeAheadLine;
        }
        return terminalLine.copy();
    }

    private void setTypeAheadLine(@NotNull TerminalLine typeAheadTerminalLine) {
        TerminalLine terminalLine = myTerminalTextBuffer.getLine(myTerminal.getCursorY() - 1);
        setTypeAheadLineField(terminalLine, typeAheadTerminalLine);
    }

    private TerminalLine getTypeAheadLineField(TerminalLine terminalLine) {
        if (typeAheadLineField == null) return null;
        try {
            return (TerminalLine) typeAheadLineField.get(terminalLine);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private void setTypeAheadLineField(TerminalLine terminalLine, TerminalLine value) {
        if (typeAheadLineField == null) return;
        try {
            typeAheadLineField.set(terminalLine, value);
        } catch (IllegalAccessException e) {
            // Ignore if unable to set
        }
    }

    public void addTypeAheadModelListener(@NotNull TerminalModelListener listener) {
        myTypeAheadListeners.add(listener);
    }

    public void removeTypeAheadModelListener(@NotNull TerminalModelListener listener) {
        myTypeAheadListeners.remove(listener);
    }

    private void fireTypeAheadModelChangeEvent() {
        for (TerminalModelListener typeAheadListener : myTypeAheadListeners) {
            typeAheadListener.modelChanged();
        }
    }
}
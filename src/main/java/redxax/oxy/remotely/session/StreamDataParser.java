package redxax.oxy.remotely.session;

import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rebase.instance.Instance;
import restudio.rescreen.debug.DebugManager;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StreamDataParser implements BiConsumer<Integer, String> {
    private static final String BASELINE_START = "[REMOTELY_PLAYER_BASELINE_START]";
    private static final String BASELINE_END = "[REMOTELY_PLAYER_BASELINE_END]";
    private final PlayerManagerController controller;
    private final Instance instance;
    private final Pattern startPattern = Pattern.compile("\\[FILE_START:(.+)]");
    private final Pattern endPattern = Pattern.compile("\\[FILE_END:(.+)]");
    private boolean isReading = false;
    private String currentFile = null;
    private final StringBuilder buffer = new StringBuilder();

    public StreamDataParser(PlayerManagerController controller) {
        this.controller = controller;
        this.instance = controller.getInstance();
    }

    @Override
    public void accept(Integer integer, String line) {
        if (line == null) return;
        line = line.trim();
        if (BASELINE_START.equals(line)) {
            controller.beginPlayerBaseline();
            instance.onLogOutput(integer == null ? 0 : integer, line);
            return;
        }
        if (BASELINE_END.equals(line)) {
            instance.onLogOutput(integer == null ? 0 : integer, line);
            controller.endPlayerBaseline();
            return;
        }

        if (isReading) {
            Matcher endMatcher = endPattern.matcher(line);
            if (endMatcher.find()) {
                String fileName = endMatcher.group(1);
                if (fileName.equals(currentFile)) {
                    DebugManager.getInstance().log("StreamDataParser", "Finished reading file: " + fileName);
                    controller.handleFileUpdate(fileName, buffer.toString());
                    isReading = false;
                    currentFile = null;
                    buffer.setLength(0);
                }
            } else {
                buffer.append(line).append("\n");
            }
        } else {
            Matcher startMatcher = startPattern.matcher(line);
            if (startMatcher.find()) {
                currentFile = startMatcher.group(1);
                DebugManager.getInstance().log("StreamDataParser", "Started reading file: " + currentFile);
                isReading = true;
                buffer.setLength(0);
            } else {
                instance.onLogOutput(integer == null ? 0 : integer, line);
            }
        }
    }
}

package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReMouseButton;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.RowWidget;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MessageRuleDesignerScreen extends FocusedJsonResourceDesignerScreen {
    protected PopupWidget messageLogPopup;
    protected int messageLogPage;
    protected int messageLogPageSize = 8;
    protected String selectedMessageText = "";
    protected String selectedMessageSource = "";
    protected String previewMessageText = "";
    protected int previewMessageX;
    protected int previewMessageY;
    protected int previewMessageSelectionAnchor = -1;
    protected int previewMessageSelectionFocus = -1;
    protected boolean previewMessageSelecting;

    public MessageRuleDesignerScreen(StudioScreen owner, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, ReSyncResourceDragPayload.MESSAGE_RULE, resourceId, resource, serverId, parent);
        requestMessageLogPage(0);
    }

    @Override
    protected boolean centeredTextPreview() {
        return true;
    }

    @Override
    protected void renderResourcePreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int mouseX, int mouseY, int text, int muted) {
        renderMessageRuleRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
    }

    @Override
    protected List<String> editorFields() {
        return messageRuleFields();
    }

    @Override
    protected List<ResourcePanelSection> editorSections(List<String> fields) {
        return appendRemainingSections(List.of(
            new ResourcePanelSection("Match", fields.stream().filter(field -> List.of("source", "contains", "players", "permission").contains(field)).toList()),
            new ResourcePanelSection("Action", fields.stream().filter(field -> List.of("action", "replacement", "flowPredicate", "flowId").contains(field)).toList()),
            new ResourcePanelSection("State", fields.stream().filter(field -> List.of("priority", "enabled").contains(field)).toList())
        ), fields);
    }

    @Override
    protected void appendResourcePanelWidgets(List<AnimatedWidget> widgets, int rowWidth) {
        widgets.addAll(messageLogPanelWidgets(rowWidth));
    }

    @Override
    protected boolean usesMessageLog() {
        return true;
    }

    @Override
    protected void onMessageLogRefreshed() {
        if (messageLogPopup != null && messageLogPopup.isVisible()) {
            messageLogPopup.hide();
            showMessageLogPopup();
        }
    }

    @Override
    protected List<String> actionOptions(String field) {
        return List.of("replace_section", "replace", "append", "prepend", "remove", "flow");
    }

    @Override
    protected List<String> customSelectorOptions(String field) {
        return switch (field) {
            case "action" -> actionOptions(field);
            case "source" -> List.of("chat", "join", "quit", "kick", "death", "title", "actionbar", "bossbar", "openScreen", "packetText", "system");
            default -> null;
        };
    }

    @Override
    protected boolean customDropdownField(String field) {
        return "source".equals(field) || "action".equals(field);
    }

    @Override
    protected boolean customCodeField(String field) {
        return "replacement".equals(field);
    }

    @Override
    protected int customCodeFieldHeight(String field) {
        return customCodeField(field) ? dynamicCodeFieldHeight(field) : -1;
    }

    @Override
    protected void onDropdownSelectionChanged(String field, String value) {
        if ("source".equals(field)) {
            requestMessageLogPage(0);
        }
    }

    @Override
    protected void onSelectorValueChanged(String field, String value) {
        if ("source".equals(field)) {
            requestMessageLogPage(0);
        }
    }

    @Override
    protected boolean handleResourceMouseClicked(ReMouseEvent event) {
        return event.button() == ReMouseButton.LEFT && handleMessagePreviewSelectionStart((int) event.x(), (int) event.y());
    }

    @Override
    protected boolean handleResourceMouseReleased(ReMouseEvent event) {
        return event.button() == ReMouseButton.LEFT && handleMessagePreviewSelectionRelease((int) event.x(), (int) event.y());
    }

    @Override
    protected boolean handleResourceMouseDragged(ReMouseEvent event) {
        return event.button() == ReMouseButton.LEFT && handleMessagePreviewSelectionDrag((int) event.x(), (int) event.y());
    }

    @Override
    protected void sanitizeLegacyResourceFields() {
        resource.remove("regex");
        resource.remove("componentPath");
        resource.remove("componentValue");
    }

    @Override
    protected String resourceSummary() {
        return firstFilled(jsonText("source"), "Rewrite");
    }

    @Override
    protected String resourceDisplayName() {
        return "Message Rule";
    }

    protected void renderMessageRuleRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
        int centerY = previewY + previewHeight / 2;
        int centerX = previewX + previewWidth / 2;
        String source = jsonText("source");
        String find = jsonText("contains");
        String original = messagePreviewSource(source);
        String action = jsonText("action").toLowerCase(Locale.ROOT);
        previewMessageText = original;
        previewMessageX = centeredTextX(previewMessageText, centerX);
        previewMessageY = centerY - 14;
        renderMessageSelection(context, previewMessageText, previewMessageX, previewMessageY);
        drawFormattedLine(context, original, previewMessageX, previewMessageY, muted, true);
        String replacement = jsonText("replacement");
        String rendered = messagePreviewResult(original, find, replacement, action);
        drawFormattedLine(context, rendered, centeredTextX(rendered, centerX), centerY + 10, text, true);
    }

    protected String messagePreviewResult(String original, String find, String replacement, String action) {
        boolean matches = find == null || find.isBlank() || original.contains(find);
        if (!matches) {
            return "<dark_gray>No Match</dark_gray>";
        }
        String renderedReplacement = messageReplacementText(original, replacement);
        return switch (action) {
            case "remove", "clear", "hide" -> "<dark_gray>Hidden</dark_gray>";
            case "append" -> original + renderedReplacement;
            case "prepend" -> renderedReplacement + original;
            case "replace_section", "section" -> find == null || find.isBlank() ? renderedReplacement : original.replace(find, renderedReplacement);
            case "flow" -> renderedReplacement;
            default -> renderedReplacement;
        };
    }

    protected String messageReplacementText(String original, String replacement) {
        String template = replacement == null || replacement.isBlank() ? "{message}" : replacement;
        return template.replace("{player}", "Steve").replace("{message}", original);
    }

    protected String messageSourceLabel(String source) {
        return switch (source == null ? "" : source.toLowerCase(Locale.ROOT)) {
            case "chat" -> "Chat";
            case "quit" -> "Quit";
            case "kick" -> "Kick";
            case "death" -> "Death";
            case "title" -> "Title";
            case "actionbar" -> "Actionbar";
            case "bossbar" -> "Bossbar";
            case "openscreen" -> "Open Screen";
            case "packettext" -> "Packet Text";
            case "system" -> "System";
            default -> "Join";
        };
    }

    protected String messagePreviewSource(String source) {
        if (!selectedMessageText.isBlank() && (source == null || source.isBlank() || selectedMessageSource.isBlank() || selectedMessageSource.equalsIgnoreCase(source))) {
            return selectedMessageText;
        }
        JsonObject entry = firstMessageLogEntry(source);
        if (entry != null) {
            return jsonText(entry, "plainText");
        }
        return sampleMessageSource(source);
    }

    protected JsonObject firstMessageLogEntry(String source) {
        JsonObject page = messageLogPage();
        JsonArray entries = page != null && page.has("entries") && page.get("entries").isJsonArray() ? page.getAsJsonArray("entries") : new JsonArray();
        for (JsonElement element : entries) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String entrySource = jsonText(entry, "source");
            if (source == null || source.isBlank() || entrySource.isBlank() || entrySource.equalsIgnoreCase(source)) {
                return entry;
            }
        }
        return null;
    }

    protected String sampleMessageSource(String source) {
        return switch (source == null ? "" : source.toLowerCase(Locale.ROOT)) {
            case "chat" -> "Steve: Hello server";
            case "quit" -> "Steve left the game";
            case "kick" -> "Steve was kicked";
            case "death" -> "Steve fell from a high place";
            case "title" -> "Welcome Steve";
            case "actionbar" -> "Objective Updated";
            case "bossbar" -> "Dragon Health";
            case "openscreen" -> "Chest";
            case "packettext" -> "Server Notice";
            case "system" -> "Server restarting soon";
            default -> "Steve joined the game";
        };
    }

    protected List<AnimatedWidget> messageLogPanelWidgets(int rowWidth) {
        List<AnimatedWidget> widgets = new ArrayList<>();
        AnimatedButton button = new AnimatedButton.Builder()
            .label("Messages")
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .entranceAnimation(false)
            .onClick(() -> {
                requestMessageLogPage(messageLogPage);
                showMessageLogPopup();
            })
            .build();
        ReSyncStudioPanelState.disableEntrance(button);
        widgets.add(studioPanelState.row("Samples", button, rowWidth, "Open Logged Messages"));
        return widgets;
    }

    protected RowWidget messageLogControls(int width) {
        AnimatedButton refresh = new AnimatedButton.Builder()
            .label("Refresh")
            .size(70, 18)
            .entranceAnimation(false)
            .onClick(() -> requestMessageLogPage(messageLogPage))
            .build();
        AnimatedButton previous = new AnimatedButton.Builder()
            .label("Prev")
            .size(52, 18)
            .entranceAnimation(false)
            .onClick(() -> requestMessageLogPage(Math.max(0, messageLogPage - 1)))
            .build();
        AnimatedButton next = new AnimatedButton.Builder()
            .label("Next")
            .size(52, 18)
            .entranceAnimation(false)
            .onClick(() -> requestMessageLogPage(messageLogPage + 1))
            .build();
        RowWidget controls = new RowWidget.Builder()
            .size(width, 18)
            .padding(4)
            .addWidget(refresh, previous, next)
            .build();
        ReSyncStudioPanelState.disableEntrance(controls);
        return controls;
    }

    protected void showMessageLogPopup() {
        if (messageLogPopup != null && messageLogPopup.isVisible()) {
            messageLogPopup.hide();
        }
        PopupWidget.Builder builder = new PopupWidget.Builder("Messages")
            .size(520, 360)
            .setResizable(true);
        builder.addRow("", true, 22, messageLogControls(480));
        List<JsonObject> entries = messageLogEntries();
        if (entries.isEmpty()) {
            builder.addRow("", true, 24, new MessageLogEntryWidget(null, 480, 22));
        } else {
            for (JsonObject entry : entries) {
                builder.addRow("", true, 24, new MessageLogEntryWidget(entry, 480, 22));
            }
        }
        messageLogPopup = builder.build();
        hostScreen().addDrawableChild(messageLogPopup);
        messageLogPopup.show();
    }

    protected List<JsonObject> messageLogEntries() {
        JsonObject page = messageLogPage();
        JsonArray entries = page != null && page.has("entries") && page.get("entries").isJsonArray() ? page.getAsJsonArray("entries") : new JsonArray();
        List<JsonObject> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonElement element : entries) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String key = jsonText(entry, "source") + "\u0000" + jsonText(entry, "plainText");
            if (seen.add(key)) {
                result.add(entry);
            }
        }
        return result;
    }

    protected class MessageLogEntryWidget extends AnimatedWidget {
        private final JsonObject entry;

        protected MessageLogEntryWidget(JsonObject entry, int width, int height) {
            super(0, 0, width, height, "");
            this.entry = entry;
            setCursorHoverReactive(entry != null);
            entranceAnimationEnabled = false;
        }

        @Override
        protected void drawContent(IDrawContext context, int mouseX, int mouseY) {
            int bg = isMouseOver(mouseX, mouseY) && entry != null ? ThemeManager.getColor(ThemeColor.elementHoverBackground) : ThemeManager.getColor(ThemeColor.elementBackground);
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
            if (entry == null) {
                context.drawText("No Messages", getX() + 6, getY() + 6, ThemeManager.getColor(ThemeColor.textDark), false);
                return;
            }
            String source = messageSourceLabel(jsonText(entry, "source"));
            String text = clippedPlainText(jsonText(entry, "plainText"), Math.max(24, getWidth() / 6));
            context.drawText(source, getX() + 6, getY() + 6, ThemeManager.getColor(ThemeColor.textDark), false);
            context.drawRichText(text, getX() + 82, getY() + 6, ThemeManager.getColor(ThemeColor.text), true);
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            if (button != 0 || entry == null) {
                return;
            }
            selectMessageLogEntry(entry);
            if (messageLogPopup != null) {
                messageLogPopup.hide();
            }
        }
    }

    protected JsonObject messageLogPage() {
        FlowManager manager = FlowManager.getInstance();
        return manager != null ? manager.getMessageLogPage(serverId) : null;
    }

    protected void requestMessageLogPage(int page) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        messageLogPage = Math.max(0, page);
        manager.requestMessageLog(serverId, messageLogPage, messageLogPageSize, "", jsonText("source"));
    }

    protected void selectMessageLogEntry(JsonObject entry) {
        selectedMessageText = jsonText(entry, "plainText");
        selectedMessageSource = jsonText(entry, "source");
        previewMessageSelectionAnchor = -1;
        previewMessageSelectionFocus = -1;
        if (!selectedMessageSource.isBlank()) {
            putJsonText("source", selectedMessageSource);
        }
        reloadFields();
    }

    protected String clippedPlainText(String value, int maxLength) {
        String clean = value != null ? value.replace('\n', ' ').replace('\r', ' ').strip() : "";
        if (clean.length() <= maxLength) {
            return clean.isBlank() ? "Message" : clean;
        }
        return clean.substring(0, Math.max(1, maxLength - 1)) + "...";
    }

    protected void renderMessageSelection(IDrawContext context, String text, int startX, int y) {
        if (text == null || text.isBlank() || previewMessageSelectionAnchor < 0 || previewMessageSelectionFocus < 0) {
            return;
        }
        int start = Math.min(previewMessageSelectionAnchor, previewMessageSelectionFocus);
        int end = Math.max(previewMessageSelectionAnchor, previewMessageSelectionFocus);
        if (start == end) {
            return;
        }
        String prefix = text.substring(0, Math.min(start, text.length()));
        String selection = text.substring(Math.min(start, text.length()), Math.min(end, text.length()));
        int x1 = startX + textWidth(prefix);
        int x2 = x1 + Math.max(2, textWidth(selection));
        context.drawInvertedRect(x1, y - 1, x2, y + 10);
    }

    protected boolean handleMessagePreviewSelectionStart(int mouseX, int mouseY) {
        if (!messagePreviewHit(mouseX, mouseY)) {
            return false;
        }
        previewMessageSelecting = true;
        previewMessageSelectionAnchor = messagePreviewIndex(mouseX);
        previewMessageSelectionFocus = previewMessageSelectionAnchor;
        return true;
    }

    protected boolean handleMessagePreviewSelectionDrag(int mouseX, int mouseY) {
        if (!previewMessageSelecting) {
            return false;
        }
        previewMessageSelectionFocus = messagePreviewIndex(mouseX);
        return true;
    }

    protected boolean handleMessagePreviewSelectionRelease(int mouseX, int mouseY) {
        if (!previewMessageSelecting) {
            return false;
        }
        previewMessageSelecting = false;
        previewMessageSelectionFocus = messagePreviewIndex(mouseX);
        int start = Math.min(previewMessageSelectionAnchor, previewMessageSelectionFocus);
        int end = Math.max(previewMessageSelectionAnchor, previewMessageSelectionFocus);
        if (previewMessageText != null && start >= 0 && end > start && end <= previewMessageText.length()) {
            putJsonText("contains", previewMessageText.substring(start, end));
            refreshResourcePanelFields();
        }
        return true;
    }

    protected boolean messagePreviewHit(int mouseX, int mouseY) {
        if (previewMessageText == null || previewMessageText.isBlank()) {
            return false;
        }
        int width = Math.max(12, textWidth(previewMessageText));
        return mouseX >= previewMessageX - 2 && mouseX <= previewMessageX + width + 2 && mouseY >= previewMessageY - 3 && mouseY <= previewMessageY + 12;
    }

    protected int messagePreviewIndex(int mouseX) {
        if (previewMessageText == null || previewMessageText.isBlank()) {
            return 0;
        }
        int relative = Math.max(0, mouseX - previewMessageX);
        for (int index = 0; index <= previewMessageText.length(); index++) {
            String prefix = previewMessageText.substring(0, index);
            if (textWidth(prefix) >= relative) {
                return index;
            }
        }
        return previewMessageText.length();
    }

    protected List<String> messageRuleFields() {
        return new ArrayList<>(List.of("source", "contains", "replacement", "action", "flowPredicate", "flowId", "priority", "enabled"));
    }
}
